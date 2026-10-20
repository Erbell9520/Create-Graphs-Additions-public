package com.createstats.aviatorhelmet.client;

import com.createstats.CreateStats;
import com.createstats.aviatorhelmet.AviatorHelmets;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class AviatorHatHider {
    @Nullable
    private static LivingEntity wearer;
    @Nullable
    private static HumanoidModel<?> hiddenFrom;
    private static boolean hatWasVisible;

    private AviatorHatHider() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        final LivingEntity entity = event.getEntity();
        if (AviatorHelmets.worn(entity).isEmpty()) {
            return;
        }
        if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
            wearer = entity;
            hiddenFrom = model;
            hatWasVisible = model.hat.visible;
            model.hat.visible = false;
        }
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() != wearer) {
            return;
        }
        if (hiddenFrom != null) {
            hiddenFrom.hat.visible = hatWasVisible;
        }
        wearer = null;
        hiddenFrom = null;
    }
}
