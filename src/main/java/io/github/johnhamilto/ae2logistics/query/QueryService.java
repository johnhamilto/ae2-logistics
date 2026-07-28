package io.github.johnhamilto.ae2logistics.query;

import java.util.SortedMap;

import org.jetbrains.annotations.Nullable;

/**
 * Per-network named-query library and evaluation services. The library is replicated
 * across every Query Terminal on the grid - any one surviving terminal preserves it.
 */
public interface QueryService extends appeng.api.networking.IGridService {

    /** Merged name -&gt; source view across the grid's terminals. Names are lowercase. */
    SortedMap<String, String> library();

    void put(String name, String source);

    void remove(String name);

    /** Parse-cached compilation; null when the source has a syntax error. */
    @Nullable
    CompiledQuery compiled(String source);

    /** Evaluation context over the network's cached inventory; refreshed each tick. */
    QueryContext context();
}
