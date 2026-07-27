package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.ConfigurePartPayload;
import io.github.johnhamilto.ae2logistics.menu.LogicPartMenu;
import io.github.johnhamilto.ae2logistics.parts.LogicPartType;

public class LogicPartScreen extends AbstractContainerScreen<LogicPartMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/logic_panel.png");

    private static final String[] THRESHOLD_OPS = {"<", "<=", "=", ">=", ">"};
    private static final String[] ARITHMETIC_OPS = {"+", "-", "x", "/", "min", "max", "mod"};
    private static final String[] BOOLEAN_OPS = {"AND", "OR", "XOR", "NOT"};

    private enum Field {
        OUT, IN_A, IN_B, VALUE_A, VALUE_B
    }

    private record Row(Field field, String label) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<EditBox> rowBoxes = new ArrayList<>();

    private int opValue;
    private boolean flagValue;

    public LogicPartScreen(LogicPartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 166;
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
        opValue = menu.op;
        flagValue = menu.flag;
        rows.clear();
        rowBoxes.clear();
        rows.addAll(rowsFor(menu.type));

        int x = leftPos + 78;
        int y = topPos + 18;
        for (var row : rows) {
            var box = new EditBox(font, x, y, 112, 16, Component.empty());
            box.setMaxLength(80);
            box.setValue(initialValue(row.field));
            rowBoxes.add(addRenderableWidget(box));
            y += 22;
        }

        if (opsFor(menu.type) != null) {
            addRenderableWidget(Button.builder(
                    Component.literal(opLabel()),
                    b -> {
                        opValue = (opValue + 1) % opsFor(menu.type).length;
                        b.setMessage(Component.literal(opLabel()));
                    }).bounds(leftPos + 10, y, 60, 18).build());
        }
        if (usesFlag(menu.type)) {
            addRenderableWidget(Button.builder(
                    Component.literal(flagLabel()),
                    b -> {
                        flagValue = !flagValue;
                        b.setMessage(Component.literal(flagLabel()));
                    }).bounds(leftPos + 78, y, 112, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 60, 18).build());
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.type == LogicPartType.STOCK_SENSOR) {
            int x = leftPos + LogicPartMenu.GHOST_SLOT_X - 1;
            int y = topPos + LogicPartMenu.GHOST_SLOT_Y - 1;
            guiGraphics.fill(x, y, x + 18, y + 18, 0xFF1A1F27);
            guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2C333F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);

        int y = 22;
        for (var row : rows) {
            guiGraphics.drawString(font, row.label, 10, y, 0x9BB2C4, false);
            y += 22;
        }
        if (menu.type == LogicPartType.STOCK_SENSOR) {
            guiGraphics.drawString(font, "Watch:", 10, LogicPartMenu.GHOST_SLOT_Y - 10, 0x9BB2C4, false);
            guiGraphics.drawString(font, "click with an item", 32, LogicPartMenu.GHOST_SLOT_Y + 5, 0x5A6B7C, false);
        }

        guiGraphics.drawString(font, "Out: " + menu.outputValue(), 78, imageHeight - 22, 0x5CE2FF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
