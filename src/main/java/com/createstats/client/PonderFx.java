package com.createstats.client;

import com.createstats.blockentity.StageLightBE;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** [1] */
public final class PonderFx {

    private PonderFx() {
    }

    // ==================================================================================
    // Pacing
    // ==================================================================================

    /** [2] */
    public static void beat(CreateSceneBuilder scene, SceneBuildingUtil util, String text,
                            BlockPos target, PonderPalette color, int duration) {
        scene.overlay().showText(duration)
                .text(text)
                .pointAt(util.vector().centerOf(target))
                .colored(color)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(duration + 10);
    }

    /** A follow-up line under the beat before it. Not keyframed: it is not its own chapter. */
    public static void aside(CreateSceneBuilder scene, SceneBuildingUtil util, String text,
                             BlockPos target, PonderPalette color, int duration) {
        scene.overlay().showText(duration)
                .text(text)
                .pointAt(util.vector().centerOf(target))
                .colored(color)
                .placeNearTarget();
        scene.idle(duration + 8);
    }

    /** A beat that also rings the selection it is talking about. */
    public static void beatOutlined(CreateSceneBuilder scene, SceneBuildingUtil util, String text,
                                    Selection selection, BlockPos target, PonderPalette color, int duration) {
        scene.overlay().showOutlineWithText(selection, duration)
                .text(text)
                .pointAt(util.vector().centerOf(target))
                .colored(color)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(duration + 10);
    }

    /** Reveals positions one at a time from the given direction, with a gap between each. */
    public static void reveal(CreateSceneBuilder scene, SceneBuildingUtil util,
                              net.minecraft.core.Direction from, int gap, BlockPos... positions) {
        for (BlockPos pos : positions) {
            scene.world().showSection(util.select().position(pos), from);
            scene.idle(gap);
        }
    }

    /** Points the camera's interest at a block, so the framing follows the story. */
    public static void look(CreateSceneBuilder scene, BlockPos pos) {
        scene.special().movePointOfInterest(pos);
    }

    // ==================================================================================
    // Custom instruction: brownout flicker
    // ==================================================================================

    /**
     * Flickers a boolean blockstate the way a real starved consumer flickers in world.
     *
     * <p>The curve is the one in {@code FePowerVisuals.brownoutFlicker}: a base level that sinks as
     * the buffer empties, punctuated by short dips, quantised into slots so it reads as unstable
     * supply rather than per-frame noise. Reproducing it rather than blinking on a timer is the
     * point; a player who has seen a lamp brown out in their base recognises this immediately.</p>
     *
     * @param fromFill buffer fill percent at the start, 0..1
     * @param toFill   buffer fill percent at the end, 0..1
     */
    public static void brownout(CreateSceneBuilder scene, BlockPos pos, BooleanProperty lit,
                                float fromFill, float toFill, int ticks) {
        scene.addInstruction(new BrownoutInstruction(pos, lit, fromFill, toFill, ticks));
    }

    private static final class BrownoutInstruction extends TickingInstruction {

        /** Below this fraction of the buffer a consumer is in brownout. Mirrors {@code FeConsumer}. */
        private static final float LOW_POWER_PCT = 0.20f;
        /** Slot length in ticks. The in-world curve quantises to ~80ms, which is around two ticks. */
        private static final int SLOT = 2;

        private final BlockPos pos;
        private final BooleanProperty lit;
        private final float fromFill;
        private final float toFill;

        private BrownoutInstruction(BlockPos pos, BooleanProperty lit, float fromFill, float toFill, int ticks) {
            super(false, ticks);
            this.pos = pos;
            this.lit = lit;
            this.fromFill = fromFill;
            this.toFill = toFill;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            final PonderLevel level = scene.getWorld();
            if (level == null) {
                return;
            }
            final BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(lit)) {
                return;
            }
            final float t = totalTicks == 0 ? 1f : 1f - (float) remainingTicks / totalTicks;
            final float fill = Mth.lerp(t, fromFill, toFill);
            final boolean on = brightness(totalTicks - remainingTicks, fill) > 0.35f;
            if (state.getValue(lit) != on) {
                level.setBlock(pos, state.setValue(lit, on), 3);
            }
        }

        /** The {@code FePowerVisuals} curve, sampled per slot instead of per millisecond. */
        private static float brightness(int tick, float fill) {
            if (fill <= 0f) {
                return 0f;
            }
            if (fill >= LOW_POWER_PCT) {
                return 1f;
            }
            final float depth = Mth.clamp(fill / LOW_POWER_PCT, 0f, 1f);
            final float base = 0.45f + 0.50f * depth;
            final float noise = hash01(tick / SLOT);
            final float dipChance = 0.30f - 0.18f * depth;
            if (noise < dipChance) {
                return base * 0.30f;
            }
            if (noise < dipChance + 0.12f) {
                return base * 0.70f;
            }
            return base;
        }

        /** Deterministic so the flicker is identical every replay of the scene. */
        private static float hash01(long value) {
            long x = value * 0x9E3779B97F4A7C15L;
            x ^= x >>> 31;
            x *= 0xBF58476D1CE4E5B9L;
            x ^= x >>> 27;
            return Math.abs((float) (x % 10000)) / 10000f;
        }
    }

    // ==================================================================================
    // Custom instruction: travelling energy pulse
    // ==================================================================================

    /**
     * Runs a bright pulse along an ordered path of block positions.
     *
     * <p>Create fakes flow direction with two chased bounding boxes, a small one then a long one, so
     * a single outline appears to stretch down a pipe. That works for a straight two-block run and
     * nothing else. This walks a genuine pulse along an arbitrary path, corners included, which is
     * what a surface cable run actually needs.</p>
     *
     * @param ticksPerStep how long the pulse dwells on each block. Two or three reads as current.
     */
    public static void energyPulse(CreateSceneBuilder scene, PonderPalette color, Object slot,
                                   List<BlockPos> path, int ticksPerStep) {
        scene.addInstruction(new EnergyPulseInstruction(color, slot, List.copyOf(path), ticksPerStep));
    }

    private static final class EnergyPulseInstruction extends TickingInstruction {

        private final PonderPalette color;
        private final Object slot;
        private final List<BlockPos> path;
        private final int ticksPerStep;
        private int lastStep = -1;

        private EnergyPulseInstruction(PonderPalette color, Object slot, List<BlockPos> path, int ticksPerStep) {
            super(false, Math.max(1, path.size() * Math.max(1, ticksPerStep)));
            this.color = color;
            this.slot = slot;
            this.path = path;
            this.ticksPerStep = Math.max(1, ticksPerStep);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            if (path.isEmpty()) {
                return;
            }
            final int elapsed = totalTicks - remainingTicks;
            final int step = Math.min(path.size() - 1, elapsed / ticksPerStep);
            if (step != lastStep) {
                lastStep = step;
            }
            // [3]
            scene.getOutliner()
                    .chaseAABB(slot, new AABB(path.get(step)).inflate(0.08))
                    .lineWidth(1 / 12f)
                    .colored(color.getColor());
        }
    }

    // ==================================================================================
    // Custom instruction: stage light aim sweep
    // ==================================================================================

    /** [4] */
    public static void sweepAim(CreateSceneBuilder scene, BlockPos pos, int fromPan, int fromTilt,
                                int toPan, int toTilt, int ticks) {
        scene.addInstruction(new AimSweepInstruction(pos, fromPan, fromTilt, toPan, toTilt, ticks));
    }

    private static final class AimSweepInstruction extends TickingInstruction {

        private final BlockPos pos;
        private final int fromPan;
        private final int fromTilt;
        private final int toPan;
        private final int toTilt;

        private AimSweepInstruction(BlockPos pos, int fromPan, int fromTilt, int toPan, int toTilt, int ticks) {
            super(false, Math.max(1, ticks));
            this.pos = pos;
            this.fromPan = fromPan;
            this.fromTilt = fromTilt;
            this.toPan = toPan;
            this.toTilt = toTilt;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            final PonderLevel level = scene.getWorld();
            if (level == null || !(level.getBlockEntity(pos) instanceof StageLightBE light)) {
                return;
            }
            final float t = totalTicks == 0 ? 1f : 1f - (float) remainingTicks / totalTicks;
            final float eased = t * t * (3f - 2f * t);
            light.applyConfig(
                    Math.round(Mth.lerp(eased, fromPan, toPan)),
                    Math.round(Mth.lerp(eased, fromTilt, toTilt)),
                    light.getRange(), light.getBrightness(), light.getFocus(), light.getTint(),
                    true, true, light.getBeamWidth(), light.getLightSpread(), 0, 5);
        }
    }

    // ==================================================================================
    // Custom instruction: live budget meter
    // ==================================================================================

    /**
     * A row of pips floating in the scene, lit for served demand and dark red for shed demand.
     *
     * <p>Ponder has no gauge, no bar and no HUD: everything it can say about a quantity has to be
     * said in a sentence or implied by a block changing state. That is a real ceiling for the Power
     * Distribution Unit, whose entire subject is a <i>budget</i> shrinking and legs falling off the
     * bottom of it. A player cannot be shown that with block states, and being told it in prose is
     * exactly what made the first version of that scene flat.</p>
     *
     * <p>So this draws one. It is built out of the outliner, which is the only per-tick drawing
     * surface a scene instruction can reach, with one boxed pip per unit of demand: served pips take
     * the given colour, the rest go red. Easing the served count over the instruction's life makes
     * the budget visibly sag as generation drops, in step with the dynamo slowing down beside it.</p>
     *
     * @param anchor centre of the row, in scene space
     * @param pips   total demand, which stays fixed
     * @param from   how many pips are served at the start
     * @param to     how many are served at the end
     */
    public static void budgetMeter(CreateSceneBuilder scene, Vec3 anchor, int pips,
                                   float from, float to, PonderPalette served, int ticks) {
        scene.addInstruction(new BudgetMeterInstruction(anchor, pips, from, to, served, ticks));
    }

    private static final class BudgetMeterInstruction extends TickingInstruction {

        private static final double SPACING = 0.34;
        private static final double PIP = 0.11;

        private final Vec3 anchor;
        private final int pips;
        private final float from;
        private final float to;
        private final PonderPalette served;
        /** One stable slot per pip. Reusing a slot would make the outliner chase between pips. */
        private final Object[] slots;

        private BudgetMeterInstruction(Vec3 anchor, int pips, float from, float to,
                                       PonderPalette served, int ticks) {
            super(false, Math.max(1, ticks));
            this.anchor = anchor;
            this.pips = Math.max(1, pips);
            this.from = from;
            this.to = to;
            this.served = served;
            this.slots = new Object[this.pips];
            for (int i = 0; i < this.pips; i++) {
                slots[i] = new Object();
            }
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            final float t = totalTicks == 0 ? 1f : 1f - (float) remainingTicks / totalTicks;
            final float live = Mth.lerp(t, from, to);
            final double left = anchor.x - (pips - 1) * SPACING / 2.0;
            for (int i = 0; i < pips; i++) {
                final Vec3 at = new Vec3(left + i * SPACING, anchor.y, anchor.z);
                // Partial credit on the boundary pip: it shrinks as the budget crosses it, so the
                // meter slides rather than stepping a whole unit at a time.
                final float fill = Mth.clamp(live - i, 0f, 1f);
                final double size = PIP * (0.45 + 0.55 * fill);
                scene.getOutliner()
                        .showAABB(slots[i], new AABB(at, at).inflate(size))
                        .lineWidth(1 / 20f)
                        .colored(fill > 0.5f ? served.getColor() : PonderPalette.RED.getColor());
            }
        }
    }

    // ==================================================================================
    // Custom instruction: eased gauge sweep
    // ==================================================================================

    /** [5] */
    public static void sweepGauge(CreateSceneBuilder scene, BlockPos pos, IntegerProperty property,
                                  int from, int to, int ticks) {
        scene.addInstruction(new GaugeSweepInstruction(pos, property, from, to, ticks));
    }

    private static final class GaugeSweepInstruction extends TickingInstruction {

        private final BlockPos pos;
        private final IntegerProperty property;
        private final int from;
        private final int to;
        private int lastValue = Integer.MIN_VALUE;

        private GaugeSweepInstruction(BlockPos pos, IntegerProperty property, int from, int to, int ticks) {
            super(false, Math.max(1, ticks));
            this.pos = pos;
            this.property = property;
            this.from = from;
            this.to = to;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            final PonderLevel level = scene.getWorld();
            if (level == null) {
                return;
            }
            final BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(property)) {
                return;
            }
            final float t = totalTicks == 0 ? 1f : 1f - (float) remainingTicks / totalTicks;
            // Ease out when filling, ease in when draining: both taper at the interesting end.
            final float eased = to >= from ? 1f - (1f - t) * (1f - t) : t * t;
            final int value = Math.round(Mth.lerp(eased, from, to));
            if (value == lastValue) {
                return;
            }
            lastValue = value;
            if (state.getValue(property) != value) {
                level.setBlock(pos, state.setValue(property, value), 3);
            }
        }
    }

}
