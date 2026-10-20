package com.createstats.mixin;

import com.createstats.block.GirderMounting;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GirderBlock.class, remap = false)
public abstract class GirderBlockMixin {
    @Inject(method = "updateState", at = @At("RETURN"), cancellable = true)
    private static void createStats$reachSideFixture(LevelAccessor level, BlockPos pos, BlockState state,
                                                     Direction d, CallbackInfoReturnable<BlockState> cir) {
        if (d.getAxis().isVertical()) {
            return;
        }
        final BlockState result = cir.getReturnValue();
        if (!(result.getBlock() instanceof GirderBlock) || !GirderMounting.fixtureOn(level, pos, d)) {
            return;
        }
        cir.setReturnValue(result.setValue(
                d.getAxis() == Direction.Axis.X ? GirderBlock.X : GirderBlock.Z, true));
    }

    @Inject(method = "updateVerticalProperty", at = @At("RETURN"), cancellable = true)
    private static void createStats$reachVerticalFixture(LevelAccessor level, BlockPos pos, BlockState state,
                                                         Property<Boolean> updateProperty, BlockState sideState,
                                                         Direction d, CallbackInfoReturnable<BlockState> cir) {
        if (!GirderMounting.fixtureOn(sideState, d)) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue().setValue(updateProperty, true));
    }
}
