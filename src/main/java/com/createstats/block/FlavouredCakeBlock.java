package com.createstats.block;

import com.createstats.baking.CakeFlavor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// really is vanilla cake, inherited not copied
public class FlavouredCakeBlock extends CakeBlock {
    public static final MapCodec<FlavouredCakeBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("flavour")
                            .forGetter(block -> block.flavour.getSerializedName()),
                    propertiesCodec()
            ).apply(instance, (key, properties) ->
                    new FlavouredCakeBlock(byName(key), properties)));

    private final CakeFlavor flavour;

    public FlavouredCakeBlock(CakeFlavor flavour, Properties properties) {
        super(properties);
        this.flavour = flavour;
    }

    public CakeFlavor flavour() {
        return flavour;
    }

    @Override
    public MapCodec<CakeBlock> codec() {
        @SuppressWarnings("unchecked")
        final MapCodec<CakeBlock> widened = (MapCodec<CakeBlock>) (MapCodec<? extends CakeBlock>) CODEC;
        return widened;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.create_stats.cake_effect",
                flavour.effect().value().getDisplayName(),
                CakeFlavor.SLICE_EFFECT_TICKS / 20).withStyle(ChatFormatting.BLUE));
    }

    private static final int MAX_EFFECT_TICKS = 20 * 60;

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        final InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        if (level.isClientSide() || !result.consumesAction()) {
            return result;
        }

        player.getFoodData().eat(1, 0.6f);

        level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
                0.5f, 0.9f + level.random.nextFloat() * 0.2f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, asItem().getDefaultInstance()),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.15, 0.25, 0.05);
        }

        final MobEffectInstance running = player.getEffect(flavour.effect());
        final int carried = running == null || running.isInfiniteDuration() ? 0 : running.getDuration();
        final int amplifier = running == null ? 0 : running.getAmplifier();
        player.addEffect(new MobEffectInstance(flavour.effect(),
                Math.min(MAX_EFFECT_TICKS, carried + CakeFlavor.SLICE_EFFECT_TICKS), amplifier));
        return result;
    }

    private static CakeFlavor byName(String key) {
        for (final CakeFlavor value : CakeFlavor.values()) {
            if (value.getSerializedName().equals(key)) {
                return value;
            }
        }
        return CakeFlavor.CARROT;
    }
}
