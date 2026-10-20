package com.createstats.network;

import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.security.LockerCode;
import com.createstats.security.LockerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// mode and code in one packet. two packets + escape in the middle = a brick
public record LockerModePacket(BlockPos pos, int mode, String code) implements CustomPacketPayload {
    private static final double REACH_SQ = 64.0;

    public static final CustomPacketPayload.Type<LockerModePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "locker_mode"));

    public static final StreamCodec<FriendlyByteBuf, LockerModePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeVarInt(pkt.mode());
                buf.writeUtf(pkt.code(), LockerCode.DIGITS);
            }, buf -> new LockerModePacket(buf.readBlockPos(), buf.readVarInt(),
                    buf.readUtf(LockerCode.DIGITS)));

    public static LockerModePacket general(BlockPos pos) {
        return new LockerModePacket(pos, LockerMode.GENERAL.ordinal(), "");
    }

    public static LockerModePacket personal(BlockPos pos, String code) {
        return new LockerModePacket(pos, LockerMode.PERSONAL.ordinal(), code);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockerModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            final SecurityLockerBE locker = SecurityLockerBE.at(sender.level(), packet.pos());
            if (locker == null) {
                return;
            }
            if (sender.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) > REACH_SQ) {
                return;
            }
            final LockerMode wanted = LockerMode.fromOrdinal(packet.mode());
            if (wanted == LockerMode.PERSONAL) {
                if (!locker.goPersonal(sender, packet.code())) {
                    com.createstats.block.SecurityLockerBlock.deny(sender.level(), packet.pos());
                    return;
                }

                sender.displayClientMessage(Component.translatable(
                        "message.create_stats.security_locker.now_personal"), false);

                com.createstats.block.SecurityLockerBlock.thunk(sender.level(), packet.pos(), true);

                sender.openMenu(locker, buf ->
                        com.createstats.menu.SecurityLockerMenu.writeOpenData(locker, buf));
                return;
            }
            if (!locker.chooseGeneral(sender)) {
                com.createstats.block.SecurityLockerBlock.deny(sender.level(), packet.pos());
                return;
            }

            com.createstats.block.SecurityLockerBlock.interact(sender.level(), packet.pos(), sender,
                    locker);
        });
    }
}
