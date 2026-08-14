package io.github.johnhamilto.ae2logistics.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEItemKey;

/**
 * The data: term's engine. An item's component PATCH (what deliberately differs
 * from the item's defaults - where mods like Apotheosis put their attributes,
 * and where classic-NBT custom_data lands) serializes to a tag tree, cached per
 * key since keys are immutable. Rules navigate the TREE by path and only then
 * glob at the located node, so a "lightning" in a lore line never trips an affix
 * rule. The V3 click-to-filter editor is designed to emit these same path rules.
 */
public final class ItemData {

    private static final Map<AEItemKey, CompoundTag> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ItemData() {
    }

    /** The serialized component patch, keyed by component id ("minecraft:custom_name", ...). */
    public static CompoundTag componentsTree(AEItemKey key, HolderLookup.Provider registries) {
        return CACHE.computeIfAbsent(key, k -> {
            var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var encoded = AEItemKey.CODEC.encodeStart(ops, k).result().orElse(null);
            return encoded instanceof CompoundTag compound
                    ? compound.getCompoundOrEmpty("components")
                    : new CompoundTag();
        });
    }

    /**
     * Navigate by dot-separated path ({@code *} matches any child, list indices are
     * numeric), then: glob null = the node must exist; glob set = some located
     * node's serialized text must match it (case-insensitive, {@code *} wildcards).
     * An empty path targets the whole tree.
     */
    public static boolean matches(CompoundTag tree, String path, @Nullable String glob) {
        var nodes = new ArrayList<Tag>();
        if (path.isEmpty()) {
            nodes.add(tree);
        } else {
            collect(tree, path.split("\\."), 0, nodes);
        }
        if (nodes.isEmpty()) {
            return false;
        }
        if (glob == null) {
            return true;
        }
        for (var node : nodes) {
            if (globMatches(node.toString().toLowerCase(Locale.ROOT), glob)) {
                return true;
            }
        }
        return false;
    }

    private static void collect(Tag node, String[] segments, int index, List<Tag> out) {
        if (index >= segments.length) {
            out.add(node);
            return;
        }
        var segment = segments[index];
        if (node instanceof CompoundTag compound) {
            if (segment.equals("*")) {
                for (var childKey : compound.keySet()) {
                    collect(compound.get(childKey), segments, index + 1, out);
                }
            } else if (compound.contains(segment)) {
                collect(compound.get(segment), segments, index + 1, out);
            }
        } else if (node instanceof ListTag list) {
            if (segment.equals("*")) {
                for (var child : list) {
                    collect(child, segments, index + 1, out);
                }
            } else {
                try {
                    int i = Integer.parseInt(segment);
                    if (i >= 0 && i < list.size()) {
                        collect(list.get(i), segments, index + 1, out);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /** Wildcard-only glob, both sides lowercased by the callers. */
    static boolean globMatches(String text, String glob) {
        return globMatches(text, 0, glob, 0);
    }

    private static boolean globMatches(String text, int ti, String glob, int gi) {
        while (gi < glob.length()) {
            char g = glob.charAt(gi);
            if (g == '*') {
                // Collapse runs; a trailing star matches the rest.
                while (gi < glob.length() && glob.charAt(gi) == '*') {
                    gi++;
                }
                if (gi == glob.length()) {
                    return true;
                }
                for (int t = ti; t <= text.length(); t++) {
                    if (globMatches(text, t, glob, gi)) {
                        return true;
                    }
                }
                return false;
            }
            if (ti >= text.length() || text.charAt(ti) != g) {
                return false;
            }
            ti++;
            gi++;
        }
        return ti == text.length();
    }
}
