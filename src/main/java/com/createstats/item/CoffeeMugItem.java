package com.createstats.item;

import com.createstats.block.MugBlock;
import com.createstats.block.MugContent;
import com.createstats.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoffeeMugItem extends BlockItem {
    private static final int DRINK_TICKS = 32;

    private static final int EFFECT_TICKS = 20 * 20;

    private static final int LATTE_EFFECT_TICKS = 45 * 20;

    private static final int MILK_EFFECT_TICKS = 10 * 20;

    private static final int CHILLED_EFFECT_TICKS = 20 * 20;

    private static final int SPARKLING_EFFECT_TICKS = 30 * 20;

    public CoffeeMugItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static MugContent contentOf(ItemStack stack) {
        final Integer stored = stack.get(ModDataComponents.CONTENT.get());
        return stored == null ? MugContent.EMPTY : MugContent.byOrdinal(stored);
    }

    public static boolean isFilled(ItemStack stack) {
        return contentOf(stack).isDrinkable();
    }

    public static ItemStack withContent(ItemStack stack, MugContent content) {
        final ItemStack out = stack.copyWithCount(1);
        if (content.isDrinkable()) {
            out.set(ModDataComponents.CONTENT.get(), content.ordinal());
        } else {
            out.remove(ModDataComponents.CONTENT.get());
        }
        return out;
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        final BlockState state = super.getPlacementState(context);
        return state == null ? null
                : state.setValue(MugBlock.CONTENT, contentOf(context.getItemInHand()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && !context.getPlayer().isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isFilled(stack) ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return isFilled(stack) ? DRINK_TICKS : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!isFilled(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        final MugContent content = contentOf(stack);
        if (!level.isClientSide()) {
            switch (content) {
                case MILK -> entity.addEffect(
                        new MobEffectInstance(MobEffects.REGENERATION, MILK_EFFECT_TICKS, 0));
                case LATTE -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LATTE_EFFECT_TICKS, 0));
                    entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, LATTE_EFFECT_TICKS, 0));
                }
                case SPRING_WATER -> hydrate(entity);
                case CHILLED_WATER -> {
                    hydrate(entity);
                    entity.addEffect(
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, CHILLED_EFFECT_TICKS, 0));
                }
                case SPARKLING_WATER -> {
                    hydrate(entity);
                    entity.addEffect(
                            new MobEffectInstance(MobEffects.WATER_BREATHING, SPARKLING_EFFECT_TICKS, 0));
                    entity.removeEffect(MobEffects.DIG_SLOWDOWN);
                }
                default -> {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_TICKS, 0));
                    entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, EFFECT_TICKS, 0));
                }
            }
        }
        if (content.isWater()) {
            drinkWaterSounds(level, entity, content);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.2f);

        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return stack;
        }

        final ItemStack empty = stack.copyWithCount(1);
        empty.remove(ModDataComponents.CONTENT.get());
        stack.shrink(1);
        if (stack.isEmpty()) {
            return empty;
        }
        if (entity instanceof Player player && !player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
        return stack;
    }

    // puts you out and clears nausea. not food
    private static void hydrate(LivingEntity entity) {
        entity.clearFire();
        entity.removeEffect(MobEffects.CONFUSION);
    }

    private static void drinkWaterSounds(Level level, LivingEntity entity, MugContent content) {
        if (content != MugContent.SPARKLING_WATER || level.isClientSide()) {
            return;
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.create_stats.coffee_mug." + contentOf(stack).getSerializedName())
                .withStyle(ChatFormatting.GRAY));
    }

    private static final class ItemUtils {
        static InteractionResultHolder<ItemStack> startUsingInstantly(Level level, Player player,
                                                                     InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
    }
}
