package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload;
import io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu;

public class JobMonitorScreen extends AEBaseScreen<JobMonitorMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int VALUE = 0x2E6E9E;
    private static final int ALERT = 0xB33A36;

    private AETextField prefixBox;
    private AETextField stallBox;

    public JobMonitorScreen(JobMonitorMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        prefixBox = new AETextField(style, font, leftPos + 78, topPos + 18, 112, 16);
        prefixBox.setBordered(false);
        prefixBox.setMaxLength(24);
        prefixBox.setValue(menu.prefix);
        addRenderableWidget(prefixBox);

        stallBox = new AETextField(style, font, leftPos + 78, topPos + 40, 112, 16);
        stallBox.setBordered(false);
        stallBox.setMaxLength(3);
        stallBox.setValue(Integer.toString(menu.stallSeconds));
        addRenderableWidget(stallBox);

    }

    private String snapshot() {
        return prefixBox.getValue() + '\0' + stallBox.getValue();
    }

    private final AutoApply autoApply = new AutoApply();

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var current = snapshot();
        if (autoApply.shouldSend(current,
                getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            apply();
            autoApply.sent(current);
        }
    }

    @Override
    public void removed() {
        if (autoApply.dirty(snapshot())) {
            apply();
        }
        super.removed();
    }

    private void apply() {
        int stallSeconds;
        try {
            stallSeconds = Integer.parseInt(stallBox.getValue().trim());
        } catch (NumberFormatException e) {
            stallSeconds = 10;
        }
        PacketDistributor.sendToServer(new ConfigureJobMonitorPayload(
                menu.pos, (byte) menu.side.ordinal(), prefixBox.getValue(), stallSeconds));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Prefix", 10, 22, LABEL, false);
        guiGraphics.drawString(font, "Stall (s)", 10, 44, LABEL, false);

        guiGraphics.drawString(font, "Jobs: " + menu.activeJobs()
                + (menu.stalledJobs() > 0 ? "  (" + menu.stalledJobs() + " stalled)" : ""),
                10, 72, menu.stalledJobs() > 0 ? ALERT : VALUE, false);
        guiGraphics.drawString(font, "Pending items: " + menu.pendingItems(), 10, 86, LABEL, false);

        guiGraphics.drawString(font, "Channels: " + menu.prefix + ":active / idle /", 10, 106,
                HINT, false);
        guiGraphics.drawString(font, "stalled / pending, plus per-named-CPU", 10, 118, HINT, false);
        guiGraphics.drawString(font, menu.prefix + ":<name>/remaining and /stalled", 10, 130,
                HINT, false);
    }
}
