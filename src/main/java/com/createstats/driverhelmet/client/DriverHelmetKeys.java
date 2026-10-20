package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.DriverHelmetItem;
import com.createstats.driverhelmet.DriverHelmetPacket;
import com.createstats.driverhelmet.DriverHelmets;
import com.createstats.driverhelmet.HelmetKeyNames;
import com.createstats.driverhelmet.VisorModes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// own conflict context, V is also the pilot helmet's. not a real clash
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class DriverHelmetKeys {
    static final IKeyConflictContext WEARING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            final Minecraft mc = Minecraft.getInstance();
            return KeyConflictContext.IN_GAME.isActive() && mc.player != null
                    && !DriverHelmets.worn(mc.player).isEmpty();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping VISOR = new KeyMapping(HelmetKeyNames.VISOR, WEARING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, HelmetKeyNames.CATEGORY);
    public static final KeyMapping NIGHT_VISION = new KeyMapping(HelmetKeyNames.NIGHT_VISION, WEARING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, HelmetKeyNames.CATEGORY);

    public static final KeyMapping THEME = new KeyMapping(HelmetKeyNames.THEME, WEARING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, HelmetKeyNames.CATEGORY);

    private DriverHelmetKeys() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            while (VISOR.consumeClick()) {
            }
            while (NIGHT_VISION.consumeClick()) {
            }
            while (THEME.consumeClick()) {
            }
            return;
        }
        while (VISOR.consumeClick()) {
            if (!DriverHelmets.worn(mc.player).isEmpty()) {
                PacketDistributor.sendToServer(new DriverHelmetPacket(DriverHelmetPacket.FLICK_VISOR));
            }
        }
        while (THEME.consumeClick()) {
            if (!DriverHelmets.worn(mc.player).isEmpty()) {
                PacketDistributor.sendToServer(new DriverHelmetPacket(DriverHelmetPacket.THEME));
                DriverHelmetHud.themeChanged();
            }
        }
        while (NIGHT_VISION.consumeClick()) {
            final ItemStack helmet = DriverHelmets.worn(mc.player);
            if (helmet.isEmpty()) {
                continue;
            }
            if (VisorModes.nightVisionKeyAccepted(DriverHelmetItem.visor(helmet).open())) {
                PacketDistributor.sendToServer(new DriverHelmetPacket(DriverHelmetPacket.NIGHT_VISION));
            } else {
                mc.player.displayClientMessage(Component.translatable(
                        "message.create_stats.driver_helmet.night_vision_visor_up",
                        Component.keybind(HelmetKeyNames.VISOR)), true);
            }
        }
    }
}
