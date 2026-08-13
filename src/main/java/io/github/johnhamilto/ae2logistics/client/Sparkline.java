package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The house sparkline: dark plot panel, bright column-joined line. Shared by the
 * Tracer Terminal and anything else that charts a signal history (the planned
 * in-world trace panels draw the same shape).
 */
public final class Sparkline {

    public static final int PANEL = 0xFF1A1F27;
    public static final int LINE = 0xFF5CE2FF;
    public static final int AXIS = 0x9BB2C4;

    private Sparkline() {
    }

    /** Draws the panel and the line; callers add labels. No-op below two samples. */
    public static void draw(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height,
            long[] samples) {
        guiGraphics.fill(x, y, x + width, y + height, PANEL);
        if (samples.length < 2) {
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        long range = Math.max(1, max - min);

        int prevY = -1;
        for (int column = 0; column < width; column++) {
            int index = (int) ((long) column * (samples.length - 1) / (width - 1));
            int h = (int) ((samples[index] - min) * (height - 4) / range);
            int lineY = y + height - 2 - h;
            int top = prevY < 0 ? lineY : Math.min(lineY, prevY);
            int bottom = prevY < 0 ? lineY + 1 : Math.max(lineY + 1, prevY);
            guiGraphics.fill(x + column, top, x + column + 1, bottom, LINE);
            prevY = lineY;
        }
    }
}
