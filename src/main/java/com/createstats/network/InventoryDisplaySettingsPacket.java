package com.createstats.network;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventorySettings;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.inventory.core.SetPointRelay;
import com.createstats.inventory.core.StallDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InventoryDisplaySettingsPacket(BlockPos pos, int op, String key, long a, long b)
        implements CustomPacketPayload {
    public static final int OP_VIEW = 0;
    public static final int OP_UNVIEW = 1;
    public static final int OP_MARKS = 2;
    public static final int OP_RELAY = 3;
    public static final int OP_STALL = 4;
    public static final int OP_CHART_DAYS = 5;
    public static final int OP_PREFER_BEHIND = 6;
    public static final int OP_DIESEL = 7;

    public static final CustomPacketPayload.Type<InventoryDisplaySettingsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "inventory_display_settings"));

    public static final StreamCodec<FriendlyByteBuf, InventoryDisplaySettingsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeVarInt(pkt.op());
                buf.writeUtf(pkt.key(), 256);
                buf.writeVarLong(pkt.a());
                buf.writeVarLong(pkt.b());
            },
            buf -> new InventoryDisplaySettingsPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256),
                    buf.readVarLong(), buf.readVarLong()));

    public static InventoryDisplaySettingsPacket of(BlockPos pos, int op) {
        return new InventoryDisplaySettingsPacket(pos, op, "", 0L, 0L);
    }

    public static InventoryDisplaySettingsPacket of(BlockPos pos, int op, long a) {
        return new InventoryDisplaySettingsPacket(pos, op, "", a, 0L);
    }

    public static InventoryDisplaySettingsPacket marks(BlockPos pos, String key, long low, long high) {
        return new InventoryDisplaySettingsPacket(pos, OP_MARKS, key, low, high);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InventoryDisplaySettingsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final BlockEntity be = player.level().isLoaded(packet.pos()) ? player.level().getBlockEntity(packet.pos()) : null;
            if (!(be instanceof ProductionDetectorBE detector)) {
                return;
            }
            if (packet.op() == OP_UNVIEW) {
                detector.removeViewer(player);
                return;
            }
            if (!SableBridge.isPlayerInInteractionRangeFor(detector, player)) {
                return;
            }
            final InventorySettings s = detector.settings();
            switch (packet.op()) {
                case OP_VIEW -> detector.addViewer(player);
                case OP_MARKS -> detector.setMarks(packet.key(), packet.a(), packet.b());
                case OP_RELAY -> {
                    final SetPointRelay.Mode mode = SetPointRelay.Mode.byOrdinal((int) packet.b());
                    if (packet.a() == 0) {
                        s.lowMode = mode;
                    } else {
                        s.highMode = mode;
                    }
                    detector.settingsChanged();
                }
                case OP_STALL -> {
                    s.stallIndex = Math.floorMod((int) packet.a(), StallDetector.STALL_TICKS.length);
                    detector.settingsChanged();
                }
                case OP_CHART_DAYS -> {
                    s.chartDays = packet.a() == 1 ? 1 : 3;
                    detector.settingsChanged();
                }
                case OP_PREFER_BEHIND -> {
                    s.preferBehind = packet.a() != 0;
                    detector.settingsChanged();
                }
                case OP_DIESEL -> {
                    s.dieselMulti = packet.a() != 0;
                    detector.settingsChanged();
                }
                default -> {
                }
            }
        });
    }
}
