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
import io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload;
import io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu;

public class QueryExportBusScreen extends AbstractContainerScreen<QueryExportBusMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/logic_panel.png");

    private EditBox expressionBox;

    public QueryExportBusScreen(QueryExportBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        expressionBox = new EditBox(font, leftPos + 10, topPos + 28, 180, 16, Component.empty());
        expressionBox.setMaxLength(256);
        expressionBox.setValue(menu.source);
        addRenderableWidget(expressionBox);

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 60, 18).build());
    }

    private void apply() {
        PacketDistributor.sendToServer(new ConfigureQueryPartPayload(
                menu.pos, (byte) menu.side.ordinal(), "", expressionBox.getValue()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, "Query", 10, 18, 0x9BB2C4, false);
        if (!menu.source.isBlank() && !menu.sourceValid()) {
            guiGraphics.drawString(font, "query has a syntax error", 10, 54, 0xE0524E, false);
        }
        guiGraphics.drawString(font, "Exports matching items into the", 10, 74, 0x5A6B7C, false);
        guiGraphics.drawString(font, "inventory this bus faces.", 10, 86, 0x5A6B7C, false);
        guiGraphics.drawString(font, "Use @name for saved queries.", 10, 98, 0x5A6B7C, false);
        guiGraphics.drawString(font, "Moved: " + menu.movedLastOperation() + "/op", 78,
                imageHeight - 22, 0x5CE2FF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
