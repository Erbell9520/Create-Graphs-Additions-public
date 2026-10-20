package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import com.createstats.registry.ModDataComponents;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// Equipable not ArmorItem, armor can't be a block item and the layer would paint it on the vanilla box
public class DriverHelmetItem extends BlockItem implements Equipable {
    public DriverHelmetItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_armor"),
                                2.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.HEAD)
                .build();
    }

    public static HelmetVisor visor(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.HELMET_VISOR.get(), HelmetVisor.DEFAULT);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.create_stats.driver_helmet.tooltip.visor",
                Component.keybind(HelmetKeyNames.VISOR)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.driver_helmet.tooltip.up")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.driver_helmet.tooltip.night_vision",
                Component.keybind(HelmetKeyNames.NIGHT_VISION)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.driver_helmet.tooltip.theme",
                Component.keybind(HelmetKeyNames.THEME)).withStyle(ChatFormatting.GRAY));

        final HelmetVisor visor = visor(stack);
        tooltip.add(themeLine(visor).withStyle(ChatFormatting.DARK_AQUA));
        if (visor.odometer() > 0.0) {
            tooltip.add(odometerLine(visor).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    public static MutableComponent themeLine(HelmetVisor visor) {
        return Component.translatable("hud.create_stats.driver_helmet.theme",
                Component.translatable("hud.create_stats.driver_helmet.theme." + HudTheme.of(visor.theme()).id));
    }

    public static MutableComponent odometerLine(HelmetVisor visor) {
        return Component.translatable("item.create_stats.driver_helmet.tooltip.odometer", odometerText(visor.odometer()));
    }

    public static String odometerText(double metres) {
        final double km = metres / 1000.0;
        return String.format(Locale.ROOT, km < 10.0 ? "%.2f km" : km < 1000.0 ? "%.1f km" : "%.0f km", km);
    }
}
