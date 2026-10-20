package com.createstats.block;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// girders never have a sturdy face, so every fixture refused them. own rule
public final class GirderMounting {
    private GirderMounting() {
    }

    public interface Fixture {
        boolean isBackedBy(BlockState state, Direction towardSupport);
    }

    public enum Kind {
        NONE,

        GIRDER,

        ENCASED_SHAFT
    }

    public static boolean holds(BlockGetter level, BlockPos supportPos, Direction face) {
        final BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, face) || holdsOnGirder(support, face);
    }

    public static boolean holdsOnGirder(BlockState support, Direction face) {
        final Kind kind = kindOf(support);
        final Direction.Axis shaft = kind == Kind.ENCASED_SHAFT
                ? support.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)
                : null;
        return faceHolds(kind, shaft, face);
    }

    public static boolean faceHolds(Kind kind, @Nullable Direction.Axis shaftAxis, Direction face) {
        return switch (kind) {
            case NONE -> false;
            case GIRDER -> true;
            case ENCASED_SHAFT -> face.getAxis().isVertical() || face.getAxis() != shaftAxis;
        };
    }

    public static Kind kindOf(BlockState state) {
        if (state.getBlock() instanceof GirderEncasedShaftBlock) {
            return Kind.ENCASED_SHAFT;
        }
        if (state.getBlock() instanceof GirderBlock) {
            return Kind.GIRDER;
        }
        return Kind.NONE;
    }

    public static boolean fixtureOn(BlockState neighbour, Direction side) {
        return neighbour.getBlock() instanceof Fixture fixture
                && fixture.isBackedBy(neighbour, side.getOpposite());
    }

    public static boolean fixtureOn(BlockGetter level, BlockPos girderPos, Direction side) {
        return fixtureOn(level.getBlockState(girderPos.relative(side)), side);
    }
}
