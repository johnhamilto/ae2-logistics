package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicCoreMenu;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

public class LogicCoreScreen extends AEBaseScreen<LogicCoreMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int MUTED = 0xA0A0A0;
    private static final int OK = 0x2E8B57;
    private static final int ALERT = 0xB33A36;

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

    private AETextField outBox;
    private AETextField inABox;
    private AETextField inBBox;
    private AETextField valueABox;
    private AETextField valueBBox;
    private int opValue;
    private boolean flagValue;

    public LogicCoreScreen(LogicCoreMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 252;
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

        addDetail(new AE2Button(leftPos + 8, topPos + 123, 46, 14,
                Component.literal(typeName(type)), b -> cycleType()));

        outBox = new AETextField(style, font, leftPos + 60, topPos + 124, 62, 12);
        outBox.setBordered(false);
        outBox.setMaxLength(80);
        outBox.setValue(menu.outs[selected]);
        addDetail(outBox);

        addDetail(new AE2Button(leftPos + 156, topPos + 123, 36, 14,
                Component.literal("Apply"), b -> apply()));

        if (type == LogicPartType.STOCK_SENSOR.ordinal()) {
            inABox = inBBox = valueABox = valueBBox = null;
        } else if (type >= 0) {
            inABox = new AETextField(style, font, leftPos + 8, topPos + 140, 44, 12);
            inABox.setBordered(false);
            inABox.setMaxLength(80);
            inABox.setValue(menu.inAs[selected]);
            addDetail(inABox);

            inBBox = new AETextField(style, font, leftPos + 56, topPos + 140, 44, 12);
            inBBox.setBordered(false);
            inBBox.setMaxLength(80);
            inBBox.setValue(menu.inBs[selected]);
            addDetail(inBBox);

            addDetail(new AE2Button(leftPos + 104, topPos + 139, 26, 14,
                    Component.literal(opName(type, opValue)), b -> cycleOp((Button) b)));

            valueABox = new AETextField(style, font, leftPos + 134, topPos + 140, 28, 12);
            valueABox.setBordered(false);
            valueABox.setMaxLength(12);
            valueABox.setValue(Long.toString(menu.valueAs[selected]));
            addDetail(valueABox);

            valueBBox = new AETextField(style, font, leftPos + 166, topPos + 140, 28, 12);
            valueBBox.setBordered(false);
            valueBBox.setMaxLength(12);
            valueBBox.setValue(Long.toString(menu.valueBs[selected]));
            addDetail(valueBBox);

            addDetail(new AE2Button(leftPos + 126, topPos + 123, 26, 14,
                    Component.literal(flagValue ? "b=ch" : "b=#"), b -> {
                        flagValue = !flagValue;
                        b.setMessage(Component.literal(flagValue ? "b=ch" : "b=#"));
                    }));
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
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        int selY = offsetY + LogicCoreMenu.ROW_Y + menu.selected() * LogicCoreMenu.ROW_STEP;
        guiGraphics.fill(offsetX + 7, selY - 1, offsetX + 193, selY + 11, 0x30405A78);
        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + LogicCoreMenu.GHOST_X - 1, offsetY + LogicCoreMenu.GHOST_Y - 1)
                    .blit(guiGraphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, menu.coreActive() ? "online" : "offline", 160, 6,
                menu.coreActive() ? OK : ALERT, false);

        for (int i = 0; i < LogicCoreMenu.ROWS; i++) {
            int y = LogicCoreMenu.ROW_Y + i * LogicCoreMenu.ROW_STEP + 1;
            int type = menu.types[i];
            boolean active = menu.entryActive(i);
            int labelColor = type < 0 ? MUTED : active ? LABEL : ALERT;
            guiGraphics.drawString(font, (i + 1) + " " + typeName(type), 10, y, labelColor, false);
            if (type >= 0) {
                var out = menu.outs[i];
                guiGraphics.drawString(font, truncate(out, 16), 62, y, HINT, false);
                var value = Long.toString(menu.entryValue(i));
                guiGraphics.drawString(font, value, 190 - font.width(value), y,
                        active ? OK : MUTED, false);
            }
        }

        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            guiGraphics.drawString(font, "click slot with held item to watch", 32, 141, HINT, false);
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }
}
