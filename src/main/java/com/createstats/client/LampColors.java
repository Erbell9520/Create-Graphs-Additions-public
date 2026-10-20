package com.createstats.client;

import com.createstats.registry.ModBlocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LampColors {
    private LampColors() {
    }

    private static Block[] lamps() {
        return new Block[] {
                ModBlocks.CAGED_BULKHEAD_LAMP.get(),
                ModBlocks.HIGH_BAY_LAMP.get(),
                ModBlocks.STRIP_LAMP.get(),
                ModBlocks.DOME_LAMP.get(),
                ModBlocks.WALL_SCONCE.get(),
                ModBlocks.PANEL_LAMP.get(),
                ModBlocks.PENDANT_LAMP.get(),
                ModBlocks.FLOOD_LAMP.get(),
                ModBlocks.VAPOUR_TIGHT_LAMP.get(),
                ModBlocks.WELL_GLASS_LAMP.get(),
                ModBlocks.FILAMENT_BULB.get(),
                ModBlocks.EMERGENCY_LAMP.get(),
                ModBlocks.BANKERS_LAMP.get(),
        };
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? tint(DyeColor.WHITE) : -1, lamps());
    }

    private static int tint(DyeColor dye) {
        return dye.getTextureDiffuseColor() & 0x00FFFFFF;
    }
}
