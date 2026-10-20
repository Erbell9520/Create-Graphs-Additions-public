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

public class DisplayLinkDieselModePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DisplayLinkDieselModePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "display_link_diesel_mode"));
    public static final StreamCodec<FriendlyByteBuf, DisplayLinkDieselModePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), DisplayLinkDieselModePacket::new);

    private final BlockPos pos;
    private final boolean multi;

    public DisplayLinkDieselModePacket(BlockPos pos, boolean multi) {
        this.pos = pos;
        this.multi = multi;
    }

    public DisplayLinkDieselModePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.multi = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.multi);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisplayLinkDieselModePacket packet, IPayloadContext context) {
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
            DieselCompatHelper.setMultiStructureMode(config, packet.multi);
            displayLink.setSourceConfig(config);
            displayLink.setChanged();
            displayLink.sendData();
        });
    }
}
