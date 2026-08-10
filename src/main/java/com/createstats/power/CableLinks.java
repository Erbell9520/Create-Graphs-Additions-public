package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** [1] */
public final class CableLinks {

    private CableLinks() {
    }

    /** Answered by {@link View#colourAt} for a position that holds no cable at all. */
    public static final int NO_COLOUR = -1;

    /** How the run leaves one face of one cable block in one direction. */
    public enum Link {
        /** Nothing that way. */
        NONE,
        /** The same surface carries on into the next block. */
        FLAT,
        /** The run turns onto another face of this same block, an inside corner. */
        CONCAVE,
        /** The run folds around the outside edge of its support, an outside corner. */
        CONVEX,
        /** Something that takes or gives power, which the cable should run into rather than stop at. */
        DEVICE
    }

    /** What the rules need to know about the world, and nothing more. */
    public interface View {

        /** True when the block at {@code pos} carries cable on {@code face}. */
        boolean hasCableFace(BlockPos pos, Direction face);

        /** True when the block at {@code pos} is cable at all, whichever faces it carries. */
        boolean isCable(BlockPos pos);

        /** True when the block at {@code pos} takes or gives energy on the side facing us. */
        boolean isDevice(BlockPos pos, Direction fromSide);

        /** [2] */
        int colourAt(BlockPos pos);
    }

    /** [3] */
    public static Link of(View view, BlockPos pos, Direction face, Direction towards) {
        if (towards.getAxis() == face.getAxis()) {
            return Link.NONE;   // along the mounting normal, not across the surface
        }
        final int mine = view.colourAt(pos);
        final BlockPos ahead = pos.relative(towards);
        if (view.hasCableFace(ahead, face)) {
            return sameCircuit(view, mine, ahead) ? Link.FLAT : Link.NONE;
        }
        if (view.hasCableFace(pos, towards)) {
            return Link.CONCAVE;
        }
        final BlockPos wrapped = ahead.relative(face);
        if (view.hasCableFace(wrapped, towards.getOpposite())) {
            return sameCircuit(view, mine, wrapped) ? Link.CONVEX : Link.NONE;
        }
        if (view.isCable(ahead)) {
            // Cable that shares no face with us: a different run passing by, not a junction.
            return Link.NONE;
        }
        return view.isDevice(ahead, towards.getOpposite()) ? Link.DEVICE : Link.NONE;
    }

    /** True when the cable at {@code other} is on the same circuit as a cable of colour {@code mine}. */
    public static boolean sameCircuit(View view, int mine, BlockPos other) {
        return view.colourAt(other) == mine;
    }

    /** [4] */
    public static java.util.List<BlockPos> continuations(View view, BlockPos pos) {
        final java.util.List<BlockPos> out = new java.util.ArrayList<>(4);
        for (Direction face : Direction.values()) {
            if (!view.hasCableFace(pos, face)) {
                continue;
            }
            for (Direction towards : Direction.values()) {
                final BlockPos next = switch (of(view, pos, face, towards)) {
                    case FLAT -> pos.relative(towards);
                    case CONVEX -> pos.relative(towards).relative(face);
                    default -> null;
                };
                if (next != null && !out.contains(next)) {
                    out.add(next);
                }
            }
        }
        return out;
    }

    /** [5] */
    public static boolean ownsCorner(Direction mine, Direction theirs) {
        return mine.ordinal() < theirs.ordinal();
    }
}
