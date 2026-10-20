package com.createstats.renderer;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.HeaderStatus;
import com.createstats.battle.core.Projection;
import com.createstats.block.BattleScreenDisplayBlock;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.client.FePowerVisuals;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// beyond 24m only the colour, tiny text is noise
@OnlyIn(Dist.CLIENT)
public class BattleScreenDisplayRenderer implements BlockEntityRenderer<BattleScreenDisplayBE> {
    private static final float W = 128f;
    private static final float H = W * 8f / 14f;
    private static final float LEAN = 22.5f;
    private static final double NEAR = 24.0;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final float LAYER = 0.2f;

    private static final int BG = 0xFF101418;
    private static final int NAME = 0xFFE0A000;
    private static final int TEXT = 0xFFE8ECEA;
    private static final int DIM = 0xFF7C8682;
    private static final int HIT_BOX = 0xFFD03A26;
    private static final int[] CELL = {0x00000000, 0xFF5F6A66, 0xFFA9B4AE, 0xFFE0402C};

    private static final float[][] LENSES = {{12, 14}, {9, 11}, {6, 8}, {3, 5}, {1, 2}};
    private static final int FACE_LAMPS = 8;

    private final Font font;
    private final Map<BlockPos, FaceTexture> textures = new HashMap<>();
    private int sweep;

    public BattleScreenDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }

    @Override
    public void render(BattleScreenDisplayBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (++sweep % 200 == 0) releaseGone();
        BattleReport r = be.report();
        if (!r.powered() || FePowerVisuals.isDead(be.feConsumer())) return;
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(BattleScreenDisplayBlock.FACING) ? state.getValue(BattleScreenDisplayBlock.FACING) : Direction.NORTH;
        long clock = be.getLevel() == null ? r.now() : be.getLevel().getGameTime();
        HeaderStatus.Status status = r.status(clock);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        frontLamps(r, pose.last().pose(), buffers, FePowerVisuals.screenBrightness(be.getBlockPos(), be.feConsumer()));
        double c = Math.cos(Math.toRadians(LEAN));
        double s = Math.sin(Math.toRadians(LEAN));
        pose.translate(15.0 / 16.0, (8.0 + 8.0 * c) / 16.0, (4.0 + 8.0 * s) / 16.0);
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        pose.mulPose(Axis.XP.rotationDegrees(-LEAN));
        pose.translate(0.0, 0.0, 0.003);
        float unit = (14f / 16f) / W;
        pose.scale(unit, -unit, unit);
        Matrix4f m = pose.last().pose();

        float bright = FePowerVisuals.screenBrightness(be.getBlockPos(), be.feConsumer());
        if (!near(be)) {
            fill(buffers, m, 0, 0, W, H, 0f, dim(lampColour(status.kind().lamp()), 0.55f * bright));
            pose.popPose();
            return;
        }
        fill(buffers, m, 0, 0, W, H, 0f, dim(BG, 1f));

        String name = r.shipName().isEmpty() ? (r.ground() ? "FORT" : "SHIP") : r.shipName().toUpperCase(Locale.ROOT);
        String integrity = r.hasReference() ? (r.integrityPermille() / 10) + "%" : "--";
        int iw = font.width(integrity);
        boolean hit = r.lastHitAt() >= 0 && clock - r.lastHitAt() < 200;
        boolean hitOn = hit && (clock / 10) % 2 == 0;
        int hitW = font.width("HIT") + 6;
        float nameMax = W - 6 - iw - (hit ? hitW + 6 : 0);
        text(buffers, m, fit(name, nameMax), 3, 2, dim(NAME, bright));
        text(buffers, m, integrity, W - 3 - iw, 2, dim(TEXT, bright));
        if (hitOn) {
            float hx = W - 3 - iw - 5 - hitW;
            fill(buffers, m, hx, 1, hx + hitW, 11, LAYER, dim(HIT_BOX, bright));
            text(buffers, m, "HIT", hx + 3, 2, 0xFFFFFFFF);
        }

        byte[] face = r.face();
        if (face.length > 0) {
            FaceTexture tex = textures.computeIfAbsent(be.getBlockPos().immutable(), p -> new FaceTexture(p));
            tex.update(face, bright);
            VertexConsumer vc = buffers.getBuffer(RenderType.text(tex.id));
            float y0 = 13;
            float y1 = y0 + (W * Projection.FACE_H / Projection.FACE_W);
            quad(vc, m, 0, y0, W, y1, LAYER);
        }
        String line = statusLine(r, status);
        float lampsW = faceLamps(r, buffers, m, bright);
        text(buffers, m, fit(line, W - 6 - lampsW), 3, H - 10, dim(lampColour(status.kind().lamp()), bright));
        pose.popPose();
    }

    private float faceLamps(BattleReport r, MultiBufferSource buffers, Matrix4f m, float bright) {
        int n = r.gunCount();
        if (n <= 0) return 0;
        if (n > FACE_LAMPS) {
            String t = r.gunsReady() + " of " + n + " ready";
            float tw = font.width(t);
            text(buffers, m, t, W - 3 - tw, H - 10, dim(TEXT, bright));
            return tw + 6;
        }
        float size = 5;
        float gap = 2;
        float x = W - 3 - n * (size + gap) + gap;
        for (int i = 0; i < n && i < r.guns().size(); i++) {
            int colour = lampColour(r.guns().get(i).gunState().lamp());
            fill(buffers, m, x, H - 9, x + size, H - 9 + size, LAYER, dim(colour, bright));
            x += size + gap;
        }
        return n * (size + gap) + 4;
    }

    private static void frontLamps(BattleReport r, Matrix4f m, MultiBufferSource buffers, float bright) {
        if (r.guns().isEmpty()) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        float z = 1f / 16f - 0.002f;
        for (int i = 0; i < 5; i++) {
            int colour;
            if (i < 4) {
                if (i >= r.guns().size()) continue;
                HeaderStatus.Lamp lamp = r.guns().get(i).gunState().lamp();
                if (lamp == HeaderStatus.Lamp.GREY) continue;
                colour = lampColour(lamp);
            } else {
                if (r.gunCount() <= 4) continue;
                boolean allReady = true;
                boolean anyRed = false;
                for (int k = 4; k < r.guns().size(); k++) {
                    HeaderStatus.Lamp lamp = r.guns().get(k).gunState().lamp();
                    allReady &= lamp == HeaderStatus.Lamp.GREEN;
                    anyRed |= lamp == HeaderStatus.Lamp.RED;
                }
                colour = lampColour(anyRed ? HeaderStatus.Lamp.RED : allReady ? HeaderStatus.Lamp.GREEN : HeaderStatus.Lamp.AMBER);
            }
            colour = dim(colour, bright);
            float x0 = (LENSES[i][0] + 0.25f) / 16f;
            float x1 = (LENSES[i][1] - 0.25f) / 16f;
            float y0 = 5.25f / 16f;
            float y1 = 6.75f / 16f;
            int a = (colour >>> 24) & 0xFF;
            int cr = (colour >> 16) & 0xFF;
            int cg = (colour >> 8) & 0xFF;
            int cb = colour & 0xFF;
            vc.addVertex(m, x0, y0, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x0, y1, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x1, y1, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x1, y0, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x1, y0, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x1, y1, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x0, y1, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
            vc.addVertex(m, x0, y0, z).setColor(cr, cg, cb, a).setLight(FULL_BRIGHT);
        }
    }

    private static String statusLine(BattleReport r, HeaderStatus.Status s) {
        return switch (s.kind()) {
            case NO_POWER -> "NO POWER";
            case FIRE -> "FIRE ABOARD";
            case UNDER_FIRE -> "UNDER FIRE " + s.number() + "s";
            case WEAPONS_FREE -> "WEAPONS FREE";
            case SURVEYING -> "SURVEYING " + s.number() + "%";
            case ALL_CLEAR -> r.placeCount() > 0 ? r.placeCount() + " TO REPAIR" : "ALL CLEAR";
        };
    }

    private static boolean near(BattleScreenDisplayBE be) {
        Vec3 world = SableBridge.worldPointOf(be, Vec3.atCenterOf(be.getBlockPos()));
        if (world == null) return true;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return world.distanceToSqr(cam) <= NEAR * NEAR;
    }

    private static int lampColour(HeaderStatus.Lamp lamp) {
        return switch (lamp) {
            case RED -> 0xFFE0402C;
            case AMBER -> 0xFFE0A000;
            case GREEN -> 0xFF4CC45A;
            case GREY -> 0xFF8A9490;
        };
    }

    private static int dim(int argb, float f) {
        if (f >= 0.999f) return argb;
        int r = Math.round(((argb >> 16) & 0xFF) * f);
        int g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private String fit(String text, float max) {
        if (font.width(text) <= max) return text;
        String t = text;
        while (!t.isEmpty() && font.width(t + ".") > max) t = t.substring(0, t.length() - 1);
        return t + ".";
    }

    private void text(MultiBufferSource buffers, Matrix4f m, String text, float x, float y, int colour) {
        font.drawInBatch(text, x, y, colour, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
    }

    private static void fill(MultiBufferSource buffers, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        vc.addVertex(m, x0, y0, z).setColor(r, g, b, a).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, z).setColor(r, g, b, a).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, z).setColor(r, g, b, a).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y0, z).setColor(r, g, b, a).setLight(FULL_BRIGHT);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z) {
        vc.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(FULL_BRIGHT);
    }

    private void releaseGone() {
        var level = Minecraft.getInstance().level;
        for (Iterator<Map.Entry<BlockPos, FaceTexture>> it = textures.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, FaceTexture> e = it.next();
            if (level == null || !(SableBridge.getBlockEntityOnContraption(level, e.getKey()) instanceof BattleScreenDisplayBE)) {
                e.getValue().close();
                it.remove();
            }
        }
    }

    private static final class FaceTexture {
        final ResourceLocation id;
        final DynamicTexture texture;
        byte[] last = new byte[0];
        float lastBright = -1f;

        FaceTexture(BlockPos pos) {
            texture = new DynamicTexture(Projection.FACE_W, Projection.FACE_H, true);
            id = ResourceLocation.fromNamespaceAndPath("create_stats", "battle_face/" + Long.toHexString(pos.asLong()).toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void update(byte[] packed, float bright) {
            if (Arrays.equals(packed, last) && Math.abs(bright - lastBright) < 0.01f) return;
            last = packed.clone();
            lastBright = bright;
            byte[] cells = Projection.unpack2(packed, Projection.FACE_W * Projection.FACE_H);
            NativeImage img = texture.getPixels();
            if (img == null) return;
            for (int y = 0; y < Projection.FACE_H; y++) {
                for (int x = 0; x < Projection.FACE_W; x++) {
                    int argb = dim(CELL[cells[y * Projection.FACE_W + x] & 3], bright);

                    int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                    img.setPixelRGBA(x, y, abgr);
                }
            }
            texture.upload();
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}
