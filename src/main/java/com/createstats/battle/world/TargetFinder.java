package com.createstats.battle.world;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.ShipFrame;
import com.createstats.battle.core.TargetTrack;
import com.createstats.battle.core.ThreatOrder;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TargetFinder {
    public static final int CONTACT_EVERY = 10;
    public static final double MIN_RADIUS = 32;
    public static final double MAX_RADIUS = 320;

    public static final double GONE_RANGE = 128;

    public static final int KIND_ENTITY = 0;
    public static final int KIND_SHIP = 1;
    public static final int KIND_PLAYER = 2;

    private TargetFinder() {
    }

    static final class Target {
        final int kind;
        final UUID id;
        String name;
        final String by;
        final long at;
        long lostSince = -1L;
        final TargetTrack track = new TargetTrack();
        int entityId = -1;
        double radius = 1;
        double lastRange = Double.NaN;
        @Nullable Entity entity;

        Target(int kind, UUID id, String name, String by, long at) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.by = by;
            this.at = at;
        }
    }

    public record Contact(int kind, int entityId, String shipId, String name, double range, double bearing,
                          double elevation, double closing, boolean hostile, boolean targetsCrew, double radius,
                          int band) {
    }

    static double radius(ShipRecord r) {
        double best = 0;
        for (GunRecord g : r.guns) if (g.maxRange > best) best = g.maxRange;
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, best));
    }

    enum Look { NONE, SEEN, LOST, DOWN, GONE }

    static Look track(ShipRecord r, ServerLevel level, Vec3 bridge, long now) {
        Target t = r.target;
        if (t == null) return Look.NONE;
        double maxRange = radius(r) * 2;
        if (t.kind == KIND_SHIP) {
            Object sub = SableBridge.findSubLevelById(level, t.id);
            if (sub == null) {
                if (!Double.isNaN(t.lastRange) && t.lastRange < GONE_RANGE) return Look.GONE;
                t.track.unseen(now);
                return Look.LOST;
            }
            Vector3d c = SableBridge.subLevelWorldCenter(sub);
            if (c == null) {
                t.track.unseen(now);
                return Look.LOST;
            }
            AABB b = SablePlots.worldBounds(sub);
            if (b != null) t.radius = Math.min(b.getXsize(), Math.min(b.getYsize(), b.getZsize())) / 2;
            String nm = SableBridge.subLevelName(sub);
            if (nm != null && !nm.isEmpty()) t.name = nm;
            double range = Math.sqrt(c.distanceSquared(bridge.x, bridge.y, bridge.z));
            if (range > maxRange) {
                t.track.unseen(now);
                return Look.LOST;
            }
            t.lastRange = range;
            t.track.observe(now, c.x, c.y, c.z);
            return Look.SEEN;
        }
        Entity e = t.entity;
        if (e == null || e.isRemoved()) {
            if (e != null && e.getRemovalReason() != null && e.getRemovalReason().shouldDestroy()) return Look.DOWN;
            e = level.getEntity(t.id);
            t.entity = e;
        }
        if (e == null || e.isRemoved()) {
            t.track.unseen(now);
            return Look.LOST;
        }
        if (e instanceof LivingEntity le && (le.isDeadOrDying() || !le.isAlive())) return Look.DOWN;
        t.entityId = e.getId();
        Vec3 c = e.getBoundingBox().getCenter();
        double range = c.distanceTo(bridge);
        if (range > maxRange) {
            t.track.unseen(now);
            return Look.LOST;
        }
        t.lastRange = range;
        t.radius = e.getBbWidth() / 2 + 0.25;
        t.track.observe(now, c.x, c.y, c.z);
        return Look.SEEN;
    }

    static BattleReport.TargetLine line(ShipRecord r, @Nullable Object body, Vec3 bridge, double[] ownVel) {
        Target t = r.target;
        if (t == null) return BattleReport.TargetLine.NONE;
        if (!t.track.hasFix()) {
            return new BattleReport.TargetLine(t.kind, t.name, t.entityId, t.kind == KIND_SHIP ? t.id.toString() : "",
                    0f, 0f, 0f, 0f, 0f, 0f, t.by, t.at, t.lostSince);
        }
        double[] p = t.track.position();
        double[] v = t.track.velocity();
        double[] rel = {p[0] - bridge.x, p[1] - bridge.y, p[2] - bridge.z};
        double range = Math.sqrt(rel[0] * rel[0] + rel[1] * rel[1] + rel[2] * rel[2]);
        double[] bearing = bearing(r, body, rel);
        double closing = 0;
        if (range > 1e-6) {
            double rate = ((v[0] - ownVel[0]) * rel[0] + (v[1] - ownVel[1]) * rel[1] + (v[2] - ownVel[2]) * rel[2]) / range;
            closing = -rate * 20;
        }

        double[] relVel = {v[0] - ownVel[0], v[1] - ownVel[1], v[2] - ownVel[2]};
        double speed = Math.sqrt(relVel[0] * relVel[0] + relVel[2] * relVel[2]) * 20;
        double course = speed < 1e-6 ? 0 : bearing(r, body, relVel)[0];
        return new BattleReport.TargetLine(t.kind, t.name, t.entityId, t.kind == KIND_SHIP ? t.id.toString() : "",
                (float) range, (float) bearing[0], (float) bearing[1], (float) closing, (float) course, (float) speed,
                t.by, t.at, t.lostSince);
    }

    static double[] bearing(ShipRecord r, @Nullable Object body, double[] worldOffset) {
        double[] d = worldOffset;
        if (body != null) {
            Quaterniond q = SableBridge.bodyOrientation(body);
            if (q != null) {
                Vector3d l = new Quaterniond(q).conjugate().transform(new Vector3d(d[0], d[1], d[2]));
                d = new double[]{l.x, l.y, l.z};
            }
        }
        ShipFrame f = new ShipFrame(r.ground() ? 3 : r.bow);

        double fore = d[0] * f.bowX() + d[2] * f.bowZ();
        double stbd = -d[0] * f.bowZ() + d[2] * f.bowX();
        double horiz = Math.sqrt(fore * fore + stbd * stbd);
        double brg = Math.toDegrees(Math.atan2(stbd, fore));
        double elev = Math.toDegrees(Math.atan2(d[1], horiz));
        return new double[]{brg, elev};
    }

    static double[] fromBearing(ShipRecord r, @Nullable Object body, double bearingDeg, double elevDeg) {
        ShipFrame f = new ShipFrame(r.ground() ? 3 : r.bow);
        double b = Math.toRadians(bearingDeg);
        double e = Math.toRadians(elevDeg);
        double fore = Math.cos(e) * Math.cos(b);
        double stbd = Math.cos(e) * Math.sin(b);

        double[] local = {fore * f.bowX() - stbd * f.bowZ(), Math.sin(e), fore * f.bowZ() + stbd * f.bowX()};
        return Frames.dirToWorld(body, local);
    }

    static List<Contact> contacts(ShipRecord r, ServerLevel level, Vec3 bridge, @Nullable Object body,
                                  @Nullable AABB ownBounds, double[] ownVel, long now) {
        double radius = radius(r);
        List<Contact> raw = new ArrayList<>();
        List<ThreatOrder.Contact> order = new ArrayList<>();
        AABB box = new AABB(bridge, bridge).inflate(radius);
        AABB aboard = ownBounds == null ? null : ownBounds.inflate(1.5);
        List<Player> crew = new ArrayList<>();
        if (aboard != null) crew.addAll(level.getEntitiesOfClass(Player.class, aboard, p -> !p.isSpectator()));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, le -> le.isAlive() && !le.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            if (c.distanceTo(bridge) > radius) continue;
            if (aboard != null && aboard.contains(c)) continue;
            boolean player = e instanceof Player;
            boolean hostile = e instanceof Enemy;
            boolean targetsCrew = e instanceof Mob mob && mob.getTarget() instanceof Player p && crew.contains(p);
            int kind = player ? KIND_PLAYER : KIND_ENTITY;
            double[] pos = {c.x, c.y, c.z};
            Contact k = measure(r, body, bridge, ownVel, kind, e.getId(), "", nameOf(e), pos,
                    velocity(r, "e" + e.getId(), pos, now), hostile, targetsCrew, e.getBbWidth() / 2 + 0.25);
            raw.add(k);
        }
        for (Object sub : SableBridge.listSubLevels(level)) {
            UUID id = SableBridge.subLevelId(sub);
            if (id == null || r.ownBodies.contains(id)) continue;
            Vector3d c = SableBridge.subLevelWorldCenter(sub);
            if (c == null || c.distance(bridge.x, bridge.y, bridge.z) > radius) continue;
            AABB b = SablePlots.worldBounds(sub);
            double rad = b == null ? 2 : Math.min(b.getXsize(), Math.min(b.getYsize(), b.getZsize())) / 2;
            String nm = SableBridge.subLevelName(sub);
            double[] pos = {c.x, c.y, c.z};
            raw.add(measure(r, body, bridge, ownVel, KIND_SHIP, -1, id.toString(), nm == null ? "" : nm, pos,
                    velocity(r, "s" + id, pos, now), false, false, rad));
        }
        r.contactHistory.values().removeIf(h -> now - (long) h[3] > 200);
        for (int i = 0; i < raw.size(); i++) {
            Contact k = raw.get(i);
            boolean current = r.target != null && (k.kind() == KIND_SHIP ? r.target.id.toString().equals(k.shipId())
                    : k.entityId() == r.target.entityId);
            ThreatOrder.Kind tk = k.kind() == KIND_SHIP ? ThreatOrder.Kind.SHIP
                    : k.kind() == KIND_PLAYER ? ThreatOrder.Kind.PLAYER : ThreatOrder.Kind.MOB;
            double approach = k.closing() > 0.5 ? k.range() / k.closing() : Double.MAX_VALUE;
            order.add(new ThreatOrder.Contact(i, tk, current, k.range(), -k.closing(), approach, k.hostile(), k.targetsCrew()));
        }
        List<Contact> out = new ArrayList<>();
        for (ThreatOrder.Contact o : ThreatOrder.order(order)) {
            Contact k = raw.get(o.index());
            out.add(new Contact(k.kind(), k.entityId(), k.shipId(), k.name(), k.range(), k.bearing(), k.elevation(),
                    k.closing(), k.hostile(), k.targetsCrew(), k.radius(), ThreatOrder.band(o)));
        }
        return out;
    }

    private static Vec3 velocity(ShipRecord r, String key, double[] pos, long now) {
        double[] prev = r.contactHistory.put(key, new double[]{pos[0], pos[1], pos[2], now});
        if (prev == null) return Vec3.ZERO;
        double dt = now - prev[3];
        if (dt <= 0 || dt > 40) return Vec3.ZERO;
        return new Vec3((pos[0] - prev[0]) / dt, (pos[1] - prev[1]) / dt, (pos[2] - prev[2]) / dt);
    }

    private static Contact measure(ShipRecord r, @Nullable Object body, Vec3 bridge, double[] ownVel, int kind, int entityId,
                                   String shipId, String name, double[] pos, Vec3 vel, boolean hostile, boolean targetsCrew,
                                   double radius) {
        double[] rel = {pos[0] - bridge.x, pos[1] - bridge.y, pos[2] - bridge.z};
        double range = Math.sqrt(rel[0] * rel[0] + rel[1] * rel[1] + rel[2] * rel[2]);
        double[] b = bearing(r, body, rel);
        double closing = 0;
        if (range > 1e-6) {
            double rate = ((vel.x - ownVel[0]) * rel[0] + (vel.y - ownVel[1]) * rel[1] + (vel.z - ownVel[2]) * rel[2]) / range;
            closing = -rate * 20;
        }
        return new Contact(kind, entityId, shipId, name, range, b[0], b[1], closing, hostile, targetsCrew, radius, 0);
    }

    static String nameOf(Entity e) {
        if (e instanceof Player p) return p.getGameProfile().getName();
        if (e.hasCustomName() && e.getCustomName() != null) return e.getCustomName().getString();
        return "#" + e.getType().getDescriptionId();
    }
}
