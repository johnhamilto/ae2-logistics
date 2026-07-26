package io.github.johnhamilto.ae2logistics.client;

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

    private EditBox outBox;
    private EditBox inABox;
    private EditBox inBBox;
    private EditBox valueABox;
    private EditBox valueBBox;
    private Button opButton;
    private Button flagButton;

    private int opValue;
    private boolean flagValue;

    public LogicPartScreen(LogicPartMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        opValue = menu.op;
        flagValue = menu.flag;
        var type = menu.type;

        int x = leftPos + 78;
        int y = topPos + 18;
        int w = 112;

        outBox = addText(x, y, w, menu.outChannel);
        y += 22;

        if (type != LogicPartType.CONSTANT) {
            inABox = addText(x, y, w, menu.inA);
            y += 22;
        }

        if (usesOperandB(type)) {
            inBBox = addText(x, y, w, menu.inB);
            y += 22;
        }

        if (type != LogicPartType.REDSTONE_IO) {
            valueABox = addText(x, y, w, Long.toString(menu.valueA));
            y += 22;
        }

        if (type == LogicPartType.HYSTERESIS) {
            valueBBox = addText(x, y, w, Long.toString(menu.valueB));
            y += 22;
        }

        if (opsFor(type) != null) {
            opButton = addRenderableWidget(Button.builder(
                    Component.literal(opLabel()),
                    b -> {
                        opValue = (opValue + 1) % opsFor(menu.type).length;
                        b.setMessage(Component.literal(opLabel()));
                    }).bounds(leftPos + 10, y, 60, 18).build());
        }

        if (usesOperandB(type) || type == LogicPartType.REDSTONE_IO) {
            flagButton = addRenderableWidget(Button.builder(
                    Component.literal(flagLabel()),
                    b -> {
                        flagValue = !flagValue;
                        b.setMessage(Component.literal(flagLabel()));
                    }).bounds(leftPos + 78, y, 112, 18).build());
        }
        y += 22;

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 60, 18).build());
    }

    private EditBox addText(int x, int y, int width, String initial) {
        var box = new EditBox(font, x, y, width, 16, Component.empty());
        box.setMaxLength(80);
        box.setValue(initial);
        return addRenderableWidget(box);
    }

    private static boolean usesOperandB(LogicPartType type) {
        return type == LogicPartType.THRESHOLD || type == LogicPartType.ARITHMETIC
                || type == LogicPartType.BOOLEAN;
    }

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

    private void apply() {
        PacketDistributor.sendToServer(new ConfigurePartPayload(
                menu.pos,
                menu.side,
                text(outBox),
                text(inABox),
                text(inBBox),
                opValue,
                parseLong(valueABox),
                parseLong(valueBBox),
                flagValue));
    }

    private static String text(EditBox box) {
        return box == null ? "" : box.getValue();
    }

    private static long parseLong(EditBox box) {
        if (box == null) {
            return 0;
        }
        try {
            return Long.parseLong(box.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);

        var type = menu.type;
        int y = 22;
        guiGraphics.drawString(font, "Output", 10, y, 0x9BB2C4, false);
        y += 22;
        if (type != LogicPartType.CONSTANT) {
            guiGraphics.drawString(font, "Input A", 10, y, 0x9BB2C4, false);
            y += 22;
        }
        if (usesOperandB(type)) {
            guiGraphics.drawString(font, "Input B", 10, y, 0x9BB2C4, false);
            y += 22;
        }
        if (type != LogicPartType.REDSTONE_IO) {
            var label = switch (type) {
                case CONSTANT -> "Value";
                case HYSTERESIS -> "Low";
                default -> "Const B";
            };
            guiGraphics.drawString(font, label, 10, y, 0x9BB2C4, false);
            y += 22;
        }
        if (type == LogicPartType.HYSTERESIS) {
            guiGraphics.drawString(font, "High", 10, y, 0x9BB2C4, false);
        }

        guiGraphics.drawString(font, "Out: " + menu.outputValue(), 78, imageHeight - 22, 0x5CE2FF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
