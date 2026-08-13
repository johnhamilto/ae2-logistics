package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureQueryPartPayload;
import io.github.johnhamilto.ae2logistics.menu.QuerySensorMenu;

public class QuerySensorScreen extends AEBaseScreen<QuerySensorMenu> {

    private AETextField channelBox;
    private AETextField expressionBox;

    public QuerySensorScreen(QuerySensorMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
    }

    @Override
    protected void init() {
        super.init();
        channelBox = new AETextField(style, font, leftPos + 78, topPos + 18, 112, 16);
        channelBox.setBordered(false);
        channelBox.setMaxLength(80);
        channelBox.setValue(menu.outChannel);
        addRenderableWidget(channelBox);

        expressionBox = new WideTextField(style, font, leftPos + 10, topPos + 52, 180, 16);
        expressionBox.setBordered(false);
        expressionBox.setMaxLength(256);
        expressionBox.setValue(menu.source);
        addRenderableWidget(expressionBox);

    }

    private String snapshot() {
        return channelBox.getValue() + '\0' + expressionBox.getValue();
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
        ClientPacketDistributor.sendToServer(new ConfigureQueryPartPayload(
                menu.pos, (byte) menu.side.ordinal(), channelBox.getValue(), expressionBox.getValue()));
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, "Output", 10, 22, Palette.LABEL, false);
        guiGraphics.text(font, "Query", 10, 42, Palette.LABEL, false);
        if (!menu.source.isBlank() && !menu.sourceValid()) {
            guiGraphics.text(font, "query has a syntax error", 10, 78, Palette.ALERT, false);
        }
        guiGraphics.text(font, "Writes total matching amount", 10, 96, Palette.HINT, false);
        guiGraphics.text(font, "e.g. tag:c:ores AND stored", 10, 108, Palette.HINT, false);
        guiGraphics.text(font, "Out: " + menu.liveValue(), 78, imageHeight - 22, Palette.VALUE, false);
    }
}
