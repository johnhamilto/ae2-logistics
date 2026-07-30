package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import io.github.johnhamilto.ae2logistics.menu.SelectTracerChannelPayload;
import io.github.johnhamilto.ae2logistics.menu.TracerTerminalMenu;

public class TracerTerminalScreen extends AEBaseScreen<TracerTerminalMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int ROW = 0x505A62;
    private static final int SELECTED = 0x2E6E9E;

    private static final int LIST_X = 10;
    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 8;
    private static final int CHART_X = 10;
    private static final int CHART_Y = 122;
    private static final int CHART_W = 216;
    private static final int CHART_H = 46;

    private int scroll;

    public TracerTerminalScreen(TracerTerminalMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 236;
        this.imageHeight = 190;
    }

    private static String fmt(long value) {
        if (value < 10_000) {
            return Long.toString(value);
        }
        if (value < 10_000_000L) {
            return "%.1fK".formatted(value / 1_000.0);
        }
        if (value < 10_000_000_000L) {
            return "%.1fM".formatted(value / 1_000_000.0);
        }
        return "%.1fG".formatted(value / 1_000_000_000.0);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var entries = menu.entries;
        int max = Math.max(0, entries.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);

        if (entries.isEmpty()) {
            guiGraphics.drawString(font, "No signals on this network", LIST_X, LIST_Y + 4, HINT, false);
        }

        for (int i = 0; i < VISIBLE_ROWS && scroll + i < entries.size(); i++) {
            var entry = entries.get(scroll + i);
            int y = LIST_Y + i * ROW_HEIGHT;
            boolean selected = entry.channel().equals(menu.clientSelected);
            if (selected) {
                guiGraphics.fill(LIST_X - 2, y - 1, LIST_X + 218, y + ROW_HEIGHT - 2, 0x332E6E9E);
            }
            var name = entry.channel().toString();
            if (name.length() > 26) {
                name = "..." + name.substring(name.length() - 23);
            }
            guiGraphics.drawString(font, name, LIST_X, y, selected ? SELECTED : ROW, false);
            var value = fmt(entry.value());
            guiGraphics.drawString(font, value, LIST_X + 216 - font.width(value), y, LABEL, false);
        }

        if (entries.size() > VISIBLE_ROWS) {
            guiGraphics.drawString(font,
                    (scroll + 1) + "-" + Math.min(entries.size(), scroll + VISIBLE_ROWS) + "/" + entries.size(),
                    LIST_X + 180, 6, HINT, false);
        }

        renderChart(guiGraphics);
    }

    private void renderChart(GuiGraphics guiGraphics) {
        if (menu.clientSelected == null) {
            guiGraphics.drawString(font, "Select a channel for history", CHART_X, CHART_Y + CHART_H / 2,
                    HINT, false);
            return;
        }
        var samples = menu.samples;
        guiGraphics.drawString(font, menu.clientSelected.toString(), CHART_X, CHART_Y - 10, SELECTED, false);
        if (samples.length < 2) {
            guiGraphics.drawString(font, "Collecting samples...", CHART_X, CHART_Y + CHART_H / 2, HINT, false);
            return;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        long range = Math.max(1, max - min);

        // The plot stays a dark panel by design; the bright sparkline reads like a screen.
        guiGraphics.fill(CHART_X, CHART_Y, CHART_X + CHART_W, CHART_Y + CHART_H, 0xFF1A1F27);
        int prevY = -1;
        for (int x = 0; x < CHART_W; x++) {
            int index = (int) ((long) x * (samples.length - 1) / (CHART_W - 1));
            int h = (int) ((samples[index] - min) * (CHART_H - 4) / range);
            int y = CHART_Y + CHART_H - 2 - h;
            int top = prevY < 0 ? y : Math.min(y, prevY);
            int bottom = prevY < 0 ? y + 1 : Math.max(y + 1, prevY);
            guiGraphics.fill(CHART_X + x, top, CHART_X + x + 1, bottom, 0xFF5CE2FF);
            prevY = y;
        }

        guiGraphics.drawString(font, fmt(max), CHART_X + CHART_W - font.width(fmt(max)) - 2, CHART_Y + 2,
                0x9BB2C4, false);
        guiGraphics.drawString(font, fmt(min), CHART_X + CHART_W - font.width(fmt(min)) - 2,
                CHART_Y + CHART_H - 10, 0x9BB2C4, false);
        var latest = "now: " + fmt(samples[samples.length - 1]);
        guiGraphics.drawString(font, latest, CHART_X + 2, CHART_Y + CHART_H + 4, ROW, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (localX >= LIST_X - 2 && localX < LIST_X + 218 && localY >= LIST_Y - 1
                && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int row = scroll + (localY - LIST_Y + 1) / ROW_HEIGHT;
            if (row >= 0 && row < menu.entries.size()) {
                var channel = menu.entries.get(row).channel();
                var next = channel.equals(menu.clientSelected) ? "" : channel.toString();
                PacketDistributor.sendToServer(new SelectTracerChannelPayload(menu.containerId, next));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, menu.entries.size() - VISIBLE_ROWS);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY));
        return true;
    }
}
