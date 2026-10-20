package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.aerial.AerialSnapshot.Air;
import com.createstats.aerial.AerialSnapshot.Balloon;
import com.createstats.aerial.AerialSnapshot.Body;
import com.createstats.aerial.AerialSnapshot.Force;
import com.createstats.aerial.AerialSnapshot.Mass;
import com.createstats.aerial.AerialSnapshot.Propeller;
import com.createstats.aerial.AerialSnapshot.Route;
import com.createstats.aerial.AerialSnapshot.Waypoint;

final class AerialFlightSim {
    static final double G = 11.0;
    static final double SEA = 63.0;
    static final double LAPSE = 0.004;

    final double mass = 1180.0;

    final double propThrust = Math.pow(16.0, 1.5) * 128.0 * 0.2;
    final double airflow = Math.sqrt(16.0) * 128.0 * 0.05;
    final double drag = 180.0;
    final double maxYawDeg = 9.0;

    final double liftMax = 6000.0;

    double balloonLift = 1000.0;
    V3 wind = V3.ZERO;

    List<Waypoint> route;
    int active;

    V3 position;
    V3 velocity = V3.ZERO;
    double heading;
    double throttle = 1.0;
    double yawCommand;
    double targetAltitude;
    double lift;
    long tick;
    boolean fuelled = true;

    AerialFlightSim(V3 start, double heading) {
        this.position = start;
        this.heading = heading;
        this.targetAltitude = start.y();
    }

    static double pressure(double y) {
        return Math.exp(-LAPSE * (y - SEA));
    }

    double trueTopSpeed(double pressure) {
        final double t = 4.0 * this.propThrust * pressure;
        return t / (this.drag + t / this.airflow);
    }

    void step() {
        final double dt = 0.05;
        final double p = pressure(this.position.y());
        final double hdg = Math.toRadians(this.heading);
        final V3 fwd = new V3(Math.sin(hdg), 0.0, -Math.cos(hdg));
        final V3 air = this.velocity.minus(this.wind);
        final double u = air.dot(fwd);
        final double scaling = AirframeMath.airflowScaling(this.airflow * this.throttle, u);
        final double thrust = 4.0 * this.propThrust * this.throttle * p * scaling;
        final V3 dragForce = air.times(-this.drag);

        final double need = this.mass * G - this.balloonLift * p * G;
        final double err = this.targetAltitude - this.position.y();
        this.lift = AirframeMath.clamp(need + 900.0 * err - 2400.0 * this.velocity.y(), 0.0, this.liftMax);

        final double yawRate = AirframeMath.clamp(this.yawCommand, -this.maxYawDeg, this.maxYawDeg);
        this.heading = AirframeMath.wrap360(this.heading + yawRate * dt);

        final V3 total = fwd.times(thrust).plus(dragForce)
                .plus(new V3(0.0, this.balloonLift * p * G + this.lift - this.mass * G, 0.0));

        V3 va = air.plus(total.times(dt / this.mass));
        final double along = va.horizontal().dot(fwd);
        final V3 lateral = va.horizontal().minus(fwd.times(along));
        va = new V3(fwd.x() * along + lateral.x() * 0.8, va.y(), fwd.z() * along + lateral.z() * 0.8);
        this.velocity = va.plus(this.wind);
        this.position = this.position.plus(this.velocity.times(dt));
        this.lastYawRate = yawRate;
        this.lastThrust = thrust;
        this.tick++;
    }

    double lastYawRate;
    double lastThrust;

    static final double LOOKAHEAD = 45.0;

    void pursue() {
        if (this.route == null || this.route.size() < 2) {
            return;
        }
        this.active = Math.max(1, this.active);
        while (this.active < this.route.size() - 1) {
            final V3 a = this.route.get(this.active - 1).position();
            final V3 b = this.route.get(this.active).position();
            final V3 d = b.minus(a).horizontal();
            if (this.position.minus(a).horizontal().dot(d) / d.length() < d.length()) {
                break;
            }
            this.active++;
        }
        final V3 aim = pursuitPoint(this.route, this.active, this.position);
        final double want = AirframeMath.headingOf(aim.minus(this.position));
        final double track = this.velocity.horizontalLength() > 0.5 ? AirframeMath.headingOf(this.velocity)
                : this.heading;
        this.yawCommand = AirframeMath.clamp(AirframeMath.wrap180(want - track) * 0.8, -this.maxYawDeg,
                this.maxYawDeg);
        this.targetAltitude = this.route.get(this.active).position().y();
    }

    static V3 pursuitPoint(List<Waypoint> route, int active, V3 position) {
        final V3 a = route.get(active - 1).position();
        V3 b = route.get(active).position();
        final V3 d = b.minus(a).horizontal();
        final double len = d.length();
        final double along = AirframeMath.clamp(position.minus(a).horizontal().dot(d) / len, 0.0, len);
        V3 point = a.plus(d.times(along / len));
        double remaining = LOOKAHEAD;
        int next = active;
        while (true) {
            final double left = b.minus(point).horizontalLength();
            if (remaining <= left || next >= route.size() - 1) {
                return point.plus(b.minus(point).horizontal().normalized().times(Math.min(remaining, left)));
            }
            remaining -= left;
            point = b;
            next++;
            b = route.get(next).position();
        }
    }

    static Quat orientation(double headingDeg, double pitchDeg, double bankDeg) {
        final Quat yaw = Quat.axisAngle(V3.UP, -Math.toRadians(headingDeg));
        final Quat pitch = Quat.axisAngle(new V3(1.0, 0.0, 0.0), Math.toRadians(pitchDeg));
        final Quat bank = Quat.axisAngle(new V3(0.0, 0.0, -1.0), Math.toRadians(bankDeg));
        return yaw.mul(pitch).mul(bank);
    }

    AerialSnapshot snapshot() {
        final double p = pressure(this.position.y());
        final double speed = this.velocity.horizontalLength();
        final double bank = Math.toDegrees(Math.atan(speed * Math.toRadians(this.lastYawRate) / G));
        final double pitch = speed > 0.5 ? Math.toDegrees(Math.atan2(this.velocity.y(), speed)) * 0.5 : 0.0;
        final Quat q = orientation(this.heading, pitch, bank);
        final V3 air = this.velocity.minus(this.wind);
        final double hdg = Math.toRadians(this.heading);
        final V3 fwd = new V3(Math.sin(hdg), 0.0, -Math.cos(hdg));
        final double u = air.dot(fwd);
        final double scaling = AirframeMath.airflowScaling(this.airflow * this.throttle, u);

        final List<Propeller> props = new ArrayList<>();
        final double[][] at = {{-3.0, 0.0, -6.0}, {3.0, 0.0, -6.0}, {-3.0, 0.0, 6.0}, {3.0, 0.0, 6.0}};
        final String[] names = {"Fore port", "Fore stbd", "Aft port", "Aft stbd"};
        for (int i = 0; i < 4; i++) {
            props.add(new Propeller(names[i], "bearing", new V3(at[i][0], at[i][1], at[i][2]), new V3(0.0, 0.0, -1.0),
                    this.propThrust * this.throttle, this.airflow * this.throttle, scaling, p, 128.0 * this.throttle,
                    0.4, this.fuelled));
        }
        final List<Force> forces = new ArrayList<>();
        forces.add(new Force("propulsion", fwd.times(this.lastThrust).plus(new V3(0.0, this.lift, 0.0)),
                this.position.plus(new V3(0.0, -1.0, 0.0))));
        forces.add(new Force("balloon_lift", new V3(0.0, this.balloonLift * p * G, 0.0),
                this.position.plus(new V3(0.0, 4.0, 0.0))));
        forces.add(new Force("drag", air.times(-this.drag), V3.NAN));
        forces.add(new Force("gravity", new V3(0.0, -this.mass * G, 0.0), this.position));
        final List<Balloon> balloons = List.of(new Balloon("Balloon 1", "hot_air", new V3(0.0, 4.0, 0.0),
                this.balloonLift, 900.0, 900.0, 1000.0, 0.0, 12.0, p));
        return new AerialSnapshot(this.tick, "Kestrel", new UUID(1L, 2L), AerialCommand.all(),
                new Body(this.position, q, new V3(0.0, 0.0, -1.0), this.velocity,
                        new V3(0.0, -Math.toRadians(this.lastYawRate), 0.0)),
                new Air(true, air, p, pressure(this.position.y() + Air.PROBE), SEA, new V3(0.0, -G, 0.0)),
                new Mass(this.mass, new V3(41000.0, 66000.0, 38000.0), 1204), forces, props, balloons, null, null,
                null, null, null, null, this.route == null ? null : new Route("Harbor run", this.route, this.active),
                null, null, null, null);
    }
}
