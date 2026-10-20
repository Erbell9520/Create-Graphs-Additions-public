package com.createstats.client.ponder.fe;

import com.createstats.CreateStats;
import com.createstats.block.InductionRangeBlock;
import com.createstats.block.LampBlock;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Kind;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.repeller.RepellerFrequencies;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

// the real blocks drawing themselves, no fakes
public final class FeProps {
    private FeProps() {
    }

    public record Spin(BlockPos pos, IntToDoubleFunction rpm) {
    }

    public static KitInstruction spin(List<Spin> blocks, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private final Map<BlockPos, Float> speed = new HashMap<>();

            @Override
            protected void begin(PonderScene scene) {
                speed.clear();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();

                final int clock = Math.max(1, PonderUI.ponderTicks);
                final double quantum = 300.0 / clock;
                for (Spin s : blocks) {
                    final double want = s.rpm().applyAsDouble(tick);
                    float now = speed.computeIfAbsent(s.pos(), p -> level.getBlockEntity(p) instanceof KineticBlockEntity k
                            ? k.getSpeed() : 0f);
                    final double steps = Math.rint((want - now) / quantum);
                    if (steps == 0) continue;
                    now = (float) (now + steps * quantum);
                    if (Math.abs(want) < 1.0e-3 && Math.abs(now) < quantum) now = 0f;
                    speed.put(s.pos(), now);
                    if (level.getBlockEntity(s.pos()) instanceof KineticBlockEntity k) k.setSpeed(now);
                }
            }
        };
    }

    public static List<Spin> driveLine(Recording rec, Warp warp) {
        final List<Spin> out = new ArrayList<>();
        final IntToDoubleFunction first = t -> rec.rpm[0][rec.at(warp.tick(t))];
        final IntToDoubleFunction after = t -> rec.rpm[1][rec.at(warp.tick(t))];
        for (BlockPos p : new BlockPos[] {FeStage.MOTOR, FeStage.GEARBOX_A, FeStage.SHAFT_AB, FeStage.CLUTCH}) {
            out.add(new Spin(p, first));
        }
        for (BlockPos p : new BlockPos[] {FeStage.GEARBOX_B, FeStage.SHAFT_BC, FeStage.GEARBOX_C}) {
            out.add(new Spin(p, after));
        }
        for (int d = 0; d < FeStage.DYNAMOS.length; d++) {
            final int dyn = d;
            out.add(new Spin(FeStage.DYNAMOS[d], t -> rec.rpm[dyn][rec.at(warp.tick(t))]));
        }
        return out;
    }

    public static List<Spin> motors(Recording rec, Warp warp) {
        final List<Spin> out = new ArrayList<>();
        for (int i = 0; i < rec.machines(); i++) {
            if (rec.machine(i).kind() != Kind.MOTOR) continue;
            final int m = i;
            final IntToDoubleFunction rpm = t -> rec.working[m][rec.at(warp.tick(t))] ? FeStage.RPM[m] : 0.0;
            out.add(new Spin(FeStage.MACHINE[i], rpm));
            if (FeStage.DRIVEN[i] != null) out.add(new Spin(FeStage.DRIVEN[i], rpm));
        }
        return out;
    }

    public static KitInstruction appliances(Recording rec, Warp warp, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private final int[] written = new int[rec.machines()];

            @Override
            protected void begin(PonderScene scene) {
                java.util.Arrays.fill(written, -1);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final int r = rec.at(warp.tick(tick));
                boolean changed = false;
                for (int i = 0; i < rec.machines(); i++) {
                    final boolean working = rec.working[i][r];
                    final boolean shed = rec.connected[i][r] && !rec.admitted[i][r];
                    final int state = working ? 2 : shed ? 5 : 1;
                    if (state == written[i]) continue;
                    written[i] = state;
                    final BlockPos pos = FeStage.MACHINE[i];
                    switch (rec.machine(i).kind()) {
                        case RANGE, LAMP -> changed |= look(level, pos, rec.machine(i).kind(), working);
                        case REPELLER -> repeller(level, pos, state, working);
                        default -> {
                        }
                    }
                }
                if (changed) scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
            }
        };
    }

    public record Worked(BlockPos pos, Kind kind, java.util.function.IntPredicate working) {
    }

    public static KitInstruction worked(List<Worked> parts, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private final Map<BlockPos, Boolean> written = new HashMap<>();

            @Override
            protected void begin(PonderScene scene) {
                written.clear();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                boolean changed = false;
                for (Worked w : parts) {
                    final boolean on = w.working().test(tick);
                    if (Boolean.valueOf(on).equals(written.get(w.pos()))) continue;
                    written.put(w.pos(), on);
                    changed |= look(scene.getWorld(), w.pos(), w.kind(), on);
                }
                if (changed) scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
            }
        };
    }

    public static KitInstruction range(BlockPos pos, java.util.function.IntUnaryOperator setting,
                                       java.util.function.IntPredicate starved, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int written = Integer.MIN_VALUE;

            @Override
            protected void begin(PonderScene scene) {
                written = Integer.MIN_VALUE;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final int s = Math.max(0, Math.min(3, setting.applyAsInt(tick)));
                final boolean hungry = starved.test(tick);
                final int state = s * 2 + (hungry ? 1 : 0);
                if (state == written) return;
                written = state;
                final PonderLevel level = scene.getWorld();
                final BlockState bs = level.getBlockState(pos);
                if (!bs.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) return;
                level.setBlock(pos, bs.setValue(BlazeBurnerBlock.HEAT_LEVEL,
                        com.createstats.blockentity.InductionRangeBE.heatFor(s))
                        .setValue(InductionRangeBlock.STARVED, hungry), 0);
                scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
            }
        };
    }

    private static boolean look(PonderLevel level, BlockPos pos, Kind kind, boolean working) {
        final BlockState bs = level.getBlockState(pos);
        if (kind == Kind.RANGE && bs.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            level.setBlock(pos, bs.setValue(BlazeBurnerBlock.HEAT_LEVEL, working
                    ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.NONE)
                    .setValue(InductionRangeBlock.STARVED, false), 0);
            return true;
        }
        if (kind == Kind.LAMP && bs.hasProperty(LampBlock.LIT)) {
            level.setBlock(pos, bs.setValue(LampBlock.LIT, working), 0);
            return true;
        }
        return false;
    }

    private static void repeller(PonderLevel level, BlockPos pos, int state, boolean working) {
        if (!(level.getBlockEntity(pos) instanceof SmartBlockEntity be)) return;
        try {
            final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            tag.putInt("State", state);
            tag.putInt("Tenths", tenths());
            tag.putFloat("Effective", working ? 16f : 0f);
            tag.putFloat("Power", working ? 1f : 0f);
            tag.putInt("Draw", working ? 64 : 0);
            tag.putInt("Getting", working ? 64 : 0);
            tag.putBoolean("OnCircuit", true);
            be.readClient(tag, level.registryAccess());
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] the repeller's state could not be set: {}", e.toString());
        }
    }

    private static int tenths() {
        try {
            return RepellerFrequencies.tenthsOf(EntityType.ZOMBIE);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static KitInstruction setState(BlockPos pos, java.util.function.UnaryOperator<BlockState> change, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                level.setBlock(pos, change.apply(level.getBlockState(pos)), 0);
                scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
            }
        };
    }

    public static KitInstruction lever(boolean on, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockState lever = level.getBlockState(FeStage.LEVER);
                if (lever.hasProperty(BlockStateProperties.POWERED)) {
                    level.setBlock(FeStage.LEVER, lever.setValue(BlockStateProperties.POWERED, on), 0);
                }
                final BlockState clutch = level.getBlockState(FeStage.CLUTCH);
                if (clutch.hasProperty(BlockStateProperties.POWERED)) {
                    level.setBlock(FeStage.CLUTCH, clutch.setValue(BlockStateProperties.POWERED, on), 0);
                }
                scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
            }
        };
    }
}
