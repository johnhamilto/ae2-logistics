package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AE2Button;

/** An AE2Button for cycling settings: left-click steps forward, right-click backward. */
public class CycleButton extends AE2Button {

    public interface Step {
        void apply(CycleButton button, int direction);
    }

    private final Step step;

    public CycleButton(int x, int y, int width, int height, Component label, Step step) {
        super(x, y, width, height, label, b -> ((CycleButton) b).step.apply((CycleButton) b, 1));
        this.step = step;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == 0 || buttonInfo.button() == 1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.active && this.visible && isValidClickButton(event.buttonInfo())
                && this.isMouseOver(event.x(), event.y())) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            // AEBaseScreen re-dispatches right-clicks to widgets as button 0; the
            // screen flag is the only trace of the real button.
            boolean reverse = event.button() == 1
                    || Minecraft.getInstance().screen instanceof AEBaseScreen<?> screen
                            && screen.isHandlingRightClick();
            step.apply(this, reverse ? -1 : 1);
            return true;
        }
        return false;
    }
}
