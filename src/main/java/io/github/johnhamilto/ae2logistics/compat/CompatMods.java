package io.github.johnhamilto.ae2logistics.compat;

import net.neoforged.fml.ModList;

/**
 * The AE2 suite mods present in the dev runtime (runtime-only Gradle deps, never real
 * dependencies). Integration tests and compat plots gate on {@link #loaded} and treat
 * absence as skip - the mod ids here are read from each jar's neoforge.mods.toml.
 */
public final class CompatMods {

    public static final String EXTENDED_AE = "extendedae";
    public static final String MEGA_CELLS = "megacells";
    public static final String APPLIED_MEKANISTICS = "appmek";
    public static final String MEKANISM = "mekanism";
    public static final String ME_REQUESTER = "merequester";
    public static final String AE2WTLIB = "ae2wtlib";
    public static final String NETWORK_ANALYSER = "ae2netanalyser";
    public static final String AE2_JEI_INTEGRATION = "ae2jeiintegration";

    private CompatMods() {
    }

    public static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
