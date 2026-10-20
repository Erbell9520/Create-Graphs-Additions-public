package com.createstats.mixin;

import com.createstats.surveyor.UnseenRoutes;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk", remap = false)
public abstract class SableParkedVetoMixin {
    @Inject(method = "canLoadSubLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void createStats$parkedStaysParked(ServerLevel level, @Coerce Object data, CallbackInfoReturnable<Boolean> cir) {
        if (UnseenRoutes.keepsParked(level, data)) cir.setReturnValue(false);
    }
}
