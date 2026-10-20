package com.createstats.client.dynlight;

import net.minecraft.client.multiplayer.ClientLevel;

@FunctionalInterface
public interface LightSource {
    void collect(ClientLevel level, LightSink sink);
}
