package com.createstats.aviatorhelmet;

import com.createstats.registry.ModDataComponents;
import com.createstats.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class AviatorHelmets {
    private AviatorHelmets() {
    }

    public static ItemStack worn(LivingEntity wearer) {
        final ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof AviatorHelmetItem ? head : ItemStack.EMPTY;
    }

    public static boolean gogglesDown(Player player) {
        final ItemStack helmet = worn(player);
        return !helmet.isEmpty() && AviatorModes.gogglesActive(AviatorHelmetItem.goggles(helmet).down());
    }

    public static boolean hudShowing(Player player) {
        final ItemStack helmet = worn(player);
        return !helmet.isEmpty() && AviatorModes.hudActive(AviatorHelmetItem.goggles(helmet).down());
    }

    static void flickGoggles(ServerPlayer player) {
        final ItemStack helmet = worn(player);
        if (helmet.isEmpty()) {
            return;
        }
        final AviatorGoggles goggles = AviatorHelmetItem.goggles(helmet);
        final long now = player.level().getGameTime();
        if (!AviatorModes.acceptsFlick(goggles.strokeTick(), now)) {
            return;
        }
        final AviatorGoggles next = goggles.flicked(now);
        helmet.set(ModDataComponents.AVIATOR_GOGGLES.get(), next);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                (next.down() ? ModSounds.AVIATOR_GOGGLES_DOWN : ModSounds.AVIATOR_GOGGLES_UP).get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    static void cycleTone(ServerPlayer player) {
        final ItemStack helmet = worn(player);
        if (helmet.isEmpty()) {
            return;
        }
        helmet.set(ModDataComponents.AVIATOR_GOGGLES.get(), AviatorHelmetItem.goggles(helmet).nextTone());
        player.playNotifySound(ModSounds.AVIATOR_HELMET_TONE.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
    }
}
