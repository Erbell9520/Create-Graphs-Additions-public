package com.createstats.aviatorhelmet.client;

import com.createstats.CreateStats;
import com.createstats.aviatorhelmet.AviatorHelmetPacket;
import com.createstats.aviatorhelmet.AviatorHelmets;
import com.createstats.aviatorhelmet.AviatorKeyNames;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// own conflict context, otherwise controls screen paints V/H red vs the driver helmet. not a real clash
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class AviatorHelmetKeys {
    static final IKeyConflictContext WEARING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            final Minecraft mc = Minecraft.getInstance();
            return KeyConflictContext.IN_GAME.isActive() && mc.player != null
                    && !AviatorHelmets.worn(mc.player).isEmpty();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping GOGGLES = new KeyMapping(AviatorKeyNames.GOGGLES, WEARING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, AviatorKeyNames.CATEGORY);
    public static final KeyMapping TONE = new KeyMapping(AviatorKeyNames.TONE, WEARING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, AviatorKeyNames.CATEGORY);

    private AviatorHelmetKeys() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final boolean worn = mc.player != null && !AviatorHelmets.worn(mc.player).isEmpty();
        while (GOGGLES.consumeClick()) {
            if (worn) {
                PacketDistributor.sendToServer(new AviatorHelmetPacket(AviatorHelmetPacket.FLICK_GOGGLES));
            }
        }
        while (TONE.consumeClick()) {
            if (worn) {
                PacketDistributor.sendToServer(new AviatorHelmetPacket(AviatorHelmetPacket.TONE));
                AviatorHud.toneChanged();
            }
        }
    }
}
