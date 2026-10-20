package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceNamerTest {
    private static PlaceNamer ship(List<PlaceNamer.Marker> markers, List<PlaceNamer.Part> props) {
        return new PlaceNamer(false, new ShipFrame(0), 0, 29, -4, 4, 0, markers, List.of(), props, c -> PackedPos.dy(c) >= 6);
    }

    private static int p(int x, int y, int z) {
        return PackedPos.pack(x, y, z);
    }

    private static final Map<String, String> EN = new HashMap<>();

    static {
        EN.put("place.fore.0", "Stern");
        EN.put("place.fore.1", "Midship");
        EN.put("place.fore.2", "Bow");
        EN.put("place.side.0", "port");
        EN.put("place.side.2", "stbd");
        EN.put("place.level.0", "below deck");
        EN.put("place.level.2", "upper works");
        EN.put("place.ns.0", "north");
        EN.put("place.ns.1", "centre");
        EN.put("place.ns.2", "south");
        EN.put("place.we.0", "west");
        EN.put("place.we.2", "east");
        EN.put("place.envelope.2", "Envelope, bow");
        EN.put("place.propeller", "P%s propeller");
        EN.put("place.gun", "G%s");
        EN.put("place.broke_away", "Broke away");
        EN.put("place.unnamed", "Unnamed");
    }

    private static String words(String token) {
        return PlaceName.describe(token, (k, a) -> String.format(EN.getOrDefault(k, "?" + k), a));
    }

    @Test
    void geometryByThirdsOfTheShip() {
        PlaceNamer n = ship(List.of(), List.of());
        assertEquals("Bow, port", words(n.name(new int[]{p(27, 0, -4), p(28, 0, -3)}, Places.KIND_DAMAGE)));
        assertEquals("Midship, below deck", words(n.name(new int[]{p(15, -3, 0)}, Places.KIND_DAMAGE)));
        assertEquals("Stern, stbd, upper works", words(n.name(new int[]{p(2, 3, 4)}, Places.KIND_DAMAGE)));
    }

    @Test
    void aMarkerCoveringTheCentroidWins() {
        PlaceNamer n = ship(List.of(new PlaceNamer.Marker("Engine room", 3, 0, 0, 4)), List.of());
        assertEquals("Engine room", words(n.name(new int[]{p(5, 0, 1), p(6, 0, 2)}, Places.KIND_DAMAGE)));
        assertEquals("Midship", words(n.name(new int[]{p(15, 0, 0)}, Places.KIND_DAMAGE)), "outside its cube");
    }

    @Test
    void propellerThenLiftThenGeometry() {
        PlaceNamer n = ship(List.of(), List.of(new PlaceNamer.Part(2, new int[]{p(0, 0, 3)})));
        assertEquals("P2 propeller", words(n.name(new int[]{p(0, 0, 3), p(1, 0, 3)}, Places.KIND_DAMAGE)));
        assertEquals("Envelope, bow", words(n.name(new int[]{p(26, 7, 0), p(27, 8, 0), p(27, 0, 0)}, Places.KIND_DAMAGE)),
                "two of three cells are lift");
    }

    @Test
    void groundUsesCompassThirds() {
        PlaceNamer n = new PlaceNamer(true, ShipFrame.ground(), -16, 16, -16, 16, 0, List.of(), List.of(), List.of(), c -> false);
        assertEquals("North, east", words(n.name(new int[]{p(14, 0, -15)}, Places.KIND_DAMAGE)));
        assertEquals("South", words(n.name(new int[]{p(0, 0, 15)}, Places.KIND_DAMAGE)));
        assertEquals("Centre", words(n.name(new int[]{p(0, 0, 0)}, Places.KIND_DAMAGE)));
    }

    @Test
    void brokeAwayIsItsOwnName() {
        assertEquals("Broke away", words(ship(List.of(), List.of()).name(new int[]{p(1, 1, 1)}, Places.KIND_BROKE_AWAY)));
        assertEquals("Unnamed", words(""));
        assertEquals("Unnamed", words("S:x"));
    }
}
