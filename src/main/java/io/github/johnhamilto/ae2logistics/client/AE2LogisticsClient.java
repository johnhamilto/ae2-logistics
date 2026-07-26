package io.github.johnhamilto.ae2logistics.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public final class AE2LogisticsClient {

    private AE2LogisticsClient() {
    }

    public static void initialize(IEventBus modBus) {
        SignalRenderer.initialize(modBus);
        modBus.addListener((RegisterMenuScreensEvent event) -> event
                .register(AE2Logistics.LOGIC_PART_MENU.get(), LogicPartScreen::new));
    }
}
