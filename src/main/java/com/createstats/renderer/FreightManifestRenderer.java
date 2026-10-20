package com.createstats.renderer;

import com.createstats.block.FreightManifestClipboardBlock;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.FreightManifestPageSummary;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class FreightManifestRenderer implements BlockEntityRenderer<FreightManifestClipboardBE> {
    private static final ResourceLocation BLANK =
            Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));

    private static final float PX = 1.0f / 16.0f;

    private static final float RAKE_DEGREES = 22.5f;
    private static final float PIVOT_X = 8.0f;
    private static final float PIVOT_Y = 5.0f;
    private static final float PIVOT_Z = 8.0f;
    private static final float STANDING_PAGE_Z = 6.5f;
    private static final float WALL_PAGE_Z = 13.5f;

    private static final float STANDING_FIRST_ROW_Y = 10.0f;
    private static final float WALL_FIRST_ROW_Y = 8.0f;
    private static final float STANDING_FOOTER_Y = 6.0f;
    private static final float WALL_FOOTER_Y = 4.0f;

    private static final int COL_PIP = 6;
    private static final int COL_BAR_START = 7;
    private static final int COL_BAR_END = 11;
    private static final int COL_FOOTER_START = 5;
    private static final int COL_FOOTER_END = 11;

    private static final int TO_TRAIN = 0x1E6E86;
    private static final int TO_STATION = 0x5A7A1E;
    private static final int DONE = 0x2E7538;

    private static final int TRACK = 0xB0A181;
    private static final int STAMP = 0xA02F28;
    private static final int FAULT = 0x8A3630;
    private static final int OVERFLOW = 0x6B6050;

    public FreightManifestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FreightManifestClipboardBE be, float partialTick, PoseStack ps,
                       MultiBufferSource buf, int packedLight, int packedOverlay) {
        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof FreightManifestClipboardBlock)) {
            return;
        }
        FreightManifestPageSummary page = be.getPageSummary();
        if (!page.isLinked()) {
            return;
        }

        boolean wall = state.getValue(FreightManifestClipboardBlock.MOUNT)
                == FreightManifestClipboardBlock.MountType.WALL;
        float pageZ = wall ? WALL_PAGE_Z : STANDING_PAGE_Z;
        float firstRowY = wall ? WALL_FIRST_ROW_Y : STANDING_FIRST_ROW_Y;
        float footerY = wall ? WALL_FOOTER_Y : STANDING_FOOTER_Y;

        ps.pushPose();

        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw(state.getValue(FreightManifestClipboardBlock.FACING))));
        ps.translate(-0.5, -0.5, -0.5);
        if (!wall) {
            ps.translate(PIVOT_X * PX, PIVOT_Y * PX, PIVOT_Z * PX);
            ps.mulPose(Axis.XP.rotationDegrees(RAKE_DEGREES));
            ps.translate(-PIVOT_X * PX, -PIVOT_Y * PX, -PIVOT_Z * PX);
        }

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BLANK));
        Matrix4f pose = ps.last().pose();
        double time = com.createstats.client.AnimationClock.time(be.getLevel(), partialTick);

        if (!page.isReachable()) {
            bar(vc, pose, pageZ, footerY + 0.25f, footerY + 0.75f,
                    COL_FOOTER_END - 2, COL_FOOTER_END, FAULT, packedLight, packedOverlay, 1);
            ps.popPose();
            return;
        }

        for (int i = 0; i < page.getRowCount(); i++) {
            float y = firstRowY - i;
            int hue = page.isRowToTrain(i) ? TO_TRAIN : TO_STATION;
            int fill = page.isRowComplete(i) ? DONE : hue;

            bar(vc, pose, pageZ, y + 0.15f, y + 0.85f, COL_PIP, COL_PIP + 1, darken(hue, 0.28f),
                    packedLight, packedOverlay, 1);

            bar(vc, pose, pageZ, y + 0.3f, y + 0.7f, COL_BAR_START, COL_BAR_END, TRACK,
                    packedLight, packedOverlay, 1);
            float progress = page.getRowProgress(i);
            if (progress > 0.001f) {
                float span = (COL_BAR_END - COL_BAR_START) * progress;
                bar(vc, pose, pageZ, y + 0.3f, y + 0.7f, COL_BAR_START, COL_BAR_START + span, fill,
                        packedLight, packedOverlay, 2);

                if (page.isTrainPresent() && !page.isRowComplete(i)) {
                    float period = 3.4f;
                    float head = (float) ((time * 0.09 + i * 0.8f) % period);
                    if (head < span) {
                        float w = Math.min(0.55f, span - head);
                        bar(vc, pose, pageZ, y + 0.32f, y + 0.68f,
                                COL_BAR_START + head, COL_BAR_START + head + w,
                                brighten(fill, 0.45f), packedLight, packedOverlay, 3);
                    }
                }
            }
        }

        if (page.hasOverflow()) {
            float y = firstRowY - (page.getRowCount() - 1);
            for (int d = 0; d < 3; d++) {
                float c = COL_BAR_END + 0.05f - d * 0.3f;
                bar(vc, pose, pageZ, y + 0.4f, y + 0.6f, c, c + 0.18f, OVERFLOW,
                        packedLight, packedOverlay, 3);
            }
        }

        bar(vc, pose, pageZ, footerY + 0.25f, footerY + 0.75f,
                COL_FOOTER_START, COL_FOOTER_END, TRACK, packedLight, packedOverlay, 1);
        if (page.hasWork()) {
            boolean stamped = page.isStamped();
            float span = (COL_FOOTER_END - COL_FOOTER_START) * page.getBoardProgress();
            if (span > 0.02f) {
                bar(vc, pose, pageZ, footerY + 0.25f, footerY + 0.75f,
                        COL_FOOTER_START, COL_FOOTER_START + span, stamped ? STAMP : DONE,
                        packedLight, packedOverlay, 2);
            }
            if (stamped) {
                stampBox(vc, pose, pageZ, footerY, firstRowY, packedLight, packedOverlay);
            }
        }

        ps.popPose();
    }

    private static void bar(VertexConsumer vc, Matrix4f pose, float pageZ, float y0, float y1,
                            float colStart, float colEnd, int rgb, int light, int overlay, int layer) {
        if (colEnd <= colStart || y1 <= y0) {
            return;
        }

        float z = pageZ - (0.18f + 0.07f * layer);

        float xLeft = colToX(colStart);
        float xRight = colToX(colEnd);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        vertex(vc, pose, xLeft, y0, z, r, g, b, light, overlay);
        vertex(vc, pose, xRight, y0, z, r, g, b, light, overlay);
        vertex(vc, pose, xRight, y1, z, r, g, b, light, overlay);
        vertex(vc, pose, xLeft, y1, z, r, g, b, light, overlay);
    }

    private static void stampBox(VertexConsumer vc, Matrix4f pose, float pageZ,
                                 float footerY, float firstRowY, int light, int overlay) {
        float top = firstRowY + 1.05f;
        float bottom = footerY + 0.02f;
        bar(vc, pose, pageZ, top, top + 0.16f, COL_FOOTER_START - 0.4f, COL_BAR_END + 0.4f,
                STAMP, light, overlay, 3);
        bar(vc, pose, pageZ, bottom, bottom + 0.16f, COL_FOOTER_START - 0.4f, COL_BAR_END + 0.4f,
                STAMP, light, overlay, 3);
        bar(vc, pose, pageZ, bottom, top + 0.16f, COL_FOOTER_START - 0.4f, COL_FOOTER_START - 0.24f,
                STAMP, light, overlay, 3);
        bar(vc, pose, pageZ, bottom, top + 0.16f, COL_BAR_END + 0.24f, COL_BAR_END + 0.4f,
                STAMP, light, overlay, 3);
    }

    private static float colToX(float c) {
        return 16.0f - c;
    }

    private static int darken(int rgb, float amount) {
        int r = Math.round(((rgb >> 16) & 0xFF) * (1f - amount));
        int g = Math.round(((rgb >> 8) & 0xFF) * (1f - amount));
        int b = Math.round((rgb & 0xFF) * (1f - amount));
        return (r << 16) | (g << 8) | b;
    }

    private static int brighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z,
                               int r, int g, int b, int light, int overlay) {
        vc.addVertex(pose, x * PX, y * PX, z * PX)
                .setColor(r, g, b, 255)
                .setUv(0.0f, 0.0f)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    private static float yaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(FreightManifestClipboardBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
