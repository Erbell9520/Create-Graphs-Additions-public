package com.createstats.network;

import com.createstats.CreateStats;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public class PilotHelmetNightVisionTogglePacket implements CustomPacketPayload {
    public static final Type<PilotHelmetNightVisionTogglePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pilot_helmet_nv_toggle"));
    public static final StreamCodec<FriendlyByteBuf, PilotHelmetNightVisionTogglePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), PilotHelmetNightVisionTogglePacket::new);

    private final boolean desiredEnabled;

    public PilotHelmetNightVisionTogglePacket(boolean desiredEnabled) {
        this.desiredEnabled = desiredEnabled;
    }

    public PilotHelmetNightVisionTogglePacket(FriendlyByteBuf buf) {
        this.desiredEnabled = buf.readBoolean();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.desiredEnabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PilotHelmetNightVisionTogglePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!(worn.getItem() instanceof PilotHelmetItem)) {
                return;
            }
            PilotHelmetData data = PilotHelmetItem.getData(worn);
            boolean previous = data.isNightVisionEnabled();

            if (packet.desiredEnabled && !previous && hasExternalNightVision(player)) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.pilot_helmet.nv_blocked")
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            data.setNightVisionEnabled(packet.desiredEnabled);

            // Clear our own short-lived NV effect on switch-off so the screen
            // darkens immediately. Long-duration effects (potions, beacons) are
            // left alone since they didn't come from us.
            if (previous && !packet.desiredEnabled) {
                MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
                if (nv != null && nv.getDuration() < 250) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
            }

            PilotHelmetItem.setData(worn, data);
        });
    }

    private static boolean hasExternalNightVision(ServerPlayer player) {
        MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
        return nv != null && nv.getDuration() > 280;
    }
}
