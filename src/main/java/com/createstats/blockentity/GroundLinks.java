package com.createstats.blockentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.CreateStats;
import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundChannels;
import com.createstats.ground.GroundCodec;
import com.createstats.ground.GroundCommand;
import com.createstats.ground.GroundFeedPlanner;
import com.createstats.ground.GroundFrame;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundTelemetrySource;
import com.createstats.network.GroundFeedPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GroundLinks {
    public static final String SOURCE_TYPE = "VCC Ground Mk IV";

    public static final long HEARTBEAT_TIMEOUT = 200L;

    public static final long REMOVED_DELAY = 40L;
    public static final long CHANNEL_TICKS = 20L;

    private final StatsDisplayPanelBE panel;
    private final Map<BlockPos, Link> links = new LinkedHashMap<>();
    private final Feed feed = new Feed();

    GroundLinks(StatsDisplayPanelBE panel) {
        this.panel = panel;
    }

    static final class Link {
        final BlockPos source;
        long lastBeat;
        long lastPull = Long.MIN_VALUE;
        long lastChannels = Long.MIN_VALUE;
        final GroundFeedPlanner planner = new GroundFeedPlanner();
        GroundSnapshot latest;
        List<GroundAlerts.Code> held = List.of();

        boolean failing;

        Link(BlockPos source, long beat) {
            this.source = source.immutable();
            this.lastBeat = beat;
        }
    }

    public void heartbeat(BlockPos source, long tick) {
        final Link link = this.links.get(source);
        if (link == null) {
            this.links.put(source.immutable(), new Link(source, tick));
            this.panel.markGroundLinksChanged();
            return;
        }
        link.lastBeat = tick;
    }

    public boolean remove(BlockPos source) {
        if (this.links.remove(source) == null) {
            return false;
        }
        this.panel.markGroundLinksChanged();
        return true;
    }

    public boolean hasLinks() {
        return !this.links.isEmpty();
    }

    public BlockPos primarySource() {
        return this.links.isEmpty() ? null : this.links.keySet().iterator().next();
    }

    public GroundSnapshot latestServerSnapshot() {
        final Link link = primary();
        return link == null ? null : link.latest;
    }

    private Link primary() {
        return this.links.isEmpty() ? null : this.links.values().iterator().next();
    }

    public void serverTick(ServerLevel level, long tick) {
        if (this.links.isEmpty()) {
            return;
        }
        boolean changed = false;
        final Iterator<Link> it = this.links.values().iterator();
        while (it.hasNext()) {
            final Link link = it.next();
            final boolean loaded = level.isLoaded(link.source);
            final BlockEntity be = loaded ? level.getBlockEntity(link.source) : null;
            final boolean missing = loaded && !(be instanceof GroundTelemetrySource);
            final long age = tick - link.lastBeat;
            if (age > HEARTBEAT_TIMEOUT || (missing && age > REMOVED_DELAY) || age < 0L) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            this.panel.markGroundLinksChanged();
            if (this.links.isEmpty()) {
                send(level, new byte[0]);
                return;
            }
        }
        final Link link = primary();

        if (link == null || (link.lastPull != Long.MIN_VALUE && tick >= link.lastPull
                && tick - link.lastPull < GroundTelemetrySource.FEED_TICKS)) {
            return;
        }
        link.lastPull = tick;
        if (!level.isLoaded(link.source) || !(level.getBlockEntity(link.source) instanceof GroundTelemetrySource src)) {
            return;
        }
        final GroundSnapshot snapshot;
        try {
            snapshot = src.groundSnapshot();
        } catch (RuntimeException e) {
            if (!link.failing) {
                CreateStats.LOGGER.warn("[Ground] snapshot from {} failed: {}", link.source, e.toString(), e);
                link.failing = true;
            }
            return;
        }
        link.failing = false;
        if (snapshot == null) {
            return;
        }
        link.latest = snapshot;
        if (hasViewers(level)) {
            send(level, link.planner.next(snapshot, tick));
        } else {
            link.planner.reset();
        }
        if (link.lastChannels == Long.MIN_VALUE || tick - link.lastChannels >= CHANNEL_TICKS || tick < link.lastChannels) {
            link.lastChannels = tick;
            publish(link, snapshot);
        }
    }

    private void publish(Link link, GroundSnapshot snapshot) {
        final DriveReadout r = DriveMath.read(snapshot);
        final List<GroundAlerts.Alert> alerts = GroundAlerts.evaluate(r, link.held, 0.0);
        link.held = GroundAlerts.codes(alerts);
        GroundChannels.publish(r, alerts,
                (id, label, value, type) -> this.panel.onAutoData(link.source, SOURCE_TYPE, id, label, value, type));
    }

    private boolean hasViewers(ServerLevel level) {
        return !level.getChunkSource().chunkMap.getPlayers(new ChunkPos(this.panel.getBlockPos()), false).isEmpty();
    }

    private void send(ServerLevel level, byte[] data) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(this.panel.getBlockPos()),
                new GroundFeedPacket(this.panel.getBlockPos(), data));
    }

    public boolean command(ServerLevel level, GroundCommand command) {
        final Link link = primary();
        if (link == null || command == null || link.latest == null || !link.latest.accepts(command)) {
            return false;
        }
        if (!level.isLoaded(link.source) || !(level.getBlockEntity(link.source) instanceof GroundTelemetrySource src)) {
            return false;
        }
        final boolean done = src.onGroundCommand(command);

        link.planner.reset();
        return done;
    }

    public void save(CompoundTag tag, boolean wire) {
        final ListTag list = new ListTag();
        for (final Link link : this.links.values()) {
            final CompoundTag t = new CompoundTag();
            t.putLong("Pos", link.source.asLong());
            t.putLong("Beat", link.lastBeat);
            list.add(t);
        }
        tag.put("GroundLinks", (Tag) list);
        if (wire) {
            final GroundSnapshot latest = latestServerSnapshot();
            if (latest != null) {
                tag.putByteArray("GroundSnap", GroundCodec.encode(latest));
            }
        }
    }

    public void load(CompoundTag tag, boolean client) {
        if (!tag.contains("GroundLinks", Tag.TAG_LIST)) {
            if (client) {
                this.links.clear();
                this.feed.clear();
            }
            return;
        }
        final Map<BlockPos, Link> previous = new LinkedHashMap<>(this.links);
        this.links.clear();
        final ListTag list = tag.getList("GroundLinks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag t = list.getCompound(i);
            final BlockPos pos = BlockPos.of(t.getLong("Pos"));

            final Link kept = previous.get(pos);
            final Link link = kept != null ? kept : new Link(pos, t.getLong("Beat"));
            link.lastBeat = t.getLong("Beat");
            this.links.put(pos, link);
        }
        if (client) {
            if (this.links.isEmpty()) {
                this.feed.clear();
            } else if (tag.contains("GroundSnap", Tag.TAG_BYTE_ARRAY)) {
                this.feed.accept(tag.getByteArray("GroundSnap"));
            }
        }
    }

    public void acceptFeed(byte[] data) {
        if (data == null || data.length == 0) {
            this.feed.clear();
            return;
        }
        this.feed.accept(data);
    }

    public Feed feed() {
        return this.feed;
    }

    public static final class Feed {
        private GroundSnapshot current;
        private GroundSnapshot previous;
        private long currentNanos;
        private long intervalNanos = 200_000_000L;
        private List<GroundAlerts.Code> held = new ArrayList<>();
        private GroundFrame cached;
        private long cachedNanos;

        synchronized void accept(byte[] data) {
            final GroundSnapshot next;
            try {
                next = GroundCodec.decode(data, this.current);
            } catch (IOException e) {
                CreateStats.LOGGER.debug("[Ground] dropped a feed packet: {}", e.getMessage());
                return;
            }
            final long now = System.nanoTime();
            if (this.current != null && next.gameTime() > this.current.gameTime()) {
                this.intervalNanos = Math.max(50_000_000L,
                        Math.min(1_000_000_000L, (next.gameTime() - this.current.gameTime()) * 50_000_000L));
            }
            this.previous = this.current;
            this.current = next;
            this.currentNanos = now;
            this.cached = null;
        }

        synchronized void clear() {
            this.current = null;
            this.previous = null;
            this.held = new ArrayList<>();
            this.cached = null;
        }

        public synchronized boolean hasData() {
            return this.current != null;
        }

        public synchronized double age(long nowNanos) {
            return this.current == null ? Double.POSITIVE_INFINITY : (nowNanos - this.currentNanos) / 1.0e9;
        }

        public synchronized GroundSnapshot interpolated(long nowNanos) {
            if (this.current == null) {
                return null;
            }
            if (this.previous == null) {
                return this.current;
            }
            final double t = DriveMath.clamp((nowNanos - this.currentNanos) / (double) this.intervalNanos, 0.0, 1.0);
            final GroundSnapshot.Body a = this.previous.body();
            final GroundSnapshot.Body b = this.current.body();
            final V3 position = a.position().lerp(b.position(), t);
            final Quat orientation = a.orientation().slerp(b.orientation(), t);
            return this.current.withBody(new GroundSnapshot.Body(position, orientation, b.nose(),
                    a.velocity().lerp(b.velocity(), t), lerp(a.yawRate(), b.yawRate(), t),
                    lerp(a.forwardSpeed(), b.forwardSpeed(), t), a.accel().lerp(b.accel(), t), b.gravity()));
        }

        private static double lerp(double a, double b, double t) {
            return Double.isFinite(a) && Double.isFinite(b) ? a + (b - a) * t : b;
        }

        // cached per frame, two reads would step the alert hysteresis twice
        public synchronized GroundFrame frame(long nowNanos, long tick, float partial) {
            if (this.cached != null && nowNanos - this.cachedNanos < 16_000_000L && nowNanos >= this.cachedNanos) {
                return this.cached;
            }
            final GroundSnapshot s = interpolated(nowNanos);
            if (s == null) {
                return null;
            }
            final double age = age(nowNanos);
            final DriveReadout r = DriveMath.read(s);
            final List<GroundAlerts.Alert> alerts = GroundAlerts.evaluate(r, this.held, age);
            this.held = GroundAlerts.codes(alerts);
            this.cached = new GroundFrame(r, alerts, age, tick, partial);
            this.cachedNanos = nowNanos;
            return this.cached;
        }
    }
}
