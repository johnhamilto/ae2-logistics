package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureCoreEntryPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicCoreMenu;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

public class LogicCoreScreen extends AEBaseScreen<LogicCoreMenu> {

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
        // Window size (200x252) comes from the style doc's generatedBackground.
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
        autoApply.reset();
        for (var widget : detailWidgets) {
            removeWidget(widget);
        }
        detailWidgets.clear();
        int selected = menu.selected();
        int type = menu.types[selected];
        opValue = menu.ops[selected];
        flagValue = menu.flags[selected];

        addDetail(new CycleButton(leftPos + 8, topPos + 123, 46, 14,
                Component.literal(typeName(type)), (b, dir) -> cycleType(dir)));

        outBox = new AETextField(style, font, leftPos + 60, topPos + 124, 62, 12);
        outBox.setBordered(false);
        outBox.setMaxLength(80);
        outBox.setValue(menu.outs[selected]);
        addDetail(outBox);


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

            addDetail(new CycleButton(leftPos + 104, topPos + 139, 26, 14,
                    Component.literal(opName(type, opValue)), this::cycleOp));

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

    private void cycleType(int dir) {
        int selected = menu.selected();
        int current = menu.types[selected];
        // Domain is empty plus TYPE_CYCLE, cycled in either direction (empty at len).
        int len = TYPE_CYCLE.length;
        int pos = len;
        for (int i = 0; i < len; i++) {
            if (TYPE_CYCLE[i].ordinal() == current) {
                pos = i;
                break;
            }
        }
        int next = Math.floorMod(pos + dir, len + 1);
        menu.types[selected] = (byte) (next == len ? -1 : TYPE_CYCLE[next].ordinal());
        apply();
        rebuildDetail();
    }

    private void cycleOp(CycleButton button, int dir) {
        int type = selectedType();
        opValue = Math.floorMod(opValue + dir, opCount(type));
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

    private String snapshot() {
        return menu.selected() + "\0" + menu.types[menu.selected()]
                + '\0' + (outBox == null ? "" : outBox.getValue())
                + '\0' + (inABox == null ? "" : inABox.getValue())
                + '\0' + (inBBox == null ? "" : inBBox.getValue())
                + '\0' + (valueABox == null ? "" : valueABox.getValue())
                + '\0' + (valueBBox == null ? "" : valueBBox.getValue())
                + '\0' + opValue + '\0' + flagValue;
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

        ClientPacketDistributor.sendToServer(new ConfigureCoreEntryPayload(menu.pos,
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) event.x() - leftPos;
        int y = (int) event.y() - topPos;
        if (x >= 8 && x < 192 && y >= LogicCoreMenu.ROW_Y
                && y < LogicCoreMenu.ROW_Y + LogicCoreMenu.ROWS * LogicCoreMenu.ROW_STEP) {
            int row = (y - LogicCoreMenu.ROW_Y) / LogicCoreMenu.ROW_STEP;
            if (row != menu.selected()) {
                if (autoApply.dirty(snapshot())) {
                    apply();
                }
                menu.setSelected(row);
                ClientPacketDistributor.sendToServer(ConfigureCoreEntryPayload.select(menu.pos, row));
                rebuildDetail();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // The style doc's generatedBackground draws the panel; player slots need
        // their insets drawn here. The ghost slot draws only when it means something
        // (a stock-sensor entry is selected) - it always exists in the menu.
        for (var slot : menu.slots) {
            if (slot.isActive() && slot.container instanceof Inventory) {
                Blitter.icon(Icon.SLOT_BACKGROUND)
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(guiGraphics);
            }
        }
        int selY = offsetY + LogicCoreMenu.ROW_Y + menu.selected() * LogicCoreMenu.ROW_STEP;
        guiGraphics.fill(offsetX + 7, selY - 1, offsetX + 193, selY + 11, 0x30405A78);
        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            Blitter.icon(Icon.SLOT_BACKGROUND)
                    .dest(offsetX + LogicCoreMenu.GHOST_X - 1, offsetY + LogicCoreMenu.GHOST_Y - 1)
                    .blit(guiGraphics);
        }
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, menu.coreActive() ? "online" : "offline", 160, 6,
                menu.coreActive() ? Palette.OK : Palette.ALERT, false);

        for (int i = 0; i < LogicCoreMenu.ROWS; i++) {
            int y = LogicCoreMenu.ROW_Y + i * LogicCoreMenu.ROW_STEP + 1;
            int type = menu.types[i];
            boolean active = menu.entryActive(i);
            int labelColor = type < 0 ? Palette.MUTED : active ? Palette.LABEL : Palette.ALERT;
            guiGraphics.text(font, (i + 1) + " " + typeName(type), 10, y, labelColor, false);
            if (type >= 0) {
                var out = menu.outs[i];
                guiGraphics.text(font, truncate(out, 16), 62, y, Palette.HINT, false);
                var value = Long.toString(menu.entryValue(i));
                guiGraphics.text(font, value, 190 - font.width(value), y,
                        active ? Palette.OK : Palette.MUTED, false);
            }
        }

        if (selectedType() == LogicPartType.STOCK_SENSOR.ordinal()) {
            guiGraphics.text(font, "click slot with held item to watch", 32, 141, Palette.HINT, false);
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }
}
