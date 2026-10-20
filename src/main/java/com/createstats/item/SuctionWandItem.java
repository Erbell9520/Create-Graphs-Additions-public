package com.createstats.item;

import com.createstats.blockentity.VacuumCleanerBE;
import com.createstats.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SuctionWandItem extends Item {
    public SuctionWandItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static VacuumCleanerBE machine(Level level, ItemStack stack) {
        final GlobalPos at = stack.get(ModDataComponents.VACUUM_HOSE.get());
        if (at == null || !at.dimension().equals(level.dimension()) || !level.isLoaded(at.pos())) {
            return null;
        }
        return level.getBlockEntity(at.pos()) instanceof VacuumCleanerBE cleaner ? cleaner : null;
    }

    public static boolean belongsTo(ItemStack stack, Level level, BlockPos pos) {
        final GlobalPos at = stack.get(ModDataComponents.VACUUM_HOSE.get());
        return at != null && at.dimension().equals(level.dimension()) && at.pos().equals(pos);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                final VacuumCleanerBE cleaner = machine(level, stack);
                if (cleaner != null) {
                    cleaner.reclaimHose();
                } else {
                    stack.setCount(0);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) {
            return;
        }
        final VacuumCleanerBE cleaner = machine(level, stack);
        if (cleaner == null) {
            return;
        }
        if (!level.isClientSide()) {
            cleaner.suck(player);
            return;
        }

        com.createstats.client.SuctionWandClient.useTick(level, player, cleaner);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || level.getGameTime() % 10 != 0 || !(entity instanceof Player player)) {
            return;
        }
        final VacuumCleanerBE cleaner = machine(level, stack);
        if (cleaner == null || cleaner.holder() == null || !cleaner.holder().equals(player.getUUID())) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (!player.level().isClientSide()) {
            final VacuumCleanerBE cleaner = machine(player.level(), stack);
            if (cleaner != null) {
                cleaner.reclaimHose();
            }
        }
        stack.setCount(0);
        return false;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            final VacuumCleanerBE cleaner = machine(entity.level(), stack);
            if (cleaner != null) {
                cleaner.reclaimHose();
            }
            entity.discard();
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.suction_wand.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
