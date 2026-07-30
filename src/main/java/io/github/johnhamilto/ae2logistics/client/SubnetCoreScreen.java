package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.block.SubnetCoreEntry;
import io.github.johnhamilto.ae2logistics.menu.ConfigureSubnetEntryPayload;
import io.github.johnhamilto.ae2logistics.menu.SubnetCoreMenu;

public class SubnetCoreScreen extends AEBaseScreen<SubnetCoreMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int MUTED = 0xA0A0A0;
    private static final int OK = 0x2E8B57;
    private static final int ALERT = 0xB33A36;

    private final List<AbstractWidget> detailWidgets = new ArrayList<>();

    private AETextField priorityBox;
    private int faceValue;

    public SubnetCoreScreen(SubnetCoreMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 252;
    }

    @Override
    protected void init() {
        super.init();
        rebuildDetail();
    }

    private void rebuildDetail() {
        for (var widget : detailWidgets) {
            removeWidget(widget);
        }
        detailWidgets.clear();
        int selected = menu.selected();
        int type = menu.types[selected];
        faceValue = menu.faces[selected];

        addDetail(new AE2Button(leftPos + 8, topPos + 123, 52, 14,
                Component.literal(typeName(type)), b -> cycleType()));

        if (type >= 0) {
            var entryType = SubnetCoreEntry.Type.byOrdinal(type);
            if (entryType.faceBound()) {
                addDetail(new AE2Button(leftPos + 64, topPos + 123, 30, 14,
                        Component.literal(faceName(faceValue)), b -> {
                            faceValue = (faceValue + 1) % Direction.values().length;
                            b.setMessage(Component.literal(faceName(faceValue)));
                        }));
            }

            priorityBox = new AETextField(style, font, leftPos + 98, topPos + 124, 40, 12);
            priorityBox.setBordered(false);
            priorityBox.setMaxLength(9);
            priorityBox.setValue(Integer.toString(menu.priorities[selected]));
            addDetail(priorityBox);
        } else {
            priorityBox = null;
        }

        addDetail(new AE2Button(leftPos + 156, topPos + 123, 36, 14,
                Component.literal("Apply"), b -> apply()));
    }

    private void addDetail(AbstractWidget widget) {
        detailWidgets.add(widget);
        addRenderableWidget(widget);
    }

    private void cycleType() {
        int selected = menu.selected();
        int current = menu.types[selected];
        int next = current + 1 >= SubnetCoreEntry.Type.values().length ? -1
                : current < 0 ? 0 : current + 1;
        menu.types[selected] = (byte) next;
        rebuildDetail();
    }

    static String typeName(int type) {
        if (type < 0) {
            return "empty";
        }
        return switch (SubnetCoreEntry.Type.byOrdinal(type)) {
            case STORAGE_BUS -> "storage";
            case IMPORT_BUS -> "import";
            case EXPORT_BUS -> "export";
            case UPLINK -> "uplink";
            case DOWNLINK -> "downlink";
        };
    }

    private static String faceName(int face) {
        return switch (Direction.values()[Math.floorMod(face, Direction.values().length)]) {
            case DOWN -> "D";
            case UP -> "U";
            case NORTH -> "N";
            case SOUTH -> "S";
            case WEST -> "W";
            case EAST -> "E";
        };
    }

    private void apply() {
        int selected = menu.selected();
        int type = menu.types[selected];
        int priority = 0;
        if (priorityBox != null) {
            try {
                priority = Integer.parseInt(priorityBox.getValue().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        menu.faces[selected] = (byte) faceValue;
        menu.priorities[selected] = priority;
        PacketDistributor.sendToServer(new ConfigureSubnetEntryPayload(menu.pos,
                ConfigureSubnetEntryPayload.ACTION_APPLY, (byte) selected, (byte) type,
                (byte) faceValue, priority));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (x >= 8 && x < 192 && y >= SubnetCoreMenu.ROW_Y
                && y < SubnetCoreMenu.ROW_Y + SubnetCoreMenu.ROWS * SubnetCoreMenu.ROW_STEP) {
            int row = (y - SubnetCoreMenu.ROW_Y) / SubnetCoreMenu.ROW_STEP;
            if (row != menu.selected()) {
                menu.setSelected(row);
                PacketDistributor.sendToServer(ConfigureSubnetEntryPayload.select(menu.pos, row));
                rebuildDetail();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        int selY = offsetY + SubnetCoreMenu.ROW_Y + menu.selected() * SubnetCoreMenu.ROW_STEP;
        guiGraphics.fill(offsetX + 7, selY - 1, offsetX + 193, selY + 11, 0x30405A78);
        Icon.SLOT_BACKGROUND.getBlitter()
                .dest(offsetX + SubnetCoreMenu.GHOST_X - 1, offsetY + SubnetCoreMenu.GHOST_Y - 1)
                .blit(guiGraphics);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, menu.coreActive() ? "online" : "offline", 160, 6,
                menu.coreActive() ? OK : ALERT, false);

        for (int i = 0; i < SubnetCoreMenu.ROWS; i++) {
            int y = SubnetCoreMenu.ROW_Y + i * SubnetCoreMenu.ROW_STEP + 1;
            int type = menu.types[i];
            boolean active = menu.entryActive(i);
            int labelColor = type < 0 ? MUTED : active ? LABEL : ALERT;
            guiGraphics.drawString(font, (i + 1) + " " + typeName(type), 10, y, labelColor, false);
            if (type >= 0) {
                var entryType = SubnetCoreEntry.Type.byOrdinal(type);
                if (entryType.faceBound()) {
                    guiGraphics.drawString(font, faceName(menu.faces[i]), 78, y, HINT, false);
                }
                guiGraphics.drawString(font, "p" + menu.priorities[i], 96, y, HINT, false);
            }
        }
        guiGraphics.drawString(font, "filter: click slot with held item", 32, 141, HINT, false);
    }
}
