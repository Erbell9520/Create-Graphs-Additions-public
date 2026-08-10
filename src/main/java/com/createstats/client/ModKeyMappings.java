package com.createstats.client;

import com.createstats.CreateStats;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** [1] */
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyMappings {

    private static final String CATEGORY = "key.categories.create_stats";

    /** [2] */
    public static final KeyMapping TOGGLE_HELMET_NIGHT_VISION = new KeyMapping(
        "key.create_stats.helmet_night_vision",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    /**
     * Pin / unpin the Battle Tactical V2 HUD overlay for the Stats Display panel
     * the player is looking at. Default {@code B} (unbound in vanilla 1.21).
     */
    public static final KeyMapping TOGGLE_BATTLE_TACTICAL_V2_PIN = new KeyMapping(
        "key.create_stats.battle_tactical_v2_pin",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        CATEGORY
    );

    /** [3] */
    public static final KeyMapping TOGGLE_HELMET_VISOR = new KeyMapping(
        "key.create_stats.helmet_visor",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        CATEGORY
    );

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HELMET_NIGHT_VISION);
        event.register(TOGGLE_BATTLE_TACTICAL_V2_PIN);
        event.register(TOGGLE_HELMET_VISOR);
    }
}
