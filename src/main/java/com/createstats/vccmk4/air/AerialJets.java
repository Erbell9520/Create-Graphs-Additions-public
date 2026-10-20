package com.createstats.vccmk4.air;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.Vcc4DirectJets;
import com.createstats.vccmk4.Vcc4Scanner;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.JetRamp;
import com.createstats.vccmk4.air.core.ThrustSplit;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.ThrustCommand;
import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AerialJets {
    static final class Jet {
        final Vcc4Scanner.Prop prop;
        final BlockPos pos;
        String name = "";

        @Nullable Vcc4DirectJets.Reading reading;
        V3 direction = V3.UNIT_Y;
        double[] column = new double[Axis6.COUNT];
        double hi;
        double ceiling;
        double push;
        double command;
        boolean foreign;
        AerialSetup.EngineJob job = AerialSetup.EngineJob.AUTO;
        int limit = 100;

        double level;

        double aim;

        boolean held;

        Jet(Vcc4Scanner.Prop prop) {
            this.prop = prop;
            this.pos = prop.pos();
        }

        double full() {
            return ceiling * (reading == null ? 1.0 : Math.max(1.0e-6, Math.min(1.0, reading.clear())));
        }

        JetKind kind() {
            return reading != null ? reading.kind() : prop.kind();
        }

        JetSupply supply() {
            return reading != null ? reading.supply() : JetSupply.NONE;
        }

        boolean out() {
            return supply().out();
        }
    }

    private final List<Jet> jets = new ArrayList<>();

    void rescan(Vcc4Scanner.Scan scan) {
        List<Jet> next = new ArrayList<>();
        for (Vcc4Scanner.Prop p : scan.props()) {
            if (!p.direct() || p.body() != 0) continue;
            Jet was = find(p.pos());
            Jet j = new Jet(p);
            if (was != null) {
                j.reading = was.reading;
                j.command = was.command;
                j.level = was.level;
                j.aim = was.aim;
                j.held = was.held;
            }
            next.add(j);
        }
        jets.clear();
        jets.addAll(next);
        for (int i = 0; i < jets.size(); i++) jets.get(i).name = "T" + (i + 1);
    }

    @Nullable
    private Jet find(BlockPos pos) {
        for (Jet j : jets) if (j.pos.equals(pos)) return j;
        return null;
    }

    List<Jet> jets() {
        return jets;
    }

    boolean contains(long pos) {
        for (Jet j : jets) if (j.pos.asLong() == pos) return true;
        return false;
    }

    void read(ServerLevel level) {
        for (Jet j : jets) {
            j.reading = Vcc4DirectJets.read(SableBridge.getBlockEntityOnContraption(level, j.pos));
            if (!j.held) j.level = ownThrottle(level, j);
        }
    }

    List<ThrustSplit.Actuator> actuators(BodyFrame frame, AerialSetup setup) {
        List<ThrustSplit.Actuator> out = new ArrayList<>(jets.size());
        for (Jet j : jets) {
            AerialSetup.Engine engine = setup.engines.get(j.pos.asLong());
            j.job = engine != null ? engine.job() : proposed(j);
            j.limit = engine != null ? engine.limit() : 100;
            Vector3d d = j.reading != null && j.reading.direction().lengthSquared() > 1.0e-6 ? j.reading.direction() : j.prop.axis();
            j.direction = new V3(d.x, d.y, d.z).normalized();
            double[] col = ThrustSplit.column(frame, List.of(AerialProps.v3(j.prop.centre())), List.of(j.direction), new double[] {1.0});
            j.column = AerialProps.mask(col, j.job);
            j.ceiling = j.reading == null ? positive(j.prop.strength()) : positive(j.reading.ceiling());
            double clear = j.reading == null ? 1.0 : Math.max(0.0, Math.min(1.0, j.reading.clear()));
            j.hi = j.job == AerialSetup.EngineJob.OFF ? 0.0 : j.ceiling * clear * j.limit / 100.0;
            out.add(new ThrustSplit.Actuator(j.column, 0.0, j.hi));
        }
        return out;
    }

    static AerialSetup.EngineJob proposed(Jet j) {
        return j.kind() == JetKind.BOOSTER ? AerialSetup.EngineJob.OFF : AerialSetup.EngineJob.AUTO;
    }

    List<ThrustSplit.Actuator> windowed(List<ThrustSplit.Actuator> all, int governors, Feel feel) {
        List<ThrustSplit.Actuator> out = new ArrayList<>(all);
        for (int i = 0; i < jets.size() && governors + i < out.size(); i++) {
            Jet j = jets.get(i);
            if (j.job == AerialSetup.EngineJob.OFF || j.kind() == JetKind.BOOSTER) continue;
            double full = j.full();
            if (!(full > 1.0e-9)) continue;
            ThrustSplit.Actuator a = out.get(governors + i);
            double cap = Math.max(0.0, a.hi()) / full;
            out.set(governors + i, new ThrustSplit.Actuator(a.column(), JetRamp.low(j.level, feel) * full,
                    JetRamp.high(j.level, cap, feel) * full, true));
        }
        return out;
    }

    void drive(ServerLevel level, double[] forces, UUID owner, Feel feel, List<String> problems) {
        for (int i = 0; i < jets.size(); i++) {
            Jet j = jets.get(i);
            j.push = i < forces.length ? Math.max(0.0, forces[i]) : 0.0;
            if (j.job == AerialSetup.EngineJob.OFF) {
                j.aim = Double.NaN;
                if (j.held) settleOne(level, j, owner, feel);
                else j.command = 0.0;
                j.foreign = false;
                continue;
            }
            double full = j.full();
            double asked = full > 1.0e-9 ? j.push / full : 0.0;
            j.aim = Math.max(0.0, Math.min(1.0, asked));
            if (j.kind() == JetKind.BOOSTER) {
                j.level = ThrustCommand.forKind(j.kind(), asked);
            } else {
                j.level = JetRamp.toward(j.level, asked, feel);
            }
            fire(level, j, owner, problems);
        }
    }

    private void fire(ServerLevel level, Jet j, UUID owner, @Nullable List<String> problems) {
        j.command = ThrustCommand.forKind(j.kind(), j.level);
        Vcc4DirectJets.Result r = Vcc4DirectJets.command(level, j.pos, owner, j.command);
        j.foreign = r == Vcc4DirectJets.Result.HELD;
        j.held = r == Vcc4DirectJets.Result.DRIVEN;
        if (j.foreign) {
            j.command = 0.0;
            j.level = 0.0;
            if (problems != null) problems.add(j.name + " is held by another console, so this one cannot fire it.");
        }
    }

    void settle(ServerLevel level, UUID owner, Feel feel) {
        boolean was = holding();
        for (Jet j : jets) {
            j.push = 0.0;
            j.aim = Double.NaN;
            if (j.held) settleOne(level, j, owner, feel);
        }

        if (was && !holding()) Vcc4DirectJets.releaseAll(level, owner);
    }

    private void settleOne(ServerLevel level, Jet j, UUID owner, Feel feel) {
        double own = ownThrottle(level, j);
        if (JetRamp.settled(j.level, own, feel) || j.kind() == JetKind.BOOSTER) {
            j.held = false;
            j.level = own;
            j.command = 0.0;
            return;
        }
        j.level = JetRamp.toward(j.level, own, feel);
        fire(level, j, owner, null);
    }

    private static double ownThrottle(ServerLevel level, Jet j) {
        return Math.max(0, Math.min(15, level.getBestNeighborSignal(j.pos))) / 15.0;
    }

    double[] measured() {
        double[] out = new double[Axis6.COUNT];
        for (Jet j : jets) {
            if (j.reading == null) continue;
            double f = Math.max(0.0, j.reading.force());
            for (int a = 0; a < Axis6.COUNT; a++) out[a] += f * j.column[a];
        }
        return out;
    }

    double forward() {
        double f = 0.0;
        for (Jet j : jets) f += Math.max(0.0, j.column[Axis6.FORWARD.index()]) * j.hi;
        return f;
    }

    boolean anyTrouble() {
        for (Jet j : jets) if (j.job != AerialSetup.EngineJob.OFF && (j.out() || j.foreign)) return true;
        return false;
    }

    void release(ServerLevel level, UUID owner) {
        for (Jet j : jets) {
            j.push = 0.0;
            j.command = 0.0;
            j.level = 0.0;
            j.aim = Double.NaN;
            j.held = false;
        }
        Vcc4DirectJets.releaseAll(level, owner);
    }

    boolean holding() {
        for (Jet j : jets) if (j.held) return true;
        return false;
    }

    private static double positive(double v) {
        return Double.isFinite(v) && v > 0.0 ? v : 0.0;
    }
}
