package com.createstats.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class SuctionEngine {
    private SuctionEngine() {
    }

    public static final double CATCH_DISTANCE = 0.6;

    public static final double TOP_SPEED = 0.6;

    public static final int SCAN_LIMIT = 24;

    public static final TagKey<Block> VACUUM_LOOSE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_stats", "vacuum_loose"));

    public static boolean loose(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return LooseBlocks.isLoose(state.is(VACUUM_LOOSE), state.getBlock() instanceof FallingBlock,
                state.hasBlockEntity(), state.getDestroySpeed(level, pos));
    }

    public static List<ItemEntity> items(Level level, Vec3 mouth, AABB box, Predicate<ItemStack> wants, int limit) {
        final List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty() && wants.test(e.getItem()));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(mouth)));
        final List<ItemEntity> out = new ArrayList<>(Math.min(limit, found.size()));
        for (ItemEntity e : found) {
            if (out.size() >= limit) {
                break;
            }
            if (clearAir(level, mouth, centre(e))) {
                out.add(e);
            }
        }
        return out;
    }

    public static List<ExperienceOrb> orbs(Level level, Vec3 mouth, AABB box, int limit) {
        final List<ExperienceOrb> found = level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive);
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(mouth)));
        final List<ExperienceOrb> out = new ArrayList<>(Math.min(limit, found.size()));
        for (ExperienceOrb o : found) {
            if (out.size() >= limit) {
                break;
            }
            if (clearAir(level, mouth, o.position().add(0, 0.1, 0))) {
                out.add(o);
            }
        }
        return out;
    }

    public static List<ItemEntity> itemsFrom(Level level, BlockPos self, Vec3 from, AABB box,
                                             Predicate<ItemStack> wants, int limit) {
        final List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty() && wants.test(e.getItem()));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(from)));
        final List<ItemEntity> out = new ArrayList<>(Math.min(limit, found.size()));
        for (ItemEntity e : found) {
            if (out.size() >= limit) {
                break;
            }
            if (clearAirFrom(level, self, from, centre(e))) {
                out.add(e);
            }
        }
        return out;
    }

    // cast from where the line leaves our own block
    public static boolean clearAirFrom(Level level, BlockPos self, Vec3 from, Vec3 point) {
        final double exit = SuctionReach.exitFraction(from.x, from.y, from.z, point.x, point.y, point.z,
                self.getX(), self.getY(), self.getZ());
        if (exit >= 1.0) {
            return true;
        }
        final Vec3 start = from.add(point.subtract(from).scale(exit + 1.0e-4));
        return clearAir(level, start, point);
    }

    public static boolean clearAir(Level level, Vec3 mouth, Vec3 point) {
        final HitResult hit = level.clip(new ClipContext(mouth, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(mouth) >= point.distanceToSqr(mouth) - 1.0e-4;
    }

    public static Vec3 centre(net.minecraft.world.entity.Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void pull(net.minecraft.world.entity.Entity e, Vec3 mouth, Vec3 axis, double strength, int seed) {
        final Vec3 at = centre(e);
        final Vec3 to = mouth.subtract(at);
        final double d = Math.max(0.05, to.length());
        final Vec3 dir = to.scale(1.0 / d);
        final double speed = Math.min(TOP_SPEED, 0.06 + 0.32 / Math.max(0.5, d)) * strength;
        Vec3 swirl = axis.cross(dir);
        if (swirl.lengthSqr() < 1.0e-6) {
            swirl = new Vec3(0, 1, 0).cross(dir);
        }
        final double spin = ((seed & 1) == 0 ? 1.0 : -1.0) * 0.45 * speed * Math.max(0.0, 1.0 - d / 7.0);
        swirl = swirl.lengthSqr() < 1.0e-6 ? Vec3.ZERO : swirl.normalize().scale(spin);
        final Vec3 target = dir.scale(speed).add(swirl).add(0, e.onGround() ? 0.10 : 0.045, 0);
        final Vec3 now = e.getDeltaMovement();
        e.setDeltaMovement(now.add(target.subtract(now).scale(0.35)));
        e.hurtMarked = true;
    }

    private static final Field ORB_COUNT = orbCountField();

    private static Field orbCountField() {
        try {
            final Field f = ExperienceOrb.class.getDeclaredField("count");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    public static int orbPoints(ExperienceOrb orb) {
        int count = 1;
        if (ORB_COUNT != null) {
            try {
                count = Math.max(1, ORB_COUNT.getInt(orb));
            } catch (IllegalAccessException ex) {
                count = 1;
            }
        }
        return orb.getValue() * count;
    }
}
