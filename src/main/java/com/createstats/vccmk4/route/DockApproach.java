package com.createstats.vccmk4.route;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.surveyor.RouteEvents;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.StopCondition;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

// capture needs tips within 0.5 and facings within 20 degrees
final class DockApproach {
    static final double EXTEND_AT = 6.0;

    static final int BAY_REACH = 6;

    static final int CAPTURE_TICKS = 60;

    static final int TRIES = 2;
    static final double BACK_OFF = 2.5;

    static final double CAPTURE = 0.5;

    enum Phase {
        NONE, APPROACH, ARRIVED, DOCKED, RELEASING, GAVE_UP
    }

    private final StopLinks link = new StopLinks();
    private Phase phase = Phase.NONE;
    private int node = -1;
    private int tries;
    private long arrivedAt = Long.MIN_VALUE;
    private long lookedAt = Long.MIN_VALUE / 2;
    @Nullable private BlockPos ours;
    @Nullable private BlockPos bay;
    @Nullable private Couple<RedstoneLinkNetworkHandler.Frequency> key;
    private double stopAt = Double.NaN;
    private double offLine = Double.NaN;
    private String status = "";

    String status() {
        return status;
    }

    Phase phase() {
        return phase;
    }

    boolean docked(ServerLevel level) {
        BlockEntity be = ours == null || !level.isLoaded(ours) ? null : level.getBlockEntity(ours);
        return be != null && DockReflect.isLocked(be);
    }

    boolean holding(ServerLevel level) {
        if (phase == Phase.NONE || phase == Phase.GAVE_UP && !paired(level)) return false;
        BlockEntity be = ours == null || !level.isLoaded(ours) ? null : level.getBlockEntity(ours);
        return be != null && (DockReflect.hasOther(be) || !DockReflect.isRetracted(be));
    }

    private boolean paired(ServerLevel level) {
        BlockEntity be = ours == null || !level.isLoaded(ours) ? null : level.getBlockEntity(ours);
        return be != null && DockReflect.hasOther(be);
    }

    static boolean waitsForDock(@Nullable List<List<StopCondition>> rules) {
        if (rules == null) return false;
        for (List<StopCondition> col : rules) for (StopCondition c : col) if (c instanceof StopCondition.Docked) return true;
        return false;
    }

    void tick(ServerLevel level, RouteRunner runner, Pathway p, PathSpline sp, RouteHost host, int dockStop, boolean leave, long now) {
        if (dockStop < 0 || dockStop != node) {
            if (phase != Phase.NONE && !holding(level)) reset(level);
            else if (phase != Phase.NONE) link.clear(level);
            if (dockStop < 0) return;
            if (phase == Phase.NONE) {
                node = dockStop;
                tries = 0;
                phase = Phase.APPROACH;
                lookedAt = Long.MIN_VALUE / 2;
            } else {
                return;
            }
        }
        PathNode n = p.node(node);
        if (n == null) {
            reset(level);
            return;
        }
        if (now - lookedAt >= 20) {
            lookedAt = now;
            look(level, p, sp, host, n);

            int k = p.indexOf(node);
            if (Double.isFinite(stopAt) && k >= 0 && k < sp.nodeCount()) {
                double nodeS = sp.nodeS(k);
                runner.stopOffset(node, sp.closed() ? sp.signedDelta(nodeS, stopAt) : stopAt - nodeS);
            }
        }
        if (ours == null || key == null || bay == null) {
            link.clear(level);
            return;
        }
        BlockEntity mine = level.isLoaded(ours) ? level.getBlockEntity(ours) : null;
        if (mine == null) {
            ours = null;
            return;
        }
        RouteRunner.State st = runner.state();
        boolean waitingHere = st == RouteRunner.State.WAITING && runner.waitingAt() == node;
        switch (phase) {
            case APPROACH -> {
                double toStop = Double.isFinite(stopAt) ? (sp.closed() ? sp.ahead(runner.progress(), stopAt) : stopAt - runner.progress())
                        : Double.POSITIVE_INFINITY;
                if (toStop < EXTEND_AT || waitingHere) link.set(level, bay, key);
                if (DockReflect.hasOther(mine)) {
                    if (!waitingHere) runner.arriveNow(now);
                    phase = Phase.DOCKED;
                    status = "docking at " + p.label(node);
                } else if (waitingHere) {
                    phase = Phase.ARRIVED;
                    arrivedAt = now;
                    status = "at " + p.label(node) + ", reaching for the bay";
                } else {
                    status = Double.isFinite(offLine) && offLine > CAPTURE
                            ? String.format(Locale.ROOT, "the bay at %s stands %.1f b off its path: it may miss", p.label(node), offLine)
                            : "coming in to dock at " + p.label(node);
                }
            }
            case ARRIVED -> {
                link.set(level, bay, key);
                if (DockReflect.hasOther(mine)) {
                    phase = Phase.DOCKED;
                    status = "docking at " + p.label(node);
                } else if (now - arrivedAt >= CAPTURE_TICKS && DockReflect.isExtended(mine)) {
                    double by = missedBy(level, host, mine);
                    if (tries < TRIES) {
                        tries++;
                        status = String.format(Locale.ROOT, "missed the dock at %s by %.1f b: backing off to try again", p.label(node), by);
                        RouteEvents.add(level, p.id(), host.vehicleName() + " " + status);
                        link.clear(level);
                        runner.redock(BACK_OFF);
                        phase = Phase.APPROACH;
                    } else {
                        phase = Phase.GAVE_UP;
                        link.clear(level);
                        status = String.format(Locale.ROOT, "could not dock at %s: missed by %.1f b after %d tries. Check the bay lines up with the stop",
                                p.label(node), by, TRIES + 1);
                        RouteEvents.add(level, p.id(), host.vehicleName() + " " + status);
                    }
                } else if (!waitingHere && st != RouteRunner.State.APPROACHING) {
                    phase = Phase.APPROACH;
                }
            }
            case DOCKED -> {
                if (leave) {
                    phase = Phase.RELEASING;
                    link.clear(level);
                    status = "undocking at " + p.label(node);
                } else {
                    link.set(level, bay, key);
                    status = DockReflect.isLocked(mine) ? "docked at " + p.label(node) : "docking at " + p.label(node);
                }
            }
            case RELEASING -> {
                link.clear(level);
                if (!DockReflect.hasOther(mine) && DockReflect.isRetracted(mine)) status = "";
            }
            case GAVE_UP -> {
                link.clear(level);
            }
            default -> {
            }
        }
    }

    private void look(ServerLevel level, Pathway p, PathSpline sp, RouteHost host, PathNode n) {
        BlockPos centre = BlockPos.containing(n.x(), n.y(), n.z());
        BlockPos found = null;
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos q : BlockPos.betweenClosed(centre.offset(-BAY_REACH, -3, -BAY_REACH), centre.offset(BAY_REACH, 3, BAY_REACH))) {
            if (!level.isLoaded(q)) continue;
            BlockEntity be = level.getBlockEntity(q);
            if (!DockReflect.is(be)) continue;
            double d = q.distToCenterSqr(n.x(), n.y(), n.z());
            if (d < best) {
                best = d;
                found = q.immutable();
            }
        }
        bay = found;
        if (bay == null) {
            ours = null;
            status = "no Docking Connector at the bay by " + p.label(n.id());
            return;
        }
        BlockEntity bayBe = level.isLoaded(bay) ? level.getBlockEntity(bay) : null;
        if (bayBe == null) return;
        Vec3 bayTip = DockReflect.tip(bayBe);

        Object craft = host.craft();
        int[] box = RouteReflect.plotBounds(craft);
        Vector3d ref = host.referenceLocal();
        Vector3d nose = host.noseLocal();
        Vector3d right = host.rightLocal();
        int k = p.indexOf(n.id());
        double nodeS = k >= 0 && k < sp.nodeCount() ? sp.nodeS(k) : Double.NaN;
        BlockPos pick = null;
        Couple<RedstoneLinkNetworkHandler.Frequency> pickKey = null;
        double pickMiss = Double.POSITIVE_INFINITY;
        double pickS = Double.NaN;
        for (BlockEntity be : host.cargoBlocks()) {
            if (!DockReflect.is(be) || box == null) continue;
            BlockPos at = be.getBlockPos();
            if (at.getX() < box[0] || at.getX() > box[3] || at.getY() < box[1] || at.getY() > box[4] || at.getZ() < box[2] || at.getZ() > box[5]) continue;
            Couple<RedstoneLinkNetworkHandler.Frequency> rk = receiverAgainst(level, at);
            if (rk == null) continue;
            Vec3 tip = DockReflect.tip(be);
            double dx = tip.x - ref.x;
            double dy = tip.y - ref.y;
            double dz = tip.z - ref.z;
            double along = dx * nose.x + dy * nose.y + dz * nose.z;
            double across = dx * right.x + dy * right.y + dz * right.z;
            double[] fit = restingPoint(sp, nodeS, along, across, bayTip);
            if (fit[1] < pickMiss) {
                pickMiss = fit[1];
                pickS = fit[0];
                pick = at.immutable();
                pickKey = rk;
            }
        }
        if (pick == null) {
            boolean any = false;
            for (BlockEntity be : host.cargoBlocks()) any |= DockReflect.is(be);
            status = any ? "put a Redstone Link receiver against " + host.vehicleName() + "'s Docking Connector: the route powers it through that"
                    : host.vehicleName() + " has no Docking Connector to dock with";
            ours = null;
            key = null;
            return;
        }
        ours = pick;
        key = pickKey;
        stopAt = pickS;
        offLine = pickMiss;
        return;
    }

    static double[] restingPoint(PathSpline sp, double nodeS, double along, double across, Vec3 bayTip) {
        if (!Double.isFinite(nodeS)) return new double[]{Double.NaN, Double.POSITIVE_INFINITY};
        double bestS = nodeS;
        double best = Double.POSITIVE_INFINITY;
        for (double d = -6.0; d <= 6.0; d += 0.05) {
            double s = nodeS + d;
            if (!sp.closed() && (s < 0.0 || s > sp.length())) continue;
            double[] t = sp.tangent(s);

            double x = sp.x(s) + t[0] * along - t[1] * across;
            double z = sp.z(s) + t[1] * along + t[0] * across;
            double miss = Math.hypot(x - bayTip.x, z - bayTip.z);
            if (miss < best) {
                best = miss;
                bestS = s;
            }
        }
        return new double[]{sp.closed() ? sp.wrap(bestS) : bestS, best};
    }

    @Nullable
    private static Couple<RedstoneLinkNetworkHandler.Frequency> receiverAgainst(ServerLevel level, BlockPos at) {
        for (Direction d : Direction.values()) {
            BlockPos neighbour = at.relative(d);
            if (!level.isLoaded(neighbour)) continue;
            BlockEntity be = level.getBlockEntity(neighbour);
            if (!(be instanceof SmartBlockEntity smart)) continue;
            LinkBehaviour link = BlockEntityBehaviour.get(smart, LinkBehaviour.TYPE);
            if (link != null && link.isListening()) return link.getNetworkKey();
        }
        return null;
    }

    private double missedBy(ServerLevel level, RouteHost host, BlockEntity mine) {
        BlockEntity bayBe = bay == null || !level.isLoaded(bay) ? null : level.getBlockEntity(bay);
        if (bayBe == null) return Double.NaN;
        Vec3 tipLocal = DockReflect.tip(mine);
        Object craft = host.craft();
        Vector3d world = craft == null ? null : SableBridge.localToWorld(craft, new Vector3d(tipLocal.x, tipLocal.y, tipLocal.z));
        if (world == null) return Double.NaN;
        Vec3 b = DockReflect.tip(bayBe);
        return Math.sqrt((world.x - b.x) * (world.x - b.x) + (world.y - b.y) * (world.y - b.y) + (world.z - b.z) * (world.z - b.z));
    }

    double stopAt() {
        return stopAt;
    }

    int node() {
        return node;
    }

    void reset(ServerLevel level) {
        link.clear(level);
        phase = Phase.NONE;
        node = -1;
        tries = 0;
        ours = null;
        bay = null;
        key = null;
        stopAt = Double.NaN;
        offLine = Double.NaN;
        status = "";
    }
}
