package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.client.ModPartialModels;
import com.createstats.driverhelmet.DriverHelmetBlock;
import com.createstats.driverhelmet.DriverHelmetItem;
import com.createstats.driverhelmet.HelmetGlowSync;
import com.createstats.driverhelmet.HelmetVisor;
import com.createstats.driverhelmet.HudTheme;
import com.createstats.driverhelmet.LedBar;
import com.createstats.driverhelmet.VisorMotion;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.data.ModelData;

public class DriverHelmetItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static DriverHelmetItemRenderer instance;

    private DriverHelmetItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static DriverHelmetItemRenderer get() {
        if (instance == null) {
            instance = new DriverHelmetItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers,
                             int light, int overlay) {
        final Minecraft mc = Minecraft.getInstance();
        final HelmetVisor visor = DriverHelmetItem.visor(stack);

        final double angle = mc.level == null
                ? VisorMotion.angleAt(visor.open(), VisorMotion.settleSeconds(visor.open()))
                : HelmetView.angle(visor);

        final int livery = HudTheme.of(visor.theme()).ordinal();
        final BlockState state = ModBlocks.DRIVER_HELMET.getDefaultState().setValue(DriverHelmetBlock.LIVERY, livery);
        final BlockRenderDispatcher blocks = mc.getBlockRenderer();
        final ModelBlockRenderer models = blocks.getModelRenderer();
        final VertexConsumer vc = buffers.getBuffer(NeoForgeRenderTypes.ITEM_UNSORTED_UNLIT_TRANSLUCENT.get());

        models.renderModel(ps.last(), vc, state, blocks.getBlockModel(state), 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, null);
        ps.pushPose();
        HelmetDraw.visorPose(ps, angle);
        models.renderModel(ps.last(), vc, state, ModPartialModels.DRIVER_HELMET_VISOR_LIVERIES[livery].get(), 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, null);
        if (context == ItemDisplayContext.HEAD && HudTheme.of(visor.theme()).glows()) {
            glow(ps, buffers, visor, angle, HudTheme.of(visor.theme()));
        }
        ps.popPose();

        if (context == ItemDisplayContext.HEAD) {
            final ResourceLocation skin = HelmetWearers.faceSkin();
            if (skin != null) {
                final VertexConsumer face = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
                eyeport(ps, face, FACE_U, FACE_Z, light, overlay);
                if (HelmetWearers.faceHatShown()) {
                    eyeport(ps, face, HAT_U, HAT_Z, light, overlay);
                }
            }
        }
    }

    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "textures/entity/driver_helmet_glow.png");

    private static final float GLOW_X0 = 4.2f;
    private static final float GLOW_X1 = 11.8f;
    private static final float GLOW_Y = 7.45f;
    private static final float GLOW_SIZE = 0.52f;

    private static final float GLOW_Z = -0.03f;

    private static final double GLOW_ANGLE = 12.0;

    private static void glow(PoseStack ps, MultiBufferSource buffers, HelmetVisor visor, double angle, HudTheme theme) {
        final LivingEntity wearer = HelmetWearers.wearer();
        if (!(wearer instanceof Player) || visor.open() || angle >= GLOW_ANGLE) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final double clock = HelmetView.now() / 20.0;
        final LedBar.Frame frame;
        if (wearer == mc.player) {
            frame = DriverHelmetHud.currentLeds(visor);
        } else {
            final HelmetGlowSync heard = HelmetGlowSync.of(wearer.getId());
            frame = heard == null ? LedBar.live(0.0, LedBar.Warning.NONE, clock) : heard.frame(clock);
        }

        final float fade = (float) Math.max(0.0, 1.0 - Math.max(0.0, angle) / GLOW_ANGLE);
        final VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        final PoseStack.Pose pose = ps.last();
        final float pitch = (GLOW_X1 - GLOW_X0) / LedBar.LEDS;
        for (int i = 0; i < LedBar.LEDS; i++) {
            final int role = frame.role()[i];
            final boolean lit = role != LedBar.OFF && frame.level()[i] > 0.0f;
            final int rgb = colour(lit ? role : LedBar.segment(i), theme);
            final float level = (lit ? frame.level()[i] : 0.10f) * fade;
            final float cx = GLOW_X0 + (i + 0.5f) * pitch;
            dot(vc, pose, cx, GLOW_Y, GLOW_SIZE, rgb, level);
        }
    }

    private static int colour(int role, HudTheme t) {
        return switch (role) {
            case LedBar.MID -> t.shiftMid;
            case LedBar.HIGH -> t.shiftHigh;
            case LedBar.BAD -> t.bad;
            case LedBar.COOL -> t.cool;
            default -> t.shiftLow;
        };
    }

    private static void dot(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float size, int rgb, float level) {
        final int r = (int) (((rgb >> 16) & 0xFF) * level);
        final int g = (int) (((rgb >> 8) & 0xFF) * level);
        final int b = (int) ((rgb & 0xFF) * level);
        final float h = size / 2.0f;
        final float z = GLOW_Z / 16.0f;
        glowCorner(vc, pose, (cx + h) / 16.0f, (cy + h) / 16.0f, z, 0.0f, 0.0f, r, g, b);
        glowCorner(vc, pose, (cx + h) / 16.0f, (cy - h) / 16.0f, z, 0.0f, 1.0f, r, g, b);
        glowCorner(vc, pose, (cx - h) / 16.0f, (cy - h) / 16.0f, z, 1.0f, 1.0f, r, g, b);
        glowCorner(vc, pose, (cx - h) / 16.0f, (cy + h) / 16.0f, z, 1.0f, 0.0f, r, g, b);
    }

    private static void glowCorner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                                   int r, int g, int b) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, -1.0f);
    }

    private static final float FACE_U = 8.0f;
    private static final float HAT_U = 40.0f;

    private static final float FACE_Z = 2.9f;
    private static final float HAT_Z = 2.8f;

    private static final float PORT_X0 = 3.95f;
    private static final float PORT_X1 = 12.05f;
    private static final float PORT_Y0 = 4.95f;
    private static final float PORT_Y1 = 8.05f;

    private static final int SHADE = 215;

    private static void eyeport(PoseStack ps, VertexConsumer vc, float u0, float zModel, int light, int overlay) {
        final PoseStack.Pose pose = ps.last();
        final float z = zModel / 16.0f;
        final float uLeft = (u0 + (12.0f - PORT_X1)) / 64.0f;
        final float uRight = (u0 + (12.0f - PORT_X0)) / 64.0f;
        final float vTop = (8.0f + (11.0f - PORT_Y1)) / 64.0f;
        final float vBottom = (8.0f + (11.0f - PORT_Y0)) / 64.0f;
        corner(vc, pose, PORT_X1, PORT_Y1, z, uLeft, vTop, light, overlay);
        corner(vc, pose, PORT_X1, PORT_Y0, z, uLeft, vBottom, light, overlay);
        corner(vc, pose, PORT_X0, PORT_Y0, z, uRight, vBottom, light, overlay);
        corner(vc, pose, PORT_X0, PORT_Y1, z, uRight, vTop, light, overlay);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int light, int overlay) {
        vc.addVertex(pose, x / 16.0f, y / 16.0f, z)
                .setColor(SHADE, SHADE, SHADE, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0.0f, 0.0f, -1.0f);
    }
}
