package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigurePartPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicPartMenu;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

public class LogicPartScreen extends AEBaseScreen<LogicPartMenu> {

    private static final String[] THRESHOLD_OPS = {"<", "<=", "==", ">=", ">"};
    private static final String[] ARITHMETIC_OPS = {"+", "-", "*", "/", "min", "max", "mod"};
    private static final String[] BOOLEAN_OPS = {"AND", "OR", "XOR", "NOT"};
    private static final String[] SIGNAL_OPS = {"Strong", "Weak"};

    private enum Field {
        OUT, IN_A, IN_B, VALUE_A, VALUE_B
    }

    private record Row(Field field, String label) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<AETextField> rowBoxes = new ArrayList<>();

    private int opValue;
    private boolean flagValue;

    public LogicPartScreen(LogicPartMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        // The sensor variant carries a player inventory (to feed the ghost slot by hand).
        this.imageHeight = menu.type == LogicPartType.STOCK_SENSOR ? 222 : 166;
    }

    private int controlY() {
        return menu.type == LogicPartType.STOCK_SENSOR ? 104 : imageHeight - 26;
    }

    private static List<Row> rowsFor(LogicPartType type) {
        var list = new ArrayList<Row>();
        list.add(new Row(Field.OUT, switch (type) {
            case REDSTONE_IO -> "Channel";
            default -> "Output";
        }));
        switch (type) {
            case CONSTANT -> list.add(new Row(Field.VALUE_A, "Value"));
            case THRESHOLD, ARITHMETIC, BOOLEAN -> {
                list.add(new Row(Field.IN_A, "Input A"));
                list.add(new Row(Field.IN_B, "Input B"));
                list.add(new Row(Field.VALUE_A, "Const B"));
            }
            case HYSTERESIS -> {
                list.add(new Row(Field.IN_A, "Input A"));
                list.add(new Row(Field.VALUE_A, "Low"));
                list.add(new Row(Field.VALUE_B, "High"));
            }
            case REDSTONE_IO -> list.add(new Row(Field.IN_A, "Input A"));
            case STOCK_SENSOR -> {
            }
            case RATE -> {
                list.add(new Row(Field.IN_A, "Input A"));
                list.add(new Row(Field.VALUE_A, "Window (s)"));
            }
            case COUNTER -> {
                list.add(new Row(Field.IN_A, "Edges"));
                list.add(new Row(Field.IN_B, "Reset"));
                list.add(new Row(Field.VALUE_A, "Wrap (0=off)"));
            }
            case TIMER -> {
                list.add(new Row(Field.VALUE_A, "Period (t)"));
                list.add(new Row(Field.VALUE_B, "Pulse (t)"));
            }
        }
        return list;
    }

    private String initialValue(Field field) {
        return switch (field) {
            case OUT -> menu.outChannel;
            case IN_A -> menu.inA;
            case IN_B -> menu.inB;
            case VALUE_A -> Long.toString(menu.valueA);
            case VALUE_B -> Long.toString(menu.valueB);
        };
    }

    @Override
    protected void init() {
        super.init();
        setTextContent("dialog_title", getTitle());
        if (menu.type != LogicPartType.STOCK_SENSOR) {
            setTextHidden("player_inventory_title", true);
        }

        opValue = menu.op;
        flagValue = menu.flag;
        rows.clear();
        rowBoxes.clear();
        rows.addAll(rowsFor(menu.type));

        int x = leftPos + 78;
        int y = topPos + 18;
        for (var row : rows) {
            var box = new AETextField(style, font, x, y, 112, 16);
            box.setBordered(false);
            box.setMaxLength(80);
            box.setValue(initialValue(row.field));
            rowBoxes.add(addRenderableWidget(box));
            y += 22;
        }

        if (opsFor(menu.type) != null) {
            addRenderableWidget(new CycleButton(leftPos + 10, y, 60, 18,
                    Component.literal(opLabel()),
                    (b, dir) -> {
                        opValue = Math.floorMod(opValue + dir, opsFor(menu.type).length);
                        b.setMessage(Component.literal(opLabel()));
                    }));
        }
        if (usesFlag(menu.type)) {
            addRenderableWidget(new AE2Button(leftPos + 78, y, 112, 18,
                    Component.literal(flagLabel()),
                    b -> {
                        flagValue = !flagValue;
                        b.setMessage(Component.literal(flagLabel()));
                    }));
        }

    }

    private static boolean usesFlag(LogicPartType type) {
        return type == LogicPartType.THRESHOLD || type == LogicPartType.ARITHMETIC
                || type == LogicPartType.BOOLEAN || type == LogicPartType.REDSTONE_IO;
    }

    @Nullable
    private static String[] opsFor(LogicPartType type) {
        return switch (type) {
            case THRESHOLD -> THRESHOLD_OPS;
            case ARITHMETIC -> ARITHMETIC_OPS;
            case BOOLEAN -> BOOLEAN_OPS;
            case REDSTONE_IO -> SIGNAL_OPS;
            default -> null;
        };
    }

    private String opLabel() {
        var ops = opsFor(menu.type);
        return ops == null ? "" : ops[Math.floorMod(opValue, ops.length)];
    }

    private String flagLabel() {
        if (menu.type == LogicPartType.REDSTONE_IO) {
            return flagValue ? "Mode: Output" : "Mode: Input";
        }
        return flagValue ? "B: Channel" : "B: Constant";
    }

    private String fieldText(Field field) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).field == field) {
                return rowBoxes.get(i).getValue();
            }
        }
        return field == Field.VALUE_A || field == Field.VALUE_B ? "0" : "";
    }

    private String snapshot() {
        var sb = new StringBuilder();
        for (var box : rowBoxes) {
            sb.append(box.getValue()).append('\0');
        }
        return sb.append(opValue).append('\0').append(flagValue).toString();
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
        PacketDistributor.sendToServer(new ConfigurePartPayload(
                menu.pos,
                menu.side,
                fieldText(Field.OUT),
                fieldText(Field.IN_A),
                fieldText(Field.IN_B),
                opValue,
                parseLong(fieldText(Field.VALUE_A)),
                parseLong(fieldText(Field.VALUE_B)),
                flagValue));
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        // One menu type, two dialog sizes: the style doc carries no background (it
        // could hold only one size), so the screen draws the generated chrome at its
        // live size and gives every real slot AE2's standard inset.
        BackgroundGenerator.draw(imageWidth, imageHeight, guiGraphics, offsetX, offsetY);
        for (var slot : menu.slots) {
            if (slot.isActive()) {
                Icon.SLOT_BACKGROUND.getBlitter()
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(guiGraphics);
            }
        }
    }

    /** One screen serves ten part types, so the help button resolves per type. */
    @Override
    protected guideme.PageAnchor getHelpTopic() {
        var page = switch (menu.type) {
            case CONSTANT -> "constant.md";
            case THRESHOLD -> "threshold.md";
            case HYSTERESIS -> "hysteresis.md";
            case ARITHMETIC -> "arithmetic.md";
            case BOOLEAN -> "logic-gate.md";
            case REDSTONE_IO -> "redstone-port.md";
            case STOCK_SENSOR -> "stock-sensor.md";
            case RATE -> "rate.md";
            case COUNTER -> "counter.md";
            case TIMER -> "timer.md";
        };
        return new guideme.PageAnchor(
                io.github.johnhamilto.ae2logistics.AE2Logistics.id(page), null);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int y = 22;
        for (var row : rows) {
            guiGraphics.drawString(font, row.label, 10, y, Palette.LABEL, false);
            y += 22;
        }
        if (menu.type == LogicPartType.STOCK_SENSOR) {
            guiGraphics.drawString(font, "Watch:", 10, 34, Palette.LABEL, false);
            guiGraphics.drawString(font, "click with an item", 32, 49, Palette.HINT, false);
        }

        guiGraphics.drawString(font, "Out: " + menu.outputValue(), 78, controlY() + 5, Palette.VALUE, false);
    }
}
