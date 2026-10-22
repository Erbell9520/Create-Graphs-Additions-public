package com.createstats.renderer;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.client.AnimationClock;
import com.createstats.client.ModPartialModels;
import com.createstats.client.coupler.CouplerFx;
import com.createstats.client.coupler.ExchangeMotion;
import com.createstats.client.ponder.SceneClock;
import com.createstats.power.CouplerFault;
import com.createstats.power.CouplerSockets;
import com.createstats.power.PowerCouplerMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public class UniversalPowerCouplerRenderer implements BlockEntityRenderer<UniversalPowerCouplerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float AXLE_Y = 8.5f;
    private static final float AXLE_Z = 8.0f;
    private static final float DRIVER_Y = 4.5f;
    private static final float DRIVER_Z = 4.0f;
    private static final float LEVER_Y = 2.75f;
    private static final float LEVER_Z = 8.0f;
    private static final float SHOE_LIFT = 1.5f;
    private static final float LIFTER_Y = 13.5f;
    private static final float LIFTER_N_Z = 2.4f;
    private static final float LIFTER_S_Z = 13.6f;
    private static final float LIFTER_ANGLE = 55.0f;
    private static final float SHUTTER_X = 15.7f;
    private static final float SHUTTER_Y = 2.25f;
    private static final float SHUTTER_FALL = 95.0f;

    private static final double DRIVER_AUTHORED = 90.0;

    private static final double HOLD_OPEN_TICKS = 4.0;
    private static final double HOLD_DROP_TICKS = 3.0;

    private static final Map<UniversalPowerCouplerBE, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        final ExchangeMotion.Animator anim = new ExchangeMotion.Animator();
        PowerCouplerMode shownMode;
        double leverFrom;
        double leverSeenAt = Double.NaN;
        boolean shownTripped;
        boolean faultSeen;
        double faultSeenAt = Double.NaN;

        int capColour = 1;

        double holdTheirs = Double.NaN;
        double holdOurs = Double.NaN;
        double holdAt = Double.NaN;

        long fxTick = Long.MIN_VALUE;
    }

    public UniversalPowerCouplerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(UniversalPowerCouplerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = be.getBlockState();
        final Direction facing = be.foreignFace();
        final long now = SceneClock.now(level);
        final double t = AnimationClock.time(now, partialTick);
        final boolean ponder = level instanceof PonderLevel;
        final State s = STATES.computeIfAbsent(be, k -> new State());

        final PowerCouplerMode mode = be.mode();
        final int direction = mode == PowerCouplerMode.PULL ? 1 : mode == PowerCouplerMode.PUSH ? -1 : 0;
        final CouplerFault fault = be.fault();
        final int crossing = be.crossingFePerTick();
        final boolean bothHeld = mode == PowerCouplerMode.OFF || fault == CouplerFault.SWITCHED_OFF
                || fault == CouplerFault.LOOPED || fault == CouplerFault.FORCED_OFF;
        final boolean oursHeld = bothHeld || fault == CouplerFault.SHED;
        final boolean flowing = !fault.blocksFlow() && !bothHeld;

        s.anim.advance(t, direction, crossing, flowing);
        if (direction != 0) {
            s.capColour = direction;
        }
        easeHolds(s, t, bothHeld, oursHeld);

        final double q = s.anim.q();
        final boolean pullSense = s.anim.direction() > 0;
        final double star = ExchangeMotion.starAngle(q);
        final double driver = ExchangeMotion.driverAngle(q);
        final double period = ExchangeMotion.periodFraction(q, pullSense);
        final double lift = ExchangeMotion.shoeLift(period);
        final double openTheirs = Math.max(lift, s.holdTheirs);
        final double openOurs = Math.max(lift, s.holdOurs);
        final boolean dark = be.storedFe() <= 0 && crossing <= 0;
        final double lever = leverAngle(be, s, mode, now, partialTick, t, ponder);
        final double fall = shutterFall(be, s, fault, now, partialTick, t, ponder);

        renderWires(be, ms, buffer, packedLight);

        ms.pushPose();
        applyFacing(ms, facing);
        final VertexConsumer solid = buffer.getBuffer(RenderType.solid());

        turned(ms, solid, state, packedLight, ModPartialModels.COUPLER_DRUM, star, AXLE_Y, AXLE_Z);
        for (int i = 0; i < 4; i++) {
            final int lit = dark ? 0 : ExchangeMotion.capsLit(ExchangeMotion.cassetteLevel(i, q, pullSense));
            final PartialModel cassette = lit == 0 ? ModPartialModels.COUPLER_CASSETTE_DARK
                    : s.capColour > 0 ? ModPartialModels.COUPLER_CASSETTE_AMBER[lit - 1]
                    : ModPartialModels.COUPLER_CASSETTE_CYAN[lit - 1];
            turned(ms, solid, state, packedLight, cassette, star + 90.0 * i, AXLE_Y, AXLE_Z);
        }
        turned(ms, solid, state, packedLight, ModPartialModels.COUPLER_DRIVER, driver - DRIVER_AUTHORED, DRIVER_Y,
                DRIVER_Z);
        turned(ms, solid, state, packedLight, ModPartialModels.COUPLER_LEVER, lever, LEVER_Y, LEVER_Z);
        lifted(ms, solid, state, packedLight, ModPartialModels.COUPLER_SHOES_NORTH, SHOE_LIFT * (float) openTheirs);
        lifted(ms, solid, state, packedLight, ModPartialModels.COUPLER_SHOES_SOUTH, SHOE_LIFT * (float) openOurs);
        turned(ms, solid, state, packedLight, ModPartialModels.COUPLER_LIFTER_NORTH, LIFTER_ANGLE * openTheirs,
                LIFTER_Y, LIFTER_N_Z);
        turned(ms, solid, state, packedLight, ModPartialModels.COUPLER_LIFTER_SOUTH, -LIFTER_ANGLE * openOurs,
                LIFTER_Y, LIFTER_S_Z);
        drawShutter(ms, solid, state, packedLight, (float) fall);
        turned(ms, solid, state, packedLight, be.foreignEndpoint().present()
                ? ModPartialModels.COUPLER_LENS_NORTH_ON : ModPartialModels.COUPLER_LENS_NORTH_OFF, 0.0, 0.0f, 0.0f);
        turned(ms, solid, state, packedLight, be.gridConnected()
                ? ModPartialModels.COUPLER_LENS_SOUTH_ON : ModPartialModels.COUPLER_LENS_SOUTH_OFF, 0.0, 0.0f, 0.0f);
        ms.popPose();

        if (s.fxTick != now) {
            final boolean firstTick = s.fxTick == Long.MIN_VALUE;
            final boolean contiguous = !firstTick && now - s.fxTick <= 2L && now > s.fxTick;
            s.fxTick = now;
            final int swings = s.anim.takeSwingsBegun();
            final int seats = s.anim.takeSeated();
            final int breaks = s.anim.takeBroken();
            CouplerFx.tick(be, new CouplerFx.Frame(facing, direction, s.capColour, crossing, flowing,
                    openTheirs > 0.5, openOurs > 0.5, fault, q, pullSense,
                    contiguous ? swings : 0, contiguous ? seats : 0, contiguous ? breaks : 0, ponder));
        }
    }

    private static void easeHolds(State s, double t, boolean bothHeld, boolean oursHeld) {
        final double wantTheirs = bothHeld ? 1.0 : 0.0;
        final double wantOurs = oursHeld ? 1.0 : 0.0;
        if (Double.isNaN(s.holdAt) || t < s.holdAt || t - s.holdAt > 200.0) {
            s.holdTheirs = wantTheirs;
            s.holdOurs = wantOurs;
            s.holdAt = t;
            return;
        }
        final double dt = t - s.holdAt;
        s.holdAt = t;
        s.holdTheirs = toward(s.holdTheirs, wantTheirs, dt);
        s.holdOurs = toward(s.holdOurs, wantOurs, dt);
    }

    private static double toward(double now, double want, double dt) {
        return want > now ? Math.min(want, now + dt / HOLD_OPEN_TICKS) : Math.max(want, now - dt / HOLD_DROP_TICKS);
    }

    private static void turned(PoseStack ms, VertexConsumer vc, BlockState state, int light, PartialModel part,
                               double degrees, float py, float pz) {
        ms.pushPose();
        if (degrees != 0.0) {
            ms.translate(0.0f, py * PX, pz * PX);
            ms.mulPose(Axis.XP.rotationDegrees((float) degrees));
            ms.translate(0.0f, -py * PX, -pz * PX);
        }
        CachedBuffers.partial(part, state).light(light).renderInto(ms, vc);
        ms.popPose();
    }

    private static void lifted(PoseStack ms, VertexConsumer vc, BlockState state, int light, PartialModel part,
                               float dy) {
        ms.pushPose();
        ms.translate(0.0f, dy * PX, 0.0f);
        CachedBuffers.partial(part, state).light(light).renderInto(ms, vc);
        ms.popPose();
    }

    private static void drawShutter(PoseStack ms, VertexConsumer vc, BlockState state, int light, float fall) {
        ms.pushPose();
        if (fall != 0.0f) {
            ms.translate(SHUTTER_X * PX, SHUTTER_Y * PX, 0.0f);
            ms.mulPose(Axis.ZP.rotationDegrees(-SHUTTER_FALL * fall));
            ms.translate(-SHUTTER_X * PX, -SHUTTER_Y * PX, 0.0f);
        }
        CachedBuffers.partial(ModPartialModels.COUPLER_SHUTTER, state).light(light).renderInto(ms, vc);
        ms.popPose();
    }

    private static double leverAngle(UniversalPowerCouplerBE be, State s, PowerCouplerMode mode, long now,
                                     float partialTick, double t, boolean ponder) {
        final double target = ExchangeMotion.leverAngle(sense(mode));
        if (s.shownMode == null) {
            s.shownMode = mode;
            s.leverFrom = target;
            return target;
        }
        if (s.shownMode != mode) {
            final double showing = Double.isNaN(s.leverSeenAt) ? ExchangeMotion.leverAngle(sense(s.shownMode))
                    : ExchangeMotion.leverThrow(s.leverFrom, ExchangeMotion.leverAngle(sense(s.shownMode)),
                    t - s.leverSeenAt);
            s.leverFrom = showing;
            s.shownMode = mode;
            s.leverSeenAt = t;
        }
        if (Double.isNaN(s.leverSeenAt)) {
            return target;
        }
        return ExchangeMotion.leverThrow(s.leverFrom, target,
                sinceChange(be.modeChangedAt(), s.leverSeenAt, now, partialTick, t, ponder));
    }

    private static int sense(PowerCouplerMode mode) {
        return mode == PowerCouplerMode.PULL ? 1 : mode == PowerCouplerMode.PUSH ? -1 : 0;
    }

    private static double shutterFall(UniversalPowerCouplerBE be, State s, CouplerFault fault, long now,
                                      float partialTick, double t, boolean ponder) {
        final boolean tripped = fault == CouplerFault.LOOPED;
        if (!s.faultSeen) {
            s.faultSeen = true;
            s.shownTripped = tripped;
            return tripped ? 1.0 : 0.0;
        }
        if (s.shownTripped != tripped) {
            s.shownTripped = tripped;
            s.faultSeenAt = t;
        }
        if (Double.isNaN(s.faultSeenAt)) {
            return tripped ? 1.0 : 0.0;
        }
        return ExchangeMotion.shutterFall(tripped,
                sinceChange(be.faultChangedAt(), s.faultSeenAt, now, partialTick, t, ponder));
    }

    private static double sinceChange(long stamp, double seenAt, long now, float partialTick, double t,
                                      boolean ponder) {
        final double seen = t - seenAt;
        if (ponder || stamp == Long.MIN_VALUE || now < stamp) {
            return seen;
        }
        final long elapsed = now - stamp;
        return Math.min(elapsed + (double) partialTick, seen);
    }

    private static void renderWires(UniversalPowerCouplerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        final Level level = be.getLevel();
        if (level == null || be.links().isEmpty()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final Matrix4f matrix = ms.last().pose();
        for (BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(level, origin, link)) {
                continue;
            }
            final Vec3 from = CouplerSockets.anchor(be.getBlockState(), be.socketOf(link));
            final Vec3 to = PowerConnectorRenderer.attachPoint(level, link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, level, origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static void applyFacing(PoseStack ms, Direction facing) {
        final float yDeg = yDegrees(facing);
        if (yDeg == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-yDeg));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    public static float yDegrees(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(UniversalPowerCouplerBE be) {
        return !be.links().isEmpty();
    }

    @Override
    public AABB getRenderBoundingBox(UniversalPowerCouplerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return ModConfiguration.cappedViewDistance(48);
    }
}
