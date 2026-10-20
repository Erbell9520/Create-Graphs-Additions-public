package com.createstats;

import org.apache.commons.lang3.tuple.Pair;
import java.util.function.Function;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;

public class ModConfiguration
{
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    private static final int CURRENT_CONFIG_VERSION = 3;

    public static int getEffectiveStatsLinkRefreshTicks() {
        return Math.max(1, Math.min((int)COMMON.statsLinkPassiveRefreshTicks.get(), (int)COMMON.responsiveStatsLinkRefreshTicks.get()));
    }

    public static int cappedViewDistance(int ownDefault) {
        try {
            return Math.min(ownDefault, CLIENT.maxRenderDistance.get());
        } catch (RuntimeException ignored) {
            return ownDefault;
        }
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfiguration.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ModConfiguration.CLIENT_SPEC);
        modEventBus.addListener(ModConfiguration::onConfigLoad);
    }

    // neoforge never rewrites a value already in the file, so old configs keep old numbers. migrate once, stamp the version
    private static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != COMMON_SPEC) {
            return;
        }
        final int stored = COMMON.configVersion.get();
        if (stored >= CURRENT_CONFIG_VERSION) {
            return;
        }
        if (stored < 1) {
            COMMON.capacitorBankTransferRate.set(32768);
        }
        if (stored < 2) {
            final FePowerValues alarm = COMMON.fePowerProfiles.get("statsAlarm");
            if (alarm != null) {
                alarm.bufferCapacity.set(2560);
                alarm.idleDrain.set(16);
                alarm.activeDrain.set(16);
                alarm.burstCost.set(0);
            }
        }
        if (stored < 3) {
            COMMON.pumpFeCostPerMb.set(4.5);
        }
        COMMON.configVersion.set(CURRENT_CONFIG_VERSION);
        COMMON_SPEC.save();
    }

    static {
        final Pair<Common, ModConfigSpec> commonPair = (Pair<Common, ModConfigSpec>)new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = (ModConfigSpec)commonPair.getRight();
        COMMON = (Common)commonPair.getLeft();
        final Pair<Client, ModConfigSpec> clientPair = (Pair<Client, ModConfigSpec>)new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = (ModConfigSpec)clientPair.getRight();
        CLIENT = (Client)clientPair.getLeft();
    }

    public static class Common
    {
        public final ModConfigSpec.IntValue configVersion;
        public final ModConfigSpec.IntValue panelBufferSize;
        public final ModConfigSpec.IntValue recorderBufferSize;
        public final ModConfigSpec.IntValue sampleIntervalTicks;
        public final ModConfigSpec.IntValue statsLinkPassiveRefreshTicks;
        public final ModConfigSpec.IntValue responsiveStatsLinkRefreshTicks;
        public final ModConfigSpec.BooleanValue enableCsvExport;
        public final ModConfigSpec.IntValue csvAutoExportIntervalMinutes;
        public final ModConfigSpec.IntValue dashboardMinBlocks;
        public final ModConfigSpec.IntValue dashboardMinDimension;
        public final ModConfigSpec.BooleanValue dashboardTickerEnabled;
        public final ModConfigSpec.IntValue dashboardTickerSpeed;
        public final ModConfigSpec.IntValue topologyMaxNodes;
        public final ModConfigSpec.IntValue topologyScanRadius;
        public final ModConfigSpec.IntValue topologyLayoutIterations;
        public final ModConfigSpec.IntValue trainMapLayoutIterations;
        public final ModConfigSpec.IntValue scannerBudgetField;
        public final ModConfigSpec.IntValue scannerBudgetAdvanced;
        public final ModConfigSpec.IntValue scannerBudgetIndustrial;
        public final ModConfigSpec.IntValue scannerRateField;
        public final ModConfigSpec.IntValue scannerRateAdvanced;
        public final ModConfigSpec.IntValue scannerRateIndustrial;
        public final ModConfigSpec.DoubleValue scannerStressField;
        public final ModConfigSpec.DoubleValue scannerStressAdvanced;
        public final ModConfigSpec.DoubleValue scannerStressIndustrial;
        public final ModConfigSpec.IntValue scannerRescanIntervalTicks;
        public final ModConfigSpec.IntValue scannerLiveSyncIntervalTicks;
        public final ModConfigSpec.IntValue scannerOverlayMaxElements;
        public final ModConfigSpec.DoubleValue scannerOverlayRange;
        public final ModConfigSpec.BooleanValue showRawDieselMetrics;
        public final ModConfigSpec.BooleanValue fccVerboseLogging;
        public final ModConfigSpec.BooleanValue rpmGovernorVerboseLogging;
        public final ModConfigSpec.BooleanValue antennaDebugLogging;
        public final ModConfigSpec.BooleanValue vccPersistControlsOnLoad;
        public final ModConfigSpec.BooleanValue showExperimentalBlocks;
        public final ModConfigSpec.DoubleValue inductionHeaterStressImpact;
        public final ModConfigSpec.IntValue inductionCoilLifetimeSeconds;
        public final ModConfigSpec.IntValue antennaRangeMultiplierSmall;
        public final ModConfigSpec.IntValue antennaRangeMultiplierAdvanced;
        public final ModConfigSpec.IntValue antennaRangeMultiplierTrt;
        public final ModConfigSpec.DoubleValue upgradeMultiplierFirst;
        public final ModConfigSpec.DoubleValue upgradeMultiplierSecond;
        public final ModConfigSpec.DoubleValue upgradeMultiplierThird;
        public final ModConfigSpec.IntValue lockerMaxRows;
        public final ModConfigSpec.IntValue lockerMaxStack;
        public final ModConfigSpec.BooleanValue creativeEnergy;
        public final ModConfigSpec.BooleanValue feShowLoginReminder;
        public final ModConfigSpec.DoubleValue feSourceLoadExponent;
        public final ModConfigSpec.IntValue feRebootThresholdPct;
        public final java.util.Map<String, FePowerValues> fePowerProfiles;
        public final ModConfigSpec.DoubleValue dynamoFePerRpm;
        public final ModConfigSpec.DoubleValue dynamoStressImpact;
        public final ModConfigSpec.IntValue dynamoBufferCapacity;
        public final ModConfigSpec.IntValue dynamoTransferRate;
        public final ModConfigSpec.IntValue solarFePerTick;
        public final ModConfigSpec.IntValue connectorTransferRate;
        public final ModConfigSpec.IntValue connectorBufferCapacity;
        public final ModConfigSpec.BooleanValue cableThroughputLimit;
        public final ModConfigSpec.IntValue connectorWireRange;
        public final ModConfigSpec.IntValue limiterMaxRate;
        public final ModConfigSpec.IntValue mainBreakerTripSeconds;
        public final ModConfigSpec.IntValue capacitorBankCapacity;
        public final ModConfigSpec.DoubleValue capacitorBankExponent;
        public final ModConfigSpec.IntValue capacitorBankMaxFootprint;
        public final ModConfigSpec.IntValue capacitorBankMaxHeight;
        public final ModConfigSpec.IntValue capacitorBankTransferRate;
        public final ModConfigSpec.BooleanValue reserveCascadeEnabled;
        public final ModConfigSpec.IntValue portableInterfaceMaxRate;
        public final ModConfigSpec.IntValue portableInterfaceFloorPct;
        public final ModConfigSpec.IntValue batteryControllerPortRate;
        public final ModConfigSpec.IntValue batteryControllerBuffer;
        public final ModConfigSpec.IntValue batteryControllerBandFloorPct;
        public final ModConfigSpec.IntValue batteryControllerBandCeilingPct;
        public final ModConfigSpec.IntValue batteryControllerBandHealthyPct;
        public final ModConfigSpec.IntValue batteryControllerBandFloorMinutes;
        public final ModConfigSpec.IntValue batteryControllerBandCeilingMinutes;
        public final ModConfigSpec.IntValue batteryControllerBandHealthyMinutes;
        public final ModConfigSpec.IntValue batteryControllerRideTicks;
        public final ModConfigSpec.IntValue batteryControllerChargeClaimPct;
        public final ModConfigSpec.BooleanValue pduCoreProtection;
        public final ModConfigSpec.IntValue pduCoreProtectionPct;
        public final ModConfigSpec.IntValue pduBufferCapacity;
        public final ModConfigSpec.IntValue pduIdleDrain;
        public final ModConfigSpec.IntValue pduReserveFloorPct;
        public final ModConfigSpec.IntValue pduRestoreSocPct;
        public final ModConfigSpec.IntValue pduBurstMarginPct;
        public final ModConfigSpec.IntValue pduDwellTicks;
        public final ModConfigSpec.IntValue pduRestoreIntervalTicks;
        public final ModConfigSpec.IntValue pduRideHorizonTicks;
        public final ModConfigSpec.IntValue pduSupplyReadIntervalTicks;
        public final ModConfigSpec.IntValue pduObservedDecayPct;
        public final ModConfigSpec.IntValue pduFillTicks;
        public final ModConfigSpec.IntValue pduLearnTicks;
        public final ModConfigSpec.BooleanValue pduExportPolicy;
        public final ModConfigSpec.DoubleValue pumpFeCostPerMb;
        public final ModConfigSpec.BooleanValue routeChunkLoading;
        public final ModConfigSpec.IntValue maxRouteVehicles;
        public final ModConfigSpec.IntValue routeChunkRadius;
        public final ModConfigSpec.IntValue pathwayMaxNodes;
        public final ModConfigSpec.IntValue pathwayMaxPerPlayer;
        public final ModConfigSpec.IntValue sparkCrownBaseFePerTick;
        public final ModConfigSpec.IntValue sparkCrownGrowthPercent;
        public final ModConfigSpec.IntValue sparkCrownMaxPerStation;
        public final ModConfigSpec.IntValue sparkCrownMaxPerPlayer;
        public final ModConfigSpec.IntValue sparkCrownRideSeconds;

        Common(ModConfigSpec.Builder builder) {
            this.configVersion = builder.comment(
                    "Internal migration marker. Do not edit by hand: the mod uses this to force-correct",
                    "specific values exactly once when their shipped default changes between versions,",
                    "so a config from an older release does not silently keep reading a stale number.",
                    "Editing this either skips a pending correction or re-applies one that already ran.")
                    .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);
            builder.push("pathways");
            this.routeChunkLoading = builder.comment(
                    "VCC Mk IV vehicles on a route keep their own chunks loaded (under them, and along the line ahead of them),",
                    "so a loop keeps running with its owner at the other end of the map. Off: a route runs only where players are.")
                    .define("routeChunkLoading", true);
            this.maxRouteVehicles = builder.comment(
                    "Vehicles on routes that may hold chunks loaded at once, server-wide. The rest wait parked,",
                    "\"Waiting for a chunk budget\", and set off as soon as one finishes or is paused.")
                    .defineInRange("maxRouteVehicles", 8, 0, 256);
            this.routeChunkRadius = builder.comment("Chunks either side of a route vehicle, and of its line ahead, that it keeps loaded.")
                    .defineInRange("routeChunkRadius", 1, 0, 4);
            this.pathwayMaxNodes = builder.comment("Nodes one pathway may have.").defineInRange("maxNodes", 512, 16, 512);
            this.pathwayMaxPerPlayer = builder.comment("Pathways one player may own in one dimension (operators are not limited).")
                    .defineInRange("maxPerPlayer", 64, 1, 1024);
            builder.pop();

            builder.push("sparkCrown");
            this.sparkCrownBaseFePerTick = builder.comment(
                    "FE per tick the first chunk a Spark Crown holds costs. Each further chunk costs growthPercent more than",
                    "the one before, so 25 chunks cost about 100,707 FE/t at the defaults.")
                    .defineInRange("baseFePerTick", 1024, 1, 1_000_000);
            this.sparkCrownGrowthPercent = builder.comment("How much dearer, in percent, each further chunk is than the one before.")
                    .defineInRange("growthPercent", 10, 0, 100);
            this.sparkCrownMaxPerStation = builder.comment(
                    "Chunks one Spark Crown may hold, its own chunk included. Never more than 25: the crown has five rings of five.")
                    .defineInRange("maxChunksPerStation", 25, 1, 25);
            this.sparkCrownMaxPerPlayer = builder.comment(
                    "Chunks one player may hold across every Spark Crown they own, in every dimension. Checked when a chunk is",
                    "picked; picks made before the number was lowered are kept.")
                    .defineInRange("maxChunksPerPlayer", 50, 1, 1024);
            this.sparkCrownRideSeconds = builder.comment(
                    "Seconds of its draw a Spark Crown's store keeps, to ride through a dip in its feed. When the store runs out",
                    "every chunk it holds is let go at once.")
                    .defineInRange("rideThroughSeconds", 5, 1, 60);
            builder.pop();
            builder.push("data");
            this.panelBufferSize = builder.comment("Max samples stored per display panel (default 600 = 10 min at 1/sec)").defineInRange("panelBufferSize", 600, 60, 6000);
            this.recorderBufferSize = builder.comment("Max samples stored per recorder (default 1800 = 30 min at 1/sec)").defineInRange("recorderBufferSize", 1800, 60, 18000);
            this.sampleIntervalTicks = builder.comment("Ticks between samples (20 = 1 second). Lower = more resolution, more memory").defineInRange("sampleIntervalTicks", 20, 1, 200);
            builder.pop();
            builder.push("display");
            this.statsLinkPassiveRefreshTicks = builder.comment("Passive refresh interval for stats-link auto-read sources (20 = default, 10 = high refresh, 5 = aggressive)").defineInRange("statsLinkPassiveRefreshTicks", 20, 5, 20);
            this.responsiveStatsLinkRefreshTicks = builder.comment("Responsive refresh cap for live stats-link telemetry. Effective cadence uses the lower of this and statsLinkPassiveRefreshTicks. Default 5 for snappier AERO/Simulated updates.").defineInRange("responsiveStatsLinkRefreshTicks", 5, 5, 20);
            builder.pop();
            builder.push("export");
            this.enableCsvExport = builder.comment("Allow CSV export from recorders").define("enableCsvExport", true);
            this.csvAutoExportIntervalMinutes = builder.comment("Auto-export interval in minutes (0 = disabled)").defineInRange("csvAutoExportIntervalMinutes", 0, 0, 60);
            builder.pop();
            builder.push("dashboard");
            this.dashboardMinBlocks = builder.comment("Minimum multiblock blocks (W*H) to auto-activate dashboard mode").defineInRange("dashboardMinBlocks", 6, 4, 64);
            this.dashboardMinDimension = builder.comment("Minimum width or height to allow dashboard (prevents thin strips)").defineInRange("dashboardMinDimension", 3, 2, 8);
            this.dashboardTickerEnabled = builder.comment("Enable scrolling alert ticker in dashboard mode").define("dashboardTickerEnabled", true);
            this.dashboardTickerSpeed = builder.comment("Ticker scroll speed in pixels per texture update (1-5)").defineInRange("dashboardTickerSpeed", 1, 1, 5);
            builder.pop();
            builder.push("topology");
            this.topologyMaxNodes = builder.comment("Default maximum nodes in topology map (higher = more CPU). Panel settings can lower this per display.").defineInRange("topologyMaxNodes", 200, 10, 1000);
            this.topologyScanRadius = builder.comment("Radius to search for kinetic seed blocks").defineInRange("topologyScanRadius", 5, 1, 16);
            this.topologyLayoutIterations = builder.comment("Force-directed layout iterations (higher = better layout, more CPU)").defineInRange("topologyLayoutIterations", 150, 30, 300);

            this.trainMapLayoutIterations = builder.comment("Force-directed layout iterations for the full train map screen (higher = better layout, more CPU)").defineInRange("trainMapLayoutIterations", 180, 30, 500);
            builder.pop();

            builder.comment("Topology Scanner blocks. Budgets are element counts, never radii: kinetic",
                            "networks are shaped like the buildings they run through, so a distance",
                            "bound cuts a long belt run in half and wastes itself on four machines at",
                            "the corners of a chunk.").push("topologyScanner");
            this.scannerBudgetField = builder.comment("Elements one Topology Scanner sweep may reach").defineInRange("scannerBudgetField", 64, 8, 4096);
            this.scannerBudgetAdvanced = builder.comment("Elements one Advanced Topology Scanner sweep may reach").defineInRange("scannerBudgetAdvanced", 128, 8, 4096);
            this.scannerBudgetIndustrial = builder.comment("Elements one Industrial Topology Scanner sweep may reach").defineInRange("scannerBudgetIndustrial", 512, 8, 4096);
            this.scannerRateField = builder.comment("Elements per tick a Topology Scanner sweeps (higher = faster scan, more CPU per tick)").defineInRange("scannerRateField", 4, 1, 128);
            this.scannerRateAdvanced = builder.comment("Elements per tick an Advanced Topology Scanner sweeps").defineInRange("scannerRateAdvanced", 8, 1, 128);
            this.scannerRateIndustrial = builder.comment("Elements per tick an Industrial Topology Scanner sweeps").defineInRange("scannerRateIndustrial", 16, 1, 128);
            this.scannerStressField = builder.comment("Stress units per RPM the inline Topology Scanner draws (16 su at 8 RPM, 512 su at 256 RPM)").defineInRange("scannerStressField", 2.0, 0.0, 64.0);
            this.scannerStressAdvanced = builder.comment("Stress units per RPM the inline Advanced Topology Scanner draws").defineInRange("scannerStressAdvanced", 4.0, 0.0, 64.0);
            this.scannerStressIndustrial = builder.comment("Stress units per RPM the inline Industrial Topology Scanner draws").defineInRange("scannerStressIndustrial", 8.0, 0.0, 64.0);
            this.scannerRescanIntervalTicks = builder.comment("Ticks between full re-sweeps when the network has not changed. A changed network re-sweeps immediately regardless.").defineInRange("scannerRescanIntervalTicks", 200, 40, 1200);
            this.scannerLiveSyncIntervalTicks = builder.comment("Ticks between live RPM/stress updates sent to an open scanner screen").defineInRange("scannerLiveSyncIntervalTicks", 10, 1, 100);
            this.scannerOverlayMaxElements = builder.comment("Most elements the world overlay will outline at once").defineInRange("scannerOverlayMaxElements", 256, 16, 2048);
            this.scannerOverlayRange = builder.comment("Blocks from the camera the world overlay draws to").defineInRange("scannerOverlayRange", 48.0, 8.0, 192.0);
            builder.pop();
            builder.push("dieselEngines");
            this.showRawDieselMetrics = builder.comment("Show every detected Create Diesel Generators metric. Disabled keeps Diesel displays curated for normal play.").define("showRawDieselMetrics", false);
            builder.pop();
            builder.comment("Verbose diagnostic logging toggles. Only the subsystems with detailed developer trace logging are exposed here; other subsystems log at standard levels and do not need a toggle.").push("debug");
            this.fccVerboseLogging = builder.comment("Enable verbose FCC and flight-control runtime logs.").define("fccVerboseLogging", false);
            this.rpmGovernorVerboseLogging = builder.comment("Enable verbose RPM Governor runtime logs.").define("rpmGovernorVerboseLogging", false);
            this.antennaDebugLogging = builder.comment("Enable antenna placement and display-link diagnostics.").define("antennaDebugLogging", false);
            builder.pop();
            builder.push("vccMkII");
            this.vccPersistControlsOnLoad = builder.comment("If true, a VCC Mk II re-applies its saved cockpit control hold (throttle, steering, etc.) on world load while armed. If false (default), controls hold across GUI reopen but require one fresh input after a world load so the craft never moves on its own.").define("vccPersistControlsOnLoad", false);
            builder.pop();
            builder.push("inductionHeater");
            this.inductionHeaterStressImpact = builder.comment("SU drawn per RPM by an active Induction Heater (>= 64 RPM). Default 32 makes the anchor costs 2048 SU at 64 RPM, 4096 SU at 128 RPM (Kindled) and 8192 SU at 256 RPM (Superheated).").defineInRange("inductionHeaterStressImpact", 32.0, 1.0, 64.0);
            this.inductionCoilLifetimeSeconds = builder.comment("Seconds of continuous Superheated runtime one Induction Coil survives before burning out.").defineInRange("inductionCoilLifetimeSeconds", 240, 10, 3600);
            builder.pop();
            builder.push("antenna");
            this.antennaRangeMultiplierSmall = builder.comment("Direct-link range multiplier granted by a Small Signal Antenna.").defineInRange("antennaRangeMultiplierSmall", 4, 1, 64);
            this.antennaRangeMultiplierAdvanced = builder.comment("Direct-link range multiplier granted by an Advanced Signal Antenna.").defineInRange("antennaRangeMultiplierAdvanced", 12, 1, 128);
            this.antennaRangeMultiplierTrt = builder.comment("Direct-link range multiplier granted by a TRT Signal Antenna (also bridges loaded Overworld and Nether targets).").defineInRange("antennaRangeMultiplierTrt", 32, 1, 512);
            builder.pop();
            builder.comment(
                    "Storage upgrades. Three degrees of Slot Upgrade and three of Stack Upgrade, installed in a block's",
                    "Upgrades rack. Degrees multiply: a rack holding one of each is 1.5 * 2 * 4 = 12 times. The two maxima",
                    "below are the ceilings that product is clamped to, and they are set to exactly what one full set",
                    "earns, so duplicate upgrades can never exceed a set. Lowering a maximum is safe at any time: a",
                    "container never shrinks below the slots its contents already occupy, so an overfull block keeps",
                    "everything and simply refuses to give its upgrades back until it has been emptied.").push("upgrades");
            this.upgradeMultiplierFirst = builder.comment("What a first-degree storage upgrade multiplies by.").defineInRange("upgradeMultiplierFirst", 1.5, 1.0, 64.0);
            this.upgradeMultiplierSecond = builder.comment("What a second-degree storage upgrade multiplies by.").defineInRange("upgradeMultiplierSecond", 2.0, 1.0, 64.0);
            this.upgradeMultiplierThird = builder.comment("What a third-degree storage upgrade multiplies by.").defineInRange("upgradeMultiplierThird", 4.0, 1.0, 64.0);
            this.lockerMaxRows = builder.comment("Most rows of nine a Security Locker's grid can reach. Default 36 is the base three rows times a full set's 12. The grid shows six rows and scrolls past them.").defineInRange("lockerMaxRows", 36, 3, 256);
            this.lockerMaxStack = builder.comment("Most items one Security Locker slot can hold. Default 768 is a normal stack of 64 times a full set's 12. Items that do not stack at all are never deepened.").defineInRange("lockerMaxStack", 768, 64, 8192);
            builder.pop();
            builder.push("experimental");
            this.showExperimentalBlocks = builder.comment("Show experimental, in-development items (the VCC Mk II, the VCC Mk III, the Vehicle Control Unit, the Stats Display Mk2, the Holographic Projector Mk III, and the Aeronautics Hinge) in the creative menu and JEI, and enable the crafting recipes for the ones that have them. Off by default because they are unfinished and may change or break. They stay obtainable with /give regardless of this setting.").define("showExperimentalBlocks", false);
            builder.pop();
            builder.comment("Forge Energy. The mod's advanced electronics (avionics, sensors, screens, lighting, comms) each keep a small internal FE buffer that external FE networks can charge, and a block whose buffer runs dry goes inert until it is powered again. This is the normal way the mod works; it is not optional. Create kinetic power stays the mechanical side; FE only powers electronics. Simple gauges, passive readouts, and utility blocks never need FE. Changes may require a restart.").push("power");
            this.creativeEnergy = builder.comment(
                    "Creative energy. When true, every block below runs on free power: buffers read full, nothing drains,",
                    "and no block can ever cut off. Intended for creative building, testing and map making, where wiring a",
                    "grid to each screen is busywork. Generation, wiring and storage still work normally, they simply have",
                    "nothing to feed.")
                    .define("creativeEnergy", false);
            this.feShowLoginReminder = builder.comment(
                    "Show a one-line notice in chat on world join saying that the mod's electronics run on Forge Energy",
                    "and pointing at creativeEnergy. Shown only while creativeEnergy is off; set false to silence it.")
                    .define("showLoginReminder", true);
            this.feSourceLoadExponent = builder.comment(
                    "Sub-linear scaling for blocks whose load grows with how many sources are linked (currently the Stats Display).",
                    "Draw multiplier = sourceCount ^ exponent, so extra sources cost progressively less each.",
                    "1.0 is fully linear (10 sources = 10x). The 0.85 default gives 1 source 1.0x, 2 sources 1.8x, 10 sources 7.1x, 20 sources 12.8x.",
                    "Lower values flatten the curve further.")
                    .defineInRange("sourceLoadExponent", 0.85, 0.50, 1.00);
            this.feRebootThresholdPct = builder.comment(
                    "Buffer percent a cut-off block must charge back up to before it restarts.",
                    "This is hysteresis, and it matters more than it sounds: without it a supply that is even slightly",
                    "short flips a block between running and dead every few ticks, which stops and restarts flight",
                    "governors, strobes stage lights, and rebuilds holograms over and over.",
                    "A cut-off block charges like a capacitor and wakes up once, at this level.",
                    "0 restores the old edge-triggered behaviour.")
                    .defineInRange("rebootThresholdPct", 25, 0, 90);
            final java.util.Map<String, FePowerValues> profiles = new java.util.LinkedHashMap<String, FePowerValues>();

            profiles.put("fcc", new FePowerValues(builder, "fcc", "Flight Control Computer", 160000, 250, 1000, 0));
            profiles.put("vccMk2", new FePowerValues(builder, "vccMk2", "VCC Mk II", 160000, 250, 1000, 0));

            profiles.put("pathfinderComputer", new FePowerValues(builder, "pathfinderComputer", "Pathfinder Computer", 96000, 150, 600, 0));
            profiles.put("pathScanner", new FePowerValues(builder, "pathScanner", "Path Scanner (burst = one scan region batch)", 96000, 150, 600, 8000));
            profiles.put("battleScreen", new FePowerValues(builder, "battleScreen", "Battle Screen Display", 96000, 150, 600, 0));
            profiles.put("holographicProjector", new FePowerValues(builder, "holographicProjector", "Holographic Projector (burst = one nearby-block scan)", 96000, 150, 600, 4000));
            profiles.put("factoryLogicController", new FePowerValues(builder, "factoryLogicController", "Factory Logic Controller", 96000, 150, 600, 0));
            profiles.put("productionDetector", new FePowerValues(builder, "productionDetector", "Production Detector", 96000, 150, 600, 0));

            profiles.put("statsDisplayPanel", new FePowerValues(builder, "statsDisplayPanel", "Stats Display Panel and Stats Display Mk2 (active drain is per single linked source; see sourceLoadExponent)", 16000, 25, 100, 0));

            profiles.put("statsAlarm", new FePowerValues(builder, "statsAlarm", "Stats Alarm (constant 16 FE/t draw)", 2560, 16, 16, 0));
            profiles.put("dispatchBoard", new FePowerValues(builder, "dispatchBoard", "Dispatch Board (burst = one summary refresh)", 64000, 100, 400, 3000));

            profiles.put("stageLight", new FePowerValues(builder, "stageLight", "Stage Light and Stage Light Mk2", 48000, 75, 300, 0));
            profiles.put("statusTowerLight", new FePowerValues(builder, "statusTowerLight", "Status Tower Light", 48000, 75, 300, 0));

            profiles.put("wallOutlet", new FePowerValues(builder, "wallOutlet", "Wall Outlet (a socket passes power, it does not spend any)", 1024, 0, 0, 0));

            profiles.put("cineCamera", new FePowerValues(builder, "cineCamera", "Cine Camera (a battery camera: active while film runs through it, idle otherwise. The default buffer films for an hour at the active draw)", 216000, 1, 3, 0));

            profiles.put("topologyScannerField", new FePowerValues(builder, "topologyScannerField", "Topology Scanner Probe (clamp-on, Field grade)", 16000, 10, 40, 0));
            profiles.put("topologyScannerAdvanced", new FePowerValues(builder, "topologyScannerAdvanced", "Advanced Topology Scanner Probe", 32000, 20, 80, 0));
            profiles.put("topologyScannerIndustrial", new FePowerValues(builder, "topologyScannerIndustrial", "Industrial Topology Scanner Probe", 64000, 40, 160, 0));
            this.fePowerProfiles = java.util.Collections.unmodifiableMap(profiles);
            builder.comment("Kinetic Dynamo, the mod's built-in rotation-to-FE generator. Unaffected by creativeEnergy, so packs can power other FE mods with it too.").push("generator");
            this.dynamoFePerRpm = builder.comment(
                    "FE per tick per RPM of shaft speed, for the STANDARD grade (Kinetic Dynamo).",
                    "Default 1.40625 = 360 FE/t at Create's top speed of 256 RPM, which is exactly a",
                    "Crafts and Additions alternator. The other two grades scale off this one:",
                    "Open Frame Dynamo x0.75 (270 FE/t), Industrial Alternator x1.5 (540 FE/t).")
                    .defineInRange("fePerRpm", 1.40625, 0.01, 100.0);
            this.dynamoStressImpact = builder.comment(
                    "Stress impact (SU per RPM) of the STANDARD grade while spinning.",
                    "Default 64 = 16384 SU at 256 RPM, which is exactly a Crafts and Additions",
                    "alternator, the same generator fePerRpm above is pinned to. Matching on both axes",
                    "is deliberate: matching only on output would make this quietly 5.7x cheaper to run",
                    "than the block it claims to equal.",
                    "Grades scale off this: Open Frame x0.5 (8192 SU), Industrial x1.2 (19661 SU).",
                    "Because the cheap grade sheds half its stress for only a quarter of its output it",
                    "is the efficient one: 0.033 FE per SU against the standard's 0.022.")
                    .defineInRange("stressImpact", 64.0, 0.0, 256.0);
            this.dynamoBufferCapacity = builder.comment("Internal FE buffer of the dynamo.").defineInRange("bufferCapacity", 64000, 100, 10000000);
            this.dynamoTransferRate = builder.comment("Maximum FE per tick the dynamo pushes to each adjacent block or hands to an extract request.").defineInRange("transferRate", 4096, 16, 1000000);
            this.solarFePerTick = builder.comment(
                    "FE per tick one REINFORCED Solar Panel makes at noon, in the clear, under open",
                    "sky, on its own. Default 32, and it is set by a relationship rather than chosen:",
                    "nine panels earn a x1.25 array bonus, so a 3x3 Reinforced roof is exactly 360 FE/t,",
                    "which is a Kinetic Dynamo at Create's top speed. Move this and that equivalence",
                    "moves with it.",
                    "The other two grades scale off this one: Solar Panel x0.6, Photovoltaic Array x1.6.",
                    "Everything else is a multiplier on top: the sun's height through the day (a sine, so",
                    "the daily mean is about a third of the peak), rain x0.35, thunder x0.15, sky light",
                    "above the panel, and the array bonus itself, which caps at x1.5 at "
                            + com.createstats.power.SolarCurve.ARRAY_CAP_PANELS + " panels.")
                    .defineInRange("solarFePerTick", 32, 1, 1000000);
            builder.pop();
            builder.comment("Power Connectors and Copper Wire, the mod's built-in point-to-point FE wiring. Unaffected by creativeEnergy.").push("wiring");
            this.connectorTransferRate = builder.comment(
                    "The one base rate everything electrical in this mod is a multiple of: cable, wire,",
                    "connector tiers, the Capacitor Bank and the PDU all scale off this single number, so",
                    "retuning it retunes the whole grid consistently rather than leaving some part of it",
                    "pinned at a number the rest has moved away from. Per tier multipliers live on",
                    "CableTier, WireTier and ConnectorTier rather than here, so they stay a fixed ratio",
                    "of this base instead of drifting independently of it.")
                    .defineInRange("transferRate", 8192, 16, 1000000);
            this.connectorBufferCapacity = builder.comment("Internal FE buffer of each connector node.").defineInRange("bufferCapacity", 16000, 64, 1000000);
            this.cableThroughputLimit = builder.comment(
                    "Hold a circuit to the grade of cable it is built from. With this on, a run carries what its",
                    "thinnest segment carries no matter how many generators, batteries or machines are bolted along",
                    "it, which is what the cable's own goggle readout has always claimed. Off restores the older",
                    "behaviour, where every rate was a per-block one and they simply added up: three generators on",
                    "one Surface Cable delivered three times that cable's rating. Wire and connectors are unaffected",
                    "either way; a connector is already rated by the spans strung to it, and separate spans really",
                    "are parallel capacity.")
                    .define("cableThroughputLimit", true);
            this.connectorWireRange = builder.comment("Maximum copper wire length in blocks between two connectors.").defineInRange("wireRange", 32, 4, 128);
            builder.pop();
            builder.comment("Power Limiter, the inline throttle. It is a boundary rather than a length of wire: the circuit on its terminal and the circuit on its gland are two separate grids, and nothing crosses between them faster than the setting allows.").push("limiter");
            this.limiterMaxRate = builder.comment(
                    "Ceiling on what one Power Limiter can be set to pass. The block's dial is a fixed ladder of",
                    "round figures from 0 to 1,048,576 FE/t, and this clamps the figure that ladder resolves to, so a",
                    "pack can cap every limiter in the world without the control changing shape under the player.",
                    "The default is the top of the ladder, which is far above anything this mod generates: a limiter",
                    "at full is a limiter that is in place for its redstone control rather than for its cap.")
                    .defineInRange("maxRate", 1048576, 0, 1000000000);
            builder.pop();
            builder.comment("Main Breaker, the rotary disconnect. Its two studs are two separate circuits and the block is the only way across, so throwing the handle really does leave the protected side with no source in it. What it passes is the lesser of the two studs' wire spans summed, so the rating is the copper actually bolted on rather than a number in here.").push("mainBreaker");
            this.mainBreakerTripSeconds = builder.comment(
                    "Seconds of sustained overload before a Main Breaker's thermal element lets go. The",
                    "element heats while the rating, rather than the demand, is what decides how much crosses,",
                    "and cools at the same rate while it is not: so anything under half duty cycle above the",
                    "rating never trips however long it runs, and a solid overload trips after this long. A",
                    "starved breaker is never counted as an overloaded one -- there has to be energy in the",
                    "buffer that the rating held back. Set to 0 to disable tripping entirely, which leaves the",
                    "breaker a pure disconnect that silently caps an undersized service. The Creative twin",
                    "never trips whatever this says.")
                    .defineInRange("tripSeconds", 5, 0, 600);
            builder.pop();
            builder.comment("Capacitor Bank, the mod's built-in FE storage. Banks merge into a single structure that fills from the bottom up, so a stack reads as one gauge. Unaffected by creativeEnergy.").push("storage");
            this.capacitorBankCapacity = builder.comment(
                    "Base FE per Capacitor Bank block, before the merge bonus below.",
                    "Default 1,024,000. A single block holds just over a million; a full 3x3x6 bank holds about 82 million.")
                    .defineInRange("bufferCapacity", 1024000, 64, 100000000);
            this.capacitorBankExponent = builder.comment(
                    "Merge bonus. Capacity = bufferCapacity x blockCount ^ exponent, so a merged bank holds more than the sum of its parts.",
                    "The default 1.1 is a deliberately gentle curve: 2 blocks give 2.14x, 4 give 4.59x, 9 give 11.2x, and a full 3x3x6 gives 80.5x.",
                    "1.0 makes merging purely linear with no bonus at all.")
                    .defineInRange("mergeExponent", 1.1, 1.0, 1.5);
            this.capacitorBankMaxFootprint = builder.comment("Maximum width and depth in blocks of one merged bank. Default 3 gives a 3x3 footprint.").defineInRange("maxFootprint", 3, 1, 16);
            this.capacitorBankMaxHeight = builder.comment("Maximum height in blocks of one merged bank. Blocks beyond this start a second bank.").defineInRange("maxHeight", 6, 1, 32);
            this.capacitorBankTransferRate = builder.comment("Maximum FE per tick in or out of a merged bank. Default is 4x the base connector rate, flat regardless of merge size.").defineInRange("transferRate", 32768, 16, 10000000);
            this.reserveCascadeEnabled = builder.comment(
                    "Reserve cascade. With this on, several banks on one grid drain in a defined order instead of an arbitrary one:",
                    "the outermost rank empties first and rank 1, the core, empties last and refills first. Set a bank's rank with a wrench.",
                    "This is the horizontal version of what a merged bank tower already does vertically, and it is what lets a set of banks",
                    "read as staged tanks emptying one at a time rather than all sagging together.",
                    "Off restores the old behaviour, where storage was walked in whatever order the wire network discovered it.")
                    .define("reserveCascade", true);
            builder.pop();
            builder.comment(
                    "FE Battery Controller, the block that replaces wiring capacitor banks to each other in a chain.",
                    "Three inputs take generation and storage, three outputs feed circuits, and every bank on the input side is",
                    "managed as one pooled reserve with a per-output state-of-charge floor. Nothing here is a battery: the",
                    "controller's own buffer is a working bus, and the reserve is whatever banks the player wired to it.")
                    .push("batteryController");
            // renamed instead of re-defaulted: every install already had 32768 on disk. new key or nobody gets it. donkemonke tax

            this.batteryControllerPortRate = builder.comment(
                    "Maximum FE per tick through one terminal, before the wire strung to it is taken into account.",
                    "Default is 8x the base connector rate, one HV Line, so the wire grade is what actually decides a",
                    "circuit's ceiling: a terminal strung in HV Line carries the HV Line's whole rating.")
                    .defineInRange("terminalRate", 65536, 16, 10000000);
            this.batteryControllerBuffer = builder.comment(
                    "The controller's own working buffer. Deliberately small: this is a switchboard, not a battery, and a large",
                    "buffer here would hide the state of the reserve the whole block exists to manage. Never less than one",
                    "tick of its three inputs at terminalRate, or the inputs could not carry their rating together.")
                    .defineInRange("bufferCapacity", 64000, 1000, 10000000);
            builder.comment(
                    "The reserve band. Instead of one floor, the controller steers the pooled banks' state of charge INTO a band,",
                    "which is what stops the two opposite failures a reserve policy can have. Above the ceiling it releases charge",
                    "(so a full bank farm is actually spent during a lull rather than hoarded while machines sit shed); inside the",
                    "band it holds and lives on generation; below the floor only CRITICAL load draws, metered so the dregs last.",
                    "A long outage therefore parks the base inside the band with its important machines still running.",
                    "Each edge is minutes of the CRITICAL load the controller feeds (the *Minutes values), never more than its",
                    "percent (the *Pct values), so the band follows what the base needs and not how many banks were built.",
                    "These are what a controller starts from: on its screen a player sets that controller's own minutes, caps,",
                    "or plain percents, and Defaults goes back to these.")
                    .push("band");
            this.batteryControllerBandFloorPct = builder.comment(
                    "The most of the pooled banks' capacity the floor may be, in percent. Below the floor only CRITICAL",
                    "load may draw. The floor itself is floorMinutes of the CRITICAL load, so a big bank farm keeps what its",
                    "CRITICAL machines need rather than a tenth of everything built.")
                    .defineInRange("floorPct", 10, 0, 95);
            this.batteryControllerBandCeilingPct = builder.comment(
                    "The most the band's top may be, in percent. Between the floor and the top the controller holds the",
                    "reserve and nothing shed comes back on. The top itself is ceilingMinutes of the CRITICAL load.",
                    "Clamped to at least floorPct.")
                    .defineInRange("ceilingPct", 30, 0, 98);
            this.batteryControllerBandHealthyPct = builder.comment(
                    "The most the healthy mark may be, in percent. At or above it the reserve counts as deep and is spent",
                    "with no rationing at all. The mark itself is healthyMinutes of the CRITICAL load, and never so low that",
                    "the controller's full rate could empty the banks above the floor in under one rideTicks.",
                    "Clamped to at least ceilingPct.")
                    .defineInRange("healthyPct", 60, 0, 100);
            this.batteryControllerBandFloorMinutes = builder.comment(
                    "Minutes of the CRITICAL load the floor holds, capped at floorPct. 0 means no floor at all.",
                    "Set it very high to judge the band by plain percents again.")
                    .defineInRange("floorMinutes", 10, 0, 100000);
            this.batteryControllerBandCeilingMinutes = builder.comment(
                    "Minutes of the CRITICAL load the band's top holds, capped at ceilingPct. Clamped to at least",
                    "floorMinutes.")
                    .defineInRange("ceilingMinutes", 30, 0, 100000);
            this.batteryControllerBandHealthyMinutes = builder.comment(
                    "Minutes of the CRITICAL load at which the reserve counts as deep, capped at healthyPct. Clamped to at",
                    "least ceilingMinutes.")
                    .defineInRange("healthyMinutes", 60, 0, 100000);
            this.batteryControllerRideTicks = builder.comment(
                    "The droop's time constant, in ticks (20 = 1 second). The controller offers the charge above the floor",
                    "spread over this, so the allowance is proportional to what is left and falls smoothly to zero AT the floor.",
                    "It is not a deadline: because the rate shrinks as the reserve drains, the charge decays toward the floor",
                    "exponentially with this as its time constant. Lower spends the reserve harder and rides shorter lulls;",
                    "higher is stingier and lasts longer. This value is what makes the system stable, so changing it by an order",
                    "of magnitude will change how readily the base sheds far more than the band percentages do.")
                    .defineInRange("rideTicks", 1200, 20, 72000);
            this.batteryControllerChargeClaimPct = builder.comment(
                    "The most of live generation the controller may hold back to refill the banks, as a percent. The claim",
                    "ramps up as the reserve falls: nothing at healthyPct, this much at floorPct and below, and a smooth",
                    "line between them so there is no threshold for the base to oscillate around. It is only ever a slice of",
                    "generation, never of stored charge, and it is bounded so that CRITICAL circuits are never funded from.",
                    "Without it a base whose generation roughly equals its demand can never climb out of a low reserve: the",
                    "machines take every FE before one reaches a bank. Set to 0 to switch the behaviour off entirely.")
                    .defineInRange("chargeClaimPct", 60, 0, 100);
            builder.pop();
            builder.pop();
            builder.comment(
                    "Portable Power Interface, Create's portable interface for FE. A pair docks a contraption and moves",
                    "charge between the ground block's circuit and the Capacitor Banks the contraption carries.")
                    .push("portableInterface");
            this.portableInterfaceMaxRate = builder.comment(
                    "The fastest a player may set the dial, in FE per tick. The dial runs from 256 up to the largest step of",
                    "the Power Limiter's ladder that does not exceed this. Default is 4x the base connector rate, Shielded Wire.")
                    .defineInRange("maxRate", com.createstats.power.PortableFeLadder.DEFAULT_CEILING_FE, 256, 1048576);
            this.portableInterfaceFloorPct = builder.comment(
                    "Where a newly placed interface sets its source floor: it never drains the side power comes from below",
                    "this percentage. In Charge mode that is the base circuit's banks, in Draw mode the train's. Rounded to tens.")
                    .defineInRange("defaultFloorPct", com.createstats.power.PortableFeTransfer.DEFAULT_FLOOR_PCT, 0, 90);
            builder.pop();
            builder.comment("FE Fluid Pump, the powered pipe that pushes fluid down its run in place of a Mechanical Pump. MK I matches a Create pump at 128 RPM (up to 64 mB/t), MK II at 256 RPM (up to 128 mB/t). Unaffected by creativeEnergy.").push("pump");
            this.pumpFeCostPerMb = builder.comment("Forge Energy burned per millibucket moved. At the default 4.5, MK I costs up to 288 FE/t at full flow and MK II up to 576 FE/t, matching the Electric Drive FE-for-FE at both grades. Zero makes pumping free.").defineInRange("feCostPerMb", 4.5, 0.0, 100000.0);
            builder.pop();
            builder.comment("Power Distribution Unit, the panel that serves its branch by priority tier and sheds what it cannot reach. A Power Connector mounted on the output stud (the right-hand side face) roots the managed branch; FE is taken in through either side boss. Each boss is rated independently at the Industrial connector's own grade (PowerDistributionUnitBE.inputRatePerBoss, not configurable here), so the panel's real input ceiling is that rate times two.").push("pdu");
            this.pduBufferCapacity = builder.comment("Internal FE buffer, the shared reserve for the whole managed branch.").defineInRange("bufferCapacity", 512000, 1000, 100000000);
            this.pduIdleDrain = builder.comment("Running overhead in FE per second, waived under creative energy. Zero makes the panel free to run.").defineInRange("idleDrain", 1024, 0, 1000000);
            builder.comment("Active Auto tuning. These shape how eagerly the panel sheds and restores; the defaults are deliberately cautious, in the spirit of a real load management controller.").push("auto");
            this.pduReserveFloorPct = builder.comment("Percent of the buffer held back as the Critical ride-through. Below this only Critical tier devices may draw. Raising it protects Critical for longer at the cost of shedding everything else sooner.").defineInRange("reserveFloorPct", 15, 0, 90);
            this.pduRestoreSocPct = builder.comment("Buffer percent that counts as spare supply, one of the two ways a shed device becomes eligible to come back (the other is an upstream capacitor bank actively charging).").defineInRange("restoreSocPct", 45, 0, 100);
            this.pduBurstMarginPct = builder.comment("Percent of headroom a shed device must clear on top of measured generation before it is restored, so the branch never comes back up at exactly 100 percent of supply. This is the operating reserve.").defineInRange("burstMarginPct", 10, 0, 50);
            this.pduDwellTicks = builder.comment("Minimum ticks between two run-state changes of the same device (20 = 1 second). This is the anti-flap minimum switching time; a reserve floor trip is exempt from it.").defineInRange("dwellTicks", 60, 0, 6000);
            this.pduRestoreIntervalTicks = builder.comment("Minimum ticks between restoring any two devices, so a recovering branch picks its load back up in stages rather than all at once.").defineInRange("restoreIntervalTicks", 20, 0, 6000);
            this.pduRideHorizonTicks = builder.comment("Ticks over which the reserve above the floor is treated as spendable. Larger means the panel spends the buffer more slowly and therefore admits less on top of real generation.").defineInRange("rideHorizonTicks", 600, 20, 72000);
            this.pduSupplyReadIntervalTicks = builder.comment("Ticks between reads of the upstream grid (summing dynamo capacity and checking capacitor bank charge). Lower reacts faster to a generation change and costs more; the decision itself still runs every tick.").defineInRange("supplyReadIntervalTicks", 10, 1, 200);
            this.pduObservedDecayPct = builder.comment("Per-tick decay, in percent, of the observed-supply peak used for third-party generators the panel cannot introspect (Create Crafts and Additions, Powah). 98 means it falls to half in about 1.7 seconds.").defineInRange("observedDecayPct", 98, 50, 100);
            this.pduFillTicks = builder.comment(
                    "Ticks a served device's own buffer is topped up over, when the panel has energy spare above its reserve floor.",
                    "The panel serves in two passes: every admitted device first gets exactly what it costs to RUN, then whatever is",
                    "left over is spent filling buffers at this pace. A device with an empty buffer will accept its entire transfer",
                    "rate, which is why it needs its own pass: without one, the first cold machine in the list drinks the pool and the",
                    "other thirteen the panel had budgeted for get nothing while the roll call reports them all as served.",
                    "Lower fills faster and makes a cold start lumpier; higher is gentler and takes longer to bank anything.")
                    .defineInRange("fillTicks", 40, 1, 1200);
            this.pduLearnTicks = builder.comment(
                    "Ticks of evidence before the panel trusts what it has WATCHED a third-party machine draw over what its buffer",
                    "headroom suggests. Our own blocks publish what they cost; anything from another mod does not, and headroom is a",
                    "poor substitute (an idle machine looks free, a cold one looks like an emergency). The panel measures the real",
                    "figure from the outside, by differencing each device's buffer against what it was handed, and switches to that",
                    "once it has this many ticks of it. Zero-ish reacts instantly and is noisier; higher is steadier and slower.")
                    .defineInRange("learnTicks", 10, 1, 600);
            this.pduExportPolicy = builder.comment(
                    "Hold the back boss to what the panel can spare. The back boss gives power out to anything willing to pull on it",
                    "(a Battery Controller charging off the run, a foreign conduit, a downstream board), and with this off the only",
                    "limits are the leg's breaker and its rating, so a battery farm behind the panel can take the buffer to zero and",
                    "shed every machine in front of it. On, the export follows three bands: nothing below the reserve floor, live",
                    "surplus generation only between the floor and the restore mark, and freely above it. Turn off for the old",
                    "behaviour; the export is counted as demand either way.")
                    .define("exportPolicy", true);
            this.pduCoreProtection = builder.comment(
                    "Core protection. Once the upstream capacitor banks are nearly spent, only Critical devices may draw on what is left:",
                    "everything else falls back to what generation alone sustains, and nothing below Critical is restored until the",
                    "banks recover. This is the reserve floor idea applied to the banks feeding the panel rather than to its own buffer.",
                    "Turning it off does not hide the cascade, the panel still reads and reports it, it just stops holding anything back.")
                    .define("coreProtection", true);
            this.pduCoreProtectionPct = builder.comment(
                    "Percent of the upstream banks' total capacity below which core protection engages.",
                    "Measured as charge remaining against capacity across every bank on the feed, deliberately NOT as a count of banks",
                    "still holding: a bank that was never filled is not a bank that drained, and since storage only ever charges from a",
                    "generator, an empty second bank on a generator-less grid stays empty. Counting banks would put an ordinary",
                    "one-full-one-empty build into protection permanently with tens of millions of FE sitting next door.")
                    .defineInRange("coreProtectionPct", 20, 0, 90);
            builder.pop();
            builder.pop();
        }
    }

    public static final class FePowerValues
    {
        public final ModConfigSpec.IntValue bufferCapacity;
        public final ModConfigSpec.IntValue idleDrain;
        public final ModConfigSpec.IntValue activeDrain;
        public final ModConfigSpec.IntValue burstCost;

        FePowerValues(ModConfigSpec.Builder builder, String key, String label, int defaultCapacity, int defaultIdle, int defaultActive, int defaultBurst) {
            builder.comment(label).push(key);
            this.bufferCapacity = builder.comment("Internal FE buffer size.").defineInRange("bufferCapacity", defaultCapacity, 100, 100000000);
            this.idleDrain = builder.comment("FE drained per tick while placed but not doing active work.").defineInRange("idleDrain", defaultIdle, 0, 100000);
            this.activeDrain = builder.comment("FE drained per tick while actively working.").defineInRange("activeDrain", defaultActive, 0, 100000);
            this.burstCost = builder.comment("Extra FE paid per discrete heavy operation. 0 = this block has no burst operations.").defineInRange("burstCost", defaultBurst, 0, 1000000);
            builder.pop();
        }
    }

    public static class Client
    {
        public final ModConfigSpec.DoubleValue flywheelSoundVolume;
        public final ModConfigSpec.DoubleValue paVoiceVolume;
        public final ModConfigSpec.BooleanValue signalTraceGlow;
        public final ModConfigSpec.IntValue maxRenderDistance;
        public final ModConfigSpec.BooleanValue weatherFahrenheit;
        public final ModConfigSpec.BooleanValue pduReduceMotion;
        public final ModConfigSpec.ConfigValue<String> panelSimplify;
        public final ModConfigSpec.DoubleValue panelDetailRangeTrim;
        public final ModConfigSpec.BooleanValue panelCrossFade;
        public final ModConfigSpec.BooleanValue panelKeepHeader;
        public final ModConfigSpec.BooleanValue dynamicLights;
        public final ModConfigSpec.BooleanValue dynamicLightsHeldItems;
        public final ModConfigSpec.BooleanValue dynamicLightsDroppedItems;
        public final ModConfigSpec.IntValue dynamicLightsMaxDistance;
        public final ModConfigSpec.IntValue dynamicLightsMaxCells;
        public final ModConfigSpec.IntValue dynamicLightsMaxChangesPerTick;
        public final ModConfigSpec.BooleanValue dynamicLightsDrawnBeams;
        public final ModConfigSpec.BooleanValue pathwaysAlwaysShown;
        public final ModConfigSpec.BooleanValue pathwaysWithGoggles;

        Client(ModConfigSpec.Builder builder) {
            builder.push("pathways");
            this.pathwaysAlwaysShown = builder.comment(
                    "Show every pathway's ribbon and stops all the time, not only with the Surveyor's Staff in hand or goggles on,",
                    "for players who like their routes always visible. Client-only.")
                    .define("alwaysShown", false);
            this.pathwaysWithGoggles = builder.comment("Show pathways' ribbons and stops, dimmer, while wearing Engineer's Goggles. Client-only.")
                    .define("withGoggles", true);
            builder.pop();
            builder.push("sound");
            this.flywheelSoundVolume = builder.comment("Volume of the Kinetic Flywheel's hum, charged chime and trip thunk (0.0 = silent, 1.0 = default, 2.0 = twice the default). The hum only plays while a flywheel is actually charging or carrying load, and a whole bank shares one hum.").defineInRange("flywheelSoundVolume", 1.0, 0.0, 2.0);
            this.paVoiceVolume = builder.comment("Volume of Announcement Computer speech (0.0 = silent, 1.0 = full). Client-only, so each player sets their own.").defineInRange("paVoiceVolume", 1.0, 0.0, 1.0);
            builder.pop();
            builder.push("graphics");
            this.signalTraceGlow = builder.comment("Draw the soft phosphor glow around Stats Display line-chart traces. Turn off for crisp, flat lines.").define("signalTraceGlow", true);
            this.maxRenderDistance = builder.comment("Global ceiling (blocks) on Create Stats display render distance. Each display has its own tuned distance (32-96); this only ever caps them lower, never higher. 128 = no cap. Client-only, so each player controls their own in multiplayer.").defineInRange("maxRenderDistance", 128, 16, 128);
            this.weatherFahrenheit = builder.comment(
                    "Show Weather Panel temperatures in Fahrenheit instead of Celsius. There is a chip",
                    "in the panel's own screen that sets this, so the file is only the escape hatch.",
                    "Client-only on purpose: a unit is a property of whoever is reading, not of the",
                    "panel, so this follows you onto somebody else's wall rather than overriding them.")
                    .define("weatherFahrenheit", false);
            builder.pop();
            builder.comment(
                    "How a Stats Display Mk III face behaves as you walk away from it. Every setting here has a",
                    "control in the display's own Screen tab, which also shows the resulting distances in metres for",
                    "the panel you are looking at; this file is the escape hatch for tuning them exactly.",
                    "",
                    "The ranges are size aware. A single block panel holds full detail to about 18 metres and",
                    "collapses to a status glyph past about 48; a larger wall holds both proportionally further,",
                    "up to four times as far, because a bigger face carries bigger type and a bigger readout.")
                    .push("panelDetail");
            this.panelSimplify = builder.comment(
                    "How eagerly the face gives up detail with distance.",
                    "NEVER      = no simplification at all, at any range the panel renders at.",
                    "FAR        = roughly double the normal ranges.",
                    "BALANCED   = the default. Full detail across a room, simplified across the factory.",
                    "AGGRESSIVE = simplifies early. The cheapest option on a large wall of panels.")
                    .define("simplify", "BALANCED");
            this.panelDetailRangeTrim = builder.comment(
                    "Extra multiplier applied on top of the mode above, for tuning the exact distance.",
                    "1.0 leaves the mode as it is; 2.0 doubles every range; 0.5 halves them.")
                    .defineInRange("detailRangeTrim", 1.0, 0.25, 4.0);
            this.panelCrossFade = builder.comment(
                    "Blend between detail levels instead of switching between one frame and the next.",
                    "Off restores a hard cut at each threshold, which is very slightly cheaper and, for some",
                    "players, easier to read because the face is never in an in-between state.")
                    .define("crossFade", true);
            this.panelKeepHeader = builder.comment(
                    "Keep the title and status band at any distance, even once the face has collapsed to a glyph.",
                    "Costs two short strings and is the difference between a distant panel that still says which",
                    "machine it is watching and one that is an anonymous coloured rectangle.")
                    .define("keepHeader", true);
            builder.pop();
            builder.push("accessibility");
            this.pduReduceMotion = builder.comment("Skip the Power Distribution Unit screen's animated transitions; every gauge and bar snaps straight to its value instead of gliding. Client-only.").define("pduReduceMotion", false);
            builder.pop();
            builder.comment(
                    "Create Stats' own dynamic lights: light that moves (a Stage Light's pool, a torch in your hand),",
                    "drawn by the game's own light engine on your client, so it works with Sodium and Iris shaderpacks",
                    "and stops at walls. No other mod is needed. Press F3 to see what it is doing.")
                    .push("dynamicLights");
            this.dynamicLights = builder.comment(
                    "Master switch. Off puts every dynamic light out, Stage Light pools included; a world-placed",
                    "Stage Light still lights the spot it points at from the server.")
                    .define("enabled", true);
            this.dynamicLightsHeldItems = builder.comment(
                    "A light-giving block held in either hand (a torch, a lantern, glowstone) lights the world round",
                    "whoever holds it, you, other players and mobs alike.")
                    .define("heldItems", true);
            this.dynamicLightsDroppedItems = builder.comment(
                    "The same for light-giving blocks lying on the ground as items.")
                    .define("droppedItems", true);
            this.dynamicLightsMaxDistance = builder.comment(
                    "Lights further than this many blocks from the camera are not drawn.")
                    .defineInRange("maxDistance", 64, 16, 256);
            this.dynamicLightsMaxCells = builder.comment(
                    "The most light cells alive at once; past it the ones nearest the camera win. A held light is one",
                    "cell, a Stage Light up to nine.")
                    .defineInRange("maxCells", 256, 16, 2048);
            this.dynamicLightsMaxChangesPerTick = builder.comment(
                    "The most light cells changed per tick. Each change relights up to 15 blocks round it and rebuilds",
                    "the chunk sections that touches, so this caps the cost of many moving or strobing lights. Changes",
                    "that do not fit wait for the next tick, nearest the camera first.")
                    .defineInRange("maxChangesPerTick", 64, 4, 1024);
            this.dynamicLightsDrawnBeams = builder.comment(
                    "Draw a vehicle headlight's beam on the road as light: a crisp cone with a dipped beam's cut-off",
                    "that glides with the car, over a dimmer block light underlay that still lights whoever stands in",
                    "it. Off, the beam is block light alone at full strength (a few cells along the road), which is",
                    "also what you get with an Iris shaderpack on, since a shaderpack owns the lighting.")
                    .define("drawnBeams", true);
            builder.pop();
        }
    }
}
