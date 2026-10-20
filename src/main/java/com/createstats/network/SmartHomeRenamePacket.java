package com.createstats.network;

import com.createstats.blockentity.SmartHomePanelBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SmartHomeRenamePacket(BlockPos panelPos, int kind, BlockPos target, int id, String name)
        implements CustomPacketPayload {
    public static final int ROW = 0;
    public static final int ROOM = 1;
    public static final int SCENE = 2;

    public static final int HOUSE = 3;

    private static final int MAX_LENGTH = 48;

    public static final CustomPacketPayload.Type<SmartHomeRenamePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "smart_home_rename"));

    public static final StreamCodec<FriendlyByteBuf, SmartHomeRenamePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.kind());
                buf.writeBlockPos(pkt.target());
                buf.writeVarInt(pkt.id());
                buf.writeUtf(pkt.name(), MAX_LENGTH);
            }, buf -> new SmartHomeRenamePacket(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readUtf(MAX_LENGTH)));

    public static SmartHomeRenamePacket row(BlockPos panel, BlockPos anchor, String name) {
        return new SmartHomeRenamePacket(panel, ROW, anchor, 0, name);
    }

    public static SmartHomeRenamePacket room(BlockPos panel, int roomId, String name) {
        return new SmartHomeRenamePacket(panel, ROOM, panel, roomId, name);
    }

    public static SmartHomeRenamePacket scene(BlockPos panel, int slot, String name) {
        return new SmartHomeRenamePacket(panel, SCENE, panel, slot, name);
    }

    public static SmartHomeRenamePacket house(BlockPos panel, String name) {
        return new SmartHomeRenamePacket(panel, HOUSE, panel, 0, name);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmartHomeRenamePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final BlockPos at = packet.panelPos();

            if (!player.level().isLoaded(at)
                    || player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)
                            > 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(at) instanceof SmartHomePanelBE panel)) {
                return;
            }
            switch (packet.kind()) {
                case ROW -> panel.setCustomName(packet.target(), packet.name());
                case ROOM -> panel.house().renameRoom(packet.id(), packet.name());
                case SCENE -> panel.house().renameScene(packet.id(), packet.name());
                case HOUSE -> panel.setTitle(packet.name());
                default -> {
                }
            }
            panel.setChanged();

            PacketDistributor.sendToPlayer(player, SmartHomeSyncPacket.of(panel, -1, false));
        });
    }
}
