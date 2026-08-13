package io.github.johnhamilto.ae2logistics.query;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import io.github.johnhamilto.ae2logistics.signal.SignalMath;

/** A parsed query, evaluatable against keys in a {@link QueryContext}. */
public record CompiledQuery(String source, QueryParser.Node root) {

    private static final int MAX_REF_DEPTH = 8;

    @Nullable
    public static CompiledQuery compile(String source) {
        var result = QueryParser.parse(source);
        return result.ok() ? new CompiledQuery(source, result.root()) : null;
    }

    public boolean matches(AEKey key, QueryContext context) {
        return eval(root, key, context, 0);
    }

    /**
     * Queries range over item and fluid keys. Signal keys also live in the network
     * inventory (F1's design), but counting them would make a sensor read its own
     * output channel - instant feedback loop.
     */
    public static boolean isQueryableKey(AEKey key) {
        return !(key instanceof io.github.johnhamilto.ae2logistics.signal.SignalKey);
    }

    /** Sum of stored amounts over all matching keys, saturating. */
    public long totalMatching(QueryContext context) {
        var stacks = context.stacks();
        if (stacks == null) {
            return 0;
        }
        long total = 0;
        for (var entry : stacks) {
            if (entry.getLongValue() > 0 && isQueryableKey(entry.getKey())
                    && matches(entry.getKey(), context)) {
                total = SignalMath.add(total, entry.getLongValue());
            }
        }
        return total;
    }

    private static boolean eval(QueryParser.Node node, AEKey key, QueryContext context, int depth) {
        return switch (node) {
            case QueryParser.And and -> eval(and.left(), key, context, depth)
                    && eval(and.right(), key, context, depth);
            case QueryParser.Or or -> eval(or.left(), key, context, depth)
                    || eval(or.right(), key, context, depth);
            case QueryParser.Not not -> !eval(not.inner(), key, context, depth);
            case QueryParser.Mod mod -> key.getId().getNamespace().equals(mod.namespace());
            case QueryParser.Tag tag -> switch (key) {
                case AEItemKey itemKey -> itemKey.isTagged(tag.itemTag());
                case AEFluidKey fluidKey -> fluidKey.isTagged(tag.fluidTag());
                default -> false;
            };
            case QueryParser.Name name -> key.getDisplayName().getString()
                    .toLowerCase(Locale.ROOT).contains(name.substring());
            case QueryParser.Count count -> QueryParser.compare(context.stored(key), count.op(), count.value());
            case QueryParser.Craftable ignored -> context.craftable(key);
            case QueryParser.Stored ignored -> context.stored(key) > 0;
            case QueryParser.Damage damage -> damageMatches(key, damage);
            case QueryParser.Signal signal -> QueryParser.compare(context.signal(signal.channel()),
                    signal.op(), signal.value());
            case QueryParser.Ref ref -> {
                if (depth >= MAX_REF_DEPTH) {
                    yield false;
                }
                var resolved = context.resolve(ref.name());
                yield resolved != null && eval(resolved.root(), key, context, depth + 1);
            }
        };
    }

    /** Signal channels this query's own AST references (not through @refs). */
    public java.util.Set<net.minecraft.resources.Identifier> referencedSignals() {
        var channels = new java.util.HashSet<net.minecraft.resources.Identifier>();
        collectSignals(root, channels);
        return channels;
    }

    private static void collectSignals(QueryParser.Node node,
            java.util.Set<net.minecraft.resources.Identifier> out) {
        switch (node) {
            case QueryParser.And and -> {
                collectSignals(and.left(), out);
                collectSignals(and.right(), out);
            }
            case QueryParser.Or or -> {
                collectSignals(or.left(), out);
                collectSignals(or.right(), out);
            }
            case QueryParser.Not not -> collectSignals(not.inner(), out);
            case QueryParser.Signal signal -> out.add(signal.channel());
            default -> {
            }
        }
    }

    private static boolean damageMatches(AEKey key, QueryParser.Damage damage) {
        if (!(key instanceof AEItemKey itemKey)) {
            return false;
        }
        var stack = itemKey.toStack();
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        long percent = 100L * stack.getDamageValue() / max;
        return QueryParser.compare(percent, damage.op(), damage.value());
    }
}
