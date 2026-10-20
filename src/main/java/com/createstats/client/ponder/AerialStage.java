package com.createstats.client.ponder;

import com.createstats.vccmk4.air.core.AerialParts;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.NoseRule;

import java.util.List;

// mirrors the stage generator, AerialPonderStageTest fails when they drift
public final class AerialStage {
    private AerialStage() {
    }

    public static final int PLATE = 12;

    public static final int[] AERIAL = {9, 3, 5};
    public static final int[] SEAT = {8, 3, 5};
    public static final int[] BURNER_AFT = {5, 3, 5};
    public static final int[] BURNER_FORE = {7, 3, 5};
    public static final int[] ENGINE = {4, 3, 5};
    public static final int[] ASSEMBLER = {6, 3, 5};
    public static final int[] GOVERNOR_PORT = {2, 3, 4};
    public static final int[] GOVERNOR_STARBOARD = {2, 3, 6};
    public static final int[] PROP_PORT = {1, 3, 4};
    public static final int[] PROP_STARBOARD = {1, 3, 6};
    public static final int[] FIN_BEARING = {1, 6, 5};
    public static final int[] FIN_TIP = {0, 8, 5};
    public static final int[] LAMP_RED = {9, 3, 4};
    public static final int[] LAMP_WHITE = {9, 1, 5};
    public static final int[] LAMP_AMBER = {6, 9, 5};
    public static final int[][] CARGO = {{5, 3, 4}, {6, 3, 4}, {5, 3, 6}, {6, 3, 6}};

    public static final int[] SHIP_MIN = {0, 1, 3};
    public static final int[] SHIP_MAX = {10, 9, 7};

    public static final int SHIP_BLOCKS = 136;
    public static final double[] COM = {6.007, 5.529, 5.493};

    public static final int[] PORT_WALL_MIN = {3, 6, 3};
    public static final int[] PORT_WALL_MAX = {9, 7, 3};
    public static final double[] INSIDE_MIN = {3.06, 6.0, 4.06};
    public static final double[] INSIDE_MAX = {9.94, 7.94, 6.94};

    public static final int[] LEVER_MIN = {0, 5, 9};
    public static final int[] LEVER_MAX = {2, 10, 11};
    public static final int[] LEVER_BURNER = {1, 6, 10};
    public static final int[] LEVER = {0, 6, 9};

    public static final int STRIP_X0 = 12;
    public static final int CHUNK = 10;
    public static final int CHUNKS = 6;

    public static AerialParts.SwivelSpec finSpec() {
        double lon = FIN_BEARING[0] + 0.5 - COM[0];
        return new AerialParts.SwivelSpec(0L, lon, 1.0, 0.0, true, false);
    }

    public static AerialSetup.SurfaceRole finJob() {
        AerialSetup.Surface s = AerialParts.propose(finSpec());
        return s == null ? AerialSetup.SurfaceRole.TOOL : s.role();
    }

    public static AirLight redJob() {
        return AirLight.propose(AirLight.RED, 0.0, 0.0);
    }

    public static AirLight amberJob() {
        return AirLight.propose(AirLight.AMBER, 1.0, 0.0);
    }

    public static AirLight whiteJob() {
        return AirLight.propose(AirLight.WHITE, -1.0, 0.0);
    }

    public static AerialSetup.EngineJob engineJob() {
        return AerialSetup.Engine.DEFAULT.job();
    }

    public static int noseTurns() {
        return NoseRule.proposeTurns(1, 0, List.of(new double[] {-1.0, 0.0, 0.0, 1.0}, new double[] {-1.0, 0.0, 0.0, 1.0}));
    }

    public static String key(AerialSetup.SurfaceRole role) {
        return "gui.create_stats.vcc_mk4_aerial.role." + role.key;
    }

    public static String key(AirLight light) {
        return "gui.create_stats.vcc_mk4_aerial.light." + light.key;
    }

    public static String key(AerialSetup.EngineJob job) {
        return "gui.create_stats.vcc_mk4_aerial.job." + job.key;
    }

    public static final String LIFT_KEY = "gui.create_stats.vcc_mk4_aerial.tab.lift";
}
