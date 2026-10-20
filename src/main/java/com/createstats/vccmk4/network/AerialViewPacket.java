package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.vccmk4.Vcc4ClientBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AerialViewPacket(BlockPos pos, boolean open, AerialView view) implements CustomPacketPayload {
    public static final Type<AerialViewPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_aerial_view"));

    public static final StreamCodec<FriendlyByteBuf, AerialViewPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.open());
                AerialView.write(buf, packet.view());
            },
            buf -> new AerialViewPacket(buf.readBlockPos(), buf.readBoolean(), AerialView.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AerialViewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Vcc4ClientBridge.aerialView(packet));
    }
}
