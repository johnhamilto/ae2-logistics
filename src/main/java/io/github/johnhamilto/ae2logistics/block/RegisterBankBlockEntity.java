package io.github.johnhamilto.ae2logistics.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

public class RegisterBankBlockEntity extends BlockEntity implements IInWorldGridNodeHost {

    private static final IGridNodeListener<RegisterBankBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(RegisterBankBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onGridChanged(RegisterBankBlockEntity owner, IGridNode node) {
            owner.pushToService();
        }
    };

    /** Channels manually written through this bank; re-published whenever it joins a grid. */
    private final Map<Identifier, Long> persisted = new LinkedHashMap<>();

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("gridnode")
            .setIdlePowerUsage(1.0);

    public RegisterBankBlockEntity(BlockPos pos, BlockState state) {
        super(AE2Logistics.REGISTER_BANK_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            GridHelper.onFirstTick(this, be -> {
                be.mainNode.create(be.level, be.getBlockPos());
                be.pushToService();
            });
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        var list = output.childrenList("signals");
        for (var entry : persisted.entrySet()) {
            var signal = list.addChild();
            signal.putString("channel", entry.getKey().toString());
            signal.putLong("value", entry.getValue());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainNode.deserialize(input);
        persisted.clear();
        input.childrenList("signals").ifPresent(signals -> {
            for (var signal : signals) {
                var channel = signal.getString("channel").map(Identifier::tryParse).orElse(null);
                if (channel != null) {
                    persisted.put(channel, signal.getLongOr("value", 0L));
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

    @Nullable
    private SignalService service() {
        var node = mainNode.getNode();
        if (node == null || node.getGrid() == null) {
            return null;
        }
        return node.getGrid().getService(SignalService.class);
    }

    private void pushToService() {
        var service = service();
        if (service != null) {
            for (var entry : persisted.entrySet()) {
                service.setStored(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Grid-wide committed view when on a grid; this bank's own values otherwise. */
    public Map<Identifier, Long> signals() {
        var service = service();
        return service != null ? service.committed() : Collections.unmodifiableMap(persisted);
    }

    public long getSignal(Identifier channel) {
        var service = service();
        return service != null ? service.get(channel) : persisted.getOrDefault(channel, 0L);
    }

    public void setSignal(Identifier channel, long value) {
        if (value <= 0) {
            persisted.remove(channel);
        } else {
            persisted.put(channel, value);
        }
        var service = service();
        if (service != null) {
            service.setStored(channel, value);
        }
        setChanged();
    }
}
