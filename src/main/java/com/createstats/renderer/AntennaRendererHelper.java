package com.createstats.renderer;

import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class AntennaRendererHelper {
    private static final ResourceLocation BLANK_TEXTURE = Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));
    private static final float PX = 1.0f / 16.0f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int DARK_CASING = 0x263039;
    private static final int ANDESITE_EDGE = 0x5F666A;
    private static final int ANDESITE_CASING = 0x6E7479;
    private static final int BRASS = 0xC39143;
    private static final int BRASS_LIGHT = 0xE0B85A;
    private static final int COPPER = 0xB46A3C;
    private static final int REDSTONE = 0xCE3F32;
    private static final int EMERALD = 0x17C44C;
    private static final int OXIDIZED_TEAL = 0x4FBDA6;
    private static final int TRT_CORE = 0x63B7D8;

    private AntennaRendererHelper() {
    }

    public static void render(BlockEntity be, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        if (!(be instanceof RangeAttachment attachment)) {
            return;
        }
        AntennaTier tier = attachment.getAntennaTier();
        if (!tier.isAttached()) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = resolveFacing(state);
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRot(facing) + facingOffset(be)));
        ps.translate(-0.5, -0.5, -0.5);
        renderMountedAntenna(be, ps, buf, tier, packedLight, packedOverlay);
        ps.popPose();
    }

    private static float facingOffset(BlockEntity be) {
        return be instanceof DispatchBoardBE ? 180.0f : 0.0f;
    }

    private static void renderMountedAntenna(BlockEntity be, PoseStack ps, MultiBufferSource buf, AntennaTier tier, int packedLight, int packedOverlay) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(BLANK_TEXTURE));
        // [1]
        int light = packedLight;
        Matrix4f pose = ps.last().pose();
        if (be instanceof StatsDisplayPanelBE panel) {
            float widthPx = Math.max(1, panel.getMultiblockWidth()) * 16.0f;
            float heightPx = Math.max(1, panel.getMultiblockHeight()) * 16.0f;
            drawAntenna(vc, pose, tier, widthPx - 7.0f, heightPx - 0.8f, 2.0f, light, packedOverlay, true);
            drawTopStatusTab(vc, pose, tier, widthPx - 15.0f, heightPx + 0.15f, 2.25f, light, packedOverlay);
            return;
        }
        if (be instanceof DispatchBoardBE board) {
            float widthPx = Math.max(1, board.getMultiblockWidth()) * 16.0f;
            float heightPx = Math.max(1, board.getMultiblockHeight()) * 16.0f;
            drawAntenna(vc, pose, tier, widthPx - 7.0f, heightPx - 0.8f, 10.4f, light, packedOverlay, true);
            drawTopStatusTab(vc, pose, tier, widthPx - 15.0f, heightPx + 0.15f, 10.65f, light, packedOverlay);
            return;
        }
        if (be instanceof HolographicProjectorBE) {
            drawAntenna(vc, pose, tier, 10.0f, 8.7f, 10.0f, light, packedOverlay, false);
            return;
        }
        drawAntenna(vc, pose, tier, 9.8f, 14.0f, 9.8f, light, packedOverlay, false);
    }

    private static void drawAntenna(VertexConsumer vc, Matrix4f pose, AntennaTier tier, float x, float y, float z,
                                    int light, int packedOverlay, boolean railMounted) {
        // Silhouette + palette mirror the textured item models (small/advanced/trt_signal_antenna.json):
        // andesite plinth -> brass collar -> chunky segmented mast -> cross-dipole arms -> seated emissive head.
        float height = tier == AntennaTier.SMALL ? 10.0f : tier == AntennaTier.ADVANCED ? 11.0f : 12.5f;
        int mast = tier == AntennaTier.SMALL ? BRASS : COPPER;
        int accent = tier == AntennaTier.SMALL ? EMERALD : tier == AntennaTier.ADVANCED ? REDSTONE : OXIDIZED_TEAL;
        int collar = railMounted ? ANDESITE_EDGE : BRASS;
        // Grounded base: an overhanging andesite flange + plinth so the antenna clamps onto its host.
        cuboidPx(vc, pose, x - 0.4f, y - 0.1f, z - 0.4f, x + 6.4f, y + 0.6f, z + 4.4f, DARK_CASING, light, packedOverlay);
        cuboidPx(vc, pose, x + 0.0f, y + 0.0f, z + 0.0f, x + 6.0f, y + 1.8f, z + 4.0f, ANDESITE_CASING, light, packedOverlay);
        // Brass collar (wide, reads as a real mounting flange).
        cuboidPx(vc, pose, x + 0.6f, y + 1.6f, z + 0.6f, x + 5.4f, y + 3.4f, z + 3.4f, collar, light, packedOverlay);
        // Chunky segmented mast (1.7px wide): lower stalk, a darker mid insulator band, then the upper stalk.
        float midA = y + height * 0.5f;
        float midB = midA + 1.0f;
        cuboidPx(vc, pose, x + 2.15f, y + 3.2f, z + 1.15f, x + 3.85f, midA, z + 2.85f, mast, light, packedOverlay);
        cuboidPx(vc, pose, x + 1.95f, midA, z + 0.95f, x + 4.05f, midB, z + 3.05f, DARK_CASING, light, packedOverlay);
        cuboidPx(vc, pose, x + 2.15f, midB, z + 1.15f, x + 3.85f, y + height, z + 2.85f, mast, light, packedOverlay);
        // Cross-dipole arms near the top (brass, thicker so they don't look spindly).
        cuboidPx(vc, pose, x - 0.5f, y + height - 3.4f, z + 1.4f, x + 6.5f, y + height - 1.9f, z + 2.6f, BRASS, light, packedOverlay);
        if (tier != AntennaTier.SMALL) {
            cuboidPx(vc, pose, x + 2.4f, y + height - 3.4f, z - 1.5f, x + 3.6f, y + height - 1.9f, z + 5.5f, BRASS, light, packedOverlay);
        }
        // Brass insulator shoulder that the emissive head sits on (kills the "floating cube" look).
        cuboidPx(vc, pose, x + 1.9f, y + height - 0.4f, z + 0.9f, x + 4.1f, y + height + 0.4f, z + 3.1f, BRASS_LIGHT, light, packedOverlay);
        // Seated emissive head (full-bright) so the tier reads at a glance and glows at night.
        cuboidPx(vc, pose, x + 2.2f, y + height + 0.4f, z + 1.2f, x + 3.8f, y + height + 1.9f, z + 2.8f, accent, FULL_BRIGHT, packedOverlay);
        if (tier == AntennaTier.TRT) {
            cuboidPx(vc, pose, x + 2.55f, y + height + 1.9f, z + 1.55f, x + 3.45f, y + height + 2.8f, z + 2.45f, TRT_CORE, FULL_BRIGHT, packedOverlay);
        }
    }

    private static void drawTopStatusTab(VertexConsumer vc, Matrix4f pose, AntennaTier tier, float x, float y, float z,
                                         int light, int packedOverlay) {
        int accent = tier == AntennaTier.SMALL ? EMERALD : tier == AntennaTier.ADVANCED ? REDSTONE : OXIDIZED_TEAL;
        cuboidPx(vc, pose, x + 0.0f, y + 0.0f, z + 0.0f, x + 6.5f, y + 0.7f, z + 2.1f, ANDESITE_CASING, light, packedOverlay);
        // Emissive status bead.
        cuboidPx(vc, pose, x + 0.7f, y + 0.7f, z + 0.35f, x + 2.1f, y + 1.45f, z + 1.75f, accent, FULL_BRIGHT, packedOverlay);
        cuboidPx(vc, pose, x + 2.8f, y + 0.7f, z + 0.45f, x + 5.8f, y + 1.15f, z + 1.65f, BRASS_LIGHT, light, packedOverlay);
    }

    private static void cuboidPx(VertexConsumer vc, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
                                 int color, int packedLight, int packedOverlay) {
        cuboid(vc, pose, x0 * PX, y0 * PX, z0 * PX, x1 * PX, y1 * PX, z1 * PX, color, packedLight, packedOverlay);
    }

    private static Direction resolveFacing(BlockState state) {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction direction = state.getValue(BlockStateProperties.FACING);
            return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
        }
        return Direction.NORTH;
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    private static void cuboid(VertexConsumer vc, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
                               int color, int packedLight, int packedOverlay) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int overlay = packedOverlay == 0 ? OverlayTexture.NO_OVERLAY : packedOverlay;
        face(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, packedLight, overlay, 0, 0, 1);
        face(vc, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, packedLight, overlay, 0, 0, -1);
        face(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, packedLight, overlay, -1, 0, 0);
        face(vc, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, packedLight, overlay, 1, 0, 0);
        face(vc, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, packedLight, overlay, 0, 1, 0);
        face(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, packedLight, overlay, 0, -1, 0);
    }

    private static void face(VertexConsumer vc, Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int r, int g, int b, int light, int overlay, int nx, int ny, int nz) {
        vertex(vc, pose, x0, y0, z0, r, g, b, light, overlay, nx, ny, nz);
        vertex(vc, pose, x1, y1, z1, r, g, b, light, overlay, nx, ny, nz);
        vertex(vc, pose, x2, y2, z2, r, g, b, light, overlay, nx, ny, nz);
        vertex(vc, pose, x3, y3, z3, r, g, b, light, overlay, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z,
                               int r, int g, int b, int light, int overlay, int nx, int ny, int nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(0.0f, 0.0f)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
