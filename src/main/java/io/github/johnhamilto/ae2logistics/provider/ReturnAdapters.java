package io.github.johnhamilto.ae2logistics.provider;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

/**
 * Buffered return path shared by the Provider P2P Tunnel and provider mesh endpoints:
 * machines insert into a 9-slot generic buffer (AE2's own return inventory), and the
 * owning part flushes the buffer into the real return {@link MEStorage} from its tick.
 * Network routing never runs inside a machine's own insert callstack - that is the
 * reentrancy class the old pass-through adapters needed hop guards for, and on
 * newer Minecraft (mc-26.1 branch) running MEStorage inside a transfer transaction
 * is an outright crash. Same architecture as AE2's own pattern provider; backpressure
 * moves to the buffer edge (nine slots of elasticity, then refusal).
 */
public final class ReturnAdapters {

    private ReturnAdapters() {
    }

    public static ReturnBuffer buffer(Runnable changeListener) {
        return new ReturnBuffer(changeListener);
    }

    public static final class ReturnBuffer {
        private final PatternProviderReturnInventory inv;
        private final GenericStackItemStorage itemHandler;
        private final GenericStackFluidStorage fluidHandler;

        private ReturnBuffer(Runnable changeListener) {
            this.inv = new PatternProviderReturnInventory(changeListener);
            this.itemHandler = new GenericStackItemStorage(inv);
            this.fluidHandler = new GenericStackFluidStorage(inv);
        }

        /** The surface addons bridge chemicals and other custom key types through. */
        public GenericInternalInventory genericInv() {
            return inv;
        }

        public IItemHandler itemHandler() {
            return itemHandler;
        }

        public IFluidHandler fluidHandler() {
            return fluidHandler;
        }

        /** Move buffered returns onward; tick-time only, outside any insert callstack. */
        public boolean flush(MEStorage target) {
            if (inv.isEmpty()) {
                return false;
            }
            return inv.injectIntoNetwork(target, IActionSource.empty(), stack -> {
            });
        }

        public boolean isEmpty() {
            return inv.isEmpty();
        }

        public void writeToNBT(CompoundTag tag, String name, HolderLookup.Provider registries) {
            if (!inv.isEmpty()) {
                inv.writeToChildTag(tag, name, registries);
            }
        }

        public void readFromNBT(CompoundTag tag, String name, HolderLookup.Provider registries) {
            inv.readFromChildTag(tag, name, registries);
        }

        public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
            inv.addDrops(drops, level, pos);
        }
    }
}
