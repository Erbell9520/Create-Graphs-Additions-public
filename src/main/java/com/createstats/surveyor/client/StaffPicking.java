package com.createstats.surveyor.client;

import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class StaffPicking {
    public static final double RANGE = 96.0;

    public static final double STAKE_HALF = 0.32;
    public static final double STAKE_HEIGHT = 1.25;
    public static final double POST_HEIGHT = 2.0;
    public static final double HANDLE_HALF = 0.28;

    public static final double LINE_REACH = 0.55;

    public enum Kind {
        NONE, NODE, HANDLE, SEGMENT, VEHICLE, GROUND
    }

    public record Pick(Kind kind, @Nullable UUID pathway, int nodeId, double s, double distance, Vec3 point,
                       @Nullable UUID vehicle, @Nullable BlockHitResult blockHit) {
        public static final Pick NONE = new Pick(Kind.NONE, null, -1, 0, Double.POSITIVE_INFINITY, Vec3.ZERO, null, null);
    }

    private StaffPicking() {
    }

    public static Pick pick(Minecraft mc, float partial, boolean handles, @Nullable UUID onlyPathway, boolean vehicles) {
        Player player = mc.player;
        if (player == null || mc.level == null) return Pick.NONE;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 dir = player.getViewVector(partial);
        Vec3 end = eye.add(dir.scale(RANGE));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double wall = block.getType() == HitResult.Type.MISS ? RANGE : block.getLocation().distanceTo(eye);
        BlockHitResult blockHit = block.getType() == HitResult.Type.MISS ? null : block;

        boolean floor = blockHit != null && blockHit.getDirection() == Direction.UP;
        Pick best = null;
        double bestD = wall + 0.05;
        for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
            Pathway p = e.pathway;
            if (onlyPathway != null && !onlyPathway.equals(p.id())) continue;
            PathSpline sp = e.spline();
            for (PathNode n : p.nodes()) {
                double h = n.isStop() ? POST_HEIGHT : STAKE_HEIGHT;
                AABB box = new AABB(n.x() - STAKE_HALF, n.y(), n.z() - STAKE_HALF, n.x() + STAKE_HALF, n.y() + h, n.z() + STAKE_HALF);
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) continue;
                double d = hit.get().distanceTo(eye);

                if (d <= bestD + (floor ? 0.3 : 0.0)) {
                    bestD = d;
                    best = new Pick(Kind.NODE, p.id(), n.id(), 0, d, new Vec3(n.x(), n.y(), n.z()), null, blockHit);
                }
            }
            if (handles && p.nodes().size() >= 2) {
                int segments = p.closed() ? p.nodes().size() : p.nodes().size() - 1;
                for (int k = 0; k < segments; k++) {
                    double a = sp.nodeS(k);
                    double b = k + 1 < sp.nodeCount() ? sp.nodeS(k + 1) : sp.length();
                    if (p.closed() && k == segments - 1) b = sp.length();
                    double mid = (a + b) * 0.5;
                    Vec3 c = new Vec3(sp.x(mid), sp.y(mid) + 0.35, sp.z(mid));
                    AABB box = new AABB(c.x - HANDLE_HALF, c.y - HANDLE_HALF, c.z - HANDLE_HALF, c.x + HANDLE_HALF, c.y + HANDLE_HALF, c.z + HANDLE_HALF);
                    Optional<Vec3> hit = box.clip(eye, end);
                    if (hit.isEmpty()) continue;
                    double d = hit.get().distanceTo(eye);
                    if (d < bestD) {
                        bestD = d;
                        best = new Pick(Kind.HANDLE, p.id(), p.nodes().get(k).id(), mid, d, c, null, blockHit);
                    }
                }
            }
        }
        if (best != null) return best;
        if (vehicles) {
            for (PathwayClientCache.Vehicle v : PathwayClientCache.vehicles()) {
                if (v.now == null) continue;
                double[] at = v.at(PathwayClientCache.clientTime() + partial);
                AABB box = new AABB(at[0] - 1.6, at[1] - 0.2, at[2] - 1.6, at[0] + 1.6, at[1] + 2.6, at[2] + 1.6);
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) continue;
                double d = hit.get().distanceTo(eye);
                if (d < bestD) {
                    bestD = d;
                    best = new Pick(Kind.VEHICLE, v.now.pathway(), -1, v.now.s(), d, new Vec3(at[0], at[1], at[2]), v.now.console(), blockHit);
                }
            }
            if (best != null) return best;
        }

        Pick line = pickLine(eye, dir, floor ? wall + 0.5 : wall, onlyPathway, blockHit);
        if (line != null) return line;
        if (blockHit != null) {
            Vec3 g = groundFrom(mc, blockHit);
            if (g != null) return new Pick(Kind.GROUND, null, -1, 0, wall, g, null, blockHit);
        }
        return new Pick(Kind.NONE, null, -1, 0, Double.POSITIVE_INFINITY, Vec3.ZERO, null, blockHit);
    }

    @Nullable
    private static Pick pickLine(Vec3 eye, Vec3 dir, double wall, @Nullable UUID only, @Nullable BlockHitResult blockHit) {
        Pick best = null;
        double bestMiss = LINE_REACH;
        for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
            if (only != null && !only.equals(e.pathway.id())) continue;
            PathSpline sp = e.spline();
            var checks = e.lastChecks();
            for (int i = 0; i < sp.sampleCount(); i++) {
                double gy = checks != null && i < checks.ground().length && Double.isFinite(checks.ground()[i]) ? checks.ground()[i] : sp.sampleY(i);
                Vec3 p = new Vec3(sp.sampleX(i), gy + 0.06, sp.sampleZ(i));
                Vec3 rel = p.subtract(eye);
                double along = rel.dot(dir);
                if (along <= 0.0 || along > wall) continue;
                double miss = rel.subtract(dir.scale(along)).length();
                if (miss < bestMiss) {
                    bestMiss = miss;
                    int seg = sp.segmentAt(sp.sampleS(i));
                    int nodeId = seg >= 0 && seg < e.pathway.nodes().size() ? e.pathway.nodes().get(seg).id() : -1;
                    best = new Pick(Kind.SEGMENT, e.pathway.id(), nodeId, sp.sampleS(i), along, p, null, blockHit);
                }
            }
        }
        return best;
    }

    @Nullable
    public static Vec3 groundFrom(Minecraft mc, BlockHitResult hit) {
        if (mc.level == null) return null;
        Vec3 at = hit.getLocation();
        if (hit.getDirection() == Direction.UP) return new Vec3(at.x, at.y, at.z);
        Vec3 out = at.add(hit.getDirection().getStepX() * 0.3, 0.0, hit.getDirection().getStepZ() * 0.3);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double g = PathwayChecks.groundAt(mc.level, out.x, at.y, out.z, pos);
        return Double.isNaN(g) ? null : new Vec3(out.x, g, out.z);
    }

    @Nullable
    public static Vec3 groundAhead(Minecraft mc, float partial) {
        Player player = mc.player;
        if (player == null || mc.level == null) return null;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 end = eye.add(player.getViewVector(partial).scale(RANGE));
        BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() == HitResult.Type.MISS) return null;
        return groundFrom(mc, block);
    }
}
