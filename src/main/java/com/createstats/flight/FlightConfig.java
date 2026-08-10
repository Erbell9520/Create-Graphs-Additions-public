package com.createstats.flight;

/** [1] */
public final class FlightConfig {

    private FlightConfig() {}

    // binding limits
    /** Maximum number of (propeller, governor) pairs the FCC can command. */
    public static final int MAX_PROPELLER_BINDINGS = 31;
    /** Maximum number of redstone-link burner channels the FCC can drive. */
    public static final int MAX_BURNER_LINKS = 5;

    // workbench model caps
    /** Maximum workbench groups a single FCC will create. */
    public static final int MAX_WORKBENCH_GROUPS = 32;
    /** Maximum composite controls a single FCC will create. */
    public static final int MAX_WORKBENCH_CONTROLS = 32;
    /** Maximum routes a single control may fan out to. */
    public static final int MAX_ROUTES_PER_CONTROL = 16;

    // actuator clamps
    /** Hard RPM clamp applied to every governor command (matches Create's internal range). */
    public static final int RPM_CLAMP = 256;
    /**
     * Reaction-torque seed used by the B matrix: yaw torque per unit thrust gain, in
     * block-metres. Applied only when a binding has an explicit/inferred yaw factor.
     */
    public static final double REACTION_TORQUE_TO_THRUST_RATIO = 0.06;

    // override / failsafe thresholds
    /**
     * Ticks the FCC may be missing before an RPM Governor autonomously zeroes its output
     * (failsafe in case of FCC removal mid-flight).
     */
    public static final int GOVERNOR_FCC_MISSING_FAILSAFE_TICKS = 10;
    /** Ticks a binding may stay foreign-owned / missing before it is flagged offline in the GUI. */
    public static final int BINDING_OFFLINE_PERSIST_TICKS = 20;
    /** Per-tick RPM ramp-down step used by the soft-cutoff (graceful descent) failsafe. */
    public static final int FAILSAFE_RAMP_STEP_RPM = 8;
    /** Maximum RPM delta an RPM Governor may apply per server tick. Keeps Create kinetic propagation stable. */
    public static final int GOVERNOR_MAX_RPM_STEP_PER_TICK = 6;
    /** Minimum ticks between governor kinetic re-propagations; avoids mutating Create networks every BE tick. */
    public static final int GOVERNOR_REBUILD_INTERVAL_TICKS = 2;
    /** Min interval (ticks) between two override / safety chat alerts to the same player. */
    public static final int SAFETY_ALERT_THROTTLE_TICKS = 100;

    // control-loop cadence
    /** Inner loop period (server ticks). Stability axes (Tx/Ty), fastest. */
    public static final int INNER_LOOP_TICKS = 1;
    /** Outer loop period (server ticks). Position / altitude / yaw, slower. */
    public static final int OUTER_LOOP_TICKS = 4;
    /** Burner trim loop period (server ticks). Hot-air response is on the order of seconds. */
    public static final int BURNER_LOOP_TICKS = 20;
}
