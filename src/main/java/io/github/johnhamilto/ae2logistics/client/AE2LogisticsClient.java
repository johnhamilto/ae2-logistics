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
        modBus.addListener((appeng.client.api.model.parts.RegisterPartModelsEvent event) ->
                event.registerModelType(WirelessConnectorModel.Unbaked.ID,
                        WirelessConnectorModel.Unbaked.MAP_CODEC));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EndpointHighlighter::render);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(AE2Logistics.TRACE_PANEL_BE.get(),
                        context -> new TracePanelRenderer()));
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            appeng.client.InitScreens.register(event,
                    AE2Logistics.LOGIC_PART_MENU.get(),
                    LogicPartScreen::new,
                    "/screens/ae2logistics_logic_part.json");
            // Piloting AE2's own GUI framework: chrome comes from a screen style doc.
            // Style docs live flat in /screens/ (prefixed filename): includes resolve
            // relative to the doc's directory, so a subfolder breaks common/*.json.
            appeng.client.InitScreens.register(event,
                    AE2Logistics.PATTERN_WORKBENCH_MENU.get(),
                    PatternWorkbenchScreen::new,
                    "/screens/ae2logistics_pattern_workbench.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.TRACER_TERMINAL_MENU.get(),
                    TracerTerminalScreen::new,
                    "/screens/ae2logistics_tracer_terminal.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.P2P_TERMINAL_MENU.get(),
                    P2PFrequencyTerminalScreen::new,
                    "/screens/ae2logistics_p2p_terminal.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.MESH_ENDPOINT_MENU.get(),
                    MeshEndpointScreen::new,
                    "/screens/ae2logistics_mesh_endpoint.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.JOB_MONITOR_MENU.get(),
                    JobMonitorScreen::new,
                    "/screens/ae2logistics_job_monitor.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.GUARDED_PROVIDER_MENU.get(),
                    GuardedProviderScreen::new,
                    "/screens/ae2logistics_guarded_provider.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.QUERY_TERMINAL_MENU.get(),
                    QueryTerminalScreen::new,
                    "/screens/ae2logistics_query_terminal.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.QUERY_SENSOR_MENU.get(),
                    QuerySensorScreen::new,
                    "/screens/ae2logistics_query_sensor.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.QUERY_EXPORT_BUS_MENU.get(),
                    QueryExportBusScreen::new,
                    "/screens/ae2logistics_query_export_bus.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.CONFIG_TERMINAL_MENU.get(),
                    ConfigTerminalScreen::new,
                    "/screens/ae2logistics_config_terminal.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.JOB_SCHEDULER_MENU.get(),
                    JobSchedulerScreen::new,
                    "/screens/ae2logistics_job_scheduler.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.LOGIC_CORE_MENU.get(),
                    LogicCoreScreen::new,
                    "/screens/ae2logistics_logic_core.json");
            appeng.client.InitScreens.register(event,
                    AE2Logistics.STORAGE_JANITOR_MENU.get(),
                    StorageJanitorScreen::new,
                    "/screens/ae2logistics_storage_janitor.json");
            // AE2's storage bus screen, our style doc - retitles the window.
            appeng.client.InitScreens.register(event,
                    AE2Logistics.SUBNET_LINK_MENU.get(),
                    appeng.client.gui.implementations.StorageBusScreen::new,
                    "/screens/ae2logistics_subnet_link.json");
        });
    }
}
