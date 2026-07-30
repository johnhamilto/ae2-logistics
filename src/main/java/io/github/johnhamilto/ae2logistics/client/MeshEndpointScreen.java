package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureMeshPayload;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;

public class MeshEndpointScreen extends AEBaseScreen<MeshEndpointMenu> {

    private static final int LABEL = 0x404040;
    private static final int HINT = 0x7b7b7b;
    private static final int OK = 0x2E8B57;
    private static final int WAIT = 0xA8760B;
    private static final int ALERT = 0xB33A36;

    private static final String[] ROLES = {"Role: Input", "Role: Output", "Role: Both"};
    private static final int[] TYPES = {MeshRegistry.TYPE_REDSTONE, MeshRegistry.TYPE_ITEM,
            MeshRegistry.TYPE_FLUID, MeshRegistry.TYPE_ENERGY, MeshRegistry.TYPE_SIGNAL,
            MeshRegistry.TYPE_ME, MeshRegistry.TYPE_PROVIDER};
    private static final String[] TYPE_NAMES = {"Redstone", "Items", "Fluids", "Energy", "Signals",
            "ME Link", "Provider"};

    private AETextField frequencyBox;
    private AETextField priorityBox;
    private byte roleValue;
    private int maskValue;

    public MeshEndpointScreen(MeshEndpointMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        this.imageWidth = 200;
        this.imageHeight = 251;
    }

    @Override
    protected void init() {
        super.init();
        roleValue = menu.role;
        maskValue = menu.capabilities;

        frequencyBox = new AETextField(style, font, leftPos + 10, topPos + 26, 104, 16);
        frequencyBox.setBordered(false);
        frequencyBox.setMaxLength(32);
        frequencyBox.setValue(menu.frequency);
        addRenderableWidget(frequencyBox);

        priorityBox = new AETextField(style, font, leftPos + 124, topPos + 26, 66, 16);
        priorityBox.setBordered(false);
        priorityBox.setMaxLength(11);
        priorityBox.setValue(Integer.toString(menu.priority));
        addRenderableWidget(priorityBox);

        addRenderableWidget(new CycleButton(leftPos + 10, topPos + 46, 88, 18,
                Component.literal(ROLES[roleValue]), (b, dir) -> {
                    roleValue = (byte) Math.floorMod(roleValue + dir, 3);
                    b.setMessage(Component.literal(ROLES[roleValue]));
                }));

        if (!menu.capabilitiesLocked) {
            for (int i = 0; i < TYPES.length; i++) {
                int type = TYPES[i];
                var name = TYPE_NAMES[i];
                int x = leftPos + 10 + (i % 3) * 62;
                int y = topPos + 68 + (i / 3) * 20;
                addRenderableWidget(new AE2Button(x, y, 58, 18,
                        Component.literal(toggleLabel(name, type)), b -> {
                            maskValue ^= type;
                            b.setMessage(Component.literal(toggleLabel(name, type)));
                        }));
            }
        }
    }

    private String attunedNames() {
        var names = new java.util.ArrayList<String>();
        for (int i = 0; i < TYPES.length; i++) {
            if ((maskValue & TYPES[i]) != 0) {
                names.add(TYPE_NAMES[i]);
            }
        }
        return names.isEmpty() ? "nothing" : String.join(", ", names);
    }

    private String toggleLabel(String name, int type) {
        return ((maskValue & type) != 0 ? "[x] " : "[ ] ") + name;
    }

    private String snapshot() {
        return frequencyBox.getValue() + '\0' + priorityBox.getValue() + '\0' + roleValue + '\0' + maskValue;
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
            case MeshRegistry.ME_STATE_LINKED -> " lane";
            case MeshRegistry.ME_STATE_STANDBY -> " standby";
            default -> "";
        };
        return "x" + menu.endpointCount() + me + " " + status;
    }

    private int statusColor() {
        return switch (menu.status()) {
            case MeshRegistry.STATUS_OFFLINE -> HINT;
            case MeshRegistry.STATUS_ME_WAITING -> WAIT;
            case MeshRegistry.STATUS_CABLED_LOOP -> ALERT;
            default -> OK;
        };
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var status = statusText();
        guiGraphics.drawString(font, status, imageWidth - 10 - font.width(status), 6, statusColor(), false);
        guiGraphics.drawString(font, "Frequency", 10, 16, LABEL, false);
        guiGraphics.drawString(font, "Priority", 124, 16, LABEL, false);
        if (menu.capabilitiesLocked) {
            guiGraphics.drawString(font, "Attuned: " + attunedNames(), 10, 74, LABEL, false);
            guiGraphics.drawString(font, "Fixed for this endpoint; craft the universal one to mix",
                    10, 88, HINT, false);
        }
        guiGraphics.drawString(font, "Filter - empty allows all; click with item or bucket", 10, 128,
                HINT, false);
    }
}
