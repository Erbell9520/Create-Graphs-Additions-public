package com.createstats.client.ponder;

import com.createstats.flight.block.RpmGovernorBlock;
import com.createstats.vccmk4.air.core.AerialPilot;
import com.createstats.vccmk4.air.core.AerialPonderFlight;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.core.Control;
import com.createstats.vehiclelight.VehicleLightBlock;
import com.createstats.vehiclelight.VehicleLightGlow;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

public final class Machines {
    private Machines() {
    }

    public static KitInstruction burners(AerialPonderFlight.Recording flight, List<BlockPos> burners, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int[] written;

            @Override
            protected void begin(PonderScene scene) {
                written = new int[burners.size()];
                java.util.Arrays.fill(written, -1);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                PonderLevel level = scene.getWorld();
                for (int i = 0; i < burners.size(); i++) {
                    int signal = flight.signal(tick, i);
                    if (signal == written[i]) continue;
                    boolean lit = written[i] > 0;
                    written[i] = signal;
                    BlockPos pos = burners.get(i);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) continue;
                    CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                    tag.putInt("SignalStrength", signal);
                    tag.putBoolean("IsPowered", signal > 0);
                    be.loadWithComponents(tag, level.registryAccess());
                    if (lit != (signal > 0)) powered(scene, pos, signal > 0);
                }
            }
        };
    }

    private static void powered(PonderScene scene, BlockPos pos, boolean on) {
        PonderLevel level = scene.getWorld();
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.POWERED) || state.getValue(BlockStateProperties.POWERED) == on) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, on), 0);
        redraw(scene);
    }

    static void redraw(PonderScene scene) {
        scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
    }

    // create draws a shaft at time*speed*0.3, change speed mid render and it jumps. round to quarter turns
    public static KitInstruction props(List<BlockPos> props, List<BlockPos> governors, IntFunction<double[]> rpm,
                                       int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private float[] speed;
            private int[] needle;

            @Override
            protected void begin(PonderScene scene) {
                speed = new float[props.size()];
                needle = new int[governors.size()];
                java.util.Arrays.fill(needle, Integer.MIN_VALUE);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                PonderLevel level = scene.getWorld();
                double[] want = rpm.apply(tick);

                int clock = Math.max(1, PonderUI.ponderTicks);
                double quantum = 300.0 / clock;
                for (int i = 0; i < props.size() && i < want.length; i++) {
                    double gap = want[i] - speed[i];
                    double steps = Math.rint(gap / quantum);
                    if (steps == 0) continue;
                    speed[i] = (float) (speed[i] + steps * quantum);
                    if (Math.abs(want[i]) < 1.0e-3 && Math.abs(speed[i]) < quantum) speed[i] = 0f;
                    if (level.getBlockEntity(props.get(i)) instanceof KineticBlockEntity k) k.setSpeed(speed[i]);
                }
                for (int i = 0; i < governors.size() && i < want.length; i++) {
                    int applied = (int) Math.round(Math.abs(want[i]));
                    if (applied == needle[i]) continue;
                    needle[i] = applied;
                    BlockPos pos = governors.get(i);
                    if (level.getBlockEntity(pos) instanceof SmartBlockEntity be) {
                        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                        tag.putInt("AppliedRpm", applied);
                        be.readClient(tag, level.registryAccess());
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.hasProperty(RpmGovernorBlock.VISUAL_STATE)) {
                        RpmGovernorBlock.VisualState v = applied != 0 ? RpmGovernorBlock.VisualState.ACTIVE
                                : RpmGovernorBlock.VisualState.IDLE;
                        if (state.getValue(RpmGovernorBlock.VISUAL_STATE) != v) {
                            level.setBlock(pos, state.setValue(RpmGovernorBlock.VISUAL_STATE, v), 0);
                            redraw(scene);
                        }
                    }
                }
            }
        };
    }

    public static KitInstruction bell(AerialPonderFlight.Recording flight, IntFunction<Vec3> at, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (!flight.rang(tick)) return;
                double pitch = 1.2 + 0.3 * flight.order(tick);
                double note = 12.0 + 12.0 * Math.log(pitch) / Math.log(2.0);
                Vec3 p = at.apply(tick);
                RandomSource r = scene.getWorld().getRandom();
                for (int i = 0; i < 3; i++) {
                    scene.getWorld().addParticle(ParticleTypes.NOTE, p.x + (r.nextDouble() - 0.5) * 0.5,
                            p.y + 0.9 + i * 0.25, p.z + (r.nextDouble() - 0.5) * 0.5, note / 24.0, 0.0, 0.0);
                }
            }
        };
    }

    public static KitInstruction leverBalloon(AerialPonderFlight.Recording lever, BlockPos handle, BlockPos burner,
                                              int step, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int written = -1;

            @Override
            protected void begin(PonderScene scene) {
                written = -1;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                int signal = lever.signal(Math.max(0, tick - step), 0);
                if (signal == written) return;
                boolean lit = written > 0;
                written = signal;
                PonderLevel level = scene.getWorld();
                if (level.getBlockEntity(handle) instanceof SmartBlockEntity be) {
                    CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                    tag.putInt("State", signal);
                    be.readClient(tag, level.registryAccess());
                }
                BlockEntity b = level.getBlockEntity(burner);
                if (b != null) {
                    CompoundTag tag = b.saveWithoutMetadata(level.registryAccess());
                    tag.putInt("SignalStrength", signal);
                    tag.putBoolean("IsPowered", signal > 0);
                    b.loadWithComponents(tag, level.registryAccess());
                }
                if (lit != (signal > 0)) powered(scene, burner, signal > 0);
            }
        };
    }

    public record Lamp(BlockPos pos, int colour, AirLight job) {
    }

    public static KitInstruction lamps(AerialPonderFlight.Recording flight, List<Lamp> lamps, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                PonderLevel level = scene.getWorld();
                AerialPilot.Mode mode = flight.mode(tick);
                boolean flying = mode.flying();
                AirLight.Signals s = new AirLight.Signals(flying, mode == AerialPilot.Mode.HELM,
                        mode == AerialPilot.Mode.MOORED, flight.keel(tick) < AirLight.LOW_HEIGHT, false,
                        flight.order(tick) < 0.0, flight.vy(tick), false, false, new double[Control.values().length], tick);
                boolean changed = false;
                for (Lamp lamp : lamps) {
                    BlockState state = level.getBlockState(lamp.pos());
                    if (!(state.getBlock() instanceof VehicleLightBlock block)) continue;
                    int strength = lamp.job().strength(s, false, lamp.colour());
                    VehicleLightGlow glow = block.kind().glow(strength, tick, false);
                    if (state.getValue(VehicleLightBlock.GLOW) != glow) {
                        level.setBlock(lamp.pos(), state.setValue(VehicleLightBlock.GLOW, glow), 0);
                        changed = true;
                    }
                }
                if (changed) redraw(scene);
            }
        };
    }

    public static KitInstruction shimmer(List<IntFunction<Vec3>> at, List<IntToDoubleFunction> signal, int start, int ticks) {
        DustParticleOptions warm = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.17f), 0.7f);
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                RandomSource r = scene.getWorld().getRandom();
                for (int i = 0; i < at.size(); i++) {
                    double s = signal.get(i).applyAsDouble(tick) / 15.0;
                    if (s <= 0.0 || r.nextDouble() > 0.25 + 0.6 * s) continue;
                    Vec3 p = at.get(i).apply(tick);
                    scene.getWorld().addParticle(warm, p.x + (r.nextDouble() - 0.5) * 0.4, p.y + r.nextDouble() * 0.6,
                            p.z + (r.nextDouble() - 0.5) * 0.4, 0.0, 0.6 + 0.8 * s, 0.0);
                }
            }
        };
    }

    public static KitInstruction sparkle(IntFunction<Vec3> at, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                Vec3 p = at.apply(tick);
                RandomSource r = scene.getWorld().getRandom();
                for (int i = 0; i < 12; i++) {
                    scene.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, p.x + (r.nextDouble() - 0.5) * 1.4,
                            p.y + r.nextDouble() * 1.2, p.z + (r.nextDouble() - 0.5) * 1.4, 0.0, 0.0, 0.0);
                }
            }
        };
    }
}
