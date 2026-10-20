package com.createstats.statsmk3.helper;

import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.blockentity.StatusTowerLightBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HelperScan {
    private static final int RADIUS = 16;

    private HelperScan() {
    }

    public static final class Result {
        public static final Result EMPTY = new Result();

        public int alarms;
        public int annunciators;
        public int beacons;
        public int recorders;

        public boolean alarmFiring;

        public boolean isEmpty() {
            return this.alarms == 0 && this.annunciators == 0
                    && this.beacons == 0 && this.recorders == 0;
        }

        public int total() {
            return this.alarms + this.annunciators + this.beacons + this.recorders;
        }
    }

    public static Result scan(StatsDisplayPanelBE panel) {
        final Result result = new Result();
        if (panel == null) {
            return result;
        }
        final Level level = panel.getLevel();
        if (level == null) {
            return result;
        }
        final BlockPos origin = panel.getControllerBE().getBlockPos();

        final int minChunkX = (origin.getX() - RADIUS) >> 4;
        final int maxChunkX = (origin.getX() + RADIUS) >> 4;
        final int minChunkZ = (origin.getZ() - RADIUS) >> 4;
        final int maxChunkZ = (origin.getZ() + RADIUS) >> 4;
        final int radiusSq = RADIUS * RADIUS;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (final BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.distSqr(origin) > radiusSq) {
                        continue;
                    }
                    final BlockEntity be = chunk.getBlockEntity(pos);
                    if (be == null) {
                        continue;
                    }
                    if (be instanceof StatsAlarmBE) {
                        result.alarms++;
                    } else if (be instanceof AnnunciatorBE) {
                        result.annunciators++;
                    } else if (be instanceof StatusTowerLightBE) {
                        result.beacons++;
                    } else if (be instanceof StatsRecorderBE) {
                        result.recorders++;
                    }
                }
            }
        }

        for (final StatsDisplayPanelBE.DataChannel ch : safeChannels(panel)) {
            if (ch.isAlertEnabled() && ch.isAlertTriggered()) {
                result.alarmFiring = true;
                break;
            }
        }
        return result;
    }

    private static java.util.List<StatsDisplayPanelBE.DataChannel> safeChannels(StatsDisplayPanelBE panel) {
        try {
            final java.util.List<StatsDisplayPanelBE.DataChannel> channels = panel.getActiveChannels();
            return channels == null ? java.util.List.of() : channels;
        } catch (final RuntimeException e) {
            return java.util.List.of();
        }
    }
}
