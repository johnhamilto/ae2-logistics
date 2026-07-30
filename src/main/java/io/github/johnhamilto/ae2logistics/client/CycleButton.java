package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && isValidClickButton(button) && this.clicked(mouseX, mouseY)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            step.apply(this, button == 1 ? -1 : 1);
            return true;
        }
        return false;
    }
}
