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

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyMappings {
    private static final String CATEGORY = "key.categories.create_stats";

    public static final KeyMapping TOGGLE_HELMET_NIGHT_VISION = new KeyMapping(
        "key.create_stats.helmet_night_vision",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    public static final KeyMapping TOGGLE_HELMET_VISOR = new KeyMapping(
        "key.create_stats.helmet_visor",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        CATEGORY
    );

    // unbound by default. O stole the key from everyone for a tool nobody uses
    public static final KeyMapping OPEN_SUPPORT_TERMINAL = new KeyMapping(
        "key.create_stats.support_terminal",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        CATEGORY
    );

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HELMET_NIGHT_VISION);
        event.register(TOGGLE_HELMET_VISOR);
        event.register(OPEN_SUPPORT_TERMINAL);
    }
}
