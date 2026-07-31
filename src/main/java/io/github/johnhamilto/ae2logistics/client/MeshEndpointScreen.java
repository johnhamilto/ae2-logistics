package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;

import io.github.johnhamilto.ae2logistics.menu.ConfigureMeshPayload;
import io.github.johnhamilto.ae2logistics.menu.MeshEndpointMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;

public class MeshEndpointScreen extends AEBaseScreen<MeshEndpointMenu> {

    private static final String[] ROLES = {"Role: Input", "Role: Output", "Role: Both"};

    // Vertical rhythm: 6px between control groups, 8px container inset at the bottom.
    private final ScrollingRowList roster = new ScrollingRowList(8, 196, 82, 170, 17);

    private AETextField frequencyBox;
    private AETextField priorityBox;
    byte roleValue;
    int maskValue;
    /** Screen state survives init() re-runs (returning from the transports sub-screen). */
    private boolean restored;
    private String frequencyValue;
    private String priorityValue;

    public MeshEndpointScreen(MeshEndpointMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        // Toolbar buttons and composite widgets must exist before init(): both are
        // injected into the screen there, so later additions never render.
        if (!menu.capabilitiesLocked) {
            var cog = new IconButton(b -> switchToScreen(new MeshTransportsScreen(this))) {
                @Override
                protected Icon getIcon() {
                    return Icon.COG;
                }
            };
            cog.setMessage(Component.literal("Configure Transports"));
            addToLeftToolbar(cog);
        }
        roster.register(widgets, "scrollbar");
        roster.setRowCount(menu.roster().size());
    }

    @Override
    protected void init() {
        super.init();
        // One style doc serves eight part items; the window titles as whichever was clicked.
        setTextContent("dialog_title", getTitle());
        if (!restored) {
            restored = true;
            roleValue = menu.role;
            maskValue = menu.capabilities;
            frequencyValue = menu.frequency;
            priorityValue = Integer.toString(menu.priority);
        }

        frequencyBox = new AETextField(style, font, leftPos + 10, topPos + 26, 104, 16);
        frequencyBox.setBordered(false);
        frequencyBox.setMaxLength(32);
        frequencyBox.setValue(frequencyValue);
        addRenderableWidget(frequencyBox);

        priorityBox = new AETextField(style, font, leftPos + 124, topPos + 26, 66, 16);
        priorityBox.setBordered(false);
        priorityBox.setMaxLength(11);
        priorityBox.setValue(priorityValue);
        addRenderableWidget(priorityBox);

        addRenderableWidget(new CycleButton(leftPos + 10, topPos + 48, 88, 18,
                Component.literal(ROLES[roleValue]), (b, dir) -> {
                    roleValue = (byte) Math.floorMod(roleValue + dir, 3);
                    b.setMessage(Component.literal(ROLES[roleValue]));
                }));
    }

    /** The transports sub-screen edits the mask and pushes it through immediately. */
    void setMaskValue(int mask) {
        maskValue = mask;
        apply();
        autoApply.sent(snapshot());
    }

    private String snapshot() {
        return frequencyBox.getValue() + '\0' + priorityBox.getValue() + '\0' + roleValue + '\0' + maskValue;
    }

    private final AutoApply autoApply = new AutoApply();

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // The server re-pushes the roster after config edits; follow its size.
        roster.setRowCount(menu.roster().size());
        frequencyValue = frequencyBox.getValue();
        priorityValue = priorityBox.getValue();
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

    private static String roleLabel(byte role) {
        return switch (role) {
            case 1 -> "OUT";
            case 2 -> "BOTH";
            default -> "IN";
        };
    }

    private static String statusLabel(MeshEndpointMenu.EndpointInfo info) {
        var status = switch (info.status()) {
            case MeshRegistry.STATUS_OFFLINE -> "offline";
            case MeshRegistry.STATUS_ME_WAITING -> "no ME peer";
            default -> "OK";
        };
        return switch (info.meState()) {
            case MeshRegistry.ME_STATE_LINKED -> status + " lane";
            case MeshRegistry.ME_STATE_STANDBY -> status + " standby";
            default -> status;
        };
    }

    private static int statusColor(byte status) {
        return switch (status) {
            case MeshRegistry.STATUS_OFFLINE -> Palette.HINT;
            case MeshRegistry.STATUS_ME_WAITING -> Palette.WAIT;
            default -> Palette.OK;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (roster.mouseScrolled(mouseX, mouseY, deltaY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private static String coords(MeshEndpointMenu.EndpointInfo info) {
        return info.pos().getX() + ", " + info.pos().getY() + ", " + info.pos().getZ();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        roster.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.drawString(font, "Frequency", 10, 16, Palette.LABEL, false);
        guiGraphics.drawString(font, "Priority", 124, 16, Palette.LABEL, false);

        guiGraphics.drawString(font, "Linked Endpoints"
                + (menu.rosterTotal() > 0 ? " (" + menu.rosterTotal() + ")" : ""),
                10, 72, Palette.LABEL, false);
        if (menu.roster().isEmpty()) {
            guiGraphics.drawString(font, frequencyValue.isBlank()
                    ? "set a frequency to link" : "none on this network", 12, 86, Palette.HINT, false);
            return;
        }
        roster.drawRows(guiGraphics, (g, index, y) -> {
            var info = menu.roster().get(index);
            g.renderItem(info.connected(), 10, y);
            g.drawString(font, coords(info), 30, y + 4, info.self() ? Palette.LABEL : Palette.HINT, false);
            g.drawString(font, roleLabel(info.role()), 112, y + 4, Palette.LABEL, false);
            g.drawString(font, "p" + info.priority(), 134, y + 4, Palette.HINT, false);
            g.drawString(font, statusLabel(info), 152, y + 4, statusColor(info.status()), false);
        });
    }
}
