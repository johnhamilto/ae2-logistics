package io.github.johnhamilto.ae2logistics.signal;

import java.util.Map;

import net.minecraft.resources.Identifier;

import appeng.api.networking.IGridService;

/**
 * Grid-wide signal state. There is exactly one value per channel per network; Register
 * Banks persist manually-set channels and logic parts recompute their outputs every tick.
 */
public interface SignalService extends IGridService {

    long get(Identifier channel);

    /**
     * Sets a stored (manually written) value. Stored values are the base layer: a channel
     * driven by a logic part reports the computed value instead.
     */
    void setStored(Identifier channel, long value);

    /** The committed channel view currently visible to storage, terminals, and emitters. */
    Map<Identifier, Long> committed();

    /** Like {@link #committed()} but without external (mesh-bridged) contributions. */
    Map<Identifier, Long> localCommitted();

    /** Re-run graph discovery before the next evaluation, e.g. after a part was reconfigured. */
    void invalidateGraph();

    /**
     * Sets an external contribution (e.g. a mesh bridge from another network). Values sum
     * with other layers per channel; pass an empty map to withdraw the source entirely.
     */
    void setExternal(Object source, Map<Identifier, Long> values);

    /**
     * History samples for a tracked channel, oldest first, sampled once per second over
     * the last five minutes. Empty if the channel is not tracked (only the first
     * {@link #MAX_TRACKED_CHANNELS} channels get history).
     */
    long[] history(Identifier channel);

    int MAX_TRACKED_CHANNELS = 64;
    int HISTORY_SAMPLES = 300;
    int SAMPLE_INTERVAL_TICKS = 20;
}
