package com.createstats.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Phantom.class)
public interface PhantomAccessor {
    @Accessor("anchorPoint")
    void createStats$setAnchorPoint(BlockPos anchor);

    @Accessor("moveTargetPoint")
    void createStats$setMoveTargetPoint(Vec3 target);
}
