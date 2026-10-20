package com.createstats.renderer;

import com.createstats.block.DiagnosticJunctionBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.power.DiagnosticJunctionStuds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class DiagnosticJunctionRenderer implements BlockEntityRenderer<DiagnosticJunctionBE> {
    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/junction_dynamic.png");
    private static final ResourceLocation CODE_CARD =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/junction_code_card.png");

    private static final float PX = 1.0f / 16.0f;

    private record Tile(float u0, float v0, float u1, float v1) {
    }

    private static final int TILE_COLS = 4;
    private static final int TILE_ROWS = 5;

    private static Tile tile(int col, int row) {
        final float inset = 0.5f / (TILE_COLS * 16.0f);
        return new Tile(col / (float) TILE_COLS + inset, row / (float) TILE_ROWS + inset,
                (col + 1) / (float) TILE_COLS - inset, (row + 1) / (float) TILE_ROWS - inset);
    }

    private static final Tile T_DOOR = tile(0, 0);
    private static final Tile T_DOOR_INNER = tile(1, 0);
    private static final Tile T_DOOR_EDGE = tile(2, 0);
    private static final Tile T_GASKET = tile(3, 0);
    private static final Tile T_LEVER = tile(0, 1);
    private static final Tile T_CAP = tile(1, 1);
    private static final Tile T_SWITCH_PLATE = tile(3, 1);
    private static final Tile T_HAZARD = tile(0, 2);
    private static final Tile T_HANDLE = tile(1, 2);
    private static final Tile T_BOSS = tile(2, 2);
    private static final Tile T_SCREW = tile(3, 2);
    private static final Tile T_CORD = tile(1, 3);
    private static final Tile T_PLATE_CABLE = tile(0, 4);
    private static final Tile T_PLATE_WIRE = tile(1, 4);
    private static final Tile T_RIM = tile(2, 4);
    private static final Tile T_LAMP = tile(3, 4);

    private static final float DOOR_X0 = 3.05f, DOOR_Y0 = 2.40f, DOOR_Z0 = 6.05f;
    private static final float DOOR_X1 = 12.95f, DOOR_Y1 = 12.60f, DOOR_Z1 = 7.05f;
    private static final float HINGE_X = 3.05f, HINGE_Z = 6.95f;
    private static final float OPEN_DEGREES = -112.0f;
    private static final double DOOR_SECONDS = 0.75D;
    private static final double SWITCH_SECONDS = 0.12D;
    private static final double ISOLATOR_SECONDS = 0.22D;

    private static final float SCREEN_X0 = 4.70f, SCREEN_Y0 = 8.10f;
    private static final float SCREEN_X1 = 11.70f, SCREEN_Y1 = 11.60f;
    private static final float SCREEN_Z = 2.98f;

    private static final float LAMP_Z = 2.52f;
    private static final float LAMP_Y0 = 7.05f, LAMP_Y1 = 7.42f;
    private static final float LAMP_X = 4.45f, LAMP_W = 0.75f, LAMP_STEP = 1.05f;

    private static final float SWITCH_X = 4.45f, SWITCH_Y = 4.35f, SWITCH_Z = 2.45f;
    private static final float SWITCH_TILT = 30.0f;

    private static final float ISO_X = 14.35f, ISO_Y = 8.00f, ISO_Z = 6.10f;

    private static final float CARD_X0 = 3.80f, CARD_Y0 = 3.10f;
    private static final float CARD_X1 = 9.85f, CARD_Y1 = 11.90f;

    private static final float GLAND_X0 = 6.60f, GLAND_X1 = 9.40f;
    private static final float GLAND_Z0 = 1.00f, GLAND_Z1 = 3.50f;

    private static final Map<BlockPos, Anim> ANIMS = new HashMap<>();

    private static final class Anim {
        float door;
        float merge;
        float isolate;
        long lastNanos;
        long lastAccess;
    }

    public DiagnosticJunctionRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DiagnosticJunctionBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final Direction facing = be.getBlockState().getValue(DiagnosticJunctionBlock.FACING);
        final boolean wire = be.kind() == DiagnosticJunctionBlock.Kind.WIRE;
        drawStudWires(be, facing, ps, buffers, packedLight);

        final Anim anim = advance(be);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ps.translate(-0.5, -0.5, -0.5);

        final VertexConsumer vc = Buffers.live(buffers, RenderType.entityCutoutNoCull(PARTS));

        if (anim.door > 0.02f) {
            renderScreen(be, ps, buffers);
            renderLamps(be, wire, vc, ps, packedLight);
            if (wire) {
                renderMergeLever(be, anim, vc, ps, packedLight);
            }
        }
        renderIsolator(anim, vc, ps, packedLight);
        if (!wire) {
            renderCordStubs(be, vc, ps, packedLight);
        }
        renderDoor(be, wire, anim, vc, ps, buffers, packedLight);
        ps.popPose();
    }

    private static Anim advance(DiagnosticJunctionBE be) {
        final long now = System.nanoTime();
        final Anim anim = ANIMS.computeIfAbsent(be.getBlockPos().immutable(), pos -> {
            final Anim created = new Anim();
            created.door = be.isOpen() ? 1.0f : 0.0f;
            created.merge = be.isMerged() ? 1.0f : 0.0f;
            created.isolate = be.isCutOff() ? 1.0f : 0.0f;
            created.lastNanos = now;
            return created;
        });

        final double dt = Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D));
        anim.lastNanos = now;
        anim.lastAccess = now;
        anim.door = approach(anim.door, be.isOpen() ? 1.0f : 0.0f, dt / DOOR_SECONDS);
        anim.merge = approach(anim.merge, be.isMerged() ? 1.0f : 0.0f, dt / SWITCH_SECONDS);
        anim.isolate = approach(anim.isolate, be.isCutOff() ? 1.0f : 0.0f, dt / ISOLATOR_SECONDS);
        cleanup(now);
        return anim;
    }

    private static float approach(float value, float target, double step) {
        return value < target ? Math.min(target, value + (float) step)
                : Math.max(target, value - (float) step);
    }

    private static float smooth(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float snap(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        final float eased = smooth(t);
        return eased + 0.14f * (float) Math.sin(t * Math.PI) * (t < 0.5f ? -1.0f : 1.0f);
    }

    private static void cleanup(long now) {
        if (ANIMS.size() < 192) {
            return;
        }
        final Iterator<Map.Entry<BlockPos, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastAccess > 30_000_000_000L) {
                it.remove();
            }
        }
    }

    private static void renderDoor(DiagnosticJunctionBE be, boolean wire, Anim anim, VertexConsumer vc,
                                   PoseStack ps, MultiBufferSource buffers, int packedLight) {
        final float swing = smooth(anim.door);
        ps.pushPose();
        ps.translate(HINGE_X * PX, 0.0f, HINGE_Z * PX);
        ps.mulPose(Axis.YP.rotationDegrees(OPEN_DEGREES * swing));
        ps.translate(-HINGE_X * PX, 0.0f, -HINGE_Z * PX);

        box(vc, ps, DOOR_X0, DOOR_Y0, DOOR_Z0, DOOR_X1, DOOR_Y1, DOOR_Z1,
                T_DOOR, T_DOOR_INNER, T_DOOR_EDGE, T_DOOR_EDGE, 0xFFFFFFFF, packedLight);

        final float gz = DOOR_Z0 - 0.02f;
        plate(vc, ps, DOOR_X0 + 0.30f, DOOR_Y0 + 0.30f, DOOR_X1 - 0.30f, DOOR_Y0 + 0.60f, gz,
                T_GASKET, 0xFFFFFFFF, packedLight, false);
        plate(vc, ps, DOOR_X0 + 0.30f, DOOR_Y1 - 0.60f, DOOR_X1 - 0.30f, DOOR_Y1 - 0.30f, gz,
                T_GASKET, 0xFFFFFFFF, packedLight, false);
        plate(vc, ps, DOOR_X0 + 0.30f, DOOR_Y0 + 0.30f, DOOR_X0 + 0.60f, DOOR_Y1 - 0.30f, gz,
                T_GASKET, 0xFFFFFFFF, packedLight, false);
        plate(vc, ps, DOOR_X1 - 0.60f, DOOR_Y0 + 0.30f, DOOR_X1 - 0.30f, DOOR_Y1 - 0.30f, gz,
                T_GASKET, 0xFFFFFFFF, packedLight, false);

        if (anim.door > 0.08f) {
            final VertexConsumer card = buffers.getBuffer(RenderType.entityCutoutNoCull(CODE_CARD));
            plate(card, ps, CARD_X0, CARD_Y0, CARD_X1, CARD_Y1, DOOR_Z0 - 0.05f,
                    new Tile(0.0f, 0.0f, 1.0f, 1.0f), 0xFFFFFFFF, packedLight, false);
            for (float cx : new float[]{CARD_X0 - 0.05f, CARD_X1 - 0.45f}) {
                for (float cy : new float[]{CARD_Y0 + 0.20f, CARD_Y1 - 0.70f}) {
                    box(vc, ps, cx, cy, DOOR_Z0 - 0.20f, cx + 0.5f, cy + 0.5f, DOOR_Z0 - 0.04f,
                            T_SCREW, T_SCREW, T_SCREW, T_SCREW, 0xFFFFFFFF, packedLight);
                }
            }

            plate(vc, ps, 10.30f, 4.60f, 12.20f, 10.40f, DOOR_Z0 - 0.05f,
                    T_HAZARD, 0xFFFFFFFF, packedLight, false);
        }

        plate(vc, ps, 5.20f, 9.20f, 10.80f, 11.00f, DOOR_Z1 + 0.02f,
                wire ? T_PLATE_WIRE : T_PLATE_CABLE, 0xFFFFFFFF, packedLight, true);

        box(vc, ps, 7.20f, 3.15f, DOOR_Z1, 8.80f, 4.40f, DOOR_Z1 + 0.16f,
                T_GASKET, T_GASKET, T_GASKET, T_GASKET, 0xFFFFFFFF, packedLight);
        final int lamp = colourOf(be);
        box(vc, ps, 7.40f, 3.35f, DOOR_Z1 + 0.02f, 8.60f, 4.20f, DOOR_Z1 + 0.26f,
                T_LAMP, T_LAMP, T_LAMP, T_LAMP, lamp, LightTexture.FULL_BRIGHT);
        renderLatch(swing, vc, ps, packedLight);
        ps.popPose();
    }

    private static void renderLatch(float swing, VertexConsumer vc, PoseStack ps, int packedLight) {
        plate(vc, ps, 11.15f, 6.50f, 12.65f, 8.50f, DOOR_Z1 + 0.03f, T_RIM, 0xFFFFFFFF, packedLight, true);
        box(vc, ps, 11.55f, 6.90f, DOOR_Z1, 12.25f, 8.10f, DOOR_Z1 + 0.80f,
                T_BOSS, T_BOSS, T_BOSS, T_BOSS, 0xFFFFFFFF, packedLight);
        final float turn = 90.0f * smooth(Math.min(1.0f, swing * 5.0f));
        ps.pushPose();
        ps.translate(11.90f * PX, 7.50f * PX, 0.0f);
        ps.mulPose(Axis.ZP.rotationDegrees(turn));
        ps.translate(-11.90f * PX, -7.50f * PX, 0.0f);
        box(vc, ps, 10.80f, 7.15f, DOOR_Z1 + 0.80f, 13.00f, 7.85f, DOOR_Z1 + 1.20f,
                T_HANDLE, T_HANDLE, T_HANDLE, T_HANDLE, 0xFFFFFFFF, packedLight);
        ps.popPose();
    }

    private static void renderMergeLever(DiagnosticJunctionBE be, Anim anim, VertexConsumer vc,
                                         PoseStack ps, int packedLight) {
        plate(vc, ps, SWITCH_X - 0.85f, SWITCH_Y - 1.25f, SWITCH_X + 0.85f, SWITCH_Y + 1.25f,
                SWITCH_Z + 0.02f, T_SWITCH_PLATE, 0xFFFFFFFF, packedLight, true);
        box(vc, ps, SWITCH_X - 0.42f, SWITCH_Y - 0.42f, SWITCH_Z, SWITCH_X + 0.42f, SWITCH_Y + 0.42f,
                SWITCH_Z + 0.30f, T_BOSS, T_BOSS, T_BOSS, T_BOSS, 0xFFFFFFFF, packedLight);

        final float t = snap(anim.merge);
        final float tilt = SWITCH_TILT - 2.0f * SWITCH_TILT * t;
        ps.pushPose();
        ps.translate(SWITCH_X * PX, SWITCH_Y * PX, (SWITCH_Z + 0.15f) * PX);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.translate(-SWITCH_X * PX, -SWITCH_Y * PX, -(SWITCH_Z + 0.15f) * PX);
        box(vc, ps, SWITCH_X - 0.24f, SWITCH_Y - 0.24f, SWITCH_Z + 0.15f,
                SWITCH_X + 0.24f, SWITCH_Y + 0.24f, SWITCH_Z + 1.35f,
                T_LEVER, T_LEVER, T_LEVER, T_LEVER, 0xFFFFFFFF, packedLight);
        final int lens = be.isMerged() ? 0xFFFFC24A : 0xFF6BE39A;
        box(vc, ps, SWITCH_X - 0.34f, SWITCH_Y - 0.34f, SWITCH_Z + 1.35f,
                SWITCH_X + 0.34f, SWITCH_Y + 0.34f, SWITCH_Z + 1.70f,
                T_CAP, T_CAP, T_CAP, T_CAP, lens, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private static void renderIsolator(Anim anim, VertexConsumer vc, PoseStack ps, int packedLight) {
        final float turn = 90.0f * snap(anim.isolate);
        ps.pushPose();
        ps.translate(0.0f, ISO_Y * PX, ISO_Z * PX);
        ps.mulPose(Axis.XP.rotationDegrees(turn));
        ps.translate(0.0f, -ISO_Y * PX, -ISO_Z * PX);

        box(vc, ps, ISO_X + 0.50f, ISO_Y - 0.30f, ISO_Z - 0.30f,
                ISO_X + 1.05f, ISO_Y + 1.50f, ISO_Z + 0.30f,
                T_HANDLE, T_HANDLE, T_HANDLE, T_HANDLE, 0xFFFFFFFF, packedLight);
        box(vc, ps, ISO_X + 0.40f, ISO_Y + 1.50f, ISO_Z - 0.42f,
                ISO_X + 1.15f, ISO_Y + 1.85f, ISO_Z + 0.42f,
                T_HANDLE, T_HANDLE, T_HANDLE, T_HANDLE, 0xFFFFFFFF, packedLight);
        ps.popPose();
    }

    private static void renderScreen(DiagnosticJunctionBE be, PoseStack ps, MultiBufferSource buffers) {
        final ResourceLocation texture = DiagnosticJunctionTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        plate(vc, ps, SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1, SCREEN_Z,
                new Tile(0.0f, 0.0f, 1.0f, 1.0f), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, true);
    }

    private static void renderLamps(DiagnosticJunctionBE be, boolean wire, VertexConsumer vc,
                                    PoseStack ps, int packedLight) {
        box(vc, ps, LAMP_X, LAMP_Y0, LAMP_Z - 0.10f, LAMP_X + LAMP_W, LAMP_Y1, LAMP_Z,
                T_LAMP, T_LAMP, T_LAMP, T_LAMP, colourOf(be), LightTexture.FULL_BRIGHT);
        if (!wire) {
            return;
        }
        for (int channel = 0; channel < DiagnosticJunctionStuds.CHANNELS; channel++) {
            final float x = LAMP_X + LAMP_STEP * (channel + 1);
            box(vc, ps, x, LAMP_Y0, LAMP_Z - 0.10f, x + LAMP_W, LAMP_Y1, LAMP_Z,
                    T_LAMP, T_LAMP, T_LAMP, T_LAMP,
                    be.isCutOff() ? 0xFF4A2523 : faultColour(be.channel(channel)),
                    LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderCordStubs(DiagnosticJunctionBE be, VertexConsumer vc, PoseStack ps,
                                        int packedLight) {
        if (be.getLevel() == null) {
            return;
        }
        for (Direction dir : new Direction[]{Direction.UP, Direction.DOWN}) {
            if (!(be.getLevel().getBlockEntity(be.getBlockPos().relative(dir)) instanceof CableBE cable)) {
                continue;
            }

            final int tint = CableRenderer.jacketColour(cable.colour());
            final float y0 = dir == Direction.UP ? 14.10f : 0.15f;
            final float y1 = dir == Direction.UP ? 15.85f : 1.90f;
            box(vc, ps, GLAND_X0, y0, GLAND_Z0, GLAND_X1, y1, GLAND_Z1,
                    T_CORD, T_CORD, T_CORD, T_CORD, tint, packedLight);
        }
    }

    private static void drawStudWires(DiagnosticJunctionBE be, Direction facing, PoseStack ps,
                                      MultiBufferSource buffers, int packedLight) {
        if (be.kind() != DiagnosticJunctionBlock.Kind.WIRE || be.wiresOnStuds() <= 0
                || be.getLevel() == null) {
            return;
        }
        final VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final org.joml.Matrix4f mat = ps.last().pose();
        final BlockPos origin = be.getBlockPos();
        for (int stud = 0; stud < DiagnosticJunctionStuds.COUNT; stud++) {
            final net.minecraft.world.phys.Vec3 from = DiagnosticJunctionStuds.attachPoint(facing, stud);
            for (DiagnosticJunctionBE.StudLink link : be.studLinks(stud)) {
                final net.minecraft.world.phys.Vec3 to = PowerConnectorRenderer.attachPoint(
                        be.getLevel(), link.to());
                PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link.to(),
                        (float) from.x, (float) from.y, (float) from.z,
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        packedLight, link.tier());
            }
        }
    }

    private static int colourOf(DiagnosticJunctionBE be) {
        return be.isCutOff() ? 0xFFFF5D49 : faultColour(be.fault());
    }

    private static int faultColour(DiagnosticJunctionBE.Fault fault) {
        return switch (fault) {
            case OVERLOAD, ROUTE_BLOCKED, ISOLATED -> 0xFFFF5D49;
            case BUFFER_FULL -> 0xFFF09A3C;
            case OPEN_CIRCUIT, SOURCE_STARVED, UNSTABLE -> 0xFFFFD56A;
            case BACKFEED -> 0xFF63D0E4;
            case NO_BUS -> 0xFF46564F;
            default -> 0xFF60FF8E;
        };
    }

    private static void box(VertexConsumer vc, PoseStack ps,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            Tile front, Tile back, Tile side, Tile cap, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        face(vc, ps, front, argb, light, Direction.SOUTH,
                ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, back, argb, light, Direction.NORTH,
                bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, side, argb, light, Direction.WEST,
                ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, side, argb, light, Direction.EAST,
                bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        face(vc, ps, cap, argb, light, Direction.UP,
                ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, cap, argb, light, Direction.DOWN,
                ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
    }

    private static void plate(VertexConsumer vc, PoseStack ps, float x0, float y0, float x1, float y1,
                              float z, Tile tile, int argb, int light, boolean forward) {
        final float ax = x0 * PX, ay = y0 * PX, bx = x1 * PX, by = y1 * PX, az = z * PX;
        if (forward) {
            face(vc, ps, tile, argb, light, Direction.SOUTH,
                    ax, ay, az, bx, ay, az, bx, by, az, ax, by, az);
        } else {
            face(vc, ps, tile, argb, light, Direction.NORTH,
                    bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        }
    }

    private static void face(VertexConsumer vc, PoseStack ps, Tile tile, int argb, int light,
                             Direction normal,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float nx = normal.getStepX(), ny = normal.getStepY(), nz = normal.getStepZ();
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{tile.u0(), tile.v1()}, {tile.u1(), tile.v1()},
                {tile.u1(), tile.v0()}, {tile.u0(), tile.v0()}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose.pose(), p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(96);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(DiagnosticJunctionBE be) {
        final net.minecraft.world.phys.AABB self = new net.minecraft.world.phys.AABB(be.getBlockPos());
        if (be.kind() != DiagnosticJunctionBlock.Kind.WIRE) {
            return self.inflate(1.0);
        }

        final PowerConnectorRenderer.WireBounds bounds =
                new PowerConnectorRenderer.WireBounds(be.getBlockPos());
        for (int stud = 0; stud < com.createstats.power.DiagnosticJunctionStuds.COUNT; stud++) {
            for (DiagnosticJunctionBE.StudLink link : be.studLinks(stud)) {
                bounds.include(link.to());
            }
        }
        return bounds.build();
    }

    @Override
    public boolean shouldRenderOffScreen(DiagnosticJunctionBE be) {
        return be.kind() == DiagnosticJunctionBlock.Kind.WIRE;
    }
}
