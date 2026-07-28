package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicCoreMenu;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

public class LogicCoreScreen extends AbstractContainerScreen<LogicCoreMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/core_panel.png");

    /** Cycle order for the type button; REDSTONE_IO stays a physical part. */
    private static final LogicPartType[] TYPE_CYCLE = {
            LogicPartType.CONSTANT, LogicPartType.THRESHOLD, LogicPartType.HYSTERESIS,
            LogicPartType.ARITHMETIC, LogicPartType.BOOLEAN, LogicPartType.STOCK_SENSOR,
            LogicPartType.RATE, LogicPartType.COUNTER, LogicPartType.TIMER,
    };

    private static final String[] THRESHOLD_OPS = {"<", "<=", "==", ">=", ">"};
    private static final String[] ARITHMETIC_OPS = {"+", "-", "*", "/", "min", "max", "mod"};
    private static final String[] BOOLEAN_OPS = {"and", "or", "xor", "not"};

    private final List<AbstractWidget> detailWidgets = new ArrayList<>();

    private EditBox outBox;
    private EditBox inABox;
    private EditBox inBBox;
    private EditBox valueABox;
    private EditBox valueBBox;
    private int opValue;
    private boolean flagValue;

    public LogicCoreScreen(LogicCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();
        rebuildDetail();
    }

    private int selectedType() {
        return menu.types[menu.selected()];
    }

    private void rebuildDetail() {
        for (var widget : detailWidgets) {
            removeWidget(widget);
        }
        detailWidgets.clear();
        int selected = menu.selected();
        int type = menu.types[selected];
        opValue = menu.ops[selected];
        flagValue = menu.flags[selected];

        addDetail(Button.builder(Component.literal(typeName(type)), b -> cycleType())
                .bounds(leftPos + 8, topPos + 123, 46, 14).build());

        outBox = new EditBox(font, leftPos + 60, topPos + 124, 62, 12, Component.empty());
        outBox.setMaxLength(80);
        outBox.setValue(menu.outs[selected]);
        addDetail(outBox);

        addDetail(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 156, topPos + 123, 36, 14).build());

        if (type == LogicPartType.STOCK_SENSOR.ordinal()) {
            inABox = inBBox = valueABox = valueBBox = null;
        } else if (type >= 0) {
            inABox = new EditBox(font, leftPos + 8, topPos + 140, 44, 12, Component.empty());
            inABox.setMaxLength(80);
            inABox.setValue(menu.inAs[selected]);
            addDetail(inABox);

            inBBox = new EditBox(font, leftPos + 56, topPos + 140, 44, 12, Component.empty());
            inBBox.setMaxLength(80);
            inBBox.setValue(menu.inBs[selected]);
            addDetail(inBBox);

            addDetail(Button.builder(Component.literal(opName(type, opValue)), b -> cycleOp(b))
                    .bounds(leftPos + 104, topPos + 139, 26, 14).build());

            valueABox = new EditBox(font, leftPos + 134, topPos + 140, 28, 12, Component.empty());
            valueABox.setMaxLength(12);
            valueABox.setValue(Long.toString(menu.valueAs[selected]));
            addDetail(valueABox);

            valueBBox = new EditBox(font, leftPos + 166, topPos + 140, 28, 12, Component.empty());
            valueBBox.setMaxLength(12);
            valueBBox.setValue(Long.toString(menu.valueBs[selected]));
            addDetail(valueBBox);

            addDetail(Button.builder(Component.literal(flagValue ? "b=ch" : "b=#"), b -> {
                flagValue = !flagValue;
                b.setMessage(Component.literal(flagValue ? "b=ch" : "b=#"));
            }).bounds(leftPos + 126, topPos + 123, 26, 14).build());
        } else {
            inABox = inBBox = valueABox = valueBBox = null;
        }
    }

    private void addDetail(AbstractWidget widget) {
        detailWidgets.add(widget);
        addRenderableWidget(widget);
    }

    private void cycleType() {
        int selected = menu.selected();
        int current = menu.types[selected];
        int index = -1;
        for (int i = 0; i < TYPE_CYCLE.length; i++) {
            if (TYPE_CYCLE[i].ordinal() == current) {
                index = i;
                break;
            }
        }
        int next = index + 1 >= TYPE_CYCLE.length ? -1
                : index < 0 ? TYPE_CYCLE[0].ordinal() : TYPE_CYCLE[index + 1].ordinal();
        menu.types[selected] = (byte) next;
        rebuildDetail();
    }

    private void cycleOp(Button button) {
        int type = selectedType();
        opValue = (opValue + 1) % opCount(type);
        button.setMessage(Component.literal(opName(type, opValue)));
    }

    private static int opCount(int type) {
        if (type == LogicPartType.THRESHOLD.ordinal()) {
            return THRESHOLD_OPS.length;
        }
        if (type == LogicPartType.ARITHMETIC.ordinal()) {
            return ARITHMETIC_OPS.length;
        }
        if (type == LogicPartType.BOOLEAN.ordinal()) {
            return BOOLEAN_OPS.length;
        }
        return 1;
    }

    private static String opName(int type, int op) {
        if (type == LogicPartType.THRESHOLD.ordinal()) {
            return THRESHOLD_OPS[op % THRESHOLD_OPS.length];
        }
        if (type == LogicPartType.ARITHMETIC.ordinal()) {
            return ARITHMETIC_OPS[op % ARITHMETIC_OPS.length];
        }
        if (type == LogicPartType.BOOLEAN.ordinal()) {
            return BOOLEAN_OPS[op % BOOLEAN_OPS.length];
        }
        return "-";
    }

    static String typeName(int type) {
        if (type < 0) {
            return "empty";
        }
        return switch (LogicPartType.byOrdinal(type)) {
            case CONSTANT -> "const";
            case THRESHOLD -> "thresh";
            case HYSTERESIS -> "hyst";
            case ARITHMETIC -> "arith";
            case BOOLEAN -> "gate";
            case STOCK_SENSOR -> "stock";
            case RATE -> "rate";
            case COUNTER -> "count";
            case TIMER -> "timer";
            default -> "?";
        };
    }

    private void apply() {
        int selected = menu.selected();
        int type = menu.types[selected];
        String out = outBox.getValue().trim();
        String inA = inABox != null ? inABox.getValue().trim() : menu.inAs[selected];
        String inB = inBBox != null ? inBBox.getValue().trim() : menu.inBs[selected];
        long valueA = valueABox != null ? parse(valueABox.getValue(), 0) : menu.valueAs[selected];
        long valueB = valueBBox != null ? parse(valueBBox.getValue(), 0) : menu.valueBs[selected];

        menu.outs[selected] = out;
        menu.inAs[selected] = inA;
        menu.inBs[selected] = inB;
        menu.ops[selected] = opValue;
        menu.valueAs[selected] = valueA;
        menu.valueBs[selected] = valueB;
        menu.flags[selected] = flagValue;

        PacketDistributor.sendToServer(new ConfigureCoreEntryPayload(menu.pos,
                ConfigureCoreEntryPayload.ACTION_APPLY, (byte) selected, (byte) type,
                out, inA, inB, opValue, valueA, valueB, flagValue));
    }

    private static long parse(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (x >= 8 && x < 192 && y >= LogicCoreMenu.ROW_Y
                && y < LogicCoreMenu.ROW_Y + LogicCoreMenu.ROWS * LogicCoreMenu.ROW_STEP) {
            int row = (y - LogicCoreMenu.ROW_Y) / LogicCoreMenu.ROW_STEP;
            if (row != menu.selected()) {
                menu.setSelected(row);
                PacketDistributor.sendToServer(ConfigureCoreEntryPayload.select(menu.pos, row));
                rebuildDetail();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        int selY = topPos + LogicCoreMenu.ROW_Y + menu.selected() * LogicCoreMenu.ROW_STEP;
        guiGraphics.fill(leftPos + 7, selY - 1, leftPos + 193, selY + 11, 0x30FFFFFF);

        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            slotFrame(guiGraphics, LogicCoreMenu.GHOST_X, LogicCoreMenu.GHOST_Y);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(guiGraphics, LogicCoreMenu.INV_X + col * 18, LogicCoreMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(guiGraphics, LogicCoreMenu.INV_X + col * 18, LogicCoreMenu.HOTBAR_Y);
        }
    }

    private void slotFrame(GuiGraphics guiGraphics, int slotX, int slotY) {
        int x = leftPos + slotX - 1;
        int y = topPos + slotY - 1;
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF1A1F27);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2C333F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, menu.coreActive() ? "online" : "offline", 160, 6,
                menu.coreActive() ? 0x6FDB6F : 0xE0524E, false);

        for (int i = 0; i < LogicCoreMenu.ROWS; i++) {
            int y = LogicCoreMenu.ROW_Y + i * LogicCoreMenu.ROW_STEP + 1;
            int type = menu.types[i];
            boolean active = menu.entryActive(i);
            int labelColor = type < 0 ? 0x4A5866 : active ? 0xE0E6EB : 0xE0524E;
            guiGraphics.drawString(font, (i + 1) + " " + typeName(type), 10, y, labelColor, false);
            if (type >= 0) {
                var out = menu.outs[i];
                guiGraphics.drawString(font, truncate(out, 16), 62, y, 0x8A9AA8, false);
                var value = Long.toString(menu.entryValue(i));
                guiGraphics.drawString(font, value, 190 - font.width(value), y,
                        active ? 0x6FDB6F : 0x4A5866, false);
            }
        }

        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            guiGraphics.drawString(font, "click slot with held item to watch", 32, 141, 0x5A6B7C, false);
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
