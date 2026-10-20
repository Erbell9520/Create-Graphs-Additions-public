package com.createstats.surveyor;

import com.createstats.ModConfiguration;
import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.PathFairing;
import com.createstats.surveyor.core.PathEdits;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.PathSurvey;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PathwayCodec;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StrokeSimplify;
import com.createstats.surveyor.network.SurveyorEditPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// stale edits refused, two players dragging one node never overwrite each other
public final class PathwayOps {
    public static final int UNDO_DEPTH = 32;

    public static final double SIMPLIFY_TOLERANCE = 0.25;

    public static final double MAX_MOVE = 64.0;

    private record Step(UUID pathway, @Nullable Pathway before) {
    }

    private record Undo(String dimension, String label, List<Step> steps) {
    }

    private record Change(UUID id, @Nullable Pathway before, @Nullable Pathway after) {
    }

    private static final Map<UUID, Deque<Undo>> UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<Undo>> REDO = new ConcurrentHashMap<>();

    private PathwayOps() {
    }

    public static void apply(ServerPlayer player, SurveyorEditPacket p) {
        if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) return;
        PathwayStore store = PathwayStore.get(level);
        if (p.op() == SurveyorEditPacket.UNDO) {
            undo(player, level, store, UNDO, REDO, "Undone: ", "Nothing to undo.");
            return;
        }
        if (p.op() == SurveyorEditPacket.REDO) {
            undo(player, level, store, REDO, UNDO, "Redone: ", "Nothing to redo.");
            return;
        }
        Pathway current = store.get(p.pathway());
        boolean creates = p.op() == SurveyorEditPacket.CREATE || (p.op() == SurveyorEditPacket.APPEND_STROKE && current == null);
        if (!creates) {
            if (current == null) {
                refuse(player, "That pathway is gone.");
                PathwaySync.removedTo(player, p.pathway());
                return;
            }
            if (!mayEdit(player, current)) {
                refuse(player, "Only " + PathwaySync.ownerName(level, current.owner()) + " (or an operator) can edit this pathway; they can share it on the Board.");
                return;
            }

            boolean relative = p.op() == SurveyorEditPacket.STEP_SPEED || p.op() == SurveyorEditPacket.STEP_CORNER
                    || p.op() == SurveyorEditPacket.STEP_STOP;
            if (!relative && p.version() != current.version()) {
                refuse(player, "Someone else edited this pathway just now: your edit was not applied. Look again and redo it.");
                PathwaySync.sendTo(player, current);
                return;
            }
        }
        String label = label(p, current);
        List<Change> changes;
        try {
            changes = switch (p.op()) {
                case SurveyorEditPacket.SPLIT, SurveyorEditPacket.JOIN, SurveyorEditPacket.DUPLICATE ->
                        lineEdit(player, level, store, current, p);
                default -> {
                    Pathway next = edit(player, level, store, current, p);
                    yield next == null || next == current ? null : List.of(new Change(next.id(), creates ? null : current, next));
                }
            };
        } catch (IOException e) {
            refuse(player, "That edit could not be read.");
            return;
        }
        if (changes == null || changes.isEmpty()) return;
        for (Change c : changes) {
            if (c.after() == null) continue;
            if (c.after().nodes().size() > maxNodes()) {
                refuse(player, "A pathway holds at most " + maxNodes() + " nodes.");
                return;
            }
            if (PathSpline.of(c.after()).length() >= PathSpline.MAX_LENGTH) {
                refuse(player, "A pathway runs at most " + (int) PathSpline.MAX_LENGTH + " blocks.");
                return;
            }
        }
        List<Step> steps = new ArrayList<>(changes.size());
        for (Change c : changes) steps.add(new Step(c.id(), c.before()));
        remember(player, level, label, steps);
        for (Change c : changes) {
            if (c.after() == null) {
                store.remove(c.id());
                PathwaySync.removed(level, c.id());
            } else {
                store.put(c.after());
                PathwaySync.changed(level, c.after());
                RouteEvents.edited(level, c.after());
            }
        }
        history(player);
    }

    @Nullable
    private static List<Change> lineEdit(ServerPlayer player, ServerLevel level, PathwayStore store, Pathway cur,
                                         SurveyorEditPacket p) {
        switch (p.op()) {
            case SurveyorEditPacket.SPLIT -> {
                PathNode n = cur.node(p.node());
                if (n == null) return null;
                if (!cur.closed() && !createAllowed(player, store)) return null;
                UUID id = UUID.randomUUID();
                PathEdits.Split s = PathEdits.split(cur, p.node(), id, uniqueName(store, cur.name() + " 2"));
                if (s == null) {
                    refuse(player, cur.label(p.node()) + " is an end of \"" + cur.name() + "\": there is nothing to cut there.");
                    return null;
                }
                List<Change> out = new ArrayList<>();
                out.add(new Change(cur.id(), cur, s.first()));
                if (s.second() != null) {
                    out.add(new Change(id, null, s.second()));
                    tell(player, "Cut \"" + cur.name() + "\" at " + cur.label(p.node()) + ": \"" + s.second().name()
                            + "\" goes on from there. Vehicles on it keep the first part. Undo: sneak and left-click with the staff.");
                } else {
                    tell(player, "Opened the loop \"" + cur.name() + "\" at " + cur.label(p.node())
                            + ": it starts and ends there now, one way. Undo: sneak and left-click with the staff.");
                }
                return out;
            }
            case SurveyorEditPacket.JOIN -> {
                int k = cur.indexOf(p.node());
                if (k < 0) return null;
                if (cur.closed() || (k != 0 && k != cur.nodes().size() - 1)) {
                    refuse(player, "Join from an end of an open line: " + cur.label(p.node()) + " is not one.");
                    return null;
                }
                PathNode end = cur.nodes().get(k);
                PathEdits.End aEnd = k == 0 && cur.nodes().size() > 1 ? PathEdits.End.FIRST : PathEdits.End.LAST;
                Pathway best = null;
                PathEdits.End bestEnd = PathEdits.End.FIRST;
                double bestD = PathEdits.JOIN_REACH;
                for (Pathway o : store.all()) {
                    if (o.id().equals(cur.id()) || o.closed() || o.nodes().isEmpty() || !mayEdit(player, o)) continue;
                    for (PathEdits.End e : PathEdits.End.values()) {
                        PathNode on = e == PathEdits.End.FIRST ? o.nodes().get(0) : o.nodes().get(o.nodes().size() - 1);
                        double d = Math.hypot(on.x() - end.x(), on.z() - end.z());
                        if (d <= bestD && Math.abs(on.y() - end.y()) <= 3.0) {
                            bestD = d;
                            best = o;
                            bestEnd = e;
                        }
                    }
                }
                if (best == null) {
                    refuse(player, "No other line you may edit ends within " + (int) PathEdits.JOIN_REACH + " blocks of "
                            + cur.label(p.node()) + ".");
                    return null;
                }
                if (PathwayTraffic.vehiclesOn(level, best.id()) > 0) {
                    refuse(player, "Vehicles drive \"" + best.name() + "\": unassign them before it is joined into \"" + cur.name() + "\".");
                    return null;
                }
                Pathway joined = PathEdits.join(cur, aEnd, best, bestEnd);
                if (joined == null) return null;
                tell(player, "Joined \"" + best.name() + "\" onto \"" + cur.name() + "\" at " + cur.label(p.node())
                        + ": one line of " + joined.nodes().size() + " nodes. Undo: sneak and left-click with the staff.");
                return List.of(new Change(cur.id(), cur, joined), new Change(best.id(), best, null));
            }
            case SurveyorEditPacket.DUPLICATE -> {
                if (!createAllowed(player, store) || cur.nodes().isEmpty()) return null;

                double dx = PathEdits.DUPLICATE_OFFSET;
                double dz = 0.0;
                if (cur.nodes().size() >= 2) {
                    PathNode a = cur.nodes().get(0);
                    PathNode b = cur.nodes().get(1);
                    double len = Math.hypot(b.x() - a.x(), b.z() - a.z());
                    if (len > 1e-6) {
                        dx = -(b.z() - a.z()) / len * PathEdits.DUPLICATE_OFFSET;
                        dz = (b.x() - a.x()) / len * PathEdits.DUPLICATE_OFFSET;
                    }
                }
                Pathway copy = PathEdits.duplicate(cur, UUID.randomUUID(), uniqueName(store, cur.name() + " copy"),
                        player.getUUID(), store.nextColour(player.getUUID()), dx, dz);
                PathwaySync.select(player, copy.id());
                tell(player, "Made \"" + copy.name() + "\" beside \"" + cur.name() + "\": move it with the staff's radial menu (Move line).");
                return List.of(new Change(copy.id(), null, copy));
            }
            default -> {
                return null;
            }
        }
    }

    @Nullable
    private static Pathway edit(ServerPlayer player, ServerLevel level, PathwayStore store, @Nullable Pathway cur,
                                SurveyorEditPacket p) throws IOException {
        switch (p.op()) {
            case SurveyorEditPacket.CREATE -> {
                if (!withinReach(player, p.x(), p.z()) || !createAllowed(player, store)) return null;
                Pathway created = Pathway.create(UUID.randomUUID(), defaultName(store, player), store.nextColour(player.getUUID()),
                        player.getUUID()).append(p.x(), p.y(), p.z(), false);
                PathwaySync.select(player, created.id());
                return created;
            }
            case SurveyorEditPacket.APPEND -> {
                if (!withinReach(player, p.x(), p.z())) return null;
                return cur.append(p.x(), p.y(), p.z(), p.a() == 1);
            }
            case SurveyorEditPacket.APPEND_STROKE -> {
                List<double[]> pts = decodePoints(p.data());
                if (pts.isEmpty()) return null;
                for (double[] pt : pts) if (!withinReach(player, pt[0], pt[2])) return null;
                List<double[]> kept = StrokeSimplify.simplify(pts, StrokeSimplify.EPSILON, StrokeSimplify.MIN_SPACING);
                if (cur == null) {
                    if (!createAllowed(player, store)) return null;
                    Pathway created = Pathway.create(UUID.randomUUID(), defaultName(store, player), store.nextColour(player.getUUID()),
                            player.getUUID()).appendAll(kept);
                    PathwaySync.select(player, created.id());
                    return created;
                }

                if (p.a() == 1) return cur.prependOutward(kept);
                return cur.appendAll(kept);
            }
            case SurveyorEditPacket.CLOSE_LOOP -> {
                if (cur.nodes().size() < 3) {
                    refuse(player, "A loop needs at least three nodes.");
                    return null;
                }
                return cur.kind() == PathKind.LOOP ? null : cur.withKind(PathKind.LOOP);
            }
            case SurveyorEditPacket.END_ONE_WAY -> {
                if (!withinReach(player, p.x(), p.z())) return null;
                Pathway ended = cur.append(p.x(), p.y(), p.z(), false);
                return ended.kind() == PathKind.ONE_WAY ? ended : ended.withKind(PathKind.ONE_WAY);
            }
            case SurveyorEditPacket.MOVE_NODE -> {
                if (cur.node(p.node()) == null || !finite(p)) return null;
                return cur.move(p.node(), p.x(), p.y(), p.z());
            }
            case SurveyorEditPacket.INSERT_AFTER -> {
                int at = cur.indexOf(p.node());
                if (at < 0 || !finite(p)) return null;
                if (at == cur.nodes().size() - 1 && !cur.closed()) return cur.append(p.x(), p.y(), p.z(), false);
                return cur.insertAfter(at, p.x(), p.y(), p.z());
            }
            case SurveyorEditPacket.DELETE_NODE -> {
                if (cur.node(p.node()) == null) return null;
                if (cur.nodes().size() == 1) {
                    return deletePathway(player, level, store, cur) ? null : null;
                }
                return cur.delete(p.node());
            }
            case SurveyorEditPacket.SET_SPEED -> {
                if (cur.node(p.node()) == null) return null;
                double v = Double.isFinite(p.x()) ? Math.max(0.0, Math.min(PathNode.MAX_SPEED, Math.round(p.x() * 2.0) / 2.0)) : 0.0;
                return cur.update(p.node(), n -> n.withSpeedLimit(v));
            }
            case SurveyorEditPacket.STEP_SPEED -> {
                PathNode n = cur.node(p.node());
                if (n == null) return null;
                double v = stepSpeed(n.speedLimit() > 0 ? n.speedLimit() : cur.cruise(), p.a(), cur.cruise());
                return cur.update(p.node(), m -> m.withSpeedLimit(v));
            }
            case SurveyorEditPacket.SET_CORNER -> {
                if (cur.node(p.node()) == null) return null;
                return cur.update(p.node(), n -> n.withCorner(Corner.byOrdinal(p.a())));
            }
            case SurveyorEditPacket.STEP_CORNER -> {
                if (cur.node(p.node()) == null) return null;
                return cur.update(p.node(), n -> n.withCorner(n.corner().next(p.a())));
            }
            case SurveyorEditPacket.STEP_STOP -> {
                PathNode n = cur.node(p.node());
                if (n == null) return null;
                Stop next = Stop.quickStep(n.stop(), p.a());
                if (next == Stop.QUICK_UNCHANGED) {
                    refuse(player, cur.label(n.id()) + " has rules of its own: change them on its card (right-click it).");
                    return null;
                }
                if (next == null && n.stop() == null) return null;
                player.displayClientMessage(Component.literal(next == null ? cur.label(n.id()) + ": no stop"
                        : cur.label(n.id()) + ": stop " + RouteWords.rules(next)).withStyle(ChatFormatting.GRAY), true);
                return cur.update(p.node(), m -> m.withStop(next));
            }
            case SurveyorEditPacket.SET_NODE_NAME -> {
                if (cur.node(p.node()) == null) return null;
                return cur.update(p.node(), n -> n.withName(p.text().strip()));
            }
            case SurveyorEditPacket.SET_STOP -> {
                if (cur.node(p.node()) == null) return null;
                Stop stop = p.data().length == 0 ? null : PathwayCodec.decodeStop(p.data());
                return cur.update(p.node(), n -> n.withStop(stop));
            }
            case SurveyorEditPacket.SET_NAME -> {
                String name = p.text().strip();
                return name.isEmpty() ? null : cur.withName(name);
            }
            case SurveyorEditPacket.SET_COLOUR -> {
                return cur.withColour(p.a());
            }
            case SurveyorEditPacket.SET_KIND -> {
                PathKind k = PathKind.byOrdinal(p.a());
                if (k == PathKind.LOOP && cur.nodes().size() < 3) {
                    refuse(player, "A loop needs at least three nodes.");
                    return null;
                }
                return k == cur.kind() ? null : cur.withKind(k);
            }
            case SurveyorEditPacket.SET_CRUISE -> {
                return cur.withCruise(Math.round(p.x() * 2.0) / 2.0);
            }
            case SurveyorEditPacket.SET_COMFORT -> {
                return cur.withComfort(Comfort.byOrdinal(p.a()));
            }
            case SurveyorEditPacket.SET_SHARED -> {
                if (!cur.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
                    refuse(player, "Only the pathway's owner can share it.");
                    return null;
                }
                return cur.withShared(p.a() != 0);
            }
            case SurveyorEditPacket.REVERSE -> {
                return cur.reversed();
            }
            case SurveyorEditPacket.DELETE_PATHWAY -> {
                deletePathway(player, level, store, cur);
                return null;
            }
            case SurveyorEditPacket.FAIR -> {
                return fair(player, level, cur);
            }
            case SurveyorEditPacket.FIX -> {
                PathSurvey.Fix[] all = PathSurvey.Fix.values();
                PathSurvey.Fix fix = p.a() >= 0 && p.a() < all.length ? all[p.a()] : PathSurvey.Fix.NONE;
                return switch (fix) {
                    case MOVE_NODE -> moveToDryGround(player, level, cur, p.node());
                    case WIDEN -> widenBend(player, level, cur, p.x());
                    case FAIR -> fair(player, level, cur);
                    case AMPHIBIOUS -> cur.amphibious() ? null : cur.withAmphibious(true);
                    default -> null;
                };
            }
            case SurveyorEditPacket.SET_AMPHIBIOUS -> {
                boolean on = p.a() != 0;
                return on == cur.amphibious() ? null : cur.withAmphibious(on);
            }
            case SurveyorEditPacket.SIMPLIFY -> {
                if (cur.nodes().size() < 3) {
                    refuse(player, "A line of fewer than three nodes has nothing to simplify.");
                    return null;
                }
                PathFairing.Result r = PathFairing.fair(cur, SIMPLIFY_TOLERANCE);
                tell(player, String.format(Locale.ROOT, "Simplified \"%s\": %d nodes to %d, within %.2f b of the old line."
                        + " Undo: sneak and left-click with the staff.", cur.name(), r.before(), r.after(), r.worstDeviation()));
                return r.pathway();
            }
            case SurveyorEditPacket.SMOOTH -> {
                List<double[]> stroke = decodePoints(p.data());
                double radius = Double.isFinite(p.x()) && p.x() > 0.0 ? Math.min(12.0, p.x()) : PathEdits.BRUSH_RADIUS;
                Pathway smoothed = PathEdits.smooth(cur, stroke, radius);
                if (smoothed == cur) {
                    refuse(player, "Nothing under the brush to smooth: sweep it along the line's shape points (named nodes,"
                            + " stops, limits and corners stay where they are).");
                    return null;
                }
                return smoothed;
            }
            case SurveyorEditPacket.MOVE_LINE -> {
                if (!finite(p) || Math.abs(p.x()) > MAX_MOVE || Math.abs(p.y()) > MAX_MOVE || Math.abs(p.z()) > MAX_MOVE) return null;
                int n = cur.nodes().size();
                if (n == 0) return null;
                int from = p.node() < 0 ? 0 : cur.indexOf(p.node());
                int to = p.node() < 0 ? n - 1 : cur.indexOf(p.a());
                if (from < 0 || to < 0) return null;
                if (!cur.closed() && to < from) {
                    int t = from;
                    from = to;
                    to = t;
                }
                return PathEdits.translate(cur, from, to, p.x(), p.y(), p.z());
            }
            case SurveyorEditPacket.MAKE_JUNCTION -> {
                if (cur.node(p.node()) == null) return null;
                PathEdits.Meeting m = PathEdits.nearestMeeting(cur, p.node(), new ArrayList<>(store.all()));
                if (m == null) {
                    refuse(player, "No other line has a node within " + (int) PathEdits.JUNCTION_REACH + " blocks of "
                            + cur.label(p.node()) + ": bring the two lines close, then make the junction.");
                    return null;
                }
                Pathway other = store.get(m.pathway());
                tell(player, cur.label(p.node()) + " now stands on " + (other == null ? "another line" : other.label(m.nodeId())
                        + " of \"" + other.name() + "\"") + ": vehicles change lines there where the two run within 60 degrees"
                        + " of each other.");
                return cur.move(p.node(), m.x(), m.y(), m.z());
            }
            default -> {
                return null;
            }
        }
    }

    static String label(SurveyorEditPacket p, @Nullable Pathway cur) {
        String line = cur == null ? "a new line" : cur.name();
        String node = cur == null || p.node() < 0 || cur.node(p.node()) == null ? "a node" : cur.label(p.node());
        String at = node + ", " + line;
        return switch (p.op()) {
            case SurveyorEditPacket.CREATE -> "New line";
            case SurveyorEditPacket.APPEND -> "Node on " + line;
            case SurveyorEditPacket.APPEND_STROKE -> cur == null ? "New line" : "Stroke on " + line;
            case SurveyorEditPacket.CLOSE_LOOP -> "Close " + line;
            case SurveyorEditPacket.END_ONE_WAY -> "End " + line;
            case SurveyorEditPacket.MOVE_NODE -> "Move " + at;
            case SurveyorEditPacket.INSERT_AFTER -> "Insert after " + at;
            case SurveyorEditPacket.DELETE_NODE -> "Delete " + at;
            case SurveyorEditPacket.SET_SPEED, SurveyorEditPacket.STEP_SPEED -> "Limit at " + at;
            case SurveyorEditPacket.SET_CORNER, SurveyorEditPacket.STEP_CORNER -> "Corner at " + at;
            case SurveyorEditPacket.SET_STOP, SurveyorEditPacket.STEP_STOP -> "Stop at " + at;
            case SurveyorEditPacket.SET_NODE_NAME -> "Name " + at;
            case SurveyorEditPacket.SET_NAME -> "Rename " + line;
            case SurveyorEditPacket.SET_COLOUR -> "Colour of " + line;
            case SurveyorEditPacket.SET_KIND -> "Kind of " + line;
            case SurveyorEditPacket.SET_CRUISE -> "Cruise of " + line;
            case SurveyorEditPacket.SET_COMFORT -> "Comfort of " + line;
            case SurveyorEditPacket.SET_SHARED -> "Sharing of " + line;
            case SurveyorEditPacket.REVERSE -> "Reverse " + line;
            case SurveyorEditPacket.DELETE_PATHWAY -> "Delete " + line;
            case SurveyorEditPacket.FAIR -> "Fit " + line;
            case SurveyorEditPacket.FIX -> "Fix on " + line;
            case SurveyorEditPacket.SET_AMPHIBIOUS -> "Amphibious " + line;
            case SurveyorEditPacket.SPLIT -> "Split at " + at;
            case SurveyorEditPacket.JOIN -> "Join at " + at;
            case SurveyorEditPacket.DUPLICATE -> "Duplicate " + line;
            case SurveyorEditPacket.SIMPLIFY -> "Simplify " + line;
            case SurveyorEditPacket.SMOOTH -> "Smooth " + line;
            case SurveyorEditPacket.MOVE_LINE -> p.node() < 0 ? "Move " + line : "Move a stretch of " + line;
            case SurveyorEditPacket.MAKE_JUNCTION -> "Junction at " + at;
            default -> "Edit " + line;
        };
    }

    @Nullable
    private static Pathway fair(ServerPlayer player, ServerLevel level, Pathway cur) {
        if (cur.nodes().size() < 3) {
            refuse(player, "A line of fewer than three nodes has nothing to fair.");
            return null;
        }
        Object[] agile = PathwaySurveyor.leastAgile(level, cur);
        double own = (double) agile[0];
        PathFairing.Drivable d = PathFairing.fairFor(cur, own, PathwaySurveyor.groundOk(level, cur));
        PathFairing.Result r = d.fair();
        StringBuilder msg = new StringBuilder(String.format(Locale.ROOT, "Faired \"%s\": %d nodes to %d, within %.1f b of the old line",
                cur.name(), r.before(), d.pathway().nodes().size(), r.worstDeviation()));
        if (own > 0.0) {
            if (d.opened() > 0) msg.append(String.format(Locale.ROOT, ", %d bend%s opened for %s", d.opened(), d.opened() == 1 ? "" : "s", agile[1]));
            if (!d.left().isEmpty()) msg.append(String.format(Locale.ROOT, ", %d still too tight (see the Board)", d.left().size()));
        }
        tell(player, msg + ". Undo: sneak and left-click with the staff.");
        return d.pathway();
    }

    @Nullable
    private static Pathway moveToDryGround(ServerPlayer player, ServerLevel level, Pathway cur, int nodeId) {
        PathNode n = cur.node(nodeId);
        if (n == null) return null;
        String label = cur.label(nodeId);
        double[] to = PathwaySurveyor.dryPlace(level, cur, nodeId);
        if (to == null) {
            refuse(player, "No dry, level ground within " + PathwaySurveyor.MOVE_REACH + " blocks of " + label
                    + " clears the line: move it on the Board, or tick Amphibious.");
            return null;
        }
        tell(player, String.format(Locale.ROOT, "Moved %s %.1f blocks to dry, level ground. Undo: sneak and left-click with the staff.",
                label, Math.hypot(to[0] - n.x(), to[2] - n.z())));
        return cur.move(nodeId, to[0], to[1], to[2]);
    }

    @Nullable
    private static Pathway widenBend(ServerPlayer player, ServerLevel level, Pathway cur, double s) {
        if (!Double.isFinite(s)) return null;
        Object[] agile = PathwaySurveyor.leastAgile(level, cur);
        double own = (double) agile[0];
        if (own <= 0.0) {
            refuse(player, "No vehicle drives this pathway now: a bend is opened for the one that will.");
            return null;
        }
        PathFairing.Widened w = PathFairing.openBend(cur, s, own, PathwaySurveyor.groundOk(level, cur));
        if (w == null) {
            refuse(player, "That bend cannot be opened for " + agile[1] + " here: no room on dry, level ground, or its legs"
                    + " are too close. Move its nodes on the Board.");
            return null;
        }
        double shift = PathFairing.shift(PathSpline.of(cur), PathSpline.of(w.pathway()));
        tell(player, String.format(Locale.ROOT, "Opened the bend to radius %.1f for %s; the line moves up to %.1f blocks there."
                + " Undo: sneak and left-click with the staff.", w.radius(), agile[1], shift));
        return w.pathway();
    }

    private static void tell(ServerPlayer player, String what) {
        player.displayClientMessage(Component.literal(what).withStyle(ChatFormatting.GRAY), false);
    }

    static double stepSpeed(double from, int direction, double cruise) {
        double step = from < 4.0 || (from == 4.0 && direction < 0) ? 0.5 : 1.0;
        double v = Math.round((from + Math.signum(direction) * step) * 2.0) / 2.0;
        if (v < 1.0) return 1.0;

        if (v > cruise) return 0.0;
        return Math.min(PathNode.MAX_SPEED, v);
    }

    private static boolean deletePathway(ServerPlayer player, ServerLevel level, PathwayStore store, Pathway cur) {
        if (PathwayTraffic.vehiclesOn(level, cur.id()) > 0) {
            refuse(player, "Vehicles still drive \"" + cur.name() + "\": unassign them first.");
            return false;
        }
        remember(player, level, "Delete " + cur.name(), List.of(new Step(cur.id(), cur)));
        store.remove(cur.id());
        PathwaySync.removed(level, cur.id());
        player.displayClientMessage(Component.literal("Deleted \"" + cur.name() + "\". Sneak and left-click with the staff to undo.")
                .withStyle(ChatFormatting.GRAY), true);
        history(player);
        return true;
    }

    // all or nothing
    private static void undo(ServerPlayer player, ServerLevel level, PathwayStore store, Map<UUID, Deque<Undo>> from,
                             Map<UUID, Deque<Undo>> to, String done, String none) {
        Deque<Undo> stack = from.get(player.getUUID());
        Undo u = stack == null ? null : stack.pollFirst();
        String dim = level.dimension().location().toString();
        while (u != null && !u.dimension().equals(dim)) u = stack.pollFirst();
        if (u == null) {
            refuse(player, none);
            return;
        }
        for (Step s : u.steps()) {
            Pathway now = store.get(s.pathway());
            if (now != null && !mayEdit(player, now)) {
                refuse(player, "\"" + now.name() + "\" is not yours to edit any more.");
                return;
            }
            if (s.before() == null && now != null && PathwayTraffic.vehiclesOn(level, now.id()) > 0) {
                refuse(player, "Vehicles drive \"" + now.name() + "\": unassign them before it is taken away.");
                return;
            }
        }
        List<Step> back = new ArrayList<>(u.steps().size());
        for (Step s : u.steps()) back.add(new Step(s.pathway(), store.get(s.pathway())));
        for (Step s : u.steps()) restore(level, store, s);
        push(to, player, new Undo(dim, u.label(), back));
        player.displayClientMessage(Component.literal(done + u.label()).withStyle(ChatFormatting.GRAY), true);
        history(player);
    }

    private static void restore(ServerLevel level, PathwayStore store, Step s) {
        Pathway now = store.get(s.pathway());
        if (s.before() == null) {
            if (now == null) return;
            store.remove(now.id());
            PathwaySync.removed(level, now.id());
            return;
        }
        long version = Math.max(s.before().version(), now == null ? 0L : now.version()) + 1L;
        Pathway b = s.before();
        Pathway restored = new Pathway(b.id(), b.name(), b.colour(), b.kind(), b.owner(), b.shared(), b.cruise(), b.comfort(),
                version, Math.max(b.nextNodeId(), now == null ? 0 : now.nextNodeId()), b.nodes(), b.flags());
        store.put(restored);
        PathwaySync.changed(level, restored);
        RouteEvents.edited(level, restored);
    }

    private static void remember(ServerPlayer player, ServerLevel level, String label, List<Step> steps) {
        push(UNDO, player, new Undo(level.dimension().location().toString(), label, List.copyOf(steps)));
        REDO.remove(player.getUUID());
    }

    private static void push(Map<UUID, Deque<Undo>> stacks, ServerPlayer player, Undo u) {
        Deque<Undo> stack = stacks.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        stack.addFirst(u);
        while (stack.size() > UNDO_DEPTH) stack.pollLast();
    }

    public static final int HISTORY_SHOWN = 3;

    private static void history(ServerPlayer player) {
        PathwaySync.history(player, names(UNDO.get(player.getUUID())), names(REDO.get(player.getUUID())));
    }

    private static List<String> names(@Nullable Deque<Undo> stack) {
        List<String> out = new ArrayList<>(HISTORY_SHOWN);
        if (stack == null) return out;
        for (Undo u : stack) {
            if (out.size() >= HISTORY_SHOWN) break;
            out.add(u.label());
        }
        return out;
    }

    public static void forget(UUID player) {
        UNDO.remove(player);
        REDO.remove(player);
    }

    public static boolean mayEdit(ServerPlayer player, Pathway p) {
        return p.owner().equals(player.getUUID()) || p.shared() || player.hasPermissions(2);
    }

    private static boolean createAllowed(ServerPlayer player, PathwayStore store) {
        int max = maxPathways();
        if (store.countOwnedBy(player.getUUID()) >= max && !player.hasPermissions(2)) {
            refuse(player, "You have " + max + " pathways in this dimension, the most allowed: delete one on the Board first.");
            return false;
        }
        return true;
    }

    private static boolean withinReach(ServerPlayer player, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
        int view = player.server.getPlayerList().getViewDistance();
        double reach = Math.max(4, view) * 16.0 + 16.0;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        if (dx * dx + dz * dz <= reach * reach) return true;
        refuse(player, "Too far away to place a node: within " + (int) reach + " blocks.");
        return false;
    }

    private static boolean finite(SurveyorEditPacket p) {
        return Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                && Math.abs(p.x()) < 3.0e7 && Math.abs(p.z()) < 3.0e7 && Math.abs(p.y()) < 4096;
    }

    private static String defaultName(PathwayStore store, ServerPlayer player) {
        int n = store.countOwnedBy(player.getUUID()) + 1;
        String name = "Pathway " + n;
        while (nameTaken(store, name)) name = "Pathway " + (++n);
        return name;
    }

    private static boolean nameTaken(PathwayStore store, String name) {
        for (Pathway p : store.all()) if (p.name().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static String uniqueName(PathwayStore store, String wish) {
        String base = wish.length() > Pathway.MAX_NAME - 4 ? wish.substring(0, Pathway.MAX_NAME - 4) : wish;
        String name = base;
        int n = 2;
        while (nameTaken(store, name)) name = base + " (" + (n++) + ")";
        return name;
    }

    static int maxNodes() {
        try {
            return Math.min(Pathway.MAX_NODES, ModConfiguration.COMMON.pathwayMaxNodes.get());
        } catch (RuntimeException e) {
            return Pathway.MAX_NODES;
        }
    }

    static int maxPathways() {
        try {
            return ModConfiguration.COMMON.pathwayMaxPerPlayer.get();
        } catch (RuntimeException e) {
            return 64;
        }
    }

    static void refuse(ServerPlayer player, String why) {
        player.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GOLD), true);
    }

    public static byte[] encodePoints(List<double[]> points) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + points.size() * 24);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            int n = Math.min(points.size(), (SurveyorEditPacket.MAX_DATA - 4) / 24);
            out.writeInt(n);
            for (int i = 0; i < n; i++) {
                double[] p = points.get(i);
                out.writeDouble(p[0]);
                out.writeDouble(p[1]);
                out.writeDouble(p[2]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static List<double[]> decodePoints(byte[] data) throws IOException {
        List<double[]> out = new ArrayList<>();
        if (data.length < 4) return out;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int n = in.readInt();
            if (n < 0 || n > (SurveyorEditPacket.MAX_DATA - 4) / 24) throw new IOException("stroke of " + n + " points");
            for (int i = 0; i < n; i++) {
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
                out.add(new double[]{x, y, z});
            }
        }
        return out;
    }
}
