package io.github.johnhamilto.ae2logistics.signal;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

import appeng.api.networking.IGridNodeService;

/**
 * A node service exposed by logic parts. The {@link SignalService} discovers these,
 * topologically sorts the channel dataflow graph, and evaluates every node once per tick.
 */
public interface ILogicNode extends IGridNodeService {

    /** Channels this node reads. Used to build dataflow edges; must match evaluate(). */
    Set<Identifier> readChannels();

    /** The single channel this node writes, or null for sink nodes (e.g. redstone out). */
    @Nullable
    Identifier writtenChannel();

    /**
     * Every channel this node may write this tick. Single-output nodes inherit the
     * default; multi-output nodes (e.g. the Job Monitor) override it and must
     * invalidate the graph when the set changes.
     */
    default Set<Identifier> writtenChannels() {
        var written = writtenChannel();
        return written == null ? Set.of() : Set.of(written);
    }

    /**
     * Compute this node's output from its inputs. Reads through the context observe
     * same-tick upstream writes; on a broken cycle edge they observe last tick's value.
     */
    void evaluate(LogicContext context);

    /** Deterministic tiebreak for evaluation order and cycle breaking. */
    long stableKey();

    interface LogicContext {
        long read(Identifier channel);

        /** Writes to {@link #writtenChannel()}. Multiple writers of a channel sum, saturating. */
        void write(long value);

        /** Writes a specific channel; it should be one of {@link #writtenChannels()}. */
        void write(Identifier channel, long value);
    }
}
