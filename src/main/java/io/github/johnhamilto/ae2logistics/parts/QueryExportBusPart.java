package io.github.johnhamilto.ae2logistics.parts;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu;
import io.github.johnhamilto.ae2logistics.query.QueryService;

/**
 * Exports items matching a query (inline or {@code @named}) into the adjacent inventory.
 * The generalized tag-bus: one part, any predicate.
 */
public class QueryExportBusPart extends AEBasePart implements IGridTickable {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/query_export_bus"));

    private static final int ITEMS_PER_OPERATION = 8;

    private String source = "";
    private int movedLastOperation;

    public QueryExportBusPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(1.0)
                .addService(IGridTickable.class, this);
    }

    public String source() {
        return source;
    }

    public int movedLastOperation() {
        return movedLastOperation;
    }

    public boolean sourceValid() {
        return !source.isBlank()
                && io.github.johnhamilto.ae2logistics.query.CompiledQuery.compile(source) != null;
    }

    public void applyBusConfig(String newSource) {
        this.source = newSource.trim();
        getHost().markForSave();
    }

    @Nullable
    private IItemHandler adjacentItemHandler() {
        var host = getHost().getBlockEntity();
        if (host.getLevel() == null) {
            return null;
        }
        return host.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                host.getBlockPos().relative(getSide()), getSide().getOpposite());
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(10, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        movedLastOperation = 0;
        if (!getMainNode().isActive() || source.isBlank() || node.getGrid() == null) {
            return TickRateModulation.SLOWER;
        }
        var service = node.getGrid().getService(QueryService.class);
        var query = service.compiled(source);
        var handler = adjacentItemHandler();
        if (query == null || handler == null) {
            return TickRateModulation.SLOWER;
        }
        var context = service.context();
        var stacks = context.stacks();
        if (stacks == null) {
            return TickRateModulation.SLOWER;
        }

        // Collect first: extraction mutates the cached inventory we are iterating.
        var matching = new ArrayList<AEItemKey>();
        for (var entry : stacks) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey itemKey
                    && query.matches(itemKey, context)) {
                matching.add(itemKey);
                if (matching.size() >= 32) {
                    break;
                }
            }
        }

        var inv = node.getGrid().getStorageService().getInventory();
        var actionSource = new MachineSource(this);
        int budget = ITEMS_PER_OPERATION;
        for (var key : matching) {
            if (budget <= 0) {
                break;
            }
            long extracted = inv.extract(key, budget, Actionable.MODULATE, actionSource);
            if (extracted <= 0) {
                continue;
            }
            var rest = ItemHandlerHelper.insertItem(handler, key.toStack((int) extracted), false);
            if (!rest.isEmpty()) {
                inv.insert(key, rest.getCount(), Actionable.MODULATE, actionSource);
            }
            int moved = (int) extracted - rest.getCount();
            movedLastOperation += moved;
            budget -= moved;
        }
        return movedLastOperation > 0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    // --- part boilerplate ---

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 14);
        bch.addBox(5, 5, 14, 11, 11, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new QueryExportBusMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> QueryExportBusMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString("query", source);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        source = data.getString("query");
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
