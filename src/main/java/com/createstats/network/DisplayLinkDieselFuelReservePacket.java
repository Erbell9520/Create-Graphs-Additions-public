package com.createstats.network;

import com.createstats.compat.DieselCompatHelper;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DisplayLinkDieselFuelReservePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DisplayLinkDieselFuelReservePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "display_link_diesel_fuel_reserve"));
    public static final StreamCodec<FriendlyByteBuf, DisplayLinkDieselFuelReservePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), DisplayLinkDieselFuelReservePacket::new);

    private final BlockPos pos;
    private final boolean enabled;
    private final String fuelType;

    public DisplayLinkDieselFuelReservePacket(BlockPos pos, boolean enabled, String fuelType) {
        this.pos = pos;
        this.enabled = enabled;
        this.fuelType = fuelType;
    }

    public DisplayLinkDieselFuelReservePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.enabled = buf.readBoolean();
        this.fuelType = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.enabled);
        buf.writeUtf(this.fuelType, 32);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisplayLinkDieselFuelReservePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) context.player();
            if (player == null || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
                return;
            }
            final CompoundTag config = displayLink.getSourceConfig().copy();
            DieselCompatHelper.setFuelReserveMode(config, packet.enabled, packet.fuelType);
            displayLink.setSourceConfig(config);
            displayLink.setChanged();
            displayLink.sendData();
        });
    }
}
