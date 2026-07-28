package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.ConfigTerminalActionPayload;
import io.github.johnhamilto.ae2logistics.menu.ConfigTerminalMenu;

public class ConfigTerminalScreen extends AbstractContainerScreen<ConfigTerminalMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/tracer_panel.png");

    private static final int LIST_X = 10;
    private static final int LIST_Y = 34;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 5;
    private static final int DETAIL_Y = 128;

    private EditBox searchBox;
    private EditBox priorityBox;
    private int scroll;
    private final List<Button> settingButtons = new ArrayList<>();
    private List<ConfigTerminalMenu.SettingLine> lastSettings = List.of();

    public ConfigTerminalScreen(ConfigTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 10, topPos + 16, 118, 14, Component.empty());
        searchBox.setMaxLength(64);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> send(
                ConfigTerminalActionPayload.ACTION_REFRESH, -1, "", 0))
                .bounds(leftPos + 134, topPos + 14, 48, 16).build());

        priorityBox = new EditBox(font, leftPos + 34, topPos + DETAIL_Y + 44, 42, 12, Component.empty());
        priorityBox.setMaxLength(9);
        addRenderableWidget(priorityBox);
        addRenderableWidget(Button.builder(Component.literal("Set"), b -> setPriority())
                .bounds(leftPos + 80, topPos + DETAIL_Y + 42, 26, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> send(
                ConfigTerminalActionPayload.ACTION_COPY, menu.selectedIndex, "", 0))
                .bounds(leftPos + 110, topPos + DETAIL_Y + 42, 36, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Paste"), b -> send(
                ConfigTerminalActionPayload.ACTION_PASTE, menu.selectedIndex, "", 0))
                .bounds(leftPos + 150, topPos + DETAIL_Y + 42, 38, 14).build());
        addRenderableWidget(Button.builder(Component.literal("All"), b -> send(
                ConfigTerminalActionPayload.ACTION_PASTE_ALL, menu.selectedIndex, "", 0))
                .bounds(leftPos + 192, topPos + DETAIL_Y + 42, 34, 14).build());

        lastSettings = List.of();
        rebuildSettingButtons();
    }

    private void send(byte action, int index, String text, long value) {
        PacketDistributor.sendToServer(new ConfigTerminalActionPayload(
                menu.containerId, action, index, text, value));
    }

    private void setPriority() {
        long value;
        try {
            value = Long.parseLong(priorityBox.getValue().trim());
        } catch (NumberFormatException e) {
            return;
        }
        send(ConfigTerminalActionPayload.ACTION_SET_PRIORITY, menu.selectedIndex, "", value);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!menu.detailSettings.equals(lastSettings)) {
            lastSettings = List.copyOf(menu.detailSettings);
            rebuildSettingButtons();
        }
    }

    private void rebuildSettingButtons() {
        for (var button : settingButtons) {
            removeWidget(button);
        }
        settingButtons.clear();
        for (int i = 0; i < menu.detailSettings.size() && i < 4; i++) {
            var line = menu.detailSettings.get(i);
            int y = topPos + DETAIL_Y + i * 10;
            var button = Button.builder(Component.literal(">"), b -> send(
                    ConfigTerminalActionPayload.ACTION_CYCLE, menu.selectedIndex, line.name(), 0))
                    .bounds(leftPos + 212, y, 14, 9).build();
            settingButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private List<Integer> filteredIndices() {
        var filter = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < menu.rows.size(); i++) {
            var row = menu.rows.get(i);
            if (filter.isEmpty()
                    || row.name().toLowerCase(Locale.ROOT).contains(filter)
                    || row.itemId().toLowerCase(Locale.ROOT).contains(filter)
                    || row.summary().toLowerCase(Locale.ROOT).contains(filter)) {
                indices.add(i);
            }
        }
        return indices;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        if (!menu.clientNotice.isEmpty()) {
            guiGraphics.drawString(font, menu.clientNotice,
                    imageWidth - 10 - font.width(menu.clientNotice), 6, 0xF5C542, false);
        }

        var indices = filteredIndices();
        int max = Math.max(0, indices.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);

        for (int i = 0; i < VISIBLE_ROWS && scroll + i < indices.size(); i++) {
            int rowIndex = indices.get(scroll + i);
            var row = menu.rows.get(rowIndex);
            int y = LIST_Y + i * ROW_HEIGHT;
            if (rowIndex == menu.selectedIndex) {
                guiGraphics.fill(LIST_X - 2, y - 1, LIST_X + 218, y + ROW_HEIGHT - 2, 0x3325C0E0);
            }
            var name = row.name();
            if (name.length() > 20) {
                name = name.substring(0, 19) + "..";
            }
            guiGraphics.drawString(font, name, LIST_X + 20, y, 0xC7D3DE, false);
            var info = row.hasPos()
                    ? row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ()
                    : "";
            if (row.hasPriority()) {
                info = info + "  p" + row.priority();
            }
            guiGraphics.drawString(font, info, LIST_X + 20, y + 8, 0x8A9AA8, false);
        }
        if (indices.isEmpty()) {
            guiGraphics.drawString(font, "No configurable devices", LIST_X, LIST_Y + 4, 0x5A6B7C, false);
        }

        for (int i = 0; i < menu.detailSettings.size() && i < 4; i++) {
            var line = menu.detailSettings.get(i);
            var text = line.name() + " = " + line.value().toLowerCase(Locale.ROOT);
            if (text.length() > 33) {
                text = text.substring(0, 32) + "..";
            }
            guiGraphics.drawString(font, text, 10, DETAIL_Y + i * 10, 0x9BB2C4, false);
        }
        if (menu.selectedIndex >= 0 && menu.detailSettings.isEmpty()) {
            guiGraphics.drawString(font, "no generic settings", 10, DETAIL_Y, 0x5A6B7C, false);
        }
        guiGraphics.drawString(font, "Pri", 10, DETAIL_Y + 46, 0x9BB2C4, false);
        if (!menu.clientClipboardType.isEmpty()) {
            var clip = "clip: " + menu.clientClipboardType;
            if (clip.length() > 34) {
                clip = clip.substring(0, 33) + "..";
            }
            guiGraphics.drawString(font, clip, 10, DETAIL_Y + 58, 0x5A6B7C, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        var indices = filteredIndices();
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < indices.size(); i++) {
            var row = menu.rows.get(indices.get(scroll + i));
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(row.itemId()))
                    .orElse(null);
            if (item != null) {
                guiGraphics.renderItem(new ItemStack(item), leftPos + LIST_X,
                        topPos + LIST_Y + i * ROW_HEIGHT);
            }
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (localX >= LIST_X - 2 && localX < LIST_X + 218 && localY >= LIST_Y - 1
                && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            var indices = filteredIndices();
            int visibleIndex = scroll + (localY - LIST_Y + 1) / ROW_HEIGHT;
            if (visibleIndex >= 0 && visibleIndex < indices.size()) {
                int rowIndex = indices.get(visibleIndex);
                send(ConfigTerminalActionPayload.ACTION_SELECT, rowIndex, "", 0);
                menu.selectedIndex = rowIndex;
                var row = menu.rows.get(rowIndex);
                if (row.hasPriority() && priorityBox != null) {
                    priorityBox.setValue(Integer.toString(row.priority()));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, filteredIndices().size() - VISIBLE_ROWS);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY));
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }
}
