package io.github.johnhamilto.ae2logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance knobs. Values are read lazily with shipped defaults as fallback,
 * so block entities stay safe if queried before the config loads.
 */
public final class AE2LogisticsConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue SCHEDULER_ATTEMPT_INTERVAL = BUILDER
            .comment("Ticks between crafting attempts per Job Scheduler rule (rate limit).")
            .defineInRange("schedulerAttemptIntervalTicks", 200, 20, 2400);

    private static final ModConfigSpec.IntValue DENSE_WAP_RANGE = BUILDER
            .comment("Coverage radius in blocks for newly placed Dense Wireless Access Points.")
            .defineInRange("denseWapRange", 32, 4, 128);

    private static final ModConfigSpec.IntValue BRIDGE_RETUNE_INTERVAL = BUILDER
            .comment("Ticks between ME Wireless Bridge coverage checks and re-association.")
            .defineInRange("bridgeRetuneIntervalTicks", 20, 5, 200);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AE2LogisticsConfig() {
    }

    public static int schedulerAttemptIntervalTicks() {
        return readOr(SCHEDULER_ATTEMPT_INTERVAL, 200);
    }

    public static int denseWapRange() {
        return readOr(DENSE_WAP_RANGE, 32);
    }

    public static int bridgeRetuneIntervalTicks() {
        return readOr(BRIDGE_RETUNE_INTERVAL, 20);
    }

    private static int readOr(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
