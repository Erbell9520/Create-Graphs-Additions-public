package com.createstats.client.battle;

import com.createstats.battle.DamageObserverVoxelMap;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BattleTacticalV2PanelHologram {
    private static final BlockState MISSING_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState NEW_OVERLAY_STATE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    private static final Map<BlockState, BlockEntity> BE_CACHE = new HashMap<>();

    private BattleTacticalV2PanelHologram() {}

    public static void render(StatsDisplayPanelBE be, PoseStack ps,
                              MultiBufferSource buf, int packedLight, float partialTick) {
        if (be == null) return;
        final StatsDisplayPanelBE.BattleScreenLink link = findV2Link(be);
        if (link == null) return;
        final List<DamageObserverVoxelMap> maps = collectVoxelMaps(link);
        if (maps.isEmpty()) {
            primeRequests(link);
            return;
        }

        BoundingBoxD bbox = computeBox(maps);
        if (bbox == null) return;
        final double cx = (bbox.minX + bbox.maxX) * 0.5;
        final double cy = (bbox.minY + bbox.maxY) * 0.5;
        final double cz = (bbox.minZ + bbox.maxZ) * 0.5;
        final double extent = Math.max(1.0, Math.max(bbox.maxX - bbox.minX,
                Math.max(bbox.maxY - bbox.minY, bbox.maxZ - bbox.minZ)));

        final BlockState ownState = be.getBlockState();
        final Direction facing = ownState.hasProperty(HorizontalDirectionalBlock.FACING)
                ? ownState.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        final int mbW = be.getMultiblockWidth();
        final int mbH = be.getMultiblockHeight();

        final Level level = be.getLevel();
        final long time = level == null ? 0L : level.getGameTime();
        final float yaw = ((time + partialTick) * 0.6f) % 360f;

        ps.pushPose();

        ps.translate(0.5, 0.5, 0.5);
        final float yRot = switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> 270f;
            default -> 0f;
        };
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.translate(-0.5, -0.5, -0.5);

        final double centerX = mbW * 0.5;
        final double centerY = mbH * 0.5;
        final double standoff = 0.4;
        ps.translate(centerX, centerY, -standoff);

        final float fitScale = (float) (Math.min(mbW, mbH) * 0.55 / extent);
        ps.scale(fitScale, fitScale, fitScale);
        ps.mulPose(Axis.XP.rotationDegrees(22f));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        renderVoxels(maps, cx, cy, cz, ps, buf, LightTexture.FULL_BRIGHT, partialTick, brd);
        ps.popPose();

        primeRequests(link);
    }

    private static void renderVoxels(List<DamageObserverVoxelMap> maps,
                                     double cx, double cy, double cz,
                                     PoseStack ps, MultiBufferSource buf,
                                     int packedLight, float partialTick,
                                     BlockRenderDispatcher brd) {
        for (DamageObserverVoxelMap map : maps) {
            final int minX = map.minCorner.getX();
            final int minY = map.minCorner.getY();
            final int minZ = map.minCorner.getZ();
            final BlockState[] palette = map.palette;
            final int[] indices = map.paletteIndices;
            for (int lz = 0; lz < map.dz; lz++) {
                for (int ly = 0; ly < map.dy; ly++) {
                    for (int lx = 0; lx < map.dx; lx++) {
                        final int flat = map.indexFor(lx, ly, lz);
                        final int paletteIdx = indices[flat];
                        final boolean inBaseline = map.baselineMask.get(flat);
                        if (paletteIdx == 0 && !inBaseline) continue;
                        final BlockState voxelState;
                        final boolean overlay;
                        if (paletteIdx == 0) {
                            voxelState = MISSING_STATE;
                            overlay = false;
                        } else {
                            voxelState = palette[paletteIdx];
                            overlay = !inBaseline;
                        }

                        if (paletteIdx > 0 && inBaseline && voxelState.canOcclude()
                                && occludes(map, lx + 1, ly, lz) && occludes(map, lx - 1, ly, lz)
                                && occludes(map, lx, ly + 1, lz) && occludes(map, lx, ly - 1, lz)
                                && occludes(map, lx, ly, lz + 1) && occludes(map, lx, ly, lz - 1)) {
                            continue;
                        }
                        ps.pushPose();
                        ps.translate(((double) minX + lx) - cx,
                                     ((double) minY + ly) - cy,
                                     ((double) minZ + lz) - cz);
                        renderBlockState(brd, voxelState, ps, buf, packedLight);
                        renderBlockEntityIfAny(voxelState, ps, buf, packedLight, partialTick);
                        if (overlay) {
                            renderBlockState(brd, NEW_OVERLAY_STATE, ps, buf, packedLight);
                        }
                        ps.popPose();
                    }
                }
            }
        }
    }

    private static boolean occludes(DamageObserverVoxelMap map, int lx, int ly, int lz) {
        if (lx < 0 || ly < 0 || lz < 0 || lx >= map.dx || ly >= map.dy || lz >= map.dz) return false;
        final int idx = map.paletteIndices[map.indexFor(lx, ly, lz)];
        if (idx <= 0) return false;
        final BlockState s = map.palette[idx];
        return s != null && s.canOcclude();
    }

    private static void renderBlockState(BlockRenderDispatcher brd, BlockState state,
                                         PoseStack pose, MultiBufferSource buf, int packedLight) {
        final BakedModel model = brd.getBlockModel(state);
        final RenderType worldType = ItemBlockRenderTypes.getRenderType(state, false);
        final RenderType sheet = (worldType == RenderType.translucent())
                ? Sheets.translucentCullBlockSheet()
                : Sheets.cutoutBlockSheet();
        brd.getModelRenderer().renderModel(
                pose.last(),
                buf.getBuffer(sheet),
                state, model,
                1f, 1f, 1f,
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }

    private static void renderBlockEntityIfAny(BlockState state, PoseStack pose,
                                                MultiBufferSource buf,
                                                int packedLight, float partialTick) {
        if (!state.hasBlockEntity()) return;
        try {
            final BlockEntity be = BE_CACHE.computeIfAbsent(state,
                    BattleTacticalV2PanelHologram::createBlockEntity);
            if (be == null) return;
            final Level lvl = Minecraft.getInstance().level;
            if (lvl != null && be.getLevel() != lvl) be.setLevel(lvl);
            @SuppressWarnings("unchecked")
            final BlockEntityRenderer<BlockEntity> renderer =
                    (BlockEntityRenderer<BlockEntity>) Minecraft.getInstance()
                            .getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer == null) return;
            renderer.render(be, partialTick, pose, buf, packedLight, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
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

    private static StatsDisplayPanelBE.BattleScreenLink findV2Link(StatsDisplayPanelBE be) {
        for (StatsDisplayPanelBE.BattleScreenLink link : be.getBattleScreenLinks()) {
            if (link == null) continue;
            if (link.getBattleViewMode() != 1) continue;
            return link;
        }
        return null;
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

    private static BoundingBoxD computeBox(List<DamageObserverVoxelMap> maps) {
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

    private static final class BoundingBoxD {
        final double minX, minY, minZ, maxX, maxY, maxZ;
        BoundingBoxD(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }
}
