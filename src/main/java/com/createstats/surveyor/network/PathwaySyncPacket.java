package com.createstats.surveyor.network;

import com.createstats.CreateStats;
import com.createstats.surveyor.SurveyorClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PathwaySyncPacket(boolean full, List<byte[]> pathways, List<String> owners, List<UUID> removed, UUID select)
        implements CustomPacketPayload {
    public static final Type<PathwaySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pathway_sync"));

    public static final StreamCodec<FriendlyByteBuf, PathwaySyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.full());
                buf.writeVarInt(p.pathways().size());
                for (int i = 0; i < p.pathways().size(); i++) {
                    buf.writeByteArray(p.pathways().get(i));
                    buf.writeUtf(i < p.owners().size() ? p.owners().get(i) : "", 64);
                }
                buf.writeVarInt(p.removed().size());
                for (UUID id : p.removed()) buf.writeUUID(id);
                buf.writeUUID(p.select());
            },
            buf -> {
                boolean full = buf.readBoolean();
                int n = Math.min(buf.readVarInt(), 4096);
                List<byte[]> list = new ArrayList<>(n);
                List<String> owners = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(buf.readByteArray(1 << 20));
                    owners.add(buf.readUtf(64));
                }
                int r = Math.min(buf.readVarInt(), 4096);
                List<UUID> removed = new ArrayList<>(r);
                for (int i = 0; i < r; i++) removed.add(buf.readUUID());
                return new PathwaySyncPacket(full, list, owners, removed, buf.readUUID());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PathwaySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SurveyorClientBridge.call("sync", PathwaySyncPacket.class, packet));
    }
}
