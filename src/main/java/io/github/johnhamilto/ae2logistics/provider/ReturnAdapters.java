package io.github.johnhamilto.ae2logistics.provider;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackFluidHandler;
import appeng.helpers.externalstorage.GenericStackItemHandler;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

/**
 * Buffered return path shared by the Provider P2P Tunnel and provider mesh endpoints:
 * machines insert into a 9-slot generic buffer (AE2's own return inventory, safe
 * inside transfer transactions via its journal), and the owning part flushes the
 * buffer into the real return {@link MEStorage} from its tick - NEVER inside a
 * transaction, because MEStorage routing can reach AE2 storage facades that open
 * root transactions of their own. Same architecture as AE2's pattern provider;
 * backpressure moves to the buffer edge (nine slots of elasticity, then refusal).
 */
public final class ReturnAdapters {

    private ReturnAdapters() {
    }

    public static ReturnBuffer buffer(Runnable changeListener) {
        return new ReturnBuffer(changeListener);
    }

    public static final class ReturnBuffer {
        private final PatternProviderReturnInventory inv;
        private final GenericStackItemHandler itemHandler;
        private final GenericStackFluidHandler fluidHandler;

        private ReturnBuffer(Runnable changeListener) {
            this.inv = new PatternProviderReturnInventory(changeListener);
            this.itemHandler = new GenericStackItemHandler(inv);
            this.fluidHandler = new GenericStackFluidHandler(inv);
        }

        /** The surface addons bridge chemicals and other custom key types through. */
        public GenericInternalInventory genericInv() {
            return inv;
        }

        public ResourceHandler<ItemResource> itemHandler() {
            return itemHandler;
        }

        public ResourceHandler<FluidResource> fluidHandler() {
            return fluidHandler;
        }

        /** Move buffered returns onward; tick-time only, never inside a transaction. */
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

        public void writeToNBT(ValueOutput output, String name) {
            if (!inv.isEmpty()) {
                inv.writeToTag(output.childrenList(name));
            }
        }

        public void readFromNBT(ValueInput input, String name) {
            inv.readFromTag(input.childrenListOrEmpty(name));
        }

        public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
            inv.addDrops(drops, level, pos);
        }
    }
}
