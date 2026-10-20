package com.createstats.netscan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.createstats.netscan.scan.KineticWalk;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.WorldProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class NetscanEngine {
    private static final int WATCH_TIMEOUT_TICKS = 60;

    public static final int HISTORY_SAMPLES = 120;
    private static final int HISTORY_INTERVAL_TICKS = 20;

    private final NetscanHost host;

    private ScanGraph graph = ScanGraph.EMPTY;
    private KineticWalk walk;
    private WorldProbe probe;

    private long lastCompletedTick;
    private long lastLiveSyncTick;
    private long lastHistoryTick;
    private long seenNetworkId;
    private int seenNetworkSize = -1;
    private boolean structureDirty;

    private final Map<UUID, Long> watchers = new HashMap<>();

    private final List<float[]> history = new ArrayList<>();

    public NetscanEngine(NetscanHost host) {
        this.host = host;
    }

    public ScanGraph graph() {
        return this.graph;
    }

    public void setGraph(ScanGraph graph) {
        this.graph = graph == null ? ScanGraph.EMPTY : graph;
    }

    public float sweepProgress() {
        if (this.walk == null) {
            return 1.0f;
        }
        final int budget = Math.max(1, this.walk.budget());
        return Math.min(1.0f, this.walk.visitedCount() / (float) budget);
    }

    public boolean sweeping() {
        return this.walk != null;
    }

    public int sweepCount() {
        return this.walk == null ? this.graph.size() : this.walk.visitedCount();
    }

    public List<float[]> history() {
        return this.history;
    }

    public boolean seedMissing() {
        return this.host.scanSeed() == null;
    }

    public boolean serverTick(ServerLevel level, long gameTime) {
        pruneWatchers(gameTime);
        this.structureDirty = false;

        if (!this.host.scanPowered()) {
            if (!this.graph.isEmpty() || this.walk != null) {
                this.graph = ScanGraph.EMPTY;
                this.walk = null;
                this.history.clear();
                this.structureDirty = true;
            }
            return this.structureDirty;
        }

        final BlockPos seed = this.host.scanSeed();
        if (seed == null) {
            if (!this.graph.isEmpty() || this.walk != null) {
                this.graph = ScanGraph.EMPTY;
                this.walk = null;
                this.structureDirty = true;
            }
            return this.structureDirty;
        }

        if (this.probe == null) {
            this.probe = new WorldProbe(level);
        }

        if (this.walk == null && shouldRestart(seed, gameTime)) {
            this.walk = new KineticWalk(seed, this.host.scannerTier().elementBudget());

            this.probe = new WorldProbe(level);
        }

        if (this.walk != null) {
            final boolean more = this.walk.step(this.host.scannerTier().elementsPerTick(), this.probe);
            if (!more) {
                finishSweep(seed, gameTime);
            }
        }

        if (!this.graph.isEmpty()) {
            recordHistory(level, seed, gameTime);
            syncLive(level, gameTime);
        }
        return this.structureDirty;
    }

    private boolean shouldRestart(BlockPos seed, long gameTime) {
        if (this.graph.isEmpty()) {
            return true;
        }
        final long networkId = this.probe.networkIdAt(seed);
        final int networkSize = this.probe.networkSizeAt(seed);
        if (networkId != this.seenNetworkId || networkSize != this.seenNetworkSize) {
            return true;
        }
        return gameTime - this.lastCompletedTick >= rescanInterval();
    }

    private void finishSweep(BlockPos seed, long gameTime) {
        final ScanGraph fresh = this.walk.build(this.probe.networkIdAt(seed), gameTime);
        this.structureDirty = fresh.contentHash() != this.graph.contentHash();
        this.graph = fresh;
        this.walk = null;
        this.lastCompletedTick = gameTime;
        this.seenNetworkId = this.probe.networkIdAt(seed);
        this.seenNetworkSize = this.probe.networkSizeAt(seed);
    }

    private void recordHistory(ServerLevel level, BlockPos seed, long gameTime) {
        if (this.host.scannerTier().detailLevel() < 3) {
            return;
        }
        if (gameTime - this.lastHistoryTick < HISTORY_INTERVAL_TICKS) {
            return;
        }
        this.lastHistoryTick = gameTime;
        this.history.add(new float[]{this.graph.totalStress(), this.graph.totalCapacity()});
        while (this.history.size() > HISTORY_SAMPLES) {
            this.history.remove(0);
        }
    }

    public void watch(ServerPlayer player) {
        if (player != null && player.level() != null) {
            this.watchers.put(player.getUUID(), player.level().getGameTime() + WATCH_TIMEOUT_TICKS);
        }
    }

    public void unwatch(ServerPlayer player) {
        if (player != null) {
            this.watchers.remove(player.getUUID());
        }
    }

    public boolean watched() {
        return !this.watchers.isEmpty();
    }

    private void pruneWatchers(long gameTime) {
        this.watchers.values().removeIf(expiry -> expiry < gameTime);
    }

    private void syncLive(ServerLevel level, long gameTime) {
        if (this.watchers.isEmpty() || gameTime - this.lastLiveSyncTick < liveSyncInterval()) {
            return;
        }
        this.lastLiveSyncTick = gameTime;

        final int n = this.graph.size();
        final float[] speeds = new float[n];
        final float[] stresses = new float[n];
        final float[] capacities = new float[n];
        final boolean[] over = new boolean[n];
        final WorldProbe live = new WorldProbe(level);
        int i = 0;
        for (final BlockPos pos : this.graph.nodes().keySet()) {
            final ScanNode fresh = live.probe(pos);
            final ScanNode fallback = this.graph.node(pos);
            final ScanNode use = fresh != null ? fresh : fallback;
            speeds[i] = use.speed();
            stresses[i] = use.stressImpact();
            capacities[i] = use.capacityProvided();
            over[i] = use.isOverStressed();
            i++;
        }
        this.graph = this.graph.withLive(speeds, stresses, capacities, over);

        final com.createstats.network.NetscanLivePacket packet =
                new com.createstats.network.NetscanLivePacket(this.host.scanPos(),
                        this.graph.contentHash(), speeds, stresses, capacities, over,
                        historySnapshot());
        for (final UUID id : List.copyOf(this.watchers.keySet())) {
            final net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(id);
            if (player instanceof ServerPlayer server) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server, packet);
            } else {
                this.watchers.remove(id);
            }
        }
    }

    private float[] historySnapshot() {
        final float[] flat = new float[this.history.size() * 2];
        for (int i = 0; i < this.history.size(); i++) {
            flat[i * 2] = this.history.get(i)[0];
            flat[i * 2 + 1] = this.history.get(i)[1];
        }
        return flat;
    }

    public void acceptLive(int hash, float[] speeds, float[] stresses, float[] capacities,
                           boolean[] over, float[] flatHistory) {
        if (this.graph.contentHash() != hash) {
            return;
        }
        this.graph = this.graph.withLive(speeds, stresses, capacities, over);
        this.history.clear();
        if (flatHistory != null) {
            for (int i = 0; i + 1 < flatHistory.length; i += 2) {
                this.history.add(new float[]{flatHistory[i], flatHistory[i + 1]});
            }
        }
    }

    public void save(CompoundTag tag) {
        if (!this.graph.isEmpty()) {
            tag.put("NetscanGraph", this.graph.save());
        }
    }

    public void load(CompoundTag tag) {
        if (tag.contains("NetscanGraph")) {
            this.graph = ScanGraph.load(tag.getCompound("NetscanGraph"));
        } else {
            this.graph = ScanGraph.EMPTY;
        }
    }

    private static int rescanInterval() {
        try {
            return com.createstats.ModConfiguration.COMMON.scannerRescanIntervalTicks.get();
        } catch (RuntimeException ignored) {
            return 200;
        }
    }

    private static int liveSyncInterval() {
        try {
            return com.createstats.ModConfiguration.COMMON.scannerLiveSyncIntervalTicks.get();
        } catch (RuntimeException ignored) {
            return 10;
        }
    }

    public static NetscanEngine of(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof NetscanHost host ? host.scanEngine() : null;
    }
}
