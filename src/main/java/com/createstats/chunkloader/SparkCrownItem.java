package com.createstats.chunkloader;

import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

public class SparkCrownItem extends BlockItem {
    public SparkCrownItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel server) {
            final BlockPos pos = context.getClickedPos();
            final CrownRegistry registry = CrownRegistry.get(server);
            final CrownRegistry.Station other = registry.holderOf(CrownRegistry.dimensionOf(server),
                    CrownRegion.regionOf(pos.getX() >> 4), CrownRegion.regionOf(pos.getZ() >> 4),
                    p -> CrownRegistry.stationStillAt(server, p));
            final Player player = context.getPlayer();
            if (other != null && other.pos() != pos.asLong()) {
                if (player != null) {
                    final BlockPos at = BlockPos.of(other.pos());
                    player.displayClientMessage(Component.translatable("block.create_stats.spark_crown.region_taken",
                            at.getX(), at.getY(), at.getZ()), true);
                }
                return InteractionResult.FAIL;
            }
            final UUID owner = CrownOwners.ownerOf(player);
            final CrownLaw law = CrownConfig.law();
            if (owner != null && registry.ownerTotal(owner) + 1 > law.maxPerPlayer()) {
                player.displayClientMessage(Component.translatable("block.create_stats.spark_crown.player_full",
                        registry.ownerTotal(owner), law.maxPerPlayer()), true);
                return InteractionResult.FAIL;
            }
        }
        return super.place(context);
    }
}
