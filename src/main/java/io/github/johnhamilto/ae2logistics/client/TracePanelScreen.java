package io.github.johnhamilto.ae2logistics.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;

import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;
import io.github.johnhamilto.ae2logistics.menu.TracePanelActionPayload;
import io.github.johnhamilto.ae2logistics.menu.TracePanelMenu;

/**
 * The trace panel's management window: one row per bound channel with a remove
 * button, and a clear-all. The channel list comes straight from the client-side
 * block entity (the master syncs it to chunk watchers), so it stays live while
 * the window is open - no menu-side replication.
 */
public class TracePanelScreen extends AEBaseScreen<TracePanelMenu> {

    private static final int ROW_TOP = 22;
    private static final int ROW_HEIGHT = 16;

    private final AE2Button[] removeButtons = new AE2Button[TracePanelBlockEntity.MAX_CHANNELS];
    private AE2Button clearButton;

    public TracePanelScreen(TracePanelMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < removeButtons.length; i++) {
            int row = i;
            removeButtons[i] = new AE2Button(leftPos + imageWidth - 60, topPos + ROW_TOP + row * ROW_HEIGHT,
                    50, 14, Component.literal("Remove"), b -> {
                        var bound = channels();
                        if (row < bound.size()) {
                            PacketDistributor.sendToServer(new TracePanelActionPayload(
                                    menu.pos, Optional.of(bound.get(row))));
                        }
                    });
            addRenderableWidget(removeButtons[i]);
        }
        clearButton = new AE2Button(leftPos + 10, topPos + imageHeight - 26, 70, 16,
                Component.literal("Clear all"), b -> PacketDistributor.sendToServer(
                        new TracePanelActionPayload(menu.pos, Optional.empty())));
        addRenderableWidget(clearButton);
    }

    private List<ResourceLocation> channels() {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(menu.pos) instanceof TracePanelBlockEntity panel) {
            return panel.boundChannels();
        }
        return List.of();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var bound = channels();
        for (int i = 0; i < removeButtons.length; i++) {
            removeButtons[i].visible = i < bound.size();
        }
        clearButton.active = !bound.isEmpty();
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var bound = channels();
        if (bound.isEmpty()) {
            guiGraphics.drawString(font, "No traces bound.", 10, ROW_TOP + 3, Palette.HINT, false);
            guiGraphics.drawString(font, "Click the panel with a bound Signal Card.",
                    10, ROW_TOP + 15, Palette.HINT, false);
            return;
        }
        for (int i = 0; i < bound.size() && i < removeButtons.length; i++) {
            guiGraphics.drawString(font, bound.get(i).toString(), 10, ROW_TOP + i * ROW_HEIGHT + 3,
                    Palette.VALUE, false);
        }
        guiGraphics.drawString(font,
                bound.size() + "/" + TracePanelBlockEntity.MAX_CHANNELS + " traces",
                10, imageHeight - 40, Palette.LABEL, false);
    }
}
