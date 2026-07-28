package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.SubnetCoreEntry;
import io.github.johnhamilto.ae2logistics.menu.ConfigureSubnetEntryPayload;
import io.github.johnhamilto.ae2logistics.menu.SubnetCoreMenu;

public class SubnetCoreScreen extends AbstractContainerScreen<SubnetCoreMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/core_panel.png");

    private final List<AbstractWidget> detailWidgets = new ArrayList<>();

    private EditBox priorityBox;
    private int faceValue;

    public SubnetCoreScreen(SubnetCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 240;
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

        addDetail(Button.builder(Component.literal(typeName(type)), b -> cycleType())
                .bounds(leftPos + 8, topPos + 123, 52, 14).build());

        if (type >= 0) {
            var entryType = SubnetCoreEntry.Type.byOrdinal(type);
            if (entryType.faceBound()) {
                addDetail(Button.builder(Component.literal(faceName(faceValue)), b -> {
                    faceValue = (faceValue + 1) % Direction.values().length;
                    b.setMessage(Component.literal(faceName(faceValue)));
                }).bounds(leftPos + 64, topPos + 123, 30, 14).build());
            }

            priorityBox = new EditBox(font, leftPos + 98, topPos + 124, 40, 12, Component.empty());
            priorityBox.setMaxLength(9);
            priorityBox.setValue(Integer.toString(menu.priorities[selected]));
            addDetail(priorityBox);
        } else {
            priorityBox = null;
        }

        addDetail(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 156, topPos + 123, 36, 14).build());
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        int selY = topPos + SubnetCoreMenu.ROW_Y + menu.selected() * SubnetCoreMenu.ROW_STEP;
        guiGraphics.fill(leftPos + 7, selY - 1, leftPos + 193, selY + 11, 0x30FFFFFF);

        slotFrame(guiGraphics, SubnetCoreMenu.GHOST_X, SubnetCoreMenu.GHOST_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(guiGraphics, SubnetCoreMenu.INV_X + col * 18, SubnetCoreMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(guiGraphics, SubnetCoreMenu.INV_X + col * 18, SubnetCoreMenu.HOTBAR_Y);
        }
    }

    private void slotFrame(GuiGraphics guiGraphics, int slotX, int slotY) {
        int x = leftPos + slotX - 1;
        int y = topPos + slotY - 1;
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF1A1F27);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2C333F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, menu.coreActive() ? "online" : "offline", 160, 6,
                menu.coreActive() ? 0x6FDB6F : 0xE0524E, false);

        for (int i = 0; i < SubnetCoreMenu.ROWS; i++) {
            int y = SubnetCoreMenu.ROW_Y + i * SubnetCoreMenu.ROW_STEP + 1;
            int type = menu.types[i];
            boolean active = menu.entryActive(i);
            int labelColor = type < 0 ? 0x4A5866 : active ? 0xE0E6EB : 0xE0524E;
            guiGraphics.drawString(font, (i + 1) + " " + typeName(type), 10, y, labelColor, false);
            if (type >= 0) {
                var entryType = SubnetCoreEntry.Type.byOrdinal(type);
                if (entryType.faceBound()) {
                    guiGraphics.drawString(font, faceName(menu.faces[i]), 78, y, 0x8A9AA8, false);
                }
                guiGraphics.drawString(font, "p" + menu.priorities[i], 96, y, 0x8A9AA8, false);
            }
        }
        guiGraphics.drawString(font, "filter: click slot with held item", 32, 141, 0x5A6B7C, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
