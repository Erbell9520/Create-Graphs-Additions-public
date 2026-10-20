package com.createstats.mixin;

import com.createstats.dynlight.DynamicLightField;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    @ModifyExpressionValue(
            method = "getEmission",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I"),
            require = 0)
    private int createstats$dynamicEmission(int original, @Local(argsOnly = true) long packedPos) {
        return DynamicLightField.emission(this, packedPos, original);
    }
}
