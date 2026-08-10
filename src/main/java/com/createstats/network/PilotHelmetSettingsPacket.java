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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PilotHelmetSettingsPacket implements CustomPacketPayload {
    public static final Type<PilotHelmetSettingsPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "pilot_helmet_settings"));
    public static final StreamCodec<FriendlyByteBuf, PilotHelmetSettingsPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), PilotHelmetSettingsPacket::new);

    private final int handIndex;
    private final String modeName;
    private final boolean autoIfSeated;
    private final int vignetteStrength;
    private final float hudScaleFactor;
    private final boolean nightVisionEnabled;
    private final String nightVisionModeName;

    public PilotHelmetSettingsPacket(InteractionHand hand, PilotHelmetData.Mode mode, boolean autoIfSeated,
                                     int vignetteStrength, float hudScaleFactor,
                                     boolean nightVisionEnabled, PilotHelmetData.NightVisionMode nightVisionMode) {
        this(hand == null ? 0 : hand.ordinal(),
            mode == null ? PilotHelmetData.Mode.FIGHTER.name() : mode.name(),
            autoIfSeated, vignetteStrength, hudScaleFactor,
            nightVisionEnabled,
            nightVisionMode == null ? PilotHelmetData.NightVisionMode.CLASSIC.name() : nightVisionMode.name());
    }

    private PilotHelmetSettingsPacket(int handIndex, String modeName, boolean autoIfSeated,
                                      int vignetteStrength, float hudScaleFactor,
                                      boolean nightVisionEnabled, String nightVisionModeName) {
        this.handIndex = handIndex;
        this.modeName = modeName == null ? PilotHelmetData.Mode.FIGHTER.name() : modeName;
        this.autoIfSeated = autoIfSeated;
        this.vignetteStrength = Math.max(0, Math.min(100, vignetteStrength));
        this.hudScaleFactor = Math.max(1.0f, Math.min(3.0f, hudScaleFactor));
        this.nightVisionEnabled = nightVisionEnabled;
        this.nightVisionModeName = nightVisionModeName == null
            ? PilotHelmetData.NightVisionMode.CLASSIC.name() : nightVisionModeName;
    }

    public PilotHelmetSettingsPacket(FriendlyByteBuf buf) {
        // Append-only wire format: NV fields are read last and tolerate older
        // clients/servers that don't include them. Field order MUST match encode().
        this(buf.readVarInt(), buf.readUtf(32), buf.readBoolean(),
            buf.isReadable() ? buf.readVarInt() : 5,
            buf.isReadable(Float.BYTES) ? buf.readFloat() : 1.0f,
            buf.isReadable() ? buf.readBoolean() : false,
            buf.isReadable() ? buf.readUtf(24) : PilotHelmetData.NightVisionMode.CLASSIC.name());
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.handIndex);
        buf.writeUtf(this.modeName, 32);
        buf.writeBoolean(this.autoIfSeated);
        buf.writeVarInt(this.vignetteStrength);
        buf.writeFloat(this.hudScaleFactor);
        buf.writeBoolean(this.nightVisionEnabled);
        buf.writeUtf(this.nightVisionModeName, 24);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PilotHelmetSettingsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            InteractionHand[] hands = InteractionHand.values();
            if (packet.handIndex < 0 || packet.handIndex >= hands.length) {
                return;
            }
            ItemStack stack = player.getItemInHand(hands[packet.handIndex]);
            if (!(stack.getItem() instanceof PilotHelmetItem)) {
                return;
            }
            PilotHelmetData data = PilotHelmetItem.getData(stack);
            data.setMode(parseMode(packet.modeName));
            data.setAutoIfSeated(packet.autoIfSeated);
            data.setVignetteStrength(packet.vignetteStrength);
            data.setHudScaleFactor(packet.hudScaleFactor);
            data.setNightVisionMode(parseNvMode(packet.nightVisionModeName));

            // [1]
            boolean requestedNV = packet.nightVisionEnabled;
            boolean previousNV = data.isNightVisionEnabled();
            if (requestedNV && !previousNV && hasExternalNightVision(player)) {
                data.setNightVisionEnabled(false);
                player.displayClientMessage(
                    Component.translatable("message.create_stats.pilot_helmet.nv_blocked")
                        .withStyle(ChatFormatting.YELLOW), true);
            } else {
                data.setNightVisionEnabled(requestedNV);
            }

            // [2]
            if (previousNV && !data.isNightVisionEnabled()) {
                MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
                if (nv != null && nv.getDuration() < 250) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
            }

            PilotHelmetItem.setData(stack, data);
        });
    }

    /**
     * @return true if the player currently has a {@link MobEffects#NIGHT_VISION}
     * effect whose remaining duration is well beyond the helmet's own 220-tick
     * refresh cycle (potion 3600t, beacon ≥ 260t). 280t margin avoids false
     * positives on the exact refresh-tick boundary.
     */
    private static boolean hasExternalNightVision(ServerPlayer player) {
        MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
        return nv != null && nv.getDuration() > 280;
    }

    private static PilotHelmetData.Mode parseMode(String modeName) {
        try {
            return PilotHelmetData.Mode.valueOf(modeName);
        } catch (IllegalArgumentException ignored) {
            return PilotHelmetData.Mode.FIGHTER;
        }
    }

    private static PilotHelmetData.NightVisionMode parseNvMode(String name) {
        try {
            return PilotHelmetData.NightVisionMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return PilotHelmetData.NightVisionMode.CLASSIC;
        }
    }
}
