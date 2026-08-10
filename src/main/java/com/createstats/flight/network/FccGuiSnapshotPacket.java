package com.createstats.flight.network;

import com.createstats.CreateStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

public record FccGuiSnapshotPacket(int containerId, FccGuiSnapshot snapshot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FccGuiSnapshotPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_gui_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccGuiSnapshotPacket> STREAM_CODEC =
        StreamCodec.of(FccGuiSnapshotPacket::encode, FccGuiSnapshotPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FccGuiSnapshotPacket pkt) {
        buf.writeVarInt(pkt.containerId);
        pkt.snapshot.encode(buf);
    }

    private static FccGuiSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        int cid = buf.readVarInt();
        FccGuiSnapshot snap = FccGuiSnapshot.decode(buf);
        return new FccGuiSnapshotPacket(cid, snap);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FccGuiSnapshotPacket pkt, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ctx.enqueueWork(() -> invokeClient(pkt));
    }

    private static void invokeClient(FccGuiSnapshotPacket pkt) {
        try {
            Class<?> c = Class.forName("com.createstats.flight.client.FccClientState");
            Method m = c.getMethod("applySnapshot", FccGuiSnapshotPacket.class);
            m.invoke(null, pkt);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FCC snapshot client hook failed", e);
        }
    }
}

