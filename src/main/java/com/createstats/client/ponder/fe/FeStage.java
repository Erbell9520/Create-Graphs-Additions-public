package com.createstats.client.ponder.fe;

import com.createstats.power.ponder.FePonderChapters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// FePonderStageTest fails the day these drift from the stage generator, there's no other error
public final class FeStage {
    private FeStage() {
    }

    public static final String WORKSHOP = "fe/workshop";
    public static final int WORKSHOP_PLATE = 12;

    public static final BlockPos MOTOR = new BlockPos(10, 1, 10);
    public static final BlockPos GEARBOX_A = new BlockPos(10, 1, 9);
    public static final BlockPos SHAFT_AB = new BlockPos(10, 1, 8);
    public static final BlockPos CLUTCH = new BlockPos(10, 1, 7);
    public static final BlockPos GEARBOX_B = new BlockPos(10, 1, 6);
    public static final BlockPos SHAFT_BC = new BlockPos(10, 1, 5);
    public static final BlockPos GEARBOX_C = new BlockPos(10, 1, 4);

    public static final BlockPos[] DYNAMOS = {new BlockPos(10, 2, 9), new BlockPos(10, 2, 6), new BlockPos(10, 2, 4)};
    public static final BlockPos LEVER = new BlockPos(11, 2, 7);
    public static final BlockPos LEVER_BASE = new BlockPos(11, 1, 7);

    public static final BlockPos[] DUCT = {new BlockPos(9, 2, 9), new BlockPos(9, 2, 8), new BlockPos(9, 2, 7),
            new BlockPos(9, 2, 6), new BlockPos(9, 2, 5), new BlockPos(9, 2, 4), new BlockPos(9, 2, 3),
            new BlockPos(8, 2, 3), new BlockPos(7, 2, 3)};

    public static final int[] DUCT_AT_DYNAMO = {0, 3, 5};

    public static final BlockPos PDU = new BlockPos(6, 2, 3);
    public static final BlockPos PEDESTAL = new BlockPos(6, 1, 3);

    public static final BlockPos FAN_MOTOR = new BlockPos(4, 1, 1);
    public static final BlockPos FAN = new BlockPos(3, 1, 1);
    public static final BlockPos REPELLER = new BlockPos(2, 1, 4);
    public static final BlockPos REPELLER_CONN = new BlockPos(2, 1, 5);
    public static final BlockPos RANGE = new BlockPos(3, 1, 7);
    public static final BlockPos RANGE_CONN = new BlockPos(4, 1, 7);
    public static final BlockPos MILL_MOTOR = new BlockPos(6, 1, 8);
    public static final BlockPos MILL = new BlockPos(6, 2, 8);
    public static final BlockPos LAMP = new BlockPos(1, 1, 9);
    public static final BlockPos LAMP_CONN = new BlockPos(2, 1, 9);

    public static final BlockPos[] MACHINE = {FAN_MOTOR, REPELLER, RANGE, MILL_MOTOR, LAMP};

    public static final BlockPos[] LEG_END = {FAN_MOTOR, REPELLER_CONN, RANGE_CONN, MILL_MOTOR, LAMP_CONN};

    public static final int[] STUD = {0, 1, 2, 3, 4};

    public static final BlockPos[] DRIVEN = {FAN, null, null, MILL, null};
    public static final int[] RPM = {128, 0, 0, 32, 0};

    public static final double[] CHIP_UP = {1.25, 1.35, 1.25, 2.25, 0.85};

    public static Vec3 chipPoint(int i) {
        return new Vec3(MACHINE[i].getX() + 0.5, MACHINE[i].getY() + CHIP_UP[i], MACHINE[i].getZ() + 0.5);
    }

    public static final float WORKSHOP_SCALE = 0.62f;
    public static final float BENCH_SCALE = 0.8f;

    public static final float OFFSET_Y = -1.5f;

    public static final String BENCH = "fe/bench";
    public static final int BENCH_PLATE = 9;

    public static final BlockPos BENCH_MOTOR = new BlockPos(7, 1, 2);
    public static final BlockPos BENCH_DYNAMO = new BlockPos(7, 2, 2);

    public static final BlockPos[] BENCH_RUN = {new BlockPos(6, 2, 2), new BlockPos(5, 2, 2), new BlockPos(4, 2, 2),
            new BlockPos(3, 2, 2), new BlockPos(2, 2, 2)};
    public static final BlockPos BENCH_FAN_MOTOR = new BlockPos(1, 2, 2);
    public static final BlockPos BENCH_PEDESTAL = new BlockPos(1, 1, 2);
    public static final BlockPos BENCH_FAN = new BlockPos(1, 3, 2);

    public static final BlockPos BENCH_LAMP = new BlockPos(4, 2, 1);
    public static final BlockPos BENCH_LAMP_BASE = new BlockPos(4, 1, 1);

    public static final int[] LADDER_Z = {4, 5, 6, 7};
    public static final int LADDER_X0 = 2;
    public static final int LADDER_X1 = 6;

    public static final Vec3 GAUGE_FROM = new Vec3(6.85, 1.02, 1.42);
    public static final Vec3 GAUGE_TO = new Vec3(2.15, 1.02, 1.42);

    public static final String SPANS = "fe/spans";
    public static final int SPANS_PLATE = 9;
    public static final float SPANS_SCALE = 0.8f;

    public static final BlockPos SPANS_MOTOR = new BlockPos(7, 1, 1);
    public static final BlockPos SPANS_DYNAMO = new BlockPos(7, 2, 1);

    public static final BlockPos[] SPANS_A = {new BlockPos(6, 2, 1), new BlockPos(7, 2, 2), new BlockPos(7, 3, 1)};
    public static final BlockPos SPANS_RANGE = new BlockPos(1, 1, 2);

    public static final BlockPos SPANS_B = new BlockPos(2, 1, 2);
    public static final BlockPos SPANS_LAMP = new BlockPos(2, 1, 4);

    public static final BlockPos SPANS_C = new BlockPos(3, 1, 4);
    public static final BlockPos SPANS_D = new BlockPos(4, 1, 4);
    public static final BlockPos SPANS_D_POST = new BlockPos(5, 1, 4);

    public static final BlockPos[] RACK_EAST = {new BlockPos(7, 2, 5), new BlockPos(7, 2, 6), new BlockPos(7, 2, 7),
            new BlockPos(7, 2, 8)};
    public static final BlockPos[] RACK_WEST = {new BlockPos(4, 2, 5), new BlockPos(4, 2, 6), new BlockPos(4, 2, 7),
            new BlockPos(4, 2, 8)};

    public static final String BANK = "fe/bank";
    public static final int BANK_PLATE = 9;
    public static final float BANK_SCALE = 0.8f;
    public static final BlockPos BANK_MOTOR = new BlockPos(7, 1, 3);
    public static final BlockPos BANK_DYNAMO = new BlockPos(7, 2, 3);

    public static final BlockPos[] BANK_RUN = {new BlockPos(6, 2, 3), new BlockPos(5, 2, 3), new BlockPos(4, 2, 3),
            new BlockPos(3, 2, 3)};
    public static final BlockPos BANK_RANGE = new BlockPos(4, 2, 4);
    public static final BlockPos BANK_RANGE_BASE = new BlockPos(4, 1, 4);

    public static final BlockPos[] BANK_BLOCKS = {new BlockPos(1, 1, 3), new BlockPos(1, 1, 4), new BlockPos(2, 1, 3),
            new BlockPos(2, 1, 4), new BlockPos(1, 2, 3), new BlockPos(1, 2, 4), new BlockPos(2, 2, 3),
            new BlockPos(2, 2, 4)};

    public static final BlockPos SPARE = new BlockPos(5, 2, 1);
    public static final BlockPos SPARE_BASE = new BlockPos(5, 1, 1);
    public static final BlockPos SPARE_LINK = new BlockPos(5, 2, 2);
    public static final BlockPos SPARE_LINK_BASE = new BlockPos(5, 1, 2);

    public static final String CONTROLLER = "fe/controller";
    public static final int CONTROLLER_PLATE = 9;
    public static final float CONTROLLER_SCALE = 0.8f;

    public static final BlockPos BC = new BlockPos(4, 1, 4);
    public static final BlockPos BC_MOTOR = new BlockPos(1, 1, 3);
    public static final BlockPos BC_DYNAMO = new BlockPos(1, 2, 3);

    public static final BlockPos[] BC_DEVICES = {BC_DYNAMO, new BlockPos(1, 1, 5), new BlockPos(1, 1, 7),
            new BlockPos(7, 1, 3), new BlockPos(7, 1, 5), new BlockPos(7, 1, 7)};

    public static final BlockPos[] BC_ENDS = {new BlockPos(2, 2, 3), new BlockPos(2, 1, 5), new BlockPos(2, 1, 7),
            new BlockPos(6, 1, 3), new BlockPos(6, 1, 5), new BlockPos(6, 1, 7)};

    public static Vec3 terminal(int port) {
        return terminal(BC, port);
    }

    public static Vec3 terminal(BlockPos bc, int port) {
        final double[] box = com.createstats.power.BatteryControllerPorts.boxOf(net.minecraft.core.Direction.NORTH, port);
        return new Vec3(bc.getX() + (box[0] + box[2]) * 0.5,
                bc.getY() + com.createstats.power.BatteryControllerPorts.topY(), bc.getZ() + (box[1] + box[3]) * 0.5);
    }

    public static final String YARD = "fe/yard";
    public static final int YARD_PLATE = 12;
    public static final float YARD_SCALE = WORKSHOP_SCALE;

    public static final BlockPos YC = new BlockPos(5, 1, 6);
    public static final BlockPos[] YARD_BANKS = {new BlockPos(2, 1, 5), new BlockPos(2, 1, 8)};
    public static final BlockPos[] YARD_BANK_ENDS = {new BlockPos(3, 1, 5), new BlockPos(3, 1, 8)};

    public static final BlockPos[] BOARDS = {new BlockPos(8, 1, 3), new BlockPos(8, 1, 9)};
    public static final BlockPos[] BOARD_FEEDS = {new BlockPos(7, 1, 3), new BlockPos(7, 1, 9)};

    public static final BlockPos[][] BOARD_MACHINES = {{new BlockPos(11, 1, 2), new BlockPos(11, 1, 4)},
            {new BlockPos(11, 1, 8), new BlockPos(11, 1, 10)}};
    public static final BlockPos[][] BOARD_ENDS = {{new BlockPos(10, 1, 2), new BlockPos(10, 1, 4)},
            {new BlockPos(10, 1, 8), new BlockPos(10, 1, 10)}};
}
