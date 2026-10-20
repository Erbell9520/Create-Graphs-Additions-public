package com.createstats.surveyor.client;

import com.createstats.surveyor.core.PathNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class StaffSnap {
    static final double LINE_REACH = 1.5;

    static final double ROAD_REACH = 8.0;

    static final double ROAD_STEP = 0.5;

    private StaffSnap() {
    }

    public static Vec3 apply(@Nullable Level level, Vec3 at, Vec3 look, @Nullable UUID movingPathway, int movingNode) {
        return switch (SurveyorClientOptions.snap()) {
            case OFF -> at;
            case GRID -> new Vec3(Math.floor(at.x) + 0.5, at.y, Math.floor(at.z) + 0.5);
            case ROAD -> level == null ? at : road(level, at, look);
            case LINES -> lines(at, movingPathway, movingNode);
        };
    }

    static Vec3 lines(Vec3 at, @Nullable UUID movingPathway, int movingNode) {
        Vec3 best = at;
        double bestD = LINE_REACH;
        for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
            boolean same = e.pathway.id().equals(movingPathway);
            for (PathNode n : e.pathway.nodes()) {
                if (same && n.id() == movingNode) continue;
                double d = Math.hypot(n.x() - at.x, n.z() - at.z);
                if (d <= bestD && Math.abs(n.y() - at.y) <= 2.0) {
                    bestD = d;
                    best = new Vec3(n.x(), n.y(), n.z());
                }
            }
        }
        return best;
    }

    static Vec3 road(Level level, Vec3 at, Vec3 look) {
        double hx = look.x;
        double hz = look.z;
        double len = Math.hypot(hx, hz);
        if (len < 1e-6) return at;

        double ax = -hz / len;
        double az = hx / len;
        BlockPos under = BlockPos.containing(at.x, at.y - 0.05, at.z);
        Block surface = level.getBlockState(under).getBlock();
        if (level.getBlockState(under).isAir()) return at;
        double left = run(level, at, under.getY(), surface, -ax, -az);
        double right = run(level, at, under.getY(), surface, ax, az);
        if (left >= ROAD_REACH && right >= ROAD_REACH) return at;
        double shift = (right - left) / 2.0;
        return new Vec3(at.x + ax * shift, at.y, at.z + az * shift);
    }

    private static double run(Level level, Vec3 at, int y, Block surface, double dx, double dz) {
        for (double t = ROAD_STEP; t <= ROAD_REACH; t += ROAD_STEP) {
            int bx = (int) Math.floor(at.x + dx * t);
            int bz = (int) Math.floor(at.z + dz * t);
            boolean same = false;
            for (int dy = -1; dy <= 1 && !same; dy++) {
                same = level.getBlockState(new BlockPos(bx, y + dy, bz)).is(surface);
            }
            if (!same) return t - ROAD_STEP / 2.0;
        }
        return ROAD_REACH;
    }
}
