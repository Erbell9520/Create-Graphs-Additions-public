package com.createstats.mixin;

import com.createstats.support.SupportClientState;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeKeyCaptureMixin {
    @Inject(method = "handleHello", at = @At("HEAD"), require = 0)
    private void createstats$captureServerKey(ClientboundHelloPacket packet, CallbackInfo ci) {
        try {
            SupportClientState.captureServerKey(packet.getPublicKey().getEncoded());
        } catch (Exception e) {
            SupportClientState.captureServerKey(null);
        }
    }
}
