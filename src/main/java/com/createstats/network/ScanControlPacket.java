package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import com.createstats.blockentity.ScanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ScanControlPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final Action action;
    private ScanSettings settings;
    private String sourceId;

    public static final CustomPacketPayload.Type<ScanControlPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "scan_control"));
    public static final StreamCodec<FriendlyByteBuf, ScanControlPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), ScanControlPacket::new);

    private ScanControlPacket(BlockPos pos, Action action) {
        this.pos = pos;
        this.action = action;
    }

    public static ScanControlPacket triggerScan(BlockPos pos) {
        return new ScanControlPacket(pos, Action.TRIGGER_SCAN);
    }

    public static ScanControlPacket updateSettings(BlockPos pos, ScanSettings settings) {
        final ScanControlPacket pkt = new ScanControlPacket(pos, Action.UPDATE_SETTINGS);
        pkt.settings = settings;
        return pkt;
    }

    public static ScanControlPacket toggleSource(BlockPos pos, String sourceId) {
        final ScanControlPacket pkt = new ScanControlPacket(pos, Action.TOGGLE_SOURCE);
        pkt.sourceId = sourceId;
        return pkt;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeEnum((Enum)this.action);
        switch (this.action) {
            case UPDATE_SETTINGS: {
                this.settings.encode(buf);
                break;
            }
            case TOGGLE_SOURCE: {
                buf.writeUtf(this.sourceId, 256);
                break;
            }
        }
    }

    public ScanControlPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = (Action)buf.readEnum((Class)Action.class);
        switch (this.action) {
            case UPDATE_SETTINGS: {
                this.settings = ScanSettings.decode(buf);
                break;
            }
            case TOGGLE_SOURCE: {
                this.sourceId = buf.readUtf(256);
                break;
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScanControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    final StatsDisplayPanelBE target = panel.getControllerBE();
                    if (!target.isPlayerInInteractionRange(player)) {
                        return;
                    }
                    switch (packet.action) {
                        case TRIGGER_SCAN: {
                            target.triggerManualScan();
                            break;
                        }
                        case UPDATE_SETTINGS: {
                            final ScanSettings current = target.getScanSettings();
                            current.setScanMode(packet.settings.getScanMode());
                            current.setScanRadius(packet.settings.getScanRadius());
                            current.setRadiusUnlocked(packet.settings.isRadiusUnlocked());
                            current.setAutoIntervalTicks(packet.settings.getAutoIntervalTicks());
                            target.markDirtyAndSync();
                            break;
                        }
                        case TOGGLE_SOURCE: {
                            target.getScanSettings().toggleSource(packet.sourceId);
                            target.markDirtyAndSync();
                            break;
                        }
                    }
                    return;
                }
                else {
                    return;
                }
            }
        });
    }

    public enum Action
    {
        TRIGGER_SCAN,
        UPDATE_SETTINGS,
        TOGGLE_SOURCE;
    }
}
