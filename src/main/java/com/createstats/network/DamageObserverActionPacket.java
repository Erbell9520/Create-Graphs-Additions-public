package com.createstats.network;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DamageObserverActionPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DamageObserverActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "damage_observer_action"));

    public static final StreamCodec<FriendlyByteBuf, DamageObserverActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), DamageObserverActionPacket::new);

    private final BlockPos pos;
    private final DamageObserverSettings settings;
    private final Action action;

    public DamageObserverActionPacket(BlockPos pos, DamageObserverSettings settings, Action action) {
        this.pos = pos;
        this.settings = settings.copy();
        this.action = action;
    }

    public DamageObserverActionPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = DamageObserverSettings.decode(buf);
        this.action = buf.readEnum(Action.class);
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
        buf.writeEnum(this.action);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DamageObserverActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof DamageObserverBE observer)) {
                return;
            }
            observer.setSettings(packet.settings);
            switch (packet.action) {
                case RECALIBRATE_BASELINE -> observer.recalibrateBaselineToCurrentStructure();
                case SAVE_STRUCTURE_PRESET -> observer.saveCurrentStructurePreset();
            }
        });
    }

    public enum Action {
        RECALIBRATE_BASELINE,
        SAVE_STRUCTURE_PRESET
    }
}