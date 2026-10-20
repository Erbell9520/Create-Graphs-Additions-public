package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// PERIPHERAL mode is sticky and ownerless, so leases that hand back on their own
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class Vcc4ThrustLeases {
    public static final int LEASE_TICKS = 2;

    public record Lease(UUID owner, long expiry, @Nullable float[] nozzle, boolean nozzleMoved) {
        public boolean live(long now) {
            return now <= expiry && expiry - now < LEASE_TICKS;
        }
    }

    public static final class Table {
        private final Map<Long, Lease> leases = new HashMap<>();

        @Nullable
        public Lease renew(long pos, UUID owner, long now, Supplier<float[]> nozzle) {
            Lease current = leases.get(pos);
            if (current != null && current.live(now) && !current.owner().equals(owner)) return null;
            long expiry = now + LEASE_TICKS - 1;
            Lease next = current != null
                    ? new Lease(owner, expiry, current.nozzle(), current.nozzleMoved())
                    : new Lease(owner, expiry, nozzle == null ? null : nozzle.get(), false);
            leases.put(pos, next);
            return next;
        }

        public void nozzleMoved(long pos, UUID owner) {
            Lease l = leases.get(pos);
            if (l != null && l.owner().equals(owner) && !l.nozzleMoved()) leases.put(pos, new Lease(owner, l.expiry(), l.nozzle(), true));
        }

        public boolean heldByAnother(long pos, UUID owner, long now) {
            Lease l = leases.get(pos);
            return l != null && l.live(now) && !l.owner().equals(owner);
        }

        public Map<Long, Lease> takeExpired(long now) {
            Map<Long, Lease> out = new HashMap<>();
            for (Map.Entry<Long, Lease> e : leases.entrySet()) {
                if (!e.getValue().live(now)) out.put(e.getKey(), e.getValue());
            }
            out.keySet().forEach(leases::remove);
            return out;
        }

        public Map<Long, Lease> takeOwned(UUID owner) {
            Map<Long, Lease> out = new HashMap<>();
            for (Map.Entry<Long, Lease> e : leases.entrySet()) {
                if (e.getValue().owner().equals(owner)) out.put(e.getKey(), e.getValue());
            }
            out.keySet().forEach(leases::remove);
            return out;
        }

        public int size() {
            return leases.size();
        }
    }

    private static final Map<ResourceKey<Level>, Table> BY_LEVEL = new ConcurrentHashMap<>();

    private Vcc4ThrustLeases() {
    }

    static Table table(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), k -> new Table());
    }

    static boolean renew(ServerLevel level, BlockPos pos, UUID owner, BlockEntity be, Vcc4Jets.Type type) {
        return table(level).renew(pos.asLong(), owner, level.getGameTime(), () -> type.nozzleTarget(be)) != null;
    }

    static void nozzleMoved(ServerLevel level, BlockPos pos, UUID owner) {
        table(level).nozzleMoved(pos.asLong(), owner);
    }

    static boolean heldByAnother(ServerLevel level, BlockPos pos, UUID owner) {
        return table(level).heldByAnother(pos.asLong(), owner, level.getGameTime());
    }

    static void releaseAll(ServerLevel level, UUID owner) {
        handBack(level, table(level).takeOwned(owner));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Table table = BY_LEVEL.get(level.dimension());
        if (table == null || table.size() == 0) return;
        handBack(level, table.takeExpired(level.getGameTime()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BY_LEVEL.clear();
    }

    private static void handBack(ServerLevel level, Map<Long, Lease> lapsed) {
        if (lapsed.isEmpty()) return;
        List<Long> keys = new ArrayList<>(lapsed.keySet());
        for (long key : keys) {
            Lease lease = lapsed.get(key);
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, BlockPos.of(key));
            Vcc4Jets.Type type = Vcc4Jets.of(be);

            if (type == null || be == null || be.isRemoved()) continue;
            type.release(level, be, lease.nozzleMoved() ? lease.nozzle() : null);
        }
    }
}
