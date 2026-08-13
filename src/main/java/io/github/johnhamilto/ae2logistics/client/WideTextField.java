package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;

/**
 * AETextField draws its background strip at most 128px wide (AE2's own fields are
 * never wider); this pre-fills the remaining middle segments for wider fields, and
 * the superclass then overdraws the left segment and both end caps on top.
 */
public class WideTextField extends AETextField {

    private static final Blitter BLITTER = Blitter.texture("guis/text_field.png", 128, 128);
    private static final int PADDING = 2;

    private final int fontPad;
    // EditBox's editable flag is only readable through AE2's access transformer.
    private boolean editableMirror = true;

    public WideTextField(ScreenStyle style, Font font, int x, int y, int width, int height) {
        super(style, font, x, y, width, height);
        this.fontPad = font.width("_");
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        this.editableMirror = editable;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
        if (isVisible()) {
            int yOffset = !editableMirror ? 12 : isFocused() ? 24 : 0;
            int left = getX() - PADDING;
            int right = getX() + width + PADDING + fontPad;
            int top = getY() - PADDING;
            for (int x = left + 1 + 126; x < right - 1; x += 126) {
                BLITTER.src(1, yOffset, Math.min(126, right - 1 - x), 12)
                        .dest(x, top)
                        .blit(guiGraphics);
            }
        }
        super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partial);
    }
}
