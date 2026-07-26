package io.github.johnhamilto.ae2logistics.signal;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import appeng.api.networking.IGridService;

/**
 * Grid-wide signal state. There is exactly one value per channel per network; Register
 * Banks persist manually-set channels and logic parts recompute their outputs every tick.
 */
public interface SignalService extends IGridService {

    long get(ResourceLocation channel);

    /**
     * Sets a stored (manually written) value. Stored values are the base layer: a channel
     * driven by a logic part reports the computed value instead.
     */
    void setStored(ResourceLocation channel, long value);

    /** The committed channel view currently visible to storage, terminals, and emitters. */
    Map<ResourceLocation, Long> committed();

    /** Re-run graph discovery before the next evaluation, e.g. after a part was reconfigured. */
    void invalidateGraph();
}
