package com.createstats.surveyor.client;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorStaffItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class SurveyorStaffRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation STAFF = part("staff");
    public static final ResourceLocation COMPASS = part("compass");
    public static final ResourceLocation NEEDLE = part("needle");
    public static final ResourceLocation BUBBLE = part("bubble");
    public static final ResourceLocation VANE_FRONT = part("vane_front");
    public static final ResourceLocation VANE_REAR = part("vane_rear");
    public static final ResourceLocation WHEEL = part("wheel");
    public static final ResourceLocation GLASS_0 = part("glass0");
    public static final ResourceLocation GLASS_1 = part("glass1");
    public static final ResourceLocation GLASS_2 = part("glass2");
    public static final ResourceLocation GLASS_3 = part("glass3");
    public static final ResourceLocation DETENT = part("detent");
    public static final ResourceLocation REEL = part("reel");
    public static final ResourceLocation CRANK = part("crank");
    public static final ResourceLocation LINE = part("line");
    public static final ResourceLocation HOOK = part("hook");

    public static final ResourceLocation[] PARTS = {STAFF, COMPASS, NEEDLE, BUBBLE, VANE_FRONT, VANE_REAR, WHEEL, GLASS_0,
            GLASS_1, GLASS_2, GLASS_3, DETENT, REEL, CRANK, LINE, HOOK};

    static final ResourceLocation[] GLASSES = {GLASS_0, GLASS_1, GLASS_2, GLASS_3};

    static final Set<ResourceLocation> LIFTED = Set.of(COMPASS, NEEDLE, BUBBLE, VANE_FRONT, VANE_REAR, WHEEL, GLASS_0,
            GLASS_1, GLASS_2, GLASS_3, DETENT);
    static final float HEAD_LIFT = 1f;

    static final float BALL_X = 0.5f, BALL_Y = 28.6f / 16f, BALL_Z = 0.5f;
    static final float NEEDLE_Y = 31.95f / 16f;
    static final float VANE_Y = 31.7f / 16f, VANE_FRONT_Z = -1.9f / 16f, VANE_REAR_Z = 17.9f / 16f;
    static final float WHEEL_X = 10f / 16f, WHEEL_Y = 39.7f / 16f, WHEEL_Z = 18.7f / 16f;
    static final float DETENT_X = 11.4f / 16f, DETENT_Y = 42.8f / 16f, DETENT_Z = 18.75f / 16f;
    static final float REEL_X = 3f / 16f, REEL_Y = 19.3f / 16f, REEL_Z = 0.5f;
    static final float NOZZLE_X = 4.2f / 16f, NOZZLE_Y = 15.6f / 16f, NOZZLE_Z = 0.5f;

    static final float LINE_REST = 1.2f;

    static final float HOOK_DROP = 1.45f;

    static final double CRANK_PER_BLOCK = 90.0;

    static final double GLASS_AT_SLIT = 20.0;

    static final float GLASS_DIM = 0.62f;

    static final float GLASS_GREY = 0xDC / 255f;

    static final float STRIKE = 0.25f;

    static final double REDRAW_GAP = 0.3;

    static final double IN_WORLD = 3.0;

    static final double LOOK_REACH = 32.0;

    static final long FORGET_NANOS = 5_000_000_000L;

    private static SurveyorStaffRenderer instance;

    private static final class Motion {
        long seenNanos;
        long drawnNanos;
        boolean aimed;
        double yaw;
        double lastYaw;
        float lastSwing;
        int knocks;
        final StaffMotion.Needle needle = new StaffMotion.Needle();
        final StaffMotion.Spring bubble = new StaffMotion.Spring(StaffMotion.BUBBLE_OMEGA, StaffMotion.BUBBLE_ZETA);
        final StaffMotion.Bob hook = new StaffMotion.Bob();
    }

    private final Map<Long, Motion> motions = new HashMap<>();
    private long lastSweep;

    private StaffMode wheelMode;
    private double wheelFrom;
    private double wheelTo;
    private double wheelNow;

    private SurveyorStaffRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static SurveyorStaffRenderer get() {
        if (instance == null) instance = new SurveyorStaffRenderer();
        return instance;
    }

    private static ResourceLocation part(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "item/surveyor_staff/jacob/" + name);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers,
                             int light, int overlay) {
        final Minecraft mc = Minecraft.getInstance();
        final boolean firstPerson = context.firstPerson();
        final LivingEntity holder = firstPerson ? mc.player : StaffHolders.current();

        final boolean leftArm = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        final boolean mainHand = holder == null || leftArm == (holder.getMainArm() == HumanoidArm.LEFT);
        final boolean mine = holder != null && holder == mc.player && mainHand;
        final long now = Util.getNanos();
        final float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        sweep(now);

        final Matrix4f pose = new Matrix4f(ps.last().pose());
        final Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        final Vector4f ball = pose.transform(new Vector4f(BALL_X, BALL_Y, BALL_Z, 1f));
        final boolean inWorld = holder != null && holder.getEyePosition(partial)
                .distanceTo(cam.add(ball.x(), ball.y(), ball.z())) < IN_WORLD;

        final Motion m = inWorld
                ? motions.computeIfAbsent(((long) holder.getId() << 2) | (firstPerson ? 2L : 0L) | (leftArm ? 1L : 0L),
                        k -> new Motion())
                : null;
        final double dt = m == null || m.seenNanos == 0L ? 0.0 : (now - m.seenNanos) / 1.0e9;

        boolean redrawn = false;
        if (m != null) {
            if (m.seenNanos == 0L || dt > REDRAW_GAP) {
                m.drawnNanos = now;
                m.aimed = false;
                redrawn = true;
            }
            m.seenNanos = now;
        }
        final double step = redrawn ? Double.POSITIVE_INFINITY : dt;

        double yaw = 0.0;
        double needle = 0.0;
        float bubble = 0f;
        if (m != null) {
            final Vec3 target = target(holder, mine, partial);
            final double want = StaffMotion.yaw(target.x - cam.x - ball.x(), target.z - cam.z - ball.z());
            if (!m.aimed) {
                m.yaw = want;
                m.lastYaw = want;
                m.aimed = true;
            } else {
                m.yaw = StaffMotion.follow(m.yaw, want, dt, StaffMotion.AIM_TAU);
            }
            final double rate = dt > 0.0 && dt <= 0.5 ? StaffMotion.wrap(m.yaw - m.lastYaw) / dt : 0.0;
            m.lastYaw = m.yaw;
            final float swing = mainHand ? strikeSwing(holder, partial) : 0f;
            if (m.lastSwing < STRIKE && swing >= STRIKE) {
                final double sign = (m.knocks++ & 1) == 0 ? 1.0 : -1.0;
                m.needle.kick(sign * StaffMotion.NEEDLE_KNOCK);
                m.bubble.kick(sign * StaffMotion.BUBBLE_KNOCK);
            }
            m.lastSwing = swing;
            m.needle.step(step, rate);
            yaw = m.yaw;
            needle = StaffMotion.wrap(m.needle.heading() - m.yaw);
            bubble = (float) Math.max(-StaffMotion.BUBBLE_TRAVEL, Math.min(StaffMotion.BUBBLE_TRAVEL,
                    m.bubble.step(StaffMotion.BUBBLE_THROW * rate, step)));
        }
        final double drawn = m == null ? 60.0 : (now - m.drawnNanos) / 1.0e9;
        final float vaneFront = (float) -StaffMotion.vane(drawn, StaffMotion.VANE_FRONT_DELAY);
        final float vaneRear = (float) StaffMotion.vane(drawn, StaffMotion.VANE_REAR_DELAY);
        final double since = mine ? ticksSinceModeChange(mc, partial) : -1.0;
        final double wheel = mine ? wheelAngle(StaffInput.mode(), since) : 0.0;
        final boolean lineOut = mine && ChalkLine.out();

        if (mine && m != null) {
            final Vector4f nozzle = pose.transform(new Vector4f(NOZZLE_X, NOZZLE_Y, NOZZLE_Z, 1f));
            if (firstPerson) {
                final Vector4f eye = RenderSystem.getModelViewMatrix().transform(new Vector4f(nozzle));
                final Vector4f clip = RenderSystem.getProjectionMatrix().transform(new Vector4f(eye));
                if (clip.w() > 1e-6f) ChalkLine.handNozzle(clip.x() / clip.w(), clip.y() / clip.w(), eye.z(), now);
            } else {
                ChalkLine.worldNozzle(cam.x + nozzle.x(), cam.y + nozzle.y(), cam.z + nozzle.z(), now);
            }
        }

        final ModelBlockRenderer blocks = mc.getBlockRenderer().getModelRenderer();
        final VertexConsumer solid = buffers.getBuffer(Sheets.cutoutBlockSheet());

        draw(blocks, ps, solid, STAFF, 1f, 1f, 1f, light, overlay);
        draw(blocks, ps, solid, REEL, 1f, 1f, 1f, light, overlay);

        ps.pushPose();
        about(ps, REEL_X, REEL_Y, REEL_Z,
                new Quaternionf().rotationX((float) Math.toRadians(mine ? ChalkLine.paidOut() * CRANK_PER_BLOCK : 0.0)));
        draw(blocks, ps, solid, CRANK, 1f, 1f, 1f, light, overlay);
        ps.popPose();

        final Vector3f hang = new Vector3f(0f, -1f, 0f);
        if (m != null) {
            final Matrix4f at = ps.last().pose();
            final Vector4f pivot = at.transform(new Vector4f(NOZZLE_X, NOZZLE_Y, NOZZLE_Z, 1f));
            final Vector4f foot = at.transform(new Vector4f(NOZZLE_X, NOZZLE_Y - (LINE_REST + HOOK_DROP) / 16f,
                    NOZZLE_Z, 1f));
            final double length = Math.sqrt(sq(foot.x() - pivot.x()) + sq(foot.y() - pivot.y()) + sq(foot.z() - pivot.z()));
            m.hook.step(cam.x + pivot.x(), cam.y + pivot.y(), cam.z + pivot.z(), length, step);
            final double[] h = m.hook.hang();
            new Matrix3f(at).invert().transform(hang.set((float) h[0], (float) h[1], (float) h[2]));
            if (hang.lengthSquared() > 1e-12f) hang.normalize();
            else hang.set(0f, -1f, 0f);
        }
        if (!lineOut) {
            ps.pushPose();
            ps.translate(NOZZLE_X, NOZZLE_Y, NOZZLE_Z);
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, -1f, 0f), hang));
            ps.pushPose();
            ps.scale(1f, LINE_REST, 1f);
            ps.translate(-NOZZLE_X, -NOZZLE_Y, -NOZZLE_Z);
            draw(blocks, ps, solid, LINE, 1f, 1f, 1f, light, overlay);
            ps.popPose();
            ps.translate(0f, -LINE_REST / 16f, 0f);
            ps.translate(-NOZZLE_X, -NOZZLE_Y, -NOZZLE_Z);
            draw(blocks, ps, solid, HOOK, 1f, 1f, 1f, light, overlay);
            ps.popPose();
        }

        ps.pushPose();
        ps.translate(BALL_X, BALL_Y, BALL_Z);
        if (inWorld) ps.mulPose(levelling(pose));
        ps.mulPose(new Quaternionf().rotationY((float) Math.toRadians(yaw)));
        ps.translate(-BALL_X, -BALL_Y, -BALL_Z);
        draw(blocks, ps, solid, COMPASS, 1f, 1f, 1f, light, overlay);

        ps.pushPose();
        about(ps, BALL_X, NEEDLE_Y, BALL_Z, new Quaternionf().rotationY((float) Math.toRadians(needle)));
        draw(blocks, ps, solid, NEEDLE, 1f, 1f, 1f, light, overlay);
        ps.popPose();

        ps.pushPose();
        ps.translate(bubble / 16f, 0f, 0f);
        draw(blocks, ps, solid, BUBBLE, 1f, 1f, 1f, light, overlay);
        ps.popPose();

        ps.pushPose();
        about(ps, BALL_X, VANE_Y, VANE_FRONT_Z, new Quaternionf().rotationX((float) Math.toRadians(vaneFront)));
        draw(blocks, ps, solid, VANE_FRONT, 1f, 1f, 1f, light, overlay);
        ps.popPose();

        ps.pushPose();
        about(ps, BALL_X, VANE_Y, VANE_REAR_Z, new Quaternionf().rotationX((float) Math.toRadians(vaneRear)));
        draw(blocks, ps, solid, VANE_REAR, 1f, 1f, 1f, light, overlay);

        ps.pushPose();
        about(ps, DETENT_X, DETENT_Y, DETENT_Z,
                new Quaternionf().rotationZ((float) Math.toRadians(-StaffMotion.detent(wheel))));
        draw(blocks, ps, solid, DETENT, 1f, 1f, 1f, light, overlay);
        ps.popPose();

        about(ps, WHEEL_X, WHEEL_Y, WHEEL_Z, new Quaternionf().rotationZ((float) Math.toRadians(wheel)));
        draw(blocks, ps, solid, WHEEL, 1f, 1f, 1f, light, overlay);
        int litGlass = -1;
        final float[] lit = new float[3];
        for (int k = 0; k < GLASSES.length; k++) {
            final int colour = StaffMode.byOrdinal(k).colour;
            final float r = ((colour >> 16) & 0xFF) / 255f;
            final float g = ((colour >> 8) & 0xFF) / 255f;
            final float b = (colour & 0xFF) / 255f;
            if (mine && Math.abs(StaffMotion.wrap(90.0 * k + wheel)) < GLASS_AT_SLIT) {
                litGlass = k;
                lit[0] = Math.min(1f, r / GLASS_GREY);
                lit[1] = Math.min(1f, g / GLASS_GREY);
                lit[2] = Math.min(1f, b / GLASS_GREY);
                draw(blocks, ps, solid, GLASSES[k], lit[0], lit[1], lit[2], LightTexture.FULL_BRIGHT, overlay);
            } else {
                draw(blocks, ps, solid, GLASSES[k], r * GLASS_DIM, g * GLASS_DIM, b * GLASS_DIM, light, overlay);
            }
        }

        final float flash = (float) StaffMotion.flash(since);
        if (litGlass >= 0 && flash > 0.01f) {
            draw(blocks, ps, buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS)), GLASSES[litGlass],
                    lit[0] * flash, lit[1] * flash, lit[2] * flash, LightTexture.FULL_BRIGHT, overlay);
        }
        ps.popPose();
        ps.popPose();
    }

    private static Vec3 target(LivingEntity holder, boolean mine, float partial) {
        if (mine) {
            final StaffPicking.Pick pick = StaffInput.hover();
            if (pick.kind() != StaffPicking.Kind.NONE) return pick.point();
        }
        return holder.getEyePosition(partial).add(holder.getViewVector(partial).scale(LOOK_REACH));
    }

    private static float strikeSwing(LivingEntity holder, float partial) {
        if (!(holder.getMainHandItem().getItem() instanceof SurveyorStaffItem)) return 0f;
        if (holder.swingingArm != null && holder.swingingArm != InteractionHand.MAIN_HAND) return 0f;
        return holder.getAttackAnim(partial);
    }

    private double wheelAngle(StaffMode mode, double since) {
        if (wheelMode == null) {
            wheelMode = mode;
            wheelTo = -90.0 * mode.ordinal();
            wheelFrom = wheelTo;
            wheelNow = wheelTo;
        } else if (mode != wheelMode) {
            final double base = 360.0 * Math.floor(wheelTo / 360.0);
            wheelFrom = wheelNow - base;
            wheelTo = wheelTo - base + StaffMotion.quarters(wheelMode.ordinal(), mode.ordinal(), StaffMode.values().length);
            wheelMode = mode;
        }
        wheelNow = StaffMotion.wheel(wheelFrom, wheelTo, since);
        return wheelNow;
    }

    private static double ticksSinceModeChange(Minecraft mc, float partial) {
        final long at = StaffInput.modeChangedAt();
        if (at == Long.MIN_VALUE || mc.level == null) return -1.0;
        final long since = mc.level.getGameTime() - at;
        return since < 0 ? -1.0 : since + partial;
    }

    private static Quaternionf levelling(Matrix4f pose) {
        final Matrix3f r = new Matrix3f(pose);
        final float s = (float) Math.cbrt(Math.abs(r.determinant()));
        if (!(s > 1e-6f)) return new Quaternionf();
        return r.scale(1f / s).transpose().getNormalizedRotation(new Quaternionf());
    }

    private static void about(PoseStack ps, float x, float y, float z, Quaternionf turn) {
        ps.translate(x, y, z);
        ps.mulPose(turn);
        ps.translate(-x, -y, -z);
    }

    private static void draw(ModelBlockRenderer blocks, PoseStack ps, VertexConsumer vc, ResourceLocation part,
                             float r, float g, float b, int light, int overlay) {
        final BakedModel model = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(part));
        final boolean lifted = LIFTED.contains(part);
        if (lifted) {
            ps.pushPose();
            ps.translate(0f, HEAD_LIFT, 0f);
        }
        blocks.renderModel(ps.last(), vc, null, model, r, g, b, light, overlay, ModelData.EMPTY, null);
        if (lifted) ps.popPose();
    }

    private void sweep(long now) {
        if (now - lastSweep < 1_000_000_000L) return;
        lastSweep = now;
        for (Iterator<Motion> it = motions.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seenNanos > FORGET_NANOS) it.remove();
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
