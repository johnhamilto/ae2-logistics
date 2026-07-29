package io.github.johnhamilto.ae2logistics.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import io.github.johnhamilto.ae2logistics.AE2Logistics;

public final class AE2LogisticsClient {

    private AE2LogisticsClient() {
    }

    public static void initialize(IEventBus modBus) {
        // Guide pages live in assets/ae2logistics/ae2guide/ and are contributed
        // directly into AE2's own guide (GuideME collects that folder across mods).
        SignalRenderer.initialize(modBus);
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(AE2Logistics.LOGIC_PART_MENU.get(), LogicPartScreen::new);
            // Piloting AE2's own GUI framework: chrome comes from a screen style doc.
            // Style docs live flat in /screens/ (prefixed filename): includes resolve
            // relative to the doc's directory, so a subfolder breaks common/*.json.
            appeng.init.client.InitScreens.register(event,
                    AE2Logistics.PATTERN_WORKBENCH_MENU.get(),
                    PatternWorkbenchScreen::new,
                    "/screens/ae2logistics_pattern_workbench.json");
            event.register(AE2Logistics.TRACER_TERMINAL_MENU.get(), TracerTerminalScreen::new);
            event.register(AE2Logistics.P2P_TERMINAL_MENU.get(), P2PFrequencyTerminalScreen::new);
            event.register(AE2Logistics.MESH_ENDPOINT_MENU.get(), MeshEndpointScreen::new);
            event.register(AE2Logistics.JOB_MONITOR_MENU.get(), JobMonitorScreen::new);
            event.register(AE2Logistics.GUARDED_PROVIDER_MENU.get(), GuardedProviderScreen::new);
            event.register(AE2Logistics.QUERY_TERMINAL_MENU.get(), QueryTerminalScreen::new);
            event.register(AE2Logistics.QUERY_SENSOR_MENU.get(), QuerySensorScreen::new);
            event.register(AE2Logistics.QUERY_EXPORT_BUS_MENU.get(), QueryExportBusScreen::new);
            event.register(AE2Logistics.CONFIG_TERMINAL_MENU.get(), ConfigTerminalScreen::new);
            event.register(AE2Logistics.JOB_SCHEDULER_MENU.get(), JobSchedulerScreen::new);
            event.register(AE2Logistics.LOGIC_CORE_MENU.get(), LogicCoreScreen::new);
            event.register(AE2Logistics.SUBNET_CORE_MENU.get(), SubnetCoreScreen::new);
        });
    }
}
