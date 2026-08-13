package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload;
import io.github.johnhamilto.ae2logistics.menu.GuardedProviderMenu;

public class GuardedProviderScreen extends AEBaseScreen<GuardedProviderMenu> {


    private AETextField guardChannelBox;
    private AETextField guardValueBox;
    private AETextField priorityChannelBox;
    private AETextField basePriorityBox;
    private int guardOp;
    private boolean gateExecution;

    public GuardedProviderScreen(GuardedProviderMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // Generated chrome carries no slot art: give every active slot AE2's inset.
        for (var slot : menu.slots) {
            if (slot.isActive()) {
                Blitter.icon(Icon.SLOT_BACKGROUND)
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(guiGraphics);
            }
        }
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
        ClientPacketDistributor.sendToServer(new ConfigureGuardPayload(
                menu.pos, guardChannelBox.getValue(), guardOp, value, gateExecution,
                priorityChannelBox.getValue(), basePriority));
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var status = menu.guardPassing() ? "Palette.OK" : "Palette.WAIT";
        var statusText = status + "  p" + menu.livePriority();
        guiGraphics.text(font, statusText, imageWidth - 10 - font.width(statusText), 6,
                menu.guardPassing() ? Palette.OK : Palette.WAIT, false);
        guiGraphics.text(font, "Guard", 10, 48, Palette.LABEL, false);
        guiGraphics.text(font, "passes if", 10, 70, Palette.HINT, false);
        guiGraphics.text(font, "Priority", 10, 116, Palette.LABEL, false);
    }
}
