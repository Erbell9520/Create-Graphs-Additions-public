package com.createstats.driverhelmet;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModDataComponents;
import com.createstats.vccmk4.Vcc4BrakeOverrides;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class DriverTelemetry {
    private static final double TICK = 0.05;

    private static final double ACCEL_SMOOTHING = 0.3;

    private static final long NEW_DRIVE_GAP = 40;
    private static final long FLUSH_TICKS = 100;
    private static final int SEND_EVERY = 5;

    private static final Map<UUID, State> DRIVERS = new HashMap<>();

    private DriverTelemetry() {
    }

    private static final class State {
        BlockPos console;
        long lastTick = Long.MIN_VALUE;
        long startTick;
        final Vector3d velocity = new Vector3d();
        final double[] accel = new double[3];
        double trip;
        double top;
        double pendingOdometer;
        long lastFlush;
        final DriveLog log = new DriveLog();
    }

    private record Frame(@Nullable Object access, Vector3d noseLocal, double[] forward, double[] up, double[] gravity,
                         double g) {
    }

    public static void tick(ServerPlayer driver, BlockEntity console, long now, Supplier<List<BlockPos>> wheels,
                            Function<BlockPos, BlockPos> governorFor, Vector3d noseLocal, double forwardSpeed) {
        final ItemStack helmet = DriverHelmets.worn(driver);
        if (helmet.isEmpty()) {
            DRIVERS.remove(driver.getUUID());
            return;
        }
        State s = DRIVERS.get(driver.getUUID());
        if (s == null || !console.getBlockPos().equals(s.console) || now - s.lastTick > NEW_DRIVE_GAP) {
            s = new State();
            s.console = console.getBlockPos();
            s.startTick = now;
            s.lastFlush = now;
            DRIVERS.put(driver.getUUID(), s);
        }
        final Frame frame = frame(console, noseLocal);
        final Vector3d v = orZero(SableBridge.linearVelocityWorld(console));
        final long dt = now - s.lastTick;
        double rawG = 0.0;
        if (dt >= 1 && dt <= 5) {
            final double seconds = dt * TICK;
            final double[] raw = {(v.x - s.velocity.x) / seconds, (v.y - s.velocity.y) / seconds,
                    (v.z - s.velocity.z) / seconds};
            rawG = Math.sqrt(TelemetryMath.dot(raw, raw)) / frame.g();
            for (int i = 0; i < 3; i++) {
                s.accel[i] += (raw[i] - s.accel[i]) * ACCEL_SMOOTHING;
            }
            final double d = v.length() * seconds;
            s.trip += d;
            s.pendingOdometer += d;
        }
        s.velocity.set(v);
        s.lastTick = now;
        s.top = Math.max(s.top, Math.abs(forwardSpeed));

        final List<BlockPos> wheelList = wheels.get();
        final Level level = console.getLevel();
        final double[] g = TelemetryMath.gForce(s.accel, frame.forward(), frame.up(), frame.g());
        s.log.tick(now, Math.hypot(g[0], g[1]), rawG, allAirborne(level, wheelList));

        HelmetVisor visor = DriverHelmetItem.visor(helmet);
        if (now - s.lastFlush >= FLUSH_TICKS && s.pendingOdometer > 0.0) {
            visor = visor.withOdometer(visor.odometer() + s.pendingOdometer);
            helmet.set(ModDataComponents.HELMET_VISOR.get(), visor);
            s.pendingOdometer = 0.0;
            s.lastFlush = now;
        }
        if (now % SEND_EVERY != 0 || visor.open()) {
            return;
        }
        PacketDistributor.sendToPlayer(driver, measure(console, s, visor, wheelList, governorFor, frame, g,
                forwardSpeed, now));
    }

    private static Frame frame(BlockEntity console, Vector3d noseLocal) {
        final Object access = SableBridge.subLevelAccessOf(console);
        final Quaterniond q = SableBridge.bodyOrientation(access);
        final Vector3d noseL = new Vector3d(noseLocal).normalize();
        final Vector3d forward = q == null ? new Vector3d(noseL) : q.transform(new Vector3d(noseL));
        final Vector3d up = q == null ? new Vector3d(0, 1, 0) : q.transform(new Vector3d(0, 1, 0));
        final Vector3d gravity = SableBridge.gravityWorld(console);
        final double[] gv = gravity == null ? new double[]{0.0, -9.81, 0.0} : new double[]{gravity.x, gravity.y, gravity.z};
        final double g = Math.sqrt(TelemetryMath.dot(gv, gv));
        return new Frame(access, noseL, new double[]{forward.x, forward.y, forward.z}, new double[]{up.x, up.y, up.z},
                gv, g > 1.0e-6 ? g : 9.81);
    }

    private static boolean allAirborne(@Nullable Level level, List<BlockPos> wheels) {
        int tyres = 0;
        for (BlockPos pos : wheels) {
            final BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            if (Double.isNaN(OffroadWheels.radius(be))) {
                continue;
            }
            tyres++;
            if (!TelemetryMath.airborne(OffroadWheels.extension(be))) {
                return false;
            }
        }
        return tyres > 0;
    }

    private static DriverTelemetryPacket measure(BlockEntity console, State s, HelmetVisor visor, List<BlockPos> wheels,
                                                 Function<BlockPos, BlockPos> governorFor, Frame frame, double[] g,
                                                 double forwardSpeed, long now) {
        final Vector3d up = new Vector3d(frame.up()[0], frame.up()[1], frame.up()[2]);
        final Vector3d rightL = new Vector3d(frame.noseLocal()).cross(0, 1, 0);
        final double vertical = TelemetryMath.verticalG(s.accel, frame.up(), frame.gravity());

        final Level level = console.getLevel();
        final List<DriverTelemetryPacket.Wheel> out = new ArrayList<>();
        for (BlockPos pos : wheels) {
            if (out.size() >= DriverTelemetryPacket.MAX_WHEELS) {
                break;
            }
            final BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
            final Vector3d d = new Vector3d(pos.getX() - console.getBlockPos().getX(),
                    pos.getY() - console.getBlockPos().getY(), pos.getZ() - console.getBlockPos().getZ());
            final float lat = (float) d.dot(rightL);
            final float lon = (float) d.dot(frame.noseLocal());
            final boolean driven = governorFor.apply(pos) != null;
            final float applied = be instanceof KineticBlockEntity k ? k.getSpeed() : 0.0f;
            final double radius = OffroadWheels.radius(be);
            final double extension = OffroadWheels.extension(be);
            final boolean airborne = TelemetryMath.airborne(extension);
            final double grip = TelemetryMath.grip(OffroadWheels.friction(be), airborne);
            final double load = TelemetryMath.load(extension, radius);
            final double ground = groundSpeed(level, frame.access(), pos, up, s.velocity);
            float actual = Float.NaN;
            float slip = Float.NaN;
            if (!Double.isNaN(radius)) {
                final double free = TelemetryMath.freeRpm(applied, brakeSignal(level, pos, now));
                final double act = TelemetryMath.actualRpm(free, TelemetryMath.rollingRpm(ground, radius), grip,
                        airborne);
                actual = (float) act;
                slip = airborne ? Float.NaN : (float) TelemetryMath.slip(act, ground, radius);
            }
            out.add(new DriverTelemetryPacket.Wheel(lat, lon, applied, actual, slip, (float) grip, (float) load,
                    !airborne, driven));
        }
        final DriveLog log = s.log;
        return new DriverTelemetryPacket((float) forwardSpeed, (float) g[0], (float) g[1], (float) vertical,
                (float) log.peak(), (float) log.lastImpact(), log.impactAge(now), (float) log.tripImpact(),
                (float) log.airNow(now), (float) log.lastAir(), (float) TelemetryMath.pitch(frame.forward()),
                (float) TelemetryMath.roll(frame.forward(), frame.up()), visor.odometer() + s.pendingOdometer,
                (float) s.trip, (int) (now - s.startTick), (float) s.top, List.copyOf(out));
    }

    private static int brakeSignal(@Nullable Level level, BlockPos pos, long now) {
        final int override = Vcc4BrakeOverrides.forTick(pos, now);
        if (override >= 0) {
            return override;
        }
        return level == null ? 0 : level.getSignal(pos.above(), Direction.UP);
    }

    private static double groundSpeed(@Nullable Level level, @Nullable Object access, BlockPos pos, Vector3d up,
                                      Vector3d bodyVelocity) {
        final Vector3d world = SableBridge.localToWorld(access, new Vector3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        final Vec3 at = world == null ? null : SableBridge.velocityAt(level, access, new Vec3(world.x, world.y, world.z));
        final Vector3d v = at == null ? new Vector3d(bodyVelocity) : new Vector3d(at.x, at.y, at.z);
        final double along = v.dot(up);
        return v.sub(new Vector3d(up).mul(along)).length();
    }

    private static Vector3d orZero(@Nullable Vector3d v) {
        return v == null ? new Vector3d() : v;
    }
}
