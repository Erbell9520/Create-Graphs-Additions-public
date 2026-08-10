package com.createstats.mixin;

import com.createstats.train.manifest.ManifestAwareFluidHandler;
import com.createstats.train.manifest.ManifestUnloadTracker;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity",
       remap = false)
public abstract class PortableFluidInterfaceMixin {

    @Shadow
    protected IFluidHandler capability;

    @Inject(method = "startTransferringTo", at = @At("TAIL"))
    private void createStats$wrapWithManifestLimits(Contraption contraption, float distance, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }

        if (contraption.entity == null
            || !(contraption.entity instanceof CarriageContraptionEntity)) {
            return;
        }

        IFluidHandler original = this.capability;
        if (original == null) return;
        AbstractContraptionEntity entity = contraption.entity;
        this.capability = new ManifestAwareFluidHandler(original, self, entity);
    }

    @Inject(method = "stopTransferring", at = @At("HEAD"))
    private void createStats$onStopTransfer(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        ManifestUnloadTracker.onInterfaceDisconnect(self.getBlockPos());
    }
}
