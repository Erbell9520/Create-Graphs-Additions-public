package com.createstats.client.battle;

import com.createstats.battle.DamageObserverVoxelMap;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.client.AnimationClock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BattleTacticalV2Viewport3D {
    private static final BlockState MISSING_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState NEW_OVERLAY_STATE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    private static final Map<BlockPos, CameraState> CAMERA_BY_PANEL = new HashMap<>();

    private static final Map<BlockPos, Boolean> FORCE_2D = new HashMap<>();

    public static boolean isForce2D(BlockPos panelPos) {
        return panelPos != null && Boolean.TRUE.equals(FORCE_2D.get(panelPos));
    }

    public static void toggleForce2D(BlockPos panelPos) {
        if (panelPos == null) return;
        FORCE_2D.put(panelPos.immutable(), !isForce2D(panelPos));
    }

    private static final Map<BlockState, BlockEntity> BE_CACHE = new HashMap<>();

    private BattleTacticalV2Viewport3D() {}

    public static boolean render(GuiGraphics g, Font font,
                                 int x, int y, int w, int h,
                                 StatsDisplayPanelBE panel,
                                 String sourceId,
                                 long renderTick,
                                 float partialTick) {
        if (panel == null || w <= 8 || h <= 8) {
            return false;
        }

        final StatsDisplayPanelBE.BattleScreenLink link = findBattleScreenLinkBySource(panel, sourceId);
        if (link == null) {
            return false;
        }
        final List<DamageObserverVoxelMap> maps = collectVoxelMaps(link);
        final List<BlockPos> mapSources = collectVoxelMapSources(link);

        g.fill(x, y, x + w, y + h, 0xFF0a0d18);
        drawBorder(g, x, y, w, h, 0xFF2a3050);

        if (maps.isEmpty()) {
            primeRequests(link);
            String hint = "Acquiring voxel data...";
            int tw = font.width(hint);
            g.drawString(font, Component.literal(hint), x + (w - tw) / 2, y + h / 2 - 4, 0xFF6e7898, false);
            return true;
        }

        BoundingBoxD bbox = computeMergedBoundingBox(maps);
        if (bbox == null) {
            return true;
        }
        double cx = (bbox.minX + bbox.maxX) * 0.5;
        double cy = (bbox.minY + bbox.maxY) * 0.5;
        double cz = (bbox.minZ + bbox.maxZ) * 0.5;
        double extent = Math.max(1.0, Math.max(bbox.maxX - bbox.minX,
                Math.max(bbox.maxY - bbox.minY, bbox.maxZ - bbox.minZ)));
        final int structureMinY = (int) Math.floor(bbox.minY);
        final int structureMaxY = (int) Math.ceil(bbox.maxY) - 1;

        final CameraState cam = CAMERA_BY_PANEL.computeIfAbsent(panel.getBlockPos(), p -> new CameraState());

        cam.lastX = x; cam.lastY = y; cam.lastW = w; cam.lastH = h;

        cam.structureMinY = structureMinY;
        cam.structureMaxY = structureMaxY;
        if (cam.yClipLevel == Integer.MAX_VALUE) {
            cam.yClipLevel = structureMaxY;
        }
        if (!cam.frozen && !cam.userDraggedThisSession) {
            float autoYaw = (float) ((AnimationClock.time(renderTick, partialTick) * 0.5) % 360.0);
            cam.yaw = ((autoYaw % 360f) + 360f) % 360f;
        }

        final double fitScale = Math.min(w, h) * 0.55 / extent * cam.zoom;

        g.flush();
        g.enableScissor(x, y, x + w, y + h);

        // clear depth inside the scissor. stale world depth let random voxels through every frame

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        final PoseStack pose = g.pose();
        pose.pushPose();

        pose.translate(x + w / 2.0f, y + h / 2.0f, 250.0f);

        pose.scale((float) fitScale, (float) -fitScale, (float) fitScale);

        pose.mulPose(Axis.XP.rotationDegrees(cam.pitch));
        pose.mulPose(Axis.YP.rotationDegrees(cam.yaw));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();

        final MultiBufferSource.BufferSource buffer = g.bufferSource();
        final int packedLight = LightTexture.FULL_BRIGHT;

        int totalPresent = 0;
        int totalMissing = 0;
        int totalNew = 0;

        for (int mi = 0; mi < maps.size(); mi++) {
            final DamageObserverVoxelMap map = maps.get(mi);
            final BlockPos sourcePos = mi < mapSources.size() ? mapSources.get(mi) : null;

            if (sourcePos != null && cam.hiddenSensors.contains(sourcePos)) continue;
            final int minX = map.minCorner.getX();
            final int minY = map.minCorner.getY();
            final int minZ = map.minCorner.getZ();
            final BlockState[] palette = map.palette;
            final int[] indices = map.paletteIndices;
            for (int lz = 0; lz < map.dz; lz++) {
                for (int ly = 0; ly < map.dy; ly++) {
                    if (minY + ly > cam.yClipLevel) continue;
                    for (int lx = 0; lx < map.dx; lx++) {
                        final int flat = map.indexFor(lx, ly, lz);
                        final int paletteIdx = indices[flat];
                        final boolean inBaseline = map.baselineMask.get(flat);
                        if (paletteIdx == 0 && !inBaseline) {
                            continue;
                        }
                        final BlockState voxelState;
                        final boolean overlayBlueTint;
                        if (paletteIdx == 0) {
                            voxelState = MISSING_STATE;
                            overlayBlueTint = false;
                            totalMissing++;
                        } else {
                            voxelState = palette[paletteIdx];
                            overlayBlueTint = !inBaseline;
                            if (overlayBlueTint) {
                                totalNew++;
                            } else {
                                totalPresent++;
                            }
                        }
                        pose.pushPose();

                        pose.translate(((double) minX + lx) - cx,
                                       ((double) minY + ly) - cy,
                                       ((double) minZ + lz) - cz);
                        renderBlockState(brd, voxelState, pose, buffer, packedLight);

                        renderBlockEntityIfAny(voxelState, pose, buffer, packedLight, partialTick);
                        if (overlayBlueTint) {
                            renderBlockState(brd, NEW_OVERLAY_STATE, pose, buffer, packedLight);
                        }
                        pose.popPose();
                    }
                }
            }
        }

        buffer.endBatch();
        Lighting.setupFor3DItems();

        pose.popPose();
        g.disableScissor();

        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();

        renderOverlay(g, font, x, y, w, h, maps, totalPresent, totalMissing, totalNew, cam, renderTick);

        renderControls(g, font, x, y, w, h, cam, mapSources);

        primeRequests(link);

        return true;
    }

    private static void renderOverlay(GuiGraphics g, Font font,
                                      int x, int y, int w, int h,
                                      List<DamageObserverVoxelMap> maps,
                                      int totalPresent, int totalMissing, int totalNew,
                                      CameraState cam, long renderTick) {
        final int totalBaseline = totalPresent + totalMissing;
        final double integrity = totalBaseline <= 0 ? 100.0 : (100.0 * totalPresent / totalBaseline);
        final int integrityColor = integrity < 25.0 ? 0xFFff5050
                : integrity < 60.0 ? 0xFFffc04a
                : 0xFF50ff90;

        final String title = "Tactical Reconstruction";
        g.drawString(font, Component.literal(title), x + 6, y + 6, 0xFFb8d0ff, false);

        final String sensors = maps.size() + " sensor" + (maps.size() == 1 ? "" : "s");
        int sw = font.width(sensors);
        g.drawString(font, Component.literal(sensors), x + w - sw - 6, y + 6, 0xFF889ec8, false);

        int lineY = y + 22;
        g.drawString(font, Component.literal("Integrity"), x + 6, lineY, 0xFF7088b8, false);
        g.drawString(font, Component.literal(String.format("%.1f%%", integrity)), x + 6, lineY + 10, integrityColor, false);
        g.drawString(font, Component.literal("Present " + totalPresent), x + 6, lineY + 26, 0xFF80c890, false);
        g.drawString(font, Component.literal("Missing " + totalMissing), x + 6, lineY + 36, 0xFFff7060, false);
        g.drawString(font, Component.literal("New     " + totalNew), x + 6, lineY + 46, 0xFF60a8ff, false);
    }

    private static void renderControls(GuiGraphics g, Font font,
                                       int x, int y, int w, int h,
                                       CameraState cam, List<BlockPos> mapSources) {
        final int btnSize = 16;
        final int btnGap = 3;
        final int barTop = y + 4;
        final int barRight = x + w - 4;

        cam.resetBtnW = btnSize; cam.resetBtnH = btnSize;
        cam.resetBtnX = barRight - btnSize;
        cam.resetBtnY = barTop;
        drawIconButton(g, font, cam.resetBtnX, cam.resetBtnY, btnSize, btnSize, "R", 0xFFbcd1ff, "Reset view");

        cam.freezeBtnW = btnSize; cam.freezeBtnH = btnSize;
        cam.freezeBtnX = cam.resetBtnX - btnGap - btnSize;
        cam.freezeBtnY = barTop;
        final String fzGlyph = cam.frozen ? ">" : "II";
        final int fzColor = cam.frozen ? 0xFF80ff90 : 0xFFffd060;
        drawIconButton(g, font, cam.freezeBtnX, cam.freezeBtnY, btnSize, btnSize, fzGlyph, fzColor, null);

        final int sliderMargin = 8;
        final int sliderTop = barTop + btnSize + sliderMargin;
        final int sliderBottom = y + h - sliderMargin - 4;
        final int sliderHeight = Math.max(20, sliderBottom - sliderTop);
        cam.sliderTrackX = barRight - 5;
        cam.sliderTrackY = sliderTop;
        cam.sliderTrackW = 4;
        cam.sliderTrackH = sliderHeight;

        g.fill(cam.sliderTrackX, cam.sliderTrackY,
               cam.sliderTrackX + cam.sliderTrackW,
               cam.sliderTrackY + cam.sliderTrackH, 0xFF1a2238);
        g.fill(cam.sliderTrackX, cam.sliderTrackY,
               cam.sliderTrackX + 1,
               cam.sliderTrackY + cam.sliderTrackH, 0xFF334268);

        final int range = Math.max(1, cam.structureMaxY - cam.structureMinY);
        final int clipClamped = Math.max(cam.structureMinY, Math.min(cam.structureMaxY, cam.yClipLevel));
        final float frac = 1f - ((float) (clipClamped - cam.structureMinY) / range);
        final int handleY = cam.sliderTrackY + (int) (frac * (cam.sliderTrackH - 6));
        final int handleColor = cam.draggingSlider ? 0xFFffd060 : 0xFF7fa0e0;
        g.fill(cam.sliderTrackX - 3, handleY,
               cam.sliderTrackX + cam.sliderTrackW + 3, handleY + 6, handleColor);

        g.drawString(font, Component.literal("Y"), cam.sliderTrackX - 4, cam.sliderTrackY - 10, 0xFF6a7ca0, false);

        if (cam.yClipLevel < cam.structureMaxY) {
            final String yLbl = "y" + cam.yClipLevel;
            g.drawString(font, Component.literal(yLbl),
                    cam.sliderTrackX - font.width(yLbl) - 6,
                    handleY - 4, 0xFF8aa0d0, false);
        }

        cam.sensorBtnRects.clear();
        cam.sensorBtnSources.clear();
        if (mapSources != null && mapSources.size() > 1) {
            int sx = x + 6;
            final int sy = y + h - 18;
            int idx = 1;
            for (BlockPos src : mapSources) {
                final String lbl = "S" + idx;
                final int lblW = font.width(lbl) + 8;
                final boolean hidden = cam.hiddenSensors.contains(src);
                final int bg = hidden ? 0xFF221a1a : 0xFF18243a;
                final int fg = hidden ? 0xFF705050 : 0xFFb8d0ff;
                g.fill(sx, sy, sx + lblW, sy + 14, bg);
                g.fill(sx, sy, sx + lblW, sy + 1, 0xFF2a3a60);
                g.fill(sx, sy + 13, sx + lblW, sy + 14, 0xFF080d18);
                g.drawString(font, Component.literal(lbl), sx + 4, sy + 3, fg, false);
                cam.sensorBtnRects.add(new int[]{sx, sy, lblW, 14});
                cam.sensorBtnSources.add(src);
                sx += lblW + 4;
                idx++;
            }
        }

        final String hint = (cam.frozen ? "[Frozen]" : "Yaw " + (int) cam.yaw) + "  Zoom " + String.format("%.1fx", cam.zoom);
        final int hintW = font.width(hint);
        g.drawString(font, Component.literal(hint), x + w - hintW - 6, y + h - 12, 0xFF4a5878, false);
    }

    private static void drawIconButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       String glyph, int glyphColor, String tooltip) {
        g.fill(x, y, x + w, y + h, 0xFF18243a);
        g.fill(x, y, x + w, y + 1, 0xFF2a3a60);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF080d18);
        g.fill(x, y, x + 1, y + h, 0xFF2a3a60);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF080d18);
        final int gw = font.width(glyph);
        g.drawString(font, Component.literal(glyph),
                x + (w - gw) / 2, y + (h - font.lineHeight) / 2 + 1, glyphColor, false);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void renderBlockState(BlockRenderDispatcher brd, BlockState state,
                                         PoseStack pose,
                                         net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer,
                                         int packedLight) {
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
                OverlayTexture.NO_OVERLAY
        );
    }

    private static List<DamageObserverVoxelMap> collectVoxelMaps(StatsDisplayPanelBE.BattleScreenLink link) {
        final List<DamageObserverVoxelMap> out = new ArrayList<>();
        if (link == null) return out;
        final List<com.createstats.battle.BattleObserverSnapshot> observers = link.getObservers();
        if (observers == null) return out;
        for (com.createstats.battle.BattleObserverSnapshot obs : observers) {
            if (obs == null || obs.sourcePos() == null) continue;
            DamageObserverVoxelMap map = ClientObserverVoxelCache.get(obs.sourcePos());
            if (map != null) out.add(map);
        }
        return out;
    }

    private static List<BlockPos> collectVoxelMapSources(StatsDisplayPanelBE.BattleScreenLink link) {
        final List<BlockPos> out = new ArrayList<>();
        if (link == null) return out;
        final List<com.createstats.battle.BattleObserverSnapshot> observers = link.getObservers();
        if (observers == null) return out;
        for (com.createstats.battle.BattleObserverSnapshot obs : observers) {
            if (obs == null || obs.sourcePos() == null) continue;
            DamageObserverVoxelMap map = ClientObserverVoxelCache.get(obs.sourcePos());
            if (map != null) out.add(obs.sourcePos());
        }
        return out;
    }

    private static void primeRequests(StatsDisplayPanelBE.BattleScreenLink link) {
        if (link == null) return;
        final List<com.createstats.battle.BattleObserverSnapshot> observers = link.getObservers();
        if (observers == null) return;
        for (com.createstats.battle.BattleObserverSnapshot obs : observers) {
            if (obs != null && obs.sourcePos() != null) {
                ClientObserverVoxelCache.ensureFresh(obs.sourcePos());
            }
        }
    }

    private static StatsDisplayPanelBE.BattleScreenLink findBattleScreenLinkBySource(StatsDisplayPanelBE panel, String sourceId) {
        if (panel == null) return null;
        BlockPos targetPos = null;
        if (sourceId != null && !sourceId.isEmpty()) {
            com.createstats.blockentity.SourceConfig src = panel.getSourceRegistry().get(sourceId);
            if (src != null) {
                targetPos = src.getSourcePos();
            }
        }
        StatsDisplayPanelBE.BattleScreenLink first = null;
        for (StatsDisplayPanelBE.BattleScreenLink link : panel.getBattleScreenLinks()) {
            if (link == null) continue;
            if (first == null) first = link;
            if (targetPos != null && targetPos.equals(link.getSourcePos())) {
                return link;
            }
        }
        return first;
    }

    private static BoundingBoxD computeMergedBoundingBox(List<DamageObserverVoxelMap> maps) {
        if (maps.isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (DamageObserverVoxelMap m : maps) {
            minX = Math.min(minX, m.minCorner.getX());
            minY = Math.min(minY, m.minCorner.getY());
            minZ = Math.min(minZ, m.minCorner.getZ());
            maxX = Math.max(maxX, m.minCorner.getX() + m.dx);
            maxY = Math.max(maxY, m.minCorner.getY() + m.dy);
            maxZ = Math.max(maxZ, m.minCorner.getZ() + m.dz);
        }
        if (!Double.isFinite(minX)) return null;
        return new BoundingBoxD(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static final class CameraState {
        float yaw = 30f;
        float pitch = 25f;
        boolean frozen = false;
        float zoom = 1.0f;

        boolean userDraggedThisSession = false;

        int lastX, lastY, lastW, lastH;

        boolean dragging = false;
        int dragButton = -1;

        int yClipLevel = Integer.MAX_VALUE;
        int structureMinY = 0;
        int structureMaxY = 0;

        final Set<BlockPos> hiddenSensors = new HashSet<>();

        int freezeBtnX, freezeBtnY, freezeBtnW, freezeBtnH;
        int resetBtnX, resetBtnY, resetBtnW, resetBtnH;
        int sliderTrackX, sliderTrackY, sliderTrackW, sliderTrackH;

        final List<int[]> sensorBtnRects = new ArrayList<>();
        final List<BlockPos> sensorBtnSources = new ArrayList<>();
        boolean draggingSlider = false;
    }

    private static final class BoundingBoxD {
        final double minX, minY, minZ, maxX, maxY, maxZ;
        BoundingBoxD(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    private static void renderBlockEntityIfAny(BlockState state, PoseStack pose,
                                                MultiBufferSource.BufferSource buffer,
                                                int packedLight, float partialTick) {
        if (!state.hasBlockEntity()) return;
        try {
            final BlockEntity be = BE_CACHE.computeIfAbsent(state, BattleTacticalV2Viewport3D::createBlockEntity);
            if (be == null) return;

            final Level lvl = Minecraft.getInstance().level;
            if (lvl != null && be.getLevel() != lvl) {
                be.setLevel(lvl);
            }
            @SuppressWarnings("unchecked")
            final BlockEntityRenderer<BlockEntity> renderer =
                    (BlockEntityRenderer<BlockEntity>) Minecraft.getInstance()
                            .getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer == null) return;
            renderer.render(be, partialTick, pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
        }
    }

    private static BlockEntity createBlockEntity(BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock eb)) return null;
        try {
            return eb.newBlockEntity(BlockPos.ZERO, state);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isInside(CameraState cam, double mx, double my) {
        return cam != null && mx >= cam.lastX && mx < cam.lastX + cam.lastW
                && my >= cam.lastY && my < cam.lastY + cam.lastH;
    }

    public static boolean mouseClicked(StatsDisplayPanelBE panel, double mx, double my, int button) {
        if (panel == null) return false;

        if (isForce2D(panel.getBlockPos())) return false;
        final CameraState cam = CAMERA_BY_PANEL.get(panel.getBlockPos());
        if (cam == null || cam.lastW <= 0 || !isInside(cam, mx, my)) return false;

        if (button == 0) {
            if (hitRect(mx, my, cam.freezeBtnX, cam.freezeBtnY, cam.freezeBtnW, cam.freezeBtnH)) {
                cam.frozen = !cam.frozen;
                if (!cam.frozen) cam.userDraggedThisSession = false;
                return true;
            }
            if (hitRect(mx, my, cam.resetBtnX, cam.resetBtnY, cam.resetBtnW, cam.resetBtnH)) {
                cam.yaw = 30f; cam.pitch = 25f; cam.zoom = 1.0f;
                cam.frozen = false; cam.userDraggedThisSession = false;
                cam.yClipLevel = cam.structureMaxY;
                cam.hiddenSensors.clear();
                return true;
            }

            for (int i = 0; i < cam.sensorBtnRects.size(); i++) {
                final int[] r = cam.sensorBtnRects.get(i);
                if (hitRect(mx, my, r[0], r[1], r[2], r[3])) {
                    final BlockPos src = cam.sensorBtnSources.get(i);
                    if (!cam.hiddenSensors.remove(src)) cam.hiddenSensors.add(src);
                    return true;
                }
            }

            if (hitRect(mx, my, cam.sliderTrackX - 6, cam.sliderTrackY - 4,
                        cam.sliderTrackW + 12, cam.sliderTrackH + 8)) {
                cam.draggingSlider = true;
                applySliderDrag(cam, my);
                return true;
            }

            cam.dragging = true;
            cam.dragButton = 0;
            cam.userDraggedThisSession = true;
            return true;
        }
        if (button == 1) {
            cam.frozen = !cam.frozen;
            if (!cam.frozen) cam.userDraggedThisSession = false;
            return true;
        }
        if (button == 2) {
            cam.yaw = 30f; cam.pitch = 25f; cam.zoom = 1.0f;
            cam.frozen = false;
            cam.userDraggedThisSession = false;
            cam.yClipLevel = cam.structureMaxY;
            cam.hiddenSensors.clear();
            return true;
        }
        return false;
    }

    private static boolean hitRect(double mx, double my, int x, int y, int w, int h) {
        return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void applySliderDrag(CameraState cam, double my) {
        if (cam.sliderTrackH <= 0) return;
        final double rel = (my - cam.sliderTrackY) / (double) Math.max(1, cam.sliderTrackH - 6);
        final double clampedRel = Math.max(0.0, Math.min(1.0, rel));
        final int range = Math.max(1, cam.structureMaxY - cam.structureMinY);

        cam.yClipLevel = (int) Math.round(cam.structureMaxY - clampedRel * range);
    }

    public static boolean mouseDragged(StatsDisplayPanelBE panel, double mx, double my,
                                       int button, double dx, double dy) {
        if (panel == null) return false;
        if (isForce2D(panel.getBlockPos())) return false;
        final CameraState cam = CAMERA_BY_PANEL.get(panel.getBlockPos());
        if (cam == null) return false;
        if (cam.draggingSlider && button == 0) {
            applySliderDrag(cam, my);
            return true;
        }
        if (!cam.dragging || cam.dragButton != button) return false;

        cam.yaw = ((cam.yaw + (float) (dx * 0.5)) % 360f + 360f) % 360f;
        cam.pitch = Math.max(-89f, Math.min(89f, cam.pitch + (float) (dy * 0.5)));
        return true;
    }

    public static boolean mouseReleased(StatsDisplayPanelBE panel, double mx, double my, int button) {
        if (panel == null) return false;
        if (isForce2D(panel.getBlockPos())) return false;
        final CameraState cam = CAMERA_BY_PANEL.get(panel.getBlockPos());
        if (cam == null) return false;
        boolean handled = false;
        if (cam.draggingSlider && button == 0) { cam.draggingSlider = false; handled = true; }
        if (cam.dragging && cam.dragButton == button) {
            cam.dragging = false; cam.dragButton = -1; handled = true;
        }
        return handled;
    }

    public static boolean mouseScrolled(StatsDisplayPanelBE panel, double mx, double my, double scrollY) {
        if (panel == null || scrollY == 0) return false;
        if (isForce2D(panel.getBlockPos())) return false;
        final CameraState cam = CAMERA_BY_PANEL.get(panel.getBlockPos());
        if (cam == null || cam.lastW <= 0 || !isInside(cam, mx, my)) return false;

        final double factor = scrollY > 0 ? 1.15 : 1.0 / 1.15;
        cam.zoom = (float) Math.max(0.15, Math.min(8.0, cam.zoom * factor));
        cam.userDraggedThisSession = true;
        return true;
    }
}
