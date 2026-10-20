package com.createstats.battle.net;

import com.createstats.CreateStats;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SectionMarkerPacket(BlockPos pos, String name, int cover) implements CustomPacketPayload {
    public static final int MAX_NAME = 24;

    public static final Type<SectionMarkerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "section_marker"));

    public static final StreamCodec<FriendlyByteBuf, SectionMarkerPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.name(), MAX_NAME * 4);
                buf.writeVarInt(p.cover());
            },
            buf -> new SectionMarkerPacket(buf.readBlockPos(), buf.readUtf(MAX_NAME * 4), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SectionMarkerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.pos());
            if (be instanceof DamageObserverBE marker && SableBridge.isPlayerInInteractionRangeFor(be, player)) {
                marker.configure(packet.name(), packet.cover());
            }
        });
    }
}
