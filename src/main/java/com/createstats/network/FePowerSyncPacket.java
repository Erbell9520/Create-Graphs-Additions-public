package com.createstats.network;

import com.createstats.power.FeConsumer;
import com.createstats.power.FePowerSnapshot;
import com.createstats.power.FePriority;
import com.createstats.power.IFeConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public record FePowerSyncPacket(BlockPos pos, FePowerSnapshot snapshot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FePowerSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "fe_power_sync"));

    public static final StreamCodec<FriendlyByteBuf, FePowerSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), FePowerSyncPacket::decode);

    private static final int FLAG_MODE_ENABLED = 1;
    private static final int FLAG_POWERED = 2;
    private static final int FLAG_BURST_STARVED = 4;
    private static final int FLAG_SHED = 8;
    /** Priority ordinal (0..4) is packed into bits 4 to 6 of the flags byte. */
    private static final int PRIORITY_SHIFT = 4;
    private static final int PRIORITY_MASK = 0x07;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        int flags = 0;
        if (snapshot.modeEnabled()) {
            flags |= FLAG_MODE_ENABLED;
        }
        if (snapshot.powered()) {
            flags |= FLAG_POWERED;
        }
        if (snapshot.burstStarved()) {
            flags |= FLAG_BURST_STARVED;
        }
        if (snapshot.shed()) {
            flags |= FLAG_SHED;
        }
        flags |= (snapshot.priority().ordinal() & PRIORITY_MASK) << PRIORITY_SHIFT;
        buf.writeByte(flags);
        buf.writeVarInt(snapshot.energy());
        buf.writeVarInt(snapshot.capacity());
        buf.writeVarInt(snapshot.idleDrain());
        buf.writeVarInt(snapshot.activeDrain());
        buf.writeVarInt(snapshot.burstCost());
        buf.writeVarInt(snapshot.rebootThresholdPct());
        buf.writeVarInt(snapshot.measuredDrain());
        buf.writeVarInt(snapshot.measuredInput());
        buf.writeFloat(snapshot.loadMultiplier());
        // [2]
        buf.writeByte(snapshot.lock().ordinal());
    }

    private static FePowerSyncPacket decode(FriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final int flags = buf.readByte() & 0xFF;
        final int energy = buf.readVarInt();
        final int capacity = buf.readVarInt();
        final int idleDrain = buf.readVarInt();
        final int activeDrain = buf.readVarInt();
        final int burstCost = buf.readVarInt();
        final int rebootThresholdPct = buf.readVarInt();
        final int measuredDrain = buf.readVarInt();
        final int measuredInput = buf.readVarInt();
        final float multiplier = buf.readFloat();
        final int lock = buf.readByte() & 0xFF;
        // The snapshot's compact constructor clamps everything, so a malformed payload cannot
        // produce a negative capacity or a NaN multiplier in a GUI.
        return new FePowerSyncPacket(pos, new FePowerSnapshot(
                (flags & FLAG_MODE_ENABLED) != 0,
                (flags & FLAG_POWERED) != 0,
                (flags & FLAG_BURST_STARVED) != 0,
                energy, capacity, idleDrain, activeDrain, burstCost, rebootThresholdPct,
                measuredDrain, measuredInput, multiplier,
                FePriority.byOrdinal((flags >> PRIORITY_SHIFT) & PRIORITY_MASK),
                (flags & FLAG_SHED) != 0,
                com.createstats.power.FeLock.byOrdinal(lock)));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FePowerSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final Level level = ctx.player().level();
            // [3]
            if (level.getBlockEntity(packet.pos()) instanceof IFeConsumer owner) {
                owner.feConsumer().applyClientSnapshot(packet.snapshot());
            }
        });
    }
}
