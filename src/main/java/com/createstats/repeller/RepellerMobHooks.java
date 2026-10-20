package com.createstats.repeller;

import com.createstats.CreateStats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class RepellerMobHooks {
    private RepellerMobHooks() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof LeaveVolumeGoal) {
                return;
            }
        }
        mob.goalSelector.addGoal(LeaveVolumeGoal.PRIORITY, new LeaveVolumeGoal(mob));
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!RepellerIndex.anyActive()) {
            return;
        }
        final LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || LeaveVolumeGoal.exempt(mob)) {
            return;
        }
        final SpawnIndex<EntityType<?>> index = RepellerIndex.of(mob.level().dimension());
        final EntityType<?> type = mob.getType();
        if (index.find(type, mob.getX(), mob.getY(), mob.getZ()) != null
                || index.find(type, target.getX(), target.getY(), target.getZ()) != null) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
}
