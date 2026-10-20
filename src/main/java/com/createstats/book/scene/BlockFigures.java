package com.createstats.book.scene;

import com.createstats.book.scene.BlockFigure.Pin;
import com.createstats.book.scene.BlockFigure.Placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BlockFigures {
    private BlockFigures() {
    }

    private static final double[] UP = {0, 1, 0};
    private static final double[] SOUTH = {0, 0, 1};
    private static final double[] EAST = {1, 0, 0};

    private static final double[] CELL = {0, 0, 0, 16, 16, 16};

    public static final BlockFigure KINETIC_DYNAMO = new BlockFigure("kinetic_dynamo", 128, 158, 5, 315.0, 30.0,
            List.of(
                    new Placement("create:shaft", Map.of("axis", "y"), 0, -1, 0, new double[]{6, 9, 6, 10, 16, 10},
                            false),

                    new Placement("create_stats:kinetic_dynamo", Map.of(), 0, 0, 0,
                            new double[]{2, -5, 2, 16, 15, 14}, true)),
            List.of(

                    new Pin("shaft", 8, -5, 10, SOUTH, new double[]{6, -7, 6, 10, -1, 10}),

                    new Pin("fan", 8, 15, 8, UP, new double[]{3.5, 14, 3.5, 12.5, 15, 12.5}),

                    new Pin("terminal", 15, 6.5, 8, EAST, new double[]{13, 4, 6, 16, 9, 10}),

                    new Pin("casing", 8, 7, 14, SOUTH, new double[]{3, 2, 12, 13, 12, 14})));

    private static final double BURNER_GAP = 1.25;

    public static final BlockFigure BIOFUEL_BURNER = new BlockFigure("biofuel_burner", 200, 112, 5, 315.0, 30.0,
            List.of(
                    new Placement("create_stats:biofuel_burner", Map.of("facing", "south", "lit", "true"), 0, 0, 0,
                            CELL, false),
                    new Placement("create_stats:biofuel_burner", Map.of("facing", "north", "lit", "false"),
                            BURNER_GAP, 0, -BURNER_GAP, CELL, false)),
            burnerPins());

    private static List<Pin> burnerPins() {
        final double bx = BURNER_GAP * 16;
        final double bz = -BURNER_GAP * 16;
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("door", 8, 6.8, 15.05, SOUTH, new double[]{3, 2.6, 14.45, 13, 11.5, 14.95}));

        out.add(new Pin("mouth", 8, 14.5, 8, UP, new double[]{4.8, 14, 4.8, 11.2, 14.5, 11.2}));

        out.add(new Pin("studs", bx + 5.3, 7.3, bz + 15.74, SOUTH,
                new double[]{bx + 4.2, 6.2, bz + 15.5, bx + 11.8, 8.4, bz + 15.74}));

        out.add(new Pin("flank", bx + 15.72, 7.2, bz + 9, EAST,
                new double[]{bx + 15.48, 3.6, bz + 3.4, bx + 15.72, 10.8, bz + 14.8}));
        return out;
    }

    private static final double[] LONE = {3.9, 0, -0.6};

    // edge flags set like updateShape so the frame only wraps the outside
    public static final BlockFigure SOLAR_PANEL = new BlockFigure("solar_panel", 230, 116, 5, 315.0, 38.0,
            solarPlacements(), solarPins());

    private static List<Placement> solarPlacements() {
        final double[] slab = {0, 0, 0, 16, com.createstats.block.SolarPanelBlock.HEIGHT, 16};
        final List<Placement> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out.add(new Placement("create_stats:reinforced_solar_panel", Map.of(
                        "north", String.valueOf(j > 0), "south", String.valueOf(j < 2),
                        "west", String.valueOf(i > 0), "east", String.valueOf(i < 2)), i, 0, j, slab, false));
            }
        }
        out.add(new Placement("create_stats:reinforced_solar_panel", Map.of(
                "north", "false", "south", "false", "west", "false", "east", "false"), LONE[0], LONE[1], LONE[2],
                slab, false));
        return out;
    }

    private static List<Pin> solarPins() {
        final double lx = LONE[0] * 16;
        final double lz = LONE[2] * 16;
        final double top = 2.6;
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("cells", 24, top, 24, UP, new double[]{18, top, 18, 30, top, 30}));

        out.add(new Pin("rail", 0.7, 3.4, 24, UP, new double[]{0, top, 16, 1.4, 3.4, 32}));

        out.add(new Pin("lone", lx + 8, top, lz + 8, UP, new double[]{lx, 0, lz, lx + 16, 3.4, lz + 16}));

        out.add(new Pin("edge", 24, 0.75, 48, SOUTH, new double[]{16, 0, 47.5, 32, 1.5, 48}));
        return out;
    }

    private static final double GRADE_STEP = 1.3;

    public static final BlockFigure DYNAMO_GRADES = new BlockFigure("dynamo_grades", 220, 92, 4, 315.0, 30.0,
            List.of(
                    new Placement("create_stats:open_frame_dynamo", Map.of(), 0, 0, 0,
                            new double[]{1, 0, 1, 15, 15, 15}, true),
                    new Placement("create_stats:kinetic_dynamo", Map.of(), GRADE_STEP, 0, -GRADE_STEP,
                            new double[]{2, 0, 2, 16, 15, 14}, true),
                    new Placement("create_stats:industrial_alternator", Map.of(), 2 * GRADE_STEP, 0, -2 * GRADE_STEP,
                            new double[]{3, 0, 3, 16, 16, 13}, true)),
            List.of());

    private static final double CAPACITOR_GAP = 1.25;

    public static final BlockFigure STARTER_CAPACITOR = new BlockFigure("starter_capacitor", 200, 112, 5, 315.0, 30.0,
            List.of(

                    new Placement("create_stats:starter_capacitor", Map.of("facing", "south", "firing", "false"), 0, 0, 0,
                            new double[]{0, 0, 3, 16, 16, 14.5}, true),
                    new Placement("create_stats:starter_capacitor", Map.of("facing", "north", "firing", "false"),
                            CAPACITOR_GAP, 0, -CAPACITOR_GAP, new double[]{0, 0, 1.5, 16, 16, 13}, true)),
            capacitorPins());

    private static List<Pin> capacitorPins() {
        final double bx = CAPACITOR_GAP * 16;
        final double bz = -CAPACITOR_GAP * 16;
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("mode", 12, 3.25, 14.5, SOUTH, new double[]{10.5, 2, 14, 13.5, 4.5, 14.5}));

        out.add(new Pin("lamp", 4, 3, 14.5, SOUTH, new double[]{3, 2, 14, 5, 4, 14.5}));

        out.add(new Pin("jars", 8, 10.5, 8, UP, new double[]{1.5, 5.5, 6, 14.5, 10.5, 10}));

        out.add(new Pin("gap", 11, 16, 8, UP, new double[]{9.5, 13, 6.5, 12.5, 16, 9.5}));

        out.add(new Pin("gland", 16, 5, 8, EAST, new double[]{15.5, 3.5, 6.5, 16, 6.5, 9.5}));

        out.add(new Pin("lug", bx + 16, 5, bz + 8, EAST, new double[]{bx + 15.5, 3, bz + 6, bx + 16, 7, bz + 10}));
        return out;
    }

    public static final BlockFigure CAPACITOR_BANK = new BlockFigure("capacitor_bank", 170, 150, 6, 315.0, 30.0,
            bankPlacements(), bankPins());

    public static final int BANK_SIDE = 3;
    private static final int BANK = BANK_SIDE;

    private static List<Placement> bankPlacements() {
        final List<Placement> out = new ArrayList<>();
        for (int y = 0; y < BANK; y++) {
            final String segment = y == 0 ? "bottom" : y == BANK - 1 ? "top" : "middle";
            for (int x = 0; x < BANK; x++) {
                for (int z = 0; z < BANK; z++) {
                    final boolean corner = x == 0 && y == 0 && z == 0;
                    out.add(new Placement("create_stats:capacitor_bank", Map.of("segment", segment, "level", "0"),
                            x, y, z, CELL, corner, corner ? com.createstats.book.specimen.StoreFixtures.BANK_FACE : null));
                }
            }
        }
        return out;
    }

    private static List<Pin> bankPins() {
        final double edge = BANK * 16;
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("face", edge / 2, edge, edge / 2, UP, new double[]{0, edge, 0, edge, edge, edge}));

        out.add(new Pin("spine", 0.305 * 16, 1.5 * 16, edge, SOUTH,
                new double[]{0.155 * 16, 0.155 * 16, edge, 0.455 * 16, 2.845 * 16, edge}));
        out.add(new Pin("readout", 0.9 * 16, 1.45 * 16, edge, SOUTH,
                new double[]{0.495 * 16, 0.64 * 16, edge, 2.845 * 16, 1.69 * 16, edge}));
        out.add(new Pin("layers", 1.67 * 16, 0.4 * 16, edge, SOUTH,
                new double[]{0.495 * 16, 0.155 * 16, edge, 2.845 * 16, 0.64 * 16, edge}));
        out.add(new Pin("lamp", edge, 2.745 * 16, edge - 2.76 * 16, EAST,
                new double[]{edge, 2.71 * 16, edge - 2.795 * 16, edge, 2.78 * 16, edge - 2.725 * 16}));
        return out;
    }

    public static final BlockFigure BATTERY_CONTROLLER = new BlockFigure("fe_battery_controller", 150, 122, 6, 330.0,
            24.0, List.of(new Placement("create_stats:fe_battery_controller", Map.of("facing", "south"), 0, 0, 0,
                    new double[]{0, 0, 0, 16, 18, 16}, true,
                    com.createstats.book.specimen.StoreFixtures.CONTROLLER_FACE)),
            List.of(

                    new Pin("inputs", 12.5, 18, 8, UP, new double[]{11.75, 16.5, 2.75, 13.25, 18, 13.25}),

                    new Pin("outputs", 3.5, 18, 8, UP, new double[]{2.75, 16.5, 2.75, 4.25, 18, 13.25}),

                    new Pin("screen", 8, 8.5, 12.9, SOUTH, new double[]{2, 4, 12.9, 14, 13, 12.9}),

                    new Pin("case", 16, 8, 6, EAST, new double[]{16, 0, 0, 16, 15, 16})));

    private static final double INTERFACE_GAP = 2.0;

    public static final BlockFigure PORTABLE_INTERFACE = new BlockFigure("portable_power_interface", 200, 112, 5, 315.0,
            30.0, List.of(
                    new Placement("create_stats:portable_power_interface", Map.of("facing", "east"), 0, 0, 0, CELL, true),
                    new Placement("create_stats:portable_power_interface", Map.of("facing", "west"), INTERFACE_GAP, 0, 0,
                            CELL, true)),
            List.of(

                    new Pin("head", 16, 8, 8, EAST, new double[]{14, 1, 1, 16, 15, 15}),

                    new Pin("floor", 6.5, 11.5, 16, SOUTH, new double[]{4, 9, 16, 9, 14, 16}),
                    new Pin("dial", 6.5, 4.5, 16, SOUTH, new double[]{4, 2, 16, 9, 7, 16}),

                    new Pin("body", 6, 16, 8, UP, new double[]{0, 16, 0, 14, 16, 16}),

                    new Pin("train", INTERFACE_GAP * 16 + 8, 16, 8, UP,
                            new double[]{INTERFACE_GAP * 16, 16, 0, INTERFACE_GAP * 16 + 16, 16, 16})));

    private static final double[] NORTH = {0, 0, -1};
    private static final String FIX_CABLE = com.createstats.book.specimen.CarryFixtures.CABLE;
    private static final String FIX_INDOOR = com.createstats.book.specimen.CarryFixtures.INDOOR;

    private static Placement cableOn(String block, String face, int x, int y, int z, double[] occupies) {
        return new Placement(block, Map.of(face, "true"), x, y, z, occupies, true, FIX_CABLE);
    }

    public static final BlockFigure CABLE_RUN = new BlockFigure("surface_cable", 220, 110, 5, 315.0, 30.0,
            List.of(

                    new Placement("create_stats:kinetic_dynamo", Map.of(), 0, 0, 1,
                            new double[]{2, -5, 2, 16, 15, 14}, true),

                    cableOn("create_stats:bus_duct", "down", 1, 0, 1, new double[]{0, 0, 4, 16, 3.75, 12}),
                    cableOn("create_stats:bus_duct", "down", 2, 0, 1, new double[]{0, 0, 4, 16, 3.5, 12}),

                    cableOn("create_stats:surface_cable", "down", 3, 0, 1, new double[]{0, 0, 6.6, 16, 2.45, 9.4}),
                    cableOn("create_stats:surface_cable", "down", 4, 0, 1, new double[]{0, 0, 0, 9, 2.1, 9}),
                    new Placement("create_stats:induction_smelter", Map.of("facing", "north"), 4, 0, 0, CELL, false)),
            List.of(

                    new Pin("feed", 17.5, 3, 24, UP, new double[]{16, 0, 21, 21, 3, 27}),

                    new Pin("joint", 24, 3.75, 24, UP, new double[]{23, 0, 20, 25, 3.75, 28}),

                    new Pin("reducer", 47.5, 3.4, 24, UP, new double[]{44, 0, 20, 52, 3.5, 28}),

                    new Pin("tracer", 52, 2.1, 24, UP, new double[]{49, 0, 23, 55, 2.1, 25}),

                    new Pin("elbow", 72, 2.1, 24, UP, new double[]{64, 0, 16, 80, 2.1, 32}),

                    new Pin("lands", 72, 3, 16, SOUTH, new double[]{68, 0, 16, 76, 6, 16})));

    public static final BlockFigure CABLE_GRADES = new BlockFigure("cable_grades", 220, 92, 4, 315.0, 30.0,
            cableGrades(), List.of());

    private static List<Placement> cableGrades() {
        final String[] grades = {"create_stats:busbar_trunk", "create_stats:bus_duct", "create_stats:cable_tray",
                "create_stats:surface_cable"};
        final String[] dyes = {"red", "blue", "lime", "orange"};
        final List<Placement> out = new ArrayList<>();
        for (int z = 0; z < grades.length; z++) {
            for (int x = 0; x < 4; x++) {
                out.add(new Placement(grades[z], Map.of("down", "true", "color", dyes[z]), x, 0, z,
                        new double[]{0, 0, 4, 16, 3.75, 12}, true, FIX_CABLE));
            }
        }
        return out;
    }

    private static final String WALL = "minecraft:smooth_stone";

    public static final BlockFigure INDOOR_RUN = new BlockFigure("indoor_cable", 200, 136, 5, 315.0, 30.0,
            List.of(
                    new Placement(WALL, Map.of(), 1, 0, 0, CELL, false),
                    new Placement(WALL, Map.of(), 1, 1, 0, CELL, false),
                    new Placement(WALL, Map.of(), 1, 0, -1, CELL, false),
                    new Placement(WALL, Map.of(), 1, 1, -1, CELL, false),

                    new Placement("create_stats:indoor_cable", Map.of("anchor", "east"), 0, 1, 0,
                            new double[]{14, 6.25, 6.25, 16, 9.75, 9.75}, true, FIX_INDOOR),

                    new Placement("create_stats:indoor_cable", Map.of("anchor", "west"), 2, 1, 0,
                            new double[]{0, 0, 6.25, 2, 9.75, 9.75}, true, FIX_INDOOR),
                    new Placement("create_stats:indoor_cable", Map.of("anchor", "west"), 2, 0, 0,
                            new double[]{1, 7.5, 7.5, 16, 16, 8.5}, true, FIX_INDOOR),

                    new Placement("create_stats:indoor_cable", Map.of("anchor", "free"), 3, 0, 0,
                            new double[]{0, 6.25, 6.25, 18, 9.75, 9.75}, true, FIX_INDOOR),

                    new Placement("create_stats:water_cooler", Map.of("facing", "east", "half", "lower"), 4, 0, 0,
                            new double[]{2, 0, 0.6, 14.05, 16, 15.4}, false),
                    new Placement("create_stats:water_cooler", Map.of("facing", "east", "half", "upper"), 4, 1, 0,
                            new double[]{3.65, 0, 3.65, 12.35, 15.6, 12.35}, false)),
            List.of(

                    new Pin("gland", 33, 24, 8, EAST, new double[]{31.5, 22.25, 6.25, 33, 25.75, 9.75}),

                    new Pin("clipped", 34, 16, 8, EAST, new double[]{33, 9, 7.5, 34, 22, 8.5}),

                    new Pin("free", 56, 8.5, 8, UP, new double[]{50, 7.5, 7.5, 62, 8.5, 8.5}),

                    new Pin("collar", 63.5, 9.75, 8, UP, new double[]{62.5, 6.25, 6.25, 64.5, 9.75, 9.75})),
            List.of(new BlockFigure.Tie(BlockFigure.Tie.Kind.BORE, new int[]{2, 1, 0}, new int[]{0, 1, 0})));

    private static final double HINGE_X = 3.05;
    private static final double HINGE_Z = 6.95;
    private static final double OPEN_DEGREES = -112.0;

    private static double[] onOpenDoor(double x, double y, double z) {
        final double t = Math.toRadians(OPEN_DEGREES);
        final double dx = x - HINGE_X;
        final double dz = z - HINGE_Z;
        return new double[]{HINGE_X + dx * Math.cos(t) + dz * Math.sin(t), y, HINGE_Z - dx * Math.sin(t) + dz * Math.cos(t)};
    }

    private static final int CABLE_BOX_X = -2;
    private static final int WIRE_BOX_X = 0;

    public static final BlockFigure JUNCTIONS = new BlockFigure("cable_diagnostic_junction", 200, 112, 8, 330.0, 24.0,
            junctionPlacements(), junctionPins());

    private static List<Placement> junctionPlacements() {
        final List<Placement> out = new ArrayList<>();

        for (int x = CABLE_BOX_X - 1; x <= WIRE_BOX_X + 1; x++) {
            for (int y = 0; y <= 2; y++) {
                final double y0 = y == 0 ? 14 : 0;
                final double y1 = y == 2 ? 2 : 16;
                final double x0 = x == CABLE_BOX_X - 1 ? 14 : 0;
                final double x1 = x == WIRE_BOX_X + 1 ? 2 : 16;
                out.add(new Placement(WALL, Map.of(), x, y, -1, new double[]{x0, y0, 15.5, x1, y1, 16}, false));
            }
        }
        final String cableFace = com.createstats.book.specimen.CarryFixtures.JUNCTION_CABLE;
        final String wireFace = com.createstats.book.specimen.CarryFixtures.JUNCTION_WIRE;

        final double[] box = {-1, 0, 0, 15.5, 16, 16.5};
        out.add(new Placement("create_stats:cable_diagnostic_junction", Map.of("facing", "north", "open", "true"),
                CABLE_BOX_X, 1, 0, box, true, cableFace));
        out.add(new Placement("create_stats:wire_terminal_junction", Map.of("facing", "north", "open", "true"),
                WIRE_BOX_X, 1, 0, box, true, wireFace));

        out.add(cableOn("create_stats:surface_cable", "north", CABLE_BOX_X, 2, 0, new double[]{7, 0, 0, 9, 2, 2}));
        out.add(cableOn("create_stats:surface_cable", "north", CABLE_BOX_X, 0, 0, new double[]{7, 14, 0, 9, 16, 2}));
        return out;
    }

    private static List<Pin> junctionPins() {
        final double cx = CABLE_BOX_X * 16;
        final double wx = WIRE_BOX_X * 16;
        final List<Pin> out = new ArrayList<>();

        final double[] card = onOpenDoor(6.825, 7.5, 6.0);
        final double[] a = onOpenDoor(3.80, 3.10, 6.0);
        final double[] b = onOpenDoor(9.85, 11.90, 6.0);
        final double t = Math.toRadians(OPEN_DEGREES);
        out.add(new Pin("card", cx + card[0], 16 + card[1], card[2], new double[]{-Math.sin(t), 0, -Math.cos(t)},
                new double[]{cx + Math.min(a[0], b[0]), 16 + a[1], Math.min(a[2], b[2]),
                        cx + Math.max(a[0], b[0]), 16 + b[1], Math.max(a[2], b[2])}));

        out.add(new Pin("screen", cx + 8.2, 16 + 9.85, 2.98, SOUTH, new double[]{cx + 4.7, 16 + 8.1, 2.98,
                cx + 11.7, 16 + 11.6, 2.98}));

        out.add(new Pin("glands", cx + 8, 16 + 15.75, 2.25, UP, new double[]{cx + 6.6, 16 + 14.1, 1.0, cx + 9.4,
                16 + 15.75, 3.5}));

        out.add(new Pin("lamps", wx + 6.4, 16 + 7.24, 2.52, SOUTH, new double[]{wx + 4.45, 16 + 7.05, 2.42, wx + 8.35,
                16 + 7.42, 2.52}));

        out.add(new Pin("lever", wx + 4.45, 16 + 3.66, 3.79, SOUTH, new double[]{wx + 4.1, 16 + 3.2, 3.4, wx + 4.8,
                16 + 4.1, 4.2}));

        out.add(new Pin("studs", wx + 8, 16 + 14.35, 7.15, UP, new double[]{wx + 3.25, 16 + 12.85, 6.9, wx + 12.75,
                16 + 14.35, 7.4}));

        out.add(new Pin("isolator", wx + 15.5, 16 + 9.6, 6.1, EAST, new double[]{wx + 14.75, 16 + 7.7, 5.68,
                wx + 15.5, 16 + 9.85, 6.52}));
        return out;
    }

    private static final int[] SOCKET = {0, 1, 0};
    private static final int[] SLICER = {-2, 0, 0};
    private static final int FEED_END = 1;

    public static final BlockFigure OUTLET = new BlockFigure("power_outlet", 200, 136, 5, 330.0, 24.0,
            outletPlacements(), outletPins(), List.of(new BlockFigure.Tie(BlockFigure.Tie.Kind.CORD, SOCKET, SLICER)));

    private static List<Placement> outletPlacements() {
        final List<Placement> out = new ArrayList<>();

        for (int x = SLICER[0]; x <= FEED_END; x++) {
            for (int y = 0; y <= 1; y++) {
                out.add(new Placement(WALL, Map.of(), x, y, -1, CELL, false));
            }
        }

        out.add(new Placement("create_stats:power_outlet", Map.of("facing", "north", "lamp", "live"), SOCKET[0], SOCKET[1],
                SOCKET[2], new double[]{2, 2, 0, 14, 14, 4.6}, true, com.createstats.book.specimen.CarryFixtures.OUTLET));

        for (int x = SOCKET[0] + 1; x <= FEED_END; x++) {
            out.add(cableOn("create_stats:surface_cable", "north", x, SOCKET[1], 0, new double[]{0, 7, 0, 16, 9, 2.45}));
        }

        out.add(cableOn("create_stats:surface_cable", "west", FEED_END + 1, SOCKET[1], -1,
                new double[]{0, 7, 0, 2.45, 9, 16}));
        out.add(cableOn("create_stats:surface_cable", "south", FEED_END, SOCKET[1], -2,
                new double[]{0, 7, 13.55, 16, 9, 16}));

        out.add(new Placement("create_stats:bread_slicer", Map.of("facing", "south"), SLICER[0], SLICER[1], SLICER[2],
                new double[]{0.85, 0, 1.1, 15.15, 14.3, 15.04}, false));
        return out;
    }

    private static List<Pin> outletPins() {
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("feed", 15, 16 + 8, 2.1, SOUTH, new double[]{14, 16 + 7, 2.1, 16, 16 + 9, 2.1}));

        out.add(new Pin("lamp", 8, 16 + 3.5, 2, SOUTH, new double[]{7, 16 + 3, 2, 9, 16 + 4, 2}));

        out.add(new Pin("plug", 8, 16 + 9.55, 3.1, UP, new double[]{6.45, 16 + 6.45, 1.7, 9.55, 16 + 9.55, 4.5}));

        final double[] mid = cordMidpoint();
        out.add(new Pin("cord", mid[0], mid[1], mid[2], UP, new double[]{mid[0] - 1, mid[1] - 1, mid[2] - 1,
                mid[0] + 1, mid[1] + 0.2, mid[2] + 1}));

        out.add(new Pin("relief", SLICER[0] * 16 + 17.5, 8, 8, EAST, new double[]{SLICER[0] * 16 + 16, 7.5, 7.5,
                SLICER[0] * 16 + 17.5, 8.5, 8.5}));
        return out;
    }

    private static double[] cordMidpoint() {
        final BlockFigure probe = new BlockFigure("power_outlet", 200, 136, 5, 330.0, 24.0, outletPlacements(),
                List.of(), List.of(new BlockFigure.Tie(BlockFigure.Tie.Kind.CORD, SOCKET, SLICER)));
        final int socket = FigureWorld.of(probe).indexAt(new net.minecraft.core.BlockPos(SOCKET[0], SOCKET[1], SOCKET[2]));
        final com.createstats.book.specimen.CarryFixtures.Cord cord =
                com.createstats.book.specimen.CarryFixtures.cord(probe, socket);
        if (cord == null || cord.route().isEmpty()) {
            return new double[]{-8, 16, 8};
        }
        final net.minecraft.world.phys.Vec3 v = cord.route().get(cord.route().size() / 2);
        return new double[]{v.x * 16, v.y * 16, v.z * 16};
    }

    private static final double[] PDU_FRONT = {1.5, 0, -0.75};

    public static final BlockFigure POWER_DISTRIBUTION_UNIT = new BlockFigure("power_distribution_unit", 200, 124, 6,
            345.0, 12.0, List.of(

                    new Placement("create_stats:power_distribution_unit", Map.of("facing", "south"), PDU_FRONT[0],
                            PDU_FRONT[1], PDU_FRONT[2], new double[]{-2, 0, -2.5, 18, 19, 16}, true,
                            com.createstats.book.specimen.PduPlates.FACE),
                    new Placement("create_stats:power_distribution_unit", Map.of("facing", "north"), 0, 0, 0,
                            new double[]{-2, 0, 0, 18, 19, 18.5}, false)),
            pduPins());

    private static List<Pin> pduPins() {
        final double fx = PDU_FRONT[0] * 16;
        final double fz = PDU_FRONT[2] * 16;

        final double glass = fz + 16 - (8 - (0.5 - 4.0 / 16.0 + 0.002) * 16);
        final List<Pin> out = new ArrayList<>();

        out.add(new Pin("face", fx + 12.6, 12.2, glass, SOUTH, new double[]{fx + 3, 6, glass, fx + 13, 14, glass}));

        out.add(new Pin("lamps", fx + 11.6, 9.9, glass, SOUTH,
                new double[]{fx + 3.29, 9.15, glass, fx + 12.71, 10.5, glass}));

        out.add(new Pin("studs", fx + 12.5, 18, fz + 9.75, SOUTH,
                new double[]{fx + 2.75, 17, fz + 5.25, fx + 13.25, 19, fz + 9.75}));

        out.add(new Pin("inputs", fx + 18, 7.5, fz + 7.5, EAST, new double[]{fx + 18, 6, fz + 5, fx + 18, 13, fz + 10}));

        out.add(new Pin("back", 8, 7, 18.5, SOUTH, new double[]{6, 5, 16, 10, 9, 18.5}));
        return out;
    }

    private static final List<BlockFigure> ALL = all(List.of(KINETIC_DYNAMO, DYNAMO_GRADES, BIOFUEL_BURNER,
            SOLAR_PANEL, STARTER_CAPACITOR, CAPACITOR_BANK, BATTERY_CONTROLLER, PORTABLE_INTERFACE, CABLE_RUN,
            CABLE_GRADES, INDOOR_RUN, JUNCTIONS, OUTLET), WireFigures.all(), List.of(POWER_DISTRIBUTION_UNIT));

    private static List<BlockFigure> all(List<BlockFigure> first, List<BlockFigure> then, List<BlockFigure> last) {
        final List<BlockFigure> out = new ArrayList<>(first);
        out.addAll(then);
        out.addAll(last);
        return List.copyOf(out);
    }

    public static List<BlockFigure> all() {
        return ALL;
    }

    public static Optional<BlockFigure> byId(String id) {
        for (final BlockFigure f : ALL) {
            if (f.id().equals(id)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
