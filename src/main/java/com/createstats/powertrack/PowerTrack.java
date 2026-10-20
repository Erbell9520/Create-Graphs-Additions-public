package com.createstats.powertrack;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackMaterialFactory;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

// material before the track BE type or the first curve throws
@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PowerTrack {
    public static final ResourceLocation MATERIAL_ID = id("power");

    public static final TrackMaterial MATERIAL = TrackMaterialFactory.make(MATERIAL_ID)
            .lang("Power")
            .block(NonNullSupplier.lazy(() -> ModBlocks.POWER_TRACK))
            .particle(ResourceLocation.fromNamespaceAndPath("create",
                    "block/palettes/stone_types/polished/andesite_cut_polished"))
            .defaultModels()
            .customBlockFactory(PowerTrackBlock::new)
            .build();

    public static final EdgePointType<InsulatorPoint> INSULATOR =
            EdgePointType.register(id("section_insulator"), InsulatorPoint::new);

    public static final EdgePointType<FeederPoint> FEEDER =
            EdgePointType.register(id("track_feeder"), FeederPoint::new);

    public static final DeferredHolder<DisplaySource, FeederDisplaySource> FEEDER_SOURCE =
            CreateStats.DISPLAY_SOURCES.register("track_feeder", FeederDisplaySource::new);

    public static final ResourceLocation ARC_SOUND_ID = id("power_track_arc");
    private static SoundEvent arcSound;

    private PowerTrack() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, path);
    }

    public static void init() {
    }

    public static SoundEvent arcSound() {
        return arcSound;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {
            arcSound = SoundEvent.createVariableRangeEvent(ARC_SOUND_ID);
            helper.register(ARC_SOUND_ID, arcSound);
        });
    }

    @SubscribeEvent
    public static void addTrackBlock(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.TRACK.get(), ModBlocks.POWER_TRACK.get());
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            final FeederDisplaySource source = FEEDER_SOURCE.get();
            DisplaySource.BY_BLOCK.addProvider(candidate -> candidate instanceof TrackFeederBlock ? source : null);
        });
    }
}
