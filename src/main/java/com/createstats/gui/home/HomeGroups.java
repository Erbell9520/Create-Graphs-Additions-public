package com.createstats.gui.home;

import com.createstats.network.SmartHomeSyncPacket;
import com.createstats.power.HomeAppliance;
import com.createstats.power.HomeSegment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HomeGroups {
    private HomeGroups() {
    }

    public enum Kind {
        NOW,

        ROOM,

        KIND,

        ELSEWHERE
    }

    public record Group(Kind kind, int id, List<SmartHomeSyncPacket.Row> rows) {
        public long key() {
            return ((long) kind.ordinal() << 32) | (id & 0xFFFFFFFFL);
        }
    }

    public static final List<HomeAppliance.Category> KIND_ORDER = List.of(HomeAppliance.Category.LIGHTING,
            HomeAppliance.Category.KITCHEN, HomeAppliance.Category.LAUNDRY, HomeAppliance.Category.SOCKETS,
            HomeAppliance.Category.OTHER);

    public static final int NOW_MAX = 4;

    public static List<Group> of(List<SmartHomeSyncPacket.Row> rows, List<Integer> roomOrder) {
        return of(rows, roomOrder, false);
    }

    public static List<Group> of(List<SmartHomeSyncPacket.Row> rows, List<Integer> roomOrder, boolean byDraw) {
        final List<Group> out = new ArrayList<>();
        final List<SmartHomeSyncPacket.Row> now = new ArrayList<>();
        for (SmartHomeSyncPacket.Row r : rows) {
            if (now.size() < NOW_MAX && needsLooking(r)) {
                now.add(r);
            }
            for (SmartHomeSyncPacket.Row c : r.plugged()) {
                if (now.size() < NOW_MAX && needsLooking(c)) {
                    now.add(c);
                }
            }
        }
        if (!now.isEmpty()) {
            out.add(new Group(Kind.NOW, 0, now));
        }
        final List<Group> rest = byRoomOrKind(rows, roomOrder);
        if (byDraw) {
            final List<Group> sorted = new ArrayList<>();
            for (Group g : rest) {
                final List<SmartHomeSyncPacket.Row> in = new ArrayList<>(g.rows());
                in.sort(java.util.Comparator.comparingInt((SmartHomeSyncPacket.Row r) -> -drawOf(r)));
                sorted.add(new Group(g.kind(), g.id(), in));
            }
            sorted.sort(java.util.Comparator.comparingInt((Group g) -> -drawOf(g.rows())));
            out.addAll(sorted);
        } else {
            out.addAll(rest);
        }
        return out;
    }

    public static boolean needsLooking(SmartHomeSyncPacket.Row r) {
        final HomeSegment.State s = HomeSegment.State.values()[Math.max(0,
                Math.min(HomeSegment.State.values().length - 1, r.state()))];
        return r.running() || s == HomeSegment.State.NO_POWER || s == HomeSegment.State.SHED;
    }

    public static int drawOf(SmartHomeSyncPacket.Row r) {
        long sum = Math.max(0, r.drawFe());
        for (SmartHomeSyncPacket.Row c : r.plugged()) {
            sum += Math.max(0, c.drawFe());
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private static int drawOf(List<SmartHomeSyncPacket.Row> rows) {
        long sum = 0;
        for (SmartHomeSyncPacket.Row r : rows) {
            sum += drawOf(r);
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private static List<Group> byRoomOrKind(List<SmartHomeSyncPacket.Row> rows, List<Integer> roomOrder) {
        final List<Group> out = new ArrayList<>();
        if (roomOrder.isEmpty()) {
            for (HomeAppliance.Category kind : KIND_ORDER) {
                final List<SmartHomeSyncPacket.Row> in = new ArrayList<>();
                for (SmartHomeSyncPacket.Row r : rows) {
                    if (HomeAppliance.Category.byOrdinal(r.category()) == kind) {
                        in.add(r);
                    }
                }
                if (!in.isEmpty()) {
                    out.add(new Group(Kind.KIND, kind.ordinal(), in));
                }
            }
            return out;
        }
        final Set<Integer> listed = new HashSet<>(roomOrder);
        for (int id : roomOrder) {
            final List<SmartHomeSyncPacket.Row> in = new ArrayList<>();
            for (SmartHomeSyncPacket.Row r : rows) {
                if (r.roomId() == id) {
                    in.add(r);
                }
            }
            if (!in.isEmpty()) {
                out.add(new Group(Kind.ROOM, id, in));
            }
        }
        final List<SmartHomeSyncPacket.Row> rest = new ArrayList<>();
        for (SmartHomeSyncPacket.Row r : rows) {
            if (!listed.contains(r.roomId())) {
                rest.add(r);
            }
        }
        if (!rest.isEmpty()) {
            out.add(new Group(Kind.ELSEWHERE, -1, rest));
        }
        return out;
    }
}
