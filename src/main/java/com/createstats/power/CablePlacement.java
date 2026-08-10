package com.createstats.power;

import com.createstats.block.CableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** [1] */
public final class CablePlacement {

    private CablePlacement() {
    }

    /** How far a single assisted run may reach. Also the router's search cap. */
    public static final int MAX_RUN = 48;

    // [2]
    private static final Map<UUID, CableRoute.Step> ANCHORS = new ConcurrentHashMap<>();

    /** [3] */
    public static void clear(UUID playerId) {
        ANCHORS.remove(playerId);
    }

    public static CableRoute.Step anchorOf(Player player) {
        return ANCHORS.get(player.getUUID());
    }

    public static void setAnchor(Player player, CableRoute.Step step) {
        ANCHORS.put(player.getUUID(), step);
    }

    /** [4] */
    public static CableRoute.Step stepFor(BlockPos clicked, Direction clickedFace) {
        return new CableRoute.Step(clicked.relative(clickedFace), clickedFace.getOpposite());
    }

    /**
     * The cable step a click means, snapping to the surface beside a fitting when the player clicked
     * the fitting itself.
     *
     * <p>Clicking a lamp used to be rejected outright. {@link CableRoute#isPlaceable} wants a full
     * solid block behind the cable and a lamp is not one, so the most obvious gesture in the whole
     * feature, pointing at the thing you want to power, was the one gesture that did not work.</p>
     *
     * <p>A fitting is a perfectly good end for a run; it is simply not a place cable can sit. So the
     * run ends <i>next to</i> it, on the surface the fitting is itself mounted on, which is where a
     * player would have put the last block by hand. Landing there is also what feeds it: cable powers
     * whatever it touches.</p>
     *
     * @param hint where on the block the player actually clicked, so a long fitting takes cable at the
     *             end the player pointed at rather than an arbitrary one
     */
    public static CableRoute.Step endpointFor(Level level, BlockPos clicked, Direction clickedFace,
                                              net.minecraft.world.phys.Vec3 hint) {
        final CableRoute.Step direct = stepFor(clicked, clickedFace);
        final CableRoute.SurfaceView view = CableBlock.viewOf(level);
        if (CableRoute.isPlaceable(view, direct)) {
            return direct;
        }
        if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                clicked, clickedFace) == null) {
            return direct;   // not a fitting either, so let the caller refuse it as before
        }

        CableRoute.Step best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction surface : Direction.values()) {
            // The fitting's own mounting surface: cable joins it on the same wall, not floating off
            // at right angles to it.
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
                final double score = hint.distanceToSqr(
                        candidate.pos().getX() + 0.5, candidate.pos().getY() + 0.5,
                        candidate.pos().getZ() + 0.5);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best != null ? best : direct;
    }

    /** [5] */
    public static List<CableRoute.Step> route(Level level, CableRoute.Step from, CableRoute.Step to) {
        return CableRoute.find(CableBlock.viewOf(level), from, to, MAX_RUN);
    }

    /**
     * Lays a route in one colour, skipping steps that already carry this cable on that face.
     *
     * <p>The colour is passed in rather than defaulted, because colour is the circuit: a run laid in
     * the player's chosen colour is a circuit of its own the moment it exists, and one laid in the
     * default would silently join whatever orange run it happened to touch.</p>
     *
     * @return how many cable blocks were actually placed
     */
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
                    continue; // already wired this way; not an error and not a charge
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

    /** How many cable items a route will actually consume, ignoring steps already wired. */
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
