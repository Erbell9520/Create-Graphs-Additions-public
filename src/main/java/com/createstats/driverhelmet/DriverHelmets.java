package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import com.createstats.registry.ModDataComponents;
import com.createstats.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class DriverHelmets {
    // over 200 or vanilla flickers night vision as "running out"
    static final int NIGHT_VISION_TICKS = 400;

    static final int NIGHT_VISION_REFRESH_BELOW = 300;

    private DriverHelmets() {
    }

    public static ItemStack worn(LivingEntity wearer) {
        final ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof DriverHelmetItem ? head : ItemStack.EMPTY;
    }

    public static boolean gogglesUp(Player player) {
        final ItemStack helmet = worn(player);
        return !helmet.isEmpty() && VisorModes.gogglesActive(DriverHelmetItem.visor(helmet).open());
    }

    static void flickVisor(ServerPlayer player) {
        final ItemStack helmet = worn(player);
        if (helmet.isEmpty()) {
            return;
        }
        final HelmetVisor visor = DriverHelmetItem.visor(helmet);
        final long now = player.level().getGameTime();
        if (!VisorModes.acceptsFlick(visor.strokeTick(), now)) {
            return;
        }
        final HelmetVisor next = visor.flicked(now);
        helmet.set(ModDataComponents.HELMET_VISOR.get(), next);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                (next.open() ? ModSounds.DRIVER_HELMET_VISOR_OPEN : ModSounds.DRIVER_HELMET_VISOR_CLOSE).get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    static void toggleNightVision(ServerPlayer player) {
        final ItemStack helmet = worn(player);
        if (helmet.isEmpty()) {
            return;
        }
        final HelmetVisor visor = DriverHelmetItem.visor(helmet);
        if (!VisorModes.nightVisionKeyAccepted(visor.open())) {
            return;
        }
        final HelmetVisor next = visor.withNightVision(!visor.nightVision());
        helmet.set(ModDataComponents.HELMET_VISOR.get(), next);
        player.playNotifySound(
                (next.nightVision() ? ModSounds.DRIVER_HELMET_NIGHT_VISION_ON : ModSounds.DRIVER_HELMET_NIGHT_VISION_OFF).get(),
                SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    static void cycleTheme(ServerPlayer player) {
        final ItemStack helmet = worn(player);
        if (helmet.isEmpty()) {
            return;
        }
        helmet.set(ModDataComponents.HELMET_VISOR.get(), DriverHelmetItem.visor(helmet).nextTheme());
        player.playNotifySound(ModSounds.DRIVER_HELMET_THEME.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        final ItemStack helmet = worn(player);
        final HelmetVisor visor = helmet.isEmpty() ? HelmetVisor.DEFAULT : DriverHelmetItem.visor(helmet);
        final boolean wanted = VisorModes.nightVisionActive(!helmet.isEmpty(), visor.open(), visor.nightVision());
        final MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        final boolean ours = current != null && isOurs(current);
        if (wanted) {
            if (current == null || (ours && current.getDuration() < NIGHT_VISION_REFRESH_BELOW)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0,
                        true, false, false));
            }
        } else if (ours) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static boolean isOurs(MobEffectInstance effect) {
        return effect.isAmbient() && !effect.isVisible() && !effect.showIcon() && effect.getAmplifier() == 0
                && effect.getDuration() <= NIGHT_VISION_TICKS;
    }
}
