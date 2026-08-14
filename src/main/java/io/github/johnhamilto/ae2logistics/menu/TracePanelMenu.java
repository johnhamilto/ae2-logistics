package io.github.johnhamilto.ae2logistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import appeng.menu.AEBaseMenu;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.block.TracePanelBlockEntity;

/**
 * Management window for a trace panel. Carries only the clicked position: the
 * channel list itself rides the block entity's normal client sync (the master
 * broadcasts formation + traces to chunk watchers), so the screen reads it from
 * the client-side block entity and actions go through {@link TracePanelActionPayload}.
 */
public class TracePanelMenu extends AEBaseMenu {

    public final BlockPos pos;

    public TracePanelMenu(int containerId, Inventory inventory, TracePanelBlockEntity panel) {
        super(AE2Logistics.TRACE_PANEL_MENU.get(), containerId, inventory, panel);
        this.pos = panel.getBlockPos();
    }

    public TracePanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(AE2Logistics.TRACE_PANEL_MENU.get(), containerId, inventory, null);
        this.pos = buffer.readBlockPos();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, TracePanelBlockEntity panel) {
        buffer.writeBlockPos(panel.getBlockPos());
    }
}
