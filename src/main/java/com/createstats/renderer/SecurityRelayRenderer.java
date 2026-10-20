package com.createstats.renderer;

import com.createstats.block.SecurityRelayBlock;
import com.createstats.blockentity.SecurityRelayBE;
import com.createstats.security.Truth;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayContact;
import com.createstats.security.relay.RelayFace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public class SecurityRelayRenderer implements BlockEntityRenderer<SecurityRelayBE> {
    private static final float PX = 1.0f / 16.0f;

    static final double PULL_TICKS = 3.0;

    static final double RELEASE_TICKS = 4.0;
    static final double REBOUND_TICKS = 3.0;
    static final double PULL_REBOUND = 0.14;
    static final double RELEASE_REBOUND = 0.12;

    static final double FALL_TICKS = 5.0;
    static final double SWING_TICKS = 6.0;
    static final double SWING_DEGREES = 22.0;
    static final double RAISE_TICKS = 2.0;

    static final double PRESS_IN = 1.0;
    static final double PRESS_HOLD = 4.5;
    static final double PRESS_OUT = 6.0;

    static final double BLINK_PERIOD = 40.0;
    static final double BREATH_PERIOD = 60.0;

    private static final int GREEN = 0x52E86A;
    private static final int AMBER = 0xFFB428;
    private static final int RED = 0xFF402C;

    private static final int OUT_RED = 0xFF2C1E;

    private static final int LENS_DARK = 0x303338;

    private static final float CARD_LIT = 0.95f;
    private static final float CARD_DIM = 0.5f;

    private static final float PULSE_FLOOR = 0.4f;

    private static final int STRIDE = 24;

    public SecurityRelayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecurityRelayBE relay, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int light, int overlay) {
        final Level level = relay.getLevel();
        final BlockState state = relay.getBlockState();
        if (level == null || !state.hasProperty(SecurityRelayBlock.FACING)) {
            return;
        }
        final int turn = turn(state.getValue(SecurityRelayBlock.FACING));
        final double now = level.getGameTime() + (double) partialTick;
        final boolean powered = relay.powered();
        final boolean on = relay.output() > 0;
        final float plate = RelayFace.PLATE_DEPTH;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        if (turn != 0) {
            ps.mulPose(Axis.YP.rotationDegrees(turn));
        }
        ps.translate(-0.5, -0.5, -0.5);
        final Pen pen = new Pen(buffers.getBuffer(RenderType.cutout()), ps.last(), sprites(), turn, light);

        pen.plain().turn(PIVOT_Y, PIVOT_Z, PULL_DEGREES * pull(on, since(now, relay.outputChangedAt())))
                .at(0.0f, 0.0f, 0.0f).draw(ARMATURE);

        pen.plain().turn(0.0f, 0.0f, 0.0f).at(16.0f - RelayFace.BUTTON_U, RelayFace.BUTTON_V,
                plate + BUTTON_TRAVEL * press(since(now, relay.resetPressedAt()))).draw(BUTTON);

        pen.plain().turn(0.0f, 0.0f, flag(relay.pending(), since(now, relay.pendingChangedAt())))
                .at(16.0f - RelayFace.FLAG_U, RelayFace.FLAG_V - HINGE_DROP, plate + HINGE_IN).draw(FLAG);

        if (powered) {
            final RelayContact contact = relay.contact();
            final float glow = contactGlow(contact, now);
            if (glow > 0.02f) {
                pen.lit(mix(LENS_DARK, contactColour(contact), glow)).turn(0.0f, 0.0f, 0.0f)
                        .at(16.0f - RelayFace.CONTACT_LAMP_U, RelayFace.LAMP_V, plate).draw(LENS_LIT);
            }
            if (on) {
                pen.lit(mix(LENS_DARK, OUT_RED, outputLevel(relay, partialTick))).turn(0.0f, 0.0f, 0.0f)
                        .at(16.0f - RelayFace.OUTPUT_LAMP_U, RelayFace.LAMP_V, plate).draw(LENS_LIT);
            }
        }

        if (on) {
            pen.lit(OUT_RED).turn(0.0f, 0.0f, 0.0f).at(0.0f, 0.0f, 0.0f).draw(POST_GLOW);
        }

        drawCard(pen, relay, powered, now);
        ps.popPose();
    }

    private static void drawCard(Pen pen, SecurityRelayBE relay, boolean powered, double now) {
        final float z = RelayFace.PLATE_DEPTH + CARD_INSET;
        final int lit = grey(CARD_LIT);
        final int dim = grey(CARD_DIM);
        if (!powered) {
            pen.plain().tint(dim);
        } else if (backlit(relay.reading(), now)) {
            pen.lit(lit);
        } else {
            pen.plain();
        }
        final int card = 4 * cardIndex(relay.condition());
        pen.northFace(16.0f - RelayFace.WINDOW_U1, RelayFace.WINDOW_V0, 16.0f - RelayFace.WINDOW_U0,
                RelayFace.WINDOW_V1, z, SPRITE_CARDS, CARD_UV[card], CARD_UV[card + 1], CARD_UV[card + 2],
                CARD_UV[card + 3]);
        final RelayBehavior behavior = relay.config().behavior();
        if (behavior == null) {
            return;
        }
        if (powered) {
            pen.lit(lit);
        } else {
            pen.plain().tint(dim);
        }
        final int letter = 4 * behavior.ordinal();
        final float cu = 16.0f - (RelayFace.CONTACT_LAMP_U + RelayFace.OUTPUT_LAMP_U) / 2.0f;
        pen.northFace(cu - MARK_HALF_U, RelayFace.LAMP_V - MARK_HALF_V, cu + MARK_HALF_U,
                RelayFace.LAMP_V + MARK_HALF_V, z, SPRITE_CARDS, LETTER_UV[letter], LETTER_UV[letter + 1],
                LETTER_UV[letter + 2], LETTER_UV[letter + 3]);
    }

    private static int grey(float level) {
        final int g = Math.round(255.0f * level);
        return (g << 16) | (g << 8) | g;
    }

    static boolean backlit(Truth reading, double now) {
        if (reading == Truth.TRUE) {
            return true;
        }
        return reading == Truth.UNKNOWN && now % BLINK_PERIOD < BLINK_PERIOD / 2.0;
    }

    static float outputLevel(SecurityRelayBE relay, float partialTick) {
        final int length = relay.config().pulseTicks();
        if (relay.config().behavior() != RelayBehavior.PULSE || relay.pulseLeft() <= 0 || length <= 0) {
            return 1.0f;
        }
        final float share = Math.max(0.0f, Math.min(1.0f, (relay.pulseLeft() - partialTick) / length));
        return PULSE_FLOOR + (1.0f - PULSE_FLOOR) * share;
    }

    static int cardIndex(RelayCondition condition) {
        if (condition != null) {
            for (int i = 0; i < CARD_KEYS.length - 1; i++) {
                if (CARD_KEYS[i].equals(condition.langKey())) {
                    return i;
                }
            }
        }
        return CARD_KEYS.length - 1;
    }

    static double since(double now, long at) {
        if (at == Long.MIN_VALUE) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, now - (double) at);
    }

    static float pull(boolean on, double s) {
        if (on) {
            if (s < PULL_TICKS) {
                final double k = s / PULL_TICKS;
                return (float) (k * k);
            }
            if (s < PULL_TICKS + REBOUND_TICKS) {
                return (float) (1.0 - PULL_REBOUND * Math.sin(Math.PI * (s - PULL_TICKS) / REBOUND_TICKS));
            }
            return 1.0f;
        }
        if (s < RELEASE_TICKS) {
            return (float) Math.cos(0.5 * Math.PI * s / RELEASE_TICKS);
        }
        if (s < RELEASE_TICKS + REBOUND_TICKS) {
            return (float) (RELEASE_REBOUND * Math.sin(Math.PI * (s - RELEASE_TICKS) / REBOUND_TICKS));
        }
        return 0.0f;
    }

    static float flag(boolean pending, double s) {
        if (pending) {
            if (s < FALL_TICKS) {
                final double k = s / FALL_TICKS;
                return (float) (-90.0 * (1.0 - k * k));
            }
            if (s < FALL_TICKS + SWING_TICKS) {
                final double k = (s - FALL_TICKS) / SWING_TICKS;
                return (float) (SWING_DEGREES * Math.sin(2.0 * Math.PI * k) * Math.exp(-2.2 * k));
            }
            return 0.0f;
        }
        if (s < RAISE_TICKS) {
            final double k = s / RAISE_TICKS;
            return (float) (-90.0 * k * k * (3.0 - 2.0 * k));
        }
        return -90.0f;
    }

    static float press(double s) {
        if (s < PRESS_IN) {
            return (float) (s / PRESS_IN);
        }
        if (s < PRESS_HOLD) {
            return 1.0f;
        }
        if (s < PRESS_OUT) {
            return (float) (1.0 - (s - PRESS_HOLD) / (PRESS_OUT - PRESS_HOLD));
        }
        return 0.0f;
    }

    static float contactGlow(RelayContact contact, double now) {
        if (contact == null) {
            return 0.0f;
        }
        return switch (contact) {
            case LINKED, LOST -> 1.0f;
            case UNKNOWN -> {
                final double p = now % BLINK_PERIOD;
                final double half = BLINK_PERIOD / 2.0;
                yield p >= half ? 0.0f : (float) Math.min(1.0, Math.min(p / 2.0, (half - p) / 2.0));
            }
            case CANDIDATE -> (float) (0.18 + 0.82 * (0.5 - 0.5 * Math.cos(2.0 * Math.PI * now / BREATH_PERIOD)));
            case UNBOUND -> 0.0f;
        };
    }

    static int contactColour(RelayContact contact) {
        if (contact == RelayContact.LINKED) {
            return GREEN;
        }
        return contact == RelayContact.LOST ? RED : AMBER;
    }

    private static int mix(int from, int to, float t) {
        final float k = Math.max(0.0f, Math.min(1.0f, t));
        final int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        final int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        final int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static int turn(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
    }

    private static TextureAtlasSprite[] sprites() {
        final Function<ResourceLocation, TextureAtlasSprite> atlas =
                Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final TextureAtlasSprite[] out = new TextureAtlasSprite[SPRITES.length];
        for (int i = 0; i < SPRITES.length; i++) {
            out[i] = atlas.apply(ResourceLocation.fromNamespaceAndPath("create_stats",
                    "block/security_relay/" + SPRITES[i]));
        }
        return out;
    }

    private static final class Pen {
        private final VertexConsumer vc;
        private final PoseStack.Pose pose;
        private final TextureAtlasSprite[] sprites;
        private final float yawCos;
        private final float yawSin;
        private final int worldLight;

        private boolean lit;
        private int rgb = 0xFFFFFF;
        private float pivotY;
        private float pivotZ;
        private float cos = 1.0f;
        private float sin = 0.0f;
        private float tx;
        private float ty;
        private float tz;

        Pen(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite[] sprites, int turn, int light) {
            this.vc = vc;
            this.pose = pose;
            this.sprites = sprites;
            final double a = Math.toRadians(turn);
            this.yawCos = (float) Math.cos(a);
            this.yawSin = (float) Math.sin(a);
            this.worldLight = light;
        }

        Pen plain() {
            this.lit = false;
            this.rgb = 0xFFFFFF;
            return this;
        }

        Pen lit(int colour) {
            this.lit = true;
            this.rgb = colour;
            return this;
        }

        Pen tint(int colour) {
            this.rgb = colour;
            return this;
        }

        Pen turn(float y, float z, float degrees) {
            this.pivotY = y;
            this.pivotZ = z;
            final double a = Math.toRadians(degrees);
            this.cos = (float) Math.cos(a);
            this.sin = (float) Math.sin(a);
            return this;
        }

        Pen at(float x, float y, float z) {
            this.tx = x;
            this.ty = y;
            this.tz = z;
            return this;
        }

        void draw(float[] q) {
            for (int i = 0; i + STRIDE <= q.length; i += STRIDE) {
                final float nx = q[i + 20];
                final float ny = q[i + 21] * cos - q[i + 22] * sin;
                final float nz = q[i + 21] * sin + q[i + 22] * cos;
                final TextureAtlasSprite sprite = sprites[(int) q[i + 23]];
                final int colour = shaded(nx, ny, nz);
                for (int k = 0; k < 4; k++) {
                    final int o = i + 5 * k;
                    final float y = q[o + 1] - pivotY;
                    final float z = q[o + 2] - pivotZ;
                    vertex(q[o] + tx, pivotY + y * cos - z * sin + ty, pivotZ + y * sin + z * cos + tz,
                            sprite.getU(q[o + 3] / 16.0f), sprite.getV(q[o + 4] / 16.0f), colour, nx, ny, nz);
                }
            }
        }

        void northFace(float x0, float y0, float x1, float y1, float z, int sprite, float u0, float v0, float u1,
                       float v1) {
            final TextureAtlasSprite s = sprites[sprite];
            final int colour = shaded(0.0f, 0.0f, -1.0f);
            final float su0 = s.getU(u0 / 16.0f);
            final float su1 = s.getU(u1 / 16.0f);
            final float sv0 = s.getV(v0 / 16.0f);
            final float sv1 = s.getV(v1 / 16.0f);

            vertex(x1, y1, z, su0, sv0, colour, 0.0f, 0.0f, -1.0f);
            vertex(x1, y0, z, su0, sv1, colour, 0.0f, 0.0f, -1.0f);
            vertex(x0, y0, z, su1, sv1, colour, 0.0f, 0.0f, -1.0f);
            vertex(x0, y1, z, su1, sv0, colour, 0.0f, 0.0f, -1.0f);
        }

        private int shaded(float nx, float ny, float nz) {
            float f = 1.0f;
            if (!lit) {
                final float wx = nx * yawCos + nz * yawSin;
                final float wz = -nx * yawSin + nz * yawCos;
                f = Math.min(1.0f, wx * wx * 0.6f + ny * ny * ((3.0f + ny) / 4.0f) + wz * wz * 0.8f);
            }
            final int r = Math.round(((rgb >> 16) & 0xFF) * f);
            final int g = Math.round(((rgb >> 8) & 0xFF) * f);
            final int b = Math.round((rgb & 0xFF) * f);
            return (r << 16) | (g << 8) | b;
        }

        private void vertex(float x, float y, float z, float u, float v, int colour, float nx, float ny, float nz) {
            vc.addVertex(pose, x * PX, y * PX, z * PX)
                    .setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, 0xFF)
                    .setUv(u, v)
                    .setLight(lit ? LightTexture.FULL_BRIGHT : worldLight)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private static final String[] SPRITES = {"instruments", "brass", "iron", "silver", "cards"};

    private static final int SPRITE_CARDS = 4;

    static final float PULL_DEGREES = 10.2f;

    static final float PIVOT_Y = 12.35f;
    static final float PIVOT_Z = 7f;

    static final float BUTTON_TRAVEL = 0.75f;

    static final float HINGE_DROP = 0.2f;
    static final float HINGE_IN = 0.12f;

    static final float CARD_INSET = 0.35f;

    static final float MARK_HALF_U = 1.5f;
    static final float MARK_HALF_V = 1.75f;

    private static final String[] CARD_KEYS = {
            "lockdown", "tamper", "fire", "water", "denied", "breached",
            "trouble", "mode", "healthy", "unsupported",
    };

    private static final float[] CARD_UV = {
            0f, 0f, 3f, 2.5f,
            3f, 0f, 6f, 2.5f,
            6f, 0f, 9f, 2.5f,
            9f, 0f, 12f, 2.5f,
            12f, 0f, 15f, 2.5f,
            0f, 2.5f, 3f, 5f,
            3f, 2.5f, 6f, 5f,
            6f, 2.5f, 9f, 5f,
            9f, 2.5f, 12f, 5f,
            12f, 2.5f, 15f, 5f,
    };

    private static final float[] LETTER_UV = {
            0f, 5f, 1.5f, 6.75f,
            1.5f, 5f, 3f, 6.75f,
            3f, 5f, 4.5f, 6.75f,
    };

    private static final float[] LENS_LIT = {
            -0.5f, 0.5f, -0.95f, 3.5f, 11f, -0.5f, -0.5f, -0.95f,
            3.5f, 12f, -1f, -0.5f, -0.95f, 4f, 12f, -1f,
            0.5f, -0.95f, 4f, 11f, 0f, 0f, -1f, 0f,
            0.5f, 1f, -0.95f, 2.5f, 10.5f, 0.5f, -1f, -0.95f,
            2.5f, 12.5f, -0.5f, -1f, -0.95f, 3.5f, 12.5f, -0.5f,
            1f, -0.95f, 3.5f, 10.5f, 0f, 0f, -1f, 0f,
            1f, 0.5f, -0.95f, 2f, 11f, 1f, -0.5f, -0.95f,
            2f, 12f, 0.5f, -0.5f, -0.95f, 2.5f, 12f, 0.5f,
            0.5f, -0.95f, 2.5f, 11f, 0f, 0f, -1f, 0f,
    };

    private static final float[] BUTTON = {
            -1f, 0.5f, -2f, 5.5f, 6f, -1f, -0.5f, -2f,
            5.5f, 7f, -1.5f, -0.5f, -2f, 6f, 7f, -1.5f,
            0.5f, -2f, 6f, 6f, 0f, 0f, -1f, 0f,
            -1.5f, 0.5f, -2f, 2.1f, 12.6f, -1.5f, -0.5f, -2f,
            2.1f, 12.9f, -1.5f, -0.5f, -1.55f, 2.4f, 12.9f, -1.5f,
            0.5f, -1.55f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            -1.5f, 0.5f, -2f, 2.1f, 12.6f, -1.5f, 0.5f, -1.55f,
            2.1f, 12.9f, -1f, 0.5f, -1.55f, 2.4f, 12.9f, -1f,
            0.5f, -2f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -1.5f, -0.5f, -1.55f, 2.1f, 12.6f, -1.5f, -0.5f, -2f,
            2.1f, 12.9f, -1f, -0.5f, -2f, 2.4f, 12.9f, -1f,
            -0.5f, -1.55f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            -0.5f, 1f, -2f, 5f, 5.5f, -0.5f, -1f, -2f,
            5f, 7.5f, -1f, -1f, -2f, 5.5f, 7.5f, -1f,
            1f, -2f, 5.5f, 5.5f, 0f, 0f, -1f, 0f,
            -1f, 1f, -2f, 2.1f, 12.6f, -1f, -1f, -2f,
            2.1f, 12.9f, -1f, -1f, -1.55f, 2.4f, 12.9f, -1f,
            1f, -1.55f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            -1f, 1f, -2f, 2.1f, 12.6f, -1f, 1f, -1.55f,
            2.1f, 12.9f, -0.5f, 1f, -1.55f, 2.4f, 12.9f, -0.5f,
            1f, -2f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -1f, -1f, -1.55f, 2.1f, 12.6f, -1f, -1f, -2f,
            2.1f, 12.9f, -0.5f, -1f, -2f, 2.4f, 12.9f, -0.5f,
            -1f, -1.55f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            0.5f, 1.5f, -2f, 4f, 5f, 0.5f, -1.5f, -2f,
            4f, 8f, -0.5f, -1.5f, -2f, 5f, 8f, -0.5f,
            1.5f, -2f, 5f, 5f, 0f, 0f, -1f, 0f,
            -0.5f, 1.5f, -2f, 2.1f, 12.6f, -0.5f, -1.5f, -2f,
            2.1f, 12.9f, -0.5f, -1.5f, -1.55f, 2.4f, 12.9f, -0.5f,
            1.5f, -1.55f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            0.5f, 1.5f, -1.55f, 2.1f, 12.6f, 0.5f, -1.5f, -1.55f,
            2.1f, 12.9f, 0.5f, -1.5f, -2f, 2.4f, 12.9f, 0.5f,
            1.5f, -2f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            -0.5f, 1.5f, -2f, 2.1f, 12.6f, -0.5f, 1.5f, -1.55f,
            2.1f, 12.9f, 0.5f, 1.5f, -1.55f, 2.4f, 12.9f, 0.5f,
            1.5f, -2f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -0.5f, -1.5f, -1.55f, 2.1f, 12.6f, -0.5f, -1.5f, -2f,
            2.1f, 12.9f, 0.5f, -1.5f, -2f, 2.4f, 12.9f, 0.5f,
            -1.5f, -1.55f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            1f, 1f, -2f, 3.5f, 5.5f, 1f, -1f, -2f,
            3.5f, 7.5f, 0.5f, -1f, -2f, 4f, 7.5f, 0.5f,
            1f, -2f, 4f, 5.5f, 0f, 0f, -1f, 0f,
            1f, 1f, -1.55f, 2.1f, 12.6f, 1f, -1f, -1.55f,
            2.1f, 12.9f, 1f, -1f, -2f, 2.4f, 12.9f, 1f,
            1f, -2f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            0.5f, 1f, -2f, 2.1f, 12.6f, 0.5f, 1f, -1.55f,
            2.1f, 12.9f, 1f, 1f, -1.55f, 2.4f, 12.9f, 1f,
            1f, -2f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            0.5f, -1f, -1.55f, 2.1f, 12.6f, 0.5f, -1f, -2f,
            2.1f, 12.9f, 1f, -1f, -2f, 2.4f, 12.9f, 1f,
            -1f, -1.55f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            1.5f, 0.5f, -2f, 3f, 6f, 1.5f, -0.5f, -2f,
            3f, 7f, 1f, -0.5f, -2f, 3.5f, 7f, 1f,
            0.5f, -2f, 3.5f, 6f, 0f, 0f, -1f, 0f,
            1.5f, 0.5f, -1.55f, 2.1f, 12.6f, 1.5f, -0.5f, -1.55f,
            2.1f, 12.9f, 1.5f, -0.5f, -2f, 2.4f, 12.9f, 1.5f,
            0.5f, -2f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            1f, 0.5f, -2f, 2.1f, 12.6f, 1f, 0.5f, -1.55f,
            2.1f, 12.9f, 1.5f, 0.5f, -1.55f, 2.4f, 12.9f, 1.5f,
            0.5f, -2f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            1f, -0.5f, -1.55f, 2.1f, 12.6f, 1f, -0.5f, -2f,
            2.1f, 12.9f, 1.5f, -0.5f, -2f, 2.4f, 12.9f, 1.5f,
            -0.5f, -1.55f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            -1.5f, 1f, -1.55f, 13f, 1f, -1.5f, -1f, -1.55f,
            13f, 3f, -2f, -1f, -1.55f, 13.5f, 3f, -2f,
            1f, -1.55f, 13.5f, 1f, 0f, 0f, -1f, 0f,
            -2f, 1f, -1.55f, 2.1f, 12.6f, -2f, -1f, -1.55f,
            2.1f, 12.9f, -2f, -1f, -1.05f, 2.4f, 12.9f, -2f,
            1f, -1.05f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            -2f, 1f, -1.55f, 2.1f, 12.6f, -2f, 1f, -1.05f,
            2.1f, 12.9f, -1.5f, 1f, -1.05f, 2.4f, 12.9f, -1.5f,
            1f, -1.55f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -2f, -1f, -1.05f, 2.1f, 12.6f, -2f, -1f, -1.55f,
            2.1f, 12.9f, -1.5f, -1f, -1.55f, 2.4f, 12.9f, -1.5f,
            -1f, -1.05f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            -1f, 1.5f, -1.55f, 12.5f, 0.5f, -1f, -1.5f, -1.55f,
            12.5f, 3.5f, -1.5f, -1.5f, -1.55f, 13f, 3.5f, -1.5f,
            1.5f, -1.55f, 13f, 0.5f, 0f, 0f, -1f, 0f,
            -1.5f, 1.5f, -1.55f, 2.1f, 12.6f, -1.5f, -1.5f, -1.55f,
            2.1f, 12.9f, -1.5f, -1.5f, -1.05f, 2.4f, 12.9f, -1.5f,
            1.5f, -1.05f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            -1.5f, 1.5f, -1.55f, 2.1f, 12.6f, -1.5f, 1.5f, -1.05f,
            2.1f, 12.9f, -1f, 1.5f, -1.05f, 2.4f, 12.9f, -1f,
            1.5f, -1.55f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -1.5f, -1.5f, -1.05f, 2.1f, 12.6f, -1.5f, -1.5f, -1.55f,
            2.1f, 12.9f, -1f, -1.5f, -1.55f, 2.4f, 12.9f, -1f,
            -1.5f, -1.05f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            1f, 2f, -1.55f, 10.5f, 0f, 1f, -2f, -1.55f,
            10.5f, 4f, -1f, -2f, -1.55f, 12.5f, 4f, -1f,
            2f, -1.55f, 12.5f, 0f, 0f, 0f, -1f, 0f,
            -1f, 2f, -1.55f, 2.1f, 12.6f, -1f, -2f, -1.55f,
            2.1f, 12.9f, -1f, -2f, -1.05f, 2.4f, 12.9f, -1f,
            2f, -1.05f, 2.4f, 12.6f, -1f, 0f, 0f, 0f,
            1f, 2f, -1.05f, 2.1f, 12.6f, 1f, -2f, -1.05f,
            2.1f, 12.9f, 1f, -2f, -1.55f, 2.4f, 12.9f, 1f,
            2f, -1.55f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            -1f, 2f, -1.55f, 2.1f, 12.6f, -1f, 2f, -1.05f,
            2.1f, 12.9f, 1f, 2f, -1.05f, 2.4f, 12.9f, 1f,
            2f, -1.55f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            -1f, -2f, -1.05f, 2.1f, 12.6f, -1f, -2f, -1.55f,
            2.1f, 12.9f, 1f, -2f, -1.55f, 2.4f, 12.9f, 1f,
            -2f, -1.05f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            1.5f, 1.5f, -1.55f, 10f, 0.5f, 1.5f, -1.5f, -1.55f,
            10f, 3.5f, 1f, -1.5f, -1.55f, 10.5f, 3.5f, 1f,
            1.5f, -1.55f, 10.5f, 0.5f, 0f, 0f, -1f, 0f,
            1.5f, 1.5f, -1.05f, 2.1f, 12.6f, 1.5f, -1.5f, -1.05f,
            2.1f, 12.9f, 1.5f, -1.5f, -1.55f, 2.4f, 12.9f, 1.5f,
            1.5f, -1.55f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            1f, 1.5f, -1.55f, 2.1f, 12.6f, 1f, 1.5f, -1.05f,
            2.1f, 12.9f, 1.5f, 1.5f, -1.05f, 2.4f, 12.9f, 1.5f,
            1.5f, -1.55f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            1f, -1.5f, -1.05f, 2.1f, 12.6f, 1f, -1.5f, -1.55f,
            2.1f, 12.9f, 1.5f, -1.5f, -1.55f, 2.4f, 12.9f, 1.5f,
            -1.5f, -1.05f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            2f, 1f, -1.55f, 9.5f, 1f, 2f, -1f, -1.55f,
            9.5f, 3f, 1.5f, -1f, -1.55f, 10f, 3f, 1.5f,
            1f, -1.55f, 10f, 1f, 0f, 0f, -1f, 0f,
            2f, 1f, -1.05f, 2.1f, 12.6f, 2f, -1f, -1.05f,
            2.1f, 12.9f, 2f, -1f, -1.55f, 2.4f, 12.9f, 2f,
            1f, -1.55f, 2.4f, 12.6f, 1f, 0f, 0f, 0f,
            1.5f, 1f, -1.55f, 2.1f, 12.6f, 1.5f, 1f, -1.05f,
            2.1f, 12.9f, 2f, 1f, -1.05f, 2.4f, 12.9f, 2f,
            1f, -1.55f, 2.4f, 12.6f, 0f, 1f, 0f, 0f,
            1.5f, -1f, -1.05f, 2.1f, 12.6f, 1.5f, -1f, -1.55f,
            2.1f, 12.9f, 2f, -1f, -1.55f, 2.4f, 12.9f, 2f,
            -1f, -1.05f, 2.4f, 12.6f, 0f, -1f, 0f, 0f,
            -0.75f, 0.25f, -1.05f, 14.1f, 12.6f, -0.75f, -0.25f, -1.05f,
            14.1f, 12.9f, -0.75f, -0.25f, -0.2f, 14.4f, 12.9f, -0.75f,
            0.25f, -0.2f, 14.4f, 12.6f, -1f, 0f, 0f, 0f,
            -0.75f, 0.25f, -1.05f, 14.1f, 12.6f, -0.75f, 0.25f, -0.2f,
            14.1f, 12.9f, -0.25f, 0.25f, -0.2f, 14.4f, 12.9f, -0.25f,
            0.25f, -1.05f, 14.4f, 12.6f, 0f, 1f, 0f, 0f,
            -0.75f, -0.25f, -0.2f, 14.1f, 12.6f, -0.75f, -0.25f, -1.05f,
            14.1f, 12.9f, -0.25f, -0.25f, -1.05f, 14.4f, 12.9f, -0.25f,
            -0.25f, -0.2f, 14.4f, 12.6f, 0f, -1f, 0f, 0f,
            -0.25f, 0.75f, -1.05f, 14.1f, 12.6f, -0.25f, -0.75f, -1.05f,
            14.1f, 12.9f, -0.25f, -0.75f, -0.2f, 14.4f, 12.9f, -0.25f,
            0.75f, -0.2f, 14.4f, 12.6f, -1f, 0f, 0f, 0f,
            0.25f, 0.75f, -0.2f, 14.1f, 12.6f, 0.25f, -0.75f, -0.2f,
            14.1f, 12.9f, 0.25f, -0.75f, -1.05f, 14.4f, 12.9f, 0.25f,
            0.75f, -1.05f, 14.4f, 12.6f, 1f, 0f, 0f, 0f,
            -0.25f, 0.75f, -1.05f, 14.1f, 12.6f, -0.25f, 0.75f, -0.2f,
            14.1f, 12.9f, 0.25f, 0.75f, -0.2f, 14.4f, 12.9f, 0.25f,
            0.75f, -1.05f, 14.4f, 12.6f, 0f, 1f, 0f, 0f,
            -0.25f, -0.75f, -0.2f, 14.1f, 12.6f, -0.25f, -0.75f, -1.05f,
            14.1f, 12.9f, 0.25f, -0.75f, -1.05f, 14.4f, 12.9f, 0.25f,
            -0.75f, -0.2f, 14.4f, 12.6f, 0f, -1f, 0f, 0f,
            0.75f, 0.25f, -0.2f, 14.1f, 12.6f, 0.75f, -0.25f, -0.2f,
            14.1f, 12.9f, 0.75f, -0.25f, -1.05f, 14.4f, 12.9f, 0.75f,
            0.25f, -1.05f, 14.4f, 12.6f, 1f, 0f, 0f, 0f,
            0.25f, 0.25f, -1.05f, 14.1f, 12.6f, 0.25f, 0.25f, -0.2f,
            14.1f, 12.9f, 0.75f, 0.25f, -0.2f, 14.4f, 12.9f, 0.75f,
            0.25f, -1.05f, 14.4f, 12.6f, 0f, 1f, 0f, 0f,
            0.25f, -0.25f, -0.2f, 14.1f, 12.6f, 0.25f, -0.25f, -1.05f,
            14.1f, 12.9f, 0.75f, -0.25f, -1.05f, 14.4f, 12.9f, 0.75f,
            -0.25f, -0.2f, 14.4f, 12.6f, 0f, -1f, 0f, 0f,
    };

    private static final float[] FLAG = {
            1.75f, 0f, -0.08f, 11.5f, 10.5f, 1.75f, -1.5f, -0.08f,
            11.5f, 12f, -1.75f, -1.5f, -0.08f, 15f, 12f, -1.75f,
            0f, -0.08f, 15f, 10.5f, 0f, 0f, -1f, 0f,
            -1.75f, 0f, 0.12f, 8f, 10.5f, -1.75f, -1.5f, 0.12f,
            8f, 12f, 1.75f, -1.5f, 0.12f, 11.5f, 12f, 1.75f,
            0f, 0.12f, 11.5f, 10.5f, 0f, 0f, 1f, 0f,
            -1.75f, 0f, -0.08f, 4.1f, 12.6f, -1.75f, -1.5f, -0.08f,
            4.1f, 12.9f, -1.75f, -1.5f, 0.12f, 4.4f, 12.9f, -1.75f,
            0f, 0.12f, 4.4f, 12.6f, -1f, 0f, 0f, 0f,
            1.75f, 0f, 0.12f, 4.1f, 12.6f, 1.75f, -1.5f, 0.12f,
            4.1f, 12.9f, 1.75f, -1.5f, -0.08f, 4.4f, 12.9f, 1.75f,
            0f, -0.08f, 4.4f, 12.6f, 1f, 0f, 0f, 0f,
            -1.75f, 0f, -0.08f, 4.1f, 12.6f, -1.75f, 0f, 0.12f,
            4.1f, 12.9f, 1.75f, 0f, 0.12f, 4.4f, 12.9f, 1.75f,
            0f, -0.08f, 4.4f, 12.6f, 0f, 1f, 0f, 0f,
            -1.75f, -1.5f, 0.12f, 4.1f, 12.6f, -1.75f, -1.5f, -0.08f,
            4.1f, 12.9f, 1.75f, -1.5f, -0.08f, 4.4f, 12.9f, 1.75f,
            -1.5f, 0.12f, 4.4f, 12.6f, 0f, -1f, 0f, 0f,
    };

    private static final float[] ARMATURE = {
            12.75f, 12.25f, 9.25f, 0f, 0f, 12.75f, 11.85f, 9.25f,
            0f, 0.4f, 3.25f, 11.85f, 9.25f, 9.5f, 0.4f, 3.25f,
            12.25f, 9.25f, 9.5f, 0f, 0f, 0f, -1f, 2f,
            3.25f, 12.25f, 10.25f, 0f, 0f, 3.25f, 11.85f, 10.25f,
            0f, 0.4f, 12.75f, 11.85f, 10.25f, 9.5f, 0.4f, 12.75f,
            12.25f, 10.25f, 9.5f, 0f, 0f, 0f, 1f, 2f,
            3.25f, 12.25f, 9.25f, 0f, 0f, 3.25f, 11.85f, 9.25f,
            0f, 0.4f, 3.25f, 11.85f, 10.25f, 1f, 0.4f, 3.25f,
            12.25f, 10.25f, 1f, 0f, -1f, 0f, 0f, 2f,
            12.75f, 12.25f, 10.25f, 0f, 0f, 12.75f, 11.85f, 10.25f,
            0f, 0.4f, 12.75f, 11.85f, 9.25f, 1f, 0.4f, 12.75f,
            12.25f, 9.25f, 1f, 0f, 1f, 0f, 0f, 2f,
            3.25f, 12.25f, 9.25f, 0f, 0f, 3.25f, 12.25f, 10.25f,
            0f, 1f, 12.75f, 12.25f, 10.25f, 9.5f, 1f, 12.75f,
            12.25f, 9.25f, 9.5f, 0f, 0f, 1f, 0f, 2f,
            3.25f, 11.85f, 10.25f, 0f, 0f, 3.25f, 11.85f, 9.25f,
            0f, 1f, 12.75f, 11.85f, 9.25f, 9.5f, 1f, 12.75f,
            11.85f, 10.25f, 9.5f, 0f, 0f, -1f, 0f, 2f,
            8.5f, 12.45f, 6.9f, 0f, 0f, 8.5f, 12.25f, 6.9f,
            0f, 0.2f, 7.5f, 12.25f, 6.9f, 1f, 0.2f, 7.5f,
            12.45f, 6.9f, 1f, 0f, 0f, 0f, -1f, 1f,
            7.5f, 12.45f, 12.6f, 0f, 0f, 7.5f, 12.25f, 12.6f,
            0f, 0.2f, 8.5f, 12.25f, 12.6f, 1f, 0.2f, 8.5f,
            12.45f, 12.6f, 1f, 0f, 0f, 0f, 1f, 1f,
            7.5f, 12.45f, 6.9f, 0f, 0f, 7.5f, 12.25f, 6.9f,
            0f, 0.2f, 7.5f, 12.25f, 12.6f, 5.7f, 0.2f, 7.5f,
            12.45f, 12.6f, 5.7f, 0f, -1f, 0f, 0f, 1f,
            8.5f, 12.45f, 12.6f, 0f, 0f, 8.5f, 12.25f, 12.6f,
            0f, 0.2f, 8.5f, 12.25f, 6.9f, 5.7f, 0.2f, 8.5f,
            12.45f, 6.9f, 5.7f, 0f, 1f, 0f, 0f, 1f,
            7.5f, 12.45f, 6.9f, 0f, 0f, 7.5f, 12.45f, 12.6f,
            0f, 5.7f, 8.5f, 12.45f, 12.6f, 1f, 5.7f, 8.5f,
            12.45f, 6.9f, 1f, 0f, 0f, 1f, 0f, 1f,
            7.5f, 12.25f, 12.6f, 0f, 0f, 7.5f, 12.25f, 6.9f,
            0f, 5.7f, 8.5f, 12.25f, 6.9f, 1f, 5.7f, 8.5f,
            12.25f, 12.6f, 1f, 0f, 0f, -1f, 0f, 1f,
            8.35f, 12.25f, 11.8f, 0f, 0f, 8.35f, 11.9f, 11.8f,
            0f, 0.35f, 7.65f, 11.9f, 11.8f, 0.7f, 0.35f, 7.65f,
            12.25f, 11.8f, 0.7f, 0f, 0f, 0f, -1f, 3f,
            7.65f, 12.25f, 12.55f, 0f, 0f, 7.65f, 11.9f, 12.55f,
            0f, 0.35f, 8.35f, 11.9f, 12.55f, 0.7f, 0.35f, 8.35f,
            12.25f, 12.55f, 0.7f, 0f, 0f, 0f, 1f, 3f,
            7.65f, 12.25f, 11.8f, 0f, 0f, 7.65f, 11.9f, 11.8f,
            0f, 0.35f, 7.65f, 11.9f, 12.55f, 0.75f, 0.35f, 7.65f,
            12.25f, 12.55f, 0.75f, 0f, -1f, 0f, 0f, 3f,
            8.35f, 12.25f, 12.55f, 0f, 0f, 8.35f, 11.9f, 12.55f,
            0f, 0.35f, 8.35f, 11.9f, 11.8f, 0.75f, 0.35f, 8.35f,
            12.25f, 11.8f, 0.75f, 0f, 1f, 0f, 0f, 3f,
            7.65f, 11.9f, 12.55f, 0f, 0f, 7.65f, 11.9f, 11.8f,
            0f, 0.75f, 8.35f, 11.9f, 11.8f, 0.7f, 0.75f, 8.35f,
            11.9f, 12.55f, 0.7f, 0f, 0f, -1f, 0f, 3f,
    };

    private static final float[] POST_GLOW = {
            6.75f, 4.875f, 14.75f, 10.5f, 8.5f, 6.75f, 3.625f, 14.75f,
            10.5f, 9.5f, 7.375f, 3.625f, 14.75f, 11f, 9.5f, 7.375f,
            4.875f, 14.75f, 11f, 8.5f, 0f, 0f, 1f, 0f,
            6.75f, 4.875f, 13.85f, 6.1f, 12.6f, 6.75f, 3.625f, 13.85f,
            6.1f, 12.9f, 6.75f, 3.625f, 14.75f, 6.4f, 12.9f, 6.75f,
            4.875f, 14.75f, 6.4f, 12.6f, -1f, 0f, 0f, 0f,
            6.75f, 4.875f, 13.85f, 6.1f, 12.6f, 6.75f, 4.875f, 14.75f,
            6.1f, 12.9f, 7.375f, 4.875f, 14.75f, 6.4f, 12.9f, 7.375f,
            4.875f, 13.85f, 6.4f, 12.6f, 0f, 1f, 0f, 0f,
            6.75f, 3.625f, 14.75f, 6.1f, 12.6f, 6.75f, 3.625f, 13.85f,
            6.1f, 12.9f, 7.375f, 3.625f, 13.85f, 6.4f, 12.9f, 7.375f,
            3.625f, 14.75f, 6.4f, 12.6f, 0f, -1f, 0f, 0f,
            7.375f, 5.5f, 14.75f, 11f, 8f, 7.375f, 3f, 14.75f,
            11f, 10f, 8.625f, 3f, 14.75f, 12f, 10f, 8.625f,
            5.5f, 14.75f, 12f, 8f, 0f, 0f, 1f, 0f,
            7.375f, 5.5f, 13.85f, 6.1f, 12.6f, 7.375f, 3f, 13.85f,
            6.1f, 12.9f, 7.375f, 3f, 14.75f, 6.4f, 12.9f, 7.375f,
            5.5f, 14.75f, 6.4f, 12.6f, -1f, 0f, 0f, 0f,
            8.625f, 5.5f, 14.75f, 6.1f, 12.6f, 8.625f, 3f, 14.75f,
            6.1f, 12.9f, 8.625f, 3f, 13.85f, 6.4f, 12.9f, 8.625f,
            5.5f, 13.85f, 6.4f, 12.6f, 1f, 0f, 0f, 0f,
            7.375f, 5.5f, 13.85f, 6.1f, 12.6f, 7.375f, 5.5f, 14.75f,
            6.1f, 12.9f, 8.625f, 5.5f, 14.75f, 6.4f, 12.9f, 8.625f,
            5.5f, 13.85f, 6.4f, 12.6f, 0f, 1f, 0f, 0f,
            7.375f, 3f, 14.75f, 6.1f, 12.6f, 7.375f, 3f, 13.85f,
            6.1f, 12.9f, 8.625f, 3f, 13.85f, 6.4f, 12.9f, 8.625f,
            3f, 14.75f, 6.4f, 12.6f, 0f, -1f, 0f, 0f,
            8.625f, 4.875f, 14.75f, 12f, 8.5f, 8.625f, 3.625f, 14.75f,
            12f, 9.5f, 9.25f, 3.625f, 14.75f, 12.5f, 9.5f, 9.25f,
            4.875f, 14.75f, 12.5f, 8.5f, 0f, 0f, 1f, 0f,
            9.25f, 4.875f, 14.75f, 6.1f, 12.6f, 9.25f, 3.625f, 14.75f,
            6.1f, 12.9f, 9.25f, 3.625f, 13.85f, 6.4f, 12.9f, 9.25f,
            4.875f, 13.85f, 6.4f, 12.6f, 1f, 0f, 0f, 0f,
            8.625f, 4.875f, 13.85f, 6.1f, 12.6f, 8.625f, 4.875f, 14.75f,
            6.1f, 12.9f, 9.25f, 4.875f, 14.75f, 6.4f, 12.9f, 9.25f,
            4.875f, 13.85f, 6.4f, 12.6f, 0f, 1f, 0f, 0f,
            8.625f, 3.625f, 14.75f, 6.1f, 12.6f, 8.625f, 3.625f, 13.85f,
            6.1f, 12.9f, 9.25f, 3.625f, 13.85f, 6.4f, 12.9f, 9.25f,
            3.625f, 14.75f, 6.4f, 12.6f, 0f, -1f, 0f, 0f,
    };
}
