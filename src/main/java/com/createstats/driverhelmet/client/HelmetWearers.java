package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.DriverHelmets;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// hide the head, an 8px cube pokes through a round shell whatever you do
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class HelmetWearers {
    @Nullable
    private static LivingEntity wearer;
    @Nullable
    private static HumanoidModel<?> hiddenFrom;
    private static boolean headWasVisible;
    private static boolean hatWasVisible;

    private HelmetWearers() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        final LivingEntity entity = event.getEntity();
        if (DriverHelmets.worn(entity).isEmpty()) {
            return;
        }
        wearer = entity;
        if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
            hiddenFrom = model;
            headWasVisible = model.head.visible;
            hatWasVisible = model.hat.visible;
            model.head.visible = false;
            model.hat.visible = false;
        }
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() != wearer) {
            return;
        }
        if (hiddenFrom != null) {
            hiddenFrom.head.visible = headWasVisible;
            hiddenFrom.hat.visible = hatWasVisible;
        }
        wearer = null;
        hiddenFrom = null;
    }

    @Nullable
    static LivingEntity wearer() {
        return wearer;
    }

    @Nullable
    static ResourceLocation faceSkin() {
        return wearer instanceof AbstractClientPlayer player ? player.getSkin().texture() : null;
    }

    static boolean faceHatShown() {
        return wearer instanceof AbstractClientPlayer player && player.isModelPartShown(PlayerModelPart.HAT);
    }
}
