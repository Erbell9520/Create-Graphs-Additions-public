package com.createstats;

import org.apache.commons.lang3.tuple.Pair;
import java.util.function.Function;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;

public class ModConfiguration
{
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    public static int getEffectiveStatsLinkRefreshTicks() {
        return Math.max(1, Math.min((int)COMMON.statsLinkPassiveRefreshTicks.get(), (int)COMMON.responsiveStatsLinkRefreshTicks.get()));
    }

    /** [1] */
    public static int cappedViewDistance(int ownDefault) {
        try {
            return Math.min(ownDefault, CLIENT.maxRenderDistance.get());
        } catch (RuntimeException ignored) {
            return ownDefault;
        }
    }
    
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfiguration.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ModConfiguration.CLIENT_SPEC);
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
        public final ModConfigSpec.BooleanValue feDependentMode;
        public final ModConfigSpec.BooleanValue feShowLoginReminder;
        public final ModConfigSpec.DoubleValue feSourceLoadExponent;
        public final ModConfigSpec.IntValue feRebootThresholdPct;
        public final java.util.Map<String, FePowerValues> fePowerProfiles;
        public final ModConfigSpec.DoubleValue dynamoFePerRpm;
        public final ModConfigSpec.DoubleValue dynamoStressImpact;
        public final ModConfigSpec.IntValue dynamoBufferCapacity;
        public final ModConfigSpec.IntValue dynamoTransferRate;
        public final ModConfigSpec.IntValue connectorTransferRate;
        public final ModConfigSpec.IntValue connectorAdvancedMultiplier;
        public final ModConfigSpec.IntValue connectorBufferCapacity;
        public final ModConfigSpec.IntValue connectorWireRange;
        public final ModConfigSpec.IntValue connectorMaxLinks;
        public final ModConfigSpec.IntValue capacitorBankCapacity;
        public final ModConfigSpec.DoubleValue capacitorBankExponent;
        public final ModConfigSpec.IntValue capacitorBankMaxFootprint;
        public final ModConfigSpec.IntValue capacitorBankMaxHeight;
        public final ModConfigSpec.IntValue capacitorBankTransferRate;
        public final ModConfigSpec.IntValue pduBufferCapacity;
        public final ModConfigSpec.IntValue pduTransferRate;
        public final ModConfigSpec.IntValue pduIdleDrain;
        public final ModConfigSpec.IntValue pduReserveFloorPct;
        public final ModConfigSpec.IntValue pduRestoreSocPct;
        public final ModConfigSpec.IntValue pduBurstMarginPct;
        public final ModConfigSpec.IntValue pduDwellTicks;
        public final ModConfigSpec.IntValue pduRestoreIntervalTicks;
        public final ModConfigSpec.IntValue pduRideHorizonTicks;
        public final ModConfigSpec.IntValue pduSupplyReadIntervalTicks;
        public final ModConfigSpec.IntValue pduObservedDecayPct;
        public final ModConfigSpec.IntValue pumpFeCostPerMb;

        Common(ModConfigSpec.Builder builder) {
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
            // Folded in from the former single-value [trainMap] section: it is the same kind of
            // force-directed layout-iteration knob, so it lives with the other layout settings.
            this.trainMapLayoutIterations = builder.comment("Force-directed layout iterations for the full train map screen (higher = better layout, more CPU)").defineInRange("trainMapLayoutIterations", 180, 30, 500);
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
            builder.push("experimental");
            this.showExperimentalBlocks = builder.comment("Show experimental, in-development items (the VCC Mk II, the Stats Display Mk2, and the Aeronautics Hinge) in the creative menu and JEI. Off by default because they are unfinished and may change or break. They stay obtainable with /give regardless of this setting.").define("showExperimentalBlocks", false);
            builder.pop();
            builder.comment("FE Dependent Mode. Off by default: no block requires Forge Energy and nothing changes. When enabled, the mod's advanced electronics (avionics, sensors, screens, lighting, comms) each keep a small internal FE buffer that external FE networks can charge, and a block whose buffer runs dry goes inert until it is powered again. Create kinetic power stays the mechanical side; FE only powers electronics. Simple gauges, passive readouts, and utility blocks never need FE. Changes may require a restart.").push("power");
            this.feDependentMode = builder.comment("Master toggle. When true, the blocks tuned below require Forge Energy to operate.").define("feDependentMode", false);
            this.feShowLoginReminder = builder.comment("Show a one-line reminder in chat on world join while FE Dependent Mode is enabled.").define("showLoginReminder", true);
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
            // [2]
            profiles.put("fcc", new FePowerValues(builder, "fcc", "Flight Control Computer", 160000, 250, 1000, 0));
            profiles.put("vccMk2", new FePowerValues(builder, "vccMk2", "VCC Mk II", 160000, 250, 1000, 0));
            // Tier B, sensors and compute.
            profiles.put("pathfinderComputer", new FePowerValues(builder, "pathfinderComputer", "Pathfinder Computer", 96000, 150, 600, 0));
            profiles.put("pathScanner", new FePowerValues(builder, "pathScanner", "Path Scanner (burst = one scan region batch)", 96000, 150, 600, 8000));
            profiles.put("battleScreen", new FePowerValues(builder, "battleScreen", "Battle Screen Display", 96000, 150, 600, 0));
            profiles.put("damageObserver", new FePowerValues(builder, "damageObserver", "Damage Observer (burst = one observed-volume sweep)", 96000, 150, 600, 8000));
            profiles.put("holographicProjector", new FePowerValues(builder, "holographicProjector", "Holographic Projector (burst = one nearby-block scan)", 96000, 150, 600, 4000));
            profiles.put("factoryLogicController", new FePowerValues(builder, "factoryLogicController", "Factory Logic Controller", 96000, 150, 600, 0));
            profiles.put("productionDetector", new FePowerValues(builder, "productionDetector", "Production Detector", 96000, 150, 600, 0));
            // [3]
            profiles.put("statsDisplayPanel", new FePowerValues(builder, "statsDisplayPanel", "Stats Display Panel and Stats Display Mk2 (active drain is per single linked source; see sourceLoadExponent)", 16000, 25, 100, 0));
            profiles.put("statsAlarm", new FePowerValues(builder, "statsAlarm", "Stats Alarm (burst = one alert network scan)", 64000, 100, 400, 3000));
            profiles.put("dispatchBoard", new FePowerValues(builder, "dispatchBoard", "Dispatch Board (burst = one summary refresh)", 64000, 100, 400, 3000));
            // Tier D, lighting.
            profiles.put("stageLight", new FePowerValues(builder, "stageLight", "Stage Light and Stage Light Mk2", 48000, 75, 300, 0));
            profiles.put("statusTowerLight", new FePowerValues(builder, "statusTowerLight", "Status Tower Light", 48000, 75, 300, 0));
            this.fePowerProfiles = java.util.Collections.unmodifiableMap(profiles);
            builder.comment("Kinetic Dynamo, the mod's built-in rotation-to-FE generator. Always functional regardless of feDependentMode, so packs can power other FE mods with it too.").push("generator");
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
            builder.pop();
            builder.comment("Power Connectors and Copper Wire, the mod's built-in point-to-point FE wiring. Always functional regardless of feDependentMode.").push("wiring");
            this.connectorTransferRate = builder.comment(
                    "Maximum FE per tick moved per connector per hop (attached block pull, wire equalization, attached block push).",
                    "Default 8192 matches the Power Distribution Unit and the Capacitor Bank, deliberately.",
                    "A connector is both a leaf (feeding one machine, which needs at most 1000 FE/t) and a trunk",
                    "(the connector on a PDU's output stud, which carries that panel's entire branch). Sizing it for",
                    "the leaf case starved the trunk: the panel's own 8192 rate became unreachable, and a branch of",
                    "only five or six machines hit the wire limit rather than any real shortage of generation.")
                    .defineInRange("transferRate", 8192, 16, 1000000);
            this.connectorAdvancedMultiplier = builder.comment(
                    "Throughput multiplier of the Advanced Power Connector over the plain one.",
                    "A hop only moves as much as its weakest part, so a connector runs at its own grade capped by",
                    "the best wire strung to it: an advanced connector on plain copper still moves the base rate.",
                    "Insulated Wire is 2x and Shielded Wire is 3x, so 3 here means a fully upgraded hop is 3x.")
                    .defineInRange("advancedMultiplier", 3, 1, 64);
            this.connectorBufferCapacity = builder.comment("Internal FE buffer of each connector node.").defineInRange("bufferCapacity", 16000, 64, 1000000);
            this.connectorWireRange = builder.comment("Maximum copper wire length in blocks between two connectors.").defineInRange("wireRange", 32, 4, 128);
            this.connectorMaxLinks = builder.comment("Maximum wires attached to one connector.").defineInRange("maxLinks", 4, 1, 8);
            builder.pop();
            builder.comment("Capacitor Bank, the mod's built-in FE storage. Banks merge into a single structure that fills from the bottom up, so a stack reads as one gauge. Always functional regardless of feDependentMode.").push("storage");
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
            this.capacitorBankTransferRate = builder.comment("Maximum FE per tick in or out of a merged bank.").defineInRange("transferRate", 8192, 16, 10000000);
            builder.pop();
            builder.comment("FE Fluid Pump, the powered pipe that pushes fluid down its run in place of a Mechanical Pump. MK I matches a Create pump at 128 RPM (up to 64 mB/t), MK II at 256 RPM (up to 128 mB/t). Always functional regardless of feDependentMode.").push("pump");
            this.pumpFeCostPerMb = builder.comment("Forge Energy burned per millibucket moved. At the default 8, MK I costs up to 512 FE/t at full flow and MK II up to 1024 FE/t. Zero makes pumping free.").defineInRange("feCostPerMb", 8, 0, 100000);
            builder.pop();
            builder.comment("Power Distribution Unit, the panel that serves its branch by priority tier and sheds what it cannot reach. A Power Connector mounted on the output stud (the right-hand side face) roots the managed branch; FE is taken in through any other face.").push("pdu");
            this.pduBufferCapacity = builder.comment("Internal FE buffer, the shared reserve for the whole managed branch.").defineInRange("bufferCapacity", 512000, 1000, 100000000);
            this.pduTransferRate = builder.comment("Maximum FE per tick the PDU takes in, and hands to its branch.").defineInRange("transferRate", 8192, 16, 10000000);
            this.pduIdleDrain = builder.comment("Running overhead in FE per second while FE Dependent Mode is on. Zero makes the panel free to run.").defineInRange("idleDrain", 1024, 0, 1000000);
            builder.comment("Active Auto tuning. These shape how eagerly the panel sheds and restores; the defaults are deliberately cautious, in the spirit of a real load management controller.").push("auto");
            this.pduReserveFloorPct = builder.comment("Percent of the buffer held back as the Critical ride-through. Below this only Critical tier devices may draw. Raising it protects Critical for longer at the cost of shedding everything else sooner.").defineInRange("reserveFloorPct", 15, 0, 90);
            this.pduRestoreSocPct = builder.comment("Buffer percent that counts as spare supply, one of the two ways a shed device becomes eligible to come back (the other is an upstream capacitor bank actively charging).").defineInRange("restoreSocPct", 45, 0, 100);
            this.pduBurstMarginPct = builder.comment("Percent of headroom a shed device must clear on top of measured generation before it is restored, so the branch never comes back up at exactly 100 percent of supply. This is the operating reserve.").defineInRange("burstMarginPct", 10, 0, 50);
            this.pduDwellTicks = builder.comment("Minimum ticks between two run-state changes of the same device (20 = 1 second). This is the anti-flap minimum switching time; a reserve floor trip is exempt from it.").defineInRange("dwellTicks", 60, 0, 6000);
            this.pduRestoreIntervalTicks = builder.comment("Minimum ticks between restoring any two devices, so a recovering branch picks its load back up in stages rather than all at once.").defineInRange("restoreIntervalTicks", 20, 0, 6000);
            this.pduRideHorizonTicks = builder.comment("Ticks over which the reserve above the floor is treated as spendable. Larger means the panel spends the buffer more slowly and therefore admits less on top of real generation.").defineInRange("rideHorizonTicks", 600, 20, 72000);
            this.pduSupplyReadIntervalTicks = builder.comment("Ticks between reads of the upstream grid (summing dynamo capacity and checking capacitor bank charge). Lower reacts faster to a generation change and costs more; the decision itself still runs every tick.").defineInRange("supplyReadIntervalTicks", 10, 1, 200);
            this.pduObservedDecayPct = builder.comment("Per-tick decay, in percent, of the observed-supply peak used for third-party generators the panel cannot introspect (Create Crafts and Additions, Powah). 98 means it falls to half in about 1.7 seconds.").defineInRange("observedDecayPct", 98, 50, 100);
            builder.pop();
            builder.pop();
        }
    }

    /** One FE Dependent Mode tuning sub-table, {@code [power.<key>]}. All values in FE. */
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
        public final ModConfigSpec.BooleanValue signalTraceGlow;
        public final ModConfigSpec.IntValue maxRenderDistance;
        public final ModConfigSpec.BooleanValue pduReduceMotion;

        Client(ModConfigSpec.Builder builder) {
            builder.push("sound");
            this.flywheelSoundVolume = builder.comment("Volume of the Kinetic Flywheel hum loop (0.0 = silent, 1.0 = full).").defineInRange("flywheelSoundVolume", 1.0, 0.0, 1.0);
            builder.pop();
            builder.push("graphics");
            this.signalTraceGlow = builder.comment("Draw the soft phosphor glow around Stats Display line-chart traces. Turn off for crisp, flat lines.").define("signalTraceGlow", true);
            this.maxRenderDistance = builder.comment("Global ceiling (blocks) on Create Stats display render distance. Each display has its own tuned distance (32-96); this only ever caps them lower, never higher. 128 = no cap. Client-only, so each player controls their own in multiplayer.").defineInRange("maxRenderDistance", 128, 16, 128);
            builder.pop();
            builder.push("accessibility");
            this.pduReduceMotion = builder.comment("Skip the Power Distribution Unit screen's animated transitions; every gauge and bar snaps straight to its value instead of gliding. Client-only.").define("pduReduceMotion", false);
            builder.pop();
        }
    }
}
