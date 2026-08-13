package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;

import io.github.johnhamilto.ae2logistics.menu.ConfigureJobMonitorPayload;
import io.github.johnhamilto.ae2logistics.menu.JobMonitorMenu;

public class JobMonitorScreen extends AEBaseScreen<JobMonitorMenu> {

    // Vertical rhythm: config rows at 18/40, summary at 64, board well below.
    private final ScrollingRowList board = new ScrollingRowList(8, 196, 74, 182, 17);

    private AETextField prefixBox;
    private AETextField stallBox;

    public JobMonitorScreen(JobMonitorMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
        board.register(widgets, "scrollbar");
        board.setRowCount(menu.board().size());
    }

    @Override
    protected void init() {
        super.init();
        prefixBox = new AETextField(style, font, leftPos + 78, topPos + 18, 112, 16);
        prefixBox.setBordered(false);
        prefixBox.setMaxLength(24);
        prefixBox.setValue(menu.prefix);
        addRenderableWidget(prefixBox);

        stallBox = new AETextField(style, font, leftPos + 78, topPos + 40, 112, 16);
        stallBox.setBordered(false);
        stallBox.setMaxLength(3);
        stallBox.setValue(Integer.toString(menu.stallSeconds));
        addRenderableWidget(stallBox);
    }

    private String snapshot() {
        return prefixBox.getValue() + '\0' + stallBox.getValue();
    }

    private final AutoApply autoApply = new AutoApply();

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        board.setRowCount(menu.board().size());
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
        int stallSeconds;
        try {
            stallSeconds = Integer.parseInt(stallBox.getValue().trim());
        } catch (NumberFormatException e) {
            stallSeconds = 10;
        }
        ClientPacketDistributor.sendToServer(new ConfigureJobMonitorPayload(
                menu.pos, (byte) menu.side.ordinal(), prefixBox.getValue(), stallSeconds));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (board.mouseScrolled(mouseX, mouseY, deltaY, leftPos, topPos, imageWidth)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void drawBG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        board.drawBackground(guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, "Prefix", 10, 22, Palette.LABEL, false);
        guiGraphics.text(font, "Stall (s)", 10, 44, Palette.LABEL, false);

        guiGraphics.text(font, "Jobs: " + menu.activeJobs()
                + (menu.stalledJobs() > 0 ? "  (" + menu.stalledJobs() + " stalled)" : "")
                + "   Pending: " + menu.pendingItems(),
                10, 64, menu.stalledJobs() > 0 ? Palette.ALERT : Palette.VALUE, false);

        if (menu.board().isEmpty()) {
            guiGraphics.text(font, "no crafting CPUs on this network", 12, 78,
                    Palette.HINT, false);
            return;
        }
        board.drawRows(guiGraphics, (g, index, y) -> {
            var row = menu.board().get(index);
            g.item(row.output(), 10, y);
            var name = row.cpuName().isBlank() ? "(unnamed)" : row.cpuName();
            g.text(font, truncate(name, 13), 30, y + 4,
                    row.cpuName().isBlank() ? Palette.MUTED : Palette.LABEL, false);
            if (row.busy()) {
                var left = Long.toString(row.remaining());
                g.text(font, left, 148 - font.width(left), y + 4, Palette.HINT, false);
                g.text(font, row.stalled() ? "stalled" : "crafting", 152, y + 4,
                        row.stalled() ? Palette.ALERT : Palette.OK, false);
            } else {
                g.text(font, "idle", 152, y + 4, Palette.MUTED, false);
            }
        });
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }
}
