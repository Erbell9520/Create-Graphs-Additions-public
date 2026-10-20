package com.createstats.renderer;

import com.createstats.block.DoorEase;
import com.createstats.block.DoorMaterial;
import com.createstats.block.DoorOpening;
import com.createstats.block.PoweredDoorBlock;
import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// empty block model, every opening moves the same geometry
@OnlyIn(Dist.CLIENT)
public class PoweredDoorRenderer implements BlockEntityRenderer<PoweredDoorBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float LEAF_D = PoweredDoorBlock.LEAF_D;

    private static final float JAMB_INSET = LEAF_D / 2.0f + 0.5f;

    private static final float CREASE_X = 8.0f;
    private static final float CREASE_Z = LEAF_D / 2.0f;

    private static final float STACK_Z = -0.2f;

    private static final float QUARTER = 90.0f;

    private static final float FOLD_OUTER = 84.0f;
    private static final float FOLD_INNER = 168.0f;

    private static final float FOLD_PHASE = 0.55f;
    private static final float SLIDE_PHASE = 0.45f;

    private static final float TRACK_Z = -(LEAF_D + 1.0f);
    private static final float TRACK_PHASE = 0.25f;

    private static final float STACK_TRACK_Z = TRACK_Z * 2.0f;

    private static final float HANDOVER = 0.8f;

    private static final float PLUG_PHASE = 0.35f;
    private static final float PLUG_LAG = 0.16f;

    public PoweredDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    private enum Part { FULL, LOW, HIGH }

    private record Leaves(PartialModel full, PartialModel low, PartialModel high,
                          ModPartialModels.DoorModels set) {
        static Leaves of(ModPartialModels.DoorModels set, boolean seam, boolean hingeRight) {
            if (!seam) {
                return new Leaves(set.leaf(), set.leafL(), set.leafR(), set);
            }
            return hingeRight
                    ? new Leaves(set.seamLo(), set.seamL(), set.leafR(), set)
                    : new Leaves(set.seamHi(), set.leafL(), set.seamR(), set);
        }

        PartialModel of(Part part) {
            return switch (part) {
                case FULL -> full;
                case LOW -> low;
                case HIGH -> high;
            };
        }
    }

    private enum Lamp {
        SHUT, OPEN, MOVING, DENIED;

        PartialModel of(ModPartialModels.DoorModels set) {
            return switch (this) {
                case SHUT -> set.lampShut();
                case OPEN -> set.lampOpen();
                case MOVING -> set.lampMoving();
                case DENIED -> set.lampDenied();
            };
        }

        static Lamp of(PoweredDoorBE be, float partialTick) {
            if (be.refusedFor(partialTick) >= 0.0f) {
                return DENIED;
            }
            if (!be.settled()) {
                return MOVING;
            }
            return be.isOpen() ? OPEN : SHUT;
        }
    }

    private record Pen(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                       Leaves leaves, Lamp lamp) {
        void draw(Part part) {
            put(light, leaves.of(part), RenderType.solid());

            if (part != Part.HIGH) {
                put(light, leaves.set().glassL(), RenderType.translucent());
            }
            if (part != Part.LOW) {
                put(light, leaves.set().glassR(), RenderType.translucent());
            }

            if (part != Part.HIGH) {
                put(LightTexture.FULL_BRIGHT, lamp.of(leaves.set()), RenderType.solid());
            }
        }

        void full() {
            draw(Part.FULL);
        }

        void atHinge(boolean hingeRight) {
            draw(hingeRight ? Part.HIGH : Part.LOW);
        }

        void atSeam(boolean hingeRight) {
            draw(hingeRight ? Part.LOW : Part.HIGH);
        }

        void put(int lit, PartialModel model, RenderType layer) {
            CachedBuffers.partial(model, state)
                    .light(lit)
                    .renderInto(ms, buffer.getBuffer(layer));
        }

        void push() {
            ms.pushPose();
        }

        void pop() {
            ms.popPose();
        }
    }

    @Override
    public void render(PoweredDoorBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PoweredDoorBlock)) {
            return;
        }

        final float t = be.progress(partialTick);
        final boolean hingeRight = state.getValue(PoweredDoorBlock.HINGE) == DoorHingeSide.RIGHT;
        final DoorOpening style = be.effectiveStyle();
        final DoorMaterial material = ((PoweredDoorBlock) state.getBlock()).material();
        final DoorEase ease = style.ease();
        final boolean opening = be.isOpen();
        final Pen pen = new Pen(ms, buffer, state, travelLight(be, state, style, hingeRight, t, light),
                Leaves.of(ModPartialModels.poweredDoor(material), be.seamDrawn(), hingeRight),
                Lamp.of(be, partialTick));

        ms.pushPose();
        applyFacing(ms, state.getValue(PoweredDoorBlock.FACING));
        nudge(ms, be.refusedFor(partialTick));

        switch (style) {
            case SWING -> renderSwing(pen, t, opening, ease, hingeRight);
            case SLIDE -> renderSlide(pen, t, opening, ease, hingeRight);
            case WINGS -> renderWings(pen, t, opening, ease);
            case FOLD_PARK, CONCERTINA -> renderFoldPark(pen, t, opening, ease, hingeRight);
            case FOLD_RETRACT -> renderFoldRetract(pen, t, opening, ease, hingeRight);
            case STACK_SLIDE -> renderStackSlide(pen, t, opening, ease, hingeRight);
            case PLUG_RETRACT -> renderPlugRetract(pen, t, opening, ease, hingeRight);
            case ROLL_UP -> renderRollUp(pen, t, opening, ease);
        }

        ms.popPose();
    }

    private static final float NUDGE_PX = 0.5f;

    private static void nudge(PoseStack ms, float refused) {
        if (refused < 0.0f) {
            return;
        }
        final float decay = 1.0f - refused;
        final float offset = NUDGE_PX * Mth.sin(refused * Mth.PI * 4.0f) * decay * decay;
        ms.translate(0.0, 0.0, offset * PX);
    }

    private static int travelLight(PoweredDoorBE be, BlockState state, DoorOpening style,
                                   boolean hingeRight, float t, int here) {
        final Level level = be.getLevel();
        if (level == null || t <= 0.0f) {
            return here;
        }
        final Direction facing = state.getValue(PoweredDoorBlock.FACING);
        final Direction plusX = PoweredDoorBlock.widthAxis(facing);
        final Direction away = hingeRight ? plusX : plusX.getOpposite();
        final BlockPos parked = switch (style) {
            case SLIDE, PLUG_RETRACT -> be.getBlockPos().relative(away).relative(facing);

            case STACK_SLIDE -> be.getBlockPos()
                    .relative(plusX.getOpposite(), hingeRight ? 2 : 1).relative(facing);
            default -> null;
        };
        return parked == null ? here : mixLight(here, LevelRenderer.getLightColor(level, parked), t);
    }

    private static int mixLight(int a, int b, float f) {
        final int block = Math.round(Mth.lerp(f, (a >> 4) & 0xF, (b >> 4) & 0xF));
        final int sky = Math.round(Mth.lerp(f, (a >> 20) & 0xF, (b >> 20) & 0xF));
        return LightTexture.pack(block, sky);
    }

    private static void renderSwing(Pen pen, float t, boolean opening, DoorEase ease,
                                    boolean hingeRight) {
        final float turn = ease.curve(t, opening);
        pen.push();
        pivotY(pen.ms(), jambX(hingeRight), JAMB_INSET, (hingeRight ? QUARTER : -QUARTER) * turn);
        pen.full();
        pen.pop();
    }

    private static void renderSlide(Pen pen, float t, boolean opening, DoorEase ease,
                                    boolean hingeRight) {
        final float onTrack = ease.curve(phase(t, 0.0f, TRACK_PHASE), opening);
        final float travel = ease.curve(phase(t, TRACK_PHASE * HANDOVER, 1.0f), opening);
        pen.push();
        pen.ms().translate((hingeRight ? 16.0f : -16.0f) * travel * PX, 0.0, TRACK_Z * onTrack * PX);
        pen.full();
        pen.pop();
    }

    private static final float LEAF_TOP = 32.0f;

    private static final float HOOD_DEPTH = 3.0f;

    private static void renderRollUp(Pen pen, float t, boolean opening, DoorEase ease) {
        final float remain = Mth.lerp(ease.curve(t, opening), 1.0f, HOOD_DEPTH / LEAF_TOP);
        pen.push();
        pen.ms().translate(0.0, LEAF_TOP * PX, 0.0);
        pen.ms().scale(1.0f, remain, 1.0f);
        pen.ms().translate(0.0, -LEAF_TOP * PX, 0.0);
        pen.full();
        pen.pop();
    }

    private static void renderWings(Pen pen, float t, boolean opening, DoorEase ease) {
        final float turn = ease.curve(t, opening);
        pen.push();
        pivotY(pen.ms(), JAMB_INSET, JAMB_INSET, -QUARTER * turn);
        pen.draw(Part.LOW);
        pen.pop();

        pen.push();
        pivotY(pen.ms(), 16.0f - JAMB_INSET, JAMB_INSET, QUARTER * turn);
        pen.draw(Part.HIGH);
        pen.pop();
    }

    private static void renderFoldPark(Pen pen, float t, boolean opening, DoorEase ease,
                                       boolean hingeRight) {
        final float fold = ease.curve(t, opening);
        final float outer = (hingeRight ? FOLD_OUTER : -FOLD_OUTER) * fold;
        final float inner = (hingeRight ? -FOLD_INNER : FOLD_INNER) * fold;

        pen.push();
        pivotY(pen.ms(), jambX(hingeRight), JAMB_INSET, outer);
        pen.atHinge(hingeRight);

        pen.push();
        pivotY(pen.ms(), CREASE_X, CREASE_Z, inner);
        pen.atSeam(hingeRight);
        pen.pop();

        pen.pop();
    }

    private static void renderFoldRetract(Pen pen, float t, boolean opening, DoorEase ease,
                                          boolean hingeRight) {
        final float fold = ease.curve(phase(t, 0.0f, FOLD_PHASE), opening);
        final float slide = ease.curve(phase(t, SLIDE_PHASE, 1.0f), opening);

        final float onTrack = ease.curve(phase(t, 0.0f, SLIDE_PHASE), opening);

        pen.push();
        pen.ms().translate((hingeRight ? 8.0f : -8.0f) * slide * PX, 0.0, TRACK_Z * onTrack * PX);

        pen.atHinge(hingeRight);

        pen.push();
        pivotY(pen.ms(), CREASE_X, STACK_Z, (hingeRight ? -180.0f : 180.0f) * fold);
        pen.atSeam(hingeRight);
        pen.pop();

        pen.pop();
    }

    private static void renderStackSlide(Pen pen, float t, boolean opening, DoorEase ease,
                                         boolean hingeRight) {
        final float onTrack = ease.curve(phase(t, 0.0f, TRACK_PHASE), opening);
        final float along = ease.curve(phase(t, TRACK_PHASE * HANDOVER, 1.0f), opening);
        final float travel = hingeRight ? -32.0f : -16.0f;
        final float depth = hingeRight ? STACK_TRACK_Z : TRACK_Z;

        pen.push();
        pen.ms().translate(travel * along * PX, 0.0, depth * onTrack * PX);
        pen.full();
        pen.pop();
    }

    private static void renderPlugRetract(Pen pen, float t, boolean opening, DoorEase ease,
                                          boolean hingeRight) {
        final float local = hingeRight ? phase(t, PLUG_LAG, 1.0f) : t;
        final float plug = ease.curve(phase(local, 0.0f, PLUG_PHASE), opening);
        final float slide = ease.curve(phase(local, PLUG_PHASE * HANDOVER, 1.0f), opening);

        pen.push();
        pen.ms().translate((hingeRight ? 16.0f : -16.0f) * slide * PX, 0.0, TRACK_Z * plug * PX);
        pen.full();
        pen.pop();
    }

    private static float phase(float t, float from, float to) {
        return Mth.clamp((t - from) / (to - from), 0.0f, 1.0f);
    }

    private static void pivotY(PoseStack ms, float axPx, float azPx, float degrees) {
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(axPx * PX, 0.0, azPx * PX);
        ms.mulPose(Axis.YP.rotationDegrees(degrees));
        ms.translate(-axPx * PX, 0.0, -azPx * PX);
    }

    private static float jambX(boolean hingeRight) {
        return hingeRight ? 16.0f - JAMB_INSET : JAMB_INSET;
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    public AABB getRenderBoundingBox(PoweredDoorBE be) {
        return new AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0).inflate(2.0, 0.0, 2.0);
    }
}
