package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoomNamingTest {
    private static String name(boolean kitchen, boolean laundry, int beds, int books, int workshop, int baths,
                               int storage, int seats, int rugs, boolean brewing, int w, int d) {
        return name(kitchen, laundry, beds, books, workshop, baths, storage, seats, rugs, brewing, 0, w, d);
    }

    private static String name(boolean kitchen, boolean laundry, int beds, int books, int workshop, int baths,
                               int storage, int seats, int rugs, boolean brewing, int exits, int w, int d) {
        final RoomNaming.Kind k = RoomNaming.kind(kitchen, laundry, beds, books, workshop, baths, storage, seats, rugs,
                brewing, exits, w, d);
        return k == null ? null : k.name();
    }

    @Test
    @DisplayName("the room the front door opens into is the entrance, whatever its shape, unless something tells more")
    void entrance() {
        assertEquals("Entrance", name(false, false, 0, 0, 0, 0, 0, 1, 0, false, 1, 4, 4));

        assertEquals("Entrance", name(false, false, 0, 0, 0, 0, 0, 0, 0, false, 1, 20, 2));

        assertEquals("Kitchen", name(true, false, 0, 0, 0, 0, 0, 0, 0, false, 1, 6, 6));
    }

    @Test
    @DisplayName("the test house: a store room full of shelves and tools, a living room with two shelves, a study")
    void theTestHouse() {
        assertEquals("Study", name(false, false, 0, 5, 3, 0, 2, 0, 0, false, 6, 6), "five shelves beat three tools");

        assertEquals("Living room", name(false, false, 0, 3, 0, 0, 0, 1, 15, false, 9, 4));

        assertEquals("Living room", name(false, false, 0, 4, 0, 0, 0, 1, 3, false, 5, 4),
                "a seat on a rug is a living room before it is a study");

        assertEquals("Library", name(false, false, 0, 16, 0, 0, 0, 0, 0, false, 20, 4));

        assertEquals("Bathroom", name(false, false, 0, 0, 0, 2, 0, 0, 0, false, 6, 6));

        assertEquals("Workshop", name(false, false, 0, 1, 2, 1, 0, 0, 0, false, 6, 6));

        assertEquals("Hall", name(false, false, 0, 0, 0, 0, 0, 0, 0, false, 20, 2));

        assertNull(name(false, false, 0, 0, 0, 0, 0, 1, 0, false, 4, 4));
    }

    @Test
    @DisplayName("what is most telling wins: a washer over a bed, a bed over books, a bench over one shelf")
    void precedence() {
        assertEquals("Laundry", name(true, true, 1, 9, 9, 1, 9, 1, 1, true, 4, 4));
        assertEquals("Kitchen", name(true, false, 1, 9, 9, 1, 9, 1, 1, true, 4, 4));
        assertEquals("Bedroom", name(false, false, 1, 9, 9, 1, 9, 1, 1, true, 4, 4));
        assertEquals("Brewery", name(false, false, 0, 9, 9, 1, 9, 1, 1, true, 4, 4));
        assertEquals("Workshop", name(false, false, 0, 1, 1, 0, 0, 0, 0, false, 4, 4), "a bench beside one shelf");
        assertEquals("Study", name(false, false, 0, 2, 1, 0, 0, 0, 0, false, 4, 4), "two shelves beside a bench");
        assertEquals("Storage", name(false, false, 0, 0, 0, 0, 4, 0, 0, false, 4, 4));
        assertNull(name(false, false, 0, 0, 0, 0, 3, 0, 0, false, 4, 4), "three chests are not yet storage");
    }

    @Test
    @DisplayName("a second study is the reading room, a second hall the landing; then the first free number, never the next after the last")
    void uniqueNames() {
        assertEquals("Study", RoomNaming.unique("Study", List.of("Kitchen")));
        assertEquals("Reading room", RoomNaming.unique("Study", List.of("Study")));
        assertEquals("Den", RoomNaming.unique("Study", List.of("Study", "Reading room")));
        assertEquals("Study 2", RoomNaming.unique("Study", List.of("Study", "Reading room", "Den")));
        assertEquals("Study 2", RoomNaming.unique("Study", List.of("Study", "Reading room", "Den", "Study 3")));
        assertEquals("Study 4", RoomNaming.unique("Study", List.of("Study", "Reading room", "Den", "Study 2", "Study 3")));
        assertEquals("Landing", RoomNaming.unique("Hall", List.of("Hall")));
        assertEquals("Bedroom 2", RoomNaming.unique("Bedroom", List.of("Bedroom")), "bedrooms count");

        assertEquals("Library", RoomNaming.unique("Library", List.of("Study", "Reading room")));
    }
}
