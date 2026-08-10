package com.createstats.network;

import com.createstats.data.TimeSeriesStore;
import com.createstats.item.HandheldLinkData.TargetType;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightStationCargoView;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class HandheldStatePacket implements CustomPacketPayload {

    public enum ConnectionState {
        LIVE,
        NO_DATA,
        OUT_OF_RANGE,
        WRONG_DIMENSION,
        MISSING
    }

    public static final CustomPacketPayload.Type<HandheldStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "handheld_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldStatePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), HandheldStatePacket::new);

    private final InteractionHand hand;
    private final TargetType targetType;
    private final ConnectionState connectionState;
    private final String targetName;
    private final String metricLabel;
    private final String statusText;
    private final int signalBars;
    @Nullable
    private final TimeSeriesStore store;
    @Nullable
    private final FreightStationCargoView cargoView;
    @Nullable
    private final DeliveryOrderBoardView deliveryBoard;

    private HandheldStatePacket(InteractionHand hand, TargetType targetType, ConnectionState connectionState, String targetName,
                                String metricLabel, String statusText, int signalBars, @Nullable TimeSeriesStore store,
                                @Nullable FreightStationCargoView cargoView, @Nullable DeliveryOrderBoardView deliveryBoard) {
        this.hand = hand;
        this.targetType = targetType;
        this.connectionState = connectionState;
        this.targetName = targetName;
        this.metricLabel = metricLabel;
        this.statusText = statusText;
        this.signalBars = signalBars;
        this.store = store;
        this.cargoView = cargoView;
        this.deliveryBoard = deliveryBoard;
    }

    public static HandheldStatePacket status(InteractionHand hand, TargetType targetType, ConnectionState connectionState,
                                             String targetName, String metricLabel, String statusText, int signalBars) {
        return new HandheldStatePacket(hand, targetType, connectionState, targetName, metricLabel, statusText, signalBars, null, null, null);
    }

    public static HandheldStatePacket graph(InteractionHand hand, TargetType targetType, ConnectionState connectionState,
                                            String targetName, String metricLabel, String statusText, int signalBars, TimeSeriesStore store) {
        return new HandheldStatePacket(hand, targetType, connectionState, targetName, metricLabel, statusText, signalBars, store, null, null);
    }

    public static HandheldStatePacket dispatch(InteractionHand hand, String targetName, String statusText, int signalBars,
                                               FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        return new HandheldStatePacket(hand, TargetType.DISPATCH_BOARD, ConnectionState.LIVE, targetName, "", statusText,
            signalBars, null, cargoView, deliveryBoard);
    }

    public HandheldStatePacket(RegistryFriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.targetType = buf.readEnum(TargetType.class);
        this.connectionState = buf.readEnum(ConnectionState.class);
        this.targetName = buf.readUtf();
        this.metricLabel = buf.readUtf();
        this.statusText = buf.readUtf();
        this.signalBars = buf.readVarInt();
        this.store = buf.readBoolean() ? TimeSeriesStore.readFromBuf(buf) : null;
        this.cargoView = buf.readBoolean() ? FreightStationCargoView.decode(buf) : null;
        this.deliveryBoard = buf.readBoolean() ? DeliveryOrderBoardView.decode(buf) : null;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeEnum(this.targetType);
        buf.writeEnum(this.connectionState);
        buf.writeUtf(this.targetName);
        buf.writeUtf(this.metricLabel);
        buf.writeUtf(this.statusText);
        buf.writeVarInt(this.signalBars);
        buf.writeBoolean(this.store != null);
        if (this.store != null) {
            this.store.writeToBuf(buf);
        }
        buf.writeBoolean(this.cargoView != null);
        if (this.cargoView != null) {
            this.cargoView.encode(buf);
        }
        buf.writeBoolean(this.deliveryBoard != null);
        if (this.deliveryBoard != null) {
            this.deliveryBoard.encode(buf);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandheldStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handleHandheldState(packet));
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public TargetType getTargetType() {
        return this.targetType;
    }

    public ConnectionState getConnectionState() {
        return this.connectionState;
    }

    public String getTargetName() {
        return this.targetName;
    }

    public String getMetricLabel() {
        return this.metricLabel;
    }

    public String getStatusText() {
        return this.statusText;
    }

    public int getSignalBars() {
        return this.signalBars;
    }

    @Nullable
    public TimeSeriesStore getStore() {
        return this.store;
    }

    @Nullable
    public FreightStationCargoView getCargoView() {
        return this.cargoView;
    }

    @Nullable
    public DeliveryOrderBoardView getDeliveryBoard() {
        return this.deliveryBoard;
    }
}
