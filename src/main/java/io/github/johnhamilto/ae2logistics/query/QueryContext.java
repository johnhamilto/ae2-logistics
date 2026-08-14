package io.github.johnhamilto.ae2logistics.query;

import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import io.github.johnhamilto.ae2logistics.signal.SignalService;

/** Everything a query evaluation may consult; nulls degrade to zero/false. */
public final class QueryContext {

    @Nullable
    private final KeyCounter stacks;
    @Nullable
    private final ICraftingService crafting;
    @Nullable
    private final ToLongFunction<Identifier> signals;
    @Nullable
    private final Function<String, CompiledQuery> resolver;
    @Nullable
    private final net.minecraft.core.HolderLookup.Provider registries;

    public QueryContext(@Nullable KeyCounter stacks, @Nullable ICraftingService crafting,
            @Nullable ToLongFunction<Identifier> signals,
            @Nullable Function<String, CompiledQuery> resolver) {
        this(stacks, crafting, signals, resolver, null);
    }

    public QueryContext(@Nullable KeyCounter stacks, @Nullable ICraftingService crafting,
            @Nullable ToLongFunction<Identifier> signals,
            @Nullable Function<String, CompiledQuery> resolver,
            @Nullable net.minecraft.core.HolderLookup.Provider registries) {
        this.stacks = stacks;
        this.crafting = crafting;
        this.signals = signals;
        this.resolver = resolver;
        this.registries = registries;
    }

    public static QueryContext of(IGrid grid, @Nullable Function<String, CompiledQuery> resolver) {
        var signalService = grid.getService(SignalService.class);
        var pivot = grid.getPivot();
        return new QueryContext(
                grid.getStorageService().getCachedInventory(),
                grid.getCraftingService(),
                signalService == null ? null : signalService::get,
                resolver,
                pivot == null || pivot.getLevel() == null ? null : pivot.getLevel().registryAccess());
    }

    /** Same data, different signal lookup (e.g. the scheduler's same-tick reads). */
    public QueryContext withSignals(ToLongFunction<Identifier> signalLookup) {
        return new QueryContext(stacks, crafting, signalLookup, resolver, registries);
    }

    /** For the data: term's component serialization; null degrades the term to false. */
    @Nullable
    public net.minecraft.core.HolderLookup.Provider registries() {
        return registries;

    }

    @Nullable
    public KeyCounter stacks() {
        return stacks;
    }

    public long stored(AEKey key) {
        return stacks == null ? 0 : Math.max(0, stacks.get(key));
    }

    public boolean craftable(AEKey key) {
        return crafting != null && crafting.isCraftable(key);
    }

    public long signal(Identifier channel) {
        return signals == null ? 0 : signals.applyAsLong(channel);
    }

    @Nullable
    public CompiledQuery resolve(String name) {
        return resolver == null ? null : resolver.apply(name);
    }
}
