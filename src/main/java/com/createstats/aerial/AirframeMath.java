package com.createstats.aerial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Traffic;
import com.createstats.aerial.AerialSnapshot.Waypoint;
import com.createstats.aerial.AirframeReadout.BalloonState;
import com.createstats.aerial.AirframeReadout.Corner;
import com.createstats.aerial.AirframeReadout.DockState;
import com.createstats.aerial.AirframeReadout.NavState;
import com.createstats.aerial.AirframeReadout.PropState;
import com.createstats.aerial.AirframeReadout.RouteState;
import com.createstats.aerial.AirframeReadout.Share;
import com.createstats.aerial.AirframeReadout.TerrainCheck;
import com.createstats.aerial.AirframeReadout.TrafficState;

public final class AirframeMath {
    public static final double DEFAULT_LAPSE = 0.004;
    public static final double PREDICT_SECONDS = 10.0;
    public static final double PREDICT_STEP = 0.5;
    public static final double PROFILE_STEP = 8.0;
    public static final double PROFILE_LENGTH = 320.0;

    public static final double CPA_HORIZON = 30.0;

    public static final double CPA_MARGIN = 8.0;

    public static final double CPA_CLOSING = 0.3;

    public static final double LAG_RATIO = 0.85;

    public static final double SATURATED = 0.15;

    public static final double OVERLOADED = 0.90;

    public static final double MIN_CORNER_DEGREES = 5.0;

    public static final double ROUTE_TOLERANCE = 3.0;

    private AirframeMath() {
    }

    public static AirframeReadout read(AerialSnapshot s) {
        final AirframeReadout r = new AirframeReadout(s);
        attitude(s, r);
        motion(s, r);
        wind(s, r);
        height(s, r);
        support(s, r);
        holdSpeed(s, r);
        ceiling(s, r);
        balance(s, r);
        propellers(s, r);
        balloons(s, r);
        endurance(s, r);
        nav(s, r);
        route(s, r);
        traffic(s, r);
        dock(s, r);
        predict(s, r);
        envelope(s, r);
        autopilot(s, r);
        return r;
    }

    static void attitude(AerialSnapshot s, AirframeReadout r) {
        final Quat q = s.body().orientation();
        final V3 nose = s.body().nose();
        r.forward = q.rotate(nose).normalized();
        r.up = q.rotate(V3.UP).normalized();
        r.right = q.rotate(nose.cross(V3.UP)).normalized();
        r.heading = headingOf(r.forward);
        r.pitch = Math.toDegrees(Math.asin(clamp(r.forward.y(), -1.0, 1.0)));
        r.bank = Math.toDegrees(Math.atan2(-r.right.y(), r.up.y()));
    }

    static void motion(AerialSnapshot s, AirframeReadout r) {
        final V3 v = s.body().velocity();
        r.groundSpeed = v.horizontalLength();
        r.verticalSpeed = v.y();
        r.speed = v.length();
        r.track = r.groundSpeed > 0.3 ? headingOf(v) : r.heading;
        r.drift = r.groundSpeed > 0.3 ? wrap180(r.track - r.heading) : 0.0;

        final double yawRate = -s.body().spin().y();
        r.turnRate = Math.toDegrees(yawRate);
        r.turnRadius = Math.abs(yawRate) > 0.005 && r.groundSpeed > 0.5
                ? r.groundSpeed / Math.abs(yawRate) : Double.POSITIVE_INFINITY;
        final V3 air = s.air().velocity();
        r.airspeed = air.length();
        r.indicatedAirspeed = air.dot(r.forward);
        r.sideslip = r.airspeed > 0.3 ? Math.toDegrees(Math.atan2(air.dot(r.right), air.dot(r.forward))) : 0.0;
    }

    static void wind(AerialSnapshot s, AirframeReadout r) {
        r.airMeasured = s.air().measured();
        if (!r.airMeasured) {
            return;
        }

        final V3 w = s.body().velocity().minus(s.air().velocity()).horizontal();
        r.windSpeed = w.length();
        r.windFrom = r.windSpeed > 0.05 ? headingOf(w.times(-1.0)) : Double.NaN;
        final V3 fwd = r.forward.horizontal().normalized();
        final V3 right = new V3(-fwd.z(), 0.0, fwd.x());
        r.headwind = -w.dot(fwd);
        r.crosswind = w.dot(right);
    }

    static void height(AerialSnapshot s, AirframeReadout r) {
        final V3 p = s.body().position();
        r.altitude = p.y();
        final AerialSnapshot.Silhouette sil = s.silhouette();
        r.keelDrop = sil.isEmpty() ? 0.0 : Math.max(0.0, -sil.originU());
        double ground = s.terrain().groundY();
        if (!Double.isFinite(ground)) {
            ground = s.terrain().heightAt(p.x(), p.z());
        }
        r.groundY = ground;
        double agl = Double.isFinite(ground) ? p.y() - r.keelDrop - ground : Double.NaN;
        final double ray = s.terrain().clearance();
        if (Double.isFinite(ray)) {
            agl = Double.isFinite(agl) ? Math.min(agl, ray) : ray;
        }
        r.agl = agl;
        r.landed = Double.isFinite(agl) && agl < 1.0 && r.speed < 0.5;
    }

    static void support(AerialSnapshot s, AirframeReadout r) {
        final V3 g = s.air().gravity();
        r.gravity = g.length() > 1.0e-6 ? g.length() : 11.0;
        final V3 worldUp = g.length() > 1.0e-6 ? g.times(-1.0 / g.length()) : V3.UP;
        r.mass = s.mass().mass();

        final double load = s.mass().load();
        r.load = Double.isFinite(load) ? Math.max(0.0, load) : Double.NaN;
        final double extra = Double.isFinite(r.load) ? r.load : 0.0;
        double weight = Double.isFinite(r.mass) && r.mass > 0.0 ? (r.mass + extra) * r.gravity : Double.NaN;
        if (!Double.isFinite(weight)) {
            for (final Force f : s.forces()) {
                if ("gravity".equals(f.group())) {
                    weight = f.force().length() + extra * r.gravity;
                    r.mass = f.force().length() / r.gravity;
                }
            }
        }
        r.carried = Double.isFinite(r.mass) ? r.mass + extra : Double.NaN;
        r.weight = weight;
        if (!(weight > 0.0) || s.forces().isEmpty()) {
            return;
        }
        double vertical = 0.0;
        double thrustAlong = 0.0;
        V3 drag = V3.ZERO;
        V3 total = V3.ZERO;
        boolean gravityReported = false;
        boolean dragReported = false;
        for (final Force f : s.forces()) {
            total = total.plus(f.force());
            if ("gravity".equals(f.group())) {
                gravityReported = true;
                continue;
            }
            if ("drag".equals(f.group())) {
                drag = drag.plus(f.force());
                dragReported = true;
                continue;
            }
            final double v = f.force().dot(worldUp);
            vertical += v;
            r.shares.add(new Share(f.group(), v / weight));
            if ("propulsion".equals(f.group())) {
                thrustAlong += f.force().dot(r.forward);
            }
        }
        r.support = vertical / weight;
        r.reserve = r.support - 1.0;
        r.dragShare = dragReported ? drag.length() / weight : Double.NaN;
        r.forwardThrustShare = thrustAlong / weight;
        if (!gravityReported) {
            total = total.plus(worldUp.times(-weight));
        } else if (Double.isFinite(r.load) && r.load > 0.0) {
            total = total.plus(worldUp.times(-r.load * r.gravity));
        }
        // no drag group = all push no resistance, so don't even guess

        r.forwardAccel = dragReported ? total.dot(r.forward) / r.carried : Double.NaN;

        final double airDown = -s.air().velocity().dot(worldUp);
        final double dragUp = drag.dot(worldUp);
        if (r.support < 1.0 && airDown > 0.3 && dragUp > 0.0) {
            r.steadySink = (1.0 - r.support) * weight / (dragUp / airDown);
        }
    }

    static void holdSpeed(AerialSnapshot s, AirframeReadout r) {
        double sails = 0.0;
        for (final Share sh : r.shares) {
            if ("lift".equals(sh.group())) {
                sails += sh.share();
            }
        }
        final double v = s.air().velocity().horizontalLength();
        if (!(sails > 0.005) || !(v > 0.5) || !Double.isFinite(r.support)) {
            return;
        }
        final double rest = r.support - sails;
        if (rest < 1.0) {
            r.minHoldSpeed = v * (1.0 - rest) / sails;
        }
    }

    static void ceiling(AerialSnapshot s, AirframeReadout r) {
        double p = s.air().pressure();
        final double above = s.air().pressureAbove();
        if (!Double.isFinite(p) && Double.isFinite(s.air().seaLevel()) && Double.isFinite(r.altitude)) {
            p = clamp(Math.exp(-DEFAULT_LAPSE * (r.altitude - s.air().seaLevel())), 0.0, 1.5);
            r.pressureEstimated = true;
        }
        r.pressure = p;
        r.lapse = lapseOf(p, above);
        if (Double.isFinite(r.support) && r.support > 0.0) {
            r.ceilingY = equilibrium(r.altitude, r.support, r.lapse);
        }
        if (Double.isFinite(r.carried) && r.carried > 0.0 && !s.balloons().isEmpty()) {
            double lifted = 0.0;
            for (final Balloon b : s.balloons()) {
                final double bp = Double.isFinite(b.pressure()) ? b.pressure() : (Double.isFinite(p) ? p : 1.0);
                if (Double.isFinite(b.lift())) {
                    lifted += b.lift() * bp;
                }
            }
            if (lifted > 0.0) {
                r.balloonFloatY = equilibrium(r.altitude, lifted / r.carried, r.lapse);
            }
        }
    }

    public static double lapseOf(double pressure, double pressureAbove) {
        if (Double.isFinite(pressure) && Double.isFinite(pressureAbove) && pressure > 0.0 && pressureAbove > 0.0
                && pressure > pressureAbove) {
            return Math.log(pressure / pressureAbove) / AerialSnapshot.Air.PROBE;
        }
        return DEFAULT_LAPSE;
    }

    public static double equilibrium(double y, double ratio, double lapse) {
        if (!(ratio > 0.0) || !(lapse > 0.0) || !Double.isFinite(y)) {
            return Double.NaN;
        }
        return y + Math.log(ratio) / lapse;
    }

    static void balance(AerialSnapshot s, AirframeReadout r) {
        final V3 com = s.body().position();
        final V3 worldUp = V3.UP;
        V3 weighted = V3.ZERO;
        double total = 0.0;
        V3 moment = V3.ZERO;
        boolean any = false;
        for (final Force f : s.forces()) {
            if ("gravity".equals(f.group()) || !f.centre().isFinite()) {
                continue;
            }
            any = true;
            final V3 arm = f.centre().minus(com);
            moment = moment.plus(arm.cross(f.force()));
            final double lift = f.force().dot(worldUp);
            if (lift > 0.0) {
                weighted = weighted.plus(f.centre().times(lift));
                total += lift;
            }
        }
        if (!any) {
            return;
        }
        r.pitchMoment = moment.dot(r.right);
        if (total > 0.0) {
            final V3 centreWorld = weighted.times(1.0 / total);
            r.liftCentre = s.body().orientation().inverseRotate(centreWorld.minus(com));
            r.balanceArm = r.liftCentre.dot(s.body().nose());
        }
    }

    static void propellers(AerialSnapshot s, AirframeReadout r) {
        final List<Propeller> props = s.propellers();
        if (props.isEmpty()) {
            return;
        }
        final Quat q = s.body().orientation();
        final V3 air = s.air().velocity();
        final V3 nose = s.body().nose();
        final V3 bodyRight = nose.cross(V3.UP);
        double total = 0.0;
        final double[] thrusts = new double[props.size()];
        final double[] delivered = new double[props.size()];
        final double[] axis = new double[props.size()];
        V3 net = V3.ZERO;
        V3 torque = V3.ZERO;
        double halfSpan = 1.0;
        for (int i = 0; i < props.size(); i++) {
            final Propeller p = props.get(i);
            final V3 dirWorld = q.rotate(p.direction());
            axis[i] = air.dot(dirWorld);
            final double scaling = Double.isFinite(p.airflowScaling()) ? clamp(p.airflowScaling(), 0.0, 1.0)
                    : airflowScaling(p.airflow(), axis[i]);
            final double pressure = Double.isFinite(p.pressure()) ? p.pressure()
                    : (Double.isFinite(r.pressure) ? r.pressure : 1.0);
            delivered[i] = p.active() ? scaling * pressure : 0.0;
            thrusts[i] = Double.isFinite(p.staticThrust()) ? Math.abs(p.staticThrust()) * delivered[i] : 0.0;
            total += thrusts[i];
            net = net.plus(p.direction().times(thrusts[i]));
            torque = torque.plus(p.position().cross(p.direction().times(thrusts[i])));
            halfSpan = Math.max(halfSpan, Math.abs(p.position().dot(bodyRight)));
        }
        for (int i = 0; i < props.size(); i++) {
            final Propeller p = props.get(i);
            final double median = siblingMedianRpm(props, p);
            final boolean lagging = p.active() && median > 0.0 && Double.isFinite(p.rpm())
                    && p.rpm() < median * LAG_RATIO;
            final boolean overloaded = Double.isFinite(p.shaftStress()) && p.shaftStress() >= OVERLOADED;
            final boolean saturated = p.active() && p.staticThrust() > 0.0
                    && delivered[i] < SATURATED;
            r.props.add(new PropState(p, delivered[i], thrusts[i], axis[i], total > 0.0 ? thrusts[i] / total : 0.0,
                    saturated, lagging, overloaded));
        }

        r.yawTorque = -torque.dot(V3.UP);

        final AerialSnapshot.Silhouette sil = s.silhouette();
        if (!sil.isEmpty()) {
            halfSpan = Math.max(halfSpan, 0.5 * sil.topRows() * sil.cell());
        }
        r.asymmetry = total > 0.0 ? clamp(Math.abs(r.yawTorque) / (total * halfSpan), 0.0, 1.0) : 0.0;
        if (net.length() > 1.0e-6) {
            r.thrustAngle = Math.toDegrees(Math.atan2(net.dot(bodyRight), net.dot(nose)));
        }
        if (Double.isFinite(r.weight) && r.weight > 0.0) {
            r.thrustToWeight = total / r.weight;
        }
        r.topSpeed = topSpeed(s, r);
        double topOut = Double.POSITIVE_INFINITY;
        for (final Propeller p : props) {
            if (p.active() && p.direction().dot(nose) > 0.5 && p.airflow() > 0.0) {
                topOut = Math.min(topOut, p.airflow() / p.direction().dot(nose));
            }
        }
        r.propTopOut = Double.isInfinite(topOut) ? Double.NaN : topOut;
    }

    public static double airflowScaling(double airflow, double axisSpeed) {
        if (!(airflow > 0.001)) {
            return 0.0;
        }
        return clamp((airflow - axisSpeed) / airflow, 0.0, 1.0);
    }

    private static double siblingMedianRpm(List<Propeller> props, Propeller p) {
        final V3 dir = p.direction().normalized();
        final double[] rpms = props.stream().filter(o -> o.active() && Double.isFinite(o.rpm()) && o.rpm() > 0.0
                        && o.direction().normalized().dot(dir) > 0.9)
                .mapToDouble(Propeller::rpm).sorted().toArray();
        if (rpms.length < 2) {
            return 0.0;
        }
        final int m = rpms.length / 2;
        return rpms.length % 2 == 1 ? rpms[m] : (rpms[m - 1] + rpms[m]) * 0.5;
    }

    static double topSpeed(AerialSnapshot s, AirframeReadout r) {
        final V3 nose = s.body().nose();
        final V3 level = r.forward.horizontal().normalized();
        V3 drag = V3.ZERO;
        boolean haveDrag = false;
        for (final Force f : s.forces()) {
            if ("drag".equals(f.group())) {
                drag = drag.plus(f.force());
                haveDrag = true;
            }
        }
        final double resisting = -drag.horizontal().dot(level);
        final double speedNow = s.air().velocity().horizontal().dot(level);
        if (!haveDrag || !(speedNow >= 1.0) || !(resisting > 0.0)) {
            return Double.NaN;
        }
        final double k = resisting / speedNow;
        double lo = 0.0;
        double hi = 200.0;
        final double fallback = Double.isFinite(r.pressure) ? r.pressure : 1.0;
        if (forwardThrustAt(s, nose, 0.0, fallback) <= 0.0) {
            return 0.0;
        }
        for (int i = 0; i < 60; i++) {
            final double mid = (lo + hi) * 0.5;
            if (forwardThrustAt(s, nose, mid, fallback) > k * mid) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) * 0.5;
    }

    static double forwardThrustAt(AerialSnapshot s, V3 nose, double u, double fallbackPressure) {
        double sum = 0.0;
        for (final Propeller p : s.propellers()) {
            final double along = p.direction().dot(nose);
            if (!p.active() || along <= 0.0 || !Double.isFinite(p.staticThrust())) {
                continue;
            }
            final double pressure = Double.isFinite(p.pressure()) ? p.pressure() : fallbackPressure;
            sum += Math.abs(p.staticThrust()) * pressure * airflowScaling(p.airflow(), u * along) * along;
        }
        return sum;
    }

    static void balloons(AerialSnapshot s, AirframeReadout r) {
        for (final Balloon b : s.balloons()) {
            final double fill = b.target() > 0.0 ? b.filled() / b.target() : Double.NaN;
            final double pressure = Double.isFinite(b.pressure()) ? b.pressure()
                    : (Double.isFinite(r.pressure) ? r.pressure : 1.0);
            final double share = Double.isFinite(r.carried) && r.carried > 0.0 ? b.lift() * pressure / r.carried
                    : Double.NaN;
            final boolean short0 = Double.isFinite(fill) && fill < 0.95;
            final double ttt = short0 && b.fillRate() > 0.001 ? (b.target() - b.filled()) / b.fillRate() : Double.NaN;
            final boolean starved = short0 && !(b.fillRate() > 0.001);
            final boolean burnersOut = starved && Double.isFinite(b.gasOutput()) && b.gasOutput() <= 0.001;
            r.balloons.add(new BalloonState(b, fill, share, ttt, starved, burnersOut));
        }
    }

    static void endurance(AerialSnapshot s, AirframeReadout r) {
        if (s.engine().count() <= 0) {
            return;
        }
        r.enduranceSeconds = s.engine().fuelSeconds();
        if (Double.isFinite(r.enduranceSeconds)) {
            r.rangeMetres = r.enduranceSeconds * r.groundSpeed;
        }
    }

    static void nav(AerialSnapshot s, AirframeReadout r) {
        final AerialSnapshot.Target t = s.target();
        if (t == null || !t.position().isFinite()) {
            return;
        }
        final V3 d = t.position().minus(s.body().position());
        final double dist = d.horizontalLength();
        final double bearing = dist > 0.01 ? headingOf(d) : r.heading;
        final double closing = dist > 0.01 ? s.body().velocity().horizontal().dot(d.horizontal().normalized()) : 0.0;
        final double eta = closing > 0.2 ? dist / closing : Double.NaN;
        r.nav = new NavState(t.label(), dist, bearing, wrap180(bearing - r.heading), closing, eta, d.y());
    }

    static void route(AerialSnapshot s, AirframeReadout r) {
        final List<Waypoint> pts = s.route().points();
        if (pts.isEmpty()) {
            return;
        }
        final V3 pos = s.body().position();
        final int a = s.route().active();
        final V3 to = pts.get(a).position();
        final V3 from = a > 0 ? pts.get(a - 1).position() : pos;
        final double cross = crossTrack(from, to, pos);
        double total = 0.0;
        final double[] cumulative = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            total += pts.get(i).position().minus(pts.get(i - 1).position()).horizontalLength();
            cumulative[i] = total;
        }
        double progress;
        if (a == 0 || total <= 0.0) {
            progress = 0.0;
        } else {
            final V3 leg = to.minus(from).horizontal();
            final double legLen = leg.length();
            final double along = legLen > 0.0 ? clamp(pos.minus(from).horizontal().dot(leg) / legLen, 0.0, legLen) : 0.0;
            progress = clamp((cumulative[a - 1] + along) / total, 0.0, 1.0);
        }
        final double toNext = to.minus(pos).horizontalLength();
        final double timeToNext = r.groundSpeed > 0.3 ? toNext / r.groundSpeed : Double.NaN;
        r.route = new RouteState(s.route().name(), a, pts.size(), cross, progress, toNext, timeToNext,
                corners(s, r, pts));
    }

    public static double crossTrack(V3 from, V3 to, V3 pos) {
        final V3 leg = to.minus(from).horizontal();
        final double len = leg.length();
        if (len < 1.0e-6) {
            return 0.0;
        }
        final V3 d = leg.times(1.0 / len);

        final V3 right = new V3(-d.z(), 0.0, d.x());
        return pos.minus(from).horizontal().dot(right);
    }

    // turn starts R*tan(theta/2) before the waypoint. shared legs split by each corner's own tan
    static List<Corner> corners(AerialSnapshot s, AirframeReadout r, List<Waypoint> pts) {
        final List<Corner> out = new ArrayList<>();
        final AerialSnapshot.Envelope env = s.envelope();
        final int n = pts.size();
        final double[] half = new double[n];
        final double[] leg = new double[n];
        for (int i = 1; i < n; i++) {
            leg[i] = pts.get(i).position().minus(pts.get(i - 1).position()).horizontalLength();
        }
        for (int i = 1; i + 1 < n; i++) {
            if (leg[i] < 0.5 || leg[i + 1] < 0.5) {
                continue;
            }
            final V3 l1 = pts.get(i).position().minus(pts.get(i - 1).position()).horizontal();
            final V3 l2 = pts.get(i + 1).position().minus(pts.get(i).position()).horizontal();
            final double angle = Math.toDegrees(Math.acos(clamp(l1.dot(l2) / (leg[i] * leg[i + 1]), -1.0, 1.0)));
            if (angle >= MIN_CORNER_DEGREES) {
                half[i] = Math.tan(Math.toRadians(angle) * 0.5);
            }
        }
        for (int i = 1; i + 1 < n; i++) {
            if (half[i] <= 0.0) {
                continue;
            }
            final double angle = Math.toDegrees(2.0 * Math.atan(half[i]));
            final double before = leg[i] * half[i] / (half[i - 1] + half[i]);
            final double after = leg[i + 1] * half[i] / (half[i] + half[i + 1]);
            final double slack = ROUTE_TOLERANCE / Math.sin(Math.toRadians(Math.min(angle, 90.0)));
            final double allowed = (Math.min(before, after) + slack) / half[i];
            double speed = pts.get(i).speed();
            if (!Double.isFinite(speed) || speed <= 0.0) {
                speed = r.groundSpeed > 0.5 ? r.groundSpeed : Double.NaN;
            }
            final double ship = Double.isFinite(speed) ? turnRadiusAt(env, speed) : Double.NaN;
            final boolean feasible = !Double.isFinite(ship) || ship <= allowed;
            final double suggested = feasible ? Double.NaN : fastestTurning(env, allowed);
            out.add(new Corner(i, angle, speed, allowed, ship, feasible, suggested));
        }
        return out;
    }

    static void traffic(AerialSnapshot s, AirframeReadout r) {
        final V3 pos = s.body().position();
        final V3 vel = s.body().velocity();
        final double own = ownRadius(s);
        for (final Traffic t : s.traffic()) {
            if (!t.position().isFinite() || (t.id() != null && t.id().equals(s.shipId()))) {
                continue;
            }
            final V3 rel = t.position().minus(pos);
            final V3 vRel = t.velocity().minus(vel);
            final double dist = rel.length();
            final double closing = dist > 1.0e-6 ? -rel.dot(vRel) / dist : 0.0;
            final double v2 = vRel.dot(vRel);
            final double tCpa = v2 > 1.0e-4 ? Math.max(0.0, -rel.dot(vRel) / v2) : 0.0;
            final double dCpa = rel.plus(vRel.times(tCpa)).length();
            final double radius = Double.isFinite(t.radius()) ? Math.max(0.0, t.radius()) : 8.0;

            final boolean conflict = closing > CPA_CLOSING && tCpa <= CPA_HORIZON
                    && dCpa < radius + own + CPA_MARGIN;
            r.traffic.add(new TrafficState(t, dist, rel.y(), headingOf(rel), closing, tCpa, dCpa, conflict));
        }
        r.traffic.sort((x, y) -> Double.compare(x.distance(), y.distance()));
    }

    static double ownRadius(AerialSnapshot s) {
        final AerialSnapshot.Silhouette sil = s.silhouette();
        if (sil.isEmpty()) {
            return 8.0;
        }
        final double width = sil.topRows() * sil.cell();
        return 0.5 * Math.hypot(sil.length(), width);
    }

    static void dock(AerialSnapshot s, AirframeReadout r) {
        final AerialSnapshot.Docking d = s.docking();
        if (d == null || !d.offset().isFinite()) {
            return;
        }
        final V3 nose = s.body().nose();
        final V3 right = nose.cross(V3.UP);
        final double dist = d.offset().length();
        final double closing = dist > 1.0e-6 ? -d.offset().dot(d.relative()) / dist : 0.0;
        final double ttc = closing > 0.05 ? dist / closing : Double.NaN;
        r.dock = new DockState(d.partner(), d.connected(), dist, d.offset().dot(nose), d.offset().dot(right),
                d.offset().y(), closing, ttc);
    }

    static void predict(AerialSnapshot s, AirframeReadout r) {
        final V3 p0 = s.body().position();
        final double rate = Math.toRadians(r.turnRate);
        double x = p0.x();
        double y = p0.y();
        double z = p0.z();
        double hdg = Math.toRadians(r.track);
        r.predicted.add(p0);
        final int steps = (int) Math.round(PREDICT_SECONDS / PREDICT_STEP);
        double worst = Double.POSITIVE_INFINITY;
        double worstAt = Double.NaN;
        double impact = Double.NaN;
        final AerialSnapshot.Terrain terrain = s.terrain();
        final double keel = r.keelDrop;

        final AerialSnapshot.Helm helm = s.helm();
        final double level = helm.known() && helm.flying() && !helm.liftShort() && Double.isFinite(helm.flownAltitude())
                ? helm.flownAltitude() : Double.NaN;
        for (int i = 1; i <= steps; i++) {
            final double mid = hdg + rate * PREDICT_STEP * 0.5;
            hdg += rate * PREDICT_STEP;
            x += r.groundSpeed * Math.sin(mid) * PREDICT_STEP;
            z -= r.groundSpeed * Math.cos(mid) * PREDICT_STEP;
            final double next = y + r.verticalSpeed * PREDICT_STEP;
            y = Double.isFinite(level) && (y - level) * (next - level) <= 0.0 && Math.abs(r.verticalSpeed) > 1.0e-9
                    ? level : (Double.isFinite(level) && Math.abs(y - level) < 1.0e-9 ? level : next);
            final V3 p = new V3(x, y, z);
            r.predicted.add(p);
            final double ground = terrain.heightAt(x, z);
            if (Double.isFinite(ground)) {
                final double clear = y - keel - ground;
                if (clear < worst) {
                    worst = clear;
                    worstAt = i * PREDICT_STEP;
                }
                if (clear <= 0.0 && Double.isNaN(impact)) {
                    impact = i * PREDICT_STEP;
                }
            }
        }

        final int n = (int) (PROFILE_LENGTH / PROFILE_STEP) + 1;
        r.profile = new double[n];
        final double h = Math.toRadians(r.track);
        double ridge = Double.NEGATIVE_INFINITY;
        int ridgeIndex = -1;
        int last = -1;
        for (int i = 0; i < n; i++) {
            final double d = i * PROFILE_STEP;
            final double g = terrain.heightAt(p0.x() + Math.sin(h) * d, p0.z() - Math.cos(h) * d);
            r.profile[i] = g;
            if (Double.isFinite(g)) {
                last = i;
                if (g > ridge) {
                    ridge = g;
                    ridgeIndex = i;
                }
            }
        }
        double ridgeAt = Double.NaN;
        double ridgeClear = Double.NaN;
        if (ridgeIndex >= 0) {
            ridgeAt = ridgeIndex * PROFILE_STEP;
            final double t = r.groundSpeed > 0.3 ? ridgeAt / r.groundSpeed : 0.0;
            ridgeClear = p0.y() + r.verticalSpeed * t - keel - ridge;
        }

        final boolean risingAtEdge = ridgeIndex == last && last > 0 && last < n - 1
                && Double.isFinite(r.profile[last - 1]) && r.profile[last] > r.profile[last - 1];
        r.terrain = new TerrainCheck(Double.isInfinite(worst) ? Double.NaN : worst, worstAt, impact,
                ridgeAt, ridgeClear, last >= 0 ? last * PROFILE_STEP : Double.NaN, risingAtEdge);
    }

    static void envelope(AerialSnapshot s, AirframeReadout r) {
        final AerialSnapshot.Envelope env = s.envelope();
        r.turnRadiusAtSpeed = turnRadiusAt(env, r.groundSpeed);
        if (Double.isFinite(env.maxDecel()) && env.maxDecel() > 0.05) {
            r.stopDistance = r.groundSpeed * r.groundSpeed / (2.0 * env.maxDecel());
        }
    }

    static void autopilot(AerialSnapshot s, AirframeReadout r) {
        final AerialSnapshot.Autopilot a = s.autopilot();
        double max = Double.NaN;
        for (final double v : new double[]{a.authThrottle(), a.authYaw(), a.authPitch(), a.authLift()}) {
            if (Double.isFinite(v)) {
                max = Double.isNaN(max) ? v : Math.max(max, v);
            }
        }
        r.maxAuthority = max;
    }

    public static double turnRadiusAt(AerialSnapshot.Envelope env, double speed) {
        if (!Double.isFinite(speed) || speed < 0.0) {
            return Double.NaN;
        }
        final float[] b = env.turnRadius();
        final double w = env.bucketWidth();
        final double f = speed / w - 0.5;
        final int i = (int) Math.floor(f);
        final double lo = bucket(b, i);
        final double hi = bucket(b, i + 1);
        if (Double.isFinite(lo) && Double.isFinite(hi)) {
            final double t = clamp(f - i, 0.0, 1.0);
            return lo + (hi - lo) * t;
        }
        for (int k = Math.max(0, i); k < b.length; k++) {
            if (Double.isFinite(b[k])) {
                return b[k];
            }
        }
        return Double.NaN;
    }

    public static double fastestTurning(AerialSnapshot.Envelope env, double allowed) {
        final float[] b = env.turnRadius();
        double best = Double.NaN;
        for (int i = 0; i < b.length; i++) {
            if (Double.isFinite(b[i]) && b[i] <= allowed) {
                best = (i + 0.5) * env.bucketWidth();
            }
        }
        return best;
    }

    private static double bucket(float[] b, int i) {
        return i >= 0 && i < b.length ? b[i] : Double.NaN;
    }

    public static double headingOf(V3 v) {
        return wrap360(Math.toDegrees(Math.atan2(v.x(), -v.z())));
    }

    public static double wrap360(double d) {
        final double m = d % 360.0;
        return m < 0.0 ? m + 360.0 : m;
    }

    public static double wrap180(double d) {
        final double m = wrap360(d + 180.0) - 180.0;
        return m;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double median(double[] values) {
        final double[] v = Arrays.stream(values).filter(Double::isFinite).sorted().toArray();
        if (v.length == 0) {
            return Double.NaN;
        }
        final int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) * 0.5;
    }
}
