package com.createstats.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VacuumGulpPacket(Vec3 from, Vec3 to, ItemStack stack, int seed, float radius)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VacuumGulpPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "vacuum_gulp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VacuumGulpPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                writeVec(buf, pkt.from);
                writeVec(buf, pkt.to);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.stack.copyWithCount(1));
                buf.writeInt(pkt.seed);
                buf.writeFloat(pkt.radius);
            },
            buf -> new VacuumGulpPacket(readVec(buf), readVec(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readInt(), buf.readFloat()));

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VacuumGulpPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.client.VacuumFlights.add(packet.from, packet.to, packet.stack,
                packet.seed, packet.radius));
    }
}
