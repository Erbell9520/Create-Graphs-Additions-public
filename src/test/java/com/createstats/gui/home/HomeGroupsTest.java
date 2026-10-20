package com.createstats.gui.home;

import com.createstats.network.SmartHomeSyncPacket;
import com.createstats.power.HomeAppliance;
import com.createstats.power.HomeSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeGroupsTest {
    private static int n;

    private static SmartHomeSyncPacket.Row row(HomeAppliance.Category kind, int roomId) {
        n++;
        return new SmartHomeSyncPacket.Row(List.of(new BlockPos(n, 64, 0)), kind.ordinal(), Component.literal("r" + n),
                false, 0, 0, List.of(), 0, List.of(), 63, -1, null, -1f, false, false, roomId);
    }

    @Test
    @DisplayName("no rooms: rows group by kind, lighting first, and an empty kind has no header")
    void byKindWithoutRooms() {
        final SmartHomeSyncPacket.Row oven = row(HomeAppliance.Category.KITCHEN, -1);
        final SmartHomeSyncPacket.Row lamp = row(HomeAppliance.Category.LIGHTING, -1);
        final SmartHomeSyncPacket.Row lamp2 = row(HomeAppliance.Category.LIGHTING, -1);
        final List<HomeGroups.Group> g = HomeGroups.of(List.of(oven, lamp, lamp2), List.of());
        assertEquals(2, g.size());
        assertEquals(HomeGroups.Kind.KIND, g.get(0).kind());
        assertEquals(HomeAppliance.Category.LIGHTING.ordinal(), g.get(0).id());
        assertEquals(List.of(lamp, lamp2), g.get(0).rows(), "the server's order is kept inside a group");
        assertEquals(List.of(oven), g.get(1).rows());
    }

    @Test
    @DisplayName("rooms: in the order given, a room with nothing in it has no header, the rest is Elsewhere, last")
    void byRoom() {
        final SmartHomeSyncPacket.Row a = row(HomeAppliance.Category.LIGHTING, 4);
        final SmartHomeSyncPacket.Row b = row(HomeAppliance.Category.KITCHEN, 2);
        final SmartHomeSyncPacket.Row c = row(HomeAppliance.Category.LIGHTING, -1);
        final SmartHomeSyncPacket.Row d = row(HomeAppliance.Category.LIGHTING, 9);
        final List<HomeGroups.Group> g = HomeGroups.of(List.of(a, b, c, d), List.of(2, 7, 4));
        assertEquals(3, g.size());
        assertEquals(List.of(b), g.get(0).rows());
        assertEquals(List.of(a), g.get(1).rows());
        assertEquals(HomeGroups.Kind.ELSEWHERE, g.get(2).kind());
        assertEquals(List.of(c, d), g.get(2).rows(), "a room the list does not know is not a lost row");
    }

    private static SmartHomeSyncPacket.Row made(HomeAppliance.Category kind, int roomId, int state, boolean running,
                                                 int draw) {
        n++;
        return new SmartHomeSyncPacket.Row(List.of(new BlockPos(n, 64, 0)), kind.ordinal(), Component.literal("r" + n),
                false, state, 0, List.of(), draw, List.of(), 63, -1, null, running ? 0.5f : -1f, running, false, roomId);
    }

    @Test
    @DisplayName("Now comes first: what is running, and what is switched on with no power or shed")
    void nowFirst() {
        final SmartHomeSyncPacket.Row oven = made(HomeAppliance.Category.KITCHEN, 1, 0, true, 96);
        final SmartHomeSyncPacket.Row lamp = made(HomeAppliance.Category.LIGHTING, 1, 0, false, 1);
        final SmartHomeSyncPacket.Row starved = made(HomeAppliance.Category.LIGHTING, 1,
                HomeSegment.State.NO_POWER.ordinal(), false, 0);
        final SmartHomeSyncPacket.Row off = made(HomeAppliance.Category.LIGHTING, 1, HomeSegment.State.OFF.ordinal(),
                false, 0);
        final List<HomeGroups.Group> g = HomeGroups.of(List.of(lamp, oven, starved, off), List.of(1));
        assertEquals(HomeGroups.Kind.NOW, g.get(0).kind());
        assertEquals(List.of(oven, starved), g.get(0).rows(), "switched off on purpose is not news");
        assertEquals(List.of(lamp, oven, starved, off), g.get(1).rows(), "and every row is still in its room");
        assertEquals(2, g.size());
    }

    @Test
    @DisplayName("a linked fixture out of range is listed in its group, never in Now, and never pushes the oven off")
    void outOfRangeIsNotNews() {
        final List<SmartHomeSyncPacket.Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < HomeGroups.NOW_MAX; i++) {
            rows.add(made(HomeAppliance.Category.OTHER, -1, HomeSegment.State.MISSING.ordinal(), false, 0));
        }
        final SmartHomeSyncPacket.Row oven = made(HomeAppliance.Category.KITCHEN, -1, 0, true, 96);
        rows.add(oven);
        final List<HomeGroups.Group> g = HomeGroups.of(rows, List.of());
        assertEquals(HomeGroups.Kind.NOW, g.get(0).kind());
        assertEquals(List.of(oven), g.get(0).rows(), "four far links held all of Now's places before the oven");
        assertEquals(rows.subList(0, HomeGroups.NOW_MAX), g.get(2).rows(), "and they are still listed, under Other");
    }

    @Test
    @DisplayName("nothing running and nothing wrong: no Now at all")
    void noNowWhenQuiet() {
        final List<HomeGroups.Group> g = HomeGroups.of(List.of(row(HomeAppliance.Category.LIGHTING, -1)), List.of());
        assertEquals(1, g.size());
        assertEquals(HomeGroups.Kind.KIND, g.get(0).kind());
    }

    @Test
    @DisplayName("the Power layer lists heaviest first, groups and rows alike; Now stays on top")
    void byDraw() {
        final SmartHomeSyncPacket.Row lamp = made(HomeAppliance.Category.LIGHTING, 1, 0, false, 1);
        final SmartHomeSyncPacket.Row cooler = made(HomeAppliance.Category.KITCHEN, 1, 0, false, 4);
        final SmartHomeSyncPacket.Row washer = made(HomeAppliance.Category.LAUNDRY, 2, 0, true, 46);
        final List<HomeGroups.Group> g = HomeGroups.of(List.of(lamp, cooler, washer), List.of(1, 2), true);
        assertEquals(HomeGroups.Kind.NOW, g.get(0).kind());
        assertEquals(2, g.get(1).id(), "the laundry draws 46, the other room 5");
        assertEquals(List.of(cooler, lamp), g.get(2).rows());
    }

    @Test
    @DisplayName("every row lands in exactly one group")
    void nothingLostNothingTwice() {
        final List<SmartHomeSyncPacket.Row> rows = List.of(row(HomeAppliance.Category.SOCKETS, 1),
                row(HomeAppliance.Category.OTHER, -1), row(HomeAppliance.Category.LAUNDRY, 1),
                row(HomeAppliance.Category.LIGHTING, 3));
        for (List<Integer> rooms : List.of(List.<Integer>of(), List.of(1), List.of(3, 1))) {
            int total = 0;
            for (HomeGroups.Group grp : HomeGroups.of(rows, rooms)) {
                if (grp.kind() != HomeGroups.Kind.NOW) {
                    total += grp.rows().size();
                }
            }
            assertEquals(rows.size(), total, "rooms " + rooms);
        }
    }
}
