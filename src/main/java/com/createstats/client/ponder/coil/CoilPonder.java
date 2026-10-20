package com.createstats.client.ponder.coil;

import com.createstats.CreateStats;
import com.createstats.block.ConnectorAnchor;
import com.createstats.blockentity.StormCoilBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.SceneClock;
import com.createstats.client.ponder.fe.FeFlow;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.power.MainBreakerStuds;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.renderer.PowerConnectorRenderer;
import com.createstats.stormcoil.CoilForm;
import com.createstats.stormcoil.CoilWatch;
import com.createstats.stormcoil.StormCoilStuds;
import com.createstats.stormcoil.ponder.CoilPonderScript;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.List;
import java.util.Random;

// played from the real BE, never faked
public final class CoilPonder {
    public static final Direction FACING = Direction.EAST;

    static final double HUSK_MID = 0.975;

    static final int DEATH_TICKS = 20;

    private CoilPonder() {
    }

    static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    static int turns() {
        return Math.floorMod(FACING.get2DDataValue() - 2, 4);
    }

    public static Vec3 stud(int stud) {
        final double[] a = StormCoilStuds.anchor(turns(), stud);
        final int[] c = CoilPonderScript.COIL;
        return new Vec3(c[0] + a[0], c[1] + a[1], c[2] + a[2]);
    }

    public static Vec3 connector(int[] at) {
        final Vec3 t = PowerConnectorRenderer.connectorTerminal(Direction.UP, ConnectorAnchor.CENTER);
        return new Vec3(at[0] + t.x, at[1] + t.y, at[2] + t.z);
    }

    public static Vec3 breakerStud(MainBreakerStuds.Stud stud) {
        final Vec3 a = MainBreakerStuds.anchor(Direction.UP, stud);
        final int[] b = CoilPonderScript.BREAKER;
        return new Vec3(b[0] + a.x, b[1] + a.y, b[2] + a.z);
    }

    static float[] target(int husk) {
        final double[] h = CoilPonderScript.HUSK_AT[husk];
        final int[] c = CoilPonderScript.COIL;
        return new float[] {(float) (h[0] - c[0]), (float) (h[1] + HUSK_MID - c[1]), (float) (h[2] - c[2])};
    }

    public static KitInstruction drive(int start, int ticks) {
        final BlockPos at = pos(CoilPonderScript.COIL);
        final BlockPos inFar = pos(CoilPonderScript.IN_CONNECTOR);
        final BlockPos outFar = pos(CoilPonderScript.OUT_CONNECTOR);
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                SceneClock.set(level, tick);
                if (!(level.getBlockEntity(at) instanceof StormCoilBE be)) {
                    return;
                }
                final CoilPonderScript.Frame f = CoilPonderScript.at(tick);
                final CompoundTag tag = new CompoundTag();
                tag.putInt("Stage", f.stage());
                tag.putInt("BlockedAt", 0);
                tag.putString("Blocker", "");
                tag.putBoolean("Redstone", false);
                tag.putBoolean("Shared", false);
                tag.putBoolean("Corona", f.corona());
                if (f.lastStrike() != Long.MIN_VALUE) {
                    tag.putLong("LastStrike", f.lastStrike());
                }
                if (f.lastZap() != Long.MIN_VALUE) {
                    tag.putLong("LastZap", f.lastZap());
                }
                final int volley = Math.min(f.volley(), CoilPonderScript.HUSKS);
                final int[] bits = new int[3 * volley];
                for (int i = 0; i < volley; i++) {
                    final float[] p = target(i);
                    for (int k = 0; k < 3; k++) {
                        bits[3 * i + k] = Float.floatToIntBits(p[k]);
                    }
                }
                final float[] first = target(0);
                tag.putFloat("ZapX", first[0]);
                tag.putFloat("ZapY", first[1]);
                tag.putFloat("ZapZ", first[2]);
                tag.putInt("Volley", volley);
                tag.putIntArray("VolleyAt", bits);
                tag.putBoolean("Primed", f.primed());
                tag.putInt("Strikes", f.lastStrike() == Long.MIN_VALUE ? 0 : 1);
                tag.putInt("Capacitor", f.capacitor());
                tag.putInt("Bank", f.bank());
                tag.putInt("CapacitorSize", f.capacitorSize());
                tag.putInt("BankSize", f.bankSize());
                tag.putInt("InDraw", f.in());
                tag.putInt("OutExport", f.out());
                final ListTag spans = new ListTag();
                if (f.inWired()) {
                    spans.add(span(StormCoilStuds.IN, inFar, CoilPonderScript.IN_WIRE_TIER));
                }
                if (f.outWired()) {
                    spans.add(span(StormCoilStuds.OUT, outFar, CoilPonderScript.OUT_WIRE_TIER));
                }
                tag.put("StudWires", spans);
                watchAndAim(level, tag, tick);
                try {
                    be.readClient(tag, level.registryAccess());
                } catch (RuntimeException e) {
                    CreateStats.LOGGER.warn("[Ponder] the Storm Coil's state could not be set: {}", e.toString());
                }
            }
        };
    }

    static void watchAndAim(PonderLevel level, CompoundTag tag, int tick) {
        final int plate = CoilPonderScript.PLATE;
        final List<CoilWatch.Entry> watch = new java.util.ArrayList<>();
        final List<Integer> husks = new java.util.ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(0, 0, 0, plate, 8, plate), e -> !e.isRemoved())) {
            if (m instanceof Husk h) {
                if (h.deathTime == 0) {
                    watch.add(new CoilWatch.Entry(h.getId(), CoilWatch.Verdict.TARGET));
                    husks.add(h.getId());
                }
            } else {
                watch.add(new CoilWatch.Entry(m.getId(), CoilWatch.Verdict.IGNORED));
            }
        }
        tag.putIntArray("Watch", CoilWatch.pack(watch));
        tag.putLong("AimAt", husks.isEmpty() ? Long.MIN_VALUE : CoilPonderScript.aimAt(tick));
        tag.putIntArray("AimIds", husks.stream().mapToInt(Integer::intValue).toArray());
    }

    private static CompoundTag span(int stud, BlockPos far, WireTier tier) {
        final CompoundTag s = new CompoundTag();
        s.putByte("S", (byte) stud);
        s.putLong("P", far.asLong());
        s.putByte("T", (byte) tier.ordinal());
        return s;
    }

    public static KitInstruction power(Object slot, int start, int ticks) {
        final double slack = WireTension.STANDARD.slack();
        final Vec3 in = connector(CoilPonderScript.IN_CONNECTOR);
        final Vec3 out = connector(CoilPonderScript.OUT_CONNECTOR);
        final FePaths.Path feed = new FePaths.Path(FePaths.catenary(breakerStud(MainBreakerStuds.Stud.INPUT), in, slack));
        final FePaths.Path inWire = new FePaths.Path(FePaths.catenary(in, stud(StormCoilStuds.IN), slack));
        final FePaths.Path outWire = new FePaths.Path(FePaths.catenary(stud(StormCoilStuds.OUT), out, slack));
        return FeFlow.beads(slot, List.of(
                FeFlow.Stream.wire(feed, t -> CoilPonderScript.at(t).in(), CoilPonderScript.IN_WIRE_TIER, t -> true),
                FeFlow.Stream.wire(inWire, t -> CoilPonderScript.at(t).in(), CoilPonderScript.IN_WIRE_TIER,
                        CoilPonderScript::inWired),
                FeFlow.Stream.wire(outWire, t -> CoilPonderScript.at(t).out(), CoilPonderScript.OUT_WIRE_TIER,
                        CoilPonderScript::outWired)),
                FeFlow.GREEN, start, ticks);
    }

    public static KitInstruction mobs(int appear, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Husk[] husks;
            private boolean[] burning;
            private Mob pig;

            @Override
            protected void begin(PonderScene scene) {
                husks = null;
                pig = null;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                if (tick == appear) {
                    husks = new Husk[CoilPonderScript.HUSKS];
                    burning = new boolean[husks.length];
                    for (int i = 0; i < husks.length; i++) {
                        final Husk h = EntityType.HUSK.create(level);
                        if (h == null) continue;
                        place(h, CoilPonderScript.HUSK_AT[i]);
                        level.addFreshEntity(h);
                        husks[i] = h;
                        puff(level, CoilPonderScript.HUSK_AT[i], 12);
                    }
                    final Mob p = EntityType.PIG.create(level);
                    if (p != null) {
                        place(p, CoilPonderScript.PIG_AT);
                        level.addFreshEntity(p);
                        pig = p;
                        puff(level, CoilPonderScript.PIG_AT, 8);
                    }
                }
                if (pig != null) {
                    hold(pig, CoilPonderScript.PIG_AT);
                }
                if (husks == null) return;
                final CoilPonderScript.Frame f = CoilPonderScript.at(tick);
                final int lastVolley = CoilPonderScript.lastVolley();
                for (int i = 0; i < husks.length; i++) {
                    final Husk h = husks[i];
                    if (h == null || h.isRemoved()) continue;
                    hold(h, CoilPonderScript.HUSK_AT[i]);
                    if (f.lastZap() == tick) {
                        h.hurtTime = 10;
                        h.hurtDuration = 10;
                        burning[i] = true;
                    }
                    if (lastVolley >= 0 && tick > lastVolley) {
                        final int dying = tick - lastVolley;
                        h.deathTime = Math.min(DEATH_TICKS, dying);
                        if (dying >= DEATH_TICKS) {
                            puff(level, CoilPonderScript.HUSK_AT[i], 20);
                            h.discard();
                            continue;
                        }
                    }
                    h.setSharedFlagOnFire(burning[i]);
                }
            }
        };
    }

    static void place(Mob m, double[] at) {
        m.setNoAi(true);
        m.setSilent(true);
        m.setPersistenceRequired();
        hold(m, at);
    }

    static void hold(Mob m, double[] at) {
        final int[] c = CoilPonderScript.COIL;
        final float yaw = (float) (Mth.atan2(c[2] + 0.5 - at[2], c[0] + 0.5 - at[0]) * Mth.RAD_TO_DEG) - 90f;
        m.setPos(at[0], at[1], at[2]);
        m.setYRot(yaw);
        m.yRotO = yaw;
        m.yBodyRot = yaw;
        m.yBodyRotO = yaw;
        m.yHeadRot = yaw;
        m.yHeadRotO = yaw;
    }

    static void puff(PonderLevel level, double[] at, int n) {
        final Random r = new Random(n * 31L + (long) (at[0] * 7 + at[2] * 13));
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.POOF, at[0] + r.nextGaussian() * 0.3, at[1] + 0.2 + r.nextDouble() * 1.4,
                    at[2] + r.nextGaussian() * 0.3, r.nextGaussian() * 0.02, 0.02 + r.nextDouble() * 0.04,
                    r.nextGaussian() * 0.02);
        }
    }

    public static KitInstruction rain(int start, int ticks) {
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final Random r = new Random(tick * 977L);
                final int plate = CoilPonderScript.PLATE;
                for (int i = 0; i < 6; i++) {
                    scene.getWorld().addParticle(ParticleTypes.FALLING_WATER, r.nextDouble() * plate,
                            6.5 + r.nextDouble() * 2.0, r.nextDouble() * plate, 0, -0.4, 0);
                }
            }
        };
    }

    static final class Bolt extends Outline {
        int seed;
        float alpha;
        float prevAlpha;
        Vec3 top = Vec3.ZERO;
        Vec3 bottom = Vec3.ZERO;

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            final float a = Mth.lerp(pt, prevAlpha, alpha);
            if (a <= 0.02f) return;
            final VertexConsumer vc = buffer.getBuffer(PonderRenderTypes.outlineSolid());
            final Vector4f core = new Vector4f(1f, 1f, 1f, a);
            final Vector4f glow = new Vector4f(0.61f, 0.85f, 1f, a * 0.6f);
            final Random r = new Random(seed);
            final int n = 9;
            Vec3 prev = top;
            for (int i = 1; i <= n; i++) {
                final double u = i / (double) n;
                Vec3 next = top.lerp(bottom, u);
                if (i < n) {
                    next = next.add((r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.9);
                }
                final Vector3d p0 = new Vector3d(prev.x, prev.y, prev.z);
                final Vector3d p1 = new Vector3d(next.x, next.y, next.z);
                bufferCuboidLine(ms, vc, Vec3.ZERO, p0, p1, 0.22f, glow, LightTexture.FULL_BRIGHT, false);
                bufferCuboidLine(ms, vc, Vec3.ZERO, p0, p1, 0.08f, core, LightTexture.FULL_BRIGHT, false);
                prev = next;
            }
        }
    }

    public static KitInstruction strike(Object slot, int at, int ticks) {
        final int[] c = CoilPonderScript.COIL;
        final CoilForm form = CoilForm.at(4.0);
        final Vec3 crown = new Vec3(c[0] + 0.5, c[1] + (form.orbY + form.orb) / 16.0, c[2] + 0.5);
        return new KitInstruction(at, ticks) {
            private Bolt bolt;

            @Override
            protected void begin(PonderScene scene) {
                bolt = new Bolt();
                bolt.top = crown.add(0.4, 9.0, -0.3);
                bolt.bottom = crown;
                scene.getOutliner().showOutline(slot, bolt).lineWidth(1 / 32f);
                final Random r = new Random(at);
                for (int i = 0; i < 24; i++) {
                    scene.getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, crown.x, crown.y, crown.z,
                            r.nextGaussian() * 0.25, 0.1 + r.nextDouble() * 0.3, r.nextGaussian() * 0.25);
                }
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (bolt == null) return;
                final int age = tick - at;
                bolt.prevAlpha = bolt.alpha;

                bolt.alpha = age < 2 ? 1f : age < 3 ? 0.35f : age < 5 ? 0.9f : Math.max(0f, 1f - (age - 4) / 4f);
                bolt.seed = at * 17 + age / 2;
                if (!last) scene.getOutliner().keep(slot);
            }
        };
    }
}
