package io.github.johnhamilto.ae2logistics.signal;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/**
 * Registers are written by assignment only. MEStorage's default insert/extract refuse all
 * transfers, so buses, cells, and IO ports cannot move signals in or out.
 */
public final class SignalStorage implements MEStorage {

    private final Map<SignalKey, Long> values = new LinkedHashMap<>();

    public void set(SignalKey key, long value) {
        if (value <= 0) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    public long get(SignalKey key) {
        return values.getOrDefault(key, 0L);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : values.entrySet()) {
            out.add(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("Register Bank");
    }

    public ListTag save(HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : values.entrySet()) {
            var tag = entry.getKey().toTag(registries);
            tag.putLong("value", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    public void load(HolderLookup.Provider registries, ListTag list) {
        values.clear();
        for (Tag element : list) {
            if (element instanceof CompoundTag tag
                    && SignalKeyType.TYPE.loadKeyFromTag(registries, tag) instanceof SignalKey key) {
                values.put(key, tag.getLong("value"));
            }
        }
    }
}
