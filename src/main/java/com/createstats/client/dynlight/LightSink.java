package com.createstats.client.dynlight;

import com.createstats.dynlight.Light;
import net.minecraft.world.phys.Vec3;

public interface LightSink {
    void add(Light light);

    Vec3 camera();

    double maxDistance();
}
