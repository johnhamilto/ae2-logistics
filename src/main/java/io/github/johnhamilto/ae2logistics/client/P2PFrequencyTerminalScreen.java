package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class P2PFrequencyTerminalScreen extends AEBaseScreen<P2PFrequencyTerminalMenu> {

    private static final int LIST_X = 10;
    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 12;

    private sealed interface Line permits P2PLine, MeshHeaderLine, MeshEndpointLine {
    }

    private record P2PLine(P2PFrequencyTerminalMenu.Row row) implements Line {
    }

    private record MeshHeaderLine(String frequency, int count, boolean flagged) implements Line {
    }

    private record MeshEndpointLine(P2PFrequencyTerminalMenu.MeshRow row) implements Line {
    }

    private final ScrollingRowList list = new ScrollingRowList(8, 228, LIST_Y, LIST_Y + 112, ROW_HEIGHT);
    private short targetFrequency;
    private boolean hasTarget;
    /** A marked MESH frequency; mutually exclusive with the P2P target above. */
    @Nullable
    private String meshTarget;
    @Nullable
    private Line selected;
    private AETextField nameBox;
    private AE2Button renameButton;
    private AE2Button markButton;
    private AE2Button retuneButton;

    public P2PFrequencyTerminalScreen(P2PFrequencyTerminalMenu menu, Inventory inventory,
            Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        list.register(widgets, "scrollbar");
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new WideTextField(style, font, leftPos + 10, topPos + imageHeight - 46, 130, 14);
        nameBox.setBordered(false);
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        renameButton = new AE2Button(leftPos + 146, topPos + imageHeight - 48, 80, 18,
                Component.literal("Rename"), b -> rename());
        markButton = new AE2Button(leftPos + 10, topPos + imageHeight - 26, 108, 18,
                Component.literal("Mark target"), b -> markTarget());
        retuneButton = new AE2Button(leftPos + 126, topPos + imageHeight - 26, 100, 18,
                Component.literal("Retune to target"), b -> retuneSelected());
        addRenderableWidget(renameButton);
        addRenderableWidget(markButton);
        addRenderableWidget(retuneButton);
    }

    private List<Line> buildLines() {
        var lines = new ArrayList<Line>();
        var meshRows = menu.meshRows;
        for (int i = 0; i < meshRows.size();) {
            var frequency = meshRows.get(i).frequency();
            int end = i;
            boolean flagged = false;
            while (end < meshRows.size() && meshRows.get(end).frequency().equals(frequency)) {
                if (meshRows.get(end).status() != MeshRegistry.STATUS_OK) {
                    flagged = true;
                }
                end++;
            }
            lines.add(new MeshHeaderLine(frequency, end - i, flagged));
            for (int k = i; k < end; k++) {
                lines.add(new MeshEndpointLine(meshRows.get(k)));
            }
            i = end;
        }
        for (var row : menu.rows) {
            lines.add(new P2PLine(row));
        }
        return lines;
    }

    private void markTarget() {
        if (selected instanceof P2PLine line) {
            targetFrequency = line.row().frequency();
            hasTarget = true;
            meshTarget = null;
        } else if (selected instanceof MeshHeaderLine header) {
            meshTarget = header.frequency();
            hasTarget = false;
        } else if (selected instanceof MeshEndpointLine endpointLine) {
            meshTarget = endpointLine.row().frequency();
            hasTarget = false;
        }
    }

    private void retuneSelected() {
        if (selected instanceof P2PLine line && hasTarget) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RETUNE, line.row().pos(), line.row().side(),
                    targetFrequency, "", ""));
        } else if (selected instanceof MeshEndpointLine endpointLine && meshTarget != null) {
            var row = endpointLine.row();
            if (row.sameGrid() && !row.frequency().equals(meshTarget)) {
                PacketDistributor.sendToServer(new io.github.johnhamilto.ae2logistics.menu.MeshRetunePayload(
                        menu.pos, (byte) menu.side.ordinal(), row.frequency(),
                        row.pos(), row.side(), row.dimension(), meshTarget));
            }
        }
    }

    /** Renaming a mesh row - header or endpoint - retags the whole frequency. */
    private void rename() {
        if (selected instanceof P2PLine line) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    line.row().frequency(), nameBox.getValue(), ""));
        } else if (selected instanceof MeshHeaderLine header) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_MESH_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    (short) 0, nameBox.getValue(), header.frequency()));
        } else if (selected instanceof MeshEndpointLine endpointLine) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_MESH_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    (short) 0, nameBox.getValue(), endpointLine.row().frequency()));
        }
    }

    /** No silent no-ops: a button that would do nothing right now renders disabled. */
    private void updateButtonStates() {
        if (renameButton == null) {
            return;
        }
        renameButton.active = selected != null;
        markButton.active = selected != null;
        retuneButton.active = canRetuneSelection();
    }

    private boolean canRetuneSelection() {
        if (selected instanceof P2PLine && hasTarget) {
            return true;
        }
        return selected instanceof MeshEndpointLine endpointLine && meshTarget != null
                && endpointLine.row().sameGrid()
                && !endpointLine.row().frequency().equals(meshTarget);
    }

    private static String freqLabel(P2PFrequencyTerminalMenu.Row row) {
        var hex = String.format("%04X", row.frequency() & 0xFFFF);
        return row.name().isBlank() ? hex : row.name() + " (" + hex + ")";
    }

    private static String capsLabel(int mask) {
        return MeshRegistry.describeTypes(mask);
    }

    private static String statusLabel(byte status) {
        return switch (status) {
            case MeshRegistry.STATUS_OFFLINE -> "off";
            case MeshRegistry.STATUS_ME_WAITING -> "wait";
            default -> "OK";
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
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        list.setRowCount(buildLines().size());
        updateButtonStates();
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        list.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var lines = buildLines();
        if (lines.isEmpty()) {
            guiGraphics.drawString(font, "No P2P tunnels or mesh endpoints", LIST_X + 2, LIST_Y + 4,
                    Palette.HINT, false);
        }

        list.drawRows(guiGraphics, (g, index, y) -> drawLine(g, lines.get(index), y));

        if (selected instanceof MeshEndpointLine endpointLine) {
            var row = endpointLine.row();
            var where = "at " + row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ()
                    + " (" + shortDimension(row.dimension()) + ")";
            if (meshTarget != null && !row.frequency().equals(meshTarget)) {
                where += row.sameGrid() ? " -> " + meshTarget : " (remote: cannot retune)";
            }
            guiGraphics.drawString(font, where, 10, imageHeight - 58, Palette.HINT, false);
        } else if (meshTarget != null) {
            guiGraphics.drawString(font, "Target: " + meshTarget, 10, imageHeight - 58,
                    Palette.WAIT, false);
        } else if (hasTarget) {
            guiGraphics.drawString(font, "Target: " + String.format("%04X", targetFrequency & 0xFFFF),
                    10, imageHeight - 58, Palette.WAIT, false);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, Line line, int y) {
        boolean isSelected = line.equals(selected);
        if (isSelected) {
            guiGraphics.fill(9, y - 2, 218, y + ROW_HEIGHT - 2, 0x332E6E9E);
        }

        if (line instanceof MeshHeaderLine header) {
            guiGraphics.drawString(font, "MESH", LIST_X, y, Palette.VALUE, false);
            var label = header.frequency();
            if (label.length() > 18) {
                label = label.substring(0, 17) + "..";
            }
            guiGraphics.drawString(font, label, LIST_X + 32, y,
                    isSelected ? Palette.VALUE : Palette.ROW, false);
            guiGraphics.drawString(font, "x" + header.count(), LIST_X + 162, y, Palette.HINT, false);
            if (header.flagged()) {
                guiGraphics.drawString(font, "!", LIST_X + 186, y, Palette.WAIT, false);
            }
        } else if (line instanceof MeshEndpointLine endpointLine) {
            var row = endpointLine.row();
            var roleText = switch (row.role()) {
                case MeshEndpointPart.ROLE_OUT -> "OUT";
                case MeshEndpointPart.ROLE_BOTH -> "BOTH";
                default -> "IN";
            };
            var roleColor = switch (row.role()) {
                case MeshEndpointPart.ROLE_OUT -> Palette.OUT;
                case MeshEndpointPart.ROLE_BOTH -> Palette.VALUE;
                default -> Palette.OK;
            };
            guiGraphics.drawString(font, roleText, LIST_X + 8, y, roleColor, false);
            var caps = capsLabel(row.capabilities());
            if (caps.length() > 12) {
                caps = caps.substring(0, 11) + "..";
            }
            guiGraphics.drawString(font, caps, LIST_X + 40, y, Palette.HINT, false);
            guiGraphics.drawString(font, row.sameGrid() ? "here" : "remote", LIST_X + 122, y,
                    row.sameGrid() ? Palette.HINT : Palette.REMOTE, false);
            guiGraphics.drawString(font, statusLabel(row.status()), LIST_X + 168, y,
                    statusColor(row.status()), false);
        } else if (line instanceof P2PLine p2pLine) {
            var row = p2pLine.row();
            boolean isTarget = hasTarget && row.frequency() == targetFrequency;
            var label = freqLabel(row);
            if (label.length() > 16) {
                label = label.substring(0, 15) + "..";
            }
            guiGraphics.drawString(font, label, LIST_X, y,
                    isTarget ? Palette.WAIT : isSelected ? Palette.VALUE : Palette.ROW, false);

            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId()));
            var typeName = item.getDescription().getString().replace(" P2P Tunnel", "");
            if (typeName.length() > 8) {
                typeName = typeName.substring(0, 8);
            }
            guiGraphics.drawString(font, typeName, LIST_X + 92, y, Palette.HINT, false);
            guiGraphics.drawString(font, row.output() ? "OUT" : "IN", LIST_X + 142, y,
                    row.output() ? Palette.OUT : Palette.OK, false);
            guiGraphics.drawString(font,
                    row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ(),
                    LIST_X + 166, y, Palette.HINT, false);
        }
    }

    private static String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = list.rowAt(mouseX, mouseY, leftPos, topPos);
        if (index >= 0) {
            var lines = buildLines();
            if (index < lines.size()) {
                selected = lines.get(index);
                if (selected instanceof P2PLine line) {
                    nameBox.setValue(line.row().name());
                } else if (selected instanceof MeshHeaderLine header) {
                    nameBox.setValue(header.frequency());
                } else if (selected instanceof MeshEndpointLine endpointLine) {
                    // Renaming targets the frequency, so pre-fill it here too.
                    nameBox.setValue(endpointLine.row().frequency());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.mouseScrolled(mouseX, mouseY, scrollY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
