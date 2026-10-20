package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

// real blocks. three shaded rhombuses per block and you couldn't tell a connector from a range
public final class PduScene3D {
    private PduScene3D() {
    }

    private static final BlockState SHED_MARKER = Blocks.RED_STAINED_GLASS.defaultBlockState();

    private static final BlockState PDU_MARKER = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    private static final float AUTO_YAW_PER_TICK = 0.5f;

    public static final class Camera {
        public float yaw = PduSceneFrame.START_YAW;
        public float pitch = PduSceneFrame.START_PITCH;
        public float zoom = 1.0f;

        public boolean userTookOver;
        public boolean dragging;
        private double lastDragX;
        private double lastDragY;

        public void beginDrag(double mouseX, double mouseY) {
            dragging = true;
            userTookOver = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
        }

        public void drag(double mouseX, double mouseY) {
            if (!dragging) {
                return;
            }
            yaw += (float) (mouseX - lastDragX) * 0.6f;

            pitch = Math.max(-89.0f, Math.min(89.0f, pitch + (float) (mouseY - lastDragY) * 0.5f));
            lastDragX = mouseX;
            lastDragY = mouseY;
        }

        public void endDrag() {
            dragging = false;
        }

        public void zoomBy(double amount) {
            userTookOver = true;
            zoom = (float) Math.max(0.35, Math.min(4.0, zoom * (amount > 0 ? 1.15 : 1.0 / 1.15)));
        }

        public void reset() {
            yaw = PduSceneFrame.START_YAW;
            pitch = PduSceneFrame.START_PITCH;
            zoom = 1.0f;
            userTookOver = false;
            dragging = false;
        }
    }

    public static boolean render(GuiGraphics g, int x, int y, int w, int h, Level level,
                                 List<BlockPos> positions, Map<BlockPos, BranchDevice> devices,
                                 BlockPos pduPos, Camera cam, long gameTime, float partialTick) {
        if (level == null || positions.isEmpty() || w <= 8 || h <= 8) {
            return false;
        }
        if (!cam.userTookOver) {
            final double spun = com.createstats.client.AnimationClock.time(gameTime, partialTick) * AUTO_YAW_PER_TICK;
            cam.yaw = (float) ((spun % 360.0 + 360.0) % 360.0);
        }
        return draw(g, x, y, w, h, level, positions, devices, pduPos, cam, partialTick);
    }

    public static boolean renderStill(GuiGraphics g, int x, int y, int w, int h, BlockGetter world,
                                      List<BlockPos> positions, Map<BlockPos, BranchDevice> devices,
                                      BlockPos pduPos, Camera cam) {
        if (world == null || positions.isEmpty() || w <= 8 || h <= 8) {
            return false;
        }
        return draw(g, x, y, w, h, world, positions, devices, pduPos, cam, 0.0f);
    }

    private static boolean draw(GuiGraphics g, int x, int y, int w, int h, BlockGetter level,
                                List<BlockPos> positions, Map<BlockPos, BranchDevice> devices,
                                BlockPos pduPos, Camera cam, float partialTick) {
        final PduSceneFrame.Frame frame = PduSceneFrame.of(positions);
        final double cx = frame.cx();
        final double cy = frame.cy();
        final double cz = frame.cz();

        final double scale = frame.scale(w, h, cam.zoom);

        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0f, y + h / 2.0f, 250.0f);

        pose.scale((float) scale, (float) -scale, (float) scale);
        pose.mulPose(Axis.XP.rotationDegrees(cam.pitch));
        pose.mulPose(Axis.YP.rotationDegrees(cam.yaw));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        final MultiBufferSource.BufferSource buffer = g.bufferSource();

        for (BlockPos p : positions) {
            final BlockState state = level.getBlockState(p);
            if (state.isAir()) {
                continue;
            }
            pose.pushPose();

            pose.translate(p.getX() - cx, p.getY() - cy, p.getZ() - cz);
            renderState(brd, state, pose, buffer);
            renderBlockEntity(level, p, pose, buffer, partialTick);
            final BranchDevice device = devices.get(p);
            if (device != null && device.isDark()) {
                renderState(brd, SHED_MARKER, pose, buffer);
            } else if (p.equals(pduPos)) {
                renderState(brd, PDU_MARKER, pose, buffer);
            }
            pose.popPose();
        }

        buffer.endBatch();
        pose.popPose();
        g.disableScissor();

        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();
        return true;
    }

    private static void renderBlockEntity(BlockGetter level, BlockPos pos, PoseStack pose,
                                          MultiBufferSource.BufferSource buffer, float partialTick) {
        final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }

        final PoseStack isolated = new PoseStack();
        isolated.last().pose().set(pose.last().pose());
        isolated.last().normal().set(pose.last().normal());
        try {
            @SuppressWarnings("unchecked")
            final net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                    net.minecraft.world.level.block.entity.BlockEntity> renderer =
                    (net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                            net.minecraft.world.level.block.entity.BlockEntity>)
                            Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer != null) {
                renderer.render(be, partialTick, isolated, buffer, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
            }
        } catch (Throwable failure) {
            if (WARNED.add(be.getType())) {
                LOGGER.warn("Block entity renderer for {} failed inside the PDU flow scene; "
                        + "that block will draw without its custom detail", be.getType(), failure);
            }
        }
    }

    private static final java.util.Set<net.minecraft.world.level.block.entity.BlockEntityType<?>> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static void renderState(BlockRenderDispatcher brd, BlockState state, PoseStack pose,
                                    MultiBufferSource.BufferSource buffer) {
        final BakedModel model = brd.getBlockModel(state);
        final RenderType worldType = ItemBlockRenderTypes.getRenderType(state, false);
        final RenderType sheet = worldType == RenderType.translucent()
                ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet();
        brd.getModelRenderer().renderModel(pose.last(), buffer.getBuffer(sheet), state, model,
                1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
}
