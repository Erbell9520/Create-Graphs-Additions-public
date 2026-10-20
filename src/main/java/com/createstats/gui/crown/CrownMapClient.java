package com.createstats.gui.crown;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.net.CrownMapDataPacket;
import com.createstats.chunkloader.net.CrownPickReplyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CrownMapClient {
    private CrownMapClient() {
    }

    public static void open(BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof SparkCrownBE be) {
            mc.setScreen(new CrownMapScreen(be));
        }
    }

    public static void data(CrownMapDataPacket packet) {
        if (Minecraft.getInstance().screen instanceof CrownMapScreen s && s.pos().equals(packet.crown())) {
            s.accept(packet);
        }
    }

    public static void reply(CrownPickReplyPacket packet) {
        if (Minecraft.getInstance().screen instanceof CrownMapScreen s && s.pos().equals(packet.crown())) {
            s.reply(packet);
        }
    }
}
