package com.createstats.aerial;

import java.util.ArrayList;
import java.util.List;

public final class AirframeReadout {
    public final AerialSnapshot snapshot;

    AirframeReadout(AerialSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public double heading;

    public double pitch;

    public double bank;

    public V3 forward = V3.ZERO;
    public V3 right = V3.ZERO;
    public V3 up = V3.UP;

    public double groundSpeed;
    public double verticalSpeed;
    public double speed;

    public double track;

    public double drift;

    public double turnRate;

    public double turnRadius = Double.POSITIVE_INFINITY;

    public double airspeed;

    public double indicatedAirspeed;

    public double sideslip;

    public boolean landed;

    public boolean airMeasured;
    public double windSpeed = Double.NaN;

    public double windFrom = Double.NaN;

    public double headwind = Double.NaN;

    public double crosswind = Double.NaN;

    public double altitude;
    public double groundY = Double.NaN;

    public double agl = Double.NaN;

    public double keelDrop;

    public double mass = Double.NaN;

    public double load = Double.NaN;

    public double carried = Double.NaN;

    public double gravity;

    public double weight = Double.NaN;

    public double support = Double.NaN;

    public double reserve = Double.NaN;

    public final List<Share> shares = new ArrayList<>();

    public double dragShare = Double.NaN;

    public double forwardThrustShare = Double.NaN;

    public double minHoldSpeed = Double.NaN;

    public double forwardAccel = Double.NaN;

    public double steadySink = Double.NaN;

    public double thrustToWeight = Double.NaN;

    public double pressure = Double.NaN;
    // no pressure read, so it's sable's default curve. flagged as an estimate
    public boolean pressureEstimated;

    public double lapse = AirframeMath.DEFAULT_LAPSE;

    public double ceilingY = Double.NaN;

    public double balloonFloatY = Double.NaN;

    public V3 liftCentre = V3.NAN;

    public double balanceArm = Double.NaN;

    public double pitchMoment = Double.NaN;

    public final List<PropState> props = new ArrayList<>();

    public double yawTorque;

    public double asymmetry;

    public double thrustAngle = Double.NaN;

    public double topSpeed = Double.NaN;

    public double propTopOut = Double.NaN;

    public final List<BalloonState> balloons = new ArrayList<>();
    public double enduranceSeconds = Double.NaN;
    public double rangeMetres = Double.NaN;

    public NavState nav;
    public RouteState route;
    public final List<TrafficState> traffic = new ArrayList<>();
    public DockState dock;

    public final List<V3> predicted = new ArrayList<>();
    public TerrainCheck terrain = TerrainCheck.UNKNOWN;

    public double[] profile = new double[0];

    public double turnRadiusAtSpeed = Double.NaN;
    public double stopDistance = Double.NaN;
    public double maxAuthority = Double.NaN;

    public record Share(String group, double share) {
    }

    public record PropState(AerialSnapshot.Propeller prop, double delivered, double thrust, double axisSpeed,
                            double share, boolean saturated, boolean lagging, boolean overloaded) {
    }

    public record BalloonState(AerialSnapshot.Balloon balloon, double fill, double liftShare, double timeToTarget,
                               boolean starved, boolean burnersOut) {
    }

    public record NavState(String label, double distance, double bearing, double relativeBearing, double closing,
                           double eta, double climb) {
    }

    public record RouteState(String name, int active, int count, double crossTrack, double progress,
                             double toNext, double timeToNext, List<Corner> corners) {
    }

    public record Corner(int index, double angle, double speed, double allowedRadius, double shipRadius,
                         boolean feasible, double suggestedSpeed) {
    }

    public record TrafficState(AerialSnapshot.Traffic traffic, double distance, double climb, double bearing,
                               double closing, double cpaTime, double cpaDistance, boolean conflict) {
    }

    public record DockState(String partner, boolean connected, double distance, double along, double lateral,
                            double vertical, double closing, double timeToContact) {
    }

    public record TerrainCheck(double minClearance, double atSeconds, double impactSeconds, double ridgeDistance,
                               double ridgeClearance, double scanned, boolean risingAtEdge) {
        public static final TerrainCheck UNKNOWN = new TerrainCheck(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, false);
    }
}
