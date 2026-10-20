package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class CreateStatsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateStats.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(itemLike -> BuiltInRegistries.ITEM.getKey(itemLike.asItem()));

        if (net.neoforged.fml.ModList.get().isLoaded(com.createstats.compat.SimulatedCompat.AERONAUTICS_MOD_ID)
                && net.neoforged.fml.ModList.get().isLoaded(com.createstats.compat.SimulatedCompat.SIMULATED_MOD_ID)) {
            itemHelper.addStoryBoard(ModBlocks.VCC_MK4_AERIAL.get(), "vcc_mk4_aerial/hangar", AerialScenes::firstFlight);
            itemHelper.addStoryBoard(ModBlocks.VCC_MK4_AERIAL.get(), "vcc_mk4_aerial/hangar", AerialScenes::altitude);
            itemHelper.addStoryBoard(ModBlocks.VCC_MK4_AERIAL.get(), "vcc_mk4_aerial/voyage", AerialScenes::telegraph);
        }

        itemHelper.addStoryBoard(ModBlocks.SURFACE_CABLE.get(), "fe/bench", FeScenes::cables);
        itemHelper.addStoryBoard(ModBlocks.CABLE_TRAY.get(), "fe/bench", FeScenes::cables);
        itemHelper.addStoryBoard(ModBlocks.BUS_DUCT.get(), "fe/bench", FeScenes::cables);
        itemHelper.addStoryBoard(ModBlocks.BUSBAR_TRUNK.get(), "fe/bench", FeScenes::cables);
        itemHelper.addStoryBoard(ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "fe/workshop", FeScenes::pdu);
        itemHelper.addStoryBoard(ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "fe/workshop", FeScenes::auto);
        itemHelper.addStoryBoard(ModBlocks.POWER_CONNECTOR.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(ModBlocks.ADVANCED_POWER_CONNECTOR.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(com.createstats.registry.ModItems.COPPER_WIRE.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(com.createstats.registry.ModItems.INSULATED_WIRE.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(com.createstats.registry.ModItems.SHIELDED_WIRE.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(com.createstats.registry.ModItems.HV_LINE.get(), "fe/spans", FeScenes::wires);
        itemHelper.addStoryBoard(ModBlocks.CAPACITOR_BANK.get(), "fe/bank", FeScenes::bank);
        itemHelper.addStoryBoard(ModBlocks.FE_BATTERY_CONTROLLER.get(), "fe/controller", FeScenes::controller);
        itemHelper.addStoryBoard(ModBlocks.FE_BATTERY_CONTROLLER.get(), "fe/yard", FeScenes::board);
        itemHelper.addStoryBoard(ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "fe/yard", FeScenes::board);

        itemHelper.addStoryBoard(ModBlocks.SEISMIC_SCANNER.get(), "seismic/pad", com.createstats.client.ponder.seismic.SeismicScenes::setup);
        itemHelper.addStoryBoard(ModBlocks.SEISMIC_SCANNER.get(), "seismic/rig", com.createstats.client.ponder.seismic.SeismicScenes::survey);
        itemHelper.addStoryBoard(ModBlocks.SEISMIC_SCANNER.get(), "seismic/strip", com.createstats.client.ponder.seismic.SeismicScenes::reading);

        itemHelper.addStoryBoard(ModBlocks.POWER_TRACK.get(), "powertrack/line", com.createstats.client.ponder.powertrack.PowerTrackScenes::laying);
        itemHelper.addStoryBoard(ModBlocks.TRACK_FEEDER.get(), "powertrack/line", com.createstats.client.ponder.powertrack.PowerTrackScenes::laying);
        itemHelper.addStoryBoard(ModBlocks.CURRENT_COLLECTOR.get(), "powertrack/line", com.createstats.client.ponder.powertrack.PowerTrackScenes::collector);
        itemHelper.addStoryBoard(ModBlocks.TRACK_FEEDER.get(), "powertrack/line", com.createstats.client.ponder.powertrack.PowerTrackScenes::feeder);
        itemHelper.addStoryBoard(ModBlocks.POWER_TRACK.get(), "powertrack/yard", com.createstats.client.ponder.powertrack.PowerTrackScenes::gaps);
        itemHelper.addStoryBoard(ModBlocks.SECTION_INSULATOR.get(), "powertrack/yard", com.createstats.client.ponder.powertrack.PowerTrackScenes::gaps);

        itemHelper.addStoryBoard(ModBlocks.SPARK_CROWN.get(), "spark_crown/station", SparkCrownScenes::crown);
        itemHelper.addStoryBoard(ModBlocks.STORM_COIL.get(), "storm_coil/yard", StormCoilScenes::coil);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }
}
