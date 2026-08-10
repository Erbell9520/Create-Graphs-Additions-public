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
        MachineSelectionCardClientHandler.tick();
        DamageObserverPreviewClientHandler.tick();
        DieselMultiblockOutlineClientHandler.tick();
        PilotHelmetHudOverlay.tick();
        // Drain keybind clicks. consumeClick() returns true once per press, so a
        // while-loop correctly handles multiple rapid taps in the same tick.
        while (ModKeyMappings.TOGGLE_HELMET_NIGHT_VISION.consumeClick()) {
            PilotHelmetNightVisionClientHandler.onToggleKey();
        }
        while (ModKeyMappings.TOGGLE_BATTLE_TACTICAL_V2_PIN.consumeClick()) {
            PinnedBattleTacticalV2HudHandler.onToggleKey();
        }
        while (ModKeyMappings.TOGGLE_HELMET_VISOR.consumeClick()) {
            PilotHelmetVisorState.requestToggle();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        BattleScreenPipHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        PinnedAeroHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        PinnedBattleTacticalV2HudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        StressometerHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        FccHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        PilotHelmetHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        CableRoutePreview.renderHud(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PinnedAeroHudOverlay.clear();
            PinnedBattleTacticalV2HudOverlay.clear();
            BattleScreenPipHudOverlay.clear();
            StressometerHudOverlay.clear();
            PilotHelmetHudOverlay.clear();
            PilotHelmetVisorState.clear();
            FccClientState.clear();
            MachineSelectionCardClientHandler.clearPreview();
            DamageObserverPreviewClientHandler.clearPreview();
            DieselMultiblockOutlineClientHandler.clearPreview();
            MiniDisplayTextureManager.disposeAll();
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
