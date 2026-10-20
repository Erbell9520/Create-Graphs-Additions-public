package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PduTableModel {
    private PduTableModel() {
    }

    public enum Group {
        TIER, TYPE, STATUS, LEG
    }

    public enum Sort {
        NAME, TIER, DRAW, STATUS, LEG;

        public String label() {
            return switch (this) {
                case NAME -> "Device";
                case TIER -> "Tier";
                case DRAW -> "Draw";
                case STATUS -> "State";
                case LEG -> "Leg";
            };
        }
    }

    public enum Status {
        SHED, BREAKER, HOLDING, RUNNING, OFF, STORAGE;

        public static Status of(BranchDevice d) {
            if (d.isStorage()) {
                return STORAGE;
            }
            if (d.lock() == FeLock.FORCE_OFF) {
                return OFF;
            }
            if (d.reason() == PduReason.BREAKER_OPEN) {
                return BREAKER;
            }
            if (d.reason() == PduReason.AWAITING_DWELL) {
                return HOLDING;
            }
            return d.shed() ? SHED : RUNNING;
        }

        public String label() {
            return switch (this) {
                case SHED -> "Shed";
                case BREAKER -> "Breaker open";
                case HOLDING -> "Holding";
                case RUNNING -> "Running";
                case OFF -> "Off";
                case STORAGE -> "Storage";
            };
        }

        public boolean isDark() {
            return this == SHED || this == BREAKER || this == OFF;
        }
    }

    public record Filter(String search, boolean shedOnly, FePriority tier, int leg) {
        public static final int ANY_LEG = -1;
        public static final Filter NONE = new Filter("", false, null, ANY_LEG);

        public Filter {
            search = search == null ? "" : search;
        }

        public Filter withSearch(String s) {
            return new Filter(s, shedOnly, tier, leg);
        }

        public Filter withShedOnly(boolean b) {
            return new Filter(search, b, tier, leg);
        }

        public Filter withTier(FePriority t) {
            return new Filter(search, shedOnly, t, leg);
        }

        public Filter withLeg(int l) {
            return new Filter(search, shedOnly, tier, l);
        }

        public boolean isEmpty() {
            return search.isEmpty() && !shedOnly && tier == null && leg == ANY_LEG;
        }
    }

    public sealed interface Row permits GroupRow, DeviceRow {
    }

    public record GroupRow(String label, int total, int served, int totalDraw,
                           boolean storage, int stored, int capacity) implements Row {
        public GroupRow(String label, int total, int served, int totalDraw) {
            this(label, total, served, totalDraw, false, 0, 0);
        }

        public float fill() {
            return capacity <= 0 ? 0.0f : Math.min(1.0f, stored / (float) capacity);
        }
    }

    public record DeviceRow(BranchDevice device) implements Row {
    }

    public static boolean matches(BranchDevice d, String search, boolean shedOnly) {
        return matches(d, new Filter(search, shedOnly, null, Filter.ANY_LEG));
    }

    public static boolean matches(BranchDevice d, String search, boolean shedOnly, FePriority tierFilter) {
        return matches(d, new Filter(search, shedOnly, tierFilter, Filter.ANY_LEG));
    }

    public static boolean matches(BranchDevice d, Filter filter) {
        if (filter.leg() != Filter.ANY_LEG && d.leg() != filter.leg()) {
            return false;
        }

        if (d.isStorage()) {
            if (filter.tier() != null || filter.shedOnly()) {
                return false;
            }
            return matchesSearch(d, filter.search());
        }
        if (filter.tier() != null && d.priority() != filter.tier()) {
            return false;
        }

        if (filter.shedOnly() && !d.shed()) {
            return false;
        }
        return matchesSearch(d, filter.search());
    }

    private static boolean matchesSearch(BranchDevice d, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }

        return d.label().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    public static List<Row> build(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                                  String search, boolean shedOnly) {
        return build(roster, group, sort, reversed,
                new Filter(search, shedOnly, null, Filter.ANY_LEG));
    }

    public static List<Row> build(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                                  String search, boolean shedOnly, FePriority tierFilter) {
        return build(roster, group, sort, reversed,
                new Filter(search, shedOnly, tierFilter, Filter.ANY_LEG));
    }

    public static List<Row> build(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                                  Filter filter) {
        final Map<String, List<BranchDevice>> groups = new LinkedHashMap<>();
        final Map<String, Integer> rankByLabel = new LinkedHashMap<>();
        for (BranchDevice d : roster) {
            if (!matches(d, filter)) {
                continue;
            }
            final String label = groupLabel(d, group);
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(d);
            rankByLabel.putIfAbsent(label, groupRank(d, group));
        }

        final List<String> order = new ArrayList<>(groups.keySet());
        if (group == Group.TYPE) {
            order.sort(String.CASE_INSENSITIVE_ORDER);
        } else {
            order.sort(Comparator.<String>comparingInt(label -> rankByLabel.get(label))
                    .thenComparing(String.CASE_INSENSITIVE_ORDER));
        }

        Comparator<BranchDevice> within = comparator(sort);
        if (reversed) {
            within = within.reversed();
        }

        final List<Row> out = new ArrayList<>();
        for (String label : order) {
            final List<BranchDevice> devices = groups.get(label);
            devices.sort(within);
            int total = 0;
            int served = 0;
            int totalDraw = 0;
            int stored = 0;
            int capacity = 0;
            boolean storage = false;
            for (BranchDevice d : devices) {
                total++;
                totalDraw += d.draw();
                if (d.isStorage()) {
                    storage = true;
                    stored += d.stored();
                    capacity += d.capacity();
                    continue;
                }

                if (!d.isDark()) {
                    served++;
                }
            }
            out.add(storage
                    ? new GroupRow(label, total, total, totalDraw, true, stored, capacity)
                    : new GroupRow(label, total, served, totalDraw));
            for (BranchDevice d : devices) {
                out.add(new DeviceRow(d));
            }
        }
        return out;
    }

    public static int deviceCount(List<Row> rows) {
        int n = 0;
        for (Row r : rows) {
            if (r instanceof DeviceRow) {
                n++;
            }
        }
        return n;
    }

    private static String groupLabel(BranchDevice d, Group group) {
        return switch (group) {
            case TIER -> d.isStorage() ? "Storage" : d.priority().getDisplayName();
            case TYPE -> d.name();
            case STATUS -> Status.of(d).label();

            case LEG -> PduLegRow.name(d.leg());
        };
    }

    private static int groupRank(BranchDevice d, Group group) {
        return switch (group) {
            case TIER -> d.isStorage() ? FePriority.values().length : d.priority().ordinal();
            case TYPE -> 0;
            case STATUS -> Status.of(d).ordinal();
            case LEG -> d.leg();
        };
    }

    static Comparator<BranchDevice> comparator(Sort sort) {
        return switch (sort) {
            case DRAW -> Comparator.comparingInt(BranchDevice::draw).reversed();
            case NAME -> Comparator.comparing(BranchDevice::name, String.CASE_INSENSITIVE_ORDER);
            case STATUS -> Comparator.comparingInt((BranchDevice d) -> Status.of(d).ordinal());

            case TIER -> Comparator.comparingInt((BranchDevice d) ->
                    d.isStorage() ? FePriority.values().length : d.priority().ordinal());
            case LEG -> Comparator.comparingInt(BranchDevice::leg);
        };
    }
}
