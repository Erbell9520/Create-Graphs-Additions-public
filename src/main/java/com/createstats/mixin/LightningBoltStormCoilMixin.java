package com.createstats.mixin;

import com.createstats.blockentity.StormCoilBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class LightningBoltStormCoilMixin {
    @Shadow
    private boolean visualOnly;

    @Inject(method = "powerLightningRod", at = @At("TAIL"))
    private void create_stats$stormCoil(CallbackInfo ci) {
        if (visualOnly) {
            return;
        }
        final LightningBolt self = (LightningBolt) (Object) this;
        StormCoilBE.struck(self.level(), BlockPos.containing(self.getX(), self.getY() - 1.0E-6, self.getZ()));
    }
}
