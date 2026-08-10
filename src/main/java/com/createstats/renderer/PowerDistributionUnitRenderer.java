package com.createstats.renderer;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FePriority;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

import java.util.Locale;

/** [1] */
public class PowerDistributionUnitRenderer implements BlockEntityRenderer<PowerDistributionUnitBE> {

    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/panel_solid.png");

    // [2]
    private static final float SCREEN_L = 3.0f / 16.0f - 0.5f;
    private static final float SCREEN_R = 13.0f / 16.0f - 0.5f;
    private static final float SCREEN_B = 6.0f / 16.0f - 0.5f;
    private static final float SCREEN_T = 14.0f / 16.0f - 0.5f;
    /** Distance from the block centre out to just proud of the glass. */
    private static final float SCREEN_OUT = 0.5f - 4.0f / 16.0f + 0.002f;

    private static final int COL_GLASS  = 0xFF070A0E;
    private static final int COL_TROUGH = 0xFF161C24;
    private static final int COL_RULE   = 0xFF2A3541;
    private static final int COL_LABEL  = 0xFF6E8093;
    private static final int COL_VALUE  = 0xFFD8E4EE;
    private static final int COL_STEEL  = 0xFF7C8C9C;
    private static final int COL_GREEN  = 0xFF58DC7A;
    private static final int COL_AMBER  = 0xFFFFBA3C;
    private static final int COL_RED    = 0xFFFF4A4A;
    /** The reserve-floor tick, matching the panel screen so the two surfaces agree. */
    private static final int COL_RESERVE = 0xFFFF6A3C;

    /** Four characters is what fits under a lamp at this size, so the tiers are abbreviated. */
    private static final String[] TIER_LABELS = {"CRIT", "HIGH", "NORM", "LOW", "OPT"};

    private final Font font;

    public PowerDistributionUnitRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(PowerDistributionUnitBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        final Direction facing = be.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? be.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;

        // [3]
        drawStudWires(be, facing, poseStack, buffers, packedLight);

        poseStack.pushPose();
        // [4]
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0.0f, 0.0f, SCREEN_OUT);

        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PANEL_TEXTURE));
        final Matrix4f mat = poseStack.last().pose();
        final int light = LightTexture.FULL_BRIGHT;

        final FeGridSolver.BranchResult branch = be.lastBranchResult();
        final float fill = be.fillFraction();
        final boolean noInput = be.storedEnergy() <= 0;
        final com.createstats.power.PduMode mode = be.mode();
        final boolean auto = mode == com.createstats.power.PduMode.AUTO;

        quad(vc, mat, SCREEN_L, SCREEN_B, SCREEN_R, SCREEN_T, COL_GLASS, light);

        final float pad = 0.022f;
        final float x0 = SCREEN_L + pad;
        final float x1 = SCREEN_R - pad;
        final float w = x1 - x0;

        // [5]
        float y = SCREEN_T - pad;

        // [6]
        final float titleH = 0.050f;
        text(poseStack, buffers, "DISTRIBUTION", x0, y - titleH, COL_LABEL, light, 0.0062f);
        final int modeColour = auto ? COL_GREEN
                : mode == com.createstats.power.PduMode.MANUAL ? COL_STEEL : COL_RULE;
        textRight(poseStack, buffers, mode.name(), x1, y - titleH, modeColour, light, 0.0062f);
        y -= titleH + 0.014f;

        quad(vc, mat, x0, y - 0.006f, x1, y, COL_RULE, light);
        y -= 0.006f + 0.014f;

        // Buffer bar. Neutral steel while healthy; it only takes colour when the reserve is low,
        // so a working panel is achromatic.
        final float barH = 0.062f;
        final int barColour = fill < 0.15f ? COL_RED : fill < 0.30f ? COL_AMBER : COL_STEEL;
        final float barFill = x0 + w * Mth.clamp(fill, 0.0f, 1.0f);
        quad(vc, mat, x0, y - barH, barFill, y, barColour, light);
        quad(vc, mat, barFill, y - barH, x1, y, COL_TROUGH, light);
        // Reserve floor tick: below this only CRITICAL draws. Server truth, not local config.
        final float floorX = x0 + w * Mth.clamp(be.reserveFloorFraction(), 0.0f, 1.0f);
        quad(vc, mat, floorX, y - barH - 0.006f, floorX + 0.004f, y + 0.006f, COL_RESERVE, light);
        y -= barH + 0.018f;

        // Supply against demand, only in AUTO because the figures behind it are only computed there.
        // Same picture as the panel screen, so the two surfaces teach one reading.
        if (auto) {
            final int gen = be.generationFePerTick();
            final int demand = be.branchDemandFePerTick();
            final int served = be.servedDemandFePerTick();
            final int scale = Math.max(1, Math.max(gen, demand));
            final float cueH = 0.026f;

            final float genW = w * (Math.min(gen, scale) / (float) scale);
            quad(vc, mat, x0, y - cueH, x1, y, COL_TROUGH, light);
            quad(vc, mat, x0, y - cueH, x0 + genW, y, COL_STEEL, light);
            y -= cueH + 0.008f;

            final float servedW = w * (Math.min(served, scale) / (float) scale);
            final float demandW = w * (Math.min(demand, scale) / (float) scale);
            quad(vc, mat, x0, y - cueH, x1, y, COL_TROUGH, light);
            quad(vc, mat, x0, y - cueH, x0 + servedW, y, COL_GREEN, light);
            quad(vc, mat, x0 + servedW, y - cueH, x0 + demandW, y, COL_AMBER, light);
            y -= cueH + 0.016f;
        }

        // [7]
        final PowerDistributionUnitBE.TierRollCall roll = be.tierRollCall();
        final float lampH = 0.062f;
        final float gap = 0.012f;
        final int tiers = FePriority.values().length;
        final float lampW = (w - gap * (tiers - 1)) / tiers;
        final boolean blink = (System.currentTimeMillis() / 900L) % 2L == 0L;
        for (int i = 0; i < tiers; i++) {
            final float lx = x0 + i * (lampW + gap);
            final int total = roll.total()[i];
            final int served = roll.served()[i];
            final int colour;
            if (total == 0) {
                colour = COL_TROUGH;
            } else if (noInput) {
                colour = COL_RED;
            } else if (served == total) {
                colour = COL_GREEN;
            } else if (served == 0) {
                colour = blink ? COL_AMBER : dim(COL_AMBER, 0.35f);
            } else {
                colour = COL_STEEL;
            }
            quad(vc, mat, lx, y - lampH, lx + lampW, y, colour, light);
            textCentred(poseStack, buffers, TIER_LABELS[i], lx + lampW * 0.5f,
                    y - lampH - 0.042f, total == 0 ? COL_RULE : COL_LABEL, light, 0.0046f);
        }

        // [8]
        final String line = noInput
                ? "NO INPUT"
                : String.format(Locale.ROOT, "%d SERVED   %d SHED", branch.served(), branch.shed());
        final int lineColour = noInput ? COL_RED : branch.shed() > 0 ? COL_AMBER : COL_VALUE;
        text(poseStack, buffers, line, x0, SCREEN_B + pad, lineColour, light, 0.0058f);

        poseStack.popPose();
    }

    /** [9] */
    private static void drawStudWires(PowerDistributionUnitBE be, Direction facing, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight) {
        if (be.wiresOnStuds() <= 0 || be.getLevel() == null) {
            return;
        }
        final VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final Matrix4f mat = poseStack.last().pose();
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        for (int stud = 0; stud < com.createstats.power.PduStuds.COUNT; stud++) {
            final java.util.List<PowerDistributionUnitBE.StudLink> links = be.studLinks(stud);
            if (links.isEmpty()) {
                continue;
            }
            final double[] box = com.createstats.power.PduStuds.boxOf(facing, stud);
            final float sx = (float) ((box[0] + box[2]) * 0.5);
            final float sz = (float) ((box[1] + box[3]) * 0.5);
            final float sy = (float) com.createstats.power.PduStuds.topY();
            for (PowerDistributionUnitBE.StudLink link : links) {
                final net.minecraft.world.phys.Vec3 to = PowerConnectorRenderer.attachPoint(
                        be.getLevel().getBlockState(link.to()));
                PowerConnectorRenderer.drawCable(vc, mat, sx, sy, sz,
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        packedLight, link.tier());
            }
        }
    }

    /** Wires reach as far as wires reach, so the panel must stay drawn while one of them is on screen. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PowerDistributionUnitBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos())
                .inflate(com.createstats.blockentity.PowerConnectorBE.wireRange() + 1);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                             int argb, int light) {
        if (u1 <= u0 || v1 <= v0) {
            return;
        }
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        vertex(vc, mat, u0, v0, r, g, b, a, light);
        vertex(vc, mat, u1, v0, r, g, b, a, light);
        vertex(vc, mat, u1, v1, r, g, b, a, light);
        vertex(vc, mat, u0, v1, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y,
                               int r, int g, int b, int a, int light) {
        vc.addVertex(mat, x, y, 0.0f).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, String s,
                      float x, float y, int argb, int light, float scale) {
        poseStack.pushPose();
        poseStack.translate(x, y + scale * 8.0f, 0.001f);
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(s, 0.0f, 0.0f, argb, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        poseStack.popPose();
    }

    /** Right-aligns text to {@code xRight}, measuring in font pixels and scaling to world units. */
    private void textRight(PoseStack poseStack, MultiBufferSource buffers, String s,
                           float xRight, float y, int argb, int light, float scale) {
        text(poseStack, buffers, s, xRight - font.width(s) * scale, y, argb, light, scale);
    }

    /** Centres text on {@code xCentre}. Used for the tier captions under the lamps. */
    private void textCentred(PoseStack poseStack, MultiBufferSource buffers, String s,
                             float xCentre, float y, int argb, int light, float scale) {
        text(poseStack, buffers, s, xCentre - font.width(s) * scale * 0.5f, y, argb, light, scale);
    }

    private static int dim(int argb, float factor) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Math.round(((argb >> 16) & 0xFF) * factor);
        final int g = Math.round(((argb >> 8) & 0xFF) * factor);
        final int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
