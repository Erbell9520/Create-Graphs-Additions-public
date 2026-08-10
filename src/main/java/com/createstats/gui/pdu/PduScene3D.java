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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/** [1] */
public final class PduScene3D {

    private PduScene3D() {
    }

    /** Sits over a shed machine. Red glass reads as "stopped" without hiding what the machine is. */
    private static final BlockState SHED_MARKER = Blocks.RED_STAINED_GLASS.defaultBlockState();
    /** Sits over the panel itself, so the one block a player is looking for is findable at a glance. */
    private static final BlockState PDU_MARKER = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    /** Degrees per tick of idle spin. Ten degrees a second: enough to read the shape, calm to sit next to. */
    private static final float AUTO_YAW_PER_TICK = 0.5f;

    /** The scene's own camera, owned by the screen so it survives a tab switch but not a reopen. */
    public static final class Camera {
        public float yaw = 35.0f;
        public float pitch = 28.0f;
        public float zoom = 1.0f;
        /** Set the first time the player grabs the view. Auto spin stops for good after that. */
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
            // Clamped rather than wrapped: past vertical the scene is upside down and a player has no
            // way to tell which way is up to get back.
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
            yaw = 35.0f;
            pitch = 28.0f;
            zoom = 1.0f;
            userTookOver = false;
            dragging = false;
        }
    }

    /**
     * Renders the branch.
     *
     * @param positions every block on the branch, in any order
     * @param devices   roster entries by position, for the shed markers
     * @param pduPos    the panel itself, marked so it can be found
     * @return false when there was nothing to draw, so the caller can show its own empty state
     */
    public static boolean render(GuiGraphics g, int x, int y, int w, int h, Level level,
                                 List<BlockPos> positions, Map<BlockPos, BranchDevice> devices,
                                 BlockPos pduPos, Camera cam, long gameTime, float partialTick) {
        if (level == null || positions.isEmpty() || w <= 8 || h <= 8) {
            return false;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (BlockPos p : positions) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        // Plus one because a block occupies its whole cell, so a single block spans one, not zero.
        final double cx = (minX + maxX + 1.0) * 0.5;
        final double cy = (minY + maxY + 1.0) * 0.5;
        final double cz = (minZ + maxZ + 1.0) * 0.5;
        final double extent = Math.max(1.0,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1.0);

        if (!cam.userTookOver) {
            // [2]
            cam.yaw = (((gameTime + partialTick) * AUTO_YAW_PER_TICK) % 360.0f + 360.0f) % 360.0f;
        }
        // Fill a bit over half the smaller dimension, so a rotating scene never clips its own corners
        // as it turns: the diagonal of a box is longer than its side.
        final double scale = Math.min(w, h) * 0.52 / extent * cam.zoom;

        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0f, y + h / 2.0f, 250.0f);
        // Negative Y so world up is screen up; Minecraft's GUI space runs the other way.
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
                continue;   // the branch moved since the last sync; skip rather than draw a hole
            }
            pose.pushPose();
            // Centroid subtracted in double precision, before the value ever reaches the float matrix.
            pose.translate(p.getX() - cx, p.getY() - cy, p.getZ() - cz);
            renderState(brd, state, pose, buffer);
            renderBlockEntity(level, p, pose, buffer, partialTick);
            final BranchDevice device = devices.get(p);
            if (device != null && (device.shed() || device.lock() == com.createstats.power.FeLock.FORCE_OFF)) {
                renderState(brd, SHED_MARKER, pose, buffer);
            } else if (p.equals(pduPos)) {
                renderState(brd, PDU_MARKER, pose, buffer);
            }
            pose.popPose();
        }

        buffer.endBatch();
        pose.popPose();
        g.disableScissor();
        // Hand the GUI back the state it expects, or every 2D draw after this one is lit and depth
        // tested and comes out wrong.
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();
        return true;
    }

    /** [3] */
    private static void renderBlockEntity(Level level, BlockPos pos, PoseStack pose,
                                          MultiBufferSource.BufferSource buffer, float partialTick) {
        final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            final net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                    net.minecraft.world.level.block.entity.BlockEntity> renderer =
                    (net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                            net.minecraft.world.level.block.entity.BlockEntity>)
                            Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer != null) {
                renderer.render(be, partialTick, pose, buffer, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
            }
        } catch (Throwable ignored) {
            // [4]
        }
    }

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
