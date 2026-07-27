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
import io.github.johnhamilto.ae2logistics.menu.ConfigureMeshPayload;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class MeshEndpointScreen extends AbstractContainerScreen<MeshEndpointMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/logic_panel.png");

    private static final String[] ROLES = {"Role: Input", "Role: Output", "Role: Both"};
    private static final int[] TYPES = {MeshRegistry.TYPE_REDSTONE, MeshRegistry.TYPE_ITEM,
            MeshRegistry.TYPE_FLUID, MeshRegistry.TYPE_ENERGY, MeshRegistry.TYPE_SIGNAL,
            MeshRegistry.TYPE_ME};
    private static final String[] TYPE_NAMES = {"Redstone", "Items", "Fluids", "Energy", "Signals", "ME Link"};

    private EditBox frequencyBox;
    private EditBox priorityBox;
    private byte roleValue;
    private int maskValue;

    public MeshEndpointScreen(MeshEndpointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        roleValue = menu.role;
        maskValue = menu.capabilities;

        frequencyBox = new EditBox(font, leftPos + 78, topPos + 18, 112, 16, Component.empty());
        frequencyBox.setMaxLength(32);
        frequencyBox.setValue(menu.frequency);
        addRenderableWidget(frequencyBox);

        priorityBox = new EditBox(font, leftPos + 78, topPos + 40, 112, 16, Component.empty());
        priorityBox.setMaxLength(11);
        priorityBox.setValue(Integer.toString(menu.priority));
        addRenderableWidget(priorityBox);

        addRenderableWidget(Button.builder(Component.literal(ROLES[roleValue]), b -> {
            roleValue = (byte) ((roleValue + 1) % 3);
            b.setMessage(Component.literal(ROLES[roleValue]));
        }).bounds(leftPos + 78, topPos + 62, 112, 18).build());

        for (int i = 0; i < TYPES.length; i++) {
            int type = TYPES[i];
            var name = TYPE_NAMES[i];
            int x = leftPos + 10 + (i % 3) * 62;
            int y = topPos + 88 + (i / 3) * 22;
            addRenderableWidget(Button.builder(Component.literal(toggleLabel(name, type)), b -> {
                maskValue ^= type;
                b.setMessage(Component.literal(toggleLabel(name, type)));
            }).bounds(x, y, 58, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 60, 18).build());
    }

    private String toggleLabel(String name, int type) {
        return ((maskValue & type) != 0 ? "[x] " : "[ ] ") + name;
    }

    private void apply() {
        int priority;
        try {
            priority = Integer.parseInt(priorityBox.getValue().trim());
        } catch (NumberFormatException e) {
            priority = 0;
        }
        PacketDistributor.sendToServer(new ConfigureMeshPayload(
                menu.pos, (byte) menu.side.ordinal(), frequencyBox.getValue(), roleValue, priority, maskValue));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);
        guiGraphics.drawString(font, "Frequency", 10, 22, 0x9BB2C4, false);
        guiGraphics.drawString(font, "Priority", 10, 44, 0x9BB2C4, false);
        guiGraphics.drawString(font, "Two endpoints = P2P, more = mesh", 10, imageHeight - 44, 0x5A6B7C, false);
        if (menu.role == MeshEndpointPart.ROLE_BOTH || roleValue == MeshEndpointPart.ROLE_BOTH) {
            guiGraphics.drawString(font, "Both: sends and receives", 78, imageHeight - 22, 0x5A6B7C, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
