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
import io.github.johnhamilto.ae2logistics.menu.QuerySensorMenu;

public class QuerySensorScreen extends AEBaseScreen<QuerySensorMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int VALUE = 0x2E6E9E;
    private static final int ALERT = 0xB33A36;

    private AETextField channelBox;
    private AETextField expressionBox;

    public QuerySensorScreen(QuerySensorMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        channelBox = new AETextField(style, font, leftPos + 78, topPos + 18, 112, 16);
        channelBox.setMaxLength(80);
        channelBox.setValue(menu.outChannel);
        addRenderableWidget(channelBox);

        expressionBox = new AETextField(style, font, leftPos + 10, topPos + 52, 180, 16);
        expressionBox.setMaxLength(256);
        expressionBox.setValue(menu.source);
        addRenderableWidget(expressionBox);

        addRenderableWidget(new AE2Button(leftPos + 10, topPos + imageHeight - 26, 60, 18,
                Component.literal("Apply"), b -> apply()));
    }

    private void apply() {
        PacketDistributor.sendToServer(new ConfigureQueryPartPayload(
                menu.pos, (byte) menu.side.ordinal(), channelBox.getValue(), expressionBox.getValue()));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Output", 10, 22, LABEL, false);
        guiGraphics.drawString(font, "Query", 10, 42, LABEL, false);
        if (!menu.source.isBlank() && !menu.sourceValid()) {
            guiGraphics.drawString(font, "query has a syntax error", 10, 78, ALERT, false);
        }
        guiGraphics.drawString(font, "Writes total matching amount", 10, 96, HINT, false);
        guiGraphics.drawString(font, "e.g. tag:c:ores AND stored", 10, 108, HINT, false);
        guiGraphics.drawString(font, "Out: " + menu.liveValue(), 78, imageHeight - 22, VALUE, false);
    }
}
