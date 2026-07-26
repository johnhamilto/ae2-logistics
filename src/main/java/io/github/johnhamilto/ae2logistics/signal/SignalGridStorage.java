package io.github.johnhamilto.ae2logistics.signal;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/**
 * The single grid-wide storage mount exposing signals to terminals, monitors, and
 * emitters. Signals are written by assignment only: MEStorage's default insert/extract
 * refuse all transfers, so buses, cells, and IO ports cannot move them.
 */
public final class SignalGridStorage implements MEStorage {

    private final SignalService service;

    public SignalGridStorage(SignalService service) {
        this.service = service;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : service.committed().entrySet()) {
            if (entry.getValue() > 0) {
                out.add(SignalKey.of(entry.getKey()), entry.getValue());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("Signals");
    }
}
