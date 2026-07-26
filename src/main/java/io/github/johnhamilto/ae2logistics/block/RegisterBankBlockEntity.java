package io.github.johnhamilto.ae2logistics.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    private final Map<ResourceLocation, Long> persisted = new LinkedHashMap<>();

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
        if (level != null && !level.isClientSide) {
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        var list = new ListTag();
        for (var entry : persisted.entrySet()) {
            var signal = new CompoundTag();
            signal.putString("channel", entry.getKey().toString());
            signal.putLong("value", entry.getValue());
            list.add(signal);
        }
        tag.put("signals", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        persisted.clear();
        for (Tag element : tag.getList("signals", Tag.TAG_COMPOUND)) {
            if (element instanceof CompoundTag signal) {
                var channel = ResourceLocation.tryParse(signal.getString("channel"));
                if (channel != null) {
                    persisted.put(channel, signal.getLong("value"));
                }
            }
        }
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
    public Map<ResourceLocation, Long> signals() {
        var service = service();
        return service != null ? service.committed() : Collections.unmodifiableMap(persisted);
    }

    public long getSignal(ResourceLocation channel) {
        var service = service();
        return service != null ? service.get(channel) : persisted.getOrDefault(channel, 0L);
    }

    public void setSignal(ResourceLocation channel, long value) {
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
