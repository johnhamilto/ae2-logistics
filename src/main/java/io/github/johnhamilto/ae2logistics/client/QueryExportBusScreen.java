package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload;
import io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu;

public class QueryExportBusScreen extends AEBaseScreen<QueryExportBusMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int VALUE = 0x2E6E9E;
    private static final int ALERT = 0xB33A36;

    private AETextField expressionBox;

    public QueryExportBusScreen(QueryExportBusMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        expressionBox = new WideTextField(style, font, leftPos + 10, topPos + 28, 180, 16);
        expressionBox.setBordered(false);
        expressionBox.setMaxLength(256);
        expressionBox.setValue(menu.source);
        addRenderableWidget(expressionBox);

        addRenderableWidget(new AE2Button(leftPos + 10, topPos + imageHeight - 26, 60, 18,
                Component.literal("Apply"), b -> apply()));
    }

    private void apply() {
        PacketDistributor.sendToServer(new ConfigureQueryPartPayload(
                menu.pos, (byte) menu.side.ordinal(), "", expressionBox.getValue()));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Query", 10, 18, LABEL, false);
        if (!menu.source.isBlank() && !menu.sourceValid()) {
            guiGraphics.drawString(font, "query has a syntax error", 10, 54, ALERT, false);
        }
        guiGraphics.drawString(font, "Exports matching items into the", 10, 74, HINT, false);
        guiGraphics.drawString(font, "inventory this bus faces.", 10, 86, HINT, false);
        guiGraphics.drawString(font, "Use @name for saved queries.", 10, 98, HINT, false);
        guiGraphics.drawString(font, "Moved: " + menu.movedLastOperation() + "/op", 78,
                imageHeight - 22, VALUE, false);
    }
}
