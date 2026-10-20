package com.createstats.repeller;

import com.createstats.mixin.PhantomAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public final class LeaveVolumeGoal extends Goal {
    public static final int PRIORITY = -1;

    private static final double LEAVE_SPEED = 1.25;

    private static final int STEP_RANGE = 16;
    private static final int STEP_HEIGHT = 7;

    private static final int REROUTE_TICKS = 20;

    private enum Steer { WALK, BRAIN, PHANTOM, FLY, PUSH }

    private final Mob mob;
    private final Steer steer;
    private final double fallbackAngle;
    private final RepelCourse.Watch watch = new RepelCourse.Watch();
    private long entryId;
    private long nextRoute;

    public LeaveVolumeGoal(Mob mob) {
        this.mob = mob;
        this.steer = steerFor(mob);
        this.fallbackAngle = RepelCourse.fallbackAngle(mob.getId());
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private static Steer steerFor(Mob mob) {
        if (mob instanceof Phantom) {
            return Steer.PHANTOM;
        }
        if (mob instanceof FlyingMob) {
            return Steer.FLY;
        }
        if (mob instanceof PathfinderMob) {
            return mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
                    ? Steer.BRAIN : Steer.WALK;
        }
        return Steer.PUSH;
    }

    public static boolean exempt(Mob mob) {
        if (mob.isNoAi() || mob.isPassenger() || mob.isLeashed() || mob.hasCustomName()) {
            return true;
        }
        if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return true;
        }
        if (mob instanceof Raider raider && raider.hasActiveRaid()) {
            return true;
        }
        final MobSpawnType from = mob.getSpawnType();
        return from == MobSpawnType.SPAWNER || from == MobSpawnType.TRIAL_SPAWNER;
    }

    @Override
    public boolean canUse() {
        if (!RepellerIndex.anyActive()) {
            return false;
        }
        final SpawnIndex.Entry<EntityType<?>> e = RepellerIndex.of(mob.level().dimension())
                .find(mob.getType(), mob.getX(), mob.getY(), mob.getZ());
        if (e == null || exempt(mob)) {
            return false;
        }
        entryId = e.id();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        final SpawnIndex.Entry<EntityType<?>> e = live();
        return e != null && !exempt(mob) && RepelCourse.stillLeaving(depth(e, mob.getX(), mob.getY(), mob.getZ()));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        final long now = mob.level().getGameTime();
        final SpawnIndex.Entry<EntityType<?>> e = live();
        watch.start(now, e == null ? 0.0 : depth(e, mob.getX(), mob.getY(), mob.getZ()));
        nextRoute = now;
        dropTarget();
    }

    @Override
    public void tick() {
        final SpawnIndex.Entry<EntityType<?>> e = live();
        if (e == null) {
            return;
        }
        final long now = mob.level().getGameTime();
        final double depth = depth(e, mob.getX(), mob.getY(), mob.getZ());
        watch.observe(now, depth);
        dropTarget();
        switch (steer) {
            case WALK, BRAIN -> {
                final boolean arrived = steer == Steer.WALK ? mob.getNavigation().isDone()
                        : !mob.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
                if ((arrived || watch.stalled(now)) && now >= nextRoute) {
                    nextRoute = now + REROUTE_TICKS;
                    route(e, depth);
                }
            }
            case PHANTOM -> steerPhantom(e);
            case FLY -> {
                final RepelCourse.Spot out = RepelCourse.beyond(e.x(), e.z(), e.radius(), mob.getX(), mob.getZ(),
                        RepelCourse.STEER_BEYOND, fallbackAngle);
                mob.getMoveControl().setWantedPosition(out.x(), mob.getY(), out.z(), 1.0);
            }
            case PUSH -> {
            }
        }
        if (steer == Steer.PUSH || watch.stalled(now)) {
            push(e);
        }
    }

    @Override
    public void stop() {
        if (steer == Steer.WALK) {
            mob.getNavigation().stop();
        }
    }

    @Nullable
    private SpawnIndex.Entry<EntityType<?>> live() {
        if (!RepellerIndex.anyActive()) {
            return null;
        }
        final SpawnIndex.Entry<EntityType<?>> e = RepellerIndex.of(mob.level().dimension()).byId(entryId);
        return e != null && e.type() == mob.getType() ? e : null;
    }

    private static double depth(SpawnIndex.Entry<EntityType<?>> e, double x, double y, double z) {
        return RepelCourse.depth(e.shape(), e.x(), e.y(), e.z(), e.radius(), x, y, z);
    }

    private void dropTarget() {
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (mob instanceof Creeper creeper && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }
    }

    private void route(SpawnIndex.Entry<EntityType<?>> e, double depthNow) {
        final PathfinderMob walker = (PathfinderMob) mob;

        Vec3 step = DefaultRandomPos.getPosAway(walker, STEP_RANGE, STEP_HEIGHT, new Vec3(e.x(), mob.getY(), e.z()));
        if (step == null || !RepelCourse.worthTaking(depthNow, depth(e, step.x, step.y, step.z))) {
            final RepelCourse.Spot out = RepelCourse.beyond(e.x(), e.z(), e.radius(), mob.getX(), mob.getZ(),
                    RepelCourse.EXIT_MARGIN + 1.0, fallbackAngle);
            step = new Vec3(out.x(), mob.getY(), out.z());
        }
        if (steer == Steer.BRAIN) {
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(step, (float) LEAVE_SPEED, 1));
            return;
        }
        final Path path = mob.getNavigation().createPath(BlockPos.containing(step), 0);
        if (path != null) {
            mob.getNavigation().moveTo(path, LEAVE_SPEED);
        }
    }

    private void steerPhantom(SpawnIndex.Entry<EntityType<?>> e) {
        final PhantomAccessor phantom = (PhantomAccessor) mob;
        final RepelCourse.Spot out = RepelCourse.beyond(e.x(), e.z(), e.radius(), mob.getX(), mob.getZ(),
                RepelCourse.STEER_BEYOND, fallbackAngle);
        phantom.createStats$setMoveTargetPoint(new Vec3(out.x(), mob.getY(), out.z()));
        final RepelCourse.Spot anchor = RepelCourse.beyond(e.x(), e.z(), e.radius(), mob.getX(), mob.getZ(),
                RepelCourse.ANCHOR_BEYOND, fallbackAngle);
        phantom.createStats$setAnchorPoint(BlockPos.containing(anchor.x(), mob.getY(), anchor.z()));
    }

    private void push(SpawnIndex.Entry<EntityType<?>> e) {
        final RepelCourse.Heading out = RepelCourse.outward(e.x(), e.z(), mob.getX(), mob.getZ(), fallbackAngle);
        final Vec3 v = mob.getDeltaMovement();
        final double add = RepelCourse.pushStep(v.x * out.x() + v.z * out.z());
        if (add > 0.0) {
            mob.setDeltaMovement(v.x + out.x() * add, v.y, v.z + out.z() * add);
        }
    }
}
