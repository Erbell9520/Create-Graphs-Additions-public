package com.createstats.alarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createstats.alarm.annunciator.RuleBinding;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.blockentity.AnnouncementComputerBE;
import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.pa.PaBinding;
import com.createstats.pa.PaSubscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class AlarmCoverage {
    public static final int RADIUS = 32;

    private AlarmCoverage() {
    }

    public record Index(Set<String> spoken, Map<String, Integer> alarmed) {
        public static final Index EMPTY = new Index(Set.of(), Map.of());

        public boolean isSpoken(RuleBinding binding) {
            return binding != null && this.spoken.contains(identity(binding));
        }

        public int alarms(RuleBinding binding) {
            return binding == null ? 0 : this.alarmed.getOrDefault(identity(binding), 0);
        }

        public String note(RuleBinding binding, boolean panelAlerts) {
            final List<String> parts = new ArrayList<>(3);
            if (panelAlerts) {
                parts.add("alerting");
            }
            final int alarms = this.alarms(binding);
            if (alarms > 0) {
                parts.add(alarms == 1 ? "1 alarm" : alarms + " alarms");
            }
            if (this.isSpoken(binding)) {
                parts.add("announced");
            }
            return String.join(", ", parts);
        }
    }

    public static Index around(Level level, BlockPos origin) {
        if (level == null) {
            return Index.EMPTY;
        }
        final Set<String> spoken = new HashSet<>();
        final Map<String, Integer> alarmed = new HashMap<>();
        for (BlockEntity be : nearby(level, origin)) {
            if (be instanceof AnnunciatorBE device) {
                for (WatchRule rule : device.getRules()) {
                    if (rule.isLive()) {
                        alarmed.merge(identity(rule.getBinding()), 1, Integer::sum);
                    }
                }
            } else if (be instanceof AnnouncementComputerBE brain) {
                for (PaSubscription sub : brain.subscriptions().values()) {
                    if (sub.enabled()) {
                        final String id = identity(sub.binding());
                        if (id != null) {
                            spoken.add(id);
                        }
                    }
                }
            }
        }
        return new Index(spoken, alarmed);
    }

    public static Set<String> spokenChannelsOf(Level level, BlockPos panel) {
        final Set<String> out = new HashSet<>();
        if (level == null) {
            return out;
        }
        for (BlockEntity be : nearby(level, panel)) {
            if (!(be instanceof AnnouncementComputerBE brain)) {
                continue;
            }
            for (PaSubscription sub : brain.subscriptions().values()) {
                final PaBinding bound = sub.binding();
                if (sub.enabled() && bound.kind() == PaBinding.Kind.CHANNEL
                        && bound.pos().equals(panel)) {
                    out.add(bound.metricId());
                }
            }
        }
        return out;
    }

    private static String identity(RuleBinding binding) {
        return binding.kind().name() + "|" + binding.target() + "|" + binding.key();
    }

    private static String identity(PaBinding binding) {
        return switch (binding.kind()) {
            case CHANNEL -> RuleBinding.Kind.PANEL_CHANNEL.name() + "|" + binding.pos() + "|"
                    + binding.metricId();
            case METRIC -> RuleBinding.Kind.MACHINE.name() + "|" + binding.pos() + "|"
                    + binding.metricId();
            case DEVICE -> null;
        };
    }

    private static List<BlockEntity> nearby(Level level, BlockPos origin) {
        return nearby(level, origin, RADIUS);
    }

    public static List<BlockEntity> nearby(Level level, BlockPos origin, int radius) {
        final List<BlockEntity> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        final int span = (radius >> 4) + 1;
        final int cx = SectionPos.blockToSectionCoord(origin.getX());
        final int cz = SectionPos.blockToSectionCoord(origin.getZ());
        final double maxDistSqr = (double) radius * radius;
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                final LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (origin.distSqr(be.getBlockPos()) <= maxDistSqr) {
                        out.add(be);
                    }
                }
            }
        }
        return out;
    }

    public static List<StatsDisplayPanelBE> panelsNear(Level level, BlockPos origin) {
        return panelsNear(level, origin, RADIUS);
    }

    public static List<StatsDisplayPanelBE> panelsNear(Level level, BlockPos origin, int radius) {
        final List<StatsDisplayPanelBE> out = new ArrayList<>();
        for (BlockEntity be : nearby(level, origin, radius)) {
            if (be instanceof StatsDisplayPanelBE panel && panel.isController()) {
                out.add(panel);
            }
        }
        return out;
    }
}
