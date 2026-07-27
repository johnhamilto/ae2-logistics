package io.github.johnhamilto.ae2logistics.parts;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.TracerTerminalMenu;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * A wall terminal showing every signal channel on the network with live values and
 * five-minute sparkline history.
 */
public class TracerTerminalPart extends AEBasePart {

    @PartModels
    public static final IPartModel MODEL = new PartModel(AE2Logistics.id("part/tracer_terminal"));

    public TracerTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5);
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new TracerTerminalMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> {
                        var host = getHost().getBlockEntity();
                        buffer.writeBlockPos(host.getBlockPos());
                        buffer.writeByte(getSide().ordinal());
                    });
        }
        return true;
    }

    @Nullable
    public SignalService service() {
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return null;
        }
        return node.getGrid().getService(SignalService.class);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }
}
