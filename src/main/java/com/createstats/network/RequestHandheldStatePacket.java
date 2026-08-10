package com.createstats.network;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.item.HandheldLinkData;
import com.createstats.item.HandheldLinkData.TargetType;
import com.createstats.item.StatsHandheldItem;
import com.createstats.registry.ModItems;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.util.StatsDisplayChannelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RequestHandheldStatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestHandheldStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_handheld_state"));
    public static final StreamCodec<FriendlyByteBuf, RequestHandheldStatePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestHandheldStatePacket::new);

    private final InteractionHand hand;

    public RequestHandheldStatePacket(InteractionHand hand) {
        this.hand = hand;
    }

    public RequestHandheldStatePacket(FriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestHandheldStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }

            ItemStack handheldStack = player.getItemInHand(packet.hand);
            if (!handheldStack.is(ModItems.STATS_HANDHELD.get())) {
                return;
            }

            HandheldLinkData linkData = StatsHandheldItem.getLinkData(handheldStack);
            if (!linkData.isLinked()) {
                return;
            }

            TargetLookup lookup = resolveTarget(player, linkData);
            if (lookup.statusText != null) {
                PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(packet.hand, linkData.getTargetType(),
                    lookup.connectionState, linkData.getTargetName(), "", lookup.statusText, 0));
                return;
            }

            switch (linkData.getTargetType()) {
                case STATS_DISPLAY -> handleDisplay(packet.hand, player, lookup, linkData);
                case STATS_RECORDER -> handleRecorder(packet.hand, player, lookup, linkData);
                case DISPATCH_BOARD -> handleDispatch(packet.hand, player, lookup, linkData);
                default -> {
                }
            }
        });
    }

    private static void handleDisplay(InteractionHand hand, ServerPlayer player, TargetLookup lookup, HandheldLinkData linkData) {
        BlockEntity be = lookup.blockEntity;
        if (!(be instanceof StatsDisplayPanelBE panel)) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_DISPLAY,
                HandheldStatePacket.ConnectionState.MISSING, linkData.getTargetName(), "", "Linked display is missing.", 0));
            return;
        }

        StatsDisplayPanelBE controller = panel.getControllerBE();
        AntennaTier tier = controller.getAntennaTier();
        double maxRange = AntennaRange.directRange(lookup.targetLevel.dimension(), 64.0, tier);
        if (!canReach(player, lookup.targetLevel, controller.getBlockPos(), tier, maxRange, controller.isPlayerInInteractionRange(player))) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_DISPLAY,
                HandheldStatePacket.ConnectionState.OUT_OF_RANGE, linkData.getTargetName(), "", "Display is out of range.", 0));
            return;
        }

        StatsDisplayPanelBE.DataChannel channel = StatsDisplayChannelHelper.selectPrimaryChannel(controller);
        int signalBars = lookup.crossDimensional ? 4 : signalBars(StatsDisplayChannelHelper.getNearestMemberDistanceSqr(player, controller), maxRange);
        if (channel == null || channel.getStore().getSampleCount() == 0) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_DISPLAY,
                HandheldStatePacket.ConnectionState.NO_DATA, linkData.getTargetName(), controller.getSourceType(), "Waiting for panel data.", signalBars));
            return;
        }

        String detail = channel.getLastText().isEmpty() ? controller.getSourceType() : channel.getLastText();
        PacketDistributor.sendToPlayer(player, HandheldStatePacket.graph(hand, TargetType.STATS_DISPLAY,
            HandheldStatePacket.ConnectionState.LIVE, linkData.getTargetName(), channel.getLabel(), detail, signalBars,
            copyStore(channel.getStore())));
    }

    private static void handleRecorder(InteractionHand hand, ServerPlayer player, TargetLookup lookup, HandheldLinkData linkData) {
        BlockEntity be = lookup.blockEntity;
        if (!(be instanceof StatsRecorderBE recorder)) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_RECORDER,
                HandheldStatePacket.ConnectionState.MISSING, linkData.getTargetName(), "", "Linked recorder is missing.", 0));
            return;
        }

        AntennaTier tier = recorder.getAntennaTier();
        double maxRange = AntennaRange.directRange(lookup.targetLevel.dimension(), 128.0, tier);
        double distanceSqr = lookup.crossDimensional ? 0.0 : player.distanceToSqr(targetCenter(recorder.getBlockPos())[0], targetCenter(recorder.getBlockPos())[1], targetCenter(recorder.getBlockPos())[2]);
        if (!canReach(player, lookup.targetLevel, recorder.getBlockPos(), tier, maxRange, distanceSqr <= maxRange * maxRange)) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_RECORDER,
                HandheldStatePacket.ConnectionState.OUT_OF_RANGE, linkData.getTargetName(), recorder.getSourceSummary(), "Recorder is out of range.", 0));
            return;
        }

        int signalBars = lookup.crossDimensional ? 4 : signalBars(distanceSqr, maxRange);
        if (recorder.getStore().getSampleCount() == 0) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.STATS_RECORDER,
                HandheldStatePacket.ConnectionState.NO_DATA, linkData.getTargetName(), recorder.getSourceSummary(), recorder.getStateDisplayName(), signalBars));
            return;
        }

        String detail = recorder.getLastDisplayText().isEmpty() ? recorder.getStateDisplayName() : recorder.getLastDisplayText();
        PacketDistributor.sendToPlayer(player, HandheldStatePacket.graph(hand, TargetType.STATS_RECORDER,
            HandheldStatePacket.ConnectionState.LIVE, linkData.getTargetName(), recorder.getSourceSummary(), detail, signalBars,
            copyStore(recorder.getStore())));
    }

    private static void handleDispatch(InteractionHand hand, ServerPlayer player, TargetLookup lookup, HandheldLinkData linkData) {
        BlockEntity be = lookup.blockEntity;
        if (!(be instanceof DispatchBoardBE board)) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.DISPATCH_BOARD,
                HandheldStatePacket.ConnectionState.MISSING, linkData.getTargetName(), "", "Linked dispatch board is missing.", 0));
            return;
        }

        DispatchBoardBE controller = board.getControllerBE();
        AntennaTier tier = controller.getAntennaTier();
        double maxRange = AntennaRange.directRange(lookup.targetLevel.dimension(), 64.0, tier);
        if (!canReach(player, lookup.targetLevel, controller.getBlockPos(), tier, maxRange, controller.isPlayerInInteractionRange(player))) {
            PacketDistributor.sendToPlayer(player, HandheldStatePacket.status(hand, TargetType.DISPATCH_BOARD,
                HandheldStatePacket.ConnectionState.OUT_OF_RANGE, linkData.getTargetName(), "", "Cargo dashboard is out of range.", 0));
            return;
        }

        controller.refreshSummary();
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos memberPos : controller.getMultiblockMembers()) {
            nearestDistance = Math.min(nearestDistance, player.distanceToSqr(memberPos.getX() + 0.5, memberPos.getY() + 0.5, memberPos.getZ() + 0.5));
        }
        int signalBars = lookup.crossDimensional ? 4 : signalBars(nearestDistance, maxRange);
        PacketDistributor.sendToPlayer(player, HandheldStatePacket.dispatch(hand, linkData.getTargetName(), controller.getSummaryStatus(), signalBars,
            controller.getCargoView(), controller.getDeliveryBoard()));
    }

    private static TargetLookup resolveTarget(ServerPlayer player, HandheldLinkData linkData) {
        BlockPos targetPos = linkData.getTargetPos();
        if (targetPos == null) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.MISSING, "Linked target is missing.");
        }
        ResourceLocation dimensionId = linkData.getDimensionId();
        if (dimensionId == null || player.getServer() == null) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.MISSING, "Linked target dimension is missing.");
        }
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetLevel = player.getServer().getLevel(targetKey);
        if (targetLevel == null) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.WRONG_DIMENSION, "Target dimension is not available.");
        }
        if (!AntennaRange.isChunkLoaded(targetLevel, targetPos)) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.MISSING, "Target chunk is not loaded.");
        }
        BlockEntity blockEntity = targetLevel.getBlockEntity(targetPos);
        if (blockEntity == null) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.MISSING, "Linked target is missing.");
        }
        boolean crossDimensional = !targetLevel.dimension().location().equals(player.level().dimension().location());
        if (crossDimensional && (!hasTrtAntenna(blockEntity) || !(player.level() instanceof ServerLevel playerLevel)
                || !AntennaRange.canReach(playerLevel, player.blockPosition(), targetLevel, targetPos, AntennaTier.TRT))) {
            return TargetLookup.status(HandheldStatePacket.ConnectionState.WRONG_DIMENSION, "Target needs a loaded TRT antenna link.");
        }
        return new TargetLookup(blockEntity, targetLevel, crossDimensional, null, null);
    }

    private static boolean canReach(ServerPlayer player, ServerLevel targetLevel, BlockPos targetPos, AntennaTier tier, double baseRange,
                                    boolean sameDimensionResult) {
        if (targetLevel.dimension().location().equals(player.level().dimension().location())) {
            return sameDimensionResult;
        }
        return player.level() instanceof ServerLevel playerLevel
                && AntennaRange.canReach(playerLevel, player.blockPosition(), targetLevel, targetPos, tier, baseRange);
    }

    private static boolean hasTrtAntenna(BlockEntity blockEntity) {
        if (blockEntity instanceof StatsDisplayPanelBE panel) {
            return panel.getControllerBE().getAntennaTier() == AntennaTier.TRT;
        }
        if (blockEntity instanceof DispatchBoardBE board) {
            return board.getControllerBE().getAntennaTier() == AntennaTier.TRT;
        }
        return blockEntity instanceof RangeAttachment attachment && attachment.getAntennaTier() == AntennaTier.TRT;
    }

    private static double[] targetCenter(BlockPos pos) {
        return new double[]{pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5};
    }

    private static int signalBars(double distanceSqr, double maxRange) {
        double distance = Math.sqrt(Math.max(0.0, distanceSqr));
        double normalized = Math.max(0.0, 1.0 - distance / maxRange);
        if (normalized <= 0.0) {
            return 0;
        }
        return Math.max(1, Math.min(4, (int)Math.ceil(normalized * 4.0)));
    }

    private static TimeSeriesStore copyStore(TimeSeriesStore original) {
        return original.copyPreview(TimeSeriesStore.DEFAULT_SYNC_PREVIEW_SAMPLES);
    }

    private record TargetLookup(BlockEntity blockEntity, ServerLevel targetLevel, boolean crossDimensional,
                                HandheldStatePacket.ConnectionState connectionState, String statusText) {
        private static TargetLookup status(HandheldStatePacket.ConnectionState state, String statusText) {
            return new TargetLookup(null, null, false, state, statusText);
        }
    }
}
