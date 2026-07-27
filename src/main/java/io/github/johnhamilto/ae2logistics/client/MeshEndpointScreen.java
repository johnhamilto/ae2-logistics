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

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/mesh_panel.png");

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
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        roleValue = menu.role;
        maskValue = menu.capabilities;

        frequencyBox = new EditBox(font, leftPos + 10, topPos + 26, 104, 16, Component.empty());
        frequencyBox.setMaxLength(32);
        frequencyBox.setValue(menu.frequency);
        addRenderableWidget(frequencyBox);

        priorityBox = new EditBox(font, leftPos + 124, topPos + 26, 66, 16, Component.empty());
        priorityBox.setMaxLength(11);
        priorityBox.setValue(Integer.toString(menu.priority));
        addRenderableWidget(priorityBox);

        addRenderableWidget(Button.builder(Component.literal(ROLES[roleValue]), b -> {
            roleValue = (byte) ((roleValue + 1) % 3);
            b.setMessage(Component.literal(ROLES[roleValue]));
        }).bounds(leftPos + 10, topPos + 46, 88, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(leftPos + 104, topPos + 46, 86, 18).build());

        for (int i = 0; i < TYPES.length; i++) {
            int type = TYPES[i];
            var name = TYPE_NAMES[i];
            int x = leftPos + 10 + (i % 3) * 62;
            int y = topPos + 68 + (i / 3) * 20;
            addRenderableWidget(Button.builder(Component.literal(toggleLabel(name, type)), b -> {
                maskValue ^= type;
                b.setMessage(Component.literal(toggleLabel(name, type)));
            }).bounds(x, y, 58, 18).build());
        }
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

    private String statusText() {
        var status = switch (menu.status()) {
            case MeshRegistry.STATUS_OFFLINE -> "offline";
            case MeshRegistry.STATUS_ME_WAITING -> "no ME peer";
            case MeshRegistry.STATUS_CABLED_LOOP -> "CABLED LOOP";
            default -> "OK";
        };
        var me = switch (menu.meState()) {
            case MeshRegistry.ME_STATE_HUB -> " hub";
            case MeshRegistry.ME_STATE_LINKED -> " spoke";
            default -> "";
        };
        return "x" + menu.endpointCount() + me + " " + status;
    }

    private int statusColor() {
        return switch (menu.status()) {
            case MeshRegistry.STATUS_OFFLINE -> 0x5A6B7C;
            case MeshRegistry.STATUS_ME_WAITING -> 0xF5C542;
            case MeshRegistry.STATUS_CABLED_LOOP -> 0xE0524E;
            default -> 0x6FDB6F;
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        for (int i = 0; i < MeshEndpointPart.FILTER_SLOTS; i++) {
            slotFrame(guiGraphics, MeshEndpointMenu.FILTER_X + i * 18, MeshEndpointMenu.FILTER_Y);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(guiGraphics, MeshEndpointMenu.INV_X + col * 18, MeshEndpointMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(guiGraphics, MeshEndpointMenu.INV_X + col * 18, MeshEndpointMenu.HOTBAR_Y);
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
        var status = statusText();
        guiGraphics.drawString(font, status, imageWidth - 10 - font.width(status), 6, statusColor(), false);
        guiGraphics.drawString(font, "Frequency", 10, 16, 0x9BB2C4, false);
        guiGraphics.drawString(font, "Priority", 124, 16, 0x9BB2C4, false);
        guiGraphics.drawString(font, "Filter - empty allows all; click with item or bucket", 10, 108,
                0x5A6B7C, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
