package com.createstats.renderer;

import com.createstats.block.StormCoilBlock;
import com.createstats.blockentity.StormCoilBE;
import com.createstats.client.AnimationClock;
import com.createstats.holomk3.render.HoloRenderTypes;
import com.createstats.powertrack.client.ArcEffects;
import com.createstats.stormcoil.CoilAim;
import com.createstats.stormcoil.CoilCabinet;
import com.createstats.stormcoil.CoilForm;
import com.createstats.stormcoil.CoilGaze;
import com.createstats.stormcoil.CoilLaw;
import com.createstats.stormcoil.CoilMotion;
import com.createstats.stormcoil.StormCoilStuds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Random;

// draws only, decides nothing
public class StormCoilRenderer implements BlockEntityRenderer<StormCoilBE> {
    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", "block/" + path);
    }

    private static ResourceLocation own(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_stats", "block/storm_coil/" + path);
    }

    static final ResourceLocation ZINC = create("zinc_block");
    static final ResourceLocation BRASS = create("brass_block");
    static final ResourceLocation COPPER = own("rod");
    static final ResourceLocation ELECTRODE = own("electrode");
    static final ResourceLocation BAR_ON = own("bar_on");
    static final ResourceLocation RING_ON = own("ring_on");
    static final ResourceLocation RING_OFF = own("ring_off");
    static final ResourceLocation STRAP = own("strap");

    static final ResourceLocation TOROID = own("toroid");
    static final ResourceLocation PRIMARY = own("primary");

    static final ResourceLocation PRIMARY_HOT = own("primary_hot");
    static final ResourceLocation CORE = own("core");
    static final ResourceLocation HELIX = own("helix");
    static final ResourceLocation INSULATOR = own("insulator");
    static final ResourceLocation ORB_GLASS = own("orb_glass");

    static final ResourceLocation ORB_PLASMA = own("orb_plasma");
    static final ResourceLocation ORB_CORE = own("orb_core");
    static final ResourceLocation FILAMENT = own("filament");
    static final ResourceLocation JAR_LIT = own("jar_lit");
    static final ResourceLocation JAR_DARK = own("jar_dark");
    static final ResourceLocation JAR_FOIL = own("jar_foil");
    static final ResourceLocation CHAIN = own("chain");
    static final ResourceLocation GAP_WHEEL = own("gap_wheel");
    static final ResourceLocation STORM_ON = own("storm_on");
    static final ResourceLocation STORM_OFF = own("storm_off");

    static final ResourceLocation WINDING_HOT = own("winding_hot");
    static final ResourceLocation LOAD_NEEDLE = own("load_needle");
    static final ResourceLocation TELL_MOBS = own("tell_mobs");
    static final ResourceLocation TELL_TRANSFORM = own("tell_transform");
    static final ResourceLocation TELL_PLAYERS = own("tell_players");
    static final ResourceLocation NOTCH_LAMP = own("notch_lamp");

    private static final int NOTCH_LIT = 0xFF9CD8FF;
    private static final int NOTCH_AMBER = 0xFFFFB43A;
    private static final int NOTCH_AMBER_DIM = 0xFF6E5428;
    private static final int NOTCH_DARK = 0xFF3C424C;

    static final ResourceLocation LAMP_LIT = own("security_lamp_lit");
    static final ResourceLocation LAMP_OFF = own("security_lamp_off");

    private static final int LAMP_GREEN = 0xFF66FF7A;
    private static final int LAMP_AMBER = 0xFFFFB43A;
    private static final int LAMP_RED = 0xFFFF4C3E;
    private static final int LAMP_RED_DARK = 0xFFB0605A;

    private static final double[][] RODS = {{-8, -8}, {24, -8}, {24, 24}, {-8, 24}};

    private static final double ROD_BALL_Y = 12.0;

    private static final int AR = 0x9C;
    private static final int AG = 0xD8;
    private static final int AB = 0xFF;

    private static final int STRAND_TARGET = 0xFFDCF2FF;
    private static final int STRAND_SPARED = 0xFFCCB8FF;
    private static final int STRAND_PLASMA = 0xFFFF94EC;
    private static final int STRAND_HELD = 0xFFB8B8C4;

    public StormCoilRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(StormCoilBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(StormCoilBE be) {
        return be.renderBounds();
    }

    @Override
    public void render(StormCoilBE be, float partial, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        if (be.getLevel() == null) {
            return;
        }

        final long now = com.createstats.client.ponder.SceneClock.now(be.getLevel());
        final double t = AnimationClock.time(now, partial);
        final double nudge = be.motion.nudge(partial);
        final CoilForm form = CoilForm.at(be.motion.height(partial) + nudge);

        final double duck = be.motion.duck(partial);
        final double eyeY = form.eyeY(duck);
        final double settle = be.motion.settle(partial);
        final int turns = StormCoilBlock.turns(be.getBlockState());
        final double sinceZap = be.lastZap() == Long.MIN_VALUE ? 1e9 : t - be.lastZap();
        final double sinceStrike = be.lastStrike() == Long.MIN_VALUE ? 1e9 : t - be.lastStrike();
        final boolean firing = sinceZap >= 0 && sinceZap < StormCoilBE.ARC_TICKS;
        final double flood = Math.max(0.0, 1.0 - sinceStrike / StormCoilBE.STRIKE_FLOOD_TICKS);
        final double windUp = be.windingUp(now) ? CoilAim.progress(t, be.aimAt()) : 0.0;
        final double speed = be.motion.gapSpeed(partial);
        final double inShare = be.shownIn() <= 0 ? 0.0
                : Math.min(1.0, be.shownIn() / (double) Math.max(1, CoilLaw.volleyRate(be.coilStage().zapCost,
                be.coilStage().targets())));

        final boolean returning = sinceZap >= 0 && sinceZap < CoilCabinet.RETURN_TICKS;
        final boolean[] flashRods = returning ? nearestRods(be) : new boolean[RODS.length];
        final CoilMesh mesh = new CoilMesh(buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
                overlay);
        final CoilMesh inner = new CoilMesh(buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)),
                overlay);
        final Anim anim = new Anim(t, firing ? 1.0 - sinceZap / StormCoilBE.ARC_TICKS : 0.0, flood, sinceStrike,
                be.shownIn() > 0, be.motion.charge(partial), be.motion.bankShown(partial), be.shownBank() > 0,
                be.primed(), windUp, be.motion.gapSpark(partial), speed, be.heldFromArcing(),
                be.shownCapacitor() <= 0 && be.shownBank() <= 0, nudge / CoilMotion.NUDGE);

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-90f * turns));
        ps.translate(-0.5, 0, -0.5);
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        drawColumn(mesh, ps, form, settle, light, anim, eyeY, duck);
        drawJars(mesh, inner, ps.last(), light, anim);
        drawGap(mesh, ps, be.motion.gapAngle(partial), light, anim);
        drawMarks(mesh, ps.last(), light, anim);
        drawStudRing(mesh, ps.last(), StormCoilStuds.STUD_Z[StormCoilStuds.IN], be.shownIn() > 0,
                0.35 + 0.65 * inShare, light);
        drawStudRing(mesh, ps.last(), StormCoilStuds.STUD_Z[StormCoilStuds.OUT], be.shownOut() > 0 || flood > 0.05, 1.0,
                light);
        final com.createstats.security.CoilStatus.Lamp lamp = be.securityLamp();
        drawSecurityLamp(mesh, ps.last(), lamp, t, light);

        final double load = be.motion.load(partial);
        drawTransformer(mesh, ps.last(), light, load);
        drawLoadNeedle(mesh, ps.last(), light, load, !anim.dark() || be.shownIn() > 0, t);
        final boolean[] lamps = be.targetLamps();
        final double shownHeight = be.motion.height(partial);
        final boolean blocked = be.blockedAt() > 0;
        drawSettingsFace(mesh, ps.last(), light, lamps, be.heldFromArcing(), shownHeight, be.requestedStage(), blocked,
                now);
        drawEarthBus(mesh, ps.last(), form, light, arrivedCorners(flashRods, turns), sinceZap);
        ps.popPose();

        ps.pushPose();
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        drawRods(mesh, ps.last(), form, light, flashRods, sinceZap);
        final List<CoilGaze.Strand> strands = be.gaze.strands(partial);
        drawEye(mesh, inner, ps.last(), form, eyeY, anim, strands, now, light);
        ps.popPose();

        final VertexConsumer fx = buffers.getBuffer(HoloRenderTypes.ADDITIVE);
        final Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        drawGapLight(fx, ps, camera, turns, anim, now);
        drawLampLight(fx, ps, camera, turns, lamp, t);
        drawCabinetLight(fx, ps, camera, turns, load, lamps, be.heldFromArcing(), shownHeight, be.requestedStage(),
                blocked, now);
        drawPrimaryGlow(fx, ps, camera, anim);
        drawEyeLight(fx, ps, camera, form, eyeY, anim, strands);
        drawFeelers(fx, ps, be, form, t, now, partial);
        if (firing) {
            final double fade = 1.0 - sinceZap / StormCoilBE.ARC_TICKS;
            halo(fx, ps, camera, 8.0, eyeY, 8.0, (float) (form.orb + 6), 0.6 * fade);
            for (int i = 0; i < be.volleySize(); i++) {
                drawArc(fx, ps, be, form, eyeY, be.volleyTarget(i), i, now, fade);
            }
            drawRodSparks(fx, ps, camera, flashRods, form, now, fade);
        }
        if (be.primed()) {
            drawStreamers(fx, ps, camera, form, eyeY, now, t);
        }
        if (be.corona()) {
            drawCorona(fx, ps, camera, form, eyeY, t);
        }
        if (flood > 0) {
            halo(fx, ps, camera, 8.0, form.toroidY, 8.0, (float) (form.major + 6), 0.5 * flood);
        }

        drawStudWires(be, ps, buffers, light);
    }

    private record Anim(double t, double fire, double flood, double sinceStrike, boolean charging, double cap,
                        double bank, boolean banked, boolean primed, double windUp, double spark, double speed,
                        boolean held, boolean dark, double nudge) {
    }

    private static void drawColumn(CoilMesh mesh, PoseStack ps, CoilForm form, double settle, int light, Anim a,
                                   double eyeY, double duck) {
        final PoseStack.Pose pose = ps.last();
        final TextureAtlasSprite brass = CoilMesh.sprite(BRASS);
        final TextureAtlasSprite alu = CoilMesh.sprite(TOROID);

        final double pulse = pulse(a);
        final boolean ringing = pulse > 0.08;
        mesh.torus(pose, 8, CoilForm.DECK + 1.1, 8, 7.0, 0.7, 32, 8, CoilMesh.sprite(ringing ? PRIMARY_HOT : PRIMARY),
                light, ringing ? CoilMesh.grey(0.6 + 0.4 * pulse) : CoilMesh.WHITE, ringing);
        for (int k = 0; k < 4; k++) {
            final double ang = Math.PI / 4 + k * Math.PI / 2;
            final double x = 8 + Math.cos(ang) * 7.0;
            final double z = 8 + Math.sin(ang) * 7.0;
            mesh.box(pose, x - 0.45, CoilForm.DECK - 0.2, z - 0.45, x + 0.45, CoilForm.DECK + 1.0, z + 0.45, brass,
                    light);
        }

        final double stack = Math.max(0.0, Math.min(5.2, form.toroidY - form.tube - CoilForm.DECK - 1.2));
        final double stackTop = CoilForm.DECK + stack;
        if (stack > 0.5) {
            final TextureAtlasSprite ins = CoilMesh.sprite(INSULATOR);
            final double k = stack / 5.2;
            double y = CoilForm.DECK - 0.1;
            final double[] discs = {4.4, 3.9, 3.4};
            for (int i = 0; i < discs.length; i++) {
                mesh.prism(pose, 8, 8, y, y + 1.5 * k, discs[i], 16, ins, ins, light, CoilMesh.WHITE, false);
                y += 1.5 * k;
                if (i < discs.length - 1) {
                    mesh.prism(pose, 8, 8, y - 0.05, y + 0.35 * k + 0.05, 2.5, 12, ins, ins, light, CoilMesh.WHITE,
                            false);
                    y += 0.35 * k;
                }
            }
        }

        mesh.prism(pose, 8, 8, CoilForm.DECK - 0.3, form.toroidY, 2.0, 16, CoilMesh.sprite(CORE), brass, light,
                CoilMesh.WHITE, false);
        drawHelix(mesh, pose, stackTop + 0.4, form.toroidY - form.tube * 0.5, light, a);

        final double jump = 1.0 + 0.035 * settle + 0.07 * a.fire();
        final double big = form.major * jump;
        final double tube = form.tube * (1.0 + 0.05 * a.fire());
        final double press = Math.max(0.0, a.nudge());
        ps.pushPose();
        ps.translate(8, form.toroidY + form.tube, 8);
        ps.scale((float) (1.0 + 0.05 * press), (float) (1.0 - 0.16 * press), (float) (1.0 + 0.05 * press));
        ps.translate(-8, -(form.toroidY + form.tube), -8);
        final PoseStack.Pose donut = ps.last();
        mesh.torus(donut, 8, form.toroidY, 8, big, tube, 32, 12, alu, light, CoilMesh.WHITE, false, 8);
        if (duck < 0.5) {
            mesh.prism(donut, 8, 8, form.toroidY - 0.8, form.toroidY + 0.8, 2.6, 12, alu, alu, light, CoilMesh.WHITE,
                    false);
        }
        for (int k = 0; k < 4; k++) {
            final double ang = Math.PI / 4 + k * Math.PI / 2;
            final double reach = big - tube * 0.6;
            final double x = 8 + Math.cos(ang) * reach / 2;
            final double z = 8 + Math.sin(ang) * reach / 2;
            final double ca = Math.abs(Math.cos(ang)) * reach / 2 + 0.4;
            final double sa = Math.abs(Math.sin(ang)) * reach / 2 + 0.4;
            mesh.box(donut, x - ca * 0.7, form.toroidY - 0.4, z - sa * 0.7, x + ca * 0.7, form.toroidY + 0.4,
                    z + sa * 0.7, alu, light);
        }
        ps.popPose();

        final double orb = orbRadius(form, a);
        final double collarTop = eyeY - orb * 0.9;
        if (collarTop > form.toroidY + form.tube * 0.2 + 0.05) {
            mesh.prism(pose, 8, 8, form.toroidY + form.tube * 0.2, collarTop, form.orb * 0.55, 12, brass, brass, light,
                    CoilMesh.WHITE, false);
        }
    }

    private static double pulse(Anim a) {
        return Math.max(a.spark(), 0.65 * a.windUp());
    }

    private static double orbRadius(CoilForm form, Anim a) {
        return form.orb * (1.0 + 0.14 * a.fire() + (a.primed() ? 0.06 * Math.sin(a.t() * 1.7) : 0.0));
    }

    private static void drawHelix(CoilMesh mesh, PoseStack.Pose pose, double y0, double y1, int light, Anim a) {
        final double len = y1 - y0;
        if (len < 1.2) {
            return;
        }
        final TextureAtlasSprite wire = CoilMesh.sprite(HELIX);
        final TextureAtlasSprite hot = CoilMesh.sprite(BAR_ON);
        final int turns = Math.max(1, (int) Math.round(len / 2.6));
        final int segs = turns * 14;
        final double r = 2.75;
        final double w = 0.55;
        float[][] prev = null;
        for (int i = 0; i <= segs; i++) {
            final double s = i / (double) segs;
            final double th = 2 * Math.PI * turns * s;
            final double c = Math.cos(th);
            final double sn = Math.sin(th);
            final double y = y0 + len * s;

            final float[][] q = {
                    {(float) (8 + (r + w) * c), (float) (y + w), (float) (8 + (r + w) * sn)},
                    {(float) (8 + (r + w) * c), (float) (y - w), (float) (8 + (r + w) * sn)},
                    {(float) (8 + (r - w) * c), (float) (y - w), (float) (8 + (r - w) * sn)},
                    {(float) (8 + (r - w) * c), (float) (y + w), (float) (8 + (r - w) * sn)},
                    {(float) c, 0f, (float) sn}};
            if (prev != null) {
                final boolean on = lit(s, a);
                final TextureAtlasSprite sp = on ? hot : wire;
                final float u0 = (i % 8) * 2f;
                final float u1 = u0 + 2f;
                final float nx = (prev[4][0] + q[4][0]) / 2;
                final float nz = (prev[4][2] + q[4][2]) / 2;
                mesh.quad(pose, new float[][] {prev[3], q[3], q[0], prev[0]}, sp, u0, 0, u1, 3, 0, 1, 0, light,
                        CoilMesh.WHITE, on);
                mesh.quad(pose, new float[][] {prev[1], q[1], q[0], prev[0]}, sp, u0, 4, u1, 11, nx, 0, nz, light,
                        CoilMesh.WHITE, on);
                mesh.quad(pose, new float[][] {prev[2], q[2], q[1], prev[1]}, sp, u0, 12, u1, 15, 0, -1, 0, light,
                        CoilMesh.WHITE, on);
                mesh.quad(pose, new float[][] {q[2], prev[2], prev[3], q[3]}, sp, u0, 8, u1, 12, -nx, 0, -nz, light,
                        CoilMesh.WHITE, on);
            }
            prev = q;
        }
    }

    private static boolean lit(double s, Anim a) {
        if (a.flood() > 0) {
            if (a.sinceStrike() < 4) {
                return true;
            }
            final double head = 1.0 - (a.sinceStrike() - 4) / 26.0;
            if (Math.abs(s - head) < 0.12) {
                return true;
            }
        }
        if (a.fire() > 0.55) {
            return true;
        }
        if (a.windUp() > 0 && (s - a.t() * 0.09 % 1.0 + 2.0) % (1.0 / 3.0) < 0.08) {
            return true;
        }
        if (a.charging() && (s - a.t() * 0.045 % 1.0 + 2.0) % 0.5 < 0.09) {
            return true;
        }
        return a.primed() && Math.floorMod((long) (s * 40) * 7919L + (long) (a.t() / 2), 5L) == 0;
    }

    private static void drawJars(CoilMesh mesh, CoilMesh inner, PoseStack.Pose pose, int light, Anim a) {
        final TextureAtlasSprite brass = CoilMesh.sprite(BRASS);
        final TextureAtlasSprite lit = CoilMesh.sprite(JAR_LIT);
        final TextureAtlasSprite dark = CoilMesh.sprite(JAR_DARK);
        final TextureAtlasSprite foil = CoilMesh.sprite(JAR_FOIL);
        final TextureAtlasSprite chain = CoilMesh.sprite(CHAIN);
        final double r = CoilCabinet.JAR_R;
        for (int k = 0; k < CoilCabinet.JARS; k++) {
            final double x = CoilCabinet.jarX(k);
            final double y = CoilCabinet.jarY(k);
            final double z = CoilCabinet.jarZ(k);
            final double level = CoilCabinet.level(a.cap(), k, CoilCabinet.JARS);
            if (level > 0.0) {
                double bright = 0.22 + 0.78 * level;
                if (level < 1.0 && a.charging()) {
                    bright *= 0.86 + 0.14 * Math.sin(a.t() * 1.9 + k * 2.1);
                }
                inner.prismInside(pose, x, z, y + 0.02, y + CoilCabinet.GLASS_TOP, r, 10, lit, lit, light,
                        CoilMesh.grey(bright), true);
            } else {
                inner.prismInside(pose, x, z, y + 0.02, y + CoilCabinet.GLASS_TOP, r, 10, dark, dark, light,
                        CoilMesh.WHITE, false);
            }
            mesh.prism(pose, x, z, y, y + CoilCabinet.FOIL_TOP, r + CoilCabinet.FOIL_PROUD, 10, foil, null, light,
                    CoilMesh.WHITE, false);
            mesh.prism(pose, x, z, y + CoilCabinet.GLASS_TOP, y + CoilCabinet.CAP_TOP, r + CoilCabinet.CAP_PROUD, 10,
                    brass, brass, light, CoilMesh.WHITE, false);
            mesh.box(pose, x - 0.11, y + CoilCabinet.CAP_TOP, z - 0.11, x + 0.11, y + CoilCabinet.ROD_TOP, z + 0.11,
                    brass, light);
            mesh.sphere(pose, x, y + CoilCabinet.KNOB_Y, z, CoilCabinet.KNOB_R, 4, 8, brass, light, CoilMesh.WHITE,
                    false);

            for (int l = 0; l < 3; l++) {
                final double cy = y + CoilCabinet.GLASS_TOP - 0.3 - l * 0.25;
                final double wx = l % 2 == 0 ? 0.12 : 0.05;
                final double wz = l % 2 == 0 ? 0.05 : 0.12;
                mesh.box(pose, x - wx, cy - 0.13, z - wz, x + wx, cy + 0.13, z + wz, chain, light);
            }
        }

        final boolean discharging = (a.windUp() > 0 || a.fire() > 0) && a.spark() > 0.5;
        final TextureAtlasSprite bus = discharging ? CoilMesh.sprite(BAR_ON) : brass;
        for (int s = 0; s < CoilCabinet.SHELF_Y.length; s++) {
            final double by = CoilCabinet.SHELF_Y[s] + CoilCabinet.KNOB_Y;
            final double bz = CoilCabinet.JAR_Z[s];
            mesh.box(pose, CoilCabinet.RISER_X1, by - 0.08, bz - 0.08, CoilCabinet.BAY_X1 - 0.6, by + 0.08, bz + 0.08,
                    bus, light, CoilMesh.WHITE, discharging, 0x3F);
        }
    }

    private static void drawGap(CoilMesh mesh, PoseStack ps, double angle, int light, Anim a) {
        ps.pushPose();
        ps.translate(CoilCabinet.GAP_X, 0, CoilCabinet.GAP_Z);
        ps.mulPose(Axis.YP.rotationDegrees((float) -(angle % 360.0)));
        final PoseStack.Pose pose = ps.last();
        final TextureAtlasSprite wheel = CoilMesh.sprite(GAP_WHEEL);
        final TextureAtlasSprite brass = CoilMesh.sprite(BRASS);
        final TextureAtlasSprite stud = CoilMesh.sprite(ELECTRODE);
        mesh.disc(pose, 0, CoilCabinet.DISC_Y1, 0, CoilCabinet.DISC_R, 16, wheel, light, CoilMesh.WHITE, false, true);
        mesh.disc(pose, 0, CoilCabinet.DISC_Y0, 0, CoilCabinet.DISC_R, 16, wheel, light, CoilMesh.WHITE, false, false);
        mesh.prism(pose, 0, 0, CoilCabinet.DISC_Y0, CoilCabinet.DISC_Y1, CoilCabinet.DISC_R, 16, wheel, null, light,
                CoilMesh.WHITE, false);
        mesh.prism(pose, 0, 0, CoilCabinet.DISC_Y1, CoilCabinet.DISC_Y1 + 0.25, 0.7, 8, brass, brass, light,
                CoilMesh.WHITE, false);
        mesh.prism(pose, 0, 0, CoilCabinet.HOUSE_FLOOR + 0.8, CoilCabinet.DISC_Y0, 0.35, 6, brass, null, light,
                CoilMesh.WHITE, false);
        final double h = CoilCabinet.STUD_HALF;
        final double ym = (CoilCabinet.DISC_Y0 + CoilCabinet.DISC_Y1) / 2;
        for (int k = 0; k < CoilMotion.STUDS; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(360f * k / CoilMotion.STUDS));
            mesh.box(ps.last(), CoilCabinet.STUD_IN, ym - h, -h, CoilCabinet.STUD_OUT, ym + h, h, stud, light);
            ps.popPose();
        }
        ps.popPose();
    }

    private static void drawMarks(CoilMesh mesh, PoseStack.Pose pose, int light, Anim a) {
        final TextureAtlasSprite on = CoilMesh.sprite(STORM_ON);
        final TextureAtlasSprite off = CoilMesh.sprite(STORM_OFF);
        final int count = CoilCabinet.lit(a.bank(), CoilCabinet.MARKS);
        final double drain = a.sinceStrike() < 8 ? 0.0 : Math.min(1.0, (a.sinceStrike() - 8) / 32.0);
        final double floodTop = a.flood() > 0 ? CoilCabinet.MARKS - drain * (CoilCabinet.MARKS - count) : 0;
        for (int k = 0; k < CoilCabinet.MARKS; k++) {
            final double z0 = CoilCabinet.markZ(k);
            final double level = CoilCabinet.level(a.bank(), k, CoilCabinet.MARKS);
            final boolean flooded = k < floodTop;
            final boolean glow = level > 0 || flooded;
            final double bright = flooded && a.sinceStrike() < 8 ? 1.0 : Math.max(0.3 + 0.7 * level, flooded ? 0.7 : 0);
            final TextureAtlasSprite lens = glow ? on : off;
            final int colour = glow ? CoilMesh.grey(bright) : CoilMesh.WHITE;
            final float x0 = -0.22f;
            final float y0 = (float) CoilCabinet.MARK_Y0;
            final float y1 = (float) CoilCabinet.MARK_Y1;
            final float za = (float) z0;
            final float zb = (float) (z0 + CoilCabinet.MARK_W);

            mesh.quad(pose, new float[][] {{x0, y0, za}, {x0, y0, zb}, {x0, y1, zb}, {x0, y1, za}}, lens, 0, 0, 16, 16,
                    -1, 0, 0, light, colour, glow);
            mesh.box(pose, x0, y0, za, -0.06, y1, zb, lens, light, colour, glow, 1 | 2 | 4 | 8);
        }
    }

    // only drawn while a house governs it
    private static void drawSecurityLamp(CoilMesh mesh, PoseStack.Pose pose, com.createstats.security.CoilStatus.Lamp lamp,
                                         double t, int light) {
        if (lamp == com.createstats.security.CoilStatus.Lamp.NONE) {
            return;
        }
        final boolean flashing = lamp == com.createstats.security.CoilStatus.Lamp.RED_FLASHING;
        final boolean on = !flashing || com.createstats.security.CoilStatus.flashOn(t);
        final double cx = CoilCabinet.SECURITY_LAMP[0];
        final double cy = CoilCabinet.SECURITY_LAMP[1];
        final double z = CoilCabinet.SECURITY_LAMP[2];
        final TextureAtlasSprite brass = CoilMesh.sprite(BRASS);

        mesh.box(pose, cx - 1.0, cy - 1.0, z - 0.3, cx + 1.0, cy + 1.0, z, brass, light);
        mesh.box(pose, cx - 1.15, cy + 1.0, z - 0.75, cx + 1.15, cy + 1.25, z, brass, light);
        final int colour = on ? switch (lamp) {
            case GREEN -> LAMP_GREEN;
            case AMBER -> LAMP_AMBER;
            default -> LAMP_RED;
        } : LAMP_RED_DARK;
        final TextureAtlasSprite lens = CoilMesh.sprite(on ? LAMP_LIT : LAMP_OFF);
        final float x0 = (float) (cx - 0.75);
        final float x1 = (float) (cx + 0.75);
        final float y0 = (float) (cy - 0.75);
        final float y1 = (float) (cy + 0.75);
        final float zf = (float) (z - 0.55);
        mesh.quad(pose, new float[][] {{x1, y0, zf}, {x0, y0, zf}, {x0, y1, zf}, {x1, y1, zf}}, lens, 0, 0, 16, 16,
                0, 0, -1, light, colour, on);
        mesh.box(pose, x0, y0, zf, x1, y1, z - 0.3, lens, light, colour, on, 1 | 2 | 16 | 32);
    }

    private static void drawLampLight(VertexConsumer fx, PoseStack ps, Quaternionf camera, int turns,
                                      com.createstats.security.CoilStatus.Lamp lamp, double t) {
        if (lamp == com.createstats.security.CoilStatus.Lamp.NONE
                || (lamp == com.createstats.security.CoilStatus.Lamp.RED_FLASHING
                && !com.createstats.security.CoilStatus.flashOn(t))) {
            return;
        }
        final int c = switch (lamp) {
            case GREEN -> LAMP_GREEN;
            case AMBER -> LAMP_AMBER;
            default -> LAMP_RED;
        };
        final double[] at = StormCoilStuds.turn(turns, CoilCabinet.SECURITY_LAMP[0], CoilCabinet.SECURITY_LAMP[2] - 0.7);
        halo(fx, ps, camera, at[0], CoilCabinet.SECURITY_LAMP[1], at[1], 2.2f, 0.42, (c >> 16) & 0xFF, (c >> 8) & 0xFF,
                c & 0xFF);
    }

    private static void drawTransformer(CoilMesh mesh, PoseStack.Pose pose, int light, double load) {
        final boolean hot = load > 0.02;
        final TextureAtlasSprite sprite = CoilMesh.sprite(hot ? WINDING_HOT : HELIX);
        final int colour = hot ? CoilMesh.grey(0.35 + 0.65 * load) : CoilMesh.WHITE;
        for (double x : CoilCabinet.LEG_X) {
            drawWinding(mesh, pose, x, CoilCabinet.LEG_Z, CoilCabinet.WIND_R, CoilCabinet.WIND_W, CoilCabinet.WIND_Y0,
                    CoilCabinet.WIND_Y1, CoilCabinet.WIND_TURNS, sprite, light, colour, hot);
        }
    }

    private static void drawWinding(CoilMesh mesh, PoseStack.Pose pose, double cx, double cz, double r, double w,
                                    double y0, double y1, int turns, TextureAtlasSprite sprite, int light, int colour,
                                    boolean glow) {
        final int segs = turns * 12;
        final double len = y1 - y0;
        float[][] prev = null;
        for (int i = 0; i <= segs; i++) {
            final double s = i / (double) segs;
            final double th = 2 * Math.PI * turns * s;
            final double c = Math.cos(th);
            final double sn = Math.sin(th);
            final double y = y0 + len * s;
            final float[][] q = {
                    {(float) (cx + (r + w) * c), (float) (y + w), (float) (cz + (r + w) * sn)},
                    {(float) (cx + (r + w) * c), (float) (y - w), (float) (cz + (r + w) * sn)},
                    {(float) (cx + (r - w) * c), (float) (y - w), (float) (cz + (r - w) * sn)},
                    {(float) (cx + (r - w) * c), (float) (y + w), (float) (cz + (r - w) * sn)},
                    {(float) c, 0f, (float) sn}};
            if (prev != null) {
                final float u0 = (i % 8) * 2f;
                final float u1 = u0 + 2f;
                final float nx = (prev[4][0] + q[4][0]) / 2;
                final float nz = (prev[4][2] + q[4][2]) / 2;
                mesh.quad(pose, new float[][] {prev[3], q[3], q[0], prev[0]}, sprite, u0, 0, u1, 3, 0, 1, 0, light,
                        colour, glow);
                mesh.quad(pose, new float[][] {prev[1], q[1], q[0], prev[0]}, sprite, u0, 4, u1, 11, nx, 0, nz, light,
                        colour, glow);
                mesh.quad(pose, new float[][] {prev[2], q[2], q[1], prev[1]}, sprite, u0, 12, u1, 15, 0, -1, 0, light,
                        colour, glow);
                mesh.quad(pose, new float[][] {q[2], prev[2], prev[3], q[3]}, sprite, u0, 8, u1, 12, -nx, 0, -nz, light,
                        colour, glow);
            }
            prev = q;
        }
    }

    private static void drawLoadNeedle(CoilMesh mesh, PoseStack.Pose pose, int light, double load, boolean humming,
                                       double t) {
        double deg = -CoilCabinet.NEEDLE_SWEEP + 2 * CoilCabinet.NEEDLE_SWEEP * load;
        if (humming) {
            deg += (0.8 + 2.2 * load) * Math.sin(t * 2.9) * Math.cos(t * 1.3);
        }
        final double a = Math.toRadians(deg);
        final double z = 16.17;
        final double[] pivot = {CoilCabinet.DIAL_X, CoilCabinet.NEEDLE_Y, z};
        final double[] tip = {CoilCabinet.DIAL_X + CoilCabinet.NEEDLE_LEN * Math.sin(a),
                CoilCabinet.NEEDLE_Y + CoilCabinet.NEEDLE_LEN * Math.cos(a), z};
        mesh.beam(pose, pivot, tip, 0.07, CoilMesh.sprite(LOAD_NEEDLE), light, CoilMesh.WHITE, false);
        mesh.box(pose, CoilCabinet.DIAL_X - 0.17, CoilCabinet.NEEDLE_Y - 0.17, 16.12, CoilCabinet.DIAL_X + 0.17,
                CoilCabinet.NEEDLE_Y + 0.17, 16.3, CoilMesh.sprite(BRASS), light, CoilMesh.WHITE, false, 0x3F, 1f);
    }

    private static void drawSettingsFace(CoilMesh mesh, PoseStack.Pose pose, int light, boolean[] lamps, boolean held,
                                         double shownHeight, int requested, boolean blocked, long now) {
        final TextureAtlasSprite[] tell = {CoilMesh.sprite(TELL_MOBS), CoilMesh.sprite(TELL_TRANSFORM),
                CoilMesh.sprite(TELL_PLAYERS)};
        for (int k = 0; k < tell.length; k++) {
            final boolean lit = lamps[k];
            final int colour = !lit ? CoilMesh.grey(0.3) : held ? CoilMesh.grey(0.42) : CoilMesh.WHITE;
            flankLens(mesh, pose, CoilCabinet.LAMP_Z0, CoilCabinet.LAMP_Z1, CoilCabinet.LAMP_Y[k],
                    CoilCabinet.LAMP_Y[k] + CoilCabinet.LAMP_H, tell[k], light, colour, lit);
        }
        final TextureAtlasSprite lens = CoilMesh.sprite(NOTCH_LAMP);
        for (int k = 1; k <= CoilCabinet.NOTCH_Y.length; k++) {
            final CoilCabinet.Notch n = CoilCabinet.notch(k, shownHeight, requested, blocked, now);
            final int colour = switch (n) {
                case LIT -> NOTCH_LIT;
                case BLOCKED_ON -> NOTCH_AMBER;
                case BLOCKED_OFF -> NOTCH_AMBER_DIM;
                case DARK -> NOTCH_DARK;
            };
            final double y = CoilCabinet.NOTCH_Y[k - 1];
            flankLens(mesh, pose, CoilCabinet.LADDER_Z0, CoilCabinet.LADDER_Z1, y, y + CoilCabinet.NOTCH_H, lens, light,
                    colour, n == CoilCabinet.Notch.LIT || n == CoilCabinet.Notch.BLOCKED_ON);
        }
    }

    private static void flankLens(CoilMesh mesh, PoseStack.Pose pose, double z0, double z1, double y0, double y1,
                                  TextureAtlasSprite lens, int light, int colour, boolean glow) {
        final float x = 16.36f;
        final float fy0 = (float) y0;
        final float fy1 = (float) y1;
        final float za = (float) z0;
        final float zb = (float) z1;

        mesh.quad(pose, new float[][] {{x, fy0, zb}, {x, fy0, za}, {x, fy1, za}, {x, fy1, zb}}, lens, 0, 0, 16, 16, 1, 0,
                0, light, colour, glow);
        mesh.box(pose, 16.2, y0, z0, x, y1, z1, CoilMesh.sprite(BRASS), light, CoilMesh.WHITE, false, 1 | 2 | 4 | 8, 1f);
    }

    private static void drawEarthBus(CoilMesh mesh, PoseStack.Pose pose, CoilForm form, int light, boolean[] arrived,
                                     double sinceZap) {
        if (form.rods <= 0.6) {
            return;
        }
        final TextureAtlasSprite strap = CoilMesh.sprite(STRAP);
        final TextureAtlasSprite lugCopper = CoilMesh.sprite(PRIMARY);
        final TextureAtlasSprite hot = CoilMesh.sprite(BAR_ON);
        final double lo = -CoilCabinet.BUS_OUT;
        final double hi = 16 + CoilCabinet.BUS_OUT;
        final double y0 = CoilCabinet.BUS_Y0;
        final double y1 = CoilCabinet.BUS_Y1;
        final double half = 0.17;

        mesh.box(pose, lo, y0, lo - half, hi, y1, lo + half, strap, light);
        mesh.box(pose, lo, y0, hi - half, hi, y1, hi + half, strap, light);
        mesh.box(pose, lo - half, y0, lo + half, lo + half, y1, hi - half, strap, light);
        mesh.box(pose, hi - half, y0, lo + half, hi + half, y1, hi - half, strap, light);
        final double head = CoilCabinet.busHead(sinceZap);
        final double lugFlash = Math.max(0.0, 1.0 - Math.abs(sinceZap - CoilCabinet.RETURN_STRAP) / 1.5);
        for (int c = 0; c < 4; c++) {
            final double[] at = CoilCabinet.lug(c);
            final boolean flash = arrived[c] && lugFlash > 0.05;

            mesh.box(pose, at[0] - 0.5, 0.0, at[1] - 0.5, at[0] + 0.5, 0.95, at[1] + 0.5, flash ? hot : lugCopper,
                    light, CoilMesh.WHITE, flash, 0x3F, 2f);
            if (arrived[c] && head > 0.0 && head < 1.0) {
                drawBusBand(mesh, pose, CoilCabinet.busPath(c), head, light, hot);
            }
        }
        final double tf = CoilCabinet.terminalFlash(sinceZap);
        boolean any = false;
        for (boolean b : arrived) {
            any |= b;
        }
        if (any && tf > 0.05) {
            mesh.box(pose, CoilCabinet.TERMINAL_X - 0.9, 0.0, 16.0, CoilCabinet.TERMINAL_X + 0.9, 0.95, 16.5, hot, light,
                    CoilMesh.grey(0.4 + 0.6 * tf), true, 0x3E);
        }
    }

    private static void drawBusBand(CoilMesh mesh, PoseStack.Pose pose, double[][] path, double head, int light,
                                    TextureAtlasSprite hot) {
        double total = 0;
        for (int i = 0; i + 1 < path.length; i++) {
            total += Math.abs(path[i + 1][0] - path[i][0]) + Math.abs(path[i + 1][1] - path[i][1]);
        }
        final double centre = head * total;
        final double reach = 1.25;
        double run = 0;
        final double y0 = CoilCabinet.BUS_Y0 - 0.03;
        final double y1 = CoilCabinet.BUS_Y1 + 0.03;
        final double half = 0.2;
        for (int i = 0; i + 1 < path.length; i++) {
            final double[] a = path[i];
            final double[] b = path[i + 1];
            final double len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            final double from = Math.max(0, centre - reach - run);
            final double to = Math.min(len, centre + reach - run);
            if (to > from) {
                final double ux = len <= 0 ? 0 : (b[0] - a[0]) / len;
                final double uz = len <= 0 ? 0 : (b[1] - a[1]) / len;
                final double x0 = a[0] + ux * from;
                final double z0 = a[1] + uz * from;
                final double x1 = a[0] + ux * to;
                final double z1 = a[1] + uz * to;
                mesh.box(pose, Math.min(x0, x1) - half, y0, Math.min(z0, z1) - half, Math.max(x0, x1) + half, y1,
                        Math.max(z0, z1) + half, hot, light, CoilMesh.WHITE, true, 0x3F);
            }
            run += len;
        }
    }

    private static boolean[] arrivedCorners(boolean[] worldRods, int turns) {
        final boolean[] out = new boolean[4];
        for (int i = 0; i < worldRods.length; i++) {
            if (!worldRods[i]) {
                continue;
            }
            final double[] w = CoilCabinet.lug(i);
            final double[] m = StormCoilStuds.turn((4 - Math.floorMod(turns, 4)) % 4, w[0], w[1]);
            for (int c = 0; c < 4; c++) {
                final double[] l = CoilCabinet.lug(c);
                if (Math.abs(l[0] - m[0]) < 1e-6 && Math.abs(l[1] - m[1]) < 1e-6) {
                    out[c] = true;
                }
            }
        }
        return out;
    }

    private static void drawCabinetLight(VertexConsumer fx, PoseStack ps, Quaternionf camera, int turns, double load,
                                         boolean[] lamps, boolean held, double shownHeight, int requested,
                                         boolean blocked, long now) {
        if (load > 0.02) {
            for (double x : CoilCabinet.LEG_X) {
                final double[] c = StormCoilStuds.turn(turns, x, CoilCabinet.LEG_Z);
                halo(fx, ps, camera, c[0], (CoilCabinet.WIND_Y0 + CoilCabinet.WIND_Y1) / 2, c[1], 2.8f, 0.42 * load,
                        0xFF, 0x9A, 0x40);
            }
        }
        final int[][] tellRgb = {{0xFF, 0x9A, 0x2A}, {0x6E, 0xE0, 0x4A}, {0xFF, 0x4A, 0x46}};
        for (int k = 0; k < lamps.length; k++) {
            if (lamps[k]) {
                final double[] c = StormCoilStuds.turn(turns, 16.6, (CoilCabinet.LAMP_Z0 + CoilCabinet.LAMP_Z1) / 2);
                halo(fx, ps, camera, c[0], CoilCabinet.LAMP_Y[k] + CoilCabinet.LAMP_H / 2, c[1], 1.1f,
                        held ? 0.12 : 0.32, tellRgb[k][0], tellRgb[k][1], tellRgb[k][2]);
            }
        }
        for (int k = 1; k <= CoilCabinet.NOTCH_Y.length; k++) {
            final CoilCabinet.Notch n = CoilCabinet.notch(k, shownHeight, requested, blocked, now);
            if (n == CoilCabinet.Notch.LIT || n == CoilCabinet.Notch.BLOCKED_ON) {
                final double[] c = StormCoilStuds.turn(turns, 16.6, (CoilCabinet.LADDER_Z0 + CoilCabinet.LADDER_Z1) / 2);
                final boolean amber = n == CoilCabinet.Notch.BLOCKED_ON;
                halo(fx, ps, camera, c[0], CoilCabinet.NOTCH_Y[k - 1] + CoilCabinet.NOTCH_H / 2, c[1], 1.0f,
                        amber ? 0.4 : 0.22, amber ? 0xFF : AR, amber ? 0xB4 : AG, amber ? 0x3A : AB);
            }
        }
    }

    private static void drawStudRing(CoilMesh mesh, PoseStack.Pose pose, double z, boolean lit, double bright,
                                     int light) {
        final TextureAtlasSprite s = CoilMesh.sprite(lit ? RING_ON : RING_OFF);
        final int colour = lit ? CoilMesh.grey(bright) : CoilMesh.WHITE;
        final double x0 = 3.9;
        final double x1 = 4.4;
        final double r = 1.9;
        final int n = 10;
        for (int k = 0; k < n; k++) {
            final double a0 = 2 * Math.PI * k / n;
            final double a1 = 2 * Math.PI * (k + 1) / n;
            final double y0 = StormCoilStuds.EYE_Y + Math.sin(a0) * r;
            final double z0 = z + Math.cos(a0) * r;
            final double y1 = StormCoilStuds.EYE_Y + Math.sin(a1) * r;
            final double z1 = z + Math.cos(a1) * r;
            mesh.box(pose, x0, Math.min(y0, y1) - 0.25, Math.min(z0, z1) - 0.25, x1, Math.max(y0, y1) + 0.25,
                    Math.max(z0, z1) + 0.25, s, light, colour, lit, 0x3F);
        }
    }

    private static void drawRods(CoilMesh mesh, PoseStack.Pose pose, CoilForm form, int light, boolean[] flash,
                                 double sinceZap) {
        if (form.rods <= 0.05) {
            return;
        }
        final TextureAtlasSprite rod = CoilMesh.sprite(COPPER);
        final TextureAtlasSprite brass = CoilMesh.sprite(BRASS);
        final TextureAtlasSprite strap = CoilMesh.sprite(STRAP);
        final TextureAtlasSprite hot = CoilMesh.sprite(BAR_ON);

        final double lift = (1.0 - form.rods) * 14.0;
        final double head = CoilCabinet.strapHead(sinceZap);
        for (int i = 0; i < RODS.length; i++) {
            final double x = RODS[i][0];
            final double z = RODS[i][1];
            mesh.box(pose, x - 0.9, -6 + lift, z - 0.9, x + 0.9, 11 + lift, z + 0.9, rod, light);

            mesh.sphere(pose, x, ROD_BALL_Y + lift, z, 1.5, 6, 10, CoilMesh.sprite(PRIMARY), light, CoilMesh.WHITE,
                    false);
            mesh.box(pose, x - 1.2, 1.2 + lift, z - 1.2, x + 1.2, 2.6 + lift, z + 1.2, brass, light);
            if (form.rods > 0.6) {
                final double fx = CoilCabinet.lug(i)[0];
                final double fz = CoilCabinet.lug(i)[1];
                for (int k = 0; k < 4; k++) {
                    final double u0 = k / 4.0;
                    final double u1 = (k + 1) / 4.0;
                    final double x0 = fx + (x - fx) * u0;
                    final double z0 = fz + (z - fz) * u0;
                    final double x1 = fx + (x - fx) * u1;
                    final double z1 = fz + (z - fz) * u1;
                    final boolean on = flash[i] && sinceZap < CoilCabinet.RETURN_STRAP + 0.5
                            && (sinceZap < 1.5 || Math.abs((u0 + u1) / 2 - head) < 0.3);
                    mesh.box(pose, Math.min(x0, x1) - 0.35, 0.05, Math.min(z0, z1) - 0.35, Math.max(x0, x1) + 0.35,
                            0.45, Math.max(z0, z1) + 0.35, on ? hot : strap, light, CoilMesh.WHITE, on, 0x3F);
                }
            }
        }
    }

    private static boolean[] nearestRods(StormCoilBE be) {
        final boolean[] out = new boolean[RODS.length];
        for (int i = 0; i < be.volleySize(); i++) {
            final Vec3 v = be.volleyTarget(i);
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int r = 0; r < RODS.length; r++) {
                final double dx = v.x * 16 - RODS[r][0];
                final double dz = v.z * 16 - RODS[r][1];
                final double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = r;
                }
            }
            out[best] = true;
        }
        return out;
    }

    private static void drawEye(CoilMesh mesh, CoilMesh inner, PoseStack.Pose pose, CoilForm form, double eyeY, Anim a,
                                List<CoilGaze.Strand> strands, long now, int light) {
        final double orb = orbRadius(form, a);
        if (a.dark()) {
            mesh.sphere(pose, 8, eyeY, 8, orb, 8, 16, CoilMesh.sprite(ORB_GLASS), light, CoilMesh.WHITE, false);
            return;
        }
        final double level = a.fire() > 0 ? 1.0 : 0.55 + 0.45 * Math.max(a.cap(), a.banked() ? 1.0 : 0.0);
        inner.sphereInside(pose, 8, eyeY, 8, orb, 8, 16, CoilMesh.sprite(ORB_PLASMA), light, CoilMesh.grey(level),
                true);
        final double core = Math.max(0.55, orb * 0.21) * (1.0 + 0.35 * a.windUp() + 0.45 * a.fire());
        mesh.sphere(pose, 8, eyeY, 8, core, 4, 8, CoilMesh.sprite(ORB_CORE), light, CoilMesh.WHITE, true);
        final TextureAtlasSprite white = CoilMesh.sprite(FILAMENT);
        final double scale = orb / 3.0;
        for (CoilGaze.Strand s : strands) {
            if (s.weight() <= 0.02) {
                continue;
            }
            final double half;
            final int colour;
            final double kink;
            switch (s.kind()) {
                case TARGET -> {
                    half = (s.aimed() ? 0.36 : 0.27) * scale;
                    colour = STRAND_TARGET;
                    kink = 0.05;
                }
                case SPARED -> {
                    half = 0.14 * scale;
                    colour = STRAND_SPARED;
                    kink = 0.1;
                }
                default -> {
                    half = 0.18 * scale;
                    colour = STRAND_PLASMA;
                    kink = 0.13;
                }
            }
            final double[][] p = strandPath(eyeY, orb, core, s, kink, now);
            final int c = a.held() ? STRAND_HELD : colour;
            for (int i = 0; i + 1 < p.length; i++) {
                mesh.beam(pose, p[i], p[i + 1], half * (i == p.length - 2 ? 0.8 : 1.0), white, light, c, true);
            }
        }
    }

    private static double[][] strandPath(double eyeY, double orb, double core, CoilGaze.Strand s, double kink,
                                         long now) {
        final double[] d = {s.x(), s.y(), s.z()};
        final double[] u = Math.abs(d[1]) < 0.9 ? new double[] {-d[2], 0, d[0]} : new double[] {1, 0, 0};
        final double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        u[0] /= ul;
        u[1] /= ul;
        u[2] /= ul;
        final double[] v = {d[1] * u[2] - d[2] * u[1], d[2] * u[0] - d[0] * u[2], d[0] * u[1] - d[1] * u[0]};
        final Random r = new Random(s.seed() * 92821L + now / 2);
        final double r0 = core * 0.9;
        final double r1 = r0 + (orb * 0.94 - r0) * s.weight();
        final double[] at = {0.0, 0.4, 0.72, 1.0};
        final double[][] p = new double[at.length][];
        for (int i = 0; i < at.length; i++) {
            final double along = r0 + (r1 - r0) * at[i];
            final double off = (i == 0 || i == at.length - 1) ? 0 : kink * orb;
            final double ku = (r.nextDouble() * 2 - 1) * off;
            final double kv = (r.nextDouble() * 2 - 1) * off;
            p[i] = new double[] {8 + d[0] * along + u[0] * ku + v[0] * kv, eyeY + d[1] * along + u[1] * ku + v[1] * kv,
                    8 + d[2] * along + u[2] * ku + v[2] * kv};
        }
        return p;
    }

    private static void drawGapLight(VertexConsumer fx, PoseStack ps, Quaternionf camera, int turns, Anim a, long now) {
        final double y = CoilCabinet.ELECTRODE_Y;
        if (a.spark() > 0.02) {
            for (int e = 0; e < 2; e++) {
                final double face = CoilCabinet.ELECTRODE_X[e];
                final double tip = e == 0 ? face + CoilCabinet.SPARK_GAP : face - CoilCabinet.SPARK_GAP;
                final double[] mid = StormCoilStuds.turn(turns, (face + tip) / 2, CoilCabinet.GAP_Z);
                halo(fx, ps, camera, mid[0], y, mid[1], (float) (0.9 + 1.4 * a.spark()), 0.95 * a.spark(), 0xD8, 0xF0,
                        0xFF);
                if (a.spark() > 0.5) {
                    final double[] p0 = StormCoilStuds.turn(turns, face, CoilCabinet.GAP_Z);
                    final double[] p1 = StormCoilStuds.turn(turns, tip, CoilCabinet.GAP_Z);
                    bolt(fx, ps, new Vec3(p0[0] / 16, y / 16, p0[1] / 16),
                            new Vec3(p1[0] / 16, y / 16 + 0.002, p1[1] / 16), (int) (now * 7 + e), 0,
                            (float) a.spark());
                }
            }
        }
        if (a.speed() > 0.7) {
            final double[] c = StormCoilStuds.turn(turns, CoilCabinet.GAP_X, CoilCabinet.GAP_Z);
            final double k = (a.speed() - 0.7) / 0.3;
            for (int i = 0; i < 16; i++) {
                final double ang = 2 * Math.PI * i / 16;
                halo(fx, ps, camera, c[0] + Math.cos(ang) * CoilCabinet.STUD_OUT, y,
                        c[1] + Math.sin(ang) * CoilCabinet.STUD_OUT, 0.55f, 0.16 * k, 0xC8, 0xD8, 0xF0);
            }
        }
    }

    private static void drawPrimaryGlow(VertexConsumer fx, PoseStack ps, Quaternionf camera, Anim a) {
        final double p = pulse(a);
        if (p <= 0.05) {
            return;
        }
        for (int k = 0; k < 8; k++) {
            final double ang = Math.PI / 8 + k * Math.PI / 4;
            halo(fx, ps, camera, 8 + Math.cos(ang) * 7.0, CoilForm.DECK + 1.1, 8 + Math.sin(ang) * 7.0, 2.2f, 0.24 * p);
        }
    }

    private static void drawEyeLight(VertexConsumer fx, PoseStack ps, Quaternionf camera, CoilForm form, double eyeY,
                                     Anim a, List<CoilGaze.Strand> strands) {
        if (a.dark()) {
            return;
        }
        final double orb = orbRadius(form, a);
        final double level = a.fire() > 0 ? 1.0 : 0.55 + 0.45 * Math.max(a.cap(), a.banked() ? 1.0 : 0.0);
        halo(fx, ps, camera, 8, eyeY, 8, (float) (orb * 1.7), 0.22 * level, 0xC8, 0x8C, 0xFF);

        final org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(camera);
        final org.joml.Vector3f left = new org.joml.Vector3f(-1, 0, 0).rotate(camera);
        final org.joml.Vector3f near = new org.joml.Vector3f(0, 0, 1).rotate(camera);
        final double gx = 8 + orb * (0.42 * up.x + 0.38 * left.x + 0.8 * near.x);
        final double gy = eyeY + orb * (0.42 * up.y + 0.38 * left.y + 0.8 * near.y);
        final double gz = 8 + orb * (0.42 * up.z + 0.38 * left.z + 0.8 * near.z);
        halo(fx, ps, camera, gx, gy, gz, (float) (orb * 0.32), 0.45, 0xFF, 0xFF, 0xFF);
        halo(fx, ps, camera, gx, gy, gz, (float) (orb * 0.12), 0.85, 0xFF, 0xFF, 0xFF);
        final double scale = orb / 3.0;
        for (CoilGaze.Strand s : strands) {
            if (s.weight() <= 0.02) {
                continue;
            }
            final double r = orb * (0.2 + 0.74 * s.weight());
            final boolean target = s.kind() == CoilGaze.Kind.TARGET;
            final int red = a.held() ? 0xC0 : target ? 0xC8 : 0xF0;
            final int green = a.held() ? 0xC0 : target ? 0xE8 : 0xB0;
            halo(fx, ps, camera, 8 + s.x() * r, eyeY + s.y() * r, 8 + s.z() * r,
                    (float) ((target ? 0.75 : 0.5) * scale), 0.55 * s.weight(), red, green, 0xFF);
        }
    }

    private static void drawFeelers(VertexConsumer fx, PoseStack ps, StormCoilBE be, CoilForm form, double t, long now,
                                     float partial) {
        final double reach;
        final int[] ids;
        if (be.windingUp(now)) {
            final double p = CoilAim.progress(t, be.aimAt());
            reach = 1.0 - (1.0 - p) * (1.0 - p);
            ids = be.aimIds();
        } else if (be.aimDroppedAt() != Long.MIN_VALUE && t - be.aimDroppedAt() >= 0 && t - be.aimDroppedAt() < 5) {
            reach = 1.0 - (t - be.aimDroppedAt()) / 5.0;
            ids = be.droppedAimIds();
        } else {
            return;
        }
        if (reach <= 0.02) {
            return;
        }
        final BlockPos o = be.getBlockPos();
        for (int n = 0; n < ids.length; n++) {
            final LivingEntity e = be.resolve(ids[n]);
            if (e == null) {
                continue;
            }
            final Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() / 2.0, 0);
            final Vec3 to = new Vec3(c.x - o.getX(), c.y - o.getY(), c.z - o.getZ());
            final double ang = Math.atan2(to.z - 0.5, to.x - 0.5);
            final double rim = (form.major + form.tube * 0.85) / 16.0;
            final Vec3 from = new Vec3(0.5 + Math.cos(ang) * rim, (form.toroidY + form.tube * 0.2) / 16.0,
                    0.5 + Math.sin(ang) * rim);
            final Vec3 d = to.subtract(from);
            final double len = d.length();
            if (len < 0.3) {
                continue;
            }
            final double l = len / 3.0 * reach;
            final Vec3 dir = d.scale(1.0 / len);
            final Random r = new Random(ids[n] * 7919L + now / 2);
            final int legs = 4;
            Vec3 prev = from;
            Vec3 branchAt = from;
            for (int i = 1; i <= legs; i++) {
                final double f = i / (double) legs;
                final double j = i == legs ? 0.04 : 0.06 + 0.05 * l;
                final Vec3 next = from.add(dir.scale(l * f)).add((r.nextDouble() - 0.5) * 2 * j,
                        (r.nextDouble() - 0.5) * 2 * j, (r.nextDouble() - 0.5) * 2 * j);
                bolt(fx, ps, prev, next, (int) (ids[n] * 31 + now / 2 + i), 0, (float) (0.45 + 0.4 * reach));
                if (i == 2) {
                    branchAt = next;
                }
                prev = next;
            }
            final Vec3 lean = new Vec3(r.nextDouble() - 0.5, 0.3 + r.nextDouble() * 0.4, r.nextDouble() - 0.5).normalize();
            bolt(fx, ps, branchAt, branchAt.add(dir.scale(l * 0.18)).add(lean.scale(l * 0.22)), (int) (ids[n] * 17 + now),
                    0, (float) (0.3 * reach));
        }
    }

    private static void drawRodSparks(VertexConsumer fx, PoseStack ps, Quaternionf camera, boolean[] flash,
                                      CoilForm form, long now, double fade) {
        if (form.rods < 0.99) {
            return;
        }
        for (int i = 0; i < RODS.length; i++) {
            if (!flash[i]) {
                continue;
            }
            final double x = RODS[i][0];
            final double z = RODS[i][1];
            halo(fx, ps, camera, x, ROD_BALL_Y, z, (float) (2.0 + 2.5 * fade), 0.9 * fade);
            final Random r = new Random(i * 131L + now);
            final Vec3 ball = new Vec3(x / 16, ROD_BALL_Y / 16, z / 16);
            for (int k = 0; k < 3; k++) {
                final Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.8, r.nextDouble() - 0.5).normalize();
                bolt(fx, ps, ball.add(off.scale(0.09)), ball.add(off.scale(0.28 + 0.12 * r.nextDouble())),
                        (int) (now * 3 + k + i * 11), 0, (float) (0.8 * fade));
            }
        }
    }

    private static void drawArc(VertexConsumer fx, PoseStack ps, StormCoilBE be, CoilForm form, double eyeY, Vec3 to,
                                int index, long now, double fade) {
        final Vec3 centre = new Vec3(0.5, eyeY / 16.0, 0.5);
        final Vec3 out = to.subtract(centre);
        final double away = out.length();
        if (away < 1e-6) {
            return;
        }
        final Vec3 from = centre.add(out.scale(form.orb / 16.0 / away));
        final Vec3 d = to.subtract(from);
        final double len = d.length();
        if (len < 0.2) {
            return;
        }
        final int legs = Math.max(3, (int) Math.min(18, len * 1.2));
        final Random r = new Random(be.lastZap() * 7919L + index * 104729L + (now - be.lastZap()));
        final Vec3[] p = new Vec3[legs + 1];
        final double jitter = 0.12 + 0.02 * len;
        for (int i = 0; i <= legs; i++) {
            final double f = i / (double) legs;
            final double j = (i == 0 || i == legs) ? 0 : jitter * Math.sin(Math.PI * f);
            p[i] = from.add(d.scale(f)).add((r.nextDouble() - 0.5) * 2 * j, (r.nextDouble() - 0.5) * 2 * j,
                    (r.nextDouble() - 0.5) * 2 * j);
        }
        final float alpha = (float) Math.max(0.0, Math.min(1.0, fade));
        final int seed = (int) (be.lastZap() * 31 + index * 977);
        for (int i = 0; i < legs; i++) {
            bolt(fx, ps, p[i], p[i + 1], seed + i, (int) (now - be.lastZap()), alpha);
        }

        final int forks = len > 3 ? 2 : 1;
        for (int k = 0; k < forks; k++) {
            final Vec3 at = p[1 + r.nextInt(Math.max(1, legs * 2 / 3))];
            final Vec3 lean = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.3, r.nextDouble() - 0.5).normalize();
            final Vec3 tip = at.add(d.normalize().scale(len * 0.12).add(lean.scale(len * (0.12 + 0.1 * r.nextDouble()))));
            final Vec3 mid = at.add(tip.subtract(at).scale(0.5)).add(lean.scale(0.08));
            bolt(fx, ps, at, mid, seed + 500 + k * 2, (int) (now - be.lastZap()), alpha * 0.7f);
            bolt(fx, ps, mid, tip, seed + 501 + k * 2, (int) (now - be.lastZap()), alpha * 0.45f);
        }
    }

    private static void bolt(VertexConsumer fx, PoseStack ps, Vec3 a, Vec3 b, int seed, int age, float alpha) {
        ps.pushPose();
        ps.translate(a.x, a.y, a.z);
        ArcEffects.bolt(ps.last().pose(), fx, b.subtract(a), seed, age, alpha);
        ps.popPose();
    }

    private static void drawStreamers(VertexConsumer fx, PoseStack ps, Quaternionf camera, CoilForm form, double eyeY,
                                      long now, double t) {
        final Random r = new Random(now / 2 * 6151L);
        final int n = 3 + (int) Math.round(form.height);
        for (int k = 0; k < n; k++) {
            final double a = r.nextDouble() * Math.PI * 2;
            final double ring = form.orb * 0.55 / 16.0;
            final Vec3 from = new Vec3(0.5 + Math.cos(a) * ring, (eyeY + form.orb * 0.8) / 16.0,
                    0.5 + Math.sin(a) * ring);
            final double reach = 0.5 + r.nextDouble() * (0.6 + 0.25 * form.height);
            final Vec3 to = from.add(Math.cos(a) * reach * 0.35, reach, Math.sin(a) * reach * 0.35);
            final Vec3 mid = from.add(to.subtract(from).scale(0.5)).add((r.nextDouble() - 0.5) * 0.2, 0,
                    (r.nextDouble() - 0.5) * 0.2);
            bolt(fx, ps, from, mid, (int) (now * 13 + k * 2), 0, 0.85f);
            bolt(fx, ps, mid, to, (int) (now * 13 + k * 2 + 1), 0, 0.55f);
        }
        final double pulse = 0.4 + 0.2 * Math.sin(t * 1.3);
        halo(fx, ps, camera, 8, eyeY, 8, (float) (form.orb + 4), pulse);
    }

    private static void drawCorona(VertexConsumer fx, PoseStack ps, Quaternionf camera, CoilForm form, double eyeY,
                                   double t) {
        final int n = 10;
        for (int k = 0; k < n; k++) {
            final double a = 2 * Math.PI * k / n + t * 0.013 * (k % 2 == 0 ? 1 : -1);
            final double flick = 0.5 + 0.5 * Math.sin(t * 0.9 + k * 2.3) * Math.sin(t * 0.37 + k);
            final double rr = form.major + form.tube * 0.6;
            halo(fx, ps, camera, 8 + Math.cos(a) * rr, form.toroidY + form.tube * 0.7, 8 + Math.sin(a) * rr,
                    (float) (1.4 + 0.8 * flick), 0.25 + 0.4 * flick);
        }
        halo(fx, ps, camera, 8, eyeY, 8, (float) (form.orb + 2.0), 0.5);
    }

    private static void halo(VertexConsumer vc, PoseStack ps, Quaternionf camera, double x, double y, double z,
                             float radius, double strength) {
        halo(vc, ps, camera, x, y, z, radius, strength, AR, AG, AB);
    }

    private static void halo(VertexConsumer vc, PoseStack ps, Quaternionf camera, double x, double y, double z,
                             float radius, double strength, int red, int green, int blue) {
        ps.pushPose();
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        ps.translate(x, y, z);
        ps.mulPose(camera);
        final Matrix4f m = ps.last().pose();
        final int a = (int) (255 * Math.max(0.0, Math.min(1.0, strength)));
        final int segs = 16;
        for (int k = 0; k < segs; k++) {
            final double a0 = 2 * Math.PI * k / segs;
            final double a1 = 2 * Math.PI * (k + 1) / segs;
            vc.addVertex(m, 0, 0, 0).setColor(red, green, blue, a);
            vc.addVertex(m, (float) (Math.cos(a0) * radius), (float) (Math.sin(a0) * radius), 0).setColor(red, green, blue, 0);
            vc.addVertex(m, (float) (Math.cos(a1) * radius), (float) (Math.sin(a1) * radius), 0).setColor(red, green, blue, 0);
            vc.addVertex(m, 0, 0, 0).setColor(red, green, blue, a);
        }
        ps.popPose();
    }

    private static void drawStudWires(StormCoilBE be, PoseStack ps, MultiBufferSource buffers, int light) {
        if (be.wiresOnStuds() <= 0) {
            return;
        }
        final int turns = StormCoilBlock.turns(be.getBlockState());
        final VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final Matrix4f mat = ps.last().pose();
        final BlockPos origin = be.getBlockPos();
        for (int stud = 0; stud < StormCoilStuds.COUNT; stud++) {
            final double[] from = StormCoilStuds.anchor(turns, stud);
            for (StormCoilBE.StudLink link : be.studLinks(stud)) {
                final Vec3 to = PowerConnectorRenderer.attachPoint(be.getLevel(), link.to(), origin);
                PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link.to(),
                        (float) from[0], (float) from[1], (float) from[2],
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        light, link.tier());
            }
        }
    }
}
