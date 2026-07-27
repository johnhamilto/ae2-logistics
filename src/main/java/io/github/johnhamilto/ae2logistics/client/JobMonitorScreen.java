package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload;
import io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu;

public class JobMonitorScreen extends AbstractContainerScreen<JobMonitorMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/logic_panel.png");

    private EditBox prefixBox;
    private EditBox stallBox;

    public JobMonitorScreen(JobMonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        prefixBox = new EditBox(font, leftPos + 78, topPos + 18, 112, 16, Component.empty());
        prefixBox.setMaxLength(24);
        prefixBox.setValue(menu.prefix);
        addRenderableWidget(prefixBox);

        stallBox = new EditBox(font, leftPos + 78, topPos + 40, 112, 16, Component.empty());
        stallBox.setMaxLength(3);
        stallBox.setValue(Integer.toString(menu.stallSeconds));
        addRenderableWidget(stallBox);

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 60, 18).build());
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, "Prefix", 10, 22, 0x9BB2C4, false);
        guiGraphics.drawString(font, "Stall (s)", 10, 44, 0x9BB2C4, false);

        guiGraphics.drawString(font, "Jobs: " + menu.activeJobs()
                + (menu.stalledJobs() > 0 ? "  (" + menu.stalledJobs() + " stalled)" : ""),
                10, 72, menu.stalledJobs() > 0 ? 0xE0524E : 0x5CE2FF, false);
        guiGraphics.drawString(font, "Pending items: " + menu.pendingItems(), 10, 86, 0x9BB2C4, false);

        guiGraphics.drawString(font, "Channels: " + menu.prefix + ":active / idle /", 10, 106,
                0x5A6B7C, false);
        guiGraphics.drawString(font, "stalled / pending, plus per-named-CPU", 10, 118, 0x5A6B7C, false);
        guiGraphics.drawString(font, menu.prefix + ":<name>/remaining and /stalled", 10, 130,
                0x5A6B7C, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
