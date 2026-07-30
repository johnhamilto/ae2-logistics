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

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int ROW = 0x505A62;
    private static final int SELECTED = 0x2E6E9E;
    private static final int OK = 0x2E8B57;
    private static final int ALERT = 0xB33A36;

    private static final int LIST_X = 10;
    private static final int LIST_Y = 62;
    private static final int LIST_WIDTH = 96;
    private static final int ROW_HEIGHT = 11;
    private static final int VISIBLE_ROWS = 9;
    private static final int RESULTS_X = 114;

    private AETextField expressionBox;
    private AETextField nameBox;
    private String lastRequested = "";
    private int scroll;

    public QueryTerminalScreen(QueryTerminalMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 236;
        this.imageHeight = 190;
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
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Saved", LIST_X, LIST_Y - 10, LABEL, false);

        var names = savedNames();
        int max = Math.max(0, names.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);
        if (names.isEmpty()) {
            guiGraphics.drawString(font, "none yet", LIST_X, LIST_Y + 2, HINT, false);
        }
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < names.size(); i++) {
            var name = names.get(scroll + i);
            var label = "@" + name;
            if (label.length() > 15) {
                label = label.substring(0, 14) + "..";
            }
            boolean selected = name.equals(nameBox.getValue());
            guiGraphics.drawString(font, label, LIST_X, LIST_Y + i * ROW_HEIGHT,
                    selected ? SELECTED : ROW, false);
        }

        if (!menu.previewError.isEmpty()) {
            var error = menu.previewError;
            if (error.length() > 36) {
                error = error.substring(0, 35) + "..";
            }
            guiGraphics.drawString(font, error, 10, imageHeight - 14, ALERT, false);
        } else if (!lastRequested.isBlank()) {
            guiGraphics.drawString(font,
                    menu.previewMatches + " kinds, " + menu.previewTotal + " total",
                    RESULTS_X, LIST_Y - 10, OK, false);
        }

        for (int i = 0; i < menu.previewStacks.size(); i++) {
            var stack = menu.previewStacks.get(i);
            int y = LIST_Y + i * 18;
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, RESULTS_X, y);
            }
            guiGraphics.drawString(font, "x" + menu.previewAmounts.get(i),
                    RESULTS_X + 20, y + 5, ROW, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (localX >= LIST_X && localX < LIST_X + LIST_WIDTH && localY >= LIST_Y
                && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            int index = scroll + (localY - LIST_Y) / ROW_HEIGHT;
            var names = savedNames();
            if (index >= 0 && index < names.size()) {
                var name = names.get(index);
                nameBox.setValue(name);
                expressionBox.setValue(menu.library.getOrDefault(name, ""));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, savedNames().size() - VISIBLE_ROWS);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY));
        return true;
    }
}
