package com.createstats.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// not create's packet, every ContraptionBlockChangedPacket rebuilds every BE copy in the car. a charging bank does that 8x per block
public class DeviceSyncPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DeviceSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "device_sync"));
    public static final StreamCodec<FriendlyByteBuf, DeviceSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), DeviceSyncPacket::new);

    public record Entry(BlockPos localPos, @Nullable BlockState state, CompoundTag tag) {
    }

    private final int entityId;
    private final List<Entry> entries;

    public DeviceSyncPacket(int entityId, List<Entry> entries) {
        this.entityId = entityId;
        this.entries = List.copyOf(entries);
    }

    public DeviceSyncPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        final int n = buf.readVarInt();
        final List<Entry> read = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final BlockPos pos = buf.readBlockPos();
            final BlockState state = buf.readBoolean() ? Block.stateById(buf.readVarInt()) : null;
            final CompoundTag tag = buf.readNbt();
            read.add(new Entry(pos, state, tag == null ? new CompoundTag() : tag));
        }
        this.entries = read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeBlockPos(entry.localPos());
            buf.writeBoolean(entry.state() != null);
            if (entry.state() != null) {
                buf.writeVarInt(Block.getId(entry.state()));
            }
            buf.writeNbt(entry.tag());
        }
    }

    public int entityId() {
        return entityId;
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeviceSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.createstats.client.train.CarriedDeviceClient.apply(packet);
            }
        });
    }
}
