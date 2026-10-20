package com.createstats.network;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.runtime.FccHudSubscriptionState;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PilotHelmetFccSubscriptionPacket implements CustomPacketPayload {
    private static final double MAX_LINK_DISTANCE_SQR = 256.0D * 256.0D;

    public static final Type<PilotHelmetFccSubscriptionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pilot_helmet_fcc_subscription"));
    public static final StreamCodec<FriendlyByteBuf, PilotHelmetFccSubscriptionPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), PilotHelmetFccSubscriptionPacket::new);

    private final long controllerPosLong;
    private final boolean enabled;

    public PilotHelmetFccSubscriptionPacket(BlockPos controllerPos, boolean enabled) {
        this(controllerPos == null ? BlockPos.ZERO.asLong() : controllerPos.asLong(), enabled);
    }

    private PilotHelmetFccSubscriptionPacket(long controllerPosLong, boolean enabled) {
        this.controllerPosLong = controllerPosLong;
        this.enabled = enabled;
    }

    public PilotHelmetFccSubscriptionPacket(FriendlyByteBuf buf) {
        this(buf.readLong(), buf.readBoolean());
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeLong(this.controllerPosLong);
        buf.writeBoolean(this.enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PilotHelmetFccSubscriptionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos controllerPos = BlockPos.of(packet.controllerPosLong);
            if (!packet.enabled) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            ItemStack helmetStack = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!(helmetStack.getItem() instanceof PilotHelmetItem)) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            PilotHelmetData data = PilotHelmetItem.getData(helmetStack);
            PilotHelmetData.LinkedNode link = data.getFccLink();
            if (!link.isLinked() || link.getTargetPos() == null || link.getDimensionId() == null) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            if (!player.level().dimension().location().equals(link.getDimensionId()) || !controllerPos.equals(link.getTargetPos())) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            if (player.distanceToSqr(controllerPos.getX() + 0.5D, controllerPos.getY() + 0.5D, controllerPos.getZ() + 0.5D) > MAX_LINK_DISTANCE_SQR) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            if (!(player.level().getBlockEntity(controllerPos) instanceof FlightControlComputerBE)) {
                FccHudSubscriptionState.unsubscribe(player.getUUID(), controllerPos);
                return;
            }
            FccHudSubscriptionState.subscribe(player.getUUID(), controllerPos);
        });
    }
}
