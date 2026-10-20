package com.createstats.support;

import com.createstats.support.SupportPolicy.Decision;
import com.createstats.support.SupportPolicy.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// tier 2 logs through its own toggle, on by default. the first version logged through tier 1's, which is off
public record SupportActionPacket(String verb, String argument, int amount) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SupportActionPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "support_action"));

    public static final StreamCodec<FriendlyByteBuf, SupportActionPacket> STREAM_CODEC =
        StreamCodec.of(SupportActionPacket::encode, SupportActionPacket::decode);

    public static final String VERB_GIVE = "give";
    public static final String VERB_GAMEMODE = "gamemode";
    public static final String VERB_FLY = "fly";
    public static final String VERB_HEAL = "heal";
    public static final String VERB_RIG = "rig";
    public static final String VERB_END = "end";

    public static SupportActionPacket of(String verb) {
        return new SupportActionPacket(verb, "", 0);
    }

    public static SupportActionPacket of(String verb, String argument) {
        return new SupportActionPacket(verb, argument, 0);
    }

    public static SupportActionPacket give(String itemId, int count) {
        return new SupportActionPacket(VERB_GIVE, itemId, count);
    }

    private static void encode(FriendlyByteBuf buf, SupportActionPacket pkt) {
        buf.writeUtf(pkt.verb, 32);
        buf.writeUtf(pkt.argument, 256);
        buf.writeVarInt(pkt.amount);
    }

    private static SupportActionPacket decode(FriendlyByteBuf buf) {
        return new SupportActionPacket(buf.readUtf(32), buf.readUtf(256), buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SupportActionPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (VERB_END.equals(pkt.verb)) {
                if (!SupportSessions.endSession(player)) {
                    player.sendSystemMessage(Component.literal("No support session is open.")
                        .withStyle(ChatFormatting.GRAY));
                }
                return;
            }

            Decision decision = SupportSessions.access(player);
            if (!decision.atLeast(Level.TIER2)) {
                SupportAccess.auditAlways(player, "tier2-DENIED", decision.level(), decision.basis(), pkt.verb);
                player.sendSystemMessage(decision.level() == Level.NONE
                    ? Component.literal("Not available.").withStyle(ChatFormatting.RED)
                    : Component.literal("Support world actions are locked: " + decision.tier2Note())
                        .withStyle(ChatFormatting.RED));
                return;
            }

            switch (pkt.verb) {
                case VERB_GIVE -> give(player, decision, pkt.argument, pkt.amount);
                case VERB_GAMEMODE -> gamemode(player, decision, pkt.argument);
                case VERB_FLY -> fly(player, decision);
                case VERB_HEAL -> heal(player, decision);
                case VERB_RIG -> SupportTestRig.place(player, decision, pkt.argument);
                default -> {
                }
            }
        });
    }

    private static void give(ServerPlayer player, Decision decision, String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            player.sendSystemMessage(Component.literal("Not a valid item id: " + itemId)
                .withStyle(ChatFormatting.RED));
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            player.sendSystemMessage(Component.literal("No such item: " + id).withStyle(ChatFormatting.RED));
            return;
        }

        int clamped = Math.max(1, Math.min(count, item.getDefaultMaxStackSize()));
        ItemStack stack = new ItemStack(item, clamped);
        SupportSessions.tier2(player, decision, VERB_GIVE, id, "x" + clamped);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void gamemode(ServerPlayer player, Decision decision, String name) {
        GameType mode = GameType.byName(name, GameType.SURVIVAL);
        SupportSessions.tier2(player, decision, VERB_GAMEMODE, mode.getName());
        SupportSessions.noteGamemodeBefore(player);
        player.setGameMode(mode);
    }

    private static void fly(ServerPlayer player, Decision decision) {
        boolean enabled = !player.getAbilities().mayfly;
        SupportSessions.tier2(player, decision, VERB_FLY, enabled);
        SupportSessions.noteFlightBefore(player);
        player.getAbilities().mayfly = enabled;
        if (!enabled) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
        player.sendSystemMessage(Component.literal("Flight " + (enabled ? "enabled" : "disabled"))
            .withStyle(ChatFormatting.GRAY));
    }

    private static void heal(ServerPlayer player, Decision decision) {
        SupportSessions.tier2(player, decision, VERB_HEAL);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
        player.clearFire();
    }
}
