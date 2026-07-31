package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.Scrollbar;

/**
 * A scrollable row container in the mod's house style: darker content well, AE2-chrome
 * gutter on the right, AE2 scrollbar thumb, mouse-wheel support. Screens draw the
 * chrome from {@code drawBG} (the thumb renders in the widget pass, which runs before
 * {@code drawFG} - anything opaque drawn there would bury it), rows from {@code drawFG},
 * and forward {@code mouseScrolled}. The scrollbar must be registered in the screen
 * CONSTRUCTOR (widgets are injected during init), positioned by the style doc.
 */
public final class ScrollingRowList {

    public interface RowRenderer {
        void render(GuiGraphics guiGraphics, int index, int y);
    }

    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    private final int left;
    private final int right;
    private final int top;
    private final int bottom;
    private final int step;
    private final int visibleRows;
    private int rowCount;

    /**
     * Coordinates are panel-local; {@code right} includes the 9px gutter column. The
     * actual bottom edge snaps DOWN from {@code maxBottom} so the rows sit with a
     * symmetric 2px pad instead of the division slack pooling at the bottom.
     */
    public ScrollingRowList(int left, int right, int top, int maxBottom, int step) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.step = step;
        // Content is rows*step minus the last row's trailing gap, plus 2px pads.
        this.visibleRows = (maxBottom - top - 3) / step;
        this.bottom = top + visibleRows * step + 3;
    }

    /** Call from the screen constructor; the style doc's widget entry positions the thumb. */
    public void register(WidgetContainer widgets, String id) {
        widgets.add(id, scrollbar);
    }

    public void setRowCount(int count) {
        this.rowCount = count;
        scrollbar.setRange(0, Math.max(0, count - visibleRows), 1);
    }

    public int visibleRows() {
        return visibleRows;
    }

    /** The well and gutter, in absolute coordinates - call from drawBG. */
    public void drawBackground(GuiGraphics guiGraphics, int offsetX, int offsetY) {
        if (rowCount == 0) {
            guiGraphics.fill(offsetX + left, offsetY + top, offsetX + right, offsetY + bottom,
                    Palette.WELL);
            return;
        }
        guiGraphics.fill(offsetX + left, offsetY + top, offsetX + right - 10, offsetY + bottom,
                Palette.WELL);
        guiGraphics.fill(offsetX + right - 9, offsetY + top, offsetX + right, offsetY + bottom,
                Palette.GUTTER_FRAME);
        guiGraphics.fill(offsetX + right - 8, offsetY + top + 1, offsetX + right - 1,
                offsetY + bottom - 1, Palette.GUTTER_FILL);
    }

    /** The visible row window, in panel-local coordinates - call from drawFG. */
    public void drawRows(GuiGraphics guiGraphics, RowRenderer renderer) {
        int first = scrollbar.getCurrentScroll();
        int shown = Math.min(rowCount - first, visibleRows);
        for (int i = 0; i < shown; i++) {
            renderer.render(guiGraphics, first + i, top + 2 + i * step);
        }
    }

    /** Wheel-scrolls when hovering the list area; returns whether the event was consumed. */
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY,
            int leftPos, int topPos, int imageWidth) {
        if (deltaY != 0 && rowCount > visibleRows
                && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos + top - 12 && mouseY < topPos + bottom) {
            scrollbar.setCurrentScroll(scrollbar.getCurrentScroll() - (int) Math.signum(deltaY));
            return true;
        }
        return false;
    }
}
