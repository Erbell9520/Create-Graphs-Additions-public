package com.createstats.client.train;

import com.createstats.network.DeviceSyncPacket;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.Map;

// patch both the rendered copy and the block map, otherwise a passenger flipping a lever resets every bank
public final class CarriedDeviceClient {
    private CarriedDeviceClient() {
    }

    public static void apply(DeviceSyncPacket packet) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null
                || !(mc.level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity entity)) {
            return;
        }
        final Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return;
        }
        final Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        boolean reshaped = false;
        for (DeviceSyncPacket.Entry entry : packet.entries()) {
            final StructureBlockInfo info = blocks.get(entry.localPos());
            if (info == null) {
                continue;
            }

            CompoundTag nbt = info.nbt();
            if (nbt == null) {
                nbt = entry.tag().copy();
            } else {
                nbt.merge(entry.tag());
            }
            final boolean newState = entry.state() != null && entry.state() != info.state();
            if (newState || info.nbt() == null) {
                blocks.put(entry.localPos(), new StructureBlockInfo(info.pos(),
                        newState ? entry.state() : info.state(), nbt));
                reshaped |= newState;
            }
            final BlockEntity copy = contraption.getBlockEntityClientSide(entry.localPos());
            if (copy != null) {
                if (newState) {
                    copy.setBlockState(entry.state());
                }
                copy.handleUpdateTag(entry.tag(), mc.level.registryAccess());
            }
        }
        if (reshaped) {
            contraption.invalidateClientContraptionStructure();
        }
    }
}
