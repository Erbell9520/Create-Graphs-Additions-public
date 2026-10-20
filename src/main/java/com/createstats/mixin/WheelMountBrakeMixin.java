package com.createstats.mixin;

import com.createstats.vccmk4.Vcc4BrakeOverrides;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountBrakeMixin {
    @ModifyExpressionValue(
            method = "sable$physicsTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"),
            require = 0)
    private int createStats$brakeSignal(int signal) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) return signal;
        int override = Vcc4BrakeOverrides.forTick(self.getBlockPos(), level.getGameTime());
        return override >= 0 ? override : signal;
    }
}
