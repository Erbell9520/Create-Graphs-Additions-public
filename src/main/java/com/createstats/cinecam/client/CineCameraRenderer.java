package com.createstats.cinecam.client;

import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.CineCameraBlock;
import com.createstats.cinecam.core.CineHead;
import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.CineRig;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

public class CineCameraRenderer implements BlockEntityRenderer<CineCameraBE> {
    private static final float PX = 1f / 16f;
    private static final float DIM = 0.62f;
    private static final PartialModel[] REELS = {ModPartialModels.CINE_REEL_F, ModPartialModels.CINE_REEL_R};
    private static final double[][] REEL_PIVOTS = {CineRig.P_REEL_F, CineRig.P_REEL_R};

    public CineCameraRenderer(BlockEntityRendererProvider.Context context) {
    }

    public record Pose(float yaw, float pitch, float reel, double driver, float plunger, int mode, boolean filming) {
        public static final Pose REST = new Pose(0f, 6f, 0f, 0.0, 0f, 0, false);
    }

    // head is a block up, don't let section culling take it
    @Override
    public boolean shouldRenderOffScreen(CineCameraBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(CineCameraBE be) {
        return new AABB(be.getBlockPos()).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 1.6, 0.0);
    }

    @Override
    public void render(CineCameraBE be, float partialTick, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CineCameraBlock) || state.getValue(CineCameraBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        if (CineView.lookingThrough(be.getBlockPos())) return;
        int upper = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        Pose pose = new Pose(
                Mth.lerp(partialTick, be.prevYaw, be.dispYaw),
                Mth.lerp(partialTick, be.prevPitch, be.dispPitch),
                Mth.lerp(partialTick, be.prevReel, be.reel),
                Mth.lerp(partialTick, be.prevDriver, be.driver),
                Mth.lerp(partialTick, be.prevPlunger, be.plunger),
                be.mode(), be.filming());
        draw(ms, buffer, state, light, upper, pose);
    }

    public static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int lower, int upper, Pose p) {
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        ms.pushPose();
        about(ms, 8, 0, 8, Axis.YP.rotationDegrees((float) -CineCameraBlock.facingDegrees(state)));

        part(ms, vc, state, ModPartialModels.CINE_CROWN, lower);
        for (double bearing : CineRig.BEARINGS) {
            ms.pushPose();
            about(ms, 8, 0, 8, Axis.YP.rotationDegrees((float) -bearing));
            part(ms, vc, state, ModPartialModels.CINE_LUG, lower);
            ms.pushPose();
            move(ms, 8, CineRig.HINGE_Y, 8 - CineRig.HINGE_RADIUS);
            ms.mulPose(Axis.XP.rotationDegrees((float) CineRig.LEG_SPLAY));
            move(ms, -8, -CineRig.LEG_LEN, -8);
            part(ms, vc, state, ModPartialModels.CINE_LEG, lower);
            ms.popPose();
            ms.pushPose();
            move(ms, 0, 0, -CineRig.FOOT_RADIUS);
            part(ms, vc, state, ModPartialModels.CINE_FOOT, lower);
            ms.popPose();
            ms.pushPose();
            move(ms, 8, CineRig.BRACE_Y, 8 - CineRig.BRACE_R);
            ms.mulPose(Axis.XP.rotationDegrees((float) CineRig.BRACE_TILT));
            move(ms, -8, 0, -8);
            part(ms, vc, state, ModPartialModels.CINE_BRACE, lower);
            ms.popPose();
            ms.popPose();
        }

        move(ms, 0, CineRig.HT, 0);
        ms.pushPose();
        about(ms, CineRig.P_HEAD, Axis.YP.rotationDegrees((float) CineRig.AZ_WHEEL_TURN));
        about(ms, CineRig.P_AZ_WHEEL, Axis.YP.rotationDegrees((float) CineHead.azimuthWheel(p.yaw())));
        part(ms, vc, state, ModPartialModels.CINE_AZ_WHEEL, upper);
        ms.popPose();

        about(ms, CineRig.P_HEAD, Axis.YP.rotationDegrees(p.yaw()));
        part(ms, vc, state, ModPartialModels.CINE_HEAD, upper);
        ms.pushPose();
        about(ms, CineRig.P_EL_WHEEL, Axis.XP.rotationDegrees((float) CineHead.elevationWheel(p.pitch())));
        part(ms, vc, state, ModPartialModels.CINE_EL_WHEEL, upper);
        ms.popPose();

        about(ms, CineRig.P_BODY, Axis.XP.rotationDegrees(p.pitch()));
        part(ms, vc, state, ModPartialModels.CINE_BODY, upper);
        for (int r = 0; r < REELS.length; r++) {
            ms.pushPose();
            about(ms, REEL_PIVOTS[r], Axis.XP.rotationDegrees(p.reel()));
            part(ms, vc, state, REELS[r], upper);
            ms.popPose();
        }

        double wheel = CineRig.geneva(p.driver());
        ms.pushPose();
        about(ms, CineRig.P_WHEEL, Axis.ZP.rotationDegrees((float) wheel));
        part(ms, vc, state, ModPartialModels.CINE_WHEEL, upper);
        for (int k = 0; k < CineMode.SLOTS; k++) {
            int colour = CineMode.slotColour(k);

            boolean lit = Math.abs(CineHead.wrap180(90.0 * k + wheel)) < 20.0;
            tinted(ms, vc, state, ModPartialModels.CINE_FILTERS[k], lit ? LightTexture.FULL_BRIGHT : upper, colour,
                    lit ? 1f : DIM, lit);
        }
        ms.popPose();

        ms.pushPose();
        about(ms, CineRig.P_DRIVER, Axis.ZP.rotationDegrees((float) p.driver()));
        part(ms, vc, state, ModPartialModels.CINE_DRIVER, upper);
        ms.popPose();

        tinted(ms, vc, state, ModPartialModels.CINE_EYEGLASS, LightTexture.FULL_BRIGHT, CineMode.byIndex(p.mode()).colour(),
                1f, true);
        tinted(ms, vc, state, ModPartialModels.CINE_TALLY, p.filming() ? LightTexture.FULL_BRIGHT : upper, 0xFFFFFFFF,
                p.filming() ? 1f : 0.42f, p.filming());

        ms.pushPose();
        move(ms, 0, -p.plunger(), 0);
        part(ms, vc, state, ModPartialModels.CINE_PLUNGER, upper);
        ms.popPose();

        ms.popPose();
    }

    private static void part(PoseStack ms, VertexConsumer vc, BlockState state, PartialModel model, int light) {
        CachedBuffers.partial(model, state).light(light).renderInto(ms, vc);
    }

    private static void tinted(PoseStack ms, VertexConsumer vc, BlockState state, PartialModel model, int light, int argb,
                               float scale, boolean glow) {
        SuperByteBuffer b = CachedBuffers.partial(model, state).light(light)
                .color((int) (((argb >> 16) & 0xFF) * scale), (int) (((argb >> 8) & 0xFF) * scale),
                        (int) ((argb & 0xFF) * scale), 255);
        if (glow) b.disableDiffuse();
        b.renderInto(ms, vc);
    }

    private static void move(PoseStack ms, double x, double y, double z) {
        ms.translate(x * PX, y * PX, z * PX);
    }

    private static void about(PoseStack ms, double x, double y, double z, Quaternionf q) {
        move(ms, x, y, z);
        ms.mulPose(q);
        move(ms, -x, -y, -z);
    }

    private static void about(PoseStack ms, double[] pivot, Quaternionf q) {
        about(ms, pivot[0], pivot[1], pivot[2], q);
    }
}
