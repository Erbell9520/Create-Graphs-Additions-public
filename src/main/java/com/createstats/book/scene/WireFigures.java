package com.createstats.book.scene;

import com.createstats.book.scene.BlockFigure.Pin;
import com.createstats.book.scene.BlockFigure.Placement;
import com.createstats.book.scene.BlockFigure.Tie;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WireFigures {
    private WireFigures() {
    }

    private static final double[] UP = {0, 1, 0};
    private static final double[] SOUTH = {0, 0, 1};
    private static final double[] EAST = {1, 0, 0};
    private static final double[] CELL = {0, 0, 0, 16, 16, 16};

    private static double[] connectorBox(String facing) {
        return switch (facing) {
            case "south" -> new double[]{5, 5, 0, 11, 11, 12};
            case "north" -> new double[]{5, 5, 4, 11, 11, 16};
            case "east" -> new double[]{0, 5, 5, 12, 11, 11};
            case "west" -> new double[]{4, 5, 5, 16, 11, 11};
            case "down" -> new double[]{5, 4, 5, 11, 16, 11};
            default -> new double[]{5, 0, 5, 11, 12, 11};
        };
    }

    private static Placement connector(String block, String facing, int x, int y, int z) {
        return new Placement(block, Map.of("facing", facing, "anchor", "center"), x, y, z, connectorBox(facing), false);
    }

    private static final String POWER = "create_stats:power_connector";
    private static final String ADVANCED = "create_stats:advanced_power_connector";
    private static final String INDUSTRIAL = "create_stats:industrial_power_connector";
    private static final String BRICKS = "minecraft:stone_bricks";
    private static final String STONE = "minecraft:smooth_stone";

    public static final int[] HUB = {0, 0, 0};

    public static final List<Tie> HUB_SPANS = List.of(
            Tie.span(HUB, new int[]{-12, 2, 0}, WireTier.COPPER, WireTension.SLACK),
            Tie.span(HUB, new int[]{5, 8, 0}, WireTier.INSULATED, WireTension.STANDARD),
            Tie.span(HUB, new int[]{13, -2, 0}, WireTier.SHIELDED, WireTension.TAUT),
            Tie.span(HUB, new int[]{-4, -8, 0}, WireTier.HV, WireTension.DRUM));

    private static List<Placement> skyWorld() {
        final List<Placement> out = new ArrayList<>();

        out.add(new Placement(BRICKS, Map.of(), HUB[0], HUB[1], HUB[2] - 1, CELL, false));
        out.add(connector(INDUSTRIAL, "south", HUB[0], HUB[1], HUB[2]));
        for (final Tie t : HUB_SPANS) {
            final String block = switch (t.tier()) {
                case COPPER, INSULATED -> POWER;
                case SHIELDED -> ADVANCED;
                case HV -> INDUSTRIAL;
            };
            out.add(new Placement(BRICKS, Map.of(), t.to()[0], t.to()[1], t.to()[2] - 1, CELL, false));
            out.add(connector(block, "south", t.to()[0], t.to()[1], t.to()[2]));
        }
        return out;
    }

    private static final double WORLD_YAW = 0.0;
    private static final double WORLD_PITCH = 0.0;

    private static final double WORLD_SCALE = 16.0;

    private static final double[] HUB_TERMINAL = {8, 8, 11};

    public static final BlockFigure RING_WORLD = new BlockFigure("wire_tension.world", 356, 220, 0, WORLD_YAW,
            WORLD_PITCH, skyWorld(), List.of(), HUB_SPANS, BlockFigure.Framing.camera(HUB_TERMINAL, WORLD_SCALE));

    public static final int WRENCH_W = 300;
    public static final int WRENCH_H = 150;

    public static final int[] WRENCH_AIM = {WRENCH_W / 2, 36};

    public static final BlockFigure WRENCH_WORLD = new BlockFigure("wire_tension.wrench_world", WRENCH_W, WRENCH_H, 0,
            WORLD_YAW, WORLD_PITCH, skyWorld(), List.of(), HUB_SPANS,
            BlockFigure.Framing.camera(HUB_TERMINAL, WORLD_SCALE, WRENCH_AIM[0], WRENCH_AIM[1]));

    private static net.minecraft.world.phys.Vec3 terminal(int[] cell, String facing) {
        final net.minecraft.world.phys.Vec3 local = facing == null ? new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5)
                : com.createstats.renderer.PowerConnectorRenderer.connectorTerminal(
                        net.minecraft.core.Direction.byName(facing), com.createstats.block.ConnectorAnchor.CENTER);
        return new net.minecraft.world.phys.Vec3(cell[0] + local.x, cell[1] + local.y, cell[2] + local.z);
    }

    static double[] onSpan(int[] from, String fromFacing, int[] to, String toFacing, WireTension tension,
                           double along) {
        final List<net.minecraft.world.phys.Vec3> points = com.createstats.client.ponder.fe.FePaths.catenary(
                terminal(from, fromFacing), terminal(to, toFacing), tension.slack());
        final net.minecraft.world.phys.Vec3 p = points.get((int) Math.round(along * (points.size() - 1)));
        return new double[]{p.x * 16, p.y * 16, p.z * 16};
    }

    private static Pin wirePin(String id, double[] at, double[] face) {
        return new Pin(id, at[0], at[1], at[2], face,
                new double[]{at[0] - 2, at[1] - 2, at[2] - 2, at[0] + 2, at[1] + 2, at[2] + 2});
    }

    private static final String SMELTER = "create_stats:induction_smelter";

    private static final int[] BOLTED = {1, 0, 0};

    private static final int[] ONWARD = {9, 4, -5};

    private static final int BX = BOLTED[0] * 16;

    public static final BlockFigure CONNECTOR = new BlockFigure("power_connector", 220, 136, 0, 315.0, 30.0,
            List.of(
                    new Placement(SMELTER, Map.of("facing", "west"), 0, 0, 0, CELL, false),
                    connector(ADVANCED, "east", BOLTED[0], BOLTED[1], BOLTED[2])),
            List.of(

                    new Pin("terminal", BX + 12, 8, 8, EAST, new double[]{BX + 10, 6, 6, BX + 12, 10, 10}),

                    new Pin("sheds", BX + 6, 8, 10.5, SOUTH, new double[]{BX + 2.25, 5.5, 5.5, BX + 9, 10.5, 10.5}),

                    new Pin("base", BX + 0.5, 11, 8, UP, new double[]{BX, 5, 5, BX + 1, 11, 11}),

                    new Pin("block", 16, 3, 13, EAST, new double[]{16, 0, 0, 16, 16, 16}),
                    wirePin("span", onSpan(BOLTED, "east", ONWARD, null, WireTension.STANDARD, 0.12), UP)),
            List.of(Tie.span(BOLTED, ONWARD, WireTier.SHIELDED, WireTension.STANDARD)),
            BlockFigure.Framing.camera(new double[]{BX + 6, 8, 8}, 72.0, 106.0, 84.0));

    public static final BlockFigure CONNECTOR_GRADES = new BlockFigure("connector_grades", 220, 92, 4, 315.0, 30.0,
            List.of(
                    new Placement(STONE, Map.of(), 0, 0, 0, CELL, false),
                    new Placement(STONE, Map.of(), 2, 0, 0, CELL, false),
                    new Placement(STONE, Map.of(), 4, 0, 0, CELL, false),
                    connector(POWER, "up", 0, 1, 0),
                    connector(ADVANCED, "up", 2, 1, 0),
                    connector(INDUSTRIAL, "up", 4, 1, 0)),
            List.of(),
            List.of(Tie.span(new int[]{0, 1, 0}, new int[]{2, 1, 0}, WireTier.INSULATED, WireTension.STANDARD),
                    Tie.span(new int[]{2, 1, 0}, new int[]{4, 1, 0}, WireTier.SHIELDED, WireTension.STANDARD)));

    private static final int LEDGES = 3;

    static String connectorFor(WireTier tier) {
        for (final com.createstats.power.ConnectorTier c : com.createstats.power.ConnectorTier.values()) {
            if (c.multiplier() >= tier.multiplier()) {
                for (final Map.Entry<String, com.createstats.power.ConnectorTier> e : FigureWorld.CONNECTORS
                        .entrySet()) {
                    if (e.getValue() == c) {
                        return e.getKey();
                    }
                }
            }
        }
        return INDUSTRIAL;
    }

    private static List<Placement> ledges() {
        final List<Placement> out = new ArrayList<>();
        final WireTier[] tiers = WireTier.values();
        for (int z = 0; z < tiers.length; z++) {
            out.add(new Placement(STONE, Map.of(), 0, 0, z, CELL, false));
            out.add(new Placement(STONE, Map.of(), LEDGES, 0, z, CELL, false));
            out.add(connector(connectorFor(tiers[z]), "up", 0, 1, z));
            out.add(connector(connectorFor(tiers[z]), "up", LEDGES, 1, z));
        }
        return out;
    }

    private static List<Tie> ledgeSpans() {
        final List<Tie> out = new ArrayList<>();
        final WireTier[] tiers = WireTier.values();
        for (int z = 0; z < tiers.length; z++) {
            out.add(Tie.span(new int[]{0, 1, z}, new int[]{LEDGES, 1, z}, tiers[z], WireTension.STANDARD));
        }
        return out;
    }

    private static List<Pin> ledgePins() {
        final List<Pin> out = new ArrayList<>();
        final WireTier[] tiers = WireTier.values();
        for (int z = 0; z < tiers.length; z++) {
            out.add(wirePin(tiers[z].name().toLowerCase(java.util.Locale.ROOT), onSpan(new int[]{0, 1, z}, "up",
                    new int[]{LEDGES, 1, z}, "up", WireTension.STANDARD, 0.6), UP));
        }

        final int near = tiers.length - 1;
        out.add(new Pin("end", LEDGES * 16 + 8, 16 + 12, near * 16 + 8, UP,
                new double[]{LEDGES * 16 + 6, 16 + 10, near * 16 + 6, LEDGES * 16 + 10, 16 + 12, near * 16 + 10}));
        return out;
    }

    public static final BlockFigure WIRES = new BlockFigure("copper_wire", 220, 112, 5, 315.0, 30.0, ledges(),
            ledgePins(), ledgeSpans());

    private static final int PILLARS = 13;
    private static final int ROW_PITCH = 2;

    private static List<Placement> pillars() {
        final List<Placement> out = new ArrayList<>();
        final WireTension[] levels = WireTension.values();
        final int bottom = -(levels.length - 1) * ROW_PITCH;
        for (int y = bottom; y <= 0; y++) {
            out.add(new Placement(STONE, Map.of(), 0, y, 0, CELL, false));
            out.add(new Placement(STONE, Map.of(), PILLARS, y, 0, CELL, false));
        }
        for (int i = 0; i < levels.length; i++) {
            out.add(connector(POWER, "east", 1, -i * ROW_PITCH, 0));
            out.add(connector(POWER, "west", PILLARS - 1, -i * ROW_PITCH, 0));
        }
        return out;
    }

    private static List<Tie> tensionSpans() {
        final List<Tie> out = new ArrayList<>();
        final WireTension[] levels = WireTension.values();
        for (int i = 0; i < levels.length; i++) {
            out.add(Tie.span(new int[]{1, -i * ROW_PITCH, 0}, new int[]{PILLARS - 1, -i * ROW_PITCH, 0},
                    WireTier.COPPER, levels[i]));
        }
        return out;
    }

    public static final BlockFigure TENSIONS = new BlockFigure("wire_tensions", 240, 120, 4, 0.0, 0.0, pillars(),
            List.of(), tensionSpans());

    public static List<BlockFigure> all() {
        return List.of(CONNECTOR, CONNECTOR_GRADES, WIRES, TENSIONS, RING_WORLD, WRENCH_WORLD);
    }
}
