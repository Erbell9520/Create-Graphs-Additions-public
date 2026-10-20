package com.createstats.vcu;

import com.createstats.CreateStats;
import com.createstats.vcu.network.VcuInputPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VcuRegistry {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vehicle_control_unit");

    public static VehicleControlUnitBlock BLOCK;
    public static VcuBlockItem ITEM;
    public static BlockEntityType<VehicleControlUnitBE> BLOCK_ENTITY;

    private VcuRegistry() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            BLOCK = new VehicleControlUnitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion());
            helper.register(ID, BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            ITEM = new VcuBlockItem(BLOCK, new Item.Properties());
            helper.register(ID, ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            BLOCK_ENTITY = BlockEntityType.Builder.of(VehicleControlUnitBE::new, BLOCK).build(null);
            helper.register(ID, BLOCK_ENTITY);
        });
    }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS || ITEM == null) return;
        boolean experimental;
        try {
            experimental = com.createstats.ModConfiguration.COMMON.showExperimentalBlocks.get();
        } catch (RuntimeException configNotLoadedYet) {
            experimental = false;
        }
        if (experimental) event.accept(ITEM);
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("vcu-1")
                .playToServer(VcuInputPacket.TYPE, VcuInputPacket.STREAM_CODEC, VcuInputPacket::handle);
    }
}
