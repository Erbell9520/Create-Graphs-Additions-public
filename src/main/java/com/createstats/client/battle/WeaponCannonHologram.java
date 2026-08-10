package com.createstats.client.battle;

import com.createstats.battle.DamageObserverVoxelMap;
import com.createstats.compat.CreateBigCannonsCompat;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class WeaponCannonHologram {

    private static final BlockState MISSING_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState NEW_OVERLAY_STATE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    private static final int BG = 0xF00A0E1A;
    private static final int FRAME = 0xFF2C6E7A;
    private static final int ACCENT = 0xFF4AD9FF;
    private static final int LABEL = 0xFF7FE3F2;
    private static final int HINT = 0xFF5A6E84;

    // interactive camera (shared; a single weapon hologram is shown at a time)
    private static float camYaw = 0f;
    private static float camPitch = 22f;
    private static float camZoom = 1f;
    private static boolean userInteracted = false;
    private static boolean expanded = false;
    private static int rectX, rectY, rectW, rectH;

    private WeaponCannonHologram() {
    }

    public static boolean isExpanded() {
        return expanded;
    }

    public static boolean isInside(double mx, double my) {
        return mx >= rectX && mx < rectX + rectW && my >= rectY && my < rectY + rectH;
    }

    /** Toggles expand/collapse when the corner icon is clicked. Returns true if it consumed the click. */
    public static boolean handleClick(double mx, double my) {
        int ix = rectX + rectW - 16;
        int iy = rectY + 4;
        if (mx >= ix && mx < ix + 12 && my >= iy && my < iy + 12) {
            expanded = !expanded;
            return true;
        }
        return false;
    }

    public static boolean handleDrag(double mx, double my, double dragX, double dragY) {
        if (!isInside(mx, my)) {
            return false;
        }
        camYaw = (float) ((camYaw + dragX) % 360.0);
        camPitch = Math.max(-89f, Math.min(89f, camPitch - (float) dragY));
        userInteracted = true;
        return true;
    }

    public static boolean handleScroll(double mx, double my, double scrollY) {
        if (!isInside(mx, my)) {
            return false;
        }
        camZoom = Math.max(0.4f, Math.min(4.0f, camZoom * (scrollY > 0 ? 1.12f : 0.89f)));
        return true;
    }

    private static float currentYaw(float time) {
        return userInteracted ? (((camYaw % 360f) + 360f) % 360f) : (time * 0.6f) % 360f;
    }

    /**
     * Renders the rotating cannon hologram into the given rect.
     *
     * @param time frame-rate-independent animation clock (game ticks + partial tick).
     * @return true when live voxel data was drawn, false when a placeholder was shown.
     */
    public static boolean render(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 BlockPos observerPos, float time) {
        if (observerPos == null || w <= 12 || h <= 12) {
            return false;
        }
        rectX = x;
        rectY = y;
        rectW = w;
        rectH = h;

        // Frame + glassy background so the inset reads as a holographic projector well.
        g.fill(x, y, x + w, y + h, BG);
        drawFrame(g, x, y, w, h, FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33000000 | (ACCENT & 0x00FFFFFF));
        g.drawString(font, Component.literal("HOLOGRAM"), x + 6, y + 5, LABEL, false);

        ClientObserverVoxelCache.ensureFresh(observerPos);
        final DamageObserverVoxelMap map = ClientObserverVoxelCache.get(observerPos);
        if (map == null) {
            String hint = "Acquiring structure...";
            g.drawString(font, Component.literal(hint), x + (w - font.width(hint)) / 2, y + h / 2 - 4, HINT, false);
            scanline(g, x, y, w, h, time);
            drawHud(g, font, x, y, w, h);
            return false;
        }

        // [2]
        if (map.hasCannonModel()) {
            boolean rendered = renderCannonModel(g, x, y, w, h, map, time);
            drawHud(g, font, x, y, w, h);
            return rendered;
        }

        // [3]
        final int volume = map.dx * map.dy * map.dz;
        boolean hasCbc = false;
        for (int i = 0; i < volume && !hasCbc; i++) {
            int idx = map.paletteIndices[i];
            if (idx != 0 && CreateBigCannonsCompat.isCreateBigCannonsBlock(map.palette[idx])) {
                hasCbc = true;
            }
        }

        // build a render mask + content bounding box over only the voxels we will draw.
        final boolean[] show = new boolean[volume];
        int lminX = Integer.MAX_VALUE, lminY = Integer.MAX_VALUE, lminZ = Integer.MAX_VALUE;
        int lmaxX = Integer.MIN_VALUE, lmaxY = Integer.MIN_VALUE, lmaxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (int lz = 0; lz < map.dz; lz++) {
            for (int ly = 0; ly < map.dy; ly++) {
                for (int lx = 0; lx < map.dx; lx++) {
                    int flat = map.indexFor(lx, ly, lz);
                    int paletteIdx = map.paletteIndices[flat];
                    boolean inBaseline = map.baselineMask.get(flat);
                    if (paletteIdx == 0 && !inBaseline) {
                        continue; // truly empty
                    }
                    boolean visible;
                    if (!hasCbc) {
                        visible = true;
                    } else if (paletteIdx == 0) {
                        visible = true; // missing baseline cell -> damage marker
                    } else {
                        visible = CreateBigCannonsCompat.isCreateBigCannonsBlock(map.palette[paletteIdx]);
                    }
                    if (!visible) {
                        continue;
                    }
                    show[flat] = true;
                    any = true;
                    if (lx < lminX) lminX = lx;
                    if (ly < lminY) lminY = ly;
                    if (lz < lminZ) lminZ = lz;
                    if (lx > lmaxX) lmaxX = lx;
                    if (ly > lmaxY) lmaxY = ly;
                    if (lz > lmaxZ) lmaxZ = lz;
                }
            }
        }
        if (!any) {
            String hint = "No structure in view";
            g.drawString(font, Component.literal(hint), x + (w - font.width(hint)) / 2, y + h / 2 - 4, HINT, false);
            scanline(g, x, y, w, h, time);
            drawHud(g, font, x, y, w, h);
            return true;
        }

        final int minX = map.minCorner.getX();
        final int minY = map.minCorner.getY();
        final int minZ = map.minCorner.getZ();
        final double cx = minX + (lminX + lmaxX + 1) * 0.5;
        final double cy = minY + (lminY + lmaxY + 1) * 0.5;
        final double cz = minZ + (lminZ + lmaxZ + 1) * 0.5;
        final double extent = Math.max(1.0, Math.max(lmaxX - lminX + 1,
                Math.max(lmaxY - lminY + 1, lmaxZ - lminZ + 1)));

        final float yaw = currentYaw(time);
        final double fitScale = Math.min(w, h) * 0.62 / extent * camZoom;

        // 3D pass (see BattleTacticalV2Viewport3D for the full rationale of each step).
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0f, y + h / 2.0f + 4.0f, 250.0f);
        pose.scale((float) fitScale, (float) -fitScale, (float) fitScale);
        pose.mulPose(Axis.XP.rotationDegrees(camPitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        final MultiBufferSource.BufferSource buffer = g.bufferSource();
        final int packedLight = LightTexture.FULL_BRIGHT;

        for (int lz = 0; lz < map.dz; lz++) {
            for (int ly = 0; ly < map.dy; ly++) {
                for (int lx = 0; lx < map.dx; lx++) {
                    int flat = map.indexFor(lx, ly, lz);
                    if (!show[flat]) {
                        continue;
                    }
                    int paletteIdx = map.paletteIndices[flat];
                    boolean inBaseline = map.baselineMask.get(flat);
                    final BlockState voxelState;
                    final boolean overlayBlueTint;
                    if (paletteIdx == 0) {
                        voxelState = MISSING_STATE;
                        overlayBlueTint = false;
                    } else {
                        voxelState = map.palette[paletteIdx];
                        overlayBlueTint = !inBaseline;
                    }
                    pose.pushPose();
                    pose.translate(((double) minX + lx) - cx,
                                   ((double) minY + ly) - cy,
                                   ((double) minZ + lz) - cz);
                    renderBlockState(brd, voxelState, pose, buffer, packedLight);
                    if (overlayBlueTint) {
                        renderBlockState(brd, NEW_OVERLAY_STATE, pose, buffer, packedLight);
                    }
                    pose.popPose();
                }
            }
        }

        buffer.endBatch();
        pose.popPose();
        g.disableScissor();
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();

        // 2D holographic overlay on top of the 3D pass.
        scanline(g, x, y, w, h, time);
        cornerTicks(g, x, y, w, h, ACCENT);
        drawHud(g, font, x, y, w, h);
        return true;
    }

    /** [4] */
    private static boolean renderCannonModel(GuiGraphics g, int x, int y, int w, int h,
                                             DamageObserverVoxelMap map, float time) {
        final long[] positions = map.cannonPositions;
        final int[] indices = map.cannonIndices;
        final BlockState[] palette = map.cannonPalette;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long packed : positions) {
            BlockPos p = BlockPos.of(packed);
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        final double cx = (minX + maxX + 1) * 0.5;
        final double cy = (minY + maxY + 1) * 0.5;
        final double cz = (minZ + maxZ + 1) * 0.5;
        final double extent = Math.max(1.0, Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1)));
        final float yaw = currentYaw(time);
        final double fitScale = Math.min(w, h) * 0.82 / extent * camZoom;

        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0f, y + h / 2.0f + 4.0f, 250.0f);
        pose.scale((float) fitScale, (float) -fitScale, (float) fitScale);
        pose.mulPose(Axis.XP.rotationDegrees(camPitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        final MultiBufferSource.BufferSource buffer = g.bufferSource();
        final int packedLight = LightTexture.FULL_BRIGHT;

        for (int i = 0; i < positions.length; i++) {
            BlockPos p = BlockPos.of(positions[i]);
            BlockState state = palette[indices[i]];
            pose.pushPose();
            pose.translate(p.getX() - cx, p.getY() - cy, p.getZ() - cz);
            renderBlockState(brd, state, pose, buffer, packedLight);
            pose.popPose();
        }

        buffer.endBatch();
        pose.popPose();
        g.disableScissor();
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();

        scanline(g, x, y, w, h, time);
        cornerTicks(g, x, y, w, h, ACCENT);
        return true;
    }

    private static void renderBlockState(BlockRenderDispatcher brd, BlockState state, PoseStack pose,
                                         MultiBufferSource.BufferSource buffer, int packedLight) {
        final BakedModel model = brd.getBlockModel(state);
        final RenderType worldType = ItemBlockRenderTypes.getRenderType(state, false);
        final RenderType sheet = (worldType == RenderType.translucent())
                ? Sheets.translucentCullBlockSheet()
                : Sheets.cutoutBlockSheet();
        brd.getModelRenderer().renderModel(
                pose.last(),
                buffer.getBuffer(sheet),
                state,
                model,
                1.0f, 1.0f, 1.0f,
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }

    private static void drawHud(GuiGraphics g, Font font, int x, int y, int w, int h) {
        int ix = x + w - 16;
        int iy = y + 4;
        g.fill(ix, iy, ix + 12, iy + 12, 0x88000000);
        drawFrame(g, ix, iy, 12, 12, ACCENT);
        if (expanded) {
            g.fill(ix + 3, iy + 5, ix + 9, iy + 7, ACCENT);
        } else {
            g.fill(ix + 2, iy + 2, ix + 6, iy + 3, ACCENT);
            g.fill(ix + 2, iy + 2, ix + 3, iy + 6, ACCENT);
            g.fill(ix + 6, iy + 9, ix + 10, iy + 10, ACCENT);
            g.fill(ix + 9, iy + 6, ix + 10, iy + 10, ACCENT);
        }
        String hint = "drag rotate / scroll zoom";
        if (font.width(hint) < w - 12) {
            g.drawString(font, hint, x + 6, y + h - 10, HINT, false);
        }
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** A slow, soft horizontal scanline sweep to sell the projection. */
    private static void scanline(GuiGraphics g, int x, int y, int w, int h, float time) {
        int span = Math.max(1, h - 4);
        int sy = y + 2 + (int) ((time * 0.6f) % span);
        g.fill(x + 1, sy, x + w - 1, sy + 1, 0x224AD9FF);
    }

    private static void cornerTicks(GuiGraphics g, int x, int y, int w, int h, int color) {
        int s = 5;
        g.fill(x + 1, y + 1, x + 1 + s, y + 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 1 + s, color);
        g.fill(x + w - 1 - s, y + 1, x + w - 1, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 1 + s, color);
        g.fill(x + 1, y + h - 2, x + 1 + s, y + h - 1, color);
        g.fill(x + 1, y + h - 1 - s, x + 2, y + h - 1, color);
        g.fill(x + w - 1 - s, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + w - 2, y + h - 1 - s, x + w - 1, y + h - 1, color);
    }
}
