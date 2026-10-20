package com.createstats.vacuum;

import com.createstats.network.VacuumGulpPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VacuumNet {
    private VacuumNet() {
    }

    public static void gulp(Level level, Vec3 from, Vec3 to, ItemStack stack, int seed, float radius) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingChunk(server, new ChunkPos(net.minecraft.core.BlockPos.containing(to)),
                new VacuumGulpPacket(from, to, stack, seed, radius));
    }
}
