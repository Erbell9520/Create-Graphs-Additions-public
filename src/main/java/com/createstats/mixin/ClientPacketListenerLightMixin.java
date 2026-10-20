package com.createstats.mixin;

import com.createstats.client.dynlight.ClientDynamicLights;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLightMixin {
    @Inject(method = "applyLightData", at = @At("TAIL"), require = 0)
    private void createstats$reassertDynamicLights(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data,
                                                   CallbackInfo ci) {
        ClientDynamicLights.onServerLightData(chunkX, chunkZ);
    }
}
