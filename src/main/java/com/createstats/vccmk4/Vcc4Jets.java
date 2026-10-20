package com.createstats.vccmk4;

import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.ThrustCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;

final class Vcc4Jets {
    interface Type {
        String name();

        boolean matches(BlockEntity be);

        @Nullable
        Spec spec(BlockEntity be);

        Reading read(BlockEntity be);

        boolean command(ServerLevel level, BlockEntity be, double command, @Nullable float[] nozzle);

        void release(ServerLevel level, BlockEntity be, @Nullable float[] nozzleBefore);

        @Nullable
        float[] nozzleTarget(BlockEntity be);
    }

    record Spec(JetKind kind, Vector3d centre, Vector3d axis, double configForce) {
    }

    record Reading(JetKind kind, double force, Vector3d direction, double ceiling, double clear, JetSupply supply,
                   float nozzleX, float nozzleY, boolean lit) {
        Vector3d forceVector() {
            return new Vector3d(direction).mul(Double.isFinite(force) ? force : 0.0);
        }
    }

    static final List<Type> ALL = List.of(new Propulsion());

    private Vcc4Jets() {
    }

    @Nullable
    static Type of(@Nullable BlockEntity be) {
        if (be == null) return null;
        for (Type t : ALL) {
            if (t.matches(be)) return t;
        }
        return null;
    }

    @Nullable
    static Reading read(@Nullable BlockEntity be) {
        Type t = of(be);
        return t == null ? null : t.read(be);
    }

    static final class Propulsion implements Type {
        @Override
        public String name() {
            return "propulsion thruster";
        }

        @Override
        public boolean matches(BlockEntity be) {
            return Vcc4Reflect.is(Vcc4Reflect.THRUSTER, be);
        }

        @Nullable
        @Override
        public Spec spec(BlockEntity be) {
            if (!Vcc4Reflect.thrusterController(be)) return null;
            BlockPos pos = be.getBlockPos();
            int width = Vcc4Reflect.thrusterWidth(be);

            double half = width / 2.0;
            Vector3d centre = new Vector3d(pos.getX() + half, pos.getY() + half, pos.getZ() + half);
            Direction facing = Vcc4Scanner.facing(be.getBlockState());
            Vector3d axis = facing != null ? Vcc4Scanner.unit(facing) : new Vector3d(0, 0, -1);
            return new Spec(kindOf(be), centre, axis, configForce(be, width));
        }

        private static double configForce(BlockEntity be, int width) {
            double config = Vcc4Reflect.thrusterConfigForce(be);
            if (!Double.isFinite(config)) return Double.NaN;
            return Vcc4Reflect.thrusterCreative(be) ? config : config * width * width * width;
        }

        @Override
        public Reading read(BlockEntity be) {
            JetKind kind = kindOf(be);
            boolean creative = Vcc4Reflect.thrusterCreative(be);
            double units = Vcc4Reflect.thrusterUnits(be);
            double thrust = Vcc4Reflect.thrusterThrust(be);
            double force = Double.isFinite(thrust) ? Math.max(0.0, thrust) / units : 0.0;
            Vector3d direction = Vcc4Reflect.thrusterDirection(be);
            if (direction == null || direction.lengthSquared() < 1.0e-6) {
                Direction facing = Vcc4Scanner.facing(be.getBlockState());
                direction = facing != null ? Vcc4Scanner.unit(facing) : new Vector3d(0, 0, -1);
            } else {
                direction.normalize();
            }
            double lit = Vcc4Reflect.thrusterLit(be, creative);
            JetSupply supply = supplyOf(be, kind, creative);

            double ceiling = supply.out() ? 0.0 : ThrustCommand.ceiling(force, lit, configForce(be, Vcc4Reflect.thrusterWidth(be)));
            double clear = creative ? 1.0 : Vcc4Reflect.thrusterClear(be);

            if (!creative && Vcc4Reflect.thrusterEmptyBlocks(be) <= 0) clear = 0.0;
            float[] nozzle = Vcc4Reflect.thrusterNozzleNow(be);
            return new Reading(kind, force, direction, ceiling, clear, supply,
                    nozzle == null ? 0f : nozzle[0], nozzle == null ? 0f : nozzle[1], Double.isFinite(lit) && lit > ThrustCommand.LIT);
        }

        @Override
        public boolean command(ServerLevel level, BlockEntity be, double command, @Nullable float[] nozzle) {
            if (!Vcc4Reflect.thrusterCommand(be, command)) return false;
            if (nozzle != null) {
                float[] target = Vcc4Reflect.thrusterNozzleTarget(be);
                if (target != null && (Math.abs(target[0] - nozzle[0]) > 1.0e-4f || Math.abs(target[1] - nozzle[1]) > 1.0e-4f)) {
                    Vcc4Reflect.thrusterSetNozzle(be, nozzle[0], nozzle[1]);
                }
            }
            return true;
        }

        @Override
        public void release(ServerLevel level, BlockEntity be, @Nullable float[] nozzleBefore) {
            Vcc4Reflect.thrusterHandBack(be);
            if (nozzleBefore == null) return;

            float[] target = Vcc4Reflect.thrusterNozzleTarget(be);
            if (target != null && (Math.abs(target[0] - nozzleBefore[0]) > 1.0e-4f || Math.abs(target[1] - nozzleBefore[1]) > 1.0e-4f)) {
                Vcc4Reflect.thrusterSetNozzle(be, nozzleBefore[0], nozzleBefore[1]);
            }
        }

        @Nullable
        @Override
        public float[] nozzleTarget(BlockEntity be) {
            return Vcc4Reflect.thrusterNozzleTarget(be);
        }

        private static JetKind kindOf(BlockEntity be) {
            if (Vcc4Reflect.is(Vcc4Reflect.THRUSTER_SOLID, be)) return JetKind.BOOSTER;
            if (Vcc4Reflect.is(Vcc4Reflect.THRUSTER_VECTOR, be)) return JetKind.VECTOR;
            return JetKind.THRUSTER;
        }

        private static JetSupply supplyOf(BlockEntity be, JetKind kind, boolean creative) {
            if (creative) return JetSupply.NONE;
            boolean working = Vcc4Reflect.thrusterWorking(be);
            if (Vcc4Reflect.thrusterIon(be)) return working ? JetSupply.POWERED : JetSupply.UNPOWERED;
            if (kind == JetKind.BOOSTER) {
                int[] burn = Vcc4Reflect.thrusterBurn(be);
                if (working || burn[0] > 0) return burn[1] > 0 && burn[0] < burn[1] / 10 ? JetSupply.LOW : JetSupply.OK;
                return JetSupply.EMPTY;
            }
            int[] fuel = Vcc4Reflect.thrusterFuel(be);
            if (!working || fuel[0] <= 0) return JetSupply.EMPTY;
            return fuel[1] > 0 && fuel[0] < fuel[1] / 10 ? JetSupply.LOW : JetSupply.OK;
        }
    }
}
