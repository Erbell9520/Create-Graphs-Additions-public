package com.createstats.renderer;

import com.createstats.block.WeatherPanelBlock;
import com.createstats.blockentity.WeatherPanelBE;
import com.createstats.client.AnimationClock;
import com.createstats.gui.weather.WeatherAnim;
import com.createstats.gui.weather.WeatherTheme;
import com.createstats.weather.Condition;
import com.createstats.weather.Forecast;
import com.createstats.weather.ForecastEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

// vertex colour quads so the crossfade costs nothing
@OnlyIn(Dist.CLIENT)
public class WeatherPanelRenderer implements BlockEntityRenderer<WeatherPanelBE> {
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/weather_white.png");
    private static final ResourceLocation GLYPHS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/weather_glyphs.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float Z0 = 1.6f * PX + (1.0f / 512.0f);
    private static final float Z_STEP = 1.0f / 2048.0f;

    private static final float GLASS_X0 = 2.0f;
    private static final float GLASS_X1 = 14.0f;
    private static final float GLASS_Y0 = 5.0f;
    private static final float GLASS_Y1 = 11.0f;
    private static final float FACE_PX = 32.0f;

    private static final int GLYPH_COUNT = 6;

    private static final int MAX_STREAKS = 26;

    public WeatherPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WeatherPanelBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!(be.getBlockState().getBlock() instanceof WeatherPanelBlock)) {
            return;
        }

        if (!be.isAwake()) {
            return;
        }
        final var level = be.getLevel();
        if (level == null) {
            return;
        }

        final WeatherAnim anim = WeatherFaceTextureManager.animFor(be);
        feed(be, anim, partialTick);

        final float nightness = nightness(be, partialTick);
        final WeatherTheme.Palette palette = anim.palette(nightness);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (be.getBlockState().getValue(WeatherPanelBlock.FACING)) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);
        final Matrix4f mat = ps.last().pose();

        final int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 13),
                LightTexture.sky(packedLight));

        final VertexConsumer white = buffer.getBuffer(RenderType.entityTranslucent(WHITE));
        sky(white, mat, palette, light);
        precipitation(white, mat, be, anim, palette, light, partialTick);
        timeline(white, mat, be, palette, light, partialTick);

        final ResourceLocation face = WeatherFaceTextureManager.getOrUpdateTexture(be);
        if (face != null) {
            final VertexConsumer ink = buffer.getBuffer(RenderType.entityTranslucent(face));
            quad(ink, mat, GLASS_X0, GLASS_Y0, GLASS_X1, GLASS_Y1, 0f, 0f, 1f, 1f,
                    palette.ink(), palette.ink(), light, Z0 + 3 * Z_STEP);
        }

        glyph(buffer, mat, anim, palette, light);
        ps.popPose();
    }

    private static void feed(WeatherPanelBE be, WeatherAnim anim, float partialTick) {
        final var level = be.getLevel();
        final float rain = level == null ? 0f : level.getRainLevel(partialTick);
        final int next = be.ticksToNextChange();
        final boolean building = be.condition() == Condition.CLEAR && next >= 0;
        final float approaching = building ? 1f - Math.min(1f, next / 8000f) : 0f;
        anim.sync(be.condition(), WeatherFaceTextureManager.displayTemperature(be), be.pressure(), be.humidity(),
                Math.max(rain, approaching), rain, be.forecast());
        anim.updateTo(level == null ? 0.0 : AnimationClock.time(level, partialTick));
    }

    private static float nightness(WeatherPanelBE be, float partialTick) {
        final var level = be.getLevel();
        if (level == null) {
            return 0f;
        }

        final float sun = (float) Math.cos(level.getTimeOfDay(partialTick) * Math.PI * 2.0);
        return Math.max(0f, Math.min(1f, 0.5f - sun * 1.6f));
    }

    private static void sky(VertexConsumer vc, Matrix4f mat, WeatherTheme.Palette palette, int light) {
        quad(vc, mat, GLASS_X0, GLASS_Y0, GLASS_X1, GLASS_Y1, 0f, 0f, 1f, 1f,
                palette.skyTop(), palette.skyBottom(), light, Z0);
    }

    private static void timeline(VertexConsumer vc, Matrix4f mat, WeatherPanelBE be,
                                 WeatherTheme.Palette palette, int light, float partialTick) {
        final Forecast forecast = be.forecast();
        if (forecast.bands().isEmpty()) {
            return;
        }
        final float x0 = tx(WeatherFaceTextureManager.GLYPH_X);
        final float x1 = tx(372f);
        final float y1 = ty(WeatherFaceTextureManager.BAR_Y);
        final float y0 = ty(WeatherFaceTextureManager.BAR_Y + WeatherFaceTextureManager.BAR_H);

        quad(vc, mat, x0, y0, x1, y1, 0f, 0f, 1f, 1f,
                fade(palette.ink(), 0.14f), fade(palette.ink(), 0.14f), light, Z0 + 2 * Z_STEP);

        final float elapsed = elapsedSinceSync(be, partialTick);
        for (Forecast.Band band : forecast.bands()) {
            final float a = (band.startTick() - elapsed) / ForecastEngine.STRIP_TICKS;
            final float b = (band.endTick() - elapsed) / ForecastEngine.STRIP_TICKS;
            final float ca = Math.max(0f, Math.min(1f, a));
            final float cb = Math.max(0f, Math.min(1f, b));
            if (cb - ca <= 0.001f) {
                continue;
            }
            final int colour = bandColour(band.condition(), palette);

            final int shown = band.certain() ? colour : fade(colour, 0.35f);
            quad(vc, mat, x0 + (x1 - x0) * ca, y0, x0 + (x1 - x0) * cb, y1, 0f, 0f, 1f, 1f,
                    shown, shown, light, Z0 + 2 * Z_STEP + Z_STEP * 0.25f);
        }
    }

    private static int bandColour(Condition condition, WeatherTheme.Palette palette) {
        return condition == Condition.CLEAR ? fade(palette.ink(), 0.30f) : palette.accent();
    }

    private static void precipitation(VertexConsumer vc, Matrix4f mat, WeatherPanelBE be,
                                      WeatherAnim anim, WeatherTheme.Palette palette,
                                      int light, float partialTick) {
        final Condition condition = be.condition();
        if (condition != Condition.RAIN && condition != Condition.STORM && condition != Condition.SNOW) {
            return;
        }
        final int count = Math.round(MAX_STREAKS * anim.rainLevel());
        if (count <= 0) {
            return;
        }
        final boolean snow = condition == Condition.SNOW;
        final var level = be.getLevel();
        final float time = level == null ? 0f : AnimationClock.wrapped(level, partialTick);
        final float speed = snow ? 0.9f : 4.2f;
        final float length = snow ? 3.0f : 13.0f;
        final int colour = fade(snow ? 0xFFFFFFFF : palette.ink(), snow ? 0.85f : 0.30f);

        for (int i = 0; i < count; i++) {
            final float seed = (i * 37.13f) % 1.0f;
            final float drift = snow ? (float) Math.sin(time * 0.03f + i) * 6.0f : 0f;
            final float x = 8f + ((seed * 368f) + drift + 368f) % 368f;
            final float y = ((time * speed) + seed * 192f) % 192f;
            final float w = snow ? 2.4f : 1.6f;
            quad(vc, mat, tx(x), ty(y + length), tx(x + w), ty(y), 0f, 0f, 1f, 1f,
                    colour, colour, light, Z0 + Z_STEP);
        }
    }

    private static void glyph(MultiBufferSource buffer, Matrix4f mat, WeatherAnim anim,
                              WeatherTheme.Palette palette, int light) {
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(GLYPHS));
        final float mix = anim.glyphMix();
        if (mix < 0.999f) {
            drawGlyph(vc, mat, anim.fromCondition(), palette.accent(), 1f - mix, 0f, light);
        }
        drawGlyph(vc, mat, anim.toCondition(), palette.accent(), mix, (1f - mix) * 4f, light);
    }

    private static void drawGlyph(VertexConsumer vc, Matrix4f mat, Condition condition,
                                  int accent, float alpha, float dropPixels, int light) {
        if (alpha <= 0.004f) {
            return;
        }
        final int index = Math.min(GLYPH_COUNT - 1, Math.max(0, condition.ordinal()));
        final float size = WeatherFaceTextureManager.GLYPH_SIZE;
        final float px = WeatherFaceTextureManager.GLYPH_X;
        final float py = WeatherFaceTextureManager.GLYPH_Y + dropPixels;
        final int colour = fade(accent, alpha);
        quad(vc, mat, tx(px), ty(py + size), tx(px + size), ty(py),
                index / (float) GLYPH_COUNT, 0f, (index + 1) / (float) GLYPH_COUNT, 1f,
                colour, colour, light, Z0 + 4 * Z_STEP);
    }

    private static float tx(float facePx) {
        return GLASS_X0 + facePx / FACE_PX;
    }

    private static float ty(float facePy) {
        return GLASS_Y1 - facePy / FACE_PX;
    }

    private static float elapsedSinceSync(WeatherPanelBE be, float partialTick) {
        final var level = be.getLevel();
        if (level == null || be.syncGameTime() == 0L) {
            return 0f;
        }
        return Math.max(0f, level.getGameTime() - be.syncGameTime() + partialTick);
    }

    private static int fade(int argb, float factor) {
        final int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1,
                             int topArgb, int bottomArgb, int light, float z) {
        vertex(vc, mat, x0 * PX, y1 * PX, u0, v0, topArgb, light, z);
        vertex(vc, mat, x0 * PX, y0 * PX, u0, v1, bottomArgb, light, z);
        vertex(vc, mat, x1 * PX, y0 * PX, u1, v1, bottomArgb, light, z);
        vertex(vc, mat, x1 * PX, y1 * PX, u1, v0, topArgb, light, z);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float u, float v,
                               int argb, int light, float z) {
        vc.addVertex(mat, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(WeatherPanelBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(32);
    }
}
