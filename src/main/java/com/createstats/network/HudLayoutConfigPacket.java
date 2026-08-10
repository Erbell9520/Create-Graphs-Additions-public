package com.createstats.network;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.PanelHudSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class HudLayoutConfigPacket implements CustomPacketPayload {
    public static final Type<HudLayoutConfigPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "hud_layout_config"));
    public static final StreamCodec<FriendlyByteBuf, HudLayoutConfigPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), HudLayoutConfigPacket::new);

    private final BlockPos pos;
    private final CompoundTag hudSettingsTag;

    public HudLayoutConfigPacket(BlockPos pos, CompoundTag hudSettingsTag) {
        this.pos = pos;
        this.hudSettingsTag = sanitize(hudSettingsTag);
    }

    public HudLayoutConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        final CompoundTag tag = buf.readNbt();
        this.hudSettingsTag = sanitize(tag);
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.hudSettingsTag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HudLayoutConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            final BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof StatsDisplayPanelBE panel)) {
                return;
            }
            final StatsDisplayPanelBE target = panel.getControllerBE();
            if (!target.isPlayerInInteractionRange(player)) {
                return;
            }
            target.getSettings().setPanelHudSettings(PanelHudSettings.load(packet.hudSettingsTag));
            target.normalizeSettingsState();
            target.refreshDerivedDataAfterSettingsChange();
            target.markDirtyAndSync();
        });
    }

    private static CompoundTag sanitize(CompoundTag tag) {
        if (tag == null) {
            return new CompoundTag();
        }
        return PanelHudSettings.load(tag).save();
    }
}
