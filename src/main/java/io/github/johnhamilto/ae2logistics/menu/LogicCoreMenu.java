package io.github.johnhamilto.ae2logistics.menu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.LogicCoreBlockEntity;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

/**
 * Entry list plus a detail editor for the selected entry. Static configuration travels
 * in the menu-open buffer and is edited via {@link ConfigureCoreEntryPayload}; live
 * output values and the active mask stream through data slots; the single ghost slot is
 * bound server-side to the selected entry.
 */
public class LogicCoreMenu extends AEBaseMenu {

    public static final int ROWS = LogicCoreBlockEntity.ENTRIES;

    public static final int ROW_Y = 18;
    public static final int ROW_STEP = 13;
    public static final int GHOST_X = 10;
    public static final int GHOST_Y = 137;

    @Nullable
    private final LogicCoreBlockEntity core;

    public final BlockPos pos;
    public final byte[] types = new byte[ROWS];
    public final String[] outs = new String[ROWS];
    public final String[] inAs = new String[ROWS];
    public final String[] inBs = new String[ROWS];
    public final int[] ops = new int[ROWS];
    public final long[] valueAs = new long[ROWS];
    public final long[] valueBs = new long[ROWS];
    public final boolean[] flags = new boolean[ROWS];

    private final SimpleContainer ghost = new SimpleContainer(1);
    private final int ghostSlotIndex;
    private int selected;
    private final int[] valueHis = new int[ROWS];
    private final int[] valueLos = new int[ROWS];
    private int activeMask;

    public LogicCoreMenu(int containerId, Inventory inventory, LogicCoreBlockEntity core) {
        super(AE2Logistics.LOGIC_CORE_MENU.get(), containerId, inventory, core);
        this.core = core;
        this.pos = core.getBlockPos();
        for (int i = 0; i < ROWS; i++) {
            var entry = core.entry(i);
            var type = entry.type();
            types[i] = type == null ? -1 : (byte) type.ordinal();
            outs[i] = channelText(entry.writtenChannel());
            inAs[i] = channelText(entry.inARaw());
            inBs[i] = channelText(entry.inBRaw());
            ops[i] = entry.opRaw();
            valueAs[i] = entry.valueARaw();
            valueBs[i] = entry.valueBRaw();
            flags[i] = entry.flagRaw();
        }
        this.ghostSlotIndex = addGhostSlot();
        addPlayerSlots(inventory);
        addSyncSlots();
        refreshGhost();
    }

    public LogicCoreMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.LOGIC_CORE_MENU.get(), containerId, inventory, null);
        this.core = null;
        this.pos = buffer.readBlockPos();
        for (int i = 0; i < ROWS; i++) {
            types[i] = buffer.readByte();
            outs[i] = buffer.readUtf();
            inAs[i] = buffer.readUtf();
            inBs[i] = buffer.readUtf();
            ops[i] = buffer.readVarInt();
            valueAs[i] = buffer.readLong();
            valueBs[i] = buffer.readLong();
            flags[i] = buffer.readBoolean();
        }
        this.ghostSlotIndex = addGhostSlot();
        addPlayerSlots(inventory);
        addSyncSlots();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, LogicCoreBlockEntity core) {
        buffer.writeBlockPos(core.getBlockPos());
        for (int i = 0; i < ROWS; i++) {
            var entry = core.entry(i);
            var type = entry.type();
            buffer.writeByte(type == null ? -1 : type.ordinal());
            buffer.writeUtf(channelText(entry.writtenChannel()));
            buffer.writeUtf(channelText(entry.inARaw()));
            buffer.writeUtf(channelText(entry.inBRaw()));
            buffer.writeVarInt(entry.opRaw());
            buffer.writeLong(entry.valueARaw());
            buffer.writeLong(entry.valueBRaw());
            buffer.writeBoolean(entry.flagRaw());
        }
    }

    private static String channelText(@Nullable net.minecraft.resources.ResourceLocation id) {
        return id == null ? "" : id.toString();
    }

    private int addGhostSlot() {
        int index = slots.size();
        addSlot(new Slot(ghost, 0, 0, 0) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        }, SlotSemantics.CONFIG);
        return index;
    }

    private void addPlayerSlots(Inventory inventory) {
        createPlayerInventorySlots(inventory);
    }

    private void addSyncSlots() {
        for (int i = 0; i < ROWS; i++) {
            int index = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return core != null ? (int) (core.entryValue(index) >>> 32) : valueHis[index];
                }

                @Override
                public void set(int value) {
                    valueHis[index] = value;
                }
            });
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return core != null ? (int) core.entryValue(index) : valueLos[index];
                }

                @Override
                public void set(int value) {
                    valueLos[index] = value;
                }
            });
        }
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (core == null) {
                    return activeMask;
                }
                int mask = 0;
                for (int i = 0; i < ROWS; i++) {
                    if (core.entry(i).isActive()) {
                        mask |= 1 << i;
                    }
                }
                if (core.coreActive()) {
                    mask |= 1 << ROWS;
                }
                return mask;
            }

            @Override
            public void set(int value) {
                activeMask = value;
            }
        });
    }

    public long entryValue(int index) {
        return (long) valueHis[index] << 32 | valueLos[index] & 0xFFFFFFFFL;
    }

    public boolean entryActive(int index) {
        return (activeMask & 1 << index) != 0;
    }

    public boolean coreActive() {
        return (activeMask & 1 << ROWS) != 0;
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int slot) {
        if (slot >= 0 && slot < ROWS) {
            selected = slot;
            refreshGhost();
        }
    }

    /** Keeps the server-side snapshot arrays current after an APPLY so reopen data is fresh. */
    void noteApplied(ConfigureCoreEntryPayload payload) {
        int i = payload.slot();
        types[i] = payload.entryType();
        outs[i] = payload.out();
        inAs[i] = payload.inA();
        inBs[i] = payload.inB();
        ops[i] = payload.op();
        valueAs[i] = payload.valueA();
        valueBs[i] = payload.valueB();
        flags[i] = payload.flag();
    }

    private void refreshGhost() {
        if (core != null) {
            ghost.setItem(0, displayStack(core.entry(selected).watchedKey()));
        }
    }

    @Override
    public void broadcastChanges() {
        refreshGhost();
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == ghostSlotIndex) {
            if (core != null && types[selected] == LogicPartType.STOCK_SENSOR.ordinal()) {
                var key = keyFromCarried(getCarried());
                core.setEntryWatched(selected, key);
                ghost.setItem(0, displayStack(key));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Nullable
    private static GenericStack keyFromCarried(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        var unwrapped = GenericStack.fromItemStack(carried);
        if (unwrapped != null) {
            return new GenericStack(unwrapped.what(), 1);
        }
        var key = AEItemKey.of(carried);
        return key != null ? new GenericStack(key, 1) : null;
    }

    private static ItemStack displayStack(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        return ItemStack.EMPTY;
    }
}
