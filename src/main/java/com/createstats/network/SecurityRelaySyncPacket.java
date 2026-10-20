package com.createstats.network;

import com.createstats.blockentity.SecurityRelayBE;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.relay.RelayContact;
import com.createstats.security.relay.RelayReading;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// one player, only after admits()
public record SecurityRelaySyncPacket(BlockPos pos, int purpose, String house, boolean mayEdit, int contact,
                                      boolean powered, int output, String condition, String behavior, int pulse,
                                      String mode, long revision, boolean pending, int pulseLeft, int truth,
                                      int sources, int unavailable, int sinceTrigger, boolean scoped,
                                      BlockPos scope)
        implements CustomPacketPayload {
    public static final int OPEN = 0;

    public static final int REFRESH = 1;

    public static final int CLOSE = 2;

    public static final CustomPacketPayload.Type<SecurityRelaySyncPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "security_relay_sync"));

    public static final StreamCodec<FriendlyByteBuf, SecurityRelaySyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.purpose());
                buf.writeUtf(clip(p.house(), 32), 32);
                buf.writeBoolean(p.mayEdit());
                buf.writeVarInt(p.contact());
                buf.writeBoolean(p.powered());
                buf.writeVarInt(p.output());
                buf.writeUtf(clip(p.condition(), 64), 64);
                buf.writeUtf(clip(p.behavior(), 16), 16);
                buf.writeVarInt(p.pulse());
                buf.writeUtf(clip(p.mode(), 16), 16);
                buf.writeVarLong(p.revision());
                buf.writeBoolean(p.pending());
                buf.writeVarInt(p.pulseLeft());
                buf.writeVarInt(p.truth());
                buf.writeVarInt(p.sources());
                buf.writeVarInt(p.unavailable());
                buf.writeVarInt(p.sinceTrigger());
                buf.writeBoolean(p.scoped());
                buf.writeBlockPos(p.scope());
            }, buf -> new SecurityRelaySyncPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(32),
                    buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(64),
                    buf.readUtf(16), buf.readVarInt(), buf.readUtf(16), buf.readVarLong(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBlockPos()));

    private static String clip(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }

    public static SecurityRelaySyncPacket open(SecurityRelayBE relay, ServerPlayer viewer) {
        return of(relay, viewer, OPEN);
    }

    public static SecurityRelaySyncPacket refresh(SecurityRelayBE relay, ServerPlayer viewer) {
        return of(relay, viewer, REFRESH);
    }

    public static SecurityRelaySyncPacket close(SecurityRelayBE relay) {
        return new SecurityRelaySyncPacket(relay.getBlockPos(), CLOSE, "", false, 0, false, 0, "", "", 0, "", 0L,
                false, 0, 0, 0, 0, -1, false, relay.getBlockPos());
    }

    private static SecurityRelaySyncPacket of(SecurityRelayBE relay, ServerPlayer viewer, int purpose) {
        final boolean candidate = relay.contact() == RelayContact.CANDIDATE;
        final SecurityHousehold h = candidate ? relay.candidateHousehold() : relay.household();
        final boolean mayEdit = h != null && h.roster().roleOf(viewer.getUUID()).mayEditDevices();
        final long now = relay.getLevel() == null ? 0L : relay.getLevel().getGameTime();
        final long since = relay.lastTriggerAt() == Long.MIN_VALUE ? -1L : Math.max(0L, now - relay.lastTriggerAt());
        final RelayReading r = relay.readingDetail();
        final boolean scoped = relay.config().scope() != null;
        return new SecurityRelaySyncPacket(relay.getBlockPos(), purpose, h == null ? "" : h.house(), mayEdit,
                relay.contact().ordinal(), relay.powered(), relay.output(), relay.config().conditionId(),
                relay.config().behavior().key(), relay.config().pulseTicks(), relay.config().mode(),
                relay.revision(), relay.pending(), relay.pulseLeft(), r.truth().ordinal(), r.sources(),
                r.unavailable(), (int) Math.min(Integer.MAX_VALUE, since), scoped,
                scoped ? BlockPos.of(relay.config().scope().pos()) : relay.getBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecurityRelaySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.util.ClientScreenAccess.acceptSecurityRelay(packet));
    }
}
