package io.github.johnhamilto.ae2logistics.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;

import io.github.johnhamilto.ae2logistics.parts.QueryTerminalPart;

public class QueryGridService implements QueryService, IGridServiceProvider {

    private final IGrid grid;
    private final Set<QueryTerminalPart> terminals = new LinkedHashSet<>();
    private final Map<String, CompiledQuery> parseCache = new HashMap<>();

    private long tickCounter;
    private long contextTick = -1;
    @Nullable
    private QueryContext cachedContext;

    public QueryGridService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        if (gridNode.getOwner() instanceof QueryTerminalPart terminal) {
            terminals.add(terminal);
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (gridNode.getOwner() instanceof QueryTerminalPart terminal) {
            terminals.remove(terminal);
        }
    }

    @Override
    public void onServerStartTick() {
        tickCounter++;
    }

    @Override
    public SortedMap<String, String> library() {
        var merged = new TreeMap<String, String>();
        var sorted = new ArrayList<>(terminals);
        sorted.sort(Comparator.comparingLong(QueryTerminalPart::stableKey));
        for (var terminal : sorted) {
            for (var entry : terminal.savedQueries().entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    @Override
    public void put(String name, String source) {
        var key = name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return;
        }
        for (var terminal : terminals) {
            terminal.putQuery(key, source);
        }
    }

    @Override
    public void remove(String name) {
        var key = name.trim().toLowerCase(Locale.ROOT);
        for (var terminal : terminals) {
            terminal.removeQuery(key);
        }
    }

    @Override
    @Nullable
    public CompiledQuery compiled(String source) {
        var cached = parseCache.get(source);
        if (cached != null) {
            return cached;
        }
        var compiled = CompiledQuery.compile(source);
        if (compiled != null) {
            if (parseCache.size() >= 256) {
                parseCache.clear();
            }
            parseCache.put(source, compiled);
        }
        return compiled;
    }

    @Override
    public QueryContext context() {
        if (contextTick != tickCounter || cachedContext == null) {
            contextTick = tickCounter;
            cachedContext = QueryContext.of(grid, name -> {
                var source = library().get(name);
                return source == null ? null : compiled(source);
            });
        }
        return cachedContext;
    }
}
