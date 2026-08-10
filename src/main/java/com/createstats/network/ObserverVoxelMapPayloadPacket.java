package com.createstats.network;

import com.createstats.battle.DamageObserverVoxelMap;
import com.createstats.client.battle.ClientObserverVoxelCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public class ObserverVoxelMapPayloadPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ObserverVoxelMapPayloadPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "observer_voxel_map_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ObserverVoxelMapPayloadPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), ObserverVoxelMapPayloadPacket::new);

    private final BlockPos observerPos;
    private final DamageObserverVoxelMap voxelMap;

    public ObserverVoxelMapPayloadPacket(BlockPos observerPos, DamageObserverVoxelMap voxelMap) {
        this.observerPos = observerPos;
        this.voxelMap = voxelMap;
    }

    public ObserverVoxelMapPayloadPacket(RegistryFriendlyByteBuf buf) {
        this.observerPos = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        this.voxelMap = tag == null ? null : DamageObserverVoxelMap.fromNbt(tag, buf.registryAccess());
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.observerPos);
        buf.writeNbt(this.voxelMap == null ? new CompoundTag() : this.voxelMap.toNbt(buf.registryAccess()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public BlockPos getObserverPos() {
        return this.observerPos;
    }

    public DamageObserverVoxelMap getVoxelMap() {
        return this.voxelMap;
    }

    public static void handle(ObserverVoxelMapPayloadPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientObserverVoxelCache.acceptPayload(packet.observerPos, packet.voxelMap));
    }
}
