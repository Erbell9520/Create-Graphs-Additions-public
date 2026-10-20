package com.createstats.network;

import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.security.LockerCode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockerKeypadPacket(BlockPos pos, String code) implements CustomPacketPayload {
    private static final double REACH_SQ = 64.0;

    public static final CustomPacketPayload.Type<LockerKeypadPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "locker_keypad"));

    public static final StreamCodec<FriendlyByteBuf, LockerKeypadPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeUtf(pkt.code(), LockerCode.DIGITS);
            }, buf -> new LockerKeypadPacket(buf.readBlockPos(), buf.readUtf(LockerCode.DIGITS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockerKeypadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            if (sender.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) > REACH_SQ) {
                return;
            }
            final SecurityLockerBE locker = SecurityLockerBE.at(sender.level(), packet.pos());
            if (locker == null) {
                return;
            }

            if (locker.mayClaimCode()) {
                if (locker.claimCode(sender, packet.code())) {
                    com.createstats.block.SecurityLockerBlock.thunk(sender.level(), packet.pos(), true);
                    sender.openMenu(locker, buf ->
                        com.createstats.menu.SecurityLockerMenu.writeOpenData(locker, buf));
                    return;
                }
                com.createstats.block.SecurityLockerBlock.deny(sender.level(), packet.pos());
                PacketDistributor.sendToPlayer(sender, new OpenLockerKeypadPacket(packet.pos(),
                        LockerCode.Result.MALFORMED.ordinal(), 0, true));
                return;
            }

            final LockerCode.Result result = locker.tryCode(sender, packet.code());
            if (result == LockerCode.Result.OPENED) {
                com.createstats.block.SecurityLockerBlock.thunk(sender.level(), packet.pos(), true);
                sender.openMenu(locker, buf ->
                        com.createstats.menu.SecurityLockerMenu.writeOpenData(locker, buf));
                return;
            }
            com.createstats.block.SecurityLockerBlock.deny(sender.level(), packet.pos());
            PacketDistributor.sendToPlayer(sender, new OpenLockerKeypadPacket(packet.pos(),
                    result.ordinal(), (locker.keypadLockoutRemaining() + 19) / 20, false));
        });
    }
}
