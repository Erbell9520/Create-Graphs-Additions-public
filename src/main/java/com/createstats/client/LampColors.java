package com.createstats.client;

import com.createstats.block.LampBlock;
import com.createstats.registry.ModBlocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** [1] */
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LampColors {

    private LampColors() {
    }

    /** Every dyeable lamp. Kept in one place so a new lamp cannot be added and silently miss tinting. */
    private static Block[] lamps() {
        return new Block[] {
                ModBlocks.CAGED_BULKHEAD_LAMP.get(),
                ModBlocks.HIGH_BAY_LAMP.get(),
                ModBlocks.STRIP_LAMP.get(),
                ModBlocks.DOME_LAMP.get(),
                ModBlocks.WALL_SCONCE.get(),
                ModBlocks.PANEL_LAMP.get(),
        };
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            return tint(state.getValue(LampBlock.COLOR), state.getValue(LampBlock.LIT));
        }, lamps());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // In the inventory a lamp always shows its lit colour, or every lamp in the creative tab
        // would be a grey lump and the dyed variants indistinguishable.
        event.register((stack, tintIndex) -> tintIndex == 0 ? tint(DyeColor.WHITE, true) : -1, lamps());
    }

    /** The multiply colour for a dye, softened while unlit so an off lamp reads as off. */
    private static int tint(DyeColor dye, boolean lit) {
        final int argb = dye.getTextureDiffuseColor();
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        if (lit) {
            return (r << 16) | (g << 8) | b;
        }
        return (soften(r) << 16) | (soften(g) << 8) | soften(b);
    }

    /** Pulls a channel most of the way to mid grey: a hint of colour, but plainly not powered. */
    private static int soften(int channel) {
        return Math.min(255, Math.round(channel * 0.45f + 77.0f));
    }
}
