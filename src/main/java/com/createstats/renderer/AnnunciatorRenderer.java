package com.createstats.renderer;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorState;
import com.createstats.alarm.annunciator.AnnunciatorVariant;
import com.createstats.alarm.annunciator.LampPattern;
import com.createstats.alarm.annunciator.RuleStatus;
import com.createstats.alarm.annunciator.ToggleMode;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.block.AnnunciatorBlock;
import com.createstats.blockentity.AnnunciatorBE;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class AnnunciatorRenderer implements BlockEntityRenderer<AnnunciatorBE> {
    private static final ResourceLocation LENS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/beacon_lens.png");
    private static final ResourceLocation METAL =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/beacon_rod.png");
    private static final ResourceLocation PADDLE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/annunciator_ack.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float LIFT = 0.06f;

    private static final int UNLIT_GLASS = 0xFF23272D;

    private final Font font;

    public AnnunciatorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(AnnunciatorBE be) {
        return true;
    }

    @Override
    public void render(AnnunciatorBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!be.getBlockState().hasProperty(AnnunciatorBlock.FACING)) {
            return;
        }
        final Direction facing = be.getBlockState().getValue(AnnunciatorBlock.FACING);
        final long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();

        final AnnunciatorState state = be.getDeviceState();
        final AlarmSeverity severity = be.getSeverity();
        final ToggleMode toggle = be.getToggle();
        final boolean powered = be.feConsumer().isPowered();

        final boolean test = toggle.isTest();
        final LampPattern lamp = test ? LampPattern.STEADY : state.lamp();
        final float intensity = powered ? lamp.intensity(gameTime, partialTick) : 0.0f;
        final int colour = test ? AlarmSeverity.WARNING.colour()
                : severity == AlarmSeverity.NORMAL ? AlarmSeverity.NOTICE.colour() : severity.colour();

        ps.pushPose();

        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-facingDegrees(facing)));
        ps.translate(-0.5, 0.0, -0.5);
        ps.scale(PX, PX, PX);

        this.drawPlinth(be, ps, buffers, packedLight, gameTime, partialTick, powered, toggle, colour, intensity);
        if (be.variant() == AnnunciatorVariant.KLAXON) {
            this.drawKlaxonHead(be, ps, buffers, packedLight, gameTime, partialTick,
                    powered, colour, intensity, test);
        } else {
            this.drawSentryHead(be, ps, buffers, packedLight, gameTime, partialTick,
                    powered, colour, intensity, test);
        }
        ps.popPose();
    }

    private void drawPlinth(AnnunciatorBE be, PoseStack ps, MultiBufferSource buffers, int light,
                            long gameTime, float partialTick, boolean powered, ToggleMode toggle,
                            int colour, float intensity) {
        final float[] ack = AnnunciatorGeometry.ACK_HEAD;

        final float sincePress = ticksSince(be.getLastAckTick(), gameTime, partialTick);
        final float travel = sincePress >= 0.0f && sincePress < 6.0f
                ? AnnunciatorGeometry.ACK_TRAVEL * (1.0f - sincePress / 6.0f) : 0.0f;
        final VertexConsumer paddle = buffers.getBuffer(RenderType.entityCutoutNoCull(PADDLE));
        box(paddle, ps, 0xFFFFFFFF, light,
                ack[0], ack[1], AnnunciatorGeometry.ACK_Z - 0.85f + travel,
                ack[2], ack[3], AnnunciatorGeometry.ACK_Z + travel);

        final VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        ps.pushPose();
        ps.translate(11.7f, 5.6f, AnnunciatorGeometry.FRONT - 0.55f);
        ps.mulPose(Axis.ZP.rotationDegrees(toggle.leverDegrees()));
        box(metal, ps, 0xFFC9D0D8, light, -0.35f, -0.20f, -0.90f, 0.35f, 2.10f, -0.10f);
        ps.popPose();

        final int ledColour = powered ? 0xFF39C46B : 0xFFB03A2E;
        final float ledGlow = powered ? 1.0f : (Math.floorMod(gameTime, 20L) < 10 ? 1.0f : 0.15f);
        final float[] led = AnnunciatorGeometry.LED;
        this.glowPanel(ps, buffers, led[0], led[1], led[2], led[3],
                AnnunciatorGeometry.LED_Z - LIFT, ledColour, ledGlow, light);
    }

    private void drawKlaxonHead(AnnunciatorBE be, PoseStack ps, MultiBufferSource buffers, int light,
                                long gameTime, float partialTick, boolean powered, int colour,
                                float intensity, boolean test) {
        final float[] lensSpan = AnnunciatorGeometry.KLAXON_LENS;

        final boolean spinning = be.getDeviceState() == AnnunciatorState.ALARM || test;
        final double sweep = spinning ? ((gameTime + partialTick) * 0.11) % (Math.PI * 2.0) : 0.0;
        final VertexConsumer glass = Buffers.live(buffers, RenderType.entityTranslucentEmissive(LENS));
        for (int side = 0; side < 4; side++) {
            final double bearing = side * Math.PI * 0.5;
            final float lobe = spinning
                    ? (float) Math.pow(Math.max(0.0, Math.cos(bearing - sweep)), 3.0)
                    : intensity * 0.55f;
            final int argb = tint(colour, powered ? Math.max(lobe, intensity * 0.25f) : 0.0f);
            this.lensFace(glass, ps, side, lensSpan[0], lensSpan[1], argb, light);
        }

        final float sinceStrike = ticksSince(be.getLastStrikeTick(), gameTime, partialTick);
        final float strikeAngle = strikerAngle(sinceStrike);
        final VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        ps.pushPose();
        ps.translate(AnnunciatorGeometry.STRIKER_PIVOT_X, AnnunciatorGeometry.STRIKER_PIVOT_Y,
                AnnunciatorGeometry.FRONT - 1.20f);
        ps.mulPose(Axis.ZP.rotationDegrees(strikeAngle));

        box(metal, ps, 0xFFA9B0B8, light, -0.30f, 0.0f, -0.35f, 0.30f,
                AnnunciatorGeometry.STRIKER_LENGTH, 0.35f);
        box(metal, ps, 0xFFD8DEE5, light, -0.62f, AnnunciatorGeometry.STRIKER_LENGTH - 0.10f, -0.62f,
                0.62f, AnnunciatorGeometry.STRIKER_LENGTH + 1.10f, 0.62f);
        ps.popPose();

        final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(be.getBlockPos(), 0);
        needle.setTarget(be.dialFraction() * NeedlePhysics.SWEEP_RADIANS);
        needle.tick();
        final float[] dial = AnnunciatorGeometry.DIAL;
        final float dialCx = (dial[0] + dial[2]) * 0.5f;
        final float dialCy = dial[1] + (dial[3] - dial[1]) * 0.22f;
        final float dialR = (dial[2] - dial[0]) * 0.40f;

        final float needleDeg = 90.0f - 180.0f
                * (needle.getCurrentAngle() / NeedlePhysics.SWEEP_RADIANS);
        ps.pushPose();
        ps.translate(dialCx, dialCy, AnnunciatorGeometry.FRONT - LIFT - 0.10f);
        ps.mulPose(Axis.ZP.rotationDegrees(needleDeg));
        box(metal, ps, 0xFFE8452F, light, -0.22f, -0.35f, -0.06f, 0.22f, dialR, 0.06f);
        ps.popPose();

        box(metal, ps, 0xFF9AA2AB, light, dialCx - 0.55f, dialCy - 0.55f,
                AnnunciatorGeometry.FRONT - LIFT - 0.20f,
                dialCx + 0.55f, dialCy + 0.55f, AnnunciatorGeometry.FRONT - LIFT - 0.02f);

        this.drawFlapDrum(be, ps, buffers, light, gameTime, partialTick, powered);
    }

    private static float strikerAngle(float sinceStrike) {
        final float rest = AnnunciatorGeometry.STRIKER_REST_DEG;
        final float hit = AnnunciatorGeometry.STRIKER_STRIKE_DEG;
        if (sinceStrike < 0.0f || sinceStrike > AnnunciatorBE.STRIKE_ANIM_TICKS) {
            return rest;
        }
        if (sinceStrike <= 2.0f) {
            return rest + (hit - rest) * (sinceStrike / 2.0f);
        }
        if (sinceStrike <= 3.0f) {
            return hit;
        }
        final float t = (sinceStrike - 3.0f) / (AnnunciatorBE.STRIKE_ANIM_TICKS - 3.0f);

        final float eased = 1.0f - (float) (Math.cos(t * Math.PI * 1.35) * Math.exp(-3.0 * t));
        return hit + (rest - hit) * Math.min(1.15f, eased);
    }

    private void drawFlapDrum(AnnunciatorBE be, PoseStack ps, MultiBufferSource buffers, int light,
                              long gameTime, float partialTick, boolean powered) {
        final float[] drum = AnnunciatorGeometry.DRUM;

        final String tag = !powered ? ""
                : !be.getCulpritLabel().isEmpty()
                        ? be.getCulpritLabel().toUpperCase(java.util.Locale.ROOT)
                        : be.getFaultCount() > 0 ? "NO DATA" : "";
        final float cx = (drum[0] + drum[2]) * 0.5f;
        final float cy = (drum[1] + drum[3]) * 0.5f;
        if (tag.isEmpty()) {
            return;
        }
        ps.pushPose();

        ps.translate(cx, cy - 0.62f, AnnunciatorGeometry.FRONT - LIFT - 0.14f);
        final float scale = 0.11f;
        ps.scale(scale, -scale, scale);
        final String shown = tag.length() > 12 ? tag.substring(0, 12) : tag;
        this.font.drawInBatch(shown, -this.font.width(shown) * 0.5f, 0.0f, 0xFFE8E2D2, false,
                ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private void drawSentryHead(AnnunciatorBE be, PoseStack ps, MultiBufferSource buffers, int light,
                                long gameTime, float partialTick, boolean powered, int colour,
                                float intensity, boolean test) {
        final float[] screen = AnnunciatorGeometry.SCREEN;
        final float[] bar = AnnunciatorGeometry.BAR;
        final float[] tiles = AnnunciatorGeometry.TILES;

        if (!powered) {
            this.glowPanel(ps, buffers, screen[0], screen[1], screen[2], screen[3],
                    AnnunciatorGeometry.FRONT - LIFT, UNLIT_GLASS, 1.0f, light);
            return;
        }

        final AnnunciatorState state = be.getDeviceState();
        final boolean idle = state == AnnunciatorState.NORMAL && !test;

        final boolean blind = idle && be.getFaultCount() > 0;
        this.glowPanel(ps, buffers, screen[0], screen[1], screen[2], screen[3],
                AnnunciatorGeometry.FRONT - LIFT,
                blind ? 0xFF161326 : idle ? 0xFF0E1A12 : dim(colour, 0.16f), 1.0f, light);

        if (idle) {
            final float sweep = (float) ((gameTime + partialTick) % 80L) / 80.0f;
            final float y = screen[1] + (screen[3] - screen[1]) * sweep;
            this.glowPanel(ps, buffers, screen[0] + 0.3f, y, screen[2] - 0.3f, y + 0.18f,
                    AnnunciatorGeometry.FRONT - LIFT - 0.06f, blind ? 0xFF7A6BC4 : 0xFF2FB36D,
                    0.55f, light);
            final float cx = (screen[0] + screen[2]) * 0.5f;
            if (blind) {
                this.screenText(ps, buffers, "NO DATA", cx, screen[3] - 1.35f, 0xFF9C8FE0);
                this.screenText(ps, buffers, be.getFaultCount() + " UNREADABLE", cx,
                        screen[3] - 2.90f, 0xFF7A6BC4);
            } else {
                this.screenText(ps, buffers, "STANDBY", cx, (screen[1] + screen[3]) * 0.5f,
                        0xFF2C6B45);
            }
        } else {
            final String label = test ? "LAMP TEST" : be.getCulpritLabel();
            final String value = test ? "ALL OK" : be.getCulpritValue();
            final float cx = (screen[0] + screen[2]) * 0.5f;
            this.screenText(ps, buffers, label, cx, screen[3] - 1.35f, 0xFFF2F6FA);
            this.screenText(ps, buffers, value, cx, screen[3] - 2.90f, colour | 0xFF000000);
            this.screenText(ps, buffers, stateWord(state), cx, screen[1] + 0.75f, dimText(colour));
        }

        final AlarmSeverity severity = test ? AlarmSeverity.CRITICAL : be.getSeverity();
        final float fill = switch (severity) {
            case NORMAL -> 0.06f;
            case NOTICE -> 0.34f;
            case WARNING -> 0.68f;
            case CRITICAL -> 1.0f;
        };
        final float barGlow = severity == AlarmSeverity.NORMAL ? 0.35f : Math.max(0.45f, intensity);
        this.glowPanel(ps, buffers, bar[0] + 0.2f, bar[1] + 0.2f,
                bar[0] + 0.2f + (bar[2] - bar[0] - 0.4f) * fill, bar[3] - 0.2f,
                AnnunciatorGeometry.FRONT - LIFT - 0.04f,
                severity == AlarmSeverity.NORMAL ? 0xFF2FB36D : colour, barGlow, light);

        this.drawRuleTiles(be, ps, buffers, light, gameTime, partialTick, tiles, test);

        // the cone is the only moving part on the sentry

        final float sinceStrike = ticksSince(be.getLastStrikeTick(), gameTime, partialTick);
        final float push = sinceStrike >= 0.0f && sinceStrike < 6.0f
                ? (float) Math.sin(sinceStrike / 6.0f * Math.PI) * 0.55f : 0.0f;
        if (push > 0.001f) {
            final float[] speaker = AnnunciatorGeometry.SPEAKER;
            final VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
            box(metal, ps, 0xFF1C2024, light, 5.6f, speaker[0] + 1.1f,
                    AnnunciatorGeometry.FRONT - 0.55f - push,
                    10.4f, speaker[1] - 1.1f, AnnunciatorGeometry.FRONT - 0.35f - push);
        }
    }

    private void drawRuleTiles(AnnunciatorBE be, PoseStack ps, MultiBufferSource buffers, int light,
                               long gameTime, float partialTick, float[] tiles, boolean test) {
        final List<WatchRule> rules = be.getRules();
        final float cellW = (tiles[2] - tiles[0]) / AnnunciatorGeometry.TILE_COLUMNS;
        final float cellH = (tiles[3] - tiles[1]) / AnnunciatorGeometry.TILE_ROWS;
        for (int i = 0; i < WatchRule.MAX_RULES; i++) {
            final int column = i % AnnunciatorGeometry.TILE_COLUMNS;

            final int row = AnnunciatorGeometry.TILE_ROWS - 1 - i / AnnunciatorGeometry.TILE_COLUMNS;
            final float x0 = tiles[0] + column * cellW + 0.22f;
            final float y0 = tiles[1] + row * cellH + 0.20f;

            int argb = 0xFF1A1E22;
            float glow = 1.0f;
            if (test) {
                argb = AlarmSeverity.WARNING.colour() | 0xFF000000;
            } else if (i < rules.size()) {
                final WatchRule rule = rules.get(i);
                final RuleStatus status = be.statusOf(rule);
                if (!rule.isLive()) {
                    argb = 0xFF23282D;
                } else if (!status.available()) {
                    argb = 0xFF7A6BC4;
                    glow = Math.floorMod(gameTime, 30L) < 15 ? 0.9f : 0.25f;
                } else {
                    final AlarmSeverity effective = status.effectiveSeverity();
                    argb = effective == AlarmSeverity.NORMAL ? 0xFF1F6B45 : effective.colour() | 0xFF000000;
                    glow = status.state() == AnnunciatorState.ALARM
                            ? status.state().lamp().intensity(gameTime, partialTick)
                            : status.isOutstanding() ? 1.0f : 0.5f;
                }
            }
            this.glowPanel(ps, buffers, x0, y0, x0 + cellW - 0.44f, y0 + cellH - 0.40f,
                    AnnunciatorGeometry.FRONT - LIFT - 0.04f, argb, glow, light);
        }
    }

    private static String stateWord(AnnunciatorState state) {
        return switch (state) {
            case NORMAL -> "NORMAL";
            case ALARM -> "UNACKNOWLEDGED";
            case ACKNOWLEDGED -> "ACKNOWLEDGED";
            case RINGBACK -> "CLEARED, NOT RESET";
        };
    }

    private void screenText(PoseStack ps, MultiBufferSource buffers, String text, float cx, float y,
                            int argb) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ps.pushPose();
        ps.translate(cx, y, AnnunciatorGeometry.FRONT - LIFT - 0.12f);
        final float scale = 0.10f;
        ps.scale(scale, -scale, scale);
        final String shown = text.length() > 22 ? text.substring(0, 22) : text;
        this.font.drawInBatch(shown, -this.font.width(shown) * 0.5f, 0.0f, argb, false,
                ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private void glowPanel(PoseStack ps, MultiBufferSource buffers, float x0, float y0, float x1,
                           float y1, float z, int argb, float glow, int light) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(LENS));
        final int tinted = tint(argb, Math.max(0.0f, Math.min(1.0f, glow)));

        quad(vc, ps, tinted, LightTexture.FULL_BRIGHT, 0.0f, 0.0f, -1.0f,
                x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
    }

    private void lensFace(VertexConsumer vc, PoseStack ps, int side, float y0, float y1, int argb,
                          int light) {
        final float lo = 4.10f;
        final float hi = 11.90f;
        switch (side) {
            case 0 -> quad(vc, ps, argb, light, 0.0f, 0.0f, -1.0f,
                    lo, y0, lo, hi, y0, lo, hi, y1, lo, lo, y1, lo);
            case 1 -> quad(vc, ps, argb, light, 1.0f, 0.0f, 0.0f,
                    hi, y0, lo, hi, y0, hi, hi, y1, hi, hi, y1, lo);
            case 2 -> quad(vc, ps, argb, light, 0.0f, 0.0f, 1.0f,
                    hi, y0, hi, lo, y0, hi, lo, y1, hi, hi, y1, hi);
            default -> quad(vc, ps, argb, light, -1.0f, 0.0f, 0.0f,
                    lo, y0, hi, lo, y0, lo, lo, y1, lo, lo, y1, hi);
        }
    }

    private static void box(VertexConsumer vc, PoseStack ps, int argb, int light,
                            float ax, float ay, float az, float bx, float by, float bz) {
        quad(vc, ps, argb, light, 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        quad(vc, ps, argb, light, 0, 0, -1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        quad(vc, ps, argb, light, -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        quad(vc, ps, argb, light, 1, 0, 0, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        quad(vc, ps, argb, light, 0, 1, 0, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        quad(vc, ps, argb, light, 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
    }

    private static void quad(VertexConsumer vc, PoseStack ps, int argb, int light,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{0.0f, 1.0f}, {1.0f, 1.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private static int tint(int argb, float factor) {
        final int r = (int) (((argb >> 16) & 0xFF) * factor);
        final int g = (int) (((argb >> 8) & 0xFF) * factor);
        final int b = (int) ((argb & 0xFF) * factor);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int dim(int argb, float factor) {
        return tint(argb, factor);
    }

    private static int dimText(int colour) {
        return tint(colour, 0.62f);
    }

    private static float ticksSince(long eventTick, long gameTime, float partialTick) {
        if (eventTick == Long.MIN_VALUE || eventTick > gameTime) {
            return -1.0f;
        }
        final long delta = gameTime - eventTick;
        return delta > 1000L ? -1.0f : delta + partialTick;
    }

    private static float facingDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0f;
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }
}
