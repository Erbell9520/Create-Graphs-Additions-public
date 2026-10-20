package com.createstats.power;

import com.createstats.block.CableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// two clicks, not fifteen placements
public final class CablePlacement {
    private CablePlacement() {
    }

    public static final int MAX_RUN = 48;

    private static final Map<UUID, CableRoute.Step> ANCHORS = new ConcurrentHashMap<>();

    private static final Map<UUID, CableRoute.Step> CLIENT_ANCHORS = new ConcurrentHashMap<>();

    private static Map<UUID, CableRoute.Step> mapFor(Player player) {
        return player.level().isClientSide() ? CLIENT_ANCHORS : ANCHORS;
    }

    public static void clear(Player player) {
        mapFor(player).remove(player.getUUID());
        publish(player, null);
    }

    public static void clear(UUID playerId) {
        ANCHORS.remove(playerId);
    }

    public static CableRoute.Step anchorOf(Player player) {
        return mapFor(player).get(player.getUUID());
    }

    public static void setAnchor(Player player, CableRoute.Step step) {
        mapFor(player).put(player.getUUID(), step);
        publish(player, step);
    }

    public static void acceptFromServer(UUID playerId, @Nullable CableRoute.Step step) {
        if (step == null) {
            CLIENT_ANCHORS.remove(playerId);
        } else {
            CLIENT_ANCHORS.put(playerId, step);
        }
    }

    private static void publish(Player player, @Nullable CableRoute.Step step) {
        if (player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new com.createstats.network.CableAnchorPacket(step));
        }
    }

    public static CableRoute.Step stepFor(BlockPos clicked, Direction clickedFace) {
        return new CableRoute.Step(clicked.relative(clickedFace), clickedFace.getOpposite());
    }

    public static CableRoute.Step endpointFor(Level level, BlockPos clicked, Direction clickedFace,
                                              net.minecraft.world.phys.Vec3 hint) {
        final CableRoute.Step direct = stepFor(clicked, clickedFace);
        final CableRoute.SurfaceView view = CableBlock.viewOf(level);
        if (CableRoute.isPlaceable(view, direct)) {
            return direct;
        }
        if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                clicked, null) == null) {
            return direct;
        }

        CableRoute.Step best = null;
        double bestScore = Double.MAX_VALUE;
        boolean bestFeeds = false;
        for (Direction surface : Direction.values()) {
            if (!view.isSolid(clicked.relative(surface))) {
                continue;
            }
            for (Direction beside : Direction.values()) {
                if (beside.getAxis() == surface.getAxis()) {
                    continue;
                }
                final CableRoute.Step candidate = new CableRoute.Step(clicked.relative(beside), surface);
                if (!CableRoute.isPlaceable(view, candidate)) {
                    continue;
                }

                final boolean feeds = level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        clicked, beside) != null;
                final double score = hint.distanceToSqr(
                        candidate.pos().getX() + 0.5, candidate.pos().getY() + 0.5,
                        candidate.pos().getZ() + 0.5);
                final boolean better = best == null
                        || (feeds && !bestFeeds)
                        || (feeds == bestFeeds && score < bestScore);
                if (better) {
                    bestScore = score;
                    bestFeeds = feeds;
                    best = candidate;
                }
            }
        }
        return best != null ? best : direct;
    }

    public static List<CableRoute.Step> route(Level level, CableRoute.Step from, CableRoute.Step to) {
        return CableRoute.find(CableBlock.viewOf(level), from, to, MAX_RUN);
    }

    public static int place(LevelAccessor level, List<CableRoute.Step> route, CableBlock cable,
                            DyeColor colour, int budget) {
        int placed = 0;
        for (CableRoute.Step step : route) {
            if (placed >= budget) {
                break;
            }
            final BlockState existing = level.getBlockState(step.pos());
            final Direction face = step.face();
            if (existing.getBlock() == cable) {
                if (existing.getValue(CableBlock.faceProperty(face))) {
                    continue;
                }
                level.setBlock(step.pos(), existing.setValue(CableBlock.faceProperty(face), true),
                        Block.UPDATE_ALL);
                placed++;
                continue;
            }
            if (!CableRoute.isPlaceable(CableBlock.viewOf(level), step)) {
                continue;
            }
            level.setBlock(step.pos(), cable.defaultBlockState()
                    .setValue(CableBlock.faceProperty(face), true)
                    .setValue(CableBlock.COLOR, colour), Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    public static int cost(LevelAccessor level, List<CableRoute.Step> route, CableBlock cable) {
        int n = 0;
        for (CableRoute.Step step : route) {
            final BlockState existing = level.getBlockState(step.pos());
            if (existing.getBlock() == cable && existing.getValue(CableBlock.faceProperty(step.face()))) {
                continue;
            }
            n++;
        }
        return n;
    }
}
