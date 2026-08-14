package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
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
    byte roleValue;
    int maskValue;
    /** Screen state survives init() re-runs (returning from the transports sub-screen). */
    private boolean restored;
    private String frequencyValue;

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
        // AE2's convention: priority lives behind the tab button in the top-right
        // corner, opening AE2's own priority picker.
        widgets.addOpenPriorityButton();
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
        }

        frequencyBox = new AETextField(style, font, leftPos + 10, topPos + 26, 104, 16);
        frequencyBox.setBordered(false);
        frequencyBox.setMaxLength(32);
        frequencyBox.setValue(frequencyValue);
        addRenderableWidget(frequencyBox);

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
        return frequencyBox.getValue() + '\0' + roleValue + '\0' + maskValue;
    }

    private final AutoApply autoApply = new AutoApply();

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        // The server re-pushes the roster after config edits; follow its size.
        roster.setRowCount(menu.roster().size());
        frequencyValue = frequencyBox.getValue();
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
        // Priority is edited through AE2's priority picker; re-send the open-time
        // value unchanged so config edits never clobber it.
        ClientPacketDistributor.sendToServer(new ConfigureMeshPayload(
                menu.pos, (byte) menu.side.ordinal(), frequencyBox.getValue(), roleValue,
                menu.priority, maskValue));    }

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

    /** Clicking a roster row closes the screen and flashes a box at that endpoint. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = roster.rowAt(event.x(), event.y(), leftPos, topPos);
        if (index >= 0 && index < menu.roster().size()) {
            var info = menu.roster().get(index);
            if (!info.self()) {
                locate(info);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Same dimension: flash the locator box, say how far, close so it is visible.
     * Another dimension: nothing to point a box at - say where it is, stay open.
     */
    private void locate(MeshEndpointMenu.EndpointInfo info) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var name = info.endpoint().getHoverName().getString();
        var at = coords(info);
        var here = player.level().dimension().identifier().toString();
        if (!info.dimension().isEmpty() && !info.dimension().equals(here)) {
            player.sendSystemMessage(Component.literal(name + " at " + at + " is in "
                    + dimensionLabel(info.dimension()))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        EndpointHighlighter.highlight(info.pos());
        int distance = (int) Math.round(Math.sqrt(
                player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(info.pos()))));
        player.sendSystemMessage(Component.literal(name + " at " + at + " highlighted in world, "
                + (distance == 1 ? "1 block" : distance + " blocks") + " away")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        onClose();
    }

    private static String dimensionLabel(String dimension) {
        return switch (dimension) {
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> "another dimension (" + dimension.substring(dimension.indexOf(':') + 1) + ")";
        };
    }

    private static String coords(MeshEndpointMenu.EndpointInfo info) {
        return info.pos().getX() + ", " + info.pos().getY() + ", " + info.pos().getZ();
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        roster.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, "Frequency", 10, 16, Palette.LABEL, false);
        guiGraphics.text(font, "Priority", 124, 16, Palette.LABEL, false);
        guiGraphics.text(font, Integer.toString(menu.priority), 124, 30, Palette.VALUE, false);
        guiGraphics.text(font, "Linked Endpoints"
                + (menu.rosterTotal() > 0 ? " (" + menu.rosterTotal() + ")" : ""),
                10, 72, Palette.LABEL, false);
        if (menu.roster().isEmpty()) {
            guiGraphics.text(font, frequencyValue.isBlank()
                    ? "set a frequency to link" : "none on this network", 12, 86, Palette.HINT, false);
            return;
        }
        int hovered = roster.rowAt(mouseX, mouseY, leftPos, topPos);
        roster.drawRows(guiGraphics, (g, index, y) -> {
            var info = menu.roster().get(index);
            if (index == hovered && !info.self()) {
                // Hover wash: these rows are clickable (they locate the endpoint).
                g.fill(9, y - 2, 186, y + 15, 0x332E6E9E);
            }
            if (info.self()) {
                // Accent bar on the well's left edge: "this row is the endpoint you opened".
                g.fill(8, y - 2, 10, y + 15, 0xFF2E6E9E);
            }
            g.item(info.connected(), 10, y);
            g.text(font, coords(info), 30, y + 4, info.self() ? Palette.LABEL : Palette.HINT, false);
            g.text(font, roleLabel(info.role()), 112, y + 4, Palette.LABEL, false);
            g.text(font, "p" + info.priority(), 134, y + 4, Palette.HINT, false);
            g.text(font, statusLabel(info), 152, y + 4, statusColor(info.status()), false);
        });
    }
}
