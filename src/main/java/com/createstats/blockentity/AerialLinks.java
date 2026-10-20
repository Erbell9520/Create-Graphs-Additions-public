package com.createstats.blockentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.CreateStats;
import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialChannels;
import com.createstats.aerial.AerialCodec;
import com.createstats.aerial.AerialCommand;
import com.createstats.aerial.AerialFeedPlanner;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialTelemetrySource;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.NeedleSpring;
import com.createstats.aerial.Quat;
import com.createstats.aerial.V3;
import com.createstats.network.AerialFeedPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AerialLinks {
    public static final String SOURCE_TYPE = "VCC Mk IV Aerial";

    public static final long HEARTBEAT_TIMEOUT = 200L;

    public static final long REMOVED_DELAY = 40L;
    public static final long CHANNEL_TICKS = 20L;

    private final StatsDisplayPanelBE panel;
    private final Map<BlockPos, Link> links = new LinkedHashMap<>();
    private final Feed feed = new Feed();

    AerialLinks(StatsDisplayPanelBE panel) {
        this.panel = panel;
    }

    static final class Link {
        final BlockPos source;
        long lastBeat;
        long lastPull = Long.MIN_VALUE;
        long lastChannels = Long.MIN_VALUE;
        final AerialFeedPlanner planner = new AerialFeedPlanner();
        AerialSnapshot latest;
        List<AerialAlerts.Code> held = List.of();

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
            this.panel.markAerialLinksChanged();
            return;
        }
        link.lastBeat = tick;
    }

    public boolean remove(BlockPos source) {
        if (this.links.remove(source) == null) {
            return false;
        }
        this.panel.markAerialLinksChanged();
        return true;
    }

    public boolean hasLinks() {
        return !this.links.isEmpty();
    }

    public BlockPos primarySource() {
        return this.links.isEmpty() ? null : this.links.keySet().iterator().next();
    }

    public AerialSnapshot latestServerSnapshot() {
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
            final boolean missing = loaded && !(be instanceof AerialTelemetrySource);
            final long age = tick - link.lastBeat;
            if (age > HEARTBEAT_TIMEOUT || (missing && age > REMOVED_DELAY) || age < 0L) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            this.panel.markAerialLinksChanged();
            if (this.links.isEmpty()) {
                send(level, new byte[0]);
                return;
            }
        }
        final Link link = primary();

        if (link == null || (link.lastPull != Long.MIN_VALUE && tick >= link.lastPull
                && tick - link.lastPull < AerialTelemetrySource.FEED_TICKS)) {
            return;
        }
        link.lastPull = tick;
        if (!level.isLoaded(link.source) || !(level.getBlockEntity(link.source) instanceof AerialTelemetrySource src)) {
            return;
        }
        final AerialSnapshot snapshot;
        try {
            snapshot = src.aerialSnapshot();
        } catch (RuntimeException e) {
            if (!link.failing) {
                CreateStats.LOGGER.warn("[Aerial] snapshot from {} failed: {}", link.source, e.toString(), e);
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

    private void publish(Link link, AerialSnapshot snapshot) {
        final AirframeReadout r = AirframeMath.read(snapshot);
        final List<AerialAlerts.Alert> alerts = AerialAlerts.evaluate(r, link.held, 0.0);
        link.held = AerialAlerts.codes(alerts);
        AerialChannels.publish(r, alerts,
                (id, label, value, type) -> this.panel.onAutoData(link.source, SOURCE_TYPE, id, label, value, type));
    }

    private boolean hasViewers(ServerLevel level) {
        return !level.getChunkSource().chunkMap.getPlayers(new ChunkPos(this.panel.getBlockPos()), false).isEmpty();
    }

    private void send(ServerLevel level, byte[] data) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(this.panel.getBlockPos()),
                new AerialFeedPacket(this.panel.getBlockPos(), data));
    }

    public boolean command(ServerLevel level, AerialCommand command) {
        final Link link = primary();
        if (link == null || command == null || link.latest == null || !link.latest.accepts(command)) {
            return false;
        }
        if (!level.isLoaded(link.source) || !(level.getBlockEntity(link.source) instanceof AerialTelemetrySource src)) {
            return false;
        }
        final boolean done = src.onAerialCommand(command);

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
        tag.put("AerialLinks", (Tag) list);
        if (wire) {
            final AerialSnapshot latest = latestServerSnapshot();
            if (latest != null) {
                tag.putByteArray("AerialSnap", AerialCodec.encode(latest));
            }
        }
    }

    public void load(CompoundTag tag, boolean client) {
        if (!tag.contains("AerialLinks", Tag.TAG_LIST)) {
            if (client) {
                this.links.clear();
                this.feed.clear();
            }
            return;
        }
        final Map<BlockPos, Link> previous = new LinkedHashMap<>(this.links);
        this.links.clear();
        final ListTag list = tag.getList("AerialLinks", Tag.TAG_COMPOUND);
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
            } else if (tag.contains("AerialSnap", Tag.TAG_BYTE_ARRAY)) {
                this.feed.accept(tag.getByteArray("AerialSnap"));
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
        private AerialSnapshot current;
        private AerialSnapshot previous;
        private long currentNanos;
        private long intervalNanos = 200_000_000L;
        private List<AerialAlerts.Code> held = new ArrayList<>();

        synchronized void accept(byte[] data) {
            final AerialSnapshot next;
            try {
                next = AerialCodec.decode(data, this.current);
            } catch (IOException e) {
                CreateStats.LOGGER.debug("[Aerial] dropped a feed packet: {}", e.getMessage());
                return;
            }
            final long now = System.nanoTime();
            if (this.current != null && next.gameTime() > this.current.gameTime()) {
                this.intervalNanos = Math.max(50_000_000L,
                        Math.min(1_000_000_000L, (next.gameTime() - this.current.gameTime()) * 50_000_000L));
            }

            if (this.current != null && this.current.helm().known() && next.helm().known()
                    && next.helm().rings() > this.current.helm().rings()) {
                this.rungNanos = now;
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
            this.orderNeedle.reset();
            this.engineNeedle.reset();
            this.rungNanos = Long.MIN_VALUE;
        }

        public synchronized boolean hasData() {
            return this.current != null;
        }

        public synchronized double age(long nowNanos) {
            return this.current == null ? Double.POSITIVE_INFINITY : (nowNanos - this.currentNanos) / 1.0e9;
        }

        public synchronized AerialSnapshot interpolated(long nowNanos) {
            if (this.current == null) {
                return null;
            }
            if (this.previous == null) {
                return this.current;
            }
            final double t = AirframeMath.clamp((nowNanos - this.currentNanos) / (double) this.intervalNanos, 0.0, 1.0);
            final AerialSnapshot.Body a = this.previous.body();
            final AerialSnapshot.Body b = this.current.body();
            final V3 position = a.position().lerp(b.position(), t);
            final Quat orientation = a.orientation().slerp(b.orientation(), t);
            return this.current.withBody(new AerialSnapshot.Body(position, orientation, b.nose(),
                    a.velocity().lerp(b.velocity(), t), a.spin().lerp(b.spin(), t)));
        }

        public synchronized AerialFrame frame(long nowNanos) {
            if (this.cached != null && nowNanos - this.cachedNanos < 16_000_000L && nowNanos >= this.cachedNanos) {
                return this.cached;
            }
            AerialSnapshot s = interpolated(nowNanos);
            if (s == null) {
                return null;
            }

            final AerialSnapshot.Helm h = s.helm();
            if (h.known()) {
                final double dt = this.cached == null ? 0.0 : Math.min(0.25, (nowNanos - this.cachedNanos) / 1.0e9);
                final boolean snap = com.createstats.statsmk3.ui.Motion.reducedMotion();
                final double order = snap ? this.orderNeedle.snap(h.order()) : this.orderNeedle.follow(h.order(), dt);
                final double engine = snap ? this.engineNeedle.snap(h.engine()) : this.engineNeedle.follow(h.engine(), dt);
                s = s.withMind(h.withTelegraph(order, engine), s.parts());
            }
            final double age = age(nowNanos);
            final AirframeReadout r = AirframeMath.read(s);
            final List<AerialAlerts.Alert> alerts = AerialAlerts.evaluate(r, this.held, age);
            this.held = AerialAlerts.codes(alerts);
            final double rungAgo = this.rungNanos == Long.MIN_VALUE ? Double.POSITIVE_INFINITY
                    : Math.max(0.0, (nowNanos - this.rungNanos) / 1.0e9);
            this.cached = new AerialFrame(r, alerts, age, rungAgo);
            this.cachedNanos = nowNanos;
            return this.cached;
        }

        private AerialFrame cached;
        private long cachedNanos;
        private final NeedleSpring orderNeedle = new NeedleSpring();
        private final NeedleSpring engineNeedle = new NeedleSpring();

        private long rungNanos = Long.MIN_VALUE;
    }
}
