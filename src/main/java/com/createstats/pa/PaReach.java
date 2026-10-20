package com.createstats.pa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.createstats.blockentity.AnnouncementComputerBE;
import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.power.FeWireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PaReach {
    private static int chunkSpan(int radius) {
        return (radius >> 4) + 1;
    }

    private PaReach() {
    }

    public record Found(BlockPos pos, BlockEntity be, Kind kind, double distance, boolean viaCable) {
        public enum Kind {
            DISPLAY,

            ANNUNCIATOR,

            ALARM,
        }
    }

    public static List<Found> scan(ServerLevel level, AnnouncementComputerBE brain) {
        return scan(level, brain.getBlockPos(), brain.settings().scanRadius(),
                circuitPositions(brain));
    }

    public static List<Found> scan(ServerLevel level, BlockPos centre, int radius,
                                   Set<Long> circuit) {
        final List<Found> out = new ArrayList<>();
        final Set<Long> seen = new HashSet<>();

        for (Long packed : circuit) {
            final BlockPos pos = BlockPos.of(packed);
            if (pos.equals(centre) || !level.isLoaded(pos)) {
                continue;
            }
            final double distance = Math.sqrt(centre.distSqr(pos));

            add(out, seen, level.getBlockEntity(pos), pos, distance, distance > radius);
        }

        final int span = chunkSpan(radius);
        final int cx = SectionPos.blockToSectionCoord(centre.getX());
        final int cz = SectionPos.blockToSectionCoord(centre.getZ());
        final double maxDistSqr = (double) radius * radius;
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                final LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    final BlockPos pos = be.getBlockPos();
                    final double distSqr = centre.distSqr(pos);
                    if (distSqr > maxDistSqr || pos.equals(centre)) {
                        continue;
                    }
                    add(out, seen, be, pos, Math.sqrt(distSqr), false);
                }
            }
        }

        out.sort((a, b) -> {
            final int byKind = a.kind().compareTo(b.kind());
            return byKind != 0 ? byKind : Double.compare(a.distance(), b.distance());
        });
        return out;
    }

    private static void add(List<Found> out, Set<Long> seen, BlockEntity be, BlockPos pos,
                            double distance, boolean viaCable) {
        final Found.Kind kind = kindOf(be);
        if (kind == null || !seen.add(pos.asLong())) {
            return;
        }
        out.add(new Found(pos.immutable(), be, kind, distance, viaCable));
    }

    private static Found.Kind kindOf(BlockEntity be) {
        if (be instanceof StatsDisplayPanelBE panel) {
            return panel.isController() ? Found.Kind.DISPLAY : null;
        }
        if (be instanceof AnnunciatorBE) {
            return Found.Kind.ANNUNCIATOR;
        }
        return be instanceof StatsAlarmBE ? Found.Kind.ALARM : null;
    }

    public static Set<Long> circuitPositions(AnnouncementComputerBE brain) {
        final FeWireNetwork network = brain.feNetwork();
        return network == null ? Set.of() : network.reachedPositions();
    }
}
