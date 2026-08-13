package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import io.github.johnhamilto.ae2logistics.menu.SelectTracerChannelPayload;
import io.github.johnhamilto.ae2logistics.menu.TracerTerminalMenu;

public class TracerTerminalScreen extends AEBaseScreen<TracerTerminalMenu> {

    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int CHART_X = 10;
    private static final int CHART_Y = 122;
    private static final int CHART_W = 216;
    private static final int CHART_H = 46;

    private final ScrollingRowList list = new ScrollingRowList(8, 228, LIST_Y, LIST_Y + 100, ROW_HEIGHT);

    public TracerTerminalScreen(TracerTerminalMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        list.register(widgets, "scrollbar");
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
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        list.setRowCount(menu.entries.size());
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        list.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var entries = menu.entries;
        if (entries.isEmpty()) {
            guiGraphics.text(font, "No signals on this network", 12, LIST_Y + 4,
                    Palette.HINT, false);
        }

        list.drawRows(guiGraphics, (g, index, y) -> {
            var entry = entries.get(index);
            boolean selected = entry.channel().equals(menu.clientSelected);
            if (selected) {
                g.fill(9, y - 2, 218, y + ROW_HEIGHT - 2, 0x332E6E9E);
            }
            var name = entry.channel().toString();
            if (name.length() > 26) {
                name = "..." + name.substring(name.length() - 23);
            }
            g.text(font, name, 10, y, selected ? Palette.VALUE : Palette.ROW, false);
            var value = fmt(entry.value());
            g.text(font, value, 216 - font.width(value), y, Palette.LABEL, false);
        });

        renderChart(guiGraphics);
    }

    private void renderChart(GuiGraphicsExtractor guiGraphics) {
        if (menu.clientSelected == null) {
            guiGraphics.text(font, "Select a channel for history", CHART_X,
                    CHART_Y + CHART_H / 2, Palette.HINT, false);
            return;
        }
        var samples = menu.samples;
        guiGraphics.text(font, menu.clientSelected.toString(), CHART_X, CHART_Y - 10,
                Palette.VALUE, false);
        if (samples.length < 2) {
            guiGraphics.text(font, "Collecting samples...", CHART_X, CHART_Y + CHART_H / 2,
                    Palette.HINT, false);
            return;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }

        Sparkline.draw(guiGraphics, CHART_X, CHART_Y, CHART_W, CHART_H, samples);
        guiGraphics.text(font, fmt(max), CHART_X + CHART_W - font.width(fmt(max)) - 2,
                CHART_Y + 2, Sparkline.AXIS, false);
        guiGraphics.text(font, fmt(min), CHART_X + CHART_W - font.width(fmt(min)) - 2,
                CHART_Y + CHART_H - 10, Sparkline.AXIS, false);
        var latest = "now: " + fmt(samples[samples.length - 1]);
        guiGraphics.text(font, latest, CHART_X + 2, CHART_Y + CHART_H + 4, Palette.ROW, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = list.rowAt(event.x(), event.y(), leftPos, topPos);
        if (index >= 0 && index < menu.entries.size()) {
            var channel = menu.entries.get(index).channel();
            var next = channel.equals(menu.clientSelected) ? "" : channel.toString();
            ClientPacketDistributor.sendToServer(new SelectTracerChannelPayload(menu.containerId, next));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
