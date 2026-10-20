package com.createstats.pducabinet;

import com.createstats.blockentity.PduCabinetBE;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class CabinetTelemetry {
    private static final Set<PduCabinetBE> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private CabinetTelemetry() {}

    public static void watch(PduCabinetBE be) {
        if (be.getLevel() != null && !be.getLevel().isClientSide()) LOADED.add(be);
    }

    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        LOADED.removeIf(PduCabinetBE::isRemoved);
        for (PduCabinetBE be : LOADED) {
            if (be.getLevel() == event.getLevel()) be.captureReadout();
        }
    }
}
