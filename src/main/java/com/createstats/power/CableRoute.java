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

/** [1] */
public final class CableRoute {

    private CableRoute() {
    }

    /**
     * One cable placement.
     *
     * @param pos  the empty block the cable body occupies
     * @param face the direction from {@code pos} to the solid block the cable is stuck to
     */
    public record Step(BlockPos pos, Direction face) {

        /** The solid block this cable is mounted on. */
        public BlockPos support() {
            return pos.relative(face);
        }

        /** Packed identity, so the search can key a map without hashing objects. */
        long key() {
            return pos.asLong() * 6L + face.ordinal();
        }
    }

    /** What the router needs to know about the world, and nothing more. */
    public interface SurfaceView {

        /** True when a cable may occupy this position: air, or something replaceable like grass. */
        boolean isFree(BlockPos pos);

        /** True when this block can hold a cable on its faces. */
        boolean isSolid(BlockPos pos);
    }

    /**
     * The shortest route from {@code from} to {@code to}, inclusive of both, or an empty list when
     * there is no route inside {@code maxSteps}.
     *
     * <p>Returning empty rather than a partial route is deliberate: showing a player half a run that
     * stops in mid air would suggest the game is offering something it cannot actually place.</p>
     *
     * @param maxSteps hard cap on route length, so a hopeless search cannot walk the whole world
     */
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

    /** A cable can sit here only if the space is empty and the thing behind it is solid. */
    public static boolean isPlaceable(SurfaceView view, Step step) {
        return view.isFree(step.pos()) && view.isSolid(step.support());
    }

    /** [2] */
    public static List<Step> neighbours(SurfaceView view, Step from) {
        final List<Step> out = new ArrayList<>(6);
        final Direction face = from.face();
        for (Direction d : Direction.values()) {
            if (d.getAxis() == face.getAxis()) {
                continue; // along the mounting normal, not across the surface
            }
            final BlockPos ahead = from.pos().relative(d);
            final BlockPos aheadSupport = ahead.relative(face);

            if (view.isFree(ahead) && view.isSolid(aheadSupport)) {
                // 1. Flat: the surface carries on.
                out.add(new Step(ahead, face));
            } else if (view.isSolid(ahead)) {
                // 2. Concave: the run has hit a wall, so it turns and climbs it. Same block
                //    position, new face, which is the case that needs a multi-face cable block.
                out.add(new Step(from.pos(), d));
            } else if (view.isFree(ahead) && view.isFree(aheadSupport)) {
                // 3. Convex: the surface has run out, so fold around the outside edge and continue
                //    on the far side of the same support block.
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
