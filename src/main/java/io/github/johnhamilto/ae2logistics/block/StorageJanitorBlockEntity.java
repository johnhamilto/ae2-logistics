package io.github.johnhamilto.ae2logistics.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalKeyType;

/**
 * The ME Storage Janitor: an in-place IO Port for the whole network. A run walks
 * every stored key and moves it through a transient buffer - extract network-wide,
 * re-insert through normal routing - so AE2's own insert ordering (priority,
 * partitions-first, filters) re-decides where everything lives. External storages
 * behind buses participate in both directions for free.
 *
 * A run is exactly TWO passes: placement changes are not observable through the
 * aggregate storage API (every extract+reinsert "moves" its full amount whether or
 * not the home changed), so loop-until-no-progress cannot be detected - pass two
 * catches space freed by pass one, and pathological full-swap webs converge over
 * repeated manual runs. Items never strand: a re-insert shortfall (should be
 * impossible - the extract just freed that space) parks in a persisted held buffer
 * that retries every tick before new work.
 */
public class StorageJanitorBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    private static final int KEYS_PER_TICK = 4;
    private static final long AMOUNT_CAP = 4096;
    private static final int PASSES = 2;
    private static final double POWER_PER_KEY = 1.0;

    private static final IGridNodeListener<StorageJanitorBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(StorageJanitorBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setIdlePowerUsage(1.0);

    private boolean running;
    private int pass;
    private int cursor;
    private List<AEKey> keys = List.of();
    private long processedTotal;
    /** Re-insert shortfall parking; persisted, retried before new work, normally empty. */
    private final List<GenericStack> held = new ArrayList<>();

    public StorageJanitorBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.STORAGE_JANITOR_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.getBlockPos()));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Nullable
    private IGrid grid() {
        var node = mainNode.getNode();
        return node == null ? null : node.getGrid();
    }

    public boolean running() {
        return running;
    }

    public int progressDone() {
        return Math.max(0, (pass * keys.size() + cursor));
    }

    public int progressTotal() {
        return keys.size() * PASSES;
    }

    public long processedTotal() {
        return processedTotal;
    }

    public int heldCount() {
        return held.size();
    }

    /** Starts a fresh two-pass run, or stops the current one (held items keep retrying). */
    public void toggle() {
        if (running) {
            running = false;
        } else {
            var grid = grid();
            if (grid == null) {
                return;
            }
            var list = new ArrayList<AEKey>();
            for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
                // Signals stay out of storage semantics by the same contract queries use.
                if (entry.getKey().getType() != SignalKeyType.TYPE) {
                    list.add(entry.getKey());
                }
            }
            keys = list;
            pass = 0;
            cursor = 0;
            processedTotal = 0;
            running = !keys.isEmpty();
        }
        setChanged();
    }

    public void serverTick() {
        var grid = grid();
        if (grid == null || !mainNode.isActive()) {
            return;
        }
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.empty();

        // Held items first: nothing new moves while anything is parked.
        if (!held.isEmpty()) {
            var stack = held.get(0);
            long placed = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (placed >= stack.amount()) {
                held.remove(0);
            } else if (placed > 0) {
                held.set(0, new GenericStack(stack.what(), stack.amount() - placed));
            }
            setChanged();
            return;
        }
        if (!running) {
            return;
        }

        int budget = KEYS_PER_TICK;
        while (budget-- > 0) {
            if (cursor >= keys.size()) {
                cursor = 0;
                if (++pass >= PASSES) {
                    running = false;
                    setChanged();
                    return;
                }
            }
            var key = keys.get(cursor++);
            long taken = storage.extract(key, AMOUNT_CAP, Actionable.MODULATE, source);
            if (taken > 0) {
                long placed = storage.insert(key, taken, Actionable.MODULATE, source);
                if (placed < taken) {
                    held.add(new GenericStack(key, taken - placed));
                }
                processedTotal += taken;
            }
            grid.getEnergyService().extractAEPower(POWER_PER_KEY, Actionable.MODULATE,
                    appeng.api.config.PowerMultiplier.CONFIG);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        var list = output.childrenList("held");
        for (var stack : held) {
            var entry = list.addChild();
            stack.what().toTagGeneric(entry.child("what"));
            entry.putLong("amount", stack.amount());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        held.clear();
        input.childrenList("held").ifPresent(entries -> {
            for (var entry : entries) {
                var what = entry.child("what").map(AEKey::fromTagGeneric).orElse(null);
                if (what != null) {
                    held.add(new GenericStack(what, entry.getLongOr("amount", 0L)));
                }
            }
        });
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
}
