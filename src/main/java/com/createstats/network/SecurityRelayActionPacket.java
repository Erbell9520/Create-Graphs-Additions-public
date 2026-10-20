package com.createstats.network;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.blockentity.SecurityRelayBE;
import com.createstats.block.SecurityRelayBlock;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

public record SecurityRelayActionPacket(BlockPos via, boolean viaPanel, BlockPos relay, int action,
                                        String condition, String behavior, int pulse, String mode,
                                        Optional<BlockPos> scope, long revision) implements CustomPacketPayload {
    public static final int REFRESH = 0;

    public static final int CONFIGURE = 1;

    public static final int RESET = 2;

    public static final int ACCEPT = 3;

    public static final int RETIRE = 4;

    private static final double REACH_SQ = 64.0;

    public static final CustomPacketPayload.Type<SecurityRelayActionPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "security_relay_action"));

    public static final StreamCodec<FriendlyByteBuf, SecurityRelayActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.via());
                buf.writeBoolean(pkt.viaPanel());
                buf.writeBlockPos(pkt.relay());
                buf.writeVarInt(pkt.action());
                buf.writeUtf(clip(pkt.condition(), 64), 64);
                buf.writeUtf(clip(pkt.behavior(), 16), 16);
                buf.writeVarInt(pkt.pulse());
                buf.writeUtf(clip(pkt.mode(), 16), 16);
                buf.writeBoolean(pkt.scope().isPresent());
                pkt.scope().ifPresent(buf::writeBlockPos);
                buf.writeVarLong(pkt.revision());
            }, buf -> new SecurityRelayActionPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readUtf(64), buf.readUtf(16), buf.readVarInt(), buf.readUtf(16),
                    buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty(), buf.readVarLong()));

    private static String clip(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }

    public static SecurityRelayActionPacket simple(BlockPos via, boolean viaPanel, BlockPos relay, int action) {
        return new SecurityRelayActionPacket(via, viaPanel, relay, action, "", "", RelayConfig.DEFAULT_PULSE, "",
                Optional.empty(), 0L);
    }

    public static SecurityRelayActionPacket configure(BlockPos via, boolean viaPanel, BlockPos relay,
                                                      RelayConfig wanted, @Nullable BlockPos scope, long revision) {
        return new SecurityRelayActionPacket(via, viaPanel, relay, CONFIGURE, wanted.conditionId(),
                wanted.behavior().key(), wanted.pulseTicks(), wanted.mode(), Optional.ofNullable(scope), revision);
    }

    RelayConfig wanted(ServerPlayer sender) {
        final RelayBehavior b = RelayBehavior.byKey(this.behavior);
        return new RelayConfig(this.condition, b == null ? RelayBehavior.FOLLOW : b, this.pulse, this.mode,
                this.scope.map(p -> SecurityIdentity.anchorOf(sender.level(), p)).orElse(null));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecurityRelayActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            if (sender.distanceToSqr(packet.via().getX() + 0.5, packet.via().getY() + 0.5,
                    packet.via().getZ() + 0.5) > REACH_SQ) {
                return;
            }
            final BlockEntity be = sender.level().getBlockEntity(packet.via());
            if (packet.viaPanel()) {
                if (!(be instanceof HomeSecurityPanelBE panel) || !panel.admits(sender)) {
                    return;
                }
                panel.refreshAdmission(sender);
                if (packet.action() != REFRESH) {
                    final Component said = panel.relayAction(sender, packet.relay(), packet.action(),
                            packet.wanted(sender), packet.revision());
                    if (said != null) {
                        sender.displayClientMessage(said, true);
                    }
                }
                PacketDistributor.sendToPlayer(sender, SecuritySyncPacket.of(panel, sender));
                return;
            }
            if (!(be instanceof SecurityRelayBE relay) || !packet.via().equals(packet.relay())) {
                return;
            }
            if (!relay.admits(sender)) {
                sender.displayClientMessage(Component.translatable(
                        "message.create_stats.security_relay.not_admitted").withStyle(ChatFormatting.GRAY), true);
                PacketDistributor.sendToPlayer(sender, SecurityRelaySyncPacket.close(relay));
                return;
            }

            relay.refreshAdmission(sender);
            switch (packet.action()) {
                case CONFIGURE -> {
                    final RelayRules.ConfigVerdict v = relay.configure(sender, packet.wanted(sender), packet.revision());
                    sender.displayClientMessage(configVerdict(v), true);
                }
                case RESET -> SecurityRelayBE.tell(sender, relay.pressReset(sender));
                case ACCEPT -> sender.displayClientMessage(Component.translatable(relay.accept(sender)
                        ? "message.create_stats.security_relay.accepted"
                        : "message.create_stats.security_relay.not_accepted")
                        .withStyle(ChatFormatting.GRAY), true);
                default -> {
                }
            }
            PacketDistributor.sendToPlayer(sender, SecurityRelaySyncPacket.refresh(relay, sender));
        });
    }

    public static Component configVerdict(RelayRules.ConfigVerdict v) {
        return Component.translatable("message.create_stats.security_relay.config."
                        + v.name().toLowerCase(Locale.ROOT))
                .withStyle(v.applied() ? ChatFormatting.GREEN
                        : v == RelayRules.ConfigVerdict.UNCHANGED ? ChatFormatting.GRAY : ChatFormatting.RED);
    }

    public static Component retireVerdict(RelayRules.RetireVerdict v) {
        return Component.translatable("message.create_stats.security_relay.retire."
                        + v.name().toLowerCase(Locale.ROOT))
                .withStyle(v == RelayRules.RetireVerdict.RETIRED ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static Component resetVerdict(RelayRules.ResetVerdict v) {
        return SecurityRelayBlock.verdict(v).copy()
                .withStyle(v.done() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
