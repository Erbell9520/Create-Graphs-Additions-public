package com.createstats.arm;

import com.createstats.registry.ModBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class InductionHeaterArmPointType extends ArmInteractionPointType {
    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.is(ModBlocks.INDUCTION_HEATER.get());
    }

    @Nullable
    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state);
    }
}
