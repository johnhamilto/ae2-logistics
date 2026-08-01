package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.QueryEditPayload;
import io.github.johnhamilto.ae2logistics.menu.QueryTerminalMenu;

public class QueryTerminalScreen extends AEBaseScreen<QueryTerminalMenu> {

    private static final int LIST_Y = 62;
    private static final int RESULTS_X = 114;

    private final ScrollingRowList list = new ScrollingRowList(8, 108, LIST_Y, LIST_Y + 102, 11);

    private AETextField expressionBox;
    private AETextField nameBox;
    private String lastRequested = "";

    public QueryTerminalScreen(QueryTerminalMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        list.register(widgets, "scrollbar");
    }

    @Override
    protected void init() {
        super.init();
        expressionBox = new WideTextField(style, font, leftPos + 10, topPos + 18, 216, 16);
        expressionBox.setBordered(false);
        expressionBox.setMaxLength(256);
        addRenderableWidget(expressionBox);

        nameBox = new AETextField(style, font, leftPos + 10, topPos + 40, 108, 14);
        nameBox.setBordered(false);
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);

        addRenderableWidget(new AE2Button(leftPos + 124, topPos + 38, 48, 18,
                Component.literal("Save"), b -> save()));
        addRenderableWidget(new AE2Button(leftPos + 178, topPos + 38, 48, 18,
                Component.literal("Delete"), b -> delete()));
    }

    private void save() {
        if (!nameBox.getValue().isBlank() && !expressionBox.getValue().isBlank()) {
            PacketDistributor.sendToServer(new QueryEditPayload(menu.pos, (byte) menu.side.ordinal(),
                    QueryEditPayload.ACTION_SAVE, nameBox.getValue(), expressionBox.getValue()));
        }
    }

    private void delete() {
        if (!nameBox.getValue().isBlank()) {
            PacketDistributor.sendToServer(new QueryEditPayload(menu.pos, (byte) menu.side.ordinal(),
                    QueryEditPayload.ACTION_DELETE, nameBox.getValue(), ""));
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        list.setRowCount(savedNames().size());
        var current = expressionBox.getValue();
        if (!current.equals(lastRequested)) {
            lastRequested = current;
            PacketDistributor.sendToServer(new QueryEditPayload(menu.pos, (byte) menu.side.ordinal(),
                    QueryEditPayload.ACTION_PREVIEW, "", current));
        }
    }

    private List<String> savedNames() {
        return new ArrayList<>(menu.library.keySet());
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        list.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Saved", 10, LIST_Y - 10, Palette.LABEL, false);

        var names = savedNames();
        if (names.isEmpty()) {
            guiGraphics.drawString(font, "none yet", 12, LIST_Y + 2, Palette.HINT, false);
        }
        list.drawRows(guiGraphics, (g, index, y) -> {
            var name = names.get(index);
            var label = "@" + name;
            if (label.length() > 14) {
                label = label.substring(0, 13) + "..";
            }
            boolean selected = name.equals(nameBox.getValue());
            g.drawString(font, label, 10, y, selected ? Palette.VALUE : Palette.ROW, false);
        });

        if (!menu.previewError.isEmpty()) {
            var error = menu.previewError;
            if (error.length() > 36) {
                error = error.substring(0, 35) + "..";
            }
            guiGraphics.drawString(font, error, 10, imageHeight - 14, Palette.ALERT, false);
        } else if (!lastRequested.isBlank()) {
            guiGraphics.drawString(font,
                    menu.previewMatches + " kinds, " + menu.previewTotal + " total",
                    RESULTS_X, LIST_Y - 10, Palette.OK, false);
        }

        for (int i = 0; i < menu.previewStacks.size(); i++) {
            var stack = menu.previewStacks.get(i);
            int y = LIST_Y + i * 18;
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, RESULTS_X, y);
            }
            guiGraphics.drawString(font, "x" + menu.previewAmounts.get(i),
                    RESULTS_X + 20, y + 5, Palette.ROW, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = list.rowAt(mouseX, mouseY, leftPos, topPos);
        var names = savedNames();
        if (index >= 0 && index < names.size()) {
            var name = names.get(index);
            nameBox.setValue(name);
            expressionBox.setValue(menu.library.getOrDefault(name, ""));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
