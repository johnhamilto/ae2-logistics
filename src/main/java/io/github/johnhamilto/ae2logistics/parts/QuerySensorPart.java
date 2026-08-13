package io.github.johnhamilto.ae2logistics.parts;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;

import io.github.johnhamilto.ae2logistics.AE2Logistics;
import io.github.johnhamilto.ae2logistics.menu.QuerySensorMenu;
import io.github.johnhamilto.ae2logistics.query.QueryService;
import io.github.johnhamilto.ae2logistics.signal.ILogicNode;
import io.github.johnhamilto.ae2logistics.signal.SignalService;

/**
 * Evaluates a query against network storage every tick and writes the total matching
 * amount to a signal channel. Queries feed signals feed guards - the query language's
 * bridge into the control plane.
 */
public class QuerySensorPart extends AEBasePart implements ILogicNode {

    @Nullable
    private Identifier outChannel;
    private String source = "";
    private Set<Identifier> signalReads = Set.of();

    public QuerySensorPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode()
                .setFlags()
                .setIdlePowerUsage(0.5)
                .addService(ILogicNode.class, this);
    }

    @Nullable
    public Identifier outChannel() {
        return outChannel;
    }

    public String source() {
        return source;
    }

    public void applySensorConfig(@Nullable Identifier channel, String newSource) {
        this.outChannel = channel;
        this.source = newSource.trim();
        var compiled = io.github.johnhamilto.ae2logistics.query.CompiledQuery.compile(this.source);
        this.signalReads = compiled != null ? Set.copyOf(compiled.referencedSignals()) : Set.of();
        getHost().markForSave();
        getMainNode().ifPresent(grid -> grid.getService(SignalService.class).invalidateGraph());
    }

    public long currentValue() {
        if (outChannel == null) {
            return 0;
        }
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        return node.getGrid().getService(SignalService.class).get(outChannel);
    }

    public boolean sourceValid() {
        return !source.isBlank()
                && io.github.johnhamilto.ae2logistics.query.CompiledQuery.compile(source) != null;
    }

    // --- ILogicNode ---

    @Override
    public Set<Identifier> readChannels() {
        return signalReads;
    }

    @Nullable
    @Override
    public Identifier writtenChannel() {
        return outChannel;
    }

    @Override
    public long stableKey() {
        var host = getHost().getBlockEntity();
        return host.getBlockPos().asLong() * 31 + (getSide() == null ? 6 : getSide().ordinal());
    }

    @Override
    public void evaluate(LogicContext context) {
        if (outChannel == null || source.isBlank()) {
            return;
        }
        var node = getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        var service = node.getGrid().getService(QueryService.class);
        var query = service.compiled(source);
        if (query == null) {
            return;
        }
        // Same-tick signal reads through the scheduler keep query signal() terms
        // consistent with the rest of the logic graph.
        var queryContext = service.context().withSignals(context::read);
        context.write(query.totalMatching(queryContext));
    }

    // --- part boilerplate ---

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 16);
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
                            (id, inventory, p) -> new QuerySensorMenu(id, inventory, this),
                            Component.translatable(getPartItem().asItem().getDescriptionId())),
                    buffer -> QuerySensorMenu.writeOpenData(buffer, this));
        }
        return true;
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        if (outChannel != null) {
            data.putString("out", outChannel.toString());
        }
        data.putString("query", source);
    }

    @Override
    public void readFromNBT(ValueInput data) {
        super.readFromNBT(data);
        outChannel = data.getString("out").map(Identifier::tryParse).orElse(null);
        source = data.getStringOr("query", "");
        var compiled = io.github.johnhamilto.ae2logistics.query.CompiledQuery.compile(source);
        signalReads = compiled != null ? Set.copyOf(compiled.referencedSignals()) : Set.of();
    }
}
