package com.createstats.renderer;

import com.createstats.block.ProductionDetectorBlock;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.blockentity.inventory.PanelGeometry;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.FeConsumer;
import com.createstats.power.client.FePowerVisuals;
import com.createstats.renderer.ProductionDetectorTextureManager.Entry;
import com.createstats.renderer.ProductionDetectorTextureManager.Mode;
import com.createstats.renderer.inventory.FacePainter;
import com.createstats.renderer.inventory.InventoryRenderTypes;
import com.createstats.renderer.inventory.LampPainter;
import com.createstats.renderer.inventory.PaperPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ProductionDetectorRenderer implements BlockEntityRenderer<ProductionDetectorBE> {
    private static final double NEAR = 24.0;
    private static final double FAR = 48.0;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private static final float LIFT_SPRITES = 1.6f;
    private static final float LIFT_ICONS = 3.2f;
    private static final float LIFT_OVER = 5.6f;

    private static final double PANEL_NY = Math.sin(Math.toRadians(-PanelGeometry.TILT_DEGREES));
    private static final double PANEL_NZ = Math.cos(Math.toRadians(-PanelGeometry.TILT_DEGREES));

    private static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath("create_stats",
            "block/production_detector_button");
    private static final ResourceLocation BELT = ResourceLocation.fromNamespaceAndPath("create", "block/belt");

    private static final int PEN_ARM = 0xD6B25C;
    private static final int PEN_NIB = 0xB02834;
    private static final int NEEDLE = 0x100E0C;
    private static final int NEEDLE_TIP = 0xECE8DE;
    private static final int JEWEL_LIT = 0xFF4632;
    private static final int JEWEL_DARK = 0x46120E;
    private static final int LENS_GREEN = 0x6EF08C;
    private static final int LENS_BLINK = 0x96FFBE;
    private static final int LENS_AMBER = 0xFFB040;
    private static final int LENS_DARK = 0x181A1C;

    private static final float PEN_TICKS = 10.0f;
    private static final float NEEDLE_TICKS = 6.0f;
    private static final float BLINK_TICKS = 4.0f;
    private static final float PRESS_TICKS = 4.0f;
    private static final float PRESS_DEPTH = 0.3f;

    private static final int PAPER_TOP_ROW = 6;
    private static final int PAPER_BOTTOM_ROW = ProductionDetectorTextureManager.PAPER_H - 17;

    public ProductionDetectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    private record Icon(ItemStack stack, float x, float y, float size) {
    }

    @Override
    public void render(ProductionDetectorBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight,
                       int packedOverlay) {
        final Level level = be.getLevel();
        final BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof ProductionDetectorBlock)
                || state.getValue(ProductionDetectorBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        final BlockPos pos = be.getBlockPos();
        final int upperLight = LevelRenderer.getLightColor(level, pos.above());
        // create only draws filter slots from its own renderer, so ask. value box scale trap again

        FilteringRenderer.renderOnBlockEntity(be, partialTick, ps, buffers, packedLight, packedOverlay);
        AntennaRendererHelper.render(be, ps, buffers, upperLight, packedOverlay);

        final Direction facing = state.getValue(ProductionDetectorBlock.FACING);

        final double now = level.getGameTime() + (double) partialTick;
        final int band = band(be, pos);

        final FeConsumer fe = be.feConsumer();

        final float brightness = FePowerVisuals.screenBrightness(pos, fe);
        final boolean dead = FePowerVisuals.isDead(fe);
        final boolean booting = FePowerVisuals.isBooting(pos, fe);
        final InventoryFace face = be.clientFace();
        final Mode mode = dead ? Mode.DEAD : booting ? Mode.WARMING : Mode.LIVE;
        final Entry entry = ProductionDetectorTextureManager.glass(pos, level, face, mode);
        final boolean live = mode == Mode.LIVE && face != null;

        ps.pushPose();
        ps.translate(0.5f, 0.0f, 0.5f);
        ps.mulPose(Axis.YP.rotationDegrees(-PanelGeometry.turnDegrees(facing)));
        ps.translate(-0.5f, 0.0f, -0.5f);
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        final Matrix4f model = new Matrix4f(ps.last().pose());
        ps.translate(8.0, PanelGeometry.PIVOT_Y, PanelGeometry.PIVOT_Z);
        ps.mulPose(Axis.XP.rotationDegrees((float) PanelGeometry.TILT_DEGREES));
        ps.translate(-8.0, -PanelGeometry.PIVOT_Y, -PanelGeometry.PIVOT_Z);
        final Matrix4f panel = new Matrix4f(ps.last().pose());
        ps.translate(PanelGeometry.GLASS_X0, PanelGeometry.GLASS_Y1, PanelGeometry.FACE_Z);
        ps.scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
        final Matrix4f glass = new Matrix4f(ps.last().pose());
        final PoseStack.Pose glassPose = ps.last().copy();
        ps.popPose();

        final float panelShade = shade(level, facing, 0.0, PANEL_NY, PANEL_NZ);
        final float frontShade = shade(level, facing, 0.0, 0.0, 1.0);
        final float glassTint = Mth.clamp(brightness, 0.0f, 1.0f);
        final List<Icon> icons = new ArrayList<>();

        final VertexConsumer atlas = buffers.getBuffer(InventoryRenderTypes.ATLAS);
        testCap(atlas, panel, level, facing, be, partialTick, upperLight);
        if (band == 0 && live && entry.layers() != null) {
            final FacePainter.Layers layers = entry.layers();
            if (layers.fluid != null && !face.fluid.isEmpty()) {
                fluid(atlas, glass, face, layers.fluid, glassTint);
            }
            if (layers.belt != null) {
                belt(atlas, glass, layers.belt, face, now, glassTint);
                beltItems(level, face, layers.belt, partialTick, facing, icons);
            }
            for (FacePainter.Icon i : layers.icons) {
                icons.add(new Icon(i.stack(), i.x(), i.y(), i.size()));
            }
        }

        final VertexConsumer g = buffers.getBuffer(entry.glassType());
        final int tint = grey(glassTint);
        quad(g, glass, 0, 0, FacePainter.W, FacePainter.H, 0.0f, 0.0f, 0.0f, 1.0f, 0.5f, tint, FULL_BRIGHT);
        if (band == 0 && live) {
            quad(g, glass, 0, 0, FacePainter.W, FacePainter.H, LIFT_OVER, 0.0f, 0.5f, 1.0f, 1.0f, tint, FULL_BRIGHT);
        }

        final VertexConsumer lamps = buffers.getBuffer(ProductionDetectorTextureManager.lamps());

        final boolean flashOn = (now % 10.0) < 5.0;
        final boolean lampTest = !dead && (booting || (face != null && face.lampTestUntil > level.getGameTime()));
        for (int i = 0; i < 4; i++) {
            final int bit = 1 << i;
            boolean lit;
            if (dead) {
                lit = false;
            } else if (lampTest) {
                lit = true;
            } else if (bit == InventoryFace.LAMP_POWER) {
                lit = true;
            } else {
                lit = face != null && face.lit(bit) && (!face.flashing(bit) || flashOn);
            }
            final float[] uv = LampPainter.uv(i, lit);
            final float x0 = (float) (PanelGeometry.LAMP_X0 + i * PanelGeometry.LAMP_W);
            final float x1 = (float) (x0 + PanelGeometry.LAMP_W);
            final int colour = lit ? grey(glassTint) : grey(panelShade);
            quadUp(lamps, panel, x0, (float) PanelGeometry.LAMP_Y0, x1, (float) PanelGeometry.LAMP_Y1,
                    (float) PanelGeometry.FACE_Z, uv[0], uv[1], uv[2], uv[3], colour, lit ? FULL_BRIGHT : upperLight);
        }

        if (band <= 1) {
            final int scale = face != null ? face.chartScale : 1;
            final float low = face != null ? face.chartLow : Float.NaN;
            final float high = face != null ? face.chartHigh : Float.NaN;
            final VertexConsumer paper = buffers.getBuffer(
                    ProductionDetectorTextureManager.paper(entry, be.clientChart(), scale, low, high));
            quadUp(paper, model, (float) PanelGeometry.PAPER_X0, (float) PanelGeometry.PAPER_Y0,
                    (float) PanelGeometry.PAPER_X1, (float) PanelGeometry.PAPER_Y1, (float) PanelGeometry.PAPER_Z,
                    0.0f, 0.0f, ProductionDetectorTextureManager.PAPER_U1, ProductionDetectorTextureManager.PAPER_V1,
                    grey(frontShade), packedLight);
        }

        final VertexConsumer flat = buffers.getBuffer(InventoryRenderTypes.FLAT);
        if (band == 0) {
            final int scale = face != null ? face.chartScale : 1;
            final float target = PaperPainter.penFraction(be.clientChart(), scale);

            final float penAt = dead && be.anim.pen.isSet() ? be.anim.pen.value() : be.anim.pen.at(target, now, PEN_TICKS);
            pen(flat, model, penAt, frontShade, packedLight);
            final float lowAt = be.anim.low.at(face != null ? face.lowNeedle : 0.0f, now, NEEDLE_TICKS);
            final float highAt = be.anim.high.at(face != null ? face.highNeedle : 0.0f, now, NEEDLE_TICKS);
            needle(flat, model, lowAt, false, shade(level, facing, -1.0, 0.0, 0.0), packedLight);
            needle(flat, model, highAt, true, shade(level, facing, 1.0, 0.0, 0.0), packedLight);
        }
        final boolean lowJewel = !dead && face != null && face.lowRelay;
        final boolean highJewel = !dead && face != null && face.highRelay;
        jewel(flat, model, false, lowJewel, glassTint, shade(level, facing, -1.0, 0.0, 0.0), packedLight);
        jewel(flat, model, true, highJewel, glassTint, shade(level, facing, 1.0, 0.0, 0.0), packedLight);
        lens(flat, model, be, face, dead, now, glassTint, shade(level, facing, 0.0, 0.0, -1.0), packedLight);

        if (!icons.isEmpty()) {
            drawIcons(ps, glassPose, icons, buffers, level, glassTint);
        }
    }

    private static int band(ProductionDetectorBE be, BlockPos pos) {
        final Vec3 at = SableBridge.worldPointOf(be, Vec3.atCenterOf(pos.above()));
        if (at == null) {
            return 0;
        }
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final double d2 = camera.distanceToSqr(at);
        return d2 < NEAR * NEAR ? 0 : d2 < FAR * FAR ? 1 : 2;
    }

    private static float shade(Level level, Direction facing, double x, double y, double z) {
        final Vec3 d = PanelGeometry.dirToWorld(facing, x, y, z);
        return level.getShade((float) d.x, (float) d.y, (float) d.z, true);
    }

    private static void fluid(VertexConsumer vc, Matrix4f glass, InventoryFace face, int[] rect, float tint) {
        final IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(face.fluid.getFluid());
        final ResourceLocation still = ext.getStillTexture(face.fluid);
        if (still == null) {
            return;
        }
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);

        final int colour = scaleRgb(ext.getTintColor(face.fluid), tint);
        final int x0 = rect[0];
        final int y0 = rect[1];
        final int x1 = rect[2];
        final int y1 = rect[3];
        for (int top = y1; top > y0; top -= 16) {
            final int ty0 = Math.max(y0, top - 16);
            final float v0 = sprite.getV(1.0f - (top - ty0) / 16.0f);
            final float v1 = sprite.getV(1.0f);
            for (int tx0 = x0; tx0 < x1; tx0 += 16) {
                final int tx1 = Math.min(x1, tx0 + 16);
                quad(vc, glass, tx0, ty0, tx1, top, LIFT_SPRITES, sprite.getU0(), v0,
                        sprite.getU((tx1 - tx0) / 16.0f), v1, colour, FULL_BRIGHT);
            }
        }
    }

    private static void belt(VertexConsumer vc, Matrix4f glass, int[] rect, InventoryFace face, double now, float tint) {
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(BELT);
        final int x0 = rect[0];
        final int y0 = rect[1];
        final int x1 = rect[2];
        final int y1 = rect[3];
        final double texelsPerBlock = (x1 - x0) / 6.0;
        final double scroll = face.beltDirection == 0 ? 0.0
                : Math.abs(face.beltSpeed) * texelsPerBlock * now * face.beltDirection;
        final float uSpan = Math.min(16.0f, (y1 - y0) / 2.0f);
        final int colour = grey(tint);
        float x = x0;

        float v = (float) (((-scroll / 2.0) % 16.0 + 16.0) % 16.0);
        if (v >= 16.0f) {
            v = 0.0f;
        }
        while (x < x1 - 1e-3f) {
            final float run = Math.min((16.0f - v) * 2.0f, x1 - x);
            final float vEnd = v + run / 2.0f;

            vertex(vc, glass, x, y0, LIFT_SPRITES, sprite.getU(0.0f), sprite.getV(v / 16.0f), colour, FULL_BRIGHT);
            vertex(vc, glass, x + run, y0, LIFT_SPRITES, sprite.getU(0.0f), sprite.getV(vEnd / 16.0f), colour, FULL_BRIGHT);
            vertex(vc, glass, x + run, y1, LIFT_SPRITES, sprite.getU(uSpan / 16.0f), sprite.getV(vEnd / 16.0f), colour,
                    FULL_BRIGHT);
            vertex(vc, glass, x, y1, LIFT_SPRITES, sprite.getU(uSpan / 16.0f), sprite.getV(v / 16.0f), colour, FULL_BRIGHT);
            x += run;
            v = 0.0f;
        }
    }

    private static void beltItems(Level level, InventoryFace face, int[] rect, float partialTick, Direction facing,
                                  List<Icon> out) {
        final int x0 = rect[0];
        final int y0 = rect[1];
        final int x1 = rect[2];
        if (face.source == InventoryFace.SOURCE_BEHIND && face.sourcePos != 0L) {
            final BlockPos src = BlockPos.of(face.sourcePos);
            if (level.isLoaded(src) && level.getBlockEntity(src) instanceof BeltBlockEntity segment) {
                final BeltBlockEntity controller = segment.getControllerBE() != null ? segment.getControllerBE() : segment;
                final BeltInventory inventory = controller.getInventory();
                if (inventory != null) {
                    final Vec3i chain = segment.getBeltChainDirection();
                    final Direction right = facing.getClockWise();
                    final int sign = chain.getX() * right.getStepX() + chain.getZ() * right.getStepZ() < 0 ? -1 : 1;
                    final float gate = segment.index + 0.5f;
                    for (TransportedItemStack t : inventory.getTransportedItems()) {
                        if (t.stack.isEmpty()) {
                            continue;
                        }
                        final float p = Mth.lerp(partialTick, t.prevBeltPosition, t.beltPosition);
                        final float rel = (p - gate) * sign;
                        if (Math.abs(rel) <= 3.0f) {
                            out.add(new Icon(t.stack, x0 + (rel + 3.0f) / 6.0f * (x1 - x0 - 16), y0 + 6, 16));
                        }
                    }
                    return;
                }
            }
        }
        for (InventoryFace.BeltItem b : face.beltItems) {
            out.add(new Icon(b.icon(), x0 + b.position() * (x1 - x0 - 16), y0 + 6, 16));
        }
    }

    private static void drawIcons(PoseStack ps, PoseStack.Pose glassPose, List<Icon> icons, MultiBufferSource buffers,
                                  Level level, float tint) {
        final ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        final int l = Mth.clamp(Math.round(15.0f * tint), 0, 15);
        final int light = LightTexture.pack(l, l);
        ps.pushPose();
        ps.last().pose().set(glassPose.pose());
        ps.last().normal().set(glassPose.normal());
        for (Icon icon : icons) {
            if (icon.stack() == null || icon.stack().isEmpty()) {
                continue;
            }
            final boolean block = renderer.getModel(icon.stack(), level, null, 0).isGui3d();
            final float s = icon.size() * (block ? 1.6f : 1.0f);
            ps.pushPose();
            ps.translate(icon.x() + icon.size() * 0.5f, icon.y() + icon.size() * 0.5f, LIFT_ICONS);
            ps.scale(s, -s, Math.max(0.01f, s * 0.05f));
            ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            renderer.renderStatic(icon.stack(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers,
                    level, 0);
            ps.popPose();
        }
        ps.popPose();
    }

    private static void testCap(VertexConsumer vc, Matrix4f panel, Level level, Direction facing, ProductionDetectorBE be,
                                float partialTick, int light) {
        float depth = 0.0f;
        if (be.testPressedAt != Long.MIN_VALUE) {
            final float t = (level.getGameTime() - be.testPressedAt) + partialTick;
            if (t >= 0.0f && t < PRESS_TICKS) {
                depth = PRESS_DEPTH;
            } else if (t >= PRESS_TICKS && t < PRESS_TICKS + 2.0f) {
                depth = PRESS_DEPTH * (1.0f - (t - PRESS_TICKS) / 2.0f);
            }
        }
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(BUTTON);
        final float x0 = 13.65f;
        final float x1 = 14.15f;
        final float y0 = 30.4f;
        final float y1 = 31.2f;
        final float z0 = 15.75f - depth;
        final float z1 = 16.25f - depth;
        final double c = PANEL_NZ;
        final double s = PANEL_NY;

        final int front = grey(shade(level, facing, 0.0, s, c));
        final int up = grey(shade(level, facing, 0.0, c, -s));
        final int down = grey(shade(level, facing, 0.0, -c, s));
        final int east = grey(shade(level, facing, 1.0, 0.0, 0.0));
        final int west = grey(shade(level, facing, -1.0, 0.0, 0.0));

        face(vc, panel, sprite, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 13.65f, 0.8f, 14.15f, 1.6f, front, light);

        face(vc, panel, sprite, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 0.0f, 0.8f, 0.5f, 1.6f, east, light);

        face(vc, panel, sprite, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, 15.5f, 0.8f, 16.0f, 1.6f, west, light);

        face(vc, panel, sprite, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 13.65f, 15.5f, 14.15f, 16.0f, up, light);
        face(vc, panel, sprite, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 13.65f, 15.5f, 14.15f, 16.0f, down, light);
    }

    private static void pen(VertexConsumer vc, Matrix4f model, float frac, float shade, int light) {
        final float h = ProductionDetectorTextureManager.PAPER_H;
        final float row = PAPER_BOTTOM_ROW - (PAPER_BOTTOM_ROW - PAPER_TOP_ROW) * Mth.clamp(frac, 0.0f, 1.0f);
        final float y = (float) (PanelGeometry.PAPER_Y1 - (row + 0.5f) / h * (PanelGeometry.PAPER_Y1 - PanelGeometry.PAPER_Y0));
        final float x1 = (float) PanelGeometry.PAPER_X1;
        final float z = (float) PanelGeometry.PAPER_Z + 0.15f;

        flatQuad(vc, model, x1 - 0.85f, y - 0.12f, x1, y + 0.12f, z, shadeRgb(PEN_ARM, shade), light);
        flatQuad(vc, model, x1 - 1.15f, y - 0.22f, x1 - 0.85f, y + 0.22f, z + 0.02f, shadeRgb(PEN_NIB, shade), light);
    }

    private static void needle(VertexConsumer vc, Matrix4f model, float frac, boolean high, float shade, int light) {
        final double length = 2.6;
        final double width = 0.5;
        final float x = high ? 15.2f + 0.12f : 0.8f - 0.12f;
        final double th = Math.toRadians(225.0 - 270.0 * Mth.clamp(frac, 0.0f, 1.0f));
        final double sx = Math.cos(th);
        final double sy = Math.sin(th);
        final double nx = -sy;
        final double ny = sx;
        final double[][] seg = {{0.0, length * 0.72}, {length * 0.72, length}};
        for (int k = 0; k < 2; k++) {
            final int rgb = shadeRgb(k == 0 ? NEEDLE : NEEDLE_TIP, shade);
            final float[] a = needlePoint(sx, sy, nx, ny, seg[k][0], -width / 2, high, x);
            final float[] b = needlePoint(sx, sy, nx, ny, seg[k][0], width / 2, high, x);
            final float[] c = needlePoint(sx, sy, nx, ny, seg[k][1], width / 2, high, x);
            final float[] d = needlePoint(sx, sy, nx, ny, seg[k][1], -width / 2, high, x);
            vertex(vc, model, b[0], b[1], b[2], 0.0f, 0.0f, rgb, light);
            vertex(vc, model, a[0], a[1], a[2], 0.0f, 1.0f, rgb, light);
            vertex(vc, model, d[0], d[1], d[2], 1.0f, 1.0f, rgb, light);
            vertex(vc, model, c[0], c[1], c[2], 1.0f, 0.0f, rgb, light);
        }
    }

    private static float[] needlePoint(double sx, double sy, double nx, double ny, double t, double w, boolean high, float x) {
        final double u = 8.0 + sx * t + nx * w;
        final double v = 8.0 + sy * t + ny * w;
        final double z = high ? 16.0 - u : u;
        return new float[]{x, (float) v, (float) z};
    }

    private static void jewel(VertexConsumer vc, Matrix4f model, boolean high, boolean lit, float tint, float shade,
                              int light) {
        final float x = high ? 13.45f : 2.55f;
        final float y0 = 12.2f;
        final float y1 = 13.4f;
        final float z0 = 7.6f;
        final float z1 = 8.4f;
        final int rgb = lit ? scaleRgb(JEWEL_LIT, tint) : shadeRgb(JEWEL_DARK, shade);
        final int l = lit ? FULL_BRIGHT : light;
        vertex(vc, model, x, y0, z0, 0.0f, 1.0f, rgb, l);
        vertex(vc, model, x, y0, z1, 1.0f, 1.0f, rgb, l);
        vertex(vc, model, x, y1, z1, 1.0f, 0.0f, rgb, l);
        vertex(vc, model, x, y1, z0, 0.0f, 0.0f, rgb, l);
    }

    private static void lens(VertexConsumer vc, Matrix4f model, ProductionDetectorBE be, InventoryFace face, boolean dead,
                             double now, float tint, float shade, int light) {
        int rgb = LENS_DARK;
        boolean lit = false;
        if (!dead && face != null) {
            if (face.contentSeq != be.anim.lastContentSeq) {
                if (be.anim.lastContentSeq != Integer.MIN_VALUE) {
                    be.anim.blinkUntil = now + BLINK_TICKS;
                }
                be.anim.lastContentSeq = face.contentSeq;
            }
            if (face.lens == InventoryFace.LENS_READING) {
                rgb = now < be.anim.blinkUntil ? LENS_BLINK : LENS_GREEN;
                lit = true;
            } else if (face.lens == InventoryFace.LENS_CANT_READ) {
                rgb = LENS_AMBER;
                lit = true;
            }
        }
        final int colour = lit ? scaleRgb(rgb, tint) : shadeRgb(rgb, shade);
        final int l = lit ? FULL_BRIGHT : light;
        final float x0 = 7.0f;
        final float y0 = 7.0f;
        final float x1 = 9.0f;
        final float y1 = 9.0f;
        final float z = 2.30f;
        vertex(vc, model, x1, y0, z, 0.0f, 1.0f, colour, l);
        vertex(vc, model, x0, y0, z, 1.0f, 1.0f, colour, l);
        vertex(vc, model, x0, y1, z, 1.0f, 0.0f, colour, l);
        vertex(vc, model, x1, y1, z, 0.0f, 0.0f, colour, l);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, float u0, float v0,
                             float u1, float v1, int argb, int light) {
        vertex(vc, m, x0, y0, z, u0, v0, argb, light);
        vertex(vc, m, x1, y0, z, u1, v0, argb, light);
        vertex(vc, m, x1, y1, z, u1, v1, argb, light);
        vertex(vc, m, x0, y1, z, u0, v1, argb, light);
    }

    private static void quadUp(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, float u0,
                               float v0, float u1, float v1, int argb, int light) {
        vertex(vc, m, x0, y1, z, u0, v0, argb, light);
        vertex(vc, m, x0, y0, z, u0, v1, argb, light);
        vertex(vc, m, x1, y0, z, u1, v1, argb, light);
        vertex(vc, m, x1, y1, z, u1, v0, argb, light);
    }

    private static void flatQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb,
                                 int light) {
        quadUp(vc, m, x0, y0, x1, y1, z, 0.0f, 0.0f, 1.0f, 1.0f, rgb, light);
    }

    private static void face(VertexConsumer vc, Matrix4f m, TextureAtlasSprite sprite, float ax, float ay, float az,
                             float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int argb, int light) {
        final float su0 = sprite.getU(u0 / 16.0f);
        final float su1 = sprite.getU(u1 / 16.0f);
        final float sv0 = sprite.getV(v0 / 16.0f);
        final float sv1 = sprite.getV(v1 / 16.0f);
        vertex(vc, m, ax, ay, az, su0, sv0, argb, light);
        vertex(vc, m, bx, by, bz, su1, sv0, argb, light);
        vertex(vc, m, cx, cy, cz, su1, sv1, argb, light);
        vertex(vc, m, dx, dy, dz, su0, sv1, argb, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int argb,
                               int light) {
        vc.addVertex(m, x, y, z)
                .setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) == 0 ? 255 : argb >>> 24)
                .setUv(u, v)
                .setLight(light);
    }

    private static int grey(float k) {
        final int c = Mth.clamp(Math.round(255.0f * k), 0, 255);
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    private static int scaleRgb(int rgb, float k) {
        final int r = Mth.clamp(Math.round(((rgb >> 16) & 255) * k), 0, 255);
        final int g = Mth.clamp(Math.round(((rgb >> 8) & 255) * k), 0, 255);
        final int b = Mth.clamp(Math.round((rgb & 255) * k), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int shadeRgb(int rgb, float shade) {
        return scaleRgb(rgb, shade);
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
