package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TravelNbtTest {
    @Test void sequentialConditionStartsAndLatchesRoundTrip() {
        var saved = new StopRules.Progress.Saved(100, new int[]{1, 2}, new long[]{180, 200});
        var restored = TravelNbt.progress(TravelNbt.progress(saved));
        assertEquals(100, restored.arrived()); assertArrayEquals(saved.current(), restored.current());
        assertArrayEquals(saved.since(), restored.since());
        int[] escaped = restored.current(); escaped[0] = 99; assertEquals(1, restored.current()[0]);
    }

    @Test void scheduleReturnsMidWaitWithoutResendingOrRestartingItsTimer() {
        RouteSchedule sch = new RouteSchedule(List.of(new RouteSchedule.Entry(new RouteSchedule.TravelTo("Quarry"),
                List.of(List.of(new StopCondition.Wait(10))), List.of())), false);
        ScheduleRun run = new ScheduleRun(sch);
        ScheduleRun.Host host = new ScheduleRun.Host() {
            public int pick(String glob) { return 2; } public int waitingAt() { return 2; }
            public String name(int node) { return "Quarry"; }
        };
        run.tick(100, host, StopRules.EMPTY); run.tick(180, host, StopRules.EMPTY);
        ScheduleRun restored = new ScheduleRun(sch); restored.restore(TravelNbt.schedule(TravelNbt.schedule(run.save())));
        assertEquals(ScheduleRun.State.POST_TRANSIT, restored.state());
        assertEquals(-1, restored.tick(299, host, StopRules.EMPTY).sendTo());
        assertEquals(0, restored.entry()); restored.tick(300, host, StopRules.EMPTY);
        assertEquals(1, restored.entry()); restored.tick(301, host, StopRules.EMPTY);
        assertTrue(restored.completed());
    }

    @Test void runnerRuntimeAndJunctionRouteRoundTrip() {
        var runtime = new RouteRunner.Travel(RouteRunner.State.APPROACHING, 12, 73, 20, 30, 14,
                7, 8, 9, 10, 2, 30, 42, .5, "step");
        assertEquals(runtime, TravelNbt.runtime(TravelNbt.runtime(runtime)));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var route = new PathNetwork.Route(List.of(new PathNetwork.Leg(a, 0, 20), new PathNetwork.Leg(b, 4, 30)),
                List.of(new PathNetwork.Transfer(a, 20, b, 4, 25, 20, 0)), 12.5, b, 3);
        assertEquals(route, TravelNbt.network(TravelNbt.network(route)));
        assertNull(TravelNbt.network(new CompoundTag()));
    }

    @Test void frozenCargoAndUnknownLegacyReadingsRoundTrip() {
        var cargo = new CargoSnapshot(Map.of("minecraft:iron_ingot", 128L), Map.of("minecraft:iron_ingot", 2L),
                Map.of("minecraft:water", 2000L), 30, 400, true);
        var restored = TravelNbt.cargo(TravelNbt.cargo(cargo));
        restored.readings(500, 1000, false, (a, b) -> false);
        assertEquals(128, restored.items("", false)); assertEquals(2, restored.items("", true));
        assertEquals(2000, restored.fluidMb("")); assertEquals(130, restored.idleTicks());
        assertFalse(TravelNbt.cargo(new CompoundTag()).known());
    }

    private static CompoundTag body(CompoundTag... bes) {
        CompoundTag root = new CompoundTag(), plot = new CompoundTag(), chunks = new CompoundTag(), chunk = new CompoundTag();
        ListTag list = new ListTag(); for (var be : bes) list.add(be);
        chunk.put("block_entities", list); chunks.put("0,0", chunk); plot.put("chunks", chunks); root.put("plot", plot);
        return root;
    }

    @Test void returnUpdatesOnlyTheMatchingConsoleInTheParkedPlot() {
        UUID id = UUID.randomUUID(); CompoundTag a = new CompoundTag(), b = new CompoundTag();
        a.putUUID("ControllerId", UUID.randomUUID()); b.putUUID("ControllerId", id);
        CompoundTag root = body(a, b); var matched = RouteParking.consoleTag(root, id);
        assertSame(b, matched); matched.putString("ReturnMarker", "current state");
        assertFalse(a.contains("ReturnMarker")); assertEquals("current state", b.getString("ReturnMarker"));
        assertNull(RouteParking.consoleTag(root, UUID.randomUUID()));
    }

    @Test void savedDockPairsDistinguishTheWorldAndAJoinedBodyFromAnUnpairedConnector() {
        UUID trailer = UUID.randomUUID(); CompoundTag world = new CompoundTag(), internal = new CompoundTag(), free = new CompoundTag();
        for (var be : List.of(world, internal, free)) be.putString("id", "simulated:docking_connector");
        world.putIntArray("OtherConnector", new int[]{1, 2, 3});
        internal.putIntArray("OtherConnector", new int[]{10, 20, 30}); internal.putUUID("OtherConnectorSubLevelId", trailer);
        var ties = RouteParking.dockTies(body(world, internal, free));
        assertEquals(2, ties.size()); assertNull(ties.get(0)); assertEquals(trailer, ties.get(1));
    }

    @Test void springsAndBothBearingSidesDistinguishInternalExternalAndWorldAnchors() {
        UUID joined = UUID.randomUUID(), external = UUID.randomUUID();
        CompoundTag world = new CompoundTag(), internal = new CompoundTag(), free = new CompoundTag();
        for (var be : List.of(world, internal, free)) be.putString("id", "simulated:spring");
        world.putLong("Goal", 12); internal.putLong("Goal", 34); internal.putUUID("GoalSubLevel", joined);
        CompoundTag bearing = new CompoundTag(), plate = new CompoundTag(), oldPlate = new CompoundTag();
        bearing.putString("id", "simulated:swivel_bearing"); bearing.putUUID("SubLevelID", external);
        plate.putString("id", "simulated:swivel_bearing_link_block"); plate.putIntArray("ParentPos", new int[]{1, 2, 3});
        oldPlate.putString("id", "simulated:swivel_bearing_link_block"); oldPlate.putIntArray("parent", new int[]{4, 5, 6});
        oldPlate.putUUID("ParentSubLevelId", joined);
        var ties = RouteParking.jointTies(body(world, internal, free, bearing, plate, oldPlate));
        assertEquals(5, ties.size()); assertNull(ties.get(0)); assertEquals(joined, ties.get(1));
        assertEquals(external, ties.get(2)); assertNull(ties.get(3)); assertEquals(joined, ties.get(4));
    }
}
