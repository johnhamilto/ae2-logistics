package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureSubnetLinkPayload;
import io.github.johnhamilto.ae2logistics.menu.SubnetLinkMenu;
import io.github.johnhamilto.ae2logistics.parts.SubnetLinkPart;

public class SubnetLinkScreen extends AEBaseScreen<SubnetLinkMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int OK = 0x2E8B57;
    private static final int ALERT = 0xB33A36;

    private static final String[] MODES = {
            "Subnet sees main", "Main sees subnet", "Both ways"};

    private AETextField priorityBox;
    private byte modeValue;

    public SubnetLinkScreen(SubnetLinkMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 231;
    }

    @Override
    protected void init() {
        super.init();
        modeValue = menu.mode;

        addRenderableWidget(new CycleButton(leftPos + 10, topPos + 26, 120, 18,
                Component.literal(MODES[modeValue]), (b, dir) -> {
                    modeValue = (byte) Math.floorMod(modeValue + dir, MODES.length);
                    b.setMessage(Component.literal(MODES[modeValue]));
                }));

        priorityBox = new AETextField(style, font, leftPos + 136, topPos + 27, 54, 16);
        priorityBox.setBordered(false);
        priorityBox.setMaxLength(11);
        priorityBox.setValue(Integer.toString(menu.priority));
        addRenderableWidget(priorityBox);

        addRenderableWidget(new AE2Button(leftPos + 10, topPos + 50, 88, 18,
                Component.literal("Apply"), b -> apply()));
    }

    private void apply() {
        int priority;
        try {
            priority = Integer.parseInt(priorityBox.getValue().trim());
        } catch (NumberFormatException e) {
            priority = 0;
        }
        PacketDistributor.sendToServer(new ConfigureSubnetLinkPayload(
                menu.pos, (byte) menu.side.ordinal(), modeValue, priority));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var status = (menu.linkActive() ? "online" : "offline")
                + " - subnet: " + menu.subnetSize() + " nodes";
        guiGraphics.drawString(font, status, imageWidth - 10 - font.width(status), 6,
                menu.linkActive() ? OK : ALERT, false);
        guiGraphics.drawString(font, "Window", 10, 16, LABEL, false);
        guiGraphics.drawString(font, "Priority", 136, 16, LABEL, false);
        guiGraphics.drawString(font, "The face carries a real subnet: cable it and", 10, 72, HINT, false);
        guiGraphics.drawString(font, "build with normal AE2 devices. Power passes", 10, 84, HINT, false);
        guiGraphics.drawString(font, "through; the window links the two storages.", 10, 96, HINT, false);
        guiGraphics.drawString(font, "Filter - empty allows all; click or drag items", 10, 108,
                HINT, false);
    }
}
