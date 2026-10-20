package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class IndoorCableLinks {
    private IndoorCableLinks() {
    }

    public static final int NO_COLOUR = -1;

    public enum Link {
        NONE,

        CABLE,

        DEVICE,

        BORE
    }

    public interface View {
        boolean isIndoorCable(BlockPos pos);

        int colourAt(BlockPos pos);

        boolean isDevice(BlockPos pos, Direction fromSide);

        @Nullable
        BlockPos boreTowards(BlockPos pos, Direction towards);
    }

    public static Link of(View view, BlockPos pos, Direction towards) {
        final BlockPos ahead = pos.relative(towards);
        if (view.isIndoorCable(ahead)) {
            return view.colourAt(ahead) == view.colourAt(pos) ? Link.CABLE : Link.NONE;
        }
        if (view.boreTowards(pos, towards) != null) {
            return Link.BORE;
        }
        return view.isDevice(ahead, towards.getOpposite()) ? Link.DEVICE : Link.NONE;
    }

    public static List<BlockPos> continuations(View view, BlockPos pos) {
        final List<BlockPos> out = new ArrayList<>(4);
        for (Direction towards : Direction.values()) {
            final BlockPos next = switch (of(view, pos, towards)) {
                case CABLE -> pos.relative(towards);
                case BORE -> view.boreTowards(pos, towards);
                default -> null;
            };
            if (next != null && !out.contains(next)) {
                out.add(next);
            }
        }
        return out;
    }
}
