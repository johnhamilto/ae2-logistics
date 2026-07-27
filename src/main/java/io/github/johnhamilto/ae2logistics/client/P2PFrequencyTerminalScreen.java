package io.github.johnhamilto.ae2logistics.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.P2PActionPayload;
import io.github.johnhamilto.ae2logistics.menu.P2PFrequencyTerminalMenu;
import io.github.johnhamilto.ae2logistics.mesh.MeshRegistry;
import io.github.johnhamilto.ae2logistics.parts.MeshEndpointPart;

public class P2PFrequencyTerminalScreen extends AbstractContainerScreen<P2PFrequencyTerminalMenu> {

    private static final ResourceLocation BACKGROUND = AE2Logistics.id("textures/gui/tracer_panel.png");

    private static final int LIST_X = 10;
    private static final int LIST_Y = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 9;

    private sealed interface Line permits P2PLine, MeshHeaderLine, MeshEndpointLine {
    }

    private record P2PLine(P2PFrequencyTerminalMenu.Row row) implements Line {
    }

    private record MeshHeaderLine(String frequency, int count, boolean flagged) implements Line {
    }

    private record MeshEndpointLine(P2PFrequencyTerminalMenu.MeshRow row) implements Line {
    }

    private int scroll;
    private short targetFrequency;
    private boolean hasTarget;
    @Nullable
    private Line selected;
    private EditBox nameBox;

    public P2PFrequencyTerminalScreen(P2PFrequencyTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + 10, topPos + imageHeight - 46, 130, 14, Component.empty());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(leftPos + 146, topPos + imageHeight - 48, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Mark target"), b -> markTarget())
                .bounds(leftPos + 10, topPos + imageHeight - 26, 108, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Retune to target"), b -> retuneSelected())
                .bounds(leftPos + 126, topPos + imageHeight - 26, 100, 18).build());
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
        }
    }

    private void retuneSelected() {
        if (selected instanceof P2PLine line && hasTarget) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RETUNE, line.row().pos(), line.row().side(),
                    targetFrequency, "", ""));
        }
    }

    private void rename() {
        if (selected instanceof P2PLine line) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    line.row().frequency(), nameBox.getValue(), ""));
        } else if (selected instanceof MeshHeaderLine header) {
            PacketDistributor.sendToServer(new P2PActionPayload(
                    P2PActionPayload.ACTION_MESH_RENAME, menu.pos, (byte) menu.side.ordinal(),
                    (short) 0, nameBox.getValue(), header.frequency()));
        }
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
            case MeshRegistry.STATUS_CABLED_LOOP -> "LOOP";
            default -> "OK";
        };
    }

    private static int statusColor(byte status) {
        return switch (status) {
            case MeshRegistry.STATUS_OFFLINE -> 0x5A6B7C;
            case MeshRegistry.STATUS_ME_WAITING -> 0xF5C542;
            case MeshRegistry.STATUS_CABLED_LOOP -> 0xE0524E;
            default -> 0x6FDB6F;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 10, 6, 0xE0E6EB, false);

        var lines = buildLines();
        int max = Math.max(0, lines.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, max);

        if (lines.isEmpty()) {
            guiGraphics.drawString(font, "No P2P tunnels or mesh endpoints", LIST_X, LIST_Y + 4,
                    0x5A6B7C, false);
        }

        for (int i = 0; i < VISIBLE_ROWS && scroll + i < lines.size(); i++) {
            var line = lines.get(scroll + i);
            int y = LIST_Y + i * ROW_HEIGHT;
            boolean isSelected = line.equals(selected);
            if (isSelected) {
                guiGraphics.fill(LIST_X - 2, y - 1, LIST_X + 218, y + ROW_HEIGHT - 2, 0x3325C0E0);
            }

            if (line instanceof MeshHeaderLine header) {
                guiGraphics.drawString(font, "MESH", LIST_X, y, 0x5CE2FF, false);
                var label = header.frequency();
                if (label.length() > 18) {
                    label = label.substring(0, 17) + "..";
                }
                guiGraphics.drawString(font, label, LIST_X + 32, y,
                        isSelected ? 0x5CE2FF : 0xC7D3DE, false);
                guiGraphics.drawString(font, "x" + header.count(), LIST_X + 162, y, 0x9BB2C4, false);
                if (header.flagged()) {
                    guiGraphics.drawString(font, "!", LIST_X + 186, y, 0xF5C542, false);
                }
            } else if (line instanceof MeshEndpointLine endpointLine) {
                var row = endpointLine.row();
                var roleText = switch (row.role()) {
                    case MeshEndpointPart.ROLE_OUT -> "OUT";
                    case MeshEndpointPart.ROLE_BOTH -> "BOTH";
                    default -> "IN";
                };
                var roleColor = switch (row.role()) {
                    case MeshEndpointPart.ROLE_OUT -> 0xE08A4E;
                    case MeshEndpointPart.ROLE_BOTH -> 0x5CE2FF;
                    default -> 0x6FDB6F;
                };
                guiGraphics.drawString(font, roleText, LIST_X + 8, y, roleColor, false);
                var caps = capsLabel(row.capabilities());
                if (caps.length() > 12) {
                    caps = caps.substring(0, 11) + "..";
                }
                guiGraphics.drawString(font, caps, LIST_X + 40, y, 0x9BB2C4, false);
                guiGraphics.drawString(font, row.sameGrid() ? "here" : "remote", LIST_X + 122, y,
                        row.sameGrid() ? 0x8A9AA8 : 0xB08AE0, false);
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
                        isTarget ? 0xF5C542 : isSelected ? 0x5CE2FF : 0xC7D3DE, false);

                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId()));
                var typeName = item.getDescription().getString().replace(" P2P Tunnel", "");
                if (typeName.length() > 8) {
                    typeName = typeName.substring(0, 8);
                }
                guiGraphics.drawString(font, typeName, LIST_X + 92, y, 0x9BB2C4, false);
                guiGraphics.drawString(font, row.output() ? "OUT" : "IN", LIST_X + 142, y,
                        row.output() ? 0xE08A4E : 0x6FDB6F, false);
                guiGraphics.drawString(font,
                        row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ(),
                        LIST_X + 166, y, 0x8A9AA8, false);
            }
        }

        if (selected instanceof MeshEndpointLine endpointLine) {
            var row = endpointLine.row();
            guiGraphics.drawString(font,
                    "at " + row.pos().getX() + "," + row.pos().getY() + "," + row.pos().getZ()
                            + " (" + shortDimension(row.dimension()) + ")",
                    10, imageHeight - 58, 0x8A9AA8, false);
        } else if (hasTarget) {
            guiGraphics.drawString(font, "Target: " + String.format("%04X", targetFrequency & 0xFFFF),
                    10, imageHeight - 58, 0xF5C542, false);
        }
    }

    private static String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (localX >= LIST_X - 2 && localX < LIST_X + 218 && localY >= LIST_Y - 1
                && localY < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
            var lines = buildLines();
            int index = scroll + (localY - LIST_Y + 1) / ROW_HEIGHT;
            if (index >= 0 && index < lines.size()) {
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
        int max = Math.max(0, buildLines().size() - VISIBLE_ROWS);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY));
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
