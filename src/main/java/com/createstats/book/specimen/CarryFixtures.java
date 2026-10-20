package com.createstats.book.specimen;

import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.FigureWorld;
import com.createstats.power.CableLinks;
import com.createstats.power.CableTier;
import com.createstats.power.CordPlug;
import com.createstats.power.CordRoute;
import com.createstats.power.FeRates;
import com.createstats.power.IndoorCableLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CarryFixtures {
    private CarryFixtures() {
    }

    public static final BlockPos AT = new BlockPos(0, -4096, 0);

    public static final String CABLE = "carry.cable";

    public static final String INDOOR = "carry.indoor_cable";

    public static final String JUNCTION_CABLE = "carry.junction.cable";

    public static final String JUNCTION_WIRE = "carry.junction.wire";

    public static final String OUTLET = "carry.outlet";

    public static final List<String> IDS = List.of(CABLE, INDOOR, JUNCTION_CABLE, JUNCTION_WIRE, OUTLET);

    public static BlockPos positionOf(BlockFigure figure, int index) {
        return AT.offset(FigureWorld.of(figure).cellOf(index));
    }

    public record JunctionReading(boolean wire, int flow, int capacity, int buffer, int maxBuffer,
                                  DiagnosticJunctionBE.Fault fault, boolean merged, boolean cutOff,
                                  List<DiagnosticJunctionBE.Fault> channels, int[] channelFlow, int[] channelCapacity,
                                  int[] channelBuffer) {
        public JunctionReading {
            channels = List.copyOf(channels);
            channelFlow = channelFlow.clone();
            channelCapacity = channelCapacity.clone();
            channelBuffer = channelBuffer.clone();
        }

        public float loadFraction() {
            return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, flow / (float) capacity));
        }
    }

    public static JunctionReading cableBox() {
        final int buffer = DiagnosticJunctionBE.JUNCTION_BUFFER + 4 * CableBE.SEGMENT_BUFFER;
        final DiagnosticJunctionBE.Fault none = DiagnosticJunctionBE.Fault.NO_BUS;
        return new JunctionReading(false, StoreFixtures.dynamoFlow(), FeRates.cable(CableTier.SURFACE), buffer, buffer,
                DiagnosticJunctionBE.Fault.OK, false, false, List.of(DiagnosticJunctionBE.Fault.OK, none, none),
                new int[3], new int[3], new int[3]);
    }

    public static JunctionReading wireBox() {
        final DiagnosticJunctionBE.Fault none = DiagnosticJunctionBE.Fault.NO_BUS;
        return new JunctionReading(true, 0, 0, 0, 0, none, false, false, List.of(none, none, none), new int[3],
                new int[3], new int[3]);
    }

    @Nullable
    public static JunctionReading junction(String fixture) {
        return switch (fixture) {
            case JUNCTION_CABLE -> cableBox();
            case JUNCTION_WIRE -> wireBox();
            default -> null;
        };
    }

    private static final double[] SINGLE_GANG = {8.0 / 16.0};

    private static final double[] DUPLEX_GANGS = {4.0 / 16.0, 12.0 / 16.0};

    public record Cord(BlockPos machine, Direction face, List<Vec3> route) {
        public Cord {
            route = List.copyOf(route);
        }
    }

    @Nullable
    public static Cord cord(BlockFigure figure, int index) {
        final FigureWorld world = FigureWorld.of(figure);
        final BlockPos socket = world.cellOf(index);
        for (final BlockFigure.Tie t : figure.ties()) {
            if (t.kind() != BlockFigure.Tie.Kind.CORD || !socket.equals(new BlockPos(t.from()[0], t.from()[1], t.from()[2]))) {
                continue;
            }
            final BlockPos machine = new BlockPos(t.to()[0], t.to()[1], t.to()[2]);
            final BlockFigure.Placement plate = figure.placements().get(index);
            final Direction facing = Direction.byName(plate.props().getOrDefault("facing", "north"));
            final double[] gangs = plate.block().endsWith("duplex_power_outlet") ? DUPLEX_GANGS : SINGLE_GANG;
            final Vec3 mouth = CordPlug.socketMouth(facing == null ? Direction.NORTH : facing, socket, gangs,
                    Vec3.atCenterOf(machine));
            final Direction face = world.cordFace(machine, mouth);
            if (face == null) {
                return null;
            }
            final Vec3 terminal = CordPlug.terminal(machine, face);
            if (mouth.distanceTo(terminal) > CordRoute.MAX_REACH) {
                return null;
            }
            final List<Vec3> route = CordRoute.settle(world.solid(socket, machine), mouth, terminal);
            return route == null ? null : new Cord(machine, face, route);
        }
        return null;
    }

    public static List<Map<String, Object>> spans(BlockFigure figure) {
        final FigureWorld world = FigureWorld.of(figure);
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final BlockFigure.Tie t : world.spans()) {
            final Map<String, Object> span = new LinkedHashMap<>();
            final List<Object> points = new ArrayList<>();
            for (final Vec3 v : world.spanPoints(t)) {
                points.add(List.of(round(v.x), round(v.y), round(v.z)));
            }
            span.put("points", points);

            span.put("radius", round(t.tier().halfThickness() * 1.15));
            span.put("colour", List.of(t.tier().strandA()[0], t.tier().strandA()[1], t.tier().strandA()[2]));
            out.add(span);
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1.0e6) / 1.0e6;
    }

    @Nullable
    public static Map<String, Object> draw(BlockFigure figure, int index) {
        final BlockFigure.Placement p = figure.placements().get(index);
        if (p.fixture() == null || !IDS.contains(p.fixture())) {
            return null;
        }
        final FigureWorld world = FigureWorld.of(figure);
        final BlockPos cell = world.cellOf(index);
        final Map<String, Object> out = new LinkedHashMap<>();
        final BlockPos at = AT.offset(cell);
        out.put("position", List.of(at.getX(), at.getY(), at.getZ()));
        switch (p.fixture()) {
            case CABLE -> {
                out.put("tier", world.cableTier(cell).name().toLowerCase(Locale.ROOT));
                out.put("dye", FigureWorld.dyeName(world.dyeAt(cell)));
                final Map<String, Object> faces = new LinkedHashMap<>();
                for (final Direction face : Direction.values()) {
                    if (!world.hasCableFace(cell, face)) {
                        continue;
                    }
                    final Map<String, Object> arms = new LinkedHashMap<>();
                    for (final Direction towards : Direction.values()) {
                        if (towards.getAxis() == face.getAxis()) {
                            continue;
                        }
                        final CableLinks.Link link = world.link(cell, face, towards);
                        if (link == CableLinks.Link.NONE && world.boreTowards(cell, towards) == null) {
                            continue;
                        }
                        final Map<String, Object> arm = new LinkedHashMap<>();
                        arm.put("link", world.boreTowards(cell, towards) != null ? "BORE" : link.name());
                        arm.put("other", world.otherGrade(cell, face, towards, link));
                        arm.put("owns", CableLinks.ownsCorner(face, link == CableLinks.Link.CONVEX
                                ? towards.getOpposite() : towards));
                        arm.put("reach", link == CableLinks.Link.DEVICE ? world.reachInto(cell, towards) : 0.0);
                        arms.put(towards.getSerializedName(), arm);
                    }
                    faces.put(face.getSerializedName(), arms);
                }
                out.put("faces", faces);
            }
            case INDOOR -> {
                out.put("dye", FigureWorld.dyeName(world.dyeAt(cell)));
                out.put("anchor", world.anchorAt(cell).getSerializedName());
                final Map<String, Object> links = new LinkedHashMap<>();
                for (final Direction d : Direction.values()) {
                    final IndoorCableLinks.Link link = world.indoorLink(cell, d);
                    if (link == IndoorCableLinks.Link.NONE) {
                        continue;
                    }
                    final Map<String, Object> arm = new LinkedHashMap<>();
                    arm.put("link", link.name());
                    arm.put("reach", link == IndoorCableLinks.Link.DEVICE ? world.reachInto(cell, d) : 0.0);
                    arm.put("theirs", world.anchorTowards(cell, d).getSerializedName());
                    links.put(d.getSerializedName(), arm);
                }
                out.put("links", links);
            }
            case JUNCTION_CABLE, JUNCTION_WIRE -> {
                final JunctionReading r = junction(p.fixture());
                out.put("wire", r.wire());
                out.put("fault", r.fault().name());
                final List<String> channels = new ArrayList<>();
                r.channels().forEach(f -> channels.add(f.name()));
                out.put("channels", channels);
                out.put("merged", r.merged());
                out.put("cut", r.cutOff());
                out.put("face", p.fixture());
            }
            case OUTLET -> {
                final Cord c = cord(figure, index);
                if (c != null) {
                    final List<Object> route = new ArrayList<>();
                    for (final Vec3 v : c.route()) {
                        route.add(List.of(v.x, v.y, v.z));
                    }
                    out.put("machine", List.of(c.machine().getX(), c.machine().getY(), c.machine().getZ()));
                    out.put("face", c.face().getSerializedName());
                    out.put("route", route);
                }
            }
            default -> {
            }
        }
        return out;
    }
}
