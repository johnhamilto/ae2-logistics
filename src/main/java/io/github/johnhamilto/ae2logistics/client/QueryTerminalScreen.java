package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.QueryEditPayload;
import io.github.johnhamilto.ae2logistics.menu.QueryTerminalMenu;

public class QueryTerminalScreen extends AbstractContainerScreen<QueryTerminalMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/tracer_panel.png");

    private static final int LIST_X = 10;
    private static final int LIST_Y = 62;
    private static final int LIST_WIDTH = 96;
    private static final int ROW_HEIGHT = 11;
    private static final int VISIBLE_ROWS = 9;
    private static final int RESULTS_X = 114;

    private EditBox expressionBox;
    private EditBox nameBox;
    private String lastRequested = "";
    private int scroll;

    public QueryTerminalScreen(QueryTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        expressionBox = new EditBox(font, leftPos + 10, topPos + 18, 216, 16, Component.empty());
        expressionBox.setMaxLength(256);
        addRenderableWidget(expressionBox);

        nameBox = new EditBox(font, leftPos + 10, topPos + 40, 108, 14, Component.empty());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(leftPos + 124, topPos + 38, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> delete())
                .bounds(leftPos + 178, topPos + 38, 48, 18).build());
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
    protected void containerTick() {
        super.containerTick();
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
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, "Saved", LIST_X, LIST_Y - 10, 0x9BB2C4, false);

        var names = savedNames();
        int max = Math.max(0, names.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);
        if (names.isEmpty()) {
            guiGraphics.drawString(font, "none yet", LIST_X, LIST_Y + 2, 0x5A6B7C, false);
        }
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < names.size(); i++) {
            var name = names.get(scroll + i);
            var label = "@" + name;
            if (label.length() > 15) {
                label = label.substring(0, 14) + "..";
            }
            boolean selected = name.equals(nameBox.getValue());
            guiGraphics.drawString(font, label, LIST_X, LIST_Y + i * ROW_HEIGHT,
                    selected ? 0x5CE2FF : 0xC7D3DE, false);
        }

        if (!menu.previewError.isEmpty()) {
            var error = menu.previewError;
            if (error.length() > 36) {
                error = error.substring(0, 35) + "..";
            }
            guiGraphics.drawString(font, error, 10, imageHeight - 14, 0xE0524E, false);
        } else if (!lastRequested.isBlank()) {
            guiGraphics.drawString(font,
                    menu.previewMatches + " kinds, " + menu.previewTotal + " total",
                    RESULTS_X, LIST_Y - 10, 0x6FDB6F, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < menu.previewStacks.size(); i++) {
            var stack = menu.previewStacks.get(i);
            int y = topPos + LIST_Y + i * 18;
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, leftPos + RESULTS_X, y);
            }
            guiGraphics.drawString(font, "x" + menu.previewAmounts.get(i),
                    leftPos + RESULTS_X + 20, y + 5, 0xC7D3DE, false);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
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

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }
}
