package com.createstats.dynlight;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// pure because it's a throttle and throttles need tests
public final class EmissionPlan {
    public static final int MAX_LEVEL = 15;

    private static final long[] NO_CHANGES = new long[0];

    private static final Comparator<Candidate> ORDER = Comparator
            .comparingDouble(Candidate::distanceSq)
            .thenComparing(Comparator.comparingInt(Candidate::level).reversed())
            .thenComparingLong(Candidate::pos);

    private EmissionPlan() {
    }

    public record Step(Long2ByteMap next, long[] changed, int deferred) {
        public boolean isIdle() {
            return this.changed.length == 0;
        }
    }

    public static void mergeMax(Long2ByteMap cells, long pos, int level) {
        if (level <= 0) {
            return;
        }
        final byte clamped = (byte) Math.min(level, MAX_LEVEL);
        if (clamped > cells.get(pos)) {
            cells.put(pos, clamped);
        }
    }

    public static Step plan(Long2ByteMap applied, Long2ByteMap wanted,
                            double cameraX, double cameraY, double cameraZ,
                            int maxCells, int maxChanges) {
        final Long2ByteMap target = nearest(wanted, cameraX, cameraY, cameraZ, Math.max(0, maxCells));

        final List<Candidate> removals = new ArrayList<>();
        for (Long2ByteMap.Entry e : applied.long2ByteEntrySet()) {
            final long pos = e.getLongKey();
            if (target.get(pos) <= 0) {
                removals.add(new Candidate(pos, 0, distanceSq(pos, cameraX, cameraY, cameraZ)));
            }
        }
        final List<Candidate> updates = new ArrayList<>();
        for (Long2ByteMap.Entry e : target.long2ByteEntrySet()) {
            final long pos = e.getLongKey();
            final byte level = e.getByteValue();
            if (applied.get(pos) != level) {
                updates.add(new Candidate(pos, level, distanceSq(pos, cameraX, cameraY, cameraZ)));
            }
        }

        final int differences = removals.size() + updates.size();
        final int take = Math.min(Math.max(0, maxChanges), differences);
        if (take == 0) {
            return new Step(applied, NO_CHANGES, differences);
        }
        removals.sort(ORDER);
        updates.sort(ORDER);

        final Long2ByteOpenHashMap next = new Long2ByteOpenHashMap(applied);
        final long[] changed = new long[take];
        int n = 0;
        for (Candidate c : removals) {
            if (n == take) {
                break;
            }
            next.remove(c.pos());
            changed[n++] = c.pos();
        }
        for (Candidate c : updates) {
            if (n == take) {
                break;
            }
            next.put(c.pos(), (byte) c.level());
            changed[n++] = c.pos();
        }
        return new Step(Long2ByteMaps.unmodifiable(next), changed, differences - take);
    }

    static Long2ByteMap nearest(Long2ByteMap wanted, double cameraX, double cameraY, double cameraZ, int maxCells) {
        final List<Candidate> legal = new ArrayList<>(wanted.size());
        for (Long2ByteMap.Entry e : wanted.long2ByteEntrySet()) {
            final int level = Math.min(e.getByteValue(), MAX_LEVEL);
            if (level > 0) {
                final long pos = e.getLongKey();
                legal.add(new Candidate(pos, level, distanceSq(pos, cameraX, cameraY, cameraZ)));
            }
        }
        if (legal.size() > maxCells) {
            legal.sort(ORDER);
        }
        final int keep = Math.min(maxCells, legal.size());
        final Long2ByteOpenHashMap target = new Long2ByteOpenHashMap(keep);
        for (int i = 0; i < keep; i++) {
            final Candidate c = legal.get(i);
            target.put(c.pos(), (byte) c.level());
        }
        return target;
    }

    static double distanceSq(long pos, double cameraX, double cameraY, double cameraZ) {
        final double x = BlockPos.getX(pos) + 0.5 - cameraX;
        final double y = BlockPos.getY(pos) + 0.5 - cameraY;
        final double z = BlockPos.getZ(pos) + 0.5 - cameraZ;
        return x * x + y * y + z * z;
    }

    private record Candidate(long pos, int level, double distanceSq) {
    }
}
