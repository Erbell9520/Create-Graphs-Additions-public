package com.createstats.network;

import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.DeliveryOrderAction;
import com.createstats.train.manifest.DeliveryOrderBoard;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderLineState;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLine;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightManifestStationState;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FreightManifestDeliveryOrderActionPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreightManifestDeliveryOrderActionPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "freight_manifest_delivery_order_action"));
    
    public static final StreamCodec<FriendlyByteBuf, FreightManifestDeliveryOrderActionPacket> STREAM_CODEC = 
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), FreightManifestDeliveryOrderActionPacket::new);

    private final BlockPos pos;
    @Nullable
    private final UUID lineId;
    private final DeliveryOrderAction action;

    public FreightManifestDeliveryOrderActionPacket(BlockPos pos, @Nullable UUID lineId, DeliveryOrderAction action) {
        this.pos = pos;
        this.lineId = lineId;
        this.action = action;
    }

    public FreightManifestDeliveryOrderActionPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.lineId = buf.readBoolean() ? buf.readUUID() : null;
        this.action = buf.readEnum(DeliveryOrderAction.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.lineId != null);
        if (this.lineId != null) {
            buf.writeUUID(this.lineId);
        }
        buf.writeEnum(this.action);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreightManifestDeliveryOrderActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            FreightStationLink link;
            boolean dispatchBoardSource = false;
            DispatchBoardBE resolvedController = null;
            if (be instanceof FreightManifestClipboardBE clipboard) {
                link = clipboard.getStationLink();
            } else if (be instanceof DispatchBoardBE clickedBoard) {
                // Resolve to controller for freight data
                resolvedController = clickedBoard.getControllerBE();
                link = resolvedController.getStationLink();
                dispatchBoardSource = true;
            } else {
                return;
            }
            if (!link.isLinked() || link.getStationDimension() == null || link.getStationId() == null) {
                return;
            }

            ServerLevel stationLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, link.getStationDimension()));
            if (stationLevel == null) {
                return;
            }

            FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
            FreightManifest manifest = savedData.getManifest(link.getStationId());
            if (manifest == null || manifest.isEmpty()) {
                return;
            }

            if (manifest.getOwnerId() != null && !manifest.getOwnerId().equals(player.getUUID()) && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.no_permission").withStyle(ChatFormatting.RED), false);
                return;
            }

            FreightManifestStationState stationState = savedData.getOrCreateStationState(link.getStationId());

            FreightManifestLine line = packet.lineId == null ? null : findLine(manifest, packet.lineId);
            if (packet.lineId != null && line == null) {
                return;
            }

            long currentTick = stationLevel.getGameTime();
            boolean changed = false;
            DeliveryOrderBoard persistentBoard = stationState.getMutablePersistentOrders();
            changed |= FreightManifestLogic.refreshPersistentCycles(manifest, persistentBoard, currentTick, stationState);

            CompoundTag activeContext = null;
            DeliveryOrderBoard arrivalBoard = null;
            StationBlockEntity stationBlockEntity = FreightManifestStationResolver.resolveStationBlockEntity(stationLevel, link);
            if (stationBlockEntity != null) {
                GlobalStation station = stationBlockEntity.getStation();
                Train presentTrain = station == null ? null : station.getPresentTrain();
                activeContext = FreightManifestLogic.findActiveManifestContext(presentTrain);
                if (activeContext != null) {
                    arrivalBoard = FreightManifestLogic.loadArrivalBoard(activeContext);
                }
            }

            if (packet.lineId == null) {
                int affected = 0;
                affected += FreightManifestLogic.applyBulkOrderAction(manifest, persistentBoard, packet.action, currentTick);
                if (arrivalBoard != null) {
                    affected += FreightManifestLogic.applyBulkOrderAction(manifest, arrivalBoard, packet.action, currentTick);
                    if (affected > 0) {
                        FreightManifestLogic.saveArrivalBoard(activeContext, arrivalBoard);
                    }
                }
                changed |= affected > 0;
                if (affected > 0) {
                    appendBulkHistory(stationState, packet.action, affected, currentTick);
                    savedData.setDirty();
                }
            } else if (line.getResetMode().isPersistent()) {
                DeliveryOrderLineState state = persistentBoard.getOrCreate(packet.lineId, currentTick);
                boolean lineChanged = FreightManifestLogic.applyOrderAction(line, state, packet.action, currentTick);
                changed |= lineChanged;
                if (lineChanged) {
                    appendHistory(stationState, line, state, packet.action, currentTick);
                    savedData.setDirty();
                }
            } else {
                if (activeContext == null || arrivalBoard == null) {
                    player.displayClientMessage(Component.translatable("message.create_stats.freight_manifest.arrival_only_locked")
                        .withStyle(ChatFormatting.RED), false);
                    return;
                }
                DeliveryOrderLineState state = arrivalBoard.getOrCreate(packet.lineId, currentTick);
                boolean lineChanged = FreightManifestLogic.applyOrderAction(line, state, packet.action, currentTick);
                changed |= lineChanged;
                if (lineChanged) {
                    appendHistory(stationState, line, state, packet.action, currentTick);
                    FreightManifestLogic.saveArrivalBoard(activeContext, arrivalBoard);
                    savedData.setDirty();
                }
            }

            if (!changed) {
                return;
            }

            if (dispatchBoardSource) {
                if (resolvedController != null) {
                    resolvedController.refreshSummary();
                    PacketDistributor.sendToPlayer(player,
                        new DispatchBoardStatePacket(resolvedController.getBlockPos(), resolvedController.getManifest(), resolvedController.getCargoView(), resolvedController.getDeliveryBoard()));
                }
            } else {
                FreightStationCargoView cargoView = FreightManifestStationResolver.captureCargoView(player.server, link);
                DeliveryOrderBoardView deliveryBoard = FreightManifestStationResolver.captureDeliveryBoard(player.server, link);
                PacketDistributor.sendToPlayer(player,
                    new FreightManifestClipboardStatePacket(packet.pos, cargoView, deliveryBoard));
            }
        });
    }

    private static FreightManifestLine findLine(FreightManifest manifest, UUID lineId) {
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getLineId().equals(lineId)) {
                return line;
            }
        }
        return null;
    }

    private static void appendHistory(FreightManifestStationState stationState, FreightManifestLine line,
                                      DeliveryOrderLineState state, DeliveryOrderAction action, long currentTick) {
        switch (action) {
            case MARK_COMPLETE ->
                stationState.addHistory(com.createstats.train.manifest.DeliveryOrderHistoryType.MANUAL_COMPLETE,
                    currentTick, "Marked Complete", FreightManifestLogic.describeLine(line));
            case RESET_CYCLE ->
                stationState.addHistory(com.createstats.train.manifest.DeliveryOrderHistoryType.MANUAL_RESET,
                    currentTick, "Manual Reset", FreightManifestLogic.describeLine(line));
            case TOGGLE_DISMISSED ->
                stationState.addHistory(
                    state.isDismissed() ? com.createstats.train.manifest.DeliveryOrderHistoryType.HIDDEN : com.createstats.train.manifest.DeliveryOrderHistoryType.SHOWN,
                    currentTick,
                    state.isDismissed() ? "Hidden" : "Shown",
                    FreightManifestLogic.describeLine(line));
            case HIDE_COMPLETED, SHOW_HIDDEN, RESET_COMPLETED -> {
            }
        }
    }

    private static void appendBulkHistory(FreightManifestStationState stationState, DeliveryOrderAction action, int affected, long currentTick) {
        String detail = affected + (affected == 1 ? " order" : " orders");
        switch (action) {
            case HIDE_COMPLETED ->
                stationState.addHistory(com.createstats.train.manifest.DeliveryOrderHistoryType.HIDDEN, currentTick, "Hide Completed", detail);
            case SHOW_HIDDEN ->
                stationState.addHistory(com.createstats.train.manifest.DeliveryOrderHistoryType.SHOWN, currentTick, "Show Hidden", detail);
            case RESET_COMPLETED ->
                stationState.addHistory(com.createstats.train.manifest.DeliveryOrderHistoryType.MANUAL_RESET, currentTick, "Reset Completed", detail);
            default -> {
            }
        }
    }
}
