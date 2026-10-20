package com.createstats.mixin;

import com.createstats.blockentity.StormCoilBE;
import com.createstats.stormcoil.CoilCatch;
import com.createstats.stormcoil.StormCoilIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerLevel.class)
public abstract class ServerLevelStormCoilMixin {
    @Inject(method = "findLightningRod", at = @At("RETURN"), cancellable = true)
    private void create_stats$stormCoil(BlockPos at, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        final ServerLevel level = (ServerLevel) (Object) this;
        final Optional<BlockPos> rod = cir.getReturnValue();
        final long rodDist = rod != null && rod.isPresent() ? (long) rod.get().below().distSqr(at) : -1L;
        BlockPos best = null;
        long bestDist = Long.MAX_VALUE;
        for (BlockPos pos : StormCoilIndex.in(level)) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof StormCoilBE coil)) {
                StormCoilIndex.remove(level, pos);
                continue;
            }
            final BlockPos crown = coil.crown();
            final long d = (long) crown.distSqr(at);
            if (d < bestDist && CoilCatch.inRadius(coil.coilStage(), d) && StormCoilBE.isColumnTop(level, crown)) {
                best = crown;
                bestDist = d;
            }
        }
        if (best != null && CoilCatch.beatsRod(bestDist, rodDist)) {
            cir.setReturnValue(Optional.of(best.above()));
        }
    }
}
