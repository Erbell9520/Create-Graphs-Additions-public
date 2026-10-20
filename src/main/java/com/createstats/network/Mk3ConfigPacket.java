package com.createstats.network;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.Mk3Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class Mk3ConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Mk3ConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "mk3_config"));

    public static final StreamCodec<FriendlyByteBuf, Mk3ConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), Mk3ConfigPacket::new);

    private final BlockPos pos;
    private final CompoundTag payload;

    public Mk3ConfigPacket(BlockPos pos, Mk3Settings settings) {
        this.pos = pos;
        final CompoundTag tag = new CompoundTag();
        settings.save(tag);
        this.payload = tag;
    }

    public Mk3ConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.payload = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.payload);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Mk3ConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || packet.payload == null) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof StatsDisplayPanelBE panel)) {
                return;
            }
            final StatsDisplayPanelBE controller = panel.getControllerBE();
            if (!controller.isPlayerInInteractionRange(player)) {
                return;
            }
            if (controller instanceof Mk3PanelBlockEntity mk3) {
                mk3.mk3Settings().load(packet.payload);
                mk3.markDirtyAndSync();
            }
        });
    }
}
