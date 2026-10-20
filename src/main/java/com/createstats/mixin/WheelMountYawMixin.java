package com.createstats.mixin;

import com.createstats.vccmk4.Vcc4YawOverrides;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// any angle, not 2 degree steps. offroad's computeYaw quantises
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountYawMixin {
    @Inject(method = "computeYaw()D", at = @At("RETURN"), cancellable = true, require = 0)
    private void createStats$continuousYaw(CallbackInfoReturnable<Double> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        double deg = Vcc4YawOverrides.forTick(self.getBlockPos(), level.getGameTime());
        if (Double.isNaN(deg)) return;

        cir.setReturnValue(-Math.toRadians(deg));
    }
}
