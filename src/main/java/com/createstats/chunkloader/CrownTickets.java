package com.createstats.chunkloader;

import com.createstats.CreateStats;
import com.createstats.chunkloader.core.CrownRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CrownTickets {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/spark_crown");

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "chunk_station"), CrownTickets::validate);

    private static boolean warned;

    private CrownTickets() {
    }

    @SubscribeEvent
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    static void validate(ServerLevel level, TicketHelper helper) {
        final CrownRegistry registry = CrownRegistry.get(level);
        final String dimension = CrownRegistry.dimensionOf(level);
        int kept = 0;
        int dropped = 0;
        for (Map.Entry<BlockPos, TicketSet> e : helper.getBlockTickets().entrySet()) {
            final BlockPos owner = e.getKey();

            for (long chunk : e.getValue().nonTicking()) {
                helper.removeTicket(owner, chunk, false);
                dropped++;
            }
            for (long chunk : e.getValue().ticking()) {
                if (registry.keepsTicket(dimension, owner.asLong(), chunk)) {
                    kept++;
                } else {
                    helper.removeTicket(owner, chunk, true);
                    dropped++;
                }
            }
        }
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(owner);
            dropped++;
        }
        if (kept > 0 || dropped > 0) {
            LOG.info("Spark Crown tickets in {}: kept {}, dropped {}", dimension, kept, dropped);
        }
    }

    public static boolean hold(ServerLevel level, BlockPos station, long chunk) {
        try {
            CONTROLLER.forceChunk(level, station, CrownRegion.x(chunk), CrownRegion.z(chunk), true, true);
            return true;
        } catch (RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    public static void release(ServerLevel level, BlockPos station, long chunk) {
        try {
            CONTROLLER.forceChunk(level, station, CrownRegion.x(chunk), CrownRegion.z(chunk), false, true);
        } catch (RuntimeException e) {
            warnOnce(e);
        }
    }

    private static void warnOnce(RuntimeException e) {
        if (!warned) {
            warned = true;
            LOG.warn("Spark Crown chunk ticket refused: {}", e.toString());
        }
    }
}
