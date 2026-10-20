package com.createstats.battle.client;

import com.createstats.CreateStats;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class BattleKeys {
    public static final String CATEGORY = "key.categories.create_stats.battle";

    public static final KeyMapping FIRE = new KeyMapping("key.create_stats.battle.fire", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping WEAPONS = new KeyMapping("key.create_stats.battle.weapons", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping DESIGNATE = new KeyMapping("key.create_stats.battle.designate", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private BattleKeys() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(FIRE);
        event.register(WEAPONS);
        event.register(DESIGNATE);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_spyglass_ring"), SpyglassDesignation::renderRing);
    }
}
