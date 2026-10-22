package com.createstats.client.coupler;

import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.CouplerFault;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class CouplerFx {
    public record Frame(Direction facing, int direction, int colour, int crossing, boolean flowing,
                        boolean theirsOpen, boolean oursOpen, CouplerFault fault, double q, boolean pull,
                        int swings, int seats, int breaks, boolean ponder) {
    }

    public static final double EARSHOT = 20.0;

    private static final double SIGHT = 32.0;

    private static final double[][] SHOE_X = {{3.5, 5.0}, {11.0, 12.5}};

    private static final double MOUTH_Y = 11.15;
    private static final double MOUTH_Z_THEIRS = 2.7;
    private static final double MOUTH_Z_OURS = 13.3;
    private static final double AXLE_Y = 8.5;
    private static final double AXLE_Z = 8.0;

    private static final double CAP_RADIUS = 6.0;
    private static final Vec3 FOREIGN_ANCHOR = new Vec3(12.0, 15.4, 1.25);
    private static final Vec3 GRID_ANCHOR = new Vec3(12.0, 14.9, 14.75);
    private static final Vec3 DRIVER = new Vec3(15.6, 4.5, 4.0);
    private static final Vec3 SHUTTER = new Vec3(15.7, 3.6, 12.25);

    private static final DustParticleOptions AMBER = new DustParticleOptions(
            new Vector3f(0xE0 / 255.0f, 0xA4 / 255.0f, 0x4C / 255.0f), 0.45f);
    private static final DustParticleOptions CYAN = new DustParticleOptions(
            new Vector3f(0x6B / 255.0f, 0xE4 / 255.0f, 0xFF / 255.0f), 0.45f);

    private static final Map<UniversalPowerCouplerBE, Fx> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Fx {
        CouplerFault lastFault;
        float want;
        boolean cracklePlaying;
        WeakReference<Crackle> crackle = new WeakReference<>(null);
        int age;
    }

    static float wanted(UniversalPowerCouplerBE be) {
        final CouplerFault fault = be.fault();
        final boolean held = be.mode() == com.createstats.power.PowerCouplerMode.OFF
                || fault == CouplerFault.SWITCHED_OFF || fault == CouplerFault.LOOPED
                || fault == CouplerFault.FORCED_OFF;
        final int crossing = be.crossingFePerTick();
        return held || fault.blocksFlow() || crossing <= 0 ? 0.0f : ExchangeMotion.crackleVolume(crossing);
    }

    private static final int START_GRACE = 5;

    private CouplerFx() {
    }

    public static void tick(UniversalPowerCouplerBE be, Frame f) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final Fx fx = STATES.computeIfAbsent(be, k -> new Fx());
        fx.age++;
        final boolean tripped = f.fault() == CouplerFault.LOOPED && fx.lastFault != null
                && fx.lastFault != CouplerFault.LOOPED;
        fx.lastFault = f.fault();
        final Vec3 centre = world(be, f.facing(), 8.0, 8.0, 8.0);
        if (centre == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final double away = f.ponder() ? 0.0 : centre.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition());
        final RandomSource random = level.random;
        if (away <= SIGHT * SIGHT) {
            particles(level, be, f, random, tripped);
        }

        fx.want = f.ponder() ? 0.0f : wanted(be);
        if (f.ponder() || level != mc.level || away > EARSHOT * EARSHOT) {
            return;
        }

        final Crackle running = fx.crackle.get();
        if (fx.cracklePlaying && (running == null
                || (fx.age - running.startedAt > START_GRACE && !mc.getSoundManager().isActive(running)))) {
            fx.cracklePlaying = false;
        }
        if (fx.want > 0.0f && !fx.cracklePlaying) {
            final Crackle crackle = new Crackle(be, fx, fx.age, centre);
            fx.crackle = new WeakReference<>(crackle);
            fx.cracklePlaying = true;
            mc.getSoundManager().play(crackle);
        }
        oneShots(level, be, f, random, tripped);
    }

    private static void particles(Level level, UniversalPowerCouplerBE be, Frame f, RandomSource random,
                                  boolean tripped) {
        final double pace = ExchangeMotion.pace(f.crossing());
        final boolean live = f.flowing() && f.crossing() > 0;
        final double t = ExchangeMotion.periodFraction(f.q(), f.pull());
        if (live) {
            final boolean contact = t >= ExchangeMotion.CONTACT_START && t < ExchangeMotion.CONTACT_END;
            for (int dock = 0; dock < 2; dock++) {
                final boolean theirs = dock == 0;
                if (theirs ? f.theirsOpen() : f.oursOpen()) {
                    continue;
                }
                if (contact && random.nextDouble() < 0.05 + 0.25 * pace) {
                    mouthSpark(level, be, f, random, theirs, 0.15, 0.3);
                }
                final int arcs = f.breaks() > 0 ? 1 + (int) Math.round(2.0 * pace) : 0;
                final int snaps = f.seats() > 0 ? 1 + (int) Math.round(pace) : 0;
                for (int i = 0; i < arcs; i++) {
                    mouthSpark(level, be, f, random, theirs, 0.25, 0.5);
                }
                for (int i = 0; i < snaps; i++) {
                    mouthSpark(level, be, f, random, theirs, 0.1, 0.2);
                }
            }
            if (t < ExchangeMotion.SWING && random.nextDouble() < 0.5 + 0.5 * pace) {
                carriedMote(level, be, f, random);
            }
            final double terminal = 0.02 + 0.08 * pace;
            if (be.foreignEndpoint().wired() && random.nextDouble() < terminal) {
                spark(level, be, f.facing(), FOREIGN_ANCHOR, jitter(random, 0.15), 0.12 + 0.12 * random.nextDouble(),
                        jitter(random, 0.15));
            }
            if (be.gridLinkCount() > 0 && random.nextDouble() < terminal) {
                spark(level, be, f.facing(), GRID_ANCHOR, jitter(random, 0.15), 0.12 + 0.12 * random.nextDouble(),
                        jitter(random, 0.15));
            }
        }
        if (tripped) {
            for (int i = 0; i < 6; i++) {
                final Vec3 at = SHUTTER.add(0.0, jitter(random, 1.5), jitter(random, 1.2));
                spark(level, be, f.facing(), at, 0.3 + 0.3 * random.nextDouble(), 0.1 + 0.3 * random.nextDouble(),
                        jitter(random, 0.3));
            }
        }
    }

    private static void mouthSpark(Level level, UniversalPowerCouplerBE be, Frame f, RandomSource random,
                                   boolean theirs, double upMin, double upMax) {
        final double[] shoe = SHOE_X[random.nextInt(2)];
        final double x = shoe[0] + random.nextDouble() * (shoe[1] - shoe[0]);
        final Vec3 at = new Vec3(x, MOUTH_Y, theirs ? MOUTH_Z_THEIRS : MOUTH_Z_OURS);
        final double in = theirs ? 0.1 : -0.1;
        spark(level, be, f.facing(), at, jitter(random, 0.1), upMin + random.nextDouble() * (upMax - upMin),
                in * (0.5 + random.nextDouble()));
    }

    private static void carriedMote(Level level, UniversalPowerCouplerBE be, Frame f, RandomSource random) {
        final double star = ExchangeMotion.starAngle(f.q());
        int full = -1;
        int seen = 0;
        for (int i = 0; i < 4; i++) {
            if (ExchangeMotion.cassetteLevel(i, f.q(), f.pull()) >= 1.0 && random.nextInt(++seen) == 0) {
                full = i;
            }
        }
        if (full < 0) {
            return;
        }
        final double phi = Math.toRadians(-90.0 + 90.0 * full + star);
        final double x = 5.5 + random.nextDouble() * 5.0;
        final Vec3 at = new Vec3(x, AXLE_Y + CAP_RADIUS * Math.cos(phi), AXLE_Z + CAP_RADIUS * Math.sin(phi));
        final double turn = f.pull() ? 1.0 : -1.0;
        final Vec3 along = new Vec3(0.0, -Math.sin(phi) * turn, Math.cos(phi) * turn);
        final Vec3 p = world(be, f.facing(), at.x, at.y, at.z);
        final Vec3 v = turnOnly(be, f.facing(), along);
        if (p != null && v != null) {
            add(level, f.colour() > 0 ? AMBER : CYAN, p, v);
        }
    }

    private static void spark(Level level, UniversalPowerCouplerBE be, Direction facing, Vec3 modelPx, double vx,
                              double vy, double vz) {
        final Vec3 p = world(be, facing, modelPx.x, modelPx.y, modelPx.z);
        final Vec3 v = turnOnly(be, facing, new Vec3(vx, vy, vz));
        if (p != null && v != null) {
            add(level, ParticleTypes.ELECTRIC_SPARK, p, v);
        }
    }

    private static void add(Level level, ParticleOptions type, Vec3 p, Vec3 v) {
        level.addParticle(type, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    private static double jitter(RandomSource random, double half) {
        return (random.nextDouble() * 2.0 - 1.0) * half;
    }

    private static void oneShots(Level level, UniversalPowerCouplerBE be, Frame f, RandomSource random,
                                 boolean tripped) {
        final double pace = ExchangeMotion.pace(f.crossing());
        if (f.swings() > 0) {
            play(level, be, f.facing(), DRIVER, ModSounds.COUPLER_INDEX.get(), (float) (0.1 + 0.1 * pace),
                    1.5f + 0.1f * random.nextFloat());
        }
        if (f.seats() > 0 && f.flowing() && f.crossing() > 0) {
            play(level, be, f.facing(), new Vec3(8.0, 11.0, 8.0), ModSounds.COUPLER_SEAT.get(),
                    (float) (0.1 + 0.25 * pace), 0.95f + 0.15f * random.nextFloat());
        }
        if (tripped) {
            play(level, be, f.facing(), SHUTTER, ModSounds.COUPLER_TRIP.get(), 0.5f, 1.1f);
        }
    }

    private static void play(Level level, UniversalPowerCouplerBE be, Direction facing, Vec3 modelPx,
                             SoundEvent sound, float volume, float pitch) {
        final Vec3 p = world(be, facing, modelPx.x, modelPx.y, modelPx.z);
        if (p != null) {
            level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.BLOCKS, volume, pitch, false);
        }
    }

    @Nullable
    static Vec3 world(UniversalPowerCouplerBE be, Direction facing, double px, double py, double pz) {
        final double[] r = turn(facing, px / 16.0 - 0.5, pz / 16.0 - 0.5);
        final BlockPos pos = be.getBlockPos();
        return SableBridge.worldPointOf(be, new Vec3(pos.getX() + 0.5 + r[0], pos.getY() + py / 16.0,
                pos.getZ() + 0.5 + r[1]));
    }

    @Nullable
    private static Vec3 turnOnly(UniversalPowerCouplerBE be, Direction facing, Vec3 v) {
        final double[] r = turn(facing, v.x, v.z);
        final BlockPos pos = be.getBlockPos();
        final Vec3 base = Vec3.atCenterOf(pos);
        final Vec3 a = SableBridge.worldPointOf(be, base);
        final Vec3 b = SableBridge.worldPointOf(be, base.add(r[0], v.y, r[1]));
        return a == null || b == null ? null : b.subtract(a);
    }

    static double[] turn(Direction facing, double x, double z) {
        final double y = switch (facing) {
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
        };
        final double a = Math.toRadians(-y);
        final double c = Math.cos(a);
        final double s = Math.sin(a);
        return new double[] {x * c + z * s, -x * s + z * c};
    }

    private static final class Crackle extends AbstractTickableSoundInstance {
        private static final float STEP = 0.05f;

        private final UniversalPowerCouplerBE be;
        private final Fx fx;
        final int startedAt;

        Crackle(UniversalPowerCouplerBE be, Fx fx, int startedAt, Vec3 at) {
            super(ModSounds.COUPLER_CRACKLE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.be = be;
            this.fx = fx;
            this.startedAt = startedAt;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.looping = true;
            this.delay = 0;

            this.volume = STEP;
            this.pitch = 1.0f;
        }

        @Override
        public void tick() {
            final Minecraft mc = Minecraft.getInstance();
            final Level level = this.be.getLevel();
            if (this.be.isRemoved() || level == null || level != mc.level
                    || level.getBlockEntity(this.be.getBlockPos()) != this.be) {
                end();
                return;
            }
            final Vec3 at = world(this.be, this.be.foreignFace(), 8.0, 9.0, 8.0);
            if (at != null) {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
            final net.minecraft.world.entity.Entity listener = mc.cameraEntity;
            final boolean near = listener != null
                    && listener.distanceToSqr(this.x, this.y, this.z) <= (EARSHOT + 8.0) * (EARSHOT + 8.0);

            final float want = near ? wanted(this.be) : 0.0f;
            this.fx.want = want;
            this.volume += Math.max(-STEP, Math.min(STEP, want - this.volume));
            if (want <= 0.0f && this.volume <= 1e-4f) {
                end();
            }
        }

        private void end() {
            this.fx.cracklePlaying = false;
            stop();
        }
    }
}
