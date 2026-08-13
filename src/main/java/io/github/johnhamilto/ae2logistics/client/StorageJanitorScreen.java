package io.github.johnhamilto.ae2logistics.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;

import io.github.johnhamilto.ae2logistics.menu.JanitorTogglePayload;
import io.github.johnhamilto.ae2logistics.menu.StorageJanitorMenu;

public class StorageJanitorScreen extends AEBaseScreen<StorageJanitorMenu> {

    private AE2Button toggleButton;

    public StorageJanitorScreen(StorageJanitorMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        // Window size comes from the style doc's generatedBackground.
    }

    @Override
    protected void init() {
        super.init();
        toggleButton = new AE2Button(leftPos + 10, topPos + imageHeight - 28, 84, 18,
                Component.literal("Rejigger"), b -> ClientPacketDistributor.sendToServer(
                        new JanitorTogglePayload(menu.pos)));
        addRenderableWidget(toggleButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        toggleButton.setMessage(Component.literal(menu.running() ? "Stop" : "Rejigger"));
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.text(font, "Re-settles stored stock to wherever", 10, 20, Palette.HINT, false);
        guiGraphics.text(font, "filters and priorities now point.", 10, 32, Palette.HINT, false);

        String status;
        int color;
        if (menu.heldCount() > 0) {
            status = "holding " + menu.heldCount() + " stacks, retrying";
            color = Palette.WAIT;
        } else if (menu.running()) {
            status = "pass " + (menu.total() > 0 ? menu.done() + "/" + menu.total() : "...") + " kinds";
            color = Palette.VALUE;
        } else {
            status = menu.processed() > 0 ? "done" : "idle";
            color = menu.processed() > 0 ? Palette.OK : Palette.HINT;
        }
        guiGraphics.text(font, status, 10, 52, color, false);
        guiGraphics.text(font, "Processed: " + menu.processed(), 10, 66, Palette.LABEL, false);
    }
}
