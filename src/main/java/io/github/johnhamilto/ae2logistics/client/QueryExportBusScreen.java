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
import io.github.johnhamilto.ae2logistics.menu.QueryExportBusMenu;

public class QueryExportBusScreen extends AEBaseScreen<QueryExportBusMenu> {

    private AETextField expressionBox;

    public QueryExportBusScreen(QueryExportBusMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
    }

    @Override
    protected void init() {
        super.init();
        expressionBox = new WideTextField(style, font, leftPos + 10, topPos + 28, 180, 16);
        expressionBox.setBordered(false);
        expressionBox.setMaxLength(256);
        expressionBox.setValue(menu.source);
        addRenderableWidget(expressionBox);

    }

    private String snapshot() {
        return expressionBox.getValue();
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
                menu.pos, (byte) menu.side.ordinal(), "", expressionBox.getValue()));
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, "Query", 10, 18, Palette.LABEL, false);
        if (!menu.source.isBlank() && !menu.sourceValid()) {
            guiGraphics.text(font, "query has a syntax error", 10, 54, Palette.ALERT, false);
        }
        guiGraphics.text(font, "Exports matching items into the", 10, 74, Palette.HINT, false);
        guiGraphics.text(font, "inventory this bus faces.", 10, 86, Palette.HINT, false);
        guiGraphics.text(font, "Use @name for saved queries.", 10, 98, Palette.HINT, false);
        guiGraphics.text(font, "Moved: " + menu.movedLastOperation() + "/op", 78,
                imageHeight - 22, Palette.VALUE, false);
    }
}
