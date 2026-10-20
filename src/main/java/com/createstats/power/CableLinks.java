package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class CableLinks {
    private CableLinks() {
    }

    public static final int NO_COLOUR = -1;

    public enum Link {
        NONE,

        FLAT,

        CONCAVE,

        CONVEX,

        DEVICE,

        DEVICE_WRAP
    }

    public interface View {
        boolean hasCableFace(BlockPos pos, Direction face);

        boolean isCable(BlockPos pos);

        boolean isDevice(BlockPos pos, Direction fromSide);

        boolean isFree(BlockPos pos);

        int colourAt(BlockPos pos);
    }

    public static Link of(View view, BlockPos pos, Direction face, Direction towards) {
        if (towards.getAxis() == face.getAxis()) {
            return Link.NONE;
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
            return Link.NONE;
        }
        if (view.isDevice(ahead, towards.getOpposite())) {
            return Link.DEVICE;
        }

        return view.isFree(ahead) && view.isDevice(wrapped, face.getOpposite())
                ? Link.DEVICE_WRAP : Link.NONE;
    }

    public static boolean sameCircuit(View view, int mine, BlockPos other) {
        return view.colourAt(other) == mine;
    }

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

    public static boolean ownsCorner(Direction mine, Direction theirs) {
        return mine.ordinal() < theirs.ordinal();
    }
}
