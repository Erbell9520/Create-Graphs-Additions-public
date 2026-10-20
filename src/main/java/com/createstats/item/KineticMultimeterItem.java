package com.createstats.item;

import java.util.List;

import com.createstats.data.KineticMultimeterData;
import com.createstats.data.KineticMultimeterData.Reading;
import com.createstats.util.InstrumentSourceHelper;
import com.createstats.util.InstrumentSourceHelper.SourceReadout;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class KineticMultimeterItem extends Item {
    public KineticMultimeterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        long now = level.getGameTime();
        Direction face = context.getClickedFace();

        // Route through the mod's unified source reader so the meter inherits every kinetic block we

        SourceReadout readout = be == null ? null : InstrumentSourceHelper.readSource(be, face);
        boolean kinetic = readout != null
            && (readout.sourceKind() == InstrumentSourceHelper.SourceKind.KINETIC_STRESS
                || readout.sourceKind() == InstrumentSourceHelper.SourceKind.KINETIC_SPEED
                || readout.metrics().containsKey("rpm"));

        if (!kinetic) {
            Reading r = new Reading(now, 0f, 0f, 0f, KineticMultimeterData.ROLE_NONE,
                blockId, KineticMultimeterData.STATE_NONET);
            KineticMultimeterData.write(stack, r);
            if (player != null) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.kinetic_multimeter.nonet")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }

        float rpm = be instanceof KineticBlockEntity kbe
            ? kbe.getSpeed()
            : metric(readout, 0f, "rpm", "abs_rpm", "network_rpm", "stored_rpm");
        float used = metric(readout, 0f, "stress_su");
        float cap = metric(readout, 0f, "capacity_su");

        byte role;
        if (be instanceof KineticBlockEntity kbe) {
            role = kbe.isSource() ? KineticMultimeterData.ROLE_SOURCE
                : (kbe.calculateStressApplied() > 0f ? KineticMultimeterData.ROLE_CONSUMER
                : KineticMultimeterData.ROLE_NONE);
        } else {
            role = KineticMultimeterData.ROLE_SOURCE;
        }

        byte healthState;
        if (cap > 0f) {
            float load = (used / cap) * 100f;
            healthState = used > cap ? KineticMultimeterData.STATE_OVER
                : (load >= 70f ? KineticMultimeterData.STATE_WARN : KineticMultimeterData.STATE_OK);
        } else {
            boolean live = Math.abs(rpm) > 0.01f
                || (be instanceof KineticBlockEntity kbe && kbe.hasNetwork());
            healthState = live ? KineticMultimeterData.STATE_OK : KineticMultimeterData.STATE_NONET;
        }

        Reading r = new Reading(now, rpm, used, cap, role, blockId, healthState);
        KineticMultimeterData.write(stack, r);

        if (player != null) {
            player.displayClientMessage(buildReadout(r), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static float metric(SourceReadout readout, float def, String... keys) {
        for (String key : keys) {
            InstrumentSourceHelper.MetricSample sample = readout.metrics().get(key);
            if (sample != null) {
                return sample.value();
            }
        }
        return def;
    }

    private static Component buildReadout(Reading r) {
        ChatFormatting loadColor = switch (r.state()) {
            case KineticMultimeterData.STATE_OVER -> ChatFormatting.RED;
            case KineticMultimeterData.STATE_WARN -> ChatFormatting.GOLD;
            default -> ChatFormatting.GREEN;
        };
        Component rpmPart = Component.translatable(
            "message.create_stats.kinetic_multimeter.rpm",
            String.format("%.0f", r.rpm())).withStyle(ChatFormatting.AQUA);

        if (!r.hasNetwork()) {
            return Component.translatable("message.create_stats.kinetic_multimeter.reading_nonet", rpmPart);
        }
        Component loadPart = Component.literal(String.format("%.0f%%", r.loadPercent())).withStyle(loadColor);
        Component suPart = Component.translatable("message.create_stats.kinetic_multimeter.su_part",
            String.format("%.0f", r.stressUsed()), String.format("%.0f", r.stressCap())).withStyle(ChatFormatting.GRAY);
        return Component.translatable(
            "message.create_stats.kinetic_multimeter.reading", rpmPart, loadPart, suPart);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        Reading r = KineticMultimeterData.read(stack);
        return r.state() == KineticMultimeterData.STATE_OVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        if (!KineticMultimeterData.has(stack)) {
            tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.empty")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Reading r = KineticMultimeterData.read(stack);
        Component target = blockName(r.blockId());
        tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.target", target)
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.rpm",
            String.format("%.0f", r.rpm())).withStyle(ChatFormatting.AQUA));
        if (r.hasNetwork()) {
            ChatFormatting loadColor = switch (r.state()) {
                case KineticMultimeterData.STATE_OVER -> ChatFormatting.RED;
                case KineticMultimeterData.STATE_WARN -> ChatFormatting.GOLD;
                default -> ChatFormatting.GREEN;
            };
            tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.load",
                String.format("%.0f", r.loadPercent())).withStyle(loadColor));
            tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.stress",
                String.format("%.0f", r.stressUsed()), String.format("%.0f", r.stressCap()))
                .withStyle(ChatFormatting.DARK_GRAY));
            String roleKey = switch (r.role()) {
                case KineticMultimeterData.ROLE_SOURCE -> "item.create_stats.kinetic_multimeter.tooltip.role.source";
                case KineticMultimeterData.ROLE_CONSUMER -> "item.create_stats.kinetic_multimeter.tooltip.role.consumer";
                default -> "item.create_stats.kinetic_multimeter.tooltip.role.passive";
            };
            tooltip.add(Component.translatable(roleKey).withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("item.create_stats.kinetic_multimeter.tooltip.nonet")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component blockName(@Nullable String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Component.translatable("item.create_stats.kinetic_multimeter.tooltip.unknown");
        }
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) {
            return Component.literal(blockId);
        }
        return Component.translatable(
            "block." + rl.getNamespace() + "." + rl.getPath());
    }
}
