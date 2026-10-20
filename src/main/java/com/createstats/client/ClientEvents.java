package com.createstats.client;

import net.neoforged.bus.api.SubscribeEvent;
import com.createstats.CreateStats;
import com.createstats.dashboard.DashboardRenderer;
import com.createstats.prediction.PredictionEngine;
import com.createstats.renderer.CalculatorTextureManager;
import com.createstats.renderer.DispatchBoardTextureManager;
import com.createstats.renderer.MiniDisplayTextureManager;
import com.createstats.renderer.ProjectorTextureManager;
import com.createstats.renderer.DisplayTextureManager;
import com.createstats.renderer.SpecializedPanelRenderer;
import com.createstats.flight.client.FccHudOverlay;
import com.createstats.flight.client.FccClientState;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PaVoicePlayer.clientTick();

        com.createstats.train.smart.ClientRailNetwork.tick();
        MachineSelectionCardClientHandler.tick();
        DieselMultiblockOutlineClientHandler.tick();
        CouplerBindHint.tick();
        MainBreakerBindHint.tick();
        ContactorBindHint.tick();
        com.createstats.vccmk3.client.VccMk3Highlight.tick();
        PilotHelmetHudOverlay.tick();

        while (ModKeyMappings.TOGGLE_HELMET_NIGHT_VISION.consumeClick()) {
            PilotHelmetNightVisionClientHandler.onToggleKey();
        }
        while (ModKeyMappings.TOGGLE_HELMET_VISOR.consumeClick()) {
            PilotHelmetVisorState.requestToggle();
        }
        while (ModKeyMappings.OPEN_SUPPORT_TERMINAL.consumeClick()) {
            if (com.createstats.support.SupportClientState.canOpen(
                net.minecraft.client.Minecraft.getInstance().player)) {
                ClientScreenHelper.openSupportTerminal();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        PinnedAeroHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        StressometerHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        FccHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        com.createstats.vccmk3.client.VccMk3HudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        PilotHelmetHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        CableRoutePreview.renderHud(event.getGuiGraphics());
        CouplerBindHint.renderHud(event.getGuiGraphics());
        MainBreakerBindHint.renderHud(event.getGuiGraphics());
        ContactorBindHint.renderHud(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            com.createstats.train.smart.ClientRailNetwork.clear();
            com.createstats.client.train.ClientTrainCharge.clear();

            com.createstats.train.map.RailHistory.clear();
            com.createstats.train.smart.RailFilter.reset();
            PinnedAeroHudOverlay.clear();
            com.createstats.battle.client.BattleClientCache.clear();
            StressometerHudOverlay.clear();
            PilotHelmetHudOverlay.clear();
            PilotHelmetVisorState.clear();
            FccClientState.clear();
            MachineSelectionCardClientHandler.clearPreview();
            DieselMultiblockOutlineClientHandler.clearPreview();
            CouplerBindHint.clear();
            MainBreakerBindHint.clear();
            ContactorBindHint.clear();
            com.createstats.vccmk3.client.VccMk3Highlight.clear();
            MiniDisplayTextureManager.disposeAll();
            com.createstats.renderer.EInkPanelTextureManager.disposeAll();
            com.createstats.renderer.SmartHomeFaceTextureManager.disposeAll();
            com.createstats.renderer.WeatherFaceTextureManager.disposeAll();
            com.createstats.renderer.DiagnosticJunctionTextureManager.disposeAll();
            com.createstats.renderer.PowerMeterTextureManager.disposeAll();
            com.createstats.renderer.PowerLimiterTextureManager.disposeAll();
            com.createstats.renderer.PowerDistributionUnitTextureManager.disposeAll();
            com.createstats.renderer.BatteryControllerTextureManager.disposeAll();
            com.createstats.renderer.CapacitorBankTextureManager.disposeAll();
            DisplayTextureManager.disposeAll();
            DispatchBoardTextureManager.disposeAll();
            ProjectorTextureManager.cleanupAll();
            CalculatorTextureManager.cleanupAll();
            PredictionEngine.clearAll();
            DashboardRenderer.clearLayoutCache();
            SpecializedPanelRenderer.clearClientTransientState();
            CreateStats.LOGGER.debug("Disposed all display/projector/calculator/dispatch textures, prediction cache, and dashboard layouts on world unload");
        }
    }
}
