package com.createstats.ground;

import java.util.ArrayList;
import java.util.List;

import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;

// one readout per frame shared by everything, so nothing computes a figure two ways
public final class DriveReadout {
    public final GroundSnapshot snapshot;

    DriveReadout(GroundSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public enum State {
        DRIVING,

        TOWED,

        PARKED,

        IDLE,

        RUNAWAY,

        OFF_CRAFT
    }

    public State state = State.IDLE;

    public V3 forward = new V3(0.0, 0.0, -1.0);
    public V3 right = new V3(1.0, 0.0, 0.0);
    public V3 up = V3.UP;

    public double heading;

    public double pitch;

    public double roll;

    public double grade;

    public double speed;

    public double speedKmh;

    public double groundSpeed;

    public double sideSpeed;
    public double verticalSpeed;

    public double track = Double.NaN;

    public double drift;

    public double turnRate;

    public double turnRadius = Double.POSITIVE_INFINITY;
    public double altitude;
    public double groundY = Double.NaN;

    public double latG;

    public double lonG;

    public double vertG = 1.0;

    public double combinedG;

    public record WheelRead(Wheel wheel, int side, double surfaceSpeed, boolean touching, boolean spinning,
                            boolean locking) {
    }

    public final List<WheelRead> wheels = new ArrayList<>();

    public int touching;
    public int lifted;

    public boolean airborne;

    public double worstSlip = Double.NaN;

    public double leastGrip = Double.NaN;

    public boolean spinning;
    public boolean locking;

    public double trackWidth = Double.NaN;
    public double wheelbase = Double.NaN;

    public double centreX = Double.NaN;
    public double centreZ = Double.NaN;

    public double pathRadius = Double.NaN;

    public double kerbRadius = Double.NaN;

    public double crabAngle = Double.NaN;

    public double leftSpeed = Double.NaN;
    public double rightSpeed = Double.NaN;

    public double leftRpm = Double.NaN;
    public double rightRpm = Double.NaN;

    public double jointAngle = Double.NaN;

    public record Model(double driveForce, double dragPerSpeed, double brakePerSpeed, double mountMax, double cruise,
                        double reverse, double tau, double toNinety, double stopTau, double hill,
                        double cruiseHere, double stopDistance) {
        public static final Model NONE = new Model(0.0, 0.0, 0.0, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        public boolean known() {
            return Double.isFinite(this.cruise);
        }
    }

    public Model model = Model.NONE;

    public int engines;

    public int noRotation;
    public int overstressed;
    public int foreign;

    public int starved;

    public int jetsLit;

    public int jetsDry;

    public int jetsLow;

    public enum LinkState {
        RIGID,

        HITCHED,

        SLACK,

        TAUT,

        STRETCHED,

        NEAR_SNAP,

        DOCKING,

        DOCKED,

        GONE
    }

    public record UnitRead(Unit unit, LinkState link) {
    }

    public final List<UnitRead> units = new ArrayList<>();

    public int trailers;

    public double worstArticulation = Double.NaN;

    public double worstSnap = Double.NaN;

    public boolean linkLost;
    public boolean swaying;

    public double comHeight = Double.NaN;
    public double stability = Double.NaN;
    public double tipAngle = Double.NaN;
    public double rolloverShare = Double.NaN;
}
