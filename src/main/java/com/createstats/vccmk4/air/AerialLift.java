package com.createstats.vccmk4.air;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.air.core.AltitudeLaw;
import com.createstats.vccmk4.air.core.BurnerDither;
import com.createstats.vccmk4.air.core.GasLaw;
import com.createstats.vccmk4.air.core.PressureModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// a heater at signal 0 drops out of its balloon list. careful
final class AerialLift {
    static final int VENT_PERIOD = 4;

    static final class Group {
        final Object balloon;
        final List<BlockPos> heaters = new ArrayList<>();

        long id;

        boolean driven = true;
        final BurnerDither dither = new BurnerDither();
        boolean vent;
        String kind = "hot_air";
        double gas;
        double capacity;
        double strength = GasLaw.STRENGTH;
        double lift;
        double target;
        double change;
        double output;
        double maxGas;
        double pressure = Double.NaN;

        @Nullable Vector3d centre;
        double[] dials = new double[0];

        Group(Object balloon) {
            this.balloon = balloon;
        }

        AltitudeLaw.Cell cell(double lon) {
            return new AltitudeLaw.Cell(gas, capacity, strength, pressure, maxGas, lon);
        }

        double force(double g) {
            double p = Double.isFinite(pressure) ? pressure : 1.0;
            return Math.max(0.0, gas) * strength * p * g;
        }
    }

    private final List<BlockPos> heaters = new ArrayList<>();
    private final Map<BlockPos, Object> home = new HashMap<>();
    private final Map<Object, Group> byBalloon = new IdentityHashMap<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<BlockPos> pending = new ArrayList<>();
    private boolean driving;

    void rescan(List<BlockEntity> onBody) {
        heaters.clear();
        for (BlockEntity be : onBody) {
            if (AerialReflect.isBurner(be) || AerialReflect.isVent(be)) heaters.add(be.getBlockPos().immutable());
        }
        heaters.sort(Comparator.naturalOrder());
        home.keySet().retainAll(heaters);
    }

    List<BlockPos> heaters() {
        return heaters;
    }

    List<Group> groups() {
        return groups;
    }

    int pending() {
        return pending.size();
    }

    boolean driving() {
        return driving;
    }

    void read(ServerLevel level, @Nullable Object subLevel, PressureModel air) {
        Map<Object, Group> seen = new IdentityHashMap<>();
        List<Group> order = new ArrayList<>();
        pending.clear();
        for (BlockPos pos : heaters) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            if (be == null) continue;
            Object balloon = AerialReflect.balloonOf(be);
            if (balloon != null) home.put(pos, balloon);
            else balloon = home.get(pos);
            if (balloon == null) {
                pending.add(pos);
                continue;
            }
            Group g = seen.get(balloon);
            if (g == null) {
                g = byBalloon.get(balloon);
                if (g == null) g = new Group(balloon);
                g.heaters.clear();
                g.vent = false;
                g.kind = "hot_air";
                seen.put(balloon, g);
                order.add(g);
            }
            g.heaters.add(pos);
            if (AerialReflect.isVent(be)) {
                g.vent = true;
                g.kind = "steam";
            }
        }
        byBalloon.clear();
        byBalloon.putAll(seen);
        groups.clear();
        groups.addAll(order);
        for (Group g : groups) {
            long first = Long.MAX_VALUE;
            BlockPos firstPos = null;
            for (BlockPos pos : g.heaters) if (firstPos == null || pos.compareTo(firstPos) < 0) firstPos = pos;
            if (firstPos != null) first = firstPos.asLong();
            g.id = first;
            g.capacity = AerialReflect.capacity(g.balloon);
            g.gas = AerialReflect.filled(g.balloon);
            g.target = AerialReflect.target(g.balloon);
            g.lift = AerialReflect.lift(g.balloon);
            g.change = AerialReflect.volumeChange(g.balloon);
            g.strength = g.gas > 1.0e-6 && Double.isFinite(g.lift) ? g.lift / g.gas : GasLaw.STRENGTH;
            double[] dials = new double[g.heaters.size()];
            double sum = 0.0;
            double out = 0.0;
            for (int i = 0; i < dials.length; i++) {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, g.heaters.get(i));
                dials[i] = be == null ? 0.0 : AerialReflect.fullOutput(be);
                sum += dials[i];
                if (be != null) out += AerialReflect.gasOutput(be);
            }
            g.dials = dials;
            g.output = out;
            g.maxGas = Math.min(g.capacity, sum);
            g.centre = AerialReflect.centre(g.balloon);
            double p = Double.NaN;
            if (g.centre != null && subLevel != null) {
                Vector3d world = SableBridge.localToWorld(subLevel, new Vector3d(g.centre));
                if (world != null) {
                    p = AerialReflect.airPressure(level, world);
                    if (!Double.isFinite(p)) p = air.at(world.y);
                }
            }
            g.pressure = Double.isFinite(p) ? p : Double.NaN;
        }
    }

    void fillPressure(double atCom) {
        for (Group g : groups) if (!Double.isFinite(g.pressure)) g.pressure = atCom;
    }

    void choose(ServerLevel level, java.util.function.LongPredicate driven) {
        for (Group g : groups) {
            boolean now = driven.test(g.id);
            if (g.driven && !now && driving) handBack(level, g);
            g.driven = now;
        }
    }

    List<AltitudeLaw.Cell> cells(java.util.function.ToDoubleFunction<Group> lon) {
        List<AltitudeLaw.Cell> out = new ArrayList<>(groups.size());
        for (Group g : groups) if (g.driven) out.add(g.cell(lon.applyAsDouble(g)));
        return out;
    }

    double passiveLift(double gravity) {
        double f = 0.0;
        for (Group g : groups) if (!g.driven) f += g.force(gravity);
        return f;
    }

    void drive(ServerLevel level, double[] targets, long tick) {
        drive(level, targets, tick, true);
    }

    private void drive(ServerLevel level, double[] targets, long tick, boolean lightPending) {
        driving = true;
        int i = -1;
        for (Group g : groups) {
            if (!g.driven) continue;
            i++;
            if (i >= targets.length) break;
            if (g.vent && tick % VENT_PERIOD != 0) continue;
            int[] signals = g.dither.signals(targets[i], g.dials);
            for (int h = 0; h < signals.length; h++) {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, g.heaters.get(h));
                if (be != null && AerialReflect.signal(be) != signals[h]) AerialReflect.setSignal(be, signals[h]);
            }
        }

        for (BlockPos pos : pending) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            int want = lightPending ? 1 : 0;
            if (be != null && AerialReflect.signal(be) != want) AerialReflect.setSignal(be, want);
        }
    }

    void bank(ServerLevel level, long tick) {
        int n = 0;
        for (Group g : groups) if (g.driven) n++;
        drive(level, new double[n], tick, false);
    }

    boolean redstoneWouldLight(ServerLevel level) {
        for (Group g : groups) {
            if (!g.driven) continue;
            for (BlockPos pos : g.heaters) if (level.getBestNeighborSignal(pos) > 0) return true;
        }
        return false;
    }

    void handBack(ServerLevel level) {
        if (!driving) return;
        driving = false;
        for (BlockPos pos : heaters) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            if (be != null) AerialReflect.handBack(be);
        }
        for (Group g : groups) g.dither.reset();
    }

    private static void handBack(ServerLevel level, Group g) {
        for (BlockPos pos : g.heaters) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            if (be != null) AerialReflect.handBack(be);
        }
        g.dither.reset();
    }

    double output() {
        double s = 0.0;
        for (Group g : groups) s += g.output;
        return s;
    }

    int lit() {
        int n = 0;
        for (Group g : groups) n += g.dither.lit();
        return n;
    }
}
