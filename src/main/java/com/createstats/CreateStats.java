package com.createstats;

import java.util.function.Supplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.createmod.catnip.data.Pair;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.trains.schedule.Schedule;
import org.slf4j.LoggerFactory;
import java.util.List;
import com.createstats.network.ModPackets;
import com.createstats.train.manifest.CargoManifestMetCondition;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import com.createstats.registry.ModCreativeTab;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModMenuTypes;
import com.createstats.registry.ModSounds;
import com.createstats.registry.ModItems;
import com.createstats.registry.ModFluids;
import com.createstats.registry.ModBlocks;
import com.createstats.display.AeroStatsLinkDisplaySource;
import com.createstats.display.AutoStatsLinkDisplaySource;
import com.createstats.display.BattleScreenDisplaySource;
import com.createstats.display.DamageObserverDisplaySource;
import com.createstats.display.DispatchBoardDisplaySource;
import com.createstats.display.FlowMeterDisplaySource;
import com.createstats.display.ProductionDetectorDisplaySource;
import com.createstats.display.ProductionDetectorDisplayTarget;
import com.createstats.display.ProductionDetectorStatsLinkDisplaySource;
import com.createstats.display.AutoTransmissionClutchDisplaySource;
import com.createstats.display.ControlClutchDisplaySource;
import com.createstats.display.FreightManifestClipboardDisplaySource;
import com.createstats.display.LoadBalancerDisplaySource;
import com.createstats.display.MiniDisplayDisplaySource;
import com.createstats.display.SafetyClutchDisplaySource;
import com.createstats.display.InductionHeaterDisplaySource;
import com.createstats.display.KineticFlywheelDisplaySource;
import com.createstats.display.SmartGearshiftDisplaySource;
import com.createstats.compat.SimulatedCompat;
import com.createstats.compat.SimulatedCompatHelper;
import com.createstats.compat.CreateAdditionCompat;
import com.createstats.compat.CreateAdditionCompatHelper;
import com.createstats.compat.DieselCompat;
import com.createstats.compat.DieselCompatHelper;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.createstats.display.StatsDisplayTarget;
import com.createstats.display.TunnelStatsLinkDisplaySource;
import com.createstats.flight.display.FccDisplayTarget;
import com.createstats.pathfinder.display.PathfinderComputerDisplaySource;
import com.createstats.pathfinder.display.PathScannerDisplaySource;
import com.createstats.pathfinder.display.PathfinderDisplayTarget;
import com.createstats.train.manifest.ManifestUnloadTracker;
import com.createstats.blockentity.OverflowGateBE;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModContainer;

@Mod("create_stats")
public class CreateStats
{
    public static final String MOD_ID = "create_stats";
    public static final String MOD_NAME = "Create: Stats & Power";
    public static final Logger LOGGER;
    private static final CreateRegistrate REGISTRATE;
    public static final DeferredRegister<DisplayTarget> DISPLAY_TARGETS;
    public static final DeferredHolder<DisplayTarget, StatsDisplayTarget> STATS_PANEL_TARGET;
    public static final DeferredHolder<DisplayTarget, ProductionDetectorDisplayTarget> PRODUCTION_DETECTOR_TARGET;
    public static final DeferredHolder<DisplayTarget, FccDisplayTarget> FCC_TARGET;
    public static final DeferredHolder<DisplayTarget, com.createstats.vccmk3.VccMk3DisplayTarget> VCC_MK3_TARGET;
    public static final DeferredHolder<DisplayTarget, com.createstats.vccmk2.VccMk2DisplayTarget> VCC_MK2_TARGET;
    public static final DeferredHolder<DisplayTarget, PathfinderDisplayTarget> PATHFINDER_TARGET;
    public static final DeferredHolder<DisplayTarget, com.createstats.display.EInkPanelDisplayTarget> EINK_PANEL_TARGET;
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES;
    public static final DeferredHolder<DisplaySource, AutoStatsLinkDisplaySource> AUTO_STATS_LINK_SOURCE;
    public static final DeferredHolder<DisplaySource, AeroStatsLinkDisplaySource> AERO_STATS_LINK_SOURCE;
    public static final DeferredHolder<DisplaySource, TunnelStatsLinkDisplaySource> TUNNEL_STATS_LINK_SOURCE;
    public static final DeferredHolder<DisplaySource, ProductionDetectorDisplaySource> PRODUCTION_DETECTOR_SOURCE;
    public static final DeferredHolder<DisplaySource, ProductionDetectorStatsLinkDisplaySource> PRODUCTION_DETECTOR_STATS_LINK_SOURCE;
    public static final DeferredHolder<DisplaySource, BattleScreenDisplaySource> BATTLE_SCREEN_SOURCE;
    public static final DeferredHolder<DisplaySource, DamageObserverDisplaySource> DAMAGE_OBSERVER_SOURCE;
    public static final DeferredHolder<DisplaySource, SafetyClutchDisplaySource> SAFETY_CLUTCH_SOURCE;
    public static final DeferredHolder<DisplaySource, KineticFlywheelDisplaySource> KINETIC_FLYWHEEL_SOURCE;
    public static final DeferredHolder<DisplaySource, InductionHeaterDisplaySource> INDUCTION_HEATER_SOURCE;
    public static final DeferredHolder<DisplaySource, AutoTransmissionClutchDisplaySource> AUTO_TRANSMISSION_CLUTCH_SOURCE;
    public static final DeferredHolder<DisplaySource, ControlClutchDisplaySource> CONTROL_CLUTCH_SOURCE;
    public static final DeferredHolder<DisplaySource, LoadBalancerDisplaySource> LOAD_BALANCER_SOURCE;
    public static final DeferredHolder<DisplaySource, FlowMeterDisplaySource> FLOW_METER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.RefinedStorageDisplaySource> REFINED_STORAGE_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.PowerMeterDisplaySource> POWER_METER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.PowerLimiterDisplaySource> POWER_LIMITER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.PowerDisplaySource> POWER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.AnnunciatorDisplaySource> ANNUNCIATOR_SOURCE;
    public static final DeferredHolder<DisplaySource, SmartGearshiftDisplaySource> SMART_GEARSHIFT_SOURCE;
    public static final DeferredHolder<DisplaySource, MiniDisplayDisplaySource> MINI_DISPLAY_SOURCE;
    public static final DeferredHolder<DisplaySource, FreightManifestClipboardDisplaySource> FREIGHT_MANIFEST_CLIPBOARD_SOURCE;
    public static final DeferredHolder<DisplaySource, DispatchBoardDisplaySource> DISPATCH_BOARD_SOURCE;
    public static final DeferredHolder<DisplaySource, PathScannerDisplaySource> PATH_SCANNER_SOURCE;
    public static final DeferredHolder<DisplaySource, PathfinderComputerDisplaySource> PATHFINDER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.netscan.NetscanDisplaySource> TOPOLOGY_SCANNER_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.AerialDisplaySource> AERIAL_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.GroundDisplaySource> GROUND_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.PathwayStationDisplaySource> PATHWAY_STATION_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.CineCameraDisplaySource> CINE_CAMERA_SOURCE;
    public static final DeferredHolder<DisplaySource, com.createstats.display.SeismicDisplaySource> SEISMIC_SOURCE;
    private final Map<ResourceLocation, Set<BlockPos>> pendingAeroStatsLinkMigrations = new HashMap<>();

    public CreateStats(IEventBus modEventBus, ModContainer modContainer) {
        CreateStats.LOGGER.info("{} initializing!", CreateStats.MOD_NAME);
        CreateStats.REGISTRATE.setCreativeTab(ModCreativeTab.CREATE_STATS_TAB);
        CreateStats.REGISTRATE.registerEventListeners(modEventBus);
        ModConfiguration.register(modEventBus, modContainer);
        ModBlocks.register();
        ModItems.register();
        com.createstats.book.BookAliases.register(modEventBus);
        com.createstats.stormcoil.StormCoilAliases.register(modEventBus);
        ModFluids.register();
        ModBlockEntities.register();
        ModMenuTypes.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        com.createstats.registry.ModRecipeConditions.register(modEventBus);
        com.createstats.registry.ModRecipes.register(modEventBus);
        ModSounds.register(modEventBus);
        com.createstats.registry.ModParticles.register(modEventBus);
        com.createstats.registry.ModDataComponents.register(modEventBus);
        com.createstats.registry.ModArmorMaterials.register(modEventBus);
        com.createstats.registry.ModArmInteractionPoints.register(modEventBus);
        CreateStats.DISPLAY_TARGETS.register(modEventBus);
        CreateStats.DISPLAY_SOURCES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(OverflowGateBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.InductionHeaterBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.InductionRangeBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.CoffeeMachineBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.AirFryerBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.BulkWasherBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.OvenBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.BiofuelBurnerBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.ToasterBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.BreadSlicerBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.ArcFurnaceBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.InductionSmelterBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.VacuumCollectorBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.VacuumCleanerBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.VacuumSnifferBE::registerCapabilities);
        modEventBus.addListener(com.createstats.power.FeCapabilities::register);
        if (net.neoforged.fml.ModList.get().isLoaded("linktablet")) {
            com.createstats.compat.linktablet.LinkTabletCompat.init(modEventBus);
        }
        NeoForge.EVENT_BUS.register(this);

        NeoForge.EVENT_BUS.register(com.createstats.security.SensorWatch.class);

        NeoForge.EVENT_BUS.register(com.createstats.security.PanelGuard.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.createstats.client.ModPartialModels.init();
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            this.registerDisplayTargetMappings();
            this.registerScheduleConditions();
            this.registerPilotHelmetAsGoggles();
            this.registerInductionHeaterAsBoilerHeat();
            com.createstats.powertrack.CurrentCollectorBlock.registerHanging();
            if (net.neoforged.fml.ModList.get().isLoaded("geckolib")) {
                com.createstats.compat.geckolib.GeckolibCompat.registerSyncedAnimatable();
            }
            return;
        });
        CreateStats.LOGGER.info("{} common setup complete!", CreateStats.MOD_NAME);
    }

    private void registerPilotHelmetAsGoggles() {
        com.simibubi.create.content.equipment.goggles.GogglesItem.addIsWearingPredicate(player -> {
            net.minecraft.world.item.ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            return !head.isEmpty() && head.getItem() instanceof com.createstats.item.PilotHelmetItem;
        });
        CreateStats.LOGGER.info("Registered Pilot Helmet as a goggles-equivalent for Create overlays.");
    }

    private void registerDisplayTargetMappings() {
        final StatsDisplayTarget target = (StatsDisplayTarget)CreateStats.STATS_PANEL_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.STATS_DISPLAY_PANEL.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.STATS_RECORDER.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.HOLOGRAPHIC_PROJECTOR.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.STATUS_TOWER_LIGHT.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.DATA_DIAL.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.MINI_DISPLAY.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATS_DISPLAY_PANEL.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATS_RECORDER.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.HOLOGRAPHIC_PROJECTOR.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATUS_TOWER_LIGHT.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.DATA_DIAL.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.MINI_DISPLAY.get(), target);

        DisplayTarget.BY_BLOCK.register(ModBlocks.STATS_DISPLAY_MK2.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.STATS_DISPLAY_MK3.get(), target);
        DisplayTarget.BY_BLOCK.register(ModBlocks.STATS_DISPLAY_MK3_CONSOLE.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATS_DISPLAY_MK2.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATS_DISPLAY_MK3.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.STATS_DISPLAY_MK3_CONSOLE.get(), target);

        DisplayTarget.BY_BLOCK.register(ModBlocks.HOLOGRAPHIC_PROJECTOR_MK3.get(), target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.HOLOGRAPHIC_PROJECTOR_MK3.get(), target);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> BY_BLOCK + BY_BLOCK_ENTITY", "create_stats", CreateStats.STATS_PANEL_TARGET.getId());
        final ProductionDetectorDisplayTarget productionDetectorTarget = (ProductionDetectorDisplayTarget)CreateStats.PRODUCTION_DETECTOR_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.PRODUCTION_DETECTOR.get(), productionDetectorTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.PRODUCTION_DETECTOR.get(), productionDetectorTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> Inventory Display", "create_stats", CreateStats.PRODUCTION_DETECTOR_TARGET.getId());
        final FccDisplayTarget fccTarget = (FccDisplayTarget) CreateStats.FCC_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.FLIGHT_CONTROL_COMPUTER.get(), fccTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get(), fccTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> FCC", "create_stats", CreateStats.FCC_TARGET.getId());
        final com.createstats.vccmk3.VccMk3DisplayTarget vccMk3Target = CreateStats.VCC_MK3_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.VCC_MK3.get(), vccMk3Target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.VCC_MK3.get(), vccMk3Target);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> VCC Mk III", "create_stats", CreateStats.VCC_MK3_TARGET.getId());
        final com.createstats.vccmk2.VccMk2DisplayTarget vccMk2Target = CreateStats.VCC_MK2_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.VCC_MK_II.get(), vccMk2Target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.VCC_MK_II.get(), vccMk2Target);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> VCC Mk II", "create_stats", CreateStats.VCC_MK2_TARGET.getId());
        final PathfinderDisplayTarget pathfinderTarget = CreateStats.PATHFINDER_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.PATHFINDER_COMPUTER.get(), pathfinderTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.PATHFINDER_COMPUTER.get(), pathfinderTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> Pathfinder Computer", "create_stats", CreateStats.PATHFINDER_TARGET.getId());
        final com.createstats.display.EInkPanelDisplayTarget einkTarget = CreateStats.EINK_PANEL_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.EINK_PANEL.get(), einkTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.EINK_PANEL.get(), einkTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> E-Ink Data Panel", "create_stats", CreateStats.EINK_PANEL_TARGET.getId());
        this.registerStatsLinkMappings();
    }

    private void registerStatsLinkMappings() {
        final AutoStatsLinkDisplaySource autoStatsLinkSource = (AutoStatsLinkDisplaySource)CreateStats.AUTO_STATS_LINK_SOURCE.get();
        final AeroStatsLinkDisplaySource aeroStatsLinkSource = (AeroStatsLinkDisplaySource)CreateStats.AERO_STATS_LINK_SOURCE.get();
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.STRESSOMETER);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.SPEEDOMETER);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.THRESHOLD_SWITCH);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.FLUID_TANK);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.CREATIVE_FLUID_TANK);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.ITEM_VAULT);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.SMART_OBSERVER);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.TRACK_STATION);
        this.registerDisplaySourceMapping(autoStatsLinkSource, AllBlocks.FACTORY_GAUGE);
        final TunnelStatsLinkDisplaySource tunnelStatsLinkSource = (TunnelStatsLinkDisplaySource)CreateStats.TUNNEL_STATS_LINK_SOURCE.get();
        this.registerDisplaySourceMapping(tunnelStatsLinkSource, AllBlocks.ANDESITE_TUNNEL);
        this.registerDisplaySourceMapping(tunnelStatsLinkSource, AllBlocks.BRASS_TUNNEL);
        this.registerDisplaySourceMapping(tunnelStatsLinkSource, AllBlocks.CHUTE);
        this.registerDisplaySourceMapping(tunnelStatsLinkSource, AllBlocks.SMART_CHUTE);
        final ProductionDetectorStatsLinkDisplaySource productionDetectorStatsLinkSource = (ProductionDetectorStatsLinkDisplaySource)CreateStats.PRODUCTION_DETECTOR_STATS_LINK_SOURCE.get();
        this.registerDisplaySourceMapping(productionDetectorStatsLinkSource, ModBlocks.PRODUCTION_DETECTOR);
        final BattleScreenDisplaySource battleScreenSource = CreateStats.BATTLE_SCREEN_SOURCE.get();
        this.registerDisplaySourceMapping(battleScreenSource, ModBlocks.BATTLE_SCREEN_DISPLAY);
        final DamageObserverDisplaySource damageObserverSource = CreateStats.DAMAGE_OBSERVER_SOURCE.get();
        this.registerDisplaySourceMapping(damageObserverSource, ModBlocks.DAMAGE_OBSERVER);
        final SafetyClutchDisplaySource safetyClutchSource = (SafetyClutchDisplaySource)CreateStats.SAFETY_CLUTCH_SOURCE.get();
        this.registerDisplaySourceMapping(safetyClutchSource, ModBlocks.SAFETY_CLUTCH);
        final KineticFlywheelDisplaySource kineticFlywheelSource = (KineticFlywheelDisplaySource)CreateStats.KINETIC_FLYWHEEL_SOURCE.get();
        this.registerDisplaySourceMapping(kineticFlywheelSource, ModBlocks.KINETIC_FLYWHEEL);
        final InductionHeaterDisplaySource inductionHeaterSource = (InductionHeaterDisplaySource)CreateStats.INDUCTION_HEATER_SOURCE.get();
        this.registerDisplaySourceMapping(inductionHeaterSource, ModBlocks.INDUCTION_HEATER);
        final FlowMeterDisplaySource flowMeterSource = (FlowMeterDisplaySource)CreateStats.FLOW_METER_SOURCE.get();
        this.registerDisplaySourceMapping(flowMeterSource, ModBlocks.FLOW_METER);

        final com.createstats.display.PowerMeterDisplaySource powerMeterSource = CreateStats.POWER_METER_SOURCE.get();
        this.registerDisplaySourceMapping(powerMeterSource, ModBlocks.POWER_METER);

        final com.createstats.display.PowerLimiterDisplaySource powerLimiterSource = CreateStats.POWER_LIMITER_SOURCE.get();
        this.registerDisplaySourceMapping(powerLimiterSource, ModBlocks.POWER_LIMITER);

        final com.createstats.display.PowerDisplaySource powerSource = CreateStats.POWER_SOURCE.get();
        this.registerDisplaySourceMapping(powerSource, ModBlocks.KINETIC_DYNAMO);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.POWER_CONNECTOR);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.CAPACITOR_BANK);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.POWER_DISTRIBUTION_UNIT);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.PDU_CABINET);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.FE_BATTERY_CONTROLLER);

        final com.createstats.display.AnnunciatorDisplaySource annunciatorSource = CreateStats.ANNUNCIATOR_SOURCE.get();
        this.registerDisplaySourceMapping(annunciatorSource, ModBlocks.KLAXON_ALARM);
        this.registerDisplaySourceMapping(annunciatorSource, ModBlocks.SENTRY_ALARM);
        final AutoTransmissionClutchDisplaySource autoTransmissionSource = (AutoTransmissionClutchDisplaySource)CreateStats.AUTO_TRANSMISSION_CLUTCH_SOURCE.get();
        this.registerDisplaySourceMapping(autoTransmissionSource, ModBlocks.AUTO_TRANSMISSION_CLUTCH);
        final ControlClutchDisplaySource controlClutchSource = (ControlClutchDisplaySource)CreateStats.CONTROL_CLUTCH_SOURCE.get();
        this.registerDisplaySourceMapping(controlClutchSource, ModBlocks.CONTROL_CLUTCH);
        final LoadBalancerDisplaySource loadBalancerSource = (LoadBalancerDisplaySource)CreateStats.LOAD_BALANCER_SOURCE.get();
        this.registerDisplaySourceMapping(loadBalancerSource, ModBlocks.LOAD_BALANCER);
        final SmartGearshiftDisplaySource smartGearshiftSource = (SmartGearshiftDisplaySource)CreateStats.SMART_GEARSHIFT_SOURCE.get();
        this.registerDisplaySourceMapping(smartGearshiftSource, ModBlocks.SMART_GEARSHIFT);
        final MiniDisplayDisplaySource miniDisplaySource = (MiniDisplayDisplaySource)CreateStats.MINI_DISPLAY_SOURCE.get();
        this.registerDisplaySourceMapping(miniDisplaySource, ModBlocks.MINI_DISPLAY);
        final FreightManifestClipboardDisplaySource freightManifestSource = (FreightManifestClipboardDisplaySource)CreateStats.FREIGHT_MANIFEST_CLIPBOARD_SOURCE.get();
        this.registerDisplaySourceMapping(freightManifestSource, ModBlocks.FREIGHT_MANIFEST_CLIPBOARD);
        final DispatchBoardDisplaySource dispatchBoardSource = (DispatchBoardDisplaySource)CreateStats.DISPATCH_BOARD_SOURCE.get();
        this.registerDisplaySourceMapping(dispatchBoardSource, ModBlocks.DISPATCH_BOARD);
        final PathScannerDisplaySource pathScannerSource = CreateStats.PATH_SCANNER_SOURCE.get();
        this.registerDisplaySourceMapping(pathScannerSource, ModBlocks.PATH_SCANNER);
        final PathfinderComputerDisplaySource pathfinderSource = CreateStats.PATHFINDER_SOURCE.get();
        this.registerDisplaySourceMapping(pathfinderSource, ModBlocks.PATHFINDER_COMPUTER);

        final com.createstats.netscan.NetscanDisplaySource topologyScannerSource =
                CreateStats.TOPOLOGY_SCANNER_SOURCE.get();
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.TOPOLOGY_SCANNER);
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.ADVANCED_TOPOLOGY_SCANNER);
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER);
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.TOPOLOGY_SCANNER_PROBE);
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.ADVANCED_TOPOLOGY_SCANNER_PROBE);
        this.registerDisplaySourceMapping(topologyScannerSource, ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER_PROBE);

        final com.createstats.display.AerialDisplaySource aerialSource = CreateStats.AERIAL_SOURCE.get();
        DisplaySource.BY_BLOCK.addProvider(candidate ->
                candidate instanceof com.createstats.aerial.AerialTelemetryBlock ? aerialSource : null);

        this.registerDisplaySourceMapping(CreateStats.CINE_CAMERA_SOURCE.get(), ModBlocks.CINE_CAMERA);

        this.registerDisplaySourceMapping(CreateStats.SEISMIC_SOURCE.get(), ModBlocks.SEISMIC_SCANNER);
        final com.createstats.display.PathwayStationDisplaySource stationSource = CreateStats.PATHWAY_STATION_SOURCE.get();
        DisplaySource.BY_BLOCK.addProvider(candidate ->
                candidate instanceof net.minecraft.world.level.block.SignBlock ? stationSource : null);
        final com.createstats.display.GroundDisplaySource groundSource = CreateStats.GROUND_SOURCE.get();
        DisplaySource.BY_BLOCK.addProvider(candidate ->
                candidate instanceof com.createstats.ground.GroundTelemetryBlock ? groundSource : null);
        if (SimulatedCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> SimulatedCompatHelper.supportsStatsLinkBlock(candidate) ? aeroStatsLinkSource : null);
        }
        if (CreateAdditionCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> CreateAdditionCompatHelper.supportsStatsLinkBlock(candidate) ? autoStatsLinkSource : null);
        }
        if (DieselCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> DieselCompatHelper.supportsStatsLinkBlock(candidate) ? autoStatsLinkSource : null);
        }
        if (com.createstats.compat.NewAgeCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate ->
                    com.createstats.compat.NewAgeCompatHelper.supportsStatsLinkBlock(candidate)
                            ? autoStatsLinkSource : null);
        }
        // RS gets its own source, it's a whole stock list not a number. by registry id so no hard dep

        if (com.createstats.compat.RefinedStorageCompat.isAvailable()) {
            final com.createstats.display.RefinedStorageDisplaySource refinedStorageSource =
                    CreateStats.REFINED_STORAGE_SOURCE.get();
            DisplaySource.BY_BLOCK.addProvider(candidate ->
                    com.createstats.compat.RefinedStorageCompatHelper.supportsStatsLinkBlock(candidate)
                            ? refinedStorageSource : null);
        }
        CreateStats.LOGGER.info("[{}] Display Source registered: Stats Link / Cargo Link -> supported display-link sources", "create_stats");
    }

    private void registerInductionHeaterAsBoilerHeat() {
        com.simibubi.create.api.boiler.BoilerHeater.REGISTRY.register(
            ModBlocks.INDUCTION_HEATER.get(),
            com.simibubi.create.api.boiler.BoilerHeater.BLAZE_BURNER);

        com.simibubi.create.api.boiler.BoilerHeater.REGISTRY.register(
            ModBlocks.INDUCTION_RANGE.get(),
            com.simibubi.create.api.boiler.BoilerHeater.BLAZE_BURNER);
        CreateStats.LOGGER.info("Registered Induction Heater as a boiler heat source.");
    }

    private void registerDisplaySourceMapping(DisplaySource source, Supplier<? extends Block> blockSupplier) {
        final Block block = (Block)blockSupplier.get();
        DisplaySource.BY_BLOCK.addProvider(candidate -> (candidate == block) ? source : null);
    }

    private void registerScheduleConditions() {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "manifest_met");
        final boolean alreadyRegistered = Schedule.CONDITION_TYPES.stream().anyMatch(pair -> pair.getFirst().equals(id));
        if (!alreadyRegistered) {
            Schedule.CONDITION_TYPES.add(Pair.of(id, CargoManifestMetCondition::new));
        }
        final ResourceLocation chargeId = com.createstats.train.power.TrainChargeCondition.ID;
        if (Schedule.CONDITION_TYPES.stream().noneMatch(pair -> pair.getFirst().equals(chargeId))) {
            Schedule.CONDITION_TYPES.add(Pair.of(chargeId, com.createstats.train.power.TrainChargeCondition::new));
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ManifestUnloadTracker.clearAll();
        this.pendingAeroStatsLinkMigrations.clear();

        com.createstats.train.smart.RailNetworkService.reset();

        com.createstats.support.SupportAccess.resetAuditEnabled();
    }

    // forget cached foreign handler verdicts on reload, tags may have changed
    @SubscribeEvent
    public void onTagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
        com.createstats.power.FeForeignHandlers.forgetLearned();
    }

    @SubscribeEvent
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        com.createstats.item.HingeItem.clearPending(event.getEntity().getUUID());

        com.createstats.item.CopperWireItem.clearPending(event.getEntity().getUUID());

        com.createstats.item.ApplianceCordItem.clearPending(event.getEntity().getUUID());

        com.createstats.compat.ForeignSpoolHandler.clearPending(event.getEntity().getUUID());
        com.createstats.item.SwitchLinkCardItem.clearPending(event.getEntity().getUUID());
        com.createstats.power.CablePlacement.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        final ResourceLocation dimensionId = level.dimension().location();
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof DisplayLinkBlockEntity displayLink
                    && displayLink.activeSource == CreateStats.AUTO_STATS_LINK_SOURCE.get()) {
                this.pendingAeroStatsLinkMigrations
                        .computeIfAbsent(dimensionId, ignored -> new HashSet<>())
                        .add(displayLink.getBlockPos().immutable());
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        com.createstats.train.smart.RailNetworkService.tick(level);

        com.createstats.train.power.CarriedDeviceSync.tick(level);
        if (level instanceof net.minecraft.server.level.ServerLevel stationSweepLevel
                && stationSweepLevel.getGameTime() % 20L == 0L) {
            com.createstats.train.manifest.FreightManifestLogic.sweepDepartedVisits(stationSweepLevel);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel manifestLevel) {
            com.createstats.train.egg.ManifestShow.tick(manifestLevel);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getGameTime() % 20L == 0L
                && com.createstats.flight.bridge.HingeConstraintBridge.isAvailable()) {
            com.createstats.flight.hinge.HingeJointManager.get(serverLevel).ensureHandles(serverLevel);
        }
        final ResourceLocation dimensionId = level.dimension().location();
        final Set<BlockPos> pending = this.pendingAeroStatsLinkMigrations.get(dimensionId);
        if (pending == null || pending.isEmpty()) {
            this.pendingAeroStatsLinkMigrations.remove(dimensionId);
            return;
        }

        for (BlockPos pos : List.copyOf(pending)) {
            final var blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
                pending.remove(pos);
                continue;
            }
            if (this.tryMigrateAeroStatsLink(displayLink)) {
                pending.remove(pos);
            }
        }
        if (pending.isEmpty()) {
            this.pendingAeroStatsLinkMigrations.remove(dimensionId);
        }
    }

    public static CreateRegistrate registrate() {
        return CreateStats.REGISTRATE;
    }

    static {
        LOGGER = LoggerFactory.getLogger(CreateStats.MOD_NAME);
        REGISTRATE = CreateRegistrate.create("create_stats")
            .defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
        DISPLAY_TARGETS = DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, "create_stats");
        STATS_PANEL_TARGET = CreateStats.DISPLAY_TARGETS.register("stats_panel", StatsDisplayTarget::new);
        PRODUCTION_DETECTOR_TARGET = CreateStats.DISPLAY_TARGETS.register("inventory_display", ProductionDetectorDisplayTarget::new);
        FCC_TARGET = CreateStats.DISPLAY_TARGETS.register("fcc_display", FccDisplayTarget::new);
        VCC_MK3_TARGET = CreateStats.DISPLAY_TARGETS.register("vcc_mk3_display", com.createstats.vccmk3.VccMk3DisplayTarget::new);
        VCC_MK2_TARGET = CreateStats.DISPLAY_TARGETS.register("vcc_mk2_display", com.createstats.vccmk2.VccMk2DisplayTarget::new);
        PATHFINDER_TARGET = CreateStats.DISPLAY_TARGETS.register("pathfinder_computer", PathfinderDisplayTarget::new);
        EINK_PANEL_TARGET = CreateStats.DISPLAY_TARGETS.register("eink_panel", com.createstats.display.EInkPanelDisplayTarget::new);
        DISPLAY_SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, "create_stats");
        AUTO_STATS_LINK_SOURCE = CreateStats.DISPLAY_SOURCES.register("auto_stats_link", AutoStatsLinkDisplaySource::new);
        AERO_STATS_LINK_SOURCE = CreateStats.DISPLAY_SOURCES.register("aero_stats_link", AeroStatsLinkDisplaySource::new);
        TUNNEL_STATS_LINK_SOURCE = CreateStats.DISPLAY_SOURCES.register("tunnel_stats_link", TunnelStatsLinkDisplaySource::new);
        PRODUCTION_DETECTOR_SOURCE = CreateStats.DISPLAY_SOURCES.register("production_detector", ProductionDetectorDisplaySource::new);
        PRODUCTION_DETECTOR_STATS_LINK_SOURCE = CreateStats.DISPLAY_SOURCES.register("production_detector_stats_link", ProductionDetectorStatsLinkDisplaySource::new);
        BATTLE_SCREEN_SOURCE = CreateStats.DISPLAY_SOURCES.register("battle_screen", BattleScreenDisplaySource::new);
        DAMAGE_OBSERVER_SOURCE = CreateStats.DISPLAY_SOURCES.register("damage_observer", DamageObserverDisplaySource::new);
        SAFETY_CLUTCH_SOURCE = CreateStats.DISPLAY_SOURCES.register("safety_clutch", SafetyClutchDisplaySource::new);
        KINETIC_FLYWHEEL_SOURCE = CreateStats.DISPLAY_SOURCES.register("kinetic_flywheel", KineticFlywheelDisplaySource::new);
        INDUCTION_HEATER_SOURCE = CreateStats.DISPLAY_SOURCES.register("induction_heater", InductionHeaterDisplaySource::new);
        AUTO_TRANSMISSION_CLUTCH_SOURCE = CreateStats.DISPLAY_SOURCES.register("auto_transmission_clutch", AutoTransmissionClutchDisplaySource::new);
        CONTROL_CLUTCH_SOURCE = CreateStats.DISPLAY_SOURCES.register("control_clutch", ControlClutchDisplaySource::new);
        LOAD_BALANCER_SOURCE = CreateStats.DISPLAY_SOURCES.register("load_balancer", LoadBalancerDisplaySource::new);
        FLOW_METER_SOURCE = CreateStats.DISPLAY_SOURCES.register("flow_meter", FlowMeterDisplaySource::new);
        REFINED_STORAGE_SOURCE = CreateStats.DISPLAY_SOURCES.register("refined_storage_network", com.createstats.display.RefinedStorageDisplaySource::new);
        POWER_METER_SOURCE = CreateStats.DISPLAY_SOURCES.register("power_meter", com.createstats.display.PowerMeterDisplaySource::new);
        POWER_LIMITER_SOURCE = CreateStats.DISPLAY_SOURCES.register("power_limiter", com.createstats.display.PowerLimiterDisplaySource::new);
        POWER_SOURCE = CreateStats.DISPLAY_SOURCES.register("power_readout", com.createstats.display.PowerDisplaySource::new);
        ANNUNCIATOR_SOURCE = CreateStats.DISPLAY_SOURCES.register("annunciator_status", com.createstats.display.AnnunciatorDisplaySource::new);
        SMART_GEARSHIFT_SOURCE = CreateStats.DISPLAY_SOURCES.register("smart_gearshift", SmartGearshiftDisplaySource::new);
        MINI_DISPLAY_SOURCE = CreateStats.DISPLAY_SOURCES.register("mini_display", MiniDisplayDisplaySource::new);
        FREIGHT_MANIFEST_CLIPBOARD_SOURCE = CreateStats.DISPLAY_SOURCES.register("freight_manifest_clipboard", FreightManifestClipboardDisplaySource::new);
        DISPATCH_BOARD_SOURCE = CreateStats.DISPLAY_SOURCES.register("dispatch_board", DispatchBoardDisplaySource::new);
        PATH_SCANNER_SOURCE = CreateStats.DISPLAY_SOURCES.register("path_scanner", PathScannerDisplaySource::new);
        PATHFINDER_SOURCE = CreateStats.DISPLAY_SOURCES.register("pathfinder_computer", PathfinderComputerDisplaySource::new);
        TOPOLOGY_SCANNER_SOURCE = CreateStats.DISPLAY_SOURCES.register("topology_scanner", com.createstats.netscan.NetscanDisplaySource::new);
        AERIAL_SOURCE = CreateStats.DISPLAY_SOURCES.register("vcc_aerial", com.createstats.display.AerialDisplaySource::new);
        GROUND_SOURCE = CreateStats.DISPLAY_SOURCES.register("vcc_ground", com.createstats.display.GroundDisplaySource::new);
        PATHWAY_STATION_SOURCE = CreateStats.DISPLAY_SOURCES.register("pathway_station", com.createstats.display.PathwayStationDisplaySource::new);
        CINE_CAMERA_SOURCE = CreateStats.DISPLAY_SOURCES.register("cine_camera", com.createstats.display.CineCameraDisplaySource::new);
        SEISMIC_SOURCE = CreateStats.DISPLAY_SOURCES.register("seismic_scanner", com.createstats.display.SeismicDisplaySource::new);
    }

    private boolean tryMigrateAeroStatsLink(DisplayLinkBlockEntity displayLink) {
        if (displayLink.activeSource != CreateStats.AUTO_STATS_LINK_SOURCE.get()) {
            return true;
        }
        final Level level = displayLink.getLevel();
        if (level == null) {
            return true;
        }
        final BlockPos sourcePos = displayLink.getSourcePosition();
        if (!level.isLoaded(sourcePos)) {
            return false;
        }
        if (!SimulatedCompatHelper.supportsStatsLinkBlock(level.getBlockState(sourcePos).getBlock())) {
            return true;
        }
        displayLink.activeSource = CreateStats.AERO_STATS_LINK_SOURCE.get();
        displayLink.refreshTicks = 0;
        displayLink.setChanged();
        displayLink.sendData();
        return true;
    }
}
