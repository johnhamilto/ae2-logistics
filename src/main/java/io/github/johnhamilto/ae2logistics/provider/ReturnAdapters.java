package io.github.johnhamilto.ae2logistics.provider;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/**
 * Insert-only faces of a provider return path: machines and pipes see a generic
 * inventory (the capability surface addons bridge chemicals and other custom key
 * types through) plus plain item/fluid handlers, all forwarding into one return
 * {@link MEStorage}. Shared by the Provider P2P Tunnel and provider mesh endpoints.
 */
public final class ReturnAdapters {

    private ReturnAdapters() {
    }

    /** Insert-only pass-through; slot indices are meaningless for a forwarding view. */
    public static GenericInternalInventory genericInv(MEStorage returnPath) {
        return new GenericInternalInventory() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            @Nullable
            public GenericStack getStack(int slot) {
                return null;
            }

            @Override
            @Nullable
            public AEKey getKey(int slot) {
                return null;
            }

            @Override
            public long getAmount(int slot) {
                return 0;
            }

            @Override
            public long getMaxAmount(AEKey key) {
                return 1_000_000_000L;
            }

            @Override
            public long getCapacity(AEKeyType type) {
                return 1_000_000_000L;
            }

            @Override
            public boolean canInsert() {
                return true;
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public void setStack(int slot, GenericStack stack) {
            }

            @Override
            public boolean isSupportedType(AEKeyType type) {
                return true;
            }

            @Override
            public boolean isAllowedIn(int slot, AEKey key) {
                return true;
            }

            @Override
            public long insert(int slot, AEKey what, long amount, Actionable mode) {
                return returnPath.insert(what, amount, mode, IActionSource.empty());
            }

            @Override
            public long extract(int slot, AEKey what, long amount, Actionable mode) {
                return 0;
            }

            @Override
            public void beginBatch() {
            }

            @Override
            public void endBatch() {
            }

            @Override
            public void endBatchSuppressed() {
            }

            @Override
            public void onChange() {
            }
        };
    }

    /** Machines return results via the plain item capability. */
    public static IItemHandler itemHandler(MEStorage returnPath) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                var key = AEItemKey.of(stack);
                if (key == null) {
                    return stack;
                }
                long inserted = returnPath.insert(key, stack.getCount(),
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                        IActionSource.empty());
                return inserted >= stack.getCount() ? ItemStack.EMPTY
                        : stack.copyWithCount(stack.getCount() - (int) inserted);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }
        };
    }

    /** Machines return results via the plain fluid capability. */
    public static IFluidHandler fluidHandler(MEStorage returnPath) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return 16000;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return true;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                var key = AEFluidKey.of(resource);
                if (key == null) {
                    return 0;
                }
                return (int) returnPath.insert(key, resource.getAmount(),
                        action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                        IActionSource.empty());
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }
}
