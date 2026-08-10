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

/** [1] */
public final class PduTableModel {

    private PduTableModel() {
    }

    /** How the table is divided into sticky sections. */
    public enum Group {
        TIER, TYPE, STATUS
    }

    /** The column a group's rows are ordered by. */
    public enum Sort {
        DRAW, NAME, STATUS
    }

    /** [2] */
    public enum Status {
        SHED, HOLDING, RUNNING, OFF;

        public static Status of(BranchDevice d) {
            if (d.lock() == FeLock.FORCE_OFF) {
                return OFF;
            }
            if (d.reason() == PduReason.AWAITING_DWELL) {
                return HOLDING;
            }
            return d.shed() ? SHED : RUNNING;
        }

        public String label() {
            return switch (this) {
                case SHED -> "Shed";
                case HOLDING -> "Holding";
                case RUNNING -> "Running";
                case OFF -> "Off";
            };
        }
    }

    /** A row in the built list: either a group header or a device. */
    public sealed interface Row permits GroupRow, DeviceRow {
    }

    /** A sticky section header carrying its own tally, so the header can state served against total. */
    public record GroupRow(String label, int total, int served, int totalDraw) implements Row {
    }

    public record DeviceRow(BranchDevice device) implements Row {
    }

    /**
     * The live filter: a case insensitive name substring and the shed only toggle. Pure and public so
     * the toolbar's live search and its tests exercise exactly the same predicate.
     */
    public static boolean matches(BranchDevice d, String search, boolean shedOnly) {
        return matches(d, search, shedOnly, null);
    }

    /** [3] */
    public static boolean matches(BranchDevice d, String search, boolean shedOnly, FePriority tierFilter) {
        if (tierFilter != null && d.priority() != tierFilter) {
            return false;
        }
        if (shedOnly && !d.shed()) {
            return false;
        }
        if (search == null || search.isEmpty()) {
            return true;
        }
        // [4]
        return d.label().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    /**
     * Builds the grouped, sorted, filtered rows. Never mutates the input roster; groups are copied
     * into fresh lists before they are sorted.
     */
    public static List<Row> build(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                                  String search, boolean shedOnly) {
        return build(roster, group, sort, reversed, search, shedOnly, null);
    }

    public static List<Row> build(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                                  String search, boolean shedOnly, FePriority tierFilter) {
        final Map<String, List<BranchDevice>> groups = new LinkedHashMap<>();
        final Map<String, Integer> rankByLabel = new LinkedHashMap<>();
        for (BranchDevice d : roster) {
            if (!matches(d, search, shedOnly, tierFilter)) {
                continue;
            }
            final String label = groupLabel(d, group);
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(d);
            rankByLabel.putIfAbsent(label, groupRank(d, group));
        }

        final List<String> order = new ArrayList<>(groups.keySet());
        if (group == Group.TYPE) {
            // A type has no natural rank, so its groups read alphabetically.
            order.sort(String.CASE_INSENSITIVE_ORDER);
        } else {
            // Tier and status carry a fixed rank; the name break only settles ties that cannot happen.
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
            for (BranchDevice d : devices) {
                total++;
                if (!d.shed()) {
                    served++;
                }
                totalDraw += d.draw();
            }
            out.add(new GroupRow(label, total, served, totalDraw));
            for (BranchDevice d : devices) {
                out.add(new DeviceRow(d));
            }
        }
        return out;
    }

    /** The number of device rows, for the keyboard cursor which skips headers. */
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
            case TIER -> d.priority().getDisplayName();
            case TYPE -> d.name();
            case STATUS -> Status.of(d).label();
        };
    }

    private static int groupRank(BranchDevice d, Group group) {
        return switch (group) {
            case TIER -> d.priority().ordinal();
            case TYPE -> 0;
            case STATUS -> Status.of(d).ordinal();
        };
    }

    /** [5] */
    static Comparator<BranchDevice> comparator(Sort sort) {
        return switch (sort) {
            case DRAW -> Comparator.comparingInt(BranchDevice::draw).reversed();
            case NAME -> Comparator.comparing(BranchDevice::name, String.CASE_INSENSITIVE_ORDER);
            case STATUS -> Comparator.comparingInt((BranchDevice d) -> Status.of(d).ordinal());
        };
    }
}
