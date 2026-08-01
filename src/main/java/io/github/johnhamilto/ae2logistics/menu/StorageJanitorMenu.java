package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.StorageJanitorBlockEntity;

public class StorageJanitorMenu extends AEBaseMenu {

    @Nullable
    private final StorageJanitorBlockEntity janitor;

    public final BlockPos pos;

    private int runningValue;
    private int doneValue;
    private int totalValue;
    private int processedValue;
    private int heldValue;

    public StorageJanitorMenu(int containerId, Inventory inventory, StorageJanitorBlockEntity janitor) {
        super(AE2Logistics.STORAGE_JANITOR_MENU.get(), containerId, inventory, janitor);
        this.janitor = janitor;
        this.pos = janitor.getBlockPos();
        addLiveSlots();
    }

    public StorageJanitorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.STORAGE_JANITOR_MENU.get(), containerId, inventory, null);
        this.janitor = null;
        this.pos = buffer.readBlockPos();
        addLiveSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, StorageJanitorBlockEntity janitor) {
        buffer.writeBlockPos(janitor.getBlockPos());
    }

    private void addLiveSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return janitor != null ? (janitor.running() ? 1 : 0) : runningValue;
            }

            @Override
            public void set(int value) {
                runningValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return janitor != null ? janitor.progressDone() : doneValue;
            }

            @Override
            public void set(int value) {
                doneValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return janitor != null ? janitor.progressTotal() : totalValue;
            }

            @Override
            public void set(int value) {
                totalValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return janitor != null
                        ? (int) Math.min(Integer.MAX_VALUE, janitor.processedTotal()) : processedValue;
            }

            @Override
            public void set(int value) {
                processedValue = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return janitor != null ? janitor.heldCount() : heldValue;
            }

            @Override
            public void set(int value) {
                heldValue = value;
            }
        });
    }

    public boolean running() {
        return runningValue != 0;
    }

    public int done() {
        return doneValue;
    }

    public int total() {
        return totalValue;
    }

    public int processed() {
        return processedValue;
    }

    public int heldCount() {
        return heldValue;
    }
}
