package com.createstats.vccmk4.air;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.Vcc4Scanner;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.PropLearner;
import com.createstats.vccmk4.air.core.PropModel;
import com.createstats.vccmk4.air.core.ThrustSplit;
import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.V3;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// learned, not assumed. gears flip and scale
final class AerialProps {
    static final int CALIBRATION_TICKS = 20;
    static final int CALIBRATION_RPM = 3;

    static final int STARVED_TICKS = 20;

    static final int DEADBAND_RPM = 2;

    static final class Governor {
        final BlockPos pos;
        final List<Vcc4Scanner.Prop> props = new ArrayList<>();
        String name = "";
        int commanded;
        boolean owned;
        boolean foreign;
        int starved;

        double gain;
        double flow;
        double maxRpm = PropModel.DEFAULT_MAX_RPM;
        double speed;
        double push;
        V3 direction = V3.UNIT_Y;
        double[] column = new double[Axis6.COUNT];
        double lo;
        double hi;

        AerialSetup.EngineJob job = AerialSetup.EngineJob.AUTO;
        boolean reverse = true;
        int limit = 100;

        boolean over;
        boolean starvedLong;

        Governor(BlockPos pos) {
            this.pos = pos;
        }
    }

    record Applied(double forward, double up, double yaw) {
        static final Applied NONE = new Applied(0.0, 0.0, 0.0);
    }

    private final Map<Long, PropLearner> learned = new HashMap<>();
    private final List<Governor> governors = new ArrayList<>();
    private final List<String> loose = new ArrayList<>();
    private int directJets;
    private int calibration;

    private int changes;

    void rescan(Vcc4Scanner.Scan scan) {
        Map<BlockPos, Governor> byPos = new LinkedHashMap<>();
        Map<BlockPos, Governor> old = new HashMap<>();
        for (Governor g : governors) old.put(g.pos, g);
        loose.clear();
        directJets = 0;
        int index = 0;
        for (Vcc4Scanner.Prop p : scan.props()) {
            index++;
            if (p.direct()) {
                directJets++;
                continue;
            }
            BlockPos gp = scan.governorFor(p.pos());
            if (gp == null) {
                loose.add("J" + index);
                continue;
            }
            Governor g = byPos.computeIfAbsent(gp, k -> {
                Governor was = old.get(k);
                Governor n = new Governor(k);
                if (was != null) {
                    n.commanded = was.commanded;
                    n.owned = was.owned;
                }
                return n;
            });
            g.props.add(p);
        }
        governors.clear();
        governors.addAll(byPos.values());
        for (int i = 0; i < governors.size(); i++) governors.get(i).name = "G" + (i + 1);
    }

    List<Governor> governors() {
        return governors;
    }

    List<String> loose() {
        return loose;
    }

    int directJets() {
        return directJets;
    }

    void startCalibration() {
        calibration = CALIBRATION_TICKS;
    }

    List<ThrustSplit.Actuator> actuators(BodyFrame frame, V3 airBody, double pressure, double maxRpm, AerialSetup setup) {
        List<ThrustSplit.Actuator> out = new ArrayList<>(governors.size());
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        for (Governor g : governors) {
            AerialSetup.Engine engine = setup.engine(g.pos.asLong());
            g.job = engine.job();
            g.reverse = engine.reverse();
            g.limit = engine.limit();
            double total = 0.0;
            double flowSum = 0.0;
            double ratio = 0.0;
            List<V3> at = new ArrayList<>();
            List<V3> dirs = new ArrayList<>();
            List<Double> gains = new ArrayList<>();
            V3 mean = V3.ZERO;
            for (Vcc4Scanner.Prop prop : g.props) {
                PropLearner l = learned.get(prop.pos().asLong());

                double gearing = l != null && Double.isFinite(l.ratio) ? l.ratio : 1.0;
                double gain = l != null && l.known() ? l.gain : priorGain(prop) * gearing;
                double flow = l != null && Double.isFinite(l.flow) ? l.flow : priorFlow(prop) * gearing;
                int sign = l != null && l.signSeen ? l.sign : 1;
                if (l != null && Double.isFinite(l.ratio)) ratio = Math.max(ratio, l.ratio);
                V3 dir = v3(prop.axis()).normalized().scale(sign);
                at.add(v3(prop.centre()));
                dirs.add(dir);
                gains.add(gain);
                total += gain;
                flowSum += gain * flow;
                mean = mean.add(dir.scale(gain));
            }
            double[] shares = new double[gains.size()];
            for (int i = 0; i < shares.length; i++) shares[i] = total > 0.0 ? gains.get(i) / total : 1.0 / shares.length;
            g.gain = total;
            g.flow = total > 0.0 ? flowSum / total : 0.0;
            g.direction = mean.length() > 1.0e-9 ? mean.normalized() : V3.UNIT_Y;
            g.speed = airBody.dot(g.direction);
            g.maxRpm = Math.min(maxRpm, ratio > 1.0 ? maxRpm / ratio : maxRpm) * g.limit / 100.0;
            g.column = mask(ThrustSplit.column(frame, at, dirs, shares), g.job);
            if (g.job == AerialSetup.EngineJob.OFF) {
                g.hi = 0.0;
                g.lo = 0.0;
            } else {
                g.hi = PropModel.maxForce(1, g.gain, g.flow, g.speed, p, g.maxRpm);
                g.lo = g.reverse ? -PropModel.maxForce(-1, g.gain, g.flow, g.speed, p, g.maxRpm) : 0.0;
            }
            out.add(new ThrustSplit.Actuator(g.column, g.lo, g.hi));
        }
        return out;
    }

    static double[] mask(double[] column, AerialSetup.EngineJob job) {
        if (job == AerialSetup.EngineJob.AUTO) return column;
        double[] out = new double[column.length];
        int keep = switch (job) {
            case PUSH -> Axis6.FORWARD.index();
            case LIFT -> Axis6.UP.index();
            default -> -1;
        };
        if (keep >= 0 && keep < column.length) out[keep] = column[keep];
        return out;
    }

    double[] forwardFade(double pressure) {
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double f0 = 0.0;
        double fade = 0.0;
        for (Governor g : governors) {
            double c = Math.abs(g.column[Axis6.FORWARD.index()]);
            f0 += c * g.gain * p * g.maxRpm;
            if (g.flow > 1.0e-9) fade += c * g.gain * p / g.flow;
        }
        return new double[] {f0, fade};
    }

    Applied drive(ServerLevel level, ThrustSplit.Result split, double pressure, BlockPos console, UUID id,
                  List<String> problems) {
        double p = pressure > 0.0 && Double.isFinite(pressure) ? pressure : 1.0;
        double fwd = 0.0;
        double up = 0.0;
        double yaw = 0.0;
        for (int i = 0; i < governors.size(); i++) {
            Governor g = governors.get(i);
            if (g.job == AerialSetup.EngineJob.OFF) {
                releaseOne(level, g, id);
                continue;
            }
            double push = i < split.force().length ? split.force()[i] : 0.0;
            g.push = push;
            fwd += push * g.column[Axis6.FORWARD.index()];
            up += push * g.column[Axis6.UP.index()];
            yaw += push * g.column[Axis6.YAW.index()];
            int rpm = (int) Math.round(PropModel.rpmFor(push, g.gain, g.flow, g.speed, p, g.maxRpm));
            if (rpm == 0 && calibration > 0 && !known(g)) rpm = CALIBRATION_RPM;
            if (rpm != 0 && Math.abs(rpm - g.commanded) < DEADBAND_RPM && Integer.signum(rpm) == Integer.signum(g.commanded)) {
                rpm = g.commanded;
            }
            command(level, g, rpm, console, id, problems);
        }
        if (calibration > 0) calibration--;
        return new Applied(fwd, up, yaw);
    }

    private void command(ServerLevel level, Governor g, int rpm, BlockPos console, UUID id, List<String> problems) {
        if (!(SableBridge.getBlockEntityOnContraption(level, g.pos) instanceof RpmGovernorBE governor)) return;
        int clamped = Math.max(-(int) PropModel.DEFAULT_MAX_RPM, Math.min((int) PropModel.DEFAULT_MAX_RPM, rpm));
        if (!governor.setCommand(console, id, clamped)) {
            g.foreign = true;
            g.owned = false;
            problems.add(g.name + " is held by another controller (a second console, or a Flight Control Computer), so this one cannot drive it.");
            return;
        }
        g.foreign = false;
        g.owned = true;
        if (clamped != g.commanded) changes++;
        g.commanded = clamped;
        boolean noSource = !governor.hasSource() || Math.abs(governor.getSpeed()) < 1.0e-4f;
        boolean over = governor.isOverStressed();
        g.over = over;
        if (clamped == 0 || !(noSource || over)) {
            g.starved = 0;
            g.starvedLong = false;
            return;
        }
        g.starvedLong = ++g.starved >= STARVED_TICKS;
        if (!g.starvedLong) return;
        problems.add(over
                ? g.name + "'s shaft network is overstressed, so every prop on it stops: it needs more stress capacity."
                : g.name + " gets no rotation from its engine: is the engine running and its shaft connected?");
    }

    int takeChanges() {
        int n = changes;
        changes = 0;
        return n;
    }

    void release(ServerLevel level, UUID id) {
        for (Governor g : governors) releaseOne(level, g, id);
    }

    private static void releaseOne(ServerLevel level, Governor g, UUID id) {
        g.push = 0.0;
        g.starved = 0;
        g.starvedLong = false;
        if (!g.owned) return;
        if (SableBridge.getBlockEntityOnContraption(level, g.pos) instanceof RpmGovernorBE governor) {
            governor.releaseOwnership(id);
        }
        g.owned = false;
        g.commanded = 0;
    }

    boolean learn(ServerLevel level) {
        boolean changed = false;
        for (Governor g : governors) {
            if (!(SableBridge.getBlockEntityOnContraption(level, g.pos) instanceof RpmGovernorBE governor)) continue;
            int applied = governor.getAppliedRpm();
            for (Vcc4Scanner.Prop prop : g.props) {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, prop.pos());
                if (be == null) continue;

                double along = -AerialReflect.staticThrust(be);
                double flow = AerialReflect.airflow(be);
                double shaft = be instanceof KineticBlockEntity k ? k.getSpeed() : Double.NaN;
                changed |= learned.computeIfAbsent(prop.pos().asLong(), k -> new PropLearner()).observe(applied, along, flow, shaft);
            }
        }
        return changed;
    }

    boolean anyTrouble() {
        for (Governor g : governors) if (g.over || g.starvedLong) return true;
        return false;
    }

    boolean known(Governor g) {
        for (Vcc4Scanner.Prop prop : g.props) {
            PropLearner l = learned.get(prop.pos().asLong());
            if (l == null || !l.signSeen) return false;
        }
        return true;
    }

    Map<Long, PropLearner> learned() {
        return learned;
    }

    @Nullable
    PropLearner learnedAt(BlockPos pos) {
        return learned.get(pos.asLong());
    }

    static boolean bearing(Vcc4Scanner.Prop prop) {
        return prop.strength() > 1.0;
    }

    static double priorGain(Vcc4Scanner.Prop prop) {
        return bearing(prop) ? 0.2 * Math.pow(prop.strength(), 1.5) : 1.0;
    }

    static double priorFlow(Vcc4Scanner.Prop prop) {
        return bearing(prop) ? 0.05 * Math.sqrt(prop.strength()) : 0.1;
    }

    static V3 v3(Vector3d v) {
        return new V3(v.x, v.y, v.z);
    }
}
