package com.createstats.battle.world;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

// handlers only queue. events fire with the block still there (cancelled breaks, air in blast lists)
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class DamageEvents {
    private record Split(ResourceKey<Level> dim, List<BlockPos> positions) {
    }

    private static final ConcurrentLinkedQueue<Split> SPLITS = new ConcurrentLinkedQueue<>();

    private DamageEvents() {
    }

    public static void ensureSplitListener() {
        SablePlots.registerSplitListener(DamageEvents::onSplit);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Map<ShipRecord, Integer> groups = new HashMap<>();
        Map<Long, List<ShipRecord>> byChunk = new HashMap<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            List<ShipRecord> records = byChunk.computeIfAbsent(ChunkPos.asLong(pos), k -> recordsAt(level, pos));
            for (ShipRecord r : records) {
                int group = groups.computeIfAbsent(r, ShipRecord::nextBlastGroup);
                r.onBlast(pos, group, now);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        String who = player == null ? "" : player.getName().getString();
        for (ShipRecord r : recordsAt(level, event.getPos())) r.onPlayerBreak(event.getPos(), who, level.getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity e = event.getEntity();
        String player = e instanceof Player p ? p.getName().getString() : null;
        for (ShipRecord r : recordsAt(level, event.getPos())) r.onPlace(event.getPos(), player, level.getGameTime());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (SPLITS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        for (Iterator<Split> it = SPLITS.iterator(); it.hasNext(); ) {
            Split s = it.next();
            if (!s.dim().equals(level.dimension())) continue;
            it.remove();
            if (s.positions().isEmpty()) continue;
            UUID ship = SablePlots.shipAt(level, s.positions().get(0));
            ShipRecord r = ship == null ? null : ShipRecords.loadedShip(level, ship);
            if (r != null) r.onSplit(s.positions(), level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ShipRecords.clear();
        SPLITS.clear();
    }

    private static void onSplit(Object[] args) {
        if (args.length < 3 || !(args[0] instanceof Level level) || level.isClientSide()) return;
        if (!(args[2] instanceof Collection<?> positions)) return;
        List<BlockPos> copy = new ArrayList<>(positions.size());
        for (Object o : positions) {
            if (o instanceof BlockPos p) copy.add(p.immutable());
        }
        SPLITS.add(new Split(level.dimension(), copy));
    }

    static List<ShipRecord> recordsAt(ServerLevel level, BlockPos pos) {
        UUID ship = SablePlots.shipAt(level, pos);
        if (ship != null) {
            ShipRecord r = ShipRecords.loadedShip(level, ship);
            return r == null ? List.of() : List.of(r);
        }
        return ShipRecords.groundAt(level, pos);
    }
}
