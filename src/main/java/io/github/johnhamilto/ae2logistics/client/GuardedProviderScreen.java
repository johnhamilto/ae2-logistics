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
import io.github.johnhamilto.ae2logistics.crafting.GuardedPattern;
import io.github.johnhamilto.ae2logistics.menu.ConfigureGuardPayload;
import io.github.johnhamilto.ae2logistics.menu.GuardedProviderMenu;

public class GuardedProviderScreen extends AbstractContainerScreen<GuardedProviderMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/mesh_panel.png");

    private EditBox guardChannelBox;
    private EditBox guardValueBox;
    private EditBox priorityChannelBox;
    private EditBox basePriorityBox;
    private int guardOp;
    private boolean gateExecution;

    public GuardedProviderScreen(GuardedProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        guardOp = menu.guardOp;
        gateExecution = menu.gateExecution;

        guardChannelBox = new EditBox(font, leftPos + 78, topPos + 44, 112, 16, Component.empty());
        guardChannelBox.setMaxLength(80);
        guardChannelBox.setValue(menu.guardChannel);
        addRenderableWidget(guardChannelBox);

        addRenderableWidget(Button.builder(Component.literal(GuardedPattern.OPS[guardOp]), b -> {
            guardOp = (guardOp + 1) % GuardedPattern.OPS.length;
            b.setMessage(Component.literal(GuardedPattern.OPS[guardOp]));
        }).bounds(leftPos + 78, topPos + 66, 28, 16).build());

        guardValueBox = new EditBox(font, leftPos + 112, topPos + 66, 78, 16, Component.empty());
        guardValueBox.setMaxLength(19);
        guardValueBox.setValue(Long.toString(menu.guardValue));
        addRenderableWidget(guardValueBox);

        addRenderableWidget(Button.builder(Component.literal(gateLabel()), b -> {
            gateExecution = !gateExecution;
            b.setMessage(Component.literal(gateLabel()));
        }).bounds(leftPos + 10, topPos + 88, 120, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 136, topPos + 88, 54, 18).build());

        priorityChannelBox = new EditBox(font, leftPos + 78, topPos + 112, 74, 16, Component.empty());
        priorityChannelBox.setMaxLength(80);
        priorityChannelBox.setValue(menu.priorityChannel);
        addRenderableWidget(priorityChannelBox);

        basePriorityBox = new EditBox(font, leftPos + 158, topPos + 112, 32, 16, Component.empty());
        basePriorityBox.setMaxLength(9);
        basePriorityBox.setValue(Integer.toString(menu.basePriority));
        addRenderableWidget(basePriorityBox);
    }

    private String gateLabel() {
        return gateExecution ? "Gate: plan + push" : "Gate: plan only";
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        for (int i = 0; i < 9; i++) {
            slotFrame(guiGraphics, GuardedProviderMenu.PATTERN_X + i * 18, GuardedProviderMenu.PATTERN_Y);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(guiGraphics, GuardedProviderMenu.INV_X + col * 18,
                        GuardedProviderMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(guiGraphics, GuardedProviderMenu.INV_X + col * 18, GuardedProviderMenu.HOTBAR_Y);
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
        var status = menu.guardPassing() ? "PASS" : "HOLD";
        var statusText = status + "  p" + menu.livePriority();
        guiGraphics.drawString(font, statusText, imageWidth - 10 - font.width(statusText), 6,
                menu.guardPassing() ? 0x6FDB6F : 0xF5C542, false);
        guiGraphics.drawString(font, "Guard", 10, 48, 0x9BB2C4, false);
        guiGraphics.drawString(font, "passes if", 10, 70, 0x5A6B7C, false);
        guiGraphics.drawString(font, "Priority", 10, 116, 0x9BB2C4, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
