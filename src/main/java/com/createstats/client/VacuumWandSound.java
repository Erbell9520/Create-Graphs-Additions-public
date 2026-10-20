package com.createstats.client;

import com.createstats.blockentity.VacuumCleanerBE;
import com.createstats.item.SuctionWandItem;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VacuumWandSound extends AbstractTickableSoundInstance {
    private static final Map<UUID, VacuumWandSound> PLAYING = new HashMap<>();

    private static final float TARGET_VOLUME = 0.22f;

    private final Player player;
    private final VacuumCleanerBE machine;

    private VacuumWandSound(Player player, VacuumCleanerBE machine) {
        super(ModSounds.VACUUM_HUM.get(), SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.machine = machine;
        this.looping = true;
        this.delay = 0;

        this.volume = 0.02f;
        this.pitch = 1.75f;
        follow();
    }

    public static void startIfNeeded(Player player, VacuumCleanerBE machine) {
        final VacuumWandSound running = PLAYING.get(player.getUUID());
        if (running != null && !running.isStopped()) {
            return;
        }
        final VacuumWandSound sound = new VacuumWandSound(player, machine);
        PLAYING.put(player.getUUID(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private boolean sucking() {
        return !player.isRemoved() && !machine.isRemoved() && player.isUsingItem()
                && player.getUseItem().getItem() instanceof SuctionWandItem
                && machine.state() == VacuumCleanerBE.State.SUCK;
    }

    private void follow() {
        final Vec3 head = VacuumCleanerBE.wandHead(player);
        this.x = head.x;
        this.y = head.y;
        this.z = head.z;
    }

    @Override
    public void tick() {
        if (player.isRemoved()) {
            PLAYING.remove(player.getUUID(), this);
            this.stop();
            return;
        }
        follow();
        if (!sucking()) {
            this.volume = Math.max(0.0f, this.volume - 0.05f);
            if (this.volume <= 0.001f) {
                PLAYING.remove(player.getUUID(), this);
                this.stop();
            }
            return;
        }
        this.volume += (TARGET_VOLUME - this.volume) * 0.25f;
    }
}
