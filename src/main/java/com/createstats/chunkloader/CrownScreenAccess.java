package com.createstats.chunkloader;

import com.createstats.chunkloader.net.CrownMapDataPacket;
import com.createstats.chunkloader.net.CrownPickReplyPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class CrownScreenAccess {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/crown_map");
    private static final String CLIENT = "com.createstats.gui.crown.CrownMapClient";

    private CrownScreenAccess() {
    }

    public static void openMap(BlockPos crown) {
        invoke("open", BlockPos.class, crown);
    }

    public static void mapData(CrownMapDataPacket packet) {
        invoke("data", CrownMapDataPacket.class, packet);
    }

    public static void pickReply(CrownPickReplyPacket packet) {
        invoke("reply", CrownPickReplyPacket.class, packet);
    }

    private static void invoke(String name, Class<?> type, Object arg) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            final Method m = Class.forName(CLIENT).getMethod(name, type);
            m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            LOG.error("Spark Crown map: could not reach the client ({})", name, e);
        }
    }
}
