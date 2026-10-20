package com.createstats.book.scene;

import com.createstats.power.CableLinks;
import com.createstats.power.CableTier;
import com.createstats.power.CordRoute;
import com.createstats.power.IndoorCableAnchor;
import com.createstats.power.IndoorCableLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

// cables have no block model, so the figure needs a tiny world for the renderers to ask
public final class FigureWorld {
    public static final Map<String, CableTier> CABLES = Map.of(
            "create_stats:surface_cable", CableTier.SURFACE,
            "create_stats:cable_tray", CableTier.TRAY,
            "create_stats:bus_duct", CableTier.DUCT,
            "create_stats:busbar_trunk", CableTier.TRUNK);

    public static final String INDOOR = "create_stats:indoor_cable";

    public static final Map<String, BiPredicate<Map<String, String>, Direction>> DEVICES = Map.ofEntries(
            Map.entry("create_stats:kinetic_dynamo", (props, side) -> true),
            Map.entry("create_stats:cable_diagnostic_junction", (props, side) -> true),
            Map.entry("create_stats:power_outlet", (props, side) -> true),
            Map.entry("create_stats:duplex_power_outlet", (props, side) -> true),
            Map.entry("create_stats:induction_smelter", (props, side) -> side == back(props)),
            Map.entry("create_stats:water_cooler",
                    (props, side) -> !"upper".equals(props.get("half")) && side == back(props)),
            Map.entry("create_stats:toaster", (props, side) -> side.getAxis().isHorizontal()),
            Map.entry("create_stats:bread_slicer", (props, side) -> side.getAxis().isHorizontal()),
            Map.entry("create_stats:smart_home_panel", (props, side) -> true),
            Map.entry("create_stats:fe_motor_mk1", (props, side) -> true),
            Map.entry("create_stats:arc_furnace", (props, side) -> true),
            Map.entry("create_stats:pendant_lamp", (props, side) -> true),
            Map.entry("create_stats:stage_light", (props, side) -> true),
            Map.entry("create_stats:rocker_switch", (props, side) -> true));

    private static Direction back(Map<String, String> props) {
        final Direction facing = Direction.byName(props.getOrDefault("facing", "north"));
        return facing == null ? Direction.SOUTH : facing.getOpposite();
    }

    private final BlockFigure figure;
    private final Map<BlockPos, Integer> byCell = new HashMap<>();

    private FigureWorld(BlockFigure figure) {
        this.figure = figure;
        for (int i = 0; i < figure.placements().size(); i++) {
            final BlockFigure.Placement p = figure.placements().get(i);
            if (p.x() == Math.rint(p.x()) && p.y() == Math.rint(p.y()) && p.z() == Math.rint(p.z())) {
                byCell.put(new BlockPos((int) p.x(), (int) p.y(), (int) p.z()), i);
            }
        }
    }

    public static FigureWorld of(BlockFigure figure) {
        return new FigureWorld(figure);
    }

    public BlockFigure figure() {
        return figure;
    }

    @Nullable
    public BlockFigure.Placement at(BlockPos cell) {
        final Integer i = byCell.get(cell);
        return i == null ? null : figure.placements().get(i);
    }

    public int indexAt(BlockPos cell) {
        final Integer i = byCell.get(cell);
        return i == null ? -1 : i;
    }

    public BlockPos cellOf(int index) {
        final BlockFigure.Placement p = figure.placements().get(index);
        return new BlockPos((int) Math.round(p.x()), (int) Math.round(p.y()), (int) Math.round(p.z()));
    }

    private String blockAt(BlockPos cell) {
        final BlockFigure.Placement p = at(cell);
        return p == null ? null : p.block();
    }

    @Nullable
    public CableTier cableTier(BlockPos cell) {
        final String block = blockAt(cell);
        return block == null ? null : CABLES.get(block);
    }

    public boolean hasCableFace(BlockPos cell, Direction face) {
        final BlockFigure.Placement p = at(cell);
        return p != null && CABLES.containsKey(p.block()) && "true".equals(p.props().get(face.getSerializedName()));
    }

    public DyeColor dyeAt(BlockPos cell) {
        final BlockFigure.Placement p = at(cell);
        final String fallback = p != null && INDOOR.equals(p.block()) ? "light_gray" : "orange";
        final DyeColor dye = DyeColor.byName(p == null ? fallback : p.props().getOrDefault("color", fallback), null);
        return dye == null ? DyeColor.ORANGE : dye;
    }

    public CableLinks.View cables() {
        return new CableLinks.View() {
            @Override
            public boolean hasCableFace(BlockPos pos, Direction face) {
                return FigureWorld.this.hasCableFace(pos, face);
            }

            @Override
            public boolean isCable(BlockPos pos) {
                return cableTier(pos) != null;
            }

            @Override
            public boolean isDevice(BlockPos pos, Direction fromSide) {
                return takesPower(pos, fromSide);
            }

            @Override
            public boolean isFree(BlockPos pos) {
                return at(pos) == null || cableTier(pos) != null;
            }

            @Override
            public int colourAt(BlockPos pos) {
                return cableTier(pos) != null ? dyeAt(pos).ordinal() : CableLinks.NO_COLOUR;
            }
        };
    }

    public CableLinks.Link link(BlockPos cell, Direction face, Direction towards) {
        return CableLinks.of(cables(), cell, face, towards);
    }

    public boolean otherGrade(BlockPos cell, Direction face, Direction towards, CableLinks.Link kind) {
        final BlockPos ahead = switch (kind) {
            case FLAT -> cell.relative(towards);
            case CONVEX -> cell.relative(towards).relative(face);
            default -> null;
        };
        final CableTier mine = cableTier(cell);
        final CableTier theirs = ahead == null ? null : cableTier(ahead);
        return theirs != null && theirs != mine;
    }

    public IndoorCableAnchor anchorAt(BlockPos cell) {
        final BlockFigure.Placement p = at(cell);
        final String name = p == null ? "free" : p.props().getOrDefault("anchor", "free");
        for (final IndoorCableAnchor a : IndoorCableAnchor.values()) {
            if (a.getSerializedName().equals(name)) {
                return a;
            }
        }
        return IndoorCableAnchor.FREE;
    }

    public List<BlockPos> boresOf(BlockPos cell) {
        final List<BlockPos> out = new ArrayList<>(1);
        for (final BlockFigure.Tie t : figure.ties()) {
            if (t.kind() != BlockFigure.Tie.Kind.BORE) {
                continue;
            }
            final BlockPos from = new BlockPos(t.from()[0], t.from()[1], t.from()[2]);
            final BlockPos to = new BlockPos(t.to()[0], t.to()[1], t.to()[2]);
            if (from.equals(cell)) {
                out.add(to);
            } else if (to.equals(cell)) {
                out.add(from);
            }
        }
        return out;
    }

    @Nullable
    public BlockPos boreTowards(BlockPos cell, Direction towards) {
        for (final BlockPos far : boresOf(cell)) {
            if (com.createstats.power.BoreLinks.axisBetween(cell, far) == towards) {
                return far;
            }
        }
        return null;
    }

    public IndoorCableLinks.View indoor() {
        return new IndoorCableLinks.View() {
            @Override
            public boolean isIndoorCable(BlockPos pos) {
                return INDOOR.equals(blockAt(pos));
            }

            @Override
            public int colourAt(BlockPos pos) {
                return INDOOR.equals(blockAt(pos)) ? dyeAt(pos).ordinal() : IndoorCableLinks.NO_COLOUR;
            }

            @Override
            public boolean isDevice(BlockPos pos, Direction fromSide) {
                return takesPower(pos, fromSide) || takesPowerAnywhere(pos);
            }

            @Override
            @Nullable
            public BlockPos boreTowards(BlockPos pos, Direction towards) {
                return FigureWorld.this.boreTowards(pos, towards);
            }
        };
    }

    public IndoorCableLinks.Link indoorLink(BlockPos cell, Direction towards) {
        return IndoorCableLinks.of(indoor(), cell, towards);
    }

    public IndoorCableAnchor anchorTowards(BlockPos cell, Direction towards) {
        final BlockPos ahead = cell.relative(towards);
        return INDOOR.equals(blockAt(ahead)) ? anchorAt(ahead) : anchorAt(cell);
    }

    public boolean takesPower(BlockPos cell, Direction side) {
        final BlockFigure.Placement p = at(cell);
        final BiPredicate<Map<String, String>, Direction> rule = p == null ? null : DEVICES.get(p.block());
        return rule != null && side != null && rule.test(p.props(), side);
    }

    public boolean takesPowerAnywhere(BlockPos cell) {
        for (final Direction d : Direction.values()) {
            if (takesPower(cell, d)) {
                return true;
            }
        }
        return false;
    }

    public double reachInto(BlockPos cell, Direction towards) {
        final double minimum = 1.0 / 16.0;
        final BlockFigure.Placement p = at(cell.relative(towards));
        if (p == null) {
            return minimum;
        }
        final double[] o = p.occupies();
        final int axis = towards.getAxis().ordinal();
        final double depth = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? o[axis] / 16.0 : 1.0 - o[axis + 3] / 16.0;
        return Math.max(minimum, Math.min(0.4, depth));
    }

    public static final Map<String, com.createstats.power.ConnectorTier> CONNECTORS = Map.of(
            "create_stats:power_connector", com.createstats.power.ConnectorTier.POWER,
            "create_stats:advanced_power_connector", com.createstats.power.ConnectorTier.ADVANCED,
            "create_stats:industrial_power_connector", com.createstats.power.ConnectorTier.INDUSTRIAL);

    public List<BlockFigure.Tie> spans() {
        final List<BlockFigure.Tie> out = new ArrayList<>();
        for (final BlockFigure.Tie t : figure.ties()) {
            if (t.kind() == BlockFigure.Tie.Kind.SPAN) {
                out.add(t);
            }
        }
        return out;
    }

    public Vec3 terminalPoint(BlockPos cell) {
        final BlockFigure.Placement p = at(cell);
        Vec3 local = new Vec3(0.5, 0.5, 0.5);
        if (p != null && CONNECTORS.containsKey(p.block())) {
            final Direction facing = Direction.byName(p.props().getOrDefault("facing", "up"));
            com.createstats.block.ConnectorAnchor anchor = com.createstats.block.ConnectorAnchor.CENTER;
            for (final com.createstats.block.ConnectorAnchor a : com.createstats.block.ConnectorAnchor.values()) {
                if (a.getSerializedName().equals(p.props().getOrDefault("anchor", "center"))) {
                    anchor = a;
                }
            }
            local = com.createstats.renderer.PowerConnectorRenderer.connectorTerminal(
                    facing == null ? Direction.UP : facing, anchor);
        }
        return new Vec3(cell.getX() + local.x, cell.getY() + local.y, cell.getZ() + local.z);
    }

    public List<Vec3> spanPoints(BlockFigure.Tie span) {
        final Vec3 a = terminalPoint(new BlockPos(span.from()[0], span.from()[1], span.from()[2]));
        final Vec3 b = terminalPoint(new BlockPos(span.to()[0], span.to()[1], span.to()[2]));
        return com.createstats.client.ponder.fe.FePaths.catenary(a, b, span.tension().slack());
    }

    public double spanLength(BlockFigure.Tie span) {
        final Vec3 a = terminalPoint(new BlockPos(span.from()[0], span.from()[1], span.from()[2]));
        final Vec3 b = terminalPoint(new BlockPos(span.to()[0], span.to()[1], span.to()[2]));
        return a.distanceTo(b);
    }

    public CordRoute.Solid solid(BlockPos socket, BlockPos machine) {
        final List<AABB> boxes = new ArrayList<>();
        for (int i = 0; i < figure.placements().size(); i++) {
            final BlockPos cell = cellOf(i);
            if (cell.equals(socket) || cell.equals(machine)) {
                continue;
            }
            final double[] b = figure.placements().get(i).sceneBox();
            boxes.add(new AABB(b[0] / 16.0, b[1] / 16.0, b[2] / 16.0, b[3] / 16.0, b[4] / 16.0, b[5] / 16.0));
        }
        return (x, y, z) -> {
            for (final AABB box : boxes) {
                if (x > box.minX && x < box.maxX && y > box.minY && y < box.maxY && z > box.minZ && z < box.maxZ) {
                    return box;
                }
            }
            return null;
        };
    }

    @Nullable
    public Direction cordFace(BlockPos machine, Vec3 from) {
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        for (final Direction face : Direction.values()) {
            if (!takesPower(machine, face) || at(machine.relative(face)) != null) {
                continue;
            }
            final double d = com.createstats.power.CordPlug.terminal(machine, face).distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = face;
            }
        }
        return best;
    }

    public static String dyeName(DyeColor dye) {
        return dye.getSerializedName().toLowerCase(Locale.ROOT);
    }
}
