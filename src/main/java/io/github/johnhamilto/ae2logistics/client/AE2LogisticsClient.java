package io.github.johnhamilto.ae2logistics.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import guideme.Guide;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public final class AE2LogisticsClient {

    private AE2LogisticsClient() {
    }

    public static void initialize(IEventBus modBus) {
        Guide.builder(AE2Logistics.id("guide")).folder("guide").build();
        SignalRenderer.initialize(modBus);
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(AE2Logistics.LOGIC_PART_MENU.get(), LogicPartScreen::new);
            event.register(AE2Logistics.PATTERN_WORKBENCH_MENU.get(), PatternWorkbenchScreen::new);
            event.register(AE2Logistics.TRACER_TERMINAL_MENU.get(), TracerTerminalScreen::new);
            event.register(AE2Logistics.P2P_TERMINAL_MENU.get(), P2PFrequencyTerminalScreen::new);
            event.register(AE2Logistics.MESH_ENDPOINT_MENU.get(), MeshEndpointScreen::new);
            event.register(AE2Logistics.JOB_MONITOR_MENU.get(), JobMonitorScreen::new);
        });
    }
}
