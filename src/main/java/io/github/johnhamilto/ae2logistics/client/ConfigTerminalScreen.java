package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigTerminalActionPayload;
import io.github.johnhamilto.ae2logistics.menu.ConfigTerminalMenu;

public class ConfigTerminalScreen extends AEBaseScreen<ConfigTerminalMenu> {

    private static final int LIST_Y = 34;
    private static final int DETAIL_Y = 128;

    private final ScrollingRowList list = new ScrollingRowList(8, 228, LIST_Y, LIST_Y + 93, 18);

    private AETextField searchBox;
    private AETextField priorityBox;
    private boolean changedOnly;
    private final List<Button> settingButtons = new ArrayList<>();
    private List<ConfigTerminalMenu.SettingLine> lastSettings = List.of();

    public ConfigTerminalScreen(ConfigTerminalMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        list.register(widgets, "scrollbar");
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new AETextField(style, font, leftPos + 10, topPos + 16, 118, 14);
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        addRenderableWidget(searchBox);

        addRenderableWidget(new AE2Button(leftPos + 132, topPos + 14, 22, 16,
                Component.literal("Rf"), b -> send(
                        ConfigTerminalActionPayload.ACTION_REFRESH, -1, "", 0)));
        addRenderableWidget(new AE2Button(leftPos + 158, topPos + 14, 34, 16,
                Component.literal("Snap"), b -> send(
                        ConfigTerminalActionPayload.ACTION_SNAPSHOT, -1, "", 0)));
        addRenderableWidget(new AE2Button(leftPos + 196, topPos + 14, 30, 16,
                Component.literal("Δ"), b -> {
                    changedOnly = !changedOnly;
                    b.setMessage(Component.literal(changedOnly ? "Δ!" : "Δ"));
                }));

        priorityBox = new AETextField(style, font, leftPos + 34, topPos + DETAIL_Y + 44, 42, 12);
        priorityBox.setBordered(false);
        priorityBox.setMaxLength(9);
        addRenderableWidget(priorityBox);
        addRenderableWidget(new AE2Button(leftPos + 80, topPos + DETAIL_Y + 42, 26, 14,
                Component.literal("Set"), b -> setPriority()));
        addRenderableWidget(new AE2Button(leftPos + 110, topPos + DETAIL_Y + 42, 36, 14,
                Component.literal("Copy"), b -> send(
                        ConfigTerminalActionPayload.ACTION_COPY, menu.selectedIndex, "", 0)));
        addRenderableWidget(new AE2Button(leftPos + 150, topPos + DETAIL_Y + 42, 38, 14,
                Component.literal("Paste"), b -> send(
                        ConfigTerminalActionPayload.ACTION_PASTE, menu.selectedIndex, "", 0)));
        addRenderableWidget(new AE2Button(leftPos + 192, topPos + DETAIL_Y + 42, 34, 14,
                Component.literal("All"), b -> send(
                        ConfigTerminalActionPayload.ACTION_PASTE_ALL, menu.selectedIndex, "", 0)));

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
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        list.setRowCount(filteredIndices().size());
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
            var button = new CycleButton(leftPos + 212, y, 14, 9,
                    Component.literal(">"), (b, dir) -> send(
                            ConfigTerminalActionPayload.ACTION_CYCLE, menu.selectedIndex, line.name(), dir));
            settingButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private List<Integer> filteredIndices() {
        var filter = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < menu.rows.size(); i++) {
            var row = menu.rows.get(i);
            if (changedOnly && row.diff() == io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.DIFF_SAME) {
                continue;
            }
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
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        list.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (!menu.clientNotice.isEmpty()) {
            guiGraphics.drawString(font, menu.clientNotice,
                    imageWidth - 10 - font.width(menu.clientNotice), 6, Palette.WAIT, false);
        }

        var indices = filteredIndices();
        if (indices.isEmpty()) {
            guiGraphics.drawString(font, "No configurable devices", 12, LIST_Y + 4,
                    Palette.HINT, false);
        }
        list.drawRows(guiGraphics, (g, index, y) -> {
            int rowIndex = indices.get(index);
            var row = menu.rows.get(rowIndex);
            if (rowIndex == menu.selectedIndex) {
                g.fill(9, y - 2, 218, y + 14, 0x332E6E9E);
            }
            var name = row.name();
            if (name.length() > 20) {
                name = name.substring(0, 19) + "..";
            }
            int nameColor = switch (row.diff()) {
                case io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.DIFF_CHANGED -> Palette.WAIT;
                case io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.DIFF_NEW -> Palette.VALUE;
                case io.github.johnhamilto.ae2logistics.config.ConfigDeviceIndex.DIFF_GONE -> Palette.ALERT;
                default -> Palette.ROW;
            };
            g.drawString(font, name, 30, y, nameColor, false);
            var info = row.hasPos()
                    ? row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ()
                    : "";
            if (row.hasPriority()) {
                info = info + "  p" + row.priority();
            }
            g.drawString(font, info, 30, y + 8, Palette.HINT, false);

            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(row.itemId()))
                    .orElse(null);
            if (item != null) {
                g.renderItem(new ItemStack(item), 10, y - 1);
            }
        });

        for (int i = 0; i < menu.detailSettings.size() && i < 4; i++) {
            var line = menu.detailSettings.get(i);
            var text = line.name() + " = " + line.value().toLowerCase(Locale.ROOT);
            if (text.length() > 33) {
                text = text.substring(0, 32) + "..";
            }
            guiGraphics.drawString(font, text, 10, DETAIL_Y + i * 10, Palette.LABEL, false);
        }
        if (menu.selectedIndex >= 0 && menu.detailSettings.isEmpty()) {
            guiGraphics.drawString(font, "no generic settings", 10, DETAIL_Y, Palette.HINT, false);
        }
        guiGraphics.drawString(font, "Pri", 10, DETAIL_Y + 46, Palette.LABEL, false);
        if (!menu.clientClipboardType.isEmpty()) {
            var clip = "clip: " + menu.clientClipboardType;
            if (clip.length() > 34) {
                clip = clip.substring(0, 33) + "..";
            }
            guiGraphics.drawString(font, clip, 10, DETAIL_Y + 58, Palette.HINT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int visibleIndex = list.rowAt(mouseX, mouseY, leftPos, topPos);
        if (visibleIndex >= 0) {
            var indices = filteredIndices();
            if (visibleIndex < indices.size()) {
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
        if (list.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
