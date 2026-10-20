package com.createstats.chunkloader;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.UsernameCache;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CrownOwners {
    private CrownOwners() {
    }

    @Nullable
    public static UUID ownerOf(@Nullable LivingEntity placer) {
        if (placer instanceof DeployerFakePlayer deployer) {
            final UUID id = deployer.getUUID();
            return id == null || DeployerFakePlayer.fallbackID.equals(id) ? null : id;
        }
        if (placer instanceof FakePlayer) {
            return null;
        }
        return placer instanceof Player player ? player.getUUID() : null;
    }

    public static String nameOf(@Nullable LivingEntity placer, @Nullable UUID owner) {
        if (placer instanceof Player player && !(placer instanceof FakePlayer)) {
            return player.getGameProfile().getName();
        }
        final String cached = owner == null ? null : UsernameCache.getLastKnownUsername(owner);
        return cached == null ? "" : cached;
    }
}
