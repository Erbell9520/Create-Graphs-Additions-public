package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CableRoute {
    private CableRoute() {
    }

    public record Step(BlockPos pos, Direction face) {
        public BlockPos support() {
            return pos.relative(face);
        }

        long key() {
            return pos.asLong() * 6L + face.ordinal();
        }
    }

    public interface SurfaceView {
        boolean isFree(BlockPos pos);

        boolean isSolid(BlockPos pos);
    }

    public static List<Step> find(SurfaceView view, Step from, Step to, int maxSteps) {
        if (from.equals(to)) {
            return List.of(from);
        }
        if (!isPlaceable(view, from) || !isPlaceable(view, to)) {
            return List.of();
        }

        final Map<Long, Step> cameFrom = new HashMap<>();
        final Map<Long, Integer> depth = new HashMap<>();
        final Deque<Step> queue = new ArrayDeque<>();
        queue.add(from);
        depth.put(from.key(), 0);

        while (!queue.isEmpty()) {
            final Step current = queue.poll();
            final int d = depth.get(current.key());
            if (d >= maxSteps) {
                continue;
            }
            for (Step next : neighbours(view, current)) {
                final long key = next.key();
                if (depth.containsKey(key)) {
                    continue;
                }
                depth.put(key, d + 1);
                cameFrom.put(key, current);
                if (next.equals(to)) {
                    return rebuild(cameFrom, from, to);
                }
                queue.add(next);
            }
        }
        return List.of();
    }

    public static boolean isPlaceable(SurfaceView view, Step step) {
        return view.isFree(step.pos()) && view.isSolid(step.support());
    }

    public static List<Step> neighbours(SurfaceView view, Step from) {
        final List<Step> out = new ArrayList<>(6);
        final Direction face = from.face();
        for (Direction d : Direction.values()) {
            if (d.getAxis() == face.getAxis()) {
                continue;
            }
            final BlockPos ahead = from.pos().relative(d);
            final BlockPos aheadSupport = ahead.relative(face);

            if (view.isFree(ahead) && view.isSolid(aheadSupport)) {
                out.add(new Step(ahead, face));
            } else if (view.isSolid(ahead)) {
                out.add(new Step(from.pos(), d));
            } else if (view.isFree(ahead) && view.isFree(aheadSupport)) {
                out.add(new Step(aheadSupport, d.getOpposite()));
            }
        }
        return out;
    }

    private static List<Step> rebuild(Map<Long, Step> cameFrom, Step from, Step to) {
        final List<Step> path = new ArrayList<>();
        Step at = to;
        while (at != null && !at.equals(from)) {
            path.add(at);
            at = cameFrom.get(at.key());
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
}
