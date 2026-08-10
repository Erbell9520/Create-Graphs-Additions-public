package com.createstats;

import java.util.function.Supplier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import com.createstats.registry.ModBlocks;
import com.createstats.display.AeroStatsLinkDisplaySource;
import com.createstats.display.AutoStatsLinkDisplaySource;
import com.createstats.display.BattleScreenDisplaySource;
import com.createstats.display.BattleScreenDisplayTarget;
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
    public static final String MOD_NAME = "Create Stats and Numbers";
    public static final Logger LOGGER;
    private static final CreateRegistrate REGISTRATE;
    public static final DeferredRegister<DisplayTarget> DISPLAY_TARGETS;
    public static final DeferredHolder<DisplayTarget, StatsDisplayTarget> STATS_PANEL_TARGET;
    public static final DeferredHolder<DisplayTarget, ProductionDetectorDisplayTarget> PRODUCTION_DETECTOR_TARGET;
    public static final DeferredHolder<DisplayTarget, BattleScreenDisplayTarget> BATTLE_SCREEN_TARGET;
    public static final DeferredHolder<DisplayTarget, FccDisplayTarget> FCC_TARGET;
    public static final DeferredHolder<DisplayTarget, com.createstats.vccmk2.VccMk2DisplayTarget> VCC_MK2_TARGET;
    public static final DeferredHolder<DisplayTarget, PathfinderDisplayTarget> PATHFINDER_TARGET;
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
    public static final DeferredHolder<DisplaySource, com.createstats.display.PowerDisplaySource> POWER_SOURCE;
    public static final DeferredHolder<DisplaySource, SmartGearshiftDisplaySource> SMART_GEARSHIFT_SOURCE;
    public static final DeferredHolder<DisplaySource, MiniDisplayDisplaySource> MINI_DISPLAY_SOURCE;
    public static final DeferredHolder<DisplaySource, FreightManifestClipboardDisplaySource> FREIGHT_MANIFEST_CLIPBOARD_SOURCE;
    public static final DeferredHolder<DisplaySource, DispatchBoardDisplaySource> DISPATCH_BOARD_SOURCE;
    public static final DeferredHolder<DisplaySource, PathScannerDisplaySource> PATH_SCANNER_SOURCE;
    public static final DeferredHolder<DisplaySource, PathfinderComputerDisplaySource> PATHFINDER_SOURCE;
    private final Map<ResourceLocation, Set<BlockPos>> pendingAeroStatsLinkMigrations = new HashMap<>();
    
    public CreateStats(IEventBus modEventBus, ModContainer modContainer) {
        CreateStats.LOGGER.info("{} initializing!", "Create Stats and Numbers");
        CreateStats.REGISTRATE.setCreativeTab(ModCreativeTab.CREATE_STATS_TAB);
        CreateStats.REGISTRATE.registerEventListeners(modEventBus);
        ModConfiguration.register(modContainer);
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenuTypes.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        ModSounds.register(modEventBus);
        com.createstats.registry.ModArmorMaterials.register(modEventBus);
        com.createstats.registry.ModArmInteractionPoints.register(modEventBus);
        CreateStats.DISPLAY_TARGETS.register(modEventBus);
        CreateStats.DISPLAY_SOURCES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(OverflowGateBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.InductionHeaterBE::registerCapabilities);
        modEventBus.addListener(com.createstats.blockentity.InductionRangeBE::registerCapabilities);
        modEventBus.addListener(com.createstats.power.FeCapabilities::register);
        NeoForge.EVENT_BUS.register(this);
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
            if (net.neoforged.fml.ModList.get().isLoaded("geckolib")) {
                com.createstats.compat.geckolib.GeckolibCompat.registerSyncedAnimatable();
            }
            return;
        });
        CreateStats.LOGGER.info("{} common setup complete!", "Create Stats and Numbers");
    }

    /** [1] */
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
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> BY_BLOCK + BY_BLOCK_ENTITY", "create_stats", CreateStats.STATS_PANEL_TARGET.getId());
        final ProductionDetectorDisplayTarget productionDetectorTarget = (ProductionDetectorDisplayTarget)CreateStats.PRODUCTION_DETECTOR_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.PRODUCTION_DETECTOR.get(), productionDetectorTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.PRODUCTION_DETECTOR.get(), productionDetectorTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> Inventory Display", "create_stats", CreateStats.PRODUCTION_DETECTOR_TARGET.getId());
        final BattleScreenDisplayTarget battleScreenTarget = CreateStats.BATTLE_SCREEN_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.BATTLE_SCREEN_DISPLAY.get(), battleScreenTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.BATTLE_SCREEN_DISPLAY.get(), battleScreenTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> Battle Bridge", "create_stats", CreateStats.BATTLE_SCREEN_TARGET.getId());
        final FccDisplayTarget fccTarget = (FccDisplayTarget) CreateStats.FCC_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.FLIGHT_CONTROL_COMPUTER.get(), fccTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get(), fccTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> FCC", "create_stats", CreateStats.FCC_TARGET.getId());
        final com.createstats.vccmk2.VccMk2DisplayTarget vccMk2Target = CreateStats.VCC_MK2_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.VCC_MK_II.get(), vccMk2Target);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.VCC_MK_II.get(), vccMk2Target);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> VCC Mk II", "create_stats", CreateStats.VCC_MK2_TARGET.getId());
        final PathfinderDisplayTarget pathfinderTarget = CreateStats.PATHFINDER_TARGET.get();
        DisplayTarget.BY_BLOCK.register(ModBlocks.PATHFINDER_COMPUTER.get(), pathfinderTarget);
        DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.PATHFINDER_COMPUTER.get(), pathfinderTarget);
        CreateStats.LOGGER.info("[{}] Display Target registered: {} -> Pathfinder Computer", "create_stats", CreateStats.PATHFINDER_TARGET.getId());
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
        // [2]
        final com.createstats.display.PowerDisplaySource powerSource = CreateStats.POWER_SOURCE.get();
        this.registerDisplaySourceMapping(powerSource, ModBlocks.KINETIC_DYNAMO);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.POWER_CONNECTOR);
        this.registerDisplaySourceMapping(powerSource, ModBlocks.CAPACITOR_BANK);
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
        if (SimulatedCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> SimulatedCompatHelper.supportsStatsLinkBlock(candidate) ? aeroStatsLinkSource : null);
        }
        if (CreateAdditionCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> CreateAdditionCompatHelper.supportsStatsLinkBlock(candidate) ? autoStatsLinkSource : null);
        }
        if (DieselCompat.isAvailable()) {
            DisplaySource.BY_BLOCK.addProvider(candidate -> DieselCompatHelper.supportsStatsLinkBlock(candidate) ? autoStatsLinkSource : null);
        }
        CreateStats.LOGGER.info("[{}] Display Source registered: Stats Link / Cargo Link -> supported display-link sources", "create_stats");
    }

    /** [3] */
    private void registerInductionHeaterAsBoilerHeat() {
        com.simibubi.create.api.boiler.BoilerHeater.REGISTRY.register(
            ModBlocks.INDUCTION_HEATER.get(),
            com.simibubi.create.api.boiler.BoilerHeater.BLAZE_BURNER);
        // The Induction Range carries the same HEAT_LEVEL property, so the same lambda reads it. That
        // is the entire payoff of reusing Create's property instead of inventing our own.
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
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ManifestUnloadTracker.clearAll();
        this.pendingAeroStatsLinkMigrations.clear();
    }

    @SubscribeEvent
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop any half-finished hinge link so the pending-anchor map never leaks across sessions.
        com.createstats.item.HingeItem.clearPending(event.getEntity().getUUID());
        // Same for a half-attached copper wire.
        com.createstats.item.CopperWireItem.clearPending(event.getEntity().getUUID());
        com.createstats.item.SwitchLinkCardItem.clearPending(event.getEntity().getUUID());
        com.createstats.power.CablePlacement.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof DisplayLinkBlockEntity displayLink) {
                this.tryMigrateAeroStatsLink(displayLink);
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
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
        final Iterator<BlockPos> iterator = pending.iterator();
        while (iterator.hasNext()) {
            final BlockPos pos = iterator.next();
            final var blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
                iterator.remove();
                continue;
            }
            if (this.tryMigrateAeroStatsLink(displayLink)) {
                iterator.remove();
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
        LOGGER = LoggerFactory.getLogger("Create Stats and Numbers");
        REGISTRATE = CreateRegistrate.create("create_stats")
            .defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
        DISPLAY_TARGETS = DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, "create_stats");
        STATS_PANEL_TARGET = CreateStats.DISPLAY_TARGETS.register("stats_panel", StatsDisplayTarget::new);
        PRODUCTION_DETECTOR_TARGET = CreateStats.DISPLAY_TARGETS.register("inventory_display", ProductionDetectorDisplayTarget::new);
        BATTLE_SCREEN_TARGET = CreateStats.DISPLAY_TARGETS.register("battle_screen_display", BattleScreenDisplayTarget::new);
        FCC_TARGET = CreateStats.DISPLAY_TARGETS.register("fcc_display", FccDisplayTarget::new);
        VCC_MK2_TARGET = CreateStats.DISPLAY_TARGETS.register("vcc_mk2_display", com.createstats.vccmk2.VccMk2DisplayTarget::new);
        PATHFINDER_TARGET = CreateStats.DISPLAY_TARGETS.register("pathfinder_computer", PathfinderDisplayTarget::new);
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
        POWER_SOURCE = CreateStats.DISPLAY_SOURCES.register("power_readout", com.createstats.display.PowerDisplaySource::new);
        SMART_GEARSHIFT_SOURCE = CreateStats.DISPLAY_SOURCES.register("smart_gearshift", SmartGearshiftDisplaySource::new);
        MINI_DISPLAY_SOURCE = CreateStats.DISPLAY_SOURCES.register("mini_display", MiniDisplayDisplaySource::new);
        FREIGHT_MANIFEST_CLIPBOARD_SOURCE = CreateStats.DISPLAY_SOURCES.register("freight_manifest_clipboard", FreightManifestClipboardDisplaySource::new);
        DISPATCH_BOARD_SOURCE = CreateStats.DISPLAY_SOURCES.register("dispatch_board", DispatchBoardDisplaySource::new);
        PATH_SCANNER_SOURCE = CreateStats.DISPLAY_SOURCES.register("path_scanner", PathScannerDisplaySource::new);
        PATHFINDER_SOURCE = CreateStats.DISPLAY_SOURCES.register("pathfinder_computer", PathfinderComputerDisplaySource::new);
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
            this.pendingAeroStatsLinkMigrations
                    .computeIfAbsent(level.dimension().location(), ignored -> new HashSet<>())
                    .add(displayLink.getBlockPos().immutable());
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
