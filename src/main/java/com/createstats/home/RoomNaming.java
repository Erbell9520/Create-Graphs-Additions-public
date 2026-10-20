package com.createstats.home;

public final class RoomNaming {
    private RoomNaming() {
    }

    public static final int LIBRARY_SHELVES = 6;

    public static final int STORAGE_CHESTS = 4;

    public record Kind(String name, String icon) {
    }

    public static Kind kind(boolean kitchen, boolean laundry, int beds, int books, int workshop, int baths, int storage,
                            int seats, int rugs, boolean brewing, int exits, int w, int d) {
        if (laundry) {
            return new Kind("Laundry", "minecraft:water_bucket");
        }
        if (kitchen) {
            return new Kind("Kitchen", "minecraft:cake");
        }
        if (beds > 0) {
            return new Kind("Bedroom", "minecraft:red_bed");
        }
        if (brewing) {
            return new Kind("Brewery", "minecraft:brewing_stand");
        }
        if (baths > 0 && books == 0 && workshop == 0) {
            return new Kind("Bathroom", "minecraft:cauldron");
        }
        if (workshop > 0 && workshop >= books) {
            return new Kind("Workshop", "minecraft:crafting_table");
        }
        if (books >= LIBRARY_SHELVES) {
            return new Kind("Library", "minecraft:bookshelf");
        }
        if (seats > 0 && rugs > 0) {
            return new Kind("Living room", "minecraft:painting");
        }
        if (books > 0) {
            return new Kind("Study", "minecraft:bookshelf");
        }
        if (storage >= STORAGE_CHESTS) {
            return new Kind("Storage", "minecraft:chest");
        }

        if (exits > 0) {
            return new Kind("Entrance", "minecraft:oak_door");
        }

        if (Math.min(w, d) <= 2 && Math.max(w, d) >= 3 * Math.min(w, d)) {
            return new Kind("Hall", "minecraft:oak_door");
        }
        return null;
    }

    private static final java.util.Map<String, String[]> ALTERNATES = java.util.Map.of(
            "Study", new String[] {"Reading room", "Den"},
            "Hall", new String[] {"Landing", "Corridor", "Passage"},
            "Living room", new String[] {"Lounge", "Sitting room"},
            "Storage", new String[] {"Store room", "Pantry"},
            "Bathroom", new String[] {"Washroom"},
            "Workshop", new String[] {"Workroom"},
            "Entrance", new String[] {"Porch", "Lobby"},
            "Kitchen", new String[] {"Scullery"});

    public static String unique(String base, java.util.Collection<String> taken) {
        if (!taken.contains(base)) {
            return base;
        }
        for (String other : ALTERNATES.getOrDefault(base, new String[0])) {
            if (!taken.contains(other)) {
                return other;
            }
        }
        for (int n = 2; ; n++) {
            final String candidate = base + " " + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }
}
