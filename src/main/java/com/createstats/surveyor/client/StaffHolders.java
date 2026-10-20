package com.createstats.surveyor.client;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorStaffItem;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class StaffHolders {
    @Nullable
    private static LivingEntity current;

    private StaffHolders() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        final LivingEntity entity = event.getEntity();
        if (entity.getMainHandItem().getItem() instanceof SurveyorStaffItem
                || entity.getOffhandItem().getItem() instanceof SurveyorStaffItem) {
            current = entity;
        }
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() == current) current = null;
    }

    @Nullable
    static LivingEntity current() {
        return current;
    }
}
