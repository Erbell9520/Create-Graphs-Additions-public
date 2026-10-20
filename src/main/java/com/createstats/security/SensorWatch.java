package com.createstats.security;

import com.createstats.blockentity.SecuritySensorBE;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SensorWatch {
    private SensorWatch() {
    }

    private static final Map<ResourceKey<Level>, Set<Long>> WATCHERS = new ConcurrentHashMap<>();

    private static final int MAX_REACH = SensorKind.maxRadius();

    public static void add(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        WATCHERS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(pos.asLong());
    }

    public static void remove(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final Set<Long> set = WATCHERS.get(level.dimension());
        if (set != null) {
            set.remove(pos.asLong());
        }
    }

    public static int watching(Level level) {
        final Set<Long> set = level == null ? null : WATCHERS.get(level.dimension());
        return set == null ? 0 : set.size();
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        reportNear(level, event.getPos(), event.getPlayer(),
                "broke " + event.getState().getBlock().getName().getString());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        final Entity placer = event.getEntity();
        reportNear(level, event.getPos(), placer instanceof Player player ? player : null,
                "placed " + event.getPlacedBlock().getBlock().getName().getString());
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        final Level level = event.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockPos at = BlockPos.containing(event.getExplosion().center());
        final Entity lit = event.getExplosion().getIndirectSourceEntity();
        final Player actor = lit instanceof Player player ? player : null;

        reportNear(level, at, actor, actor == null ? "explosion" : "set off an explosion");
    }

    private static void reportNear(Level level, BlockPos at, @Nullable Player actor, String what) {
        final Set<Long> set = WATCHERS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        final String who = actor == null ? "" : actor.getGameProfile().getName() + " ";
        final Iterator<Long> it = set.iterator();
        while (it.hasNext()) {
            final BlockPos sensorPos = BlockPos.of(it.next());

            if (sensorPos.distSqr(at) > (double) MAX_REACH * MAX_REACH) {
                continue;
            }
            if (!level.isLoaded(sensorPos)) {
                continue;
            }
            if (!(level.getBlockEntity(sensorPos) instanceof SecuritySensorBE sensor)
                    || sensor.kind() != SensorKind.TAMPER) {
                it.remove();
                continue;
            }
            if (sensorPos.distSqr(at) > (double) sensor.radius() * sensor.radius()) {
                continue;
            }
            if (actor != null && sensor.permits(actor)) {
                continue;
            }
            sensor.report(who + what);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            WATCHERS.remove(level.dimension());
        }
    }
}
