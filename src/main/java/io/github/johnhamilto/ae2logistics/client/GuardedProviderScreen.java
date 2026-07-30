package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload;
import io.github.johnhamilto.ae2logistics.menu.GuardedProviderMenu;

public class GuardedProviderScreen extends AEBaseScreen<GuardedProviderMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int PASS = 0x2E8B57;
    private static final int HOLD = 0xA8760B;

    private AETextField guardChannelBox;
    private AETextField guardValueBox;
    private AETextField priorityChannelBox;
    private AETextField basePriorityBox;
    private int guardOp;
    private boolean gateExecution;

    public GuardedProviderScreen(GuardedProviderMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 231;
    }

    @Override
    protected void init() {
        super.init();
        guardOp = menu.guardOp;
        gateExecution = menu.gateExecution;

        guardChannelBox = new AETextField(style, font, leftPos + 78, topPos + 44, 112, 16);
        guardChannelBox.setBordered(false);
        guardChannelBox.setMaxLength(80);
        guardChannelBox.setValue(menu.guardChannel);
        addRenderableWidget(guardChannelBox);

        addRenderableWidget(new CycleButton(leftPos + 78, topPos + 66, 28, 16,
                Component.literal(GuardedPattern.OPS[guardOp]), (b, dir) -> {
                    guardOp = Math.floorMod(guardOp + dir, GuardedPattern.OPS.length);
                    b.setMessage(Component.literal(GuardedPattern.OPS[guardOp]));
                }));

        guardValueBox = new AETextField(style, font, leftPos + 112, topPos + 66, 78, 16);
        guardValueBox.setBordered(false);
        guardValueBox.setMaxLength(19);
        guardValueBox.setValue(Long.toString(menu.guardValue));
        addRenderableWidget(guardValueBox);

        addRenderableWidget(new AE2Button(leftPos + 10, topPos + 88, 120, 18,
                Component.literal(gateLabel()), b -> {
                    gateExecution = !gateExecution;
                    b.setMessage(Component.literal(gateLabel()));
                }));

        priorityChannelBox = new AETextField(style, font, leftPos + 78, topPos + 112, 74, 16);
        priorityChannelBox.setBordered(false);
        priorityChannelBox.setMaxLength(80);
        priorityChannelBox.setValue(menu.priorityChannel);
        addRenderableWidget(priorityChannelBox);

        basePriorityBox = new AETextField(style, font, leftPos + 158, topPos + 112, 32, 16);
        basePriorityBox.setBordered(false);
        basePriorityBox.setMaxLength(9);
        basePriorityBox.setValue(Integer.toString(menu.basePriority));
        addRenderableWidget(basePriorityBox);
    }

    private String gateLabel() {
        return gateExecution ? "Gate: plan + push" : "Gate: plan only";
    }

    private String snapshot() {
        return guardChannelBox.getValue() + '\0' + guardOp + '\0' + guardValueBox.getValue()
                + '\0' + gateExecution + '\0' + priorityChannelBox.getValue()
                + '\0' + basePriorityBox.getValue();
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
        long value;
        try {
            value = Long.parseLong(guardValueBox.getValue().trim());
        } catch (NumberFormatException e) {
            value = 0;
        }
        int basePriority;
        try {
            basePriority = Integer.parseInt(basePriorityBox.getValue().trim());
        } catch (NumberFormatException e) {
            basePriority = 0;
        }
        PacketDistributor.sendToServer(new ConfigureGuardPayload(
                menu.pos, guardChannelBox.getValue(), guardOp, value, gateExecution,
                priorityChannelBox.getValue(), basePriority));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var status = menu.guardPassing() ? "PASS" : "HOLD";
        var statusText = status + "  p" + menu.livePriority();
        guiGraphics.drawString(font, statusText, imageWidth - 10 - font.width(statusText), 6,
                menu.guardPassing() ? PASS : HOLD, false);
        guiGraphics.drawString(font, "Guard", 10, 48, LABEL, false);
        guiGraphics.drawString(font, "passes if", 10, 70, HINT, false);
        guiGraphics.drawString(font, "Priority", 10, 116, LABEL, false);
    }
}
