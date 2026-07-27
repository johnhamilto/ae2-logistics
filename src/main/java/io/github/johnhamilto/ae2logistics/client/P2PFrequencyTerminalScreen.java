package io.github.johnhamilto.ae2logistics.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;

public class P2PFrequencyTerminalScreen extends AbstractContainerScreen<P2PFrequencyTerminalMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/tracer_panel.png");

    private static final int LIST_X = 10;
    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 9;

    private int scroll;
    private short targetFrequency;
    private boolean hasTarget;
    @Nullable
    private P2PFrequencyTerminalMenu.Row selected;
    private EditBox nameBox;

    public P2PFrequencyTerminalScreen(P2PFrequencyTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + 10, topPos + imageHeight - 46, 130, 14, Component.empty());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(leftPos + 146, topPos + imageHeight - 48, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Mark target"), b -> markTarget())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 108, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Retune to target"), b -> retuneSelected())
                .bounds(leftPos + 126, topPos + imageHeight - 26, 100, 18).build());
    }

    private void markTarget() {
        if (selected != null) {
            targetFrequency = selected.frequency();
            hasTarget = true;
        }
    }

    private void retuneSelected() {
        if (selected != null && hasTarget) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RETUNE, selected.pos(), selected.side(), targetFrequency, ""));
        }
    }

    private void rename() {
        if (selected != null) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    selected.frequency(), nameBox.getValue()));
        }
    }

    private static String freqLabel(P2PFrequencyTerminalMenu.Row row) {
        var hex = String.format("%04X", row.frequency() & 0xFFFF);
        return row.name().isBlank() ? hex : row.name() + " (" + hex + ")";
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);

        var rows = menu.rows;
        int max = Math.max(0, rows.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);

        if (rows.isEmpty()) {
            guiGraphics.drawString(font, "No P2P tunnels on this network", LIST_X, LIST_Y + 4, 0x5A6B7C, false);
        }

        for (int i = 0; i < VISIBLE_ROWS && scroll + i < rows.size(); i++) {
            var row = rows.get(scroll + i);
            int y = LIST_Y + i * ROW_HEIGHT;
            boolean isSelected = row.equals(selected);
            if (isSelected) {
                guiGraphics.fill(LIST_X - 2, y - 1, LIST_X + 218, y + ROW_HEIGHT - 2, 0x3325C0E0);
            }
            boolean isTarget = hasTarget && row.frequency() == targetFrequency;

            var label = freqLabel(row);
            if (label.length() > 16) {
                label = label.substring(0, 15) + "..";
            }
            guiGraphics.drawString(font, label, LIST_X, y,
                    isTarget ? 0xF5C542 : isSelected ? 0x5CE2FF : 0xC7D3DE, false);

            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId()));
            var typeName = item.getDescription().getString().replace(" P2P Tunnel", "");
            if (typeName.length() > 8) {
                typeName = typeName.substring(0, 8);
            }
            guiGraphics.drawString(font, typeName, LIST_X + 92, y, 0x9BB2C4, false);
            guiGraphics.drawString(font, row.output() ? "OUT" : "IN", LIST_X + 142, y,
                    row.output() ? 0xE08A4E : 0x6FDB6F, false);
            guiGraphics.drawString(font,
                    row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ(),
                    LIST_X + 166, y, 0x8A9AA8, false);
        }

        if (hasTarget) {
            guiGraphics.drawString(font, "Target: " + String.format("%04X", targetFrequency & 0xFFFF),
                    10, imageHeight - 58, 0xF5C542, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (localX >= LIST_X - 2 && localX < LIST_X + 218 && localY >= LIST_Y - 1
                && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int row = scroll + (localY - LIST_Y + 1) / ROW_HEIGHT;
            if (row >= 0 && row < menu.rows.size()) {
                selected = menu.rows.get(row);
                nameBox.setValue(selected.name());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, menu.rows.size() - VISIBLE_ROWS);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY));
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
