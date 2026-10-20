package com.createstats.mixin;

import com.createstats.flight.bridge.SablePoseGuard;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// "not in that map" == "not in the engine", so check before the native call. sable tax #12
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierReadPoseGuardMixin {
    @Shadow(remap = false)
    @Final
    private Int2ObjectMap<?> activeSubLevels;

    @Inject(method = "readPose", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createStats$readPoseOfLiveBodiesOnly(@Coerce Object subLevel, @Coerce Object dest, CallbackInfoReturnable<Object> cir) {
        int id = SablePoseGuard.runtimeId(subLevel);
        if (id == Integer.MIN_VALUE || activeSubLevels.containsKey(id)) return;
        if (SablePoseGuard.answerFromLogicalPose(subLevel, dest, "readPose")) cir.setReturnValue(dest);
    }

    @Inject(method = "onStatsChanged", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createStats$statsOfLiveBodiesOnly(@Coerce Object subLevel, CallbackInfo ci) {
        int id = SablePoseGuard.runtimeId(subLevel);
        if (id == Integer.MIN_VALUE || activeSubLevels.containsKey(id)) return;
        SablePoseGuard.report(subLevel, "onStatsChanged");
        ci.cancel();
    }
}
