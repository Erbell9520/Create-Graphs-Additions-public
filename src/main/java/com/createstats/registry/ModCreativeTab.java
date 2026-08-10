package com.createstats.registry;

import com.createstats.CreateStats;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab
{
    private static final DeferredRegister<CreativeModeTab> TABS;
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATE_STATS_TAB;
    
    public static void register(IEventBus modEventBus) {
        ModCreativeTab.TABS.register(modEventBus);
    }
    
    private static CreativeModeTab buildTab() {
        return CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_stats"))
            .icon(() -> ModBlocks.STATS_DISPLAY_PANEL.asStack())
            .displayItems((params, output) -> {
                CreateRegistrate registrate = CreateStats.registrate();
                java.util.Set<net.minecraft.world.item.Item> added = new java.util.LinkedHashSet<>();
                // [1]
                boolean experimental;
                try {
                    experimental = com.createstats.ModConfiguration.COMMON.showExperimentalBlocks.get();
                } catch (RuntimeException ignored) {
                    experimental = false;
                }
                final java.util.Set<net.minecraft.world.item.Item> hidden = experimental
                    ? java.util.Set.of()
                    : java.util.Set.of(ModBlocks.VCC_MK_II.get().asItem(), ModBlocks.STATS_DISPLAY_MK2.get().asItem(), ModItems.HINGE.get());
                // Add all blocks registered with our tab
                registrate.getAll(Registries.BLOCK).forEach(entry -> {
                    if (CreateRegistrate.isInCreativeTab(entry, CREATE_STATS_TAB)) {
                        net.minecraft.world.item.Item item = ((Block) entry.get()).asItem();
                        if (item != Items.AIR && !hidden.contains(item)) {
                            added.add(item);
                        }
                    }
                });
                // Add standalone items (skip block items already added)
                registrate.getAll(Registries.ITEM).forEach(entry -> {
                    if (CreateRegistrate.isInCreativeTab(entry, CREATE_STATS_TAB)) {
                        net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) entry.get();
                        if (!hidden.contains(item)) {
                            added.add(item);
                        }
                    }
                });
                added.forEach(item -> output.accept(new ItemStack(item)));
            })
            .build();
    }

    static {
        TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "create_stats");
        CREATE_STATS_TAB = ModCreativeTab.TABS.register("create_stats_tab", ModCreativeTab::buildTab);
    }
}
