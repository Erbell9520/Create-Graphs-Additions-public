package com.createstats.repeller;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;

// fast path allocates nothing and returns at once with no repeller
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class RepellerSpawnHooks {
    private static final int INSOMNIA_TICKS = 72_000;

    private RepellerSpawnHooks() {
    }

    @SubscribeEvent
    public static void onPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!RepellerIndex.anyActive()) {
            return;
        }
        final MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        final ServerLevel level = event.getLevel().getLevel();
        final SpawnIndex<EntityType<?>> index = RepellerIndex.of(level.dimension());
        if (index.isEmpty()) {
            return;
        }
        final BlockPos pos = event.getPos();

        final SpawnIndex.Entry<EntityType<?>> hit = index.find(event.getEntityType(),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (hit == null) {
            return;
        }
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        if (event.getDefaultResult()) {
            hit.repelled().incrementAndGet();
        }
    }

    @SubscribeEvent
    public static void onFinalize(FinalizeSpawnEvent event) {
        if (!RepellerIndex.anyActive()) {
            return;
        }
        final MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.PATROL) {
            return;
        }
        final SpawnIndex<EntityType<?>> index = RepellerIndex.of(event.getLevel().getLevel().dimension());
        if (index.isEmpty()) {
            return;
        }
        final SpawnIndex.Entry<EntityType<?>> hit = index.find(event.getEntity().getType(),
                event.getX(), event.getY(), event.getZ());
        if (hit == null) {
            return;
        }
        event.setSpawnCancelled(true);
        event.setCanceled(true);
        hit.repelled().incrementAndGet();
    }

    @SubscribeEvent
    public static void onPhantoms(PlayerSpawnPhantomsEvent event) {
        if (!RepellerIndex.anyActive() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final SpawnIndex<EntityType<?>> index = RepellerIndex.of(player.serverLevel().dimension());
        if (index.isEmpty()) {
            return;
        }
        final SpawnIndex.Entry<EntityType<?>> hit = index.find(EntityType.PHANTOM,
                player.getX(), player.getY(), player.getZ());
        if (hit == null) {
            return;
        }
        event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= INSOMNIA_TICKS) {
            hit.repelled().incrementAndGet();
        }
    }
}
