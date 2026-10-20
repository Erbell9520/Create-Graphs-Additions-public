package com.createstats.flight;

public final class FlightConfig {
    private FlightConfig() {}

    public static final int MAX_PROPELLER_BINDINGS = 31;

    public static final int MAX_BURNER_LINKS = 5;

    public static final int MAX_WORKBENCH_GROUPS = 32;

    public static final int MAX_WORKBENCH_CONTROLS = 32;

    public static final int MAX_ROUTES_PER_CONTROL = 16;

    public static final int RPM_CLAMP = 256;

    public static final double REACTION_TORQUE_TO_THRUST_RATIO = 0.06;

    public static final int GOVERNOR_FCC_MISSING_FAILSAFE_TICKS = 10;

    public static final int BINDING_OFFLINE_PERSIST_TICKS = 20;

    public static final int FAILSAFE_RAMP_STEP_RPM = 8;

    public static final int GOVERNOR_MAX_RPM_STEP_PER_TICK = 6;

    public static final int GOVERNOR_REBUILD_INTERVAL_TICKS = 2;

    public static final int SAFETY_ALERT_THROTTLE_TICKS = 100;

    public static final int INNER_LOOP_TICKS = 1;

    public static final int OUTER_LOOP_TICKS = 4;

    public static final int BURNER_LOOP_TICKS = 20;
}
