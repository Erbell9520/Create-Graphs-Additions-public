package com.createstats.statsmk3.render;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.renderer.AntennaRendererHelper;
import com.createstats.statsmk3.Mk3ConsoleBlockEntity;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelModelCache;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.paint.WorldSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Tokens;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Mk3PanelRenderer implements BlockEntityRenderer<Mk3PanelBlockEntity> {
    private static final float BASE_PIXELS_PER_BLOCK = 64.0f;

    private static final float FACE_DEPTH = 3.0f / 16.0f + 0.03125f;

    private static final float FACE_LIFT = 0.03125f;

    // stay inside the recess, the face used to paint over the bezel and the lamp strip
    private static final float BEZEL_SIDE_TEXELS = 2.0f;
    private static final float BEZEL_TOP_TEXELS = 2.0f;
    private static final float BEZEL_BOTTOM_TEXELS = 3.0f;

    private static final float REBATE_BLOCKS = 2.0f / 64.0f;

    private static final int WALL_LAMPS = 6;

    private static final int CONSOLE_LAMPS = 4;

    private static float pixelsPerBlock(PanelModel model) {
        final float scale = model == null || !Float.isFinite(model.style.faceScale)
                ? com.createstats.statsmk3.Mk3Settings.DEFAULT_FACE_SCALE
                : Math.max(com.createstats.statsmk3.Mk3Settings.MIN_FACE_SCALE,
                        Math.min(com.createstats.statsmk3.Mk3Settings.MAX_FACE_SCALE,
                                model.style.faceScale));
        return BASE_PIXELS_PER_BLOCK / scale;
    }

    private static int evictCounter;

    public Mk3PanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(Mk3PanelBlockEntity be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.isController()) {
            return;
        }
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        AntennaRendererHelper.render(be, ps, buffers, packedLight, packedOverlay);

        final Minecraft mc = Minecraft.getInstance();
        final Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        final Vec3 at = SableBridge.worldPointOf(be, Vec3.atCenterOf(be.getBlockPos()));
        final double distSq = at == null ? 0.0 : camera.distanceToSqr(at);
        final int mbW = Math.max(1, be.getMultiblockWidth());
        final int mbH = Math.max(1, be.getMultiblockHeight());

        final Lod lod = Lod.forView(distSq, mbW, mbH, be.mk3Settings().faceScale);

        final float brightness = com.createstats.power.client.FePowerVisuals
                .screenBrightness(be.getControllerBE().getBlockPos(), be.feConsumer());
        if (brightness <= 0.01f) {
            return;
        }

        final PanelModel model = PanelModelCache.get(be.getBlockPos())
                .build(be, lod.historyBudget(), clientSeconds());

        if (model.faceMode.isTrain()) {
            com.createstats.train.smart.ClientRailNetwork.requestUpdates();
        }

        if (++evictCounter >= 600) {
            evictCounter = 0;
            PanelModelCache.evictIdle();
        }

        final Direction facing = (Direction) state.getValue(HorizontalDirectionalBlock.FACING);
        final boolean console = be instanceof Mk3ConsoleBlockEntity;
        final float px = pixelsPerBlock(model);

        ps.pushPose();
        final Rect face = console ? orientConsole(ps, facing, px)
                : orientWall(ps, facing, mbW, mbH, px);

        final WorldSurface surface = new WorldSurface(ps, buffers, mc.font, packedLight, brightness);
        PanelPainter.paint(surface, face, model, lod);
        drawRebate(surface, face, px);
        drawBezelGauge(surface, be.feConsumer(), console, mbW, mbH, px);

        surface.finish();

        ps.popPose();
    }

    private static void drawRebate(WorldSurface s, Rect face, float px) {
        s.advanceLayer();
        final float rebate = REBATE_BLOCKS * px;
        final int lip = Tokens.alpha(Tokens.LINE, 0.55f);
        Shapes.rect(s, face.x, face.y, face.w, rebate, lip);
        Shapes.rect(s, face.x, face.y + rebate, rebate, face.h - rebate, lip);
    }

    private static void drawBezelGauge(WorldSurface s, com.createstats.power.FeConsumer fe,
                                       boolean console, int mbW, int mbH, float px) {
        final int perTile = console ? CONSOLE_LAMPS : WALL_LAMPS;
        final int total = console ? perTile : perTile * mbW;
        if (total <= 0) {
            return;
        }

        final float wallTexel = px / 16.0f;

        final float consoleTexelX = 14.0f / 16.0f * px / 16.0f;
        final float consoleTexelY = 11.0f / 16.0f * px / 16.0f;
        final float fill = gaugeFill(fe);

        final int lit = fill <= 0.0f ? 0 : Math.max(1, Math.min(total, Math.round(fill * total)));
        final int on = gaugeColor(fe);

        s.advanceLayer();
        for (int i = 0; i < total; i++) {
            final int color = i < lit ? on : Tokens.LINE_HI;
            if (console) {
                Shapes.rect(s, 7.0f * consoleTexelX + i * 2.0f * consoleTexelX, 14.0f * consoleTexelY,
                        consoleTexelX, consoleTexelY, color);
                continue;
            }

            final float x = (i / perTile) * px + 3.0f * wallTexel
                    + (i % perTile) * 2.0f * wallTexel;
            Shapes.rect(s, x, (mbH - 1) * px + 14.0f * wallTexel, wallTexel, wallTexel, color);
        }
    }

    private static float gaugeFill(com.createstats.power.FeConsumer fe) {
        if (fe == null || !fe.requiresPower()) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, fe.fillPct() / 100.0f));
    }

    private static int gaugeColor(com.createstats.power.FeConsumer fe) {
        if (fe == null || !fe.requiresPower() || (fe.isPowered() && !fe.isBrownout())) {
            return Tokens.GOOD;
        }
        return fe.isPowered() ? Tokens.CAUTION : Tokens.CRITICAL;
    }

    private static void yaw(PoseStack ps, Direction facing) {
        final float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static Rect orientWall(PoseStack ps, Direction facing, int mbW, int mbH, float px) {
        yaw(ps, facing);
        ps.translate(0.0, 0.0, FACE_DEPTH);

        final float s = 1.0f / px;
        ps.translate(0.0, mbH, 0.0);
        ps.scale(s, -s, s);
        final float texel = px / 16.0f;
        final float side = BEZEL_SIDE_TEXELS * texel;
        final float top = BEZEL_TOP_TEXELS * texel;
        final float bottom = BEZEL_BOTTOM_TEXELS * texel;
        return new Rect(side, top, mbW * px - side * 2.0f, mbH * px - top - bottom);
    }

    private static Rect orientConsole(PoseStack ps, Direction facing, float px) {
        yaw(ps, facing);

        ps.translate(8.0 / 16.0, 9.0 / 16.0, 15.0 / 16.0);
        ps.mulPose(Axis.XP.rotationDegrees(-22.5f));
        ps.translate(-8.0 / 16.0, -9.0 / 16.0, -15.0 / 16.0);

        ps.translate(15.0 / 16.0, 14.0 / 16.0 + FACE_LIFT, 15.0 / 16.0);

        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.mulPose(Axis.XP.rotationDegrees(-90.0f));

        final float s = 1.0f / px;
        ps.scale(s, -s, s);
        return consoleFace(px);
    }

    private static Rect consoleFace(float px) {
        return new Rect(
                2.0f / 16.0f * 14.0f / 16.0f * px,
                1.0f / 16.0f * 11.0f / 16.0f * px,
                12.0f / 16.0f * 14.0f / 16.0f * px,
                11.0f / 16.0f * 11.0f / 16.0f * px);
    }

    private static float clientSeconds() {
        return (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f;
    }

    @Override
    public boolean shouldRenderOffScreen(Mk3PanelBlockEntity be) {
        return false;
    }

    @Override
    public AABB getRenderBoundingBox(Mk3PanelBlockEntity be) {
        return be.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
