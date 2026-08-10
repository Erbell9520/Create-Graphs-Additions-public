package com.createstats.client;

import com.createstats.block.CapacitorBankBlock;
import com.createstats.block.PowerConnectorBlock;
import com.createstats.block.StageLightBlock;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** [1] */
public class PowerScenes {

    /** Wipes everything the host schematic left above the base plate so a scene starts clean. */
    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos,
                            net.minecraft.world.level.block.state.BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    /** A connector mounted on the block in the given direction, facing away from it. */
    private static net.minecraft.world.level.block.state.BlockState connector(Direction facing) {
        return ModBlocks.POWER_CONNECTOR.getDefaultState().setValue(PowerConnectorBlock.FACING, facing);
    }

    /** Trunk-grade cable lying on the floor, which is what a run into a panel should look like. */
    private static net.minecraft.world.level.block.state.BlockState busDuct() {
        return ModBlocks.BUS_DUCT.getDefaultState()
                .setValue(com.createstats.block.CableBlock.faceProperty(Direction.DOWN), true);
    }

    /** [2] */
    private static void leg(CreateSceneBuilder scene, SceneBuildingUtil util,
                            BlockPos pdu, BlockPos terminal, PonderPalette colour, int duration) {
        scene.overlay().showBigLine(colour,
                util.vector().topOf(pdu), util.vector().topOf(terminal), duration);
    }

    // ==================================================================================
    // 1. Generation and wiring
    // ==================================================================================

    public static void powerBasics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_power_basics", "Forge Energy: generating and wiring");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.68f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        BlockPos shaft = util.grid().at(2, 1, 4);
        BlockPos dynamo = util.grid().at(2, 2, 4);
        BlockPos dynConn = util.grid().at(2, 3, 4);
        BlockPos machine = util.grid().at(6, 2, 4);
        BlockPos machConn = util.grid().at(6, 3, 4);

        CreateStatsPonderScenes.showBase(scene, util);

        put(scene, shaft, AllBlocks.SHAFT.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                        Direction.Axis.Y));
        put(scene, dynamo, ModBlocks.KINETIC_DYNAMO.getDefaultState());
        PonderFx.reveal(scene, util, Direction.DOWN, 5, shaft, dynamo);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(dynamo, Direction.WEST), Pointing.RIGHT, 60)
                .withItem(ModBlocks.KINETIC_DYNAMO.asStack());
        PonderFx.beat(scene, util,
                "The Kinetic Dynamo turns rotation into Forge Energy. Drive its bottom shaft and it generates energy per tick in proportion to speed",
                dynamo, PonderPalette.BLUE, 115);

        // Spin it up rather than snapping to speed, so "in proportion to speed" is something seen.
        final Selection drive = CreateStatsPonderScenes.select(util, shaft, dynamo);
        for (int rpm : new int[]{32, 96, 176, 256}) {
            scene.world().setKineticSpeed(drive, rpm);
            scene.idle(6);
        }
        scene.effects().rotationSpeedIndicator(dynamo);
        scene.idle(12);
        PonderFx.aside(scene, util,
                "Faster rotation makes more power, and costs more stress. It works whether or not FE Dependent Mode is on, so it can feed other FE mods too",
                dynamo, PonderPalette.INPUT, 115);

        PonderFx.look(scene, machine);
        put(scene, machine, ModBlocks.STATS_DISPLAY_PANEL.getDefaultState());
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(12);

        put(scene, dynConn, connector(Direction.UP));
        put(scene, machConn, connector(Direction.UP));
        PonderFx.reveal(scene, util, Direction.DOWN, 5, dynConn, machConn);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(dynConn, Direction.UP), Pointing.DOWN, 50)
                .withItem(ModBlocks.POWER_CONNECTOR.asStack());
        PonderFx.beat(scene, util,
                "Mount a Power Connector on anything that makes or uses power. It reads whatever block it is attached to",
                dynConn, PonderPalette.OUTPUT, 110);

        scene.overlay().showControls(util.vector().blockSurface(dynConn, Direction.UP), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(ModItems.COPPER_WIRE.asStack());
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.OUTPUT,
                util.vector().topOf(dynConn), util.vector().topOf(machConn), 130);
        PonderFx.beat(scene, util,
                "Right-click one connector with Copper Wire, then a second, to span a cable between them",
                dynConn, PonderPalette.INPUT, 110);

        // Current arriving at the far end, which is the claim the next line makes.
        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.GREEN, current,
                List.of(dynamo, dynConn, machConn, machine), 4);
        scene.idle(20);
        PonderFx.beatOutlined(scene, util,
                "Every connected connector forms one grid, solved as a whole each tick, so power reaches the far end immediately no matter how long the run is",
                CreateStatsPonderScenes.select(util, dynConn, machConn), machConn, PonderPalette.GREEN, 130);

        PonderFx.beat(scene, util,
                "Machines are always served before storage, and when supply runs short the grid splits it fairly rather than letting the first machine drink it all",
                machine, PonderPalette.WHITE, 120);
        scene.effects().indicateSuccess(machine);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 1b. The three generator grades
    // ==================================================================================

    /** [3] */
    public static void dynamoGrades(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_dynamo_grades", "Generators: three grades, three trades");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos openShaft = util.grid().at(2, 1, 4);
        BlockPos open = util.grid().at(2, 2, 4);
        BlockPos stdShaft = util.grid().at(4, 1, 4);
        BlockPos std = util.grid().at(4, 2, 4);
        BlockPos indShaft = util.grid().at(6, 1, 4);
        BlockPos ind = util.grid().at(6, 2, 4);

        for (BlockPos s : new BlockPos[]{openShaft, stdShaft, indShaft}) {
            put(scene, s, AllBlocks.SHAFT.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                            Direction.Axis.Y));
        }
        put(scene, open, ModBlocks.OPEN_FRAME_DYNAMO.getDefaultState());
        put(scene, std, ModBlocks.KINETIC_DYNAMO.getDefaultState());
        put(scene, ind, ModBlocks.INDUSTRIAL_ALTERNATOR.getDefaultState());

        final Selection all = CreateStatsPonderScenes.select(
                util, openShaft, open, stdShaft, std, indShaft, ind);
        // The baseline first and alone, because it is the block the other two are described against.
        PonderFx.reveal(scene, util, Direction.DOWN, 4, stdShaft, std);
        scene.idle(8);
        for (int rpm : new int[]{64, 160, 256}) {
            scene.world().setKineticSpeed(CreateStatsPonderScenes.select(util, stdShaft, std), rpm);
            scene.idle(6);
        }
        scene.effects().rotationSpeedIndicator(std);
        scene.idle(10);
        PonderFx.beatOutlined(scene, util,
                "The Kinetic Dynamo is the baseline every figure is quoted against: 360 energy a tick at Create's top speed",
                util.select().position(std), std, PonderPalette.BLUE, 125);
        PonderFx.aside(scene, util,
                "That is deliberately equal to a Crafts and Additions alternator, on stress as well as on output, so the number means something",
                std, PonderPalette.WHITE, 120);

        PonderFx.reveal(scene, util, Direction.DOWN, 4, indShaft, ind);
        scene.world().setKineticSpeed(CreateStatsPonderScenes.select(util, indShaft, ind), 256);
        scene.effects().rotationSpeedIndicator(ind);
        scene.idle(12);
        PonderFx.beatOutlined(scene, util,
                "The Industrial Alternator makes half again as much, 540 a tick, and eats more stress to do it",
                util.select().position(ind), ind, PonderPalette.OUTPUT, 115);

        PonderFx.reveal(scene, util, Direction.DOWN, 4, openShaft, open);
        scene.world().setKineticSpeed(CreateStatsPonderScenes.select(util, openShaft, open), 256);
        scene.effects().rotationSpeedIndicator(open);
        scene.idle(12);
        PonderFx.beatOutlined(scene, util,
                "The Open Frame Dynamo makes the least, 270 a tick, but takes only half the stress of the baseline",
                util.select().position(open), open, PonderPalette.GREEN, 120);

        // Output as pulses per grade: three, four, six, in proportion to what each actually makes.
        // Side by side and simultaneous, because the comparison is the entire point of the chapter.
        for (int i = 0; i < 3; i++) {
            PonderFx.energyPulse(scene, PonderPalette.GREEN, new Object(), List.of(open, open.above()), 6);
            PonderFx.energyPulse(scene, PonderPalette.BLUE, new Object(), List.of(std, std.above()), 6);
            PonderFx.energyPulse(scene, PonderPalette.OUTPUT, new Object(), List.of(ind, ind.above()), 6);
            scene.idle(4);
        }
        PonderFx.energyPulse(scene, PonderPalette.BLUE, new Object(), List.of(std, std.above()), 6);
        for (int i = 0; i < 3; i++) {
            PonderFx.energyPulse(scene, PonderPalette.OUTPUT, new Object(), List.of(ind, ind.above()), 6);
            scene.idle(3);
        }
        scene.idle(20);

        PonderFx.beat(scene, util,
                "So these are not a ladder. The axis that matters is energy per unit of stress, and the cheapest grade wins it outright",
                open, PonderPalette.MEDIUM, 130);
        PonderFx.beat(scene, util,
                "Rotation to spare and no room to build? Take the Industrial. Windmills already groaning? A row of Open Frames gets more out of the same stress",
                ind, PonderPalette.INPUT, 140);

        // Halve the speed everywhere: output follows, which is the last rule worth showing.
        scene.world().multiplyKineticSpeed(all, 0.5f);
        scene.effects().rotationSpeedIndicator(std);
        scene.idle(14);
        PonderFx.beat(scene, util,
                "All three scale with speed, so a dynamo spun at half its rated RPM makes half the power. The base level is tunable in the [power.generator] config",
                std, PonderPalette.WHITE, 140);
        scene.world().multiplyKineticSpeed(all, 2f);
        scene.effects().indicateSuccess(std);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. Capacitor Bank
    // ==================================================================================

    public static void capacitorBank(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_capacitor_bank", "Capacitor Bank: pooled storage");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        BlockPos b1 = util.grid().at(4, 1, 4);
        BlockPos b2 = util.grid().at(4, 2, 4);
        BlockPos b3 = util.grid().at(4, 3, 4);
        BlockPos b4 = util.grid().at(4, 4, 4);

        CreateStatsPonderScenes.showBase(scene, util);

        put(scene, b1, ModBlocks.CAPACITOR_BANK.getDefaultState());
        scene.world().showSection(util.select().position(b1), Direction.DOWN);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(b1, Direction.WEST), Pointing.RIGHT, 60)
                .withItem(ModBlocks.CAPACITOR_BANK.asStack());
        PonderFx.beat(scene, util,
                "A Capacitor Bank stores Forge Energy and shows its charge on the outside",
                b1, PonderPalette.BLUE, 100);

        for (BlockPos pos : new BlockPos[]{b2, b3, b4}) {
            put(scene, pos, ModBlocks.CAPACITOR_BANK.getDefaultState());
            scene.world().showSection(util.select().position(pos), Direction.DOWN);
            scene.idle(6);
        }
        scene.idle(8);
        PonderFx.beatOutlined(scene, util,
                "Touching banks merge into one battery, so a stack needs a single connector rather than one per block",
                CreateStatsPonderScenes.select(util, b1, b2, b3, b4), b3, PonderPalette.GREEN, 120);
        PonderFx.aside(scene, util,
                "Merging pays a bonus: the pool holds more than the blocks would separately, so building bigger is worth it",
                b3, PonderPalette.OUTPUT, 110);

        // [4]
        PonderFx.beat(scene, util,
                "It fills from the bottom block upward and drains from the top down, so the stack reads as one big gauge",
                b2, PonderPalette.INPUT, 115);
        for (BlockPos pos : new BlockPos[]{b1, b2, b3, b4}) {
            PonderFx.sweepGauge(scene, pos, CapacitorBankBlock.LEVEL, 0, CapacitorBankBE.LED_SEGMENTS, 22);
            scene.idle(24);
        }
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Lit blocks are charge and dark blocks are headroom, readable across a base without opening anything",
                b2, PonderPalette.WHITE, 110);

        // And back down from the top, which is the half players get wrong.
        for (BlockPos pos : new BlockPos[]{b4, b3}) {
            PonderFx.sweepGauge(scene, pos, CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS, 0, 20);
            scene.idle(22);
        }
        scene.idle(10);
        scene.effects().indicateSuccess(b1);
        scene.idle(12);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 3. FE Dependent Mode
    // ==================================================================================

    public static void feDependentMode(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_dependent_mode", "FE Dependent Mode: brownout and cutoff");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.68f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        BlockPos light = util.grid().at(4, 2, 4);
        BlockPos lightConn = util.grid().at(4, 3, 4);
        BlockPos bank = util.grid().at(6, 1, 4);
        BlockPos bankConn = util.grid().at(6, 2, 4);

        CreateStatsPonderScenes.showBase(scene, util);

        put(scene, light, ModBlocks.STAGE_LIGHT.getDefaultState()
                .setValue(StageLightBlock.LIT, true));
        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, lightConn, connector(Direction.UP));
        put(scene, bankConn, connector(Direction.UP));
        PonderFx.reveal(scene, util, Direction.DOWN, 5, light, lightConn, bank, bankConn);
        scene.idle(10);

        PonderFx.beat(scene, util,
                "FE Dependent Mode is optional and off by default. Switch it on and the mod's electronics each need power to run",
                light, PonderPalette.BLUE, 120);
        PonderFx.aside(scene, util,
                "Every powered block keeps a small internal buffer that any grid can charge. Simple gauges and kinetic blocks never need it",
                light, PonderPalette.INPUT, 120);

        // [5]
        PonderFx.look(scene, bank);
        PonderFx.beat(scene, util,
                "Cut the supply and the buffer drains. Below a fifth the block browns out and its screens start to flicker",
                bank, PonderPalette.OUTPUT, 120);
        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS, 0, 80);
        PonderFx.brownout(scene, light, StageLightBlock.LIT, 1.0f, 0.0f, 80);
        scene.idle(84);
        scene.effects().indicateRedstone(light);
        scene.idle(8);
        PonderFx.beat(scene, util,
                "When the buffer empties the block goes inert. It stops working but keeps its settings",
                light, PonderPalette.RED, 120);

        // The reboot: charge visibly before it comes back, not the instant power returns.
        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, 0, 3, 30);
        scene.idle(34);
        PonderFx.beat(scene, util,
                "Power it again and it charges like a capacitor before restarting, so a marginal supply cannot make it flicker on and off",
                bank, PonderPalette.GREEN, 130);
        scene.world().modifyBlock(light, s -> s.setValue(StageLightBlock.LIT, true), true);
        scene.effects().indicateSuccess(light);
        scene.idle(14);
        PonderFx.beat(scene, util,
                "Every buffer, drain and the restart threshold are tunable in the [power] config section",
                light, PonderPalette.WHITE, 110);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 4. Power Distribution Unit
    // ==================================================================================

    /** [6] */
    public static void powerDistributionUnit(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_power_distribution_unit", "Power Distribution Unit: load shedding");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.58f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        final BlockPos shaft = util.grid().at(1, 1, 4);
        final BlockPos dynamo = util.grid().at(1, 2, 4);
        final BlockPos trunkA = util.grid().at(2, 1, 4);
        final BlockPos trunkB = util.grid().at(3, 1, 4);
        final BlockPos pdu = util.grid().at(4, 1, 4);

        // Four loads, ranked, each standing on the floor with its own terminal on top.
        final BlockPos[] load = {
                util.grid().at(7, 1, 1), util.grid().at(7, 1, 3),
                util.grid().at(7, 1, 5), util.grid().at(7, 1, 7)
        };
        final BlockPos[] term = {load[0].above(), load[1].above(), load[2].above(), load[3].above()};

        put(scene, pdu, ModBlocks.POWER_DISTRIBUTION_UNIT.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        scene.world().showSection(util.select().position(pdu), Direction.DOWN);
        scene.idle(14);
        scene.overlay().showControls(util.vector().blockSurface(pdu, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.POWER_DISTRIBUTION_UNIT.asStack());
        PonderFx.beat(scene, util,
                "Without a Power Distribution Unit a short grid splits power evenly, so every machine browns out together and you lose all of them",
                pdu, PonderPalette.RED, 130);

        // The supply, spun up, so the whole scene has a visible cause.
        PonderFx.look(scene, dynamo);
        put(scene, shaft, AllBlocks.SHAFT.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                        Direction.Axis.Y));
        put(scene, dynamo, ModBlocks.KINETIC_DYNAMO.getDefaultState());
        put(scene, trunkA, busDuct());
        put(scene, trunkB, busDuct());
        PonderFx.reveal(scene, util, Direction.DOWN, 4, shaft, dynamo, trunkA, trunkB);
        final Selection supply = CreateStatsPonderScenes.select(util, shaft, dynamo);
        for (int rpm : new int[]{64, 160, 256}) {
            scene.world().setKineticSpeed(supply, rpm);
            scene.idle(5);
        }
        scene.effects().rotationSpeedIndicator(dynamo);
        final Object trunkFlow = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, trunkFlow,
                List.of(dynamo, trunkA, trunkB, pdu), 4);
        scene.idle(24);
        PonderFx.beat(scene, util,
                "Power goes in through either side boss. Everything the panel can hand out comes from what your generators are actually making",
                pdu, PonderPalette.INPUT, 130);

        // The four legs, revealed in priority order, each wired from a stud on top of the panel.
        final String[] tier = {"Critical", "High", "Medium", "Optional"};
        final PonderPalette[] tierColour = {
                PonderPalette.GREEN, PonderPalette.BLUE, PonderPalette.MEDIUM, PonderPalette.OUTPUT
        };
        put(scene, load[0], ModBlocks.FLIGHT_CONTROL_COMPUTER.getDefaultState());
        put(scene, load[1], ModBlocks.STATS_DISPLAY_PANEL.getDefaultState());
        put(scene, load[2], ModBlocks.FLUID_PUMP_MK1.getDefaultState()
                .setValue(com.createstats.block.FluidPumpBlock.FACING, Direction.EAST));
        put(scene, load[3], ModBlocks.STAGE_LIGHT.getDefaultState()
                .setValue(StageLightBlock.FACING, Direction.UP).setValue(StageLightBlock.LIT, true));
        for (int i = 0; i < 4; i++) {
            put(scene, term[i], connector(Direction.UP));
            scene.world().showSection(
                    CreateStatsPonderScenes.select(util, load[i], term[i]), Direction.DOWN);
            scene.idle(6);
        }
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(pdu), Pointing.DOWN, 50)
                .rightClick()
                .withItem(ModItems.COPPER_WIRE.asStack());
        scene.idle(10);
        for (int i = 0; i < 4; i++) {
            leg(scene, util, pdu, term[i], PonderPalette.OUTPUT, 150);
            scene.idle(5);
        }
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Run a Copper Wire from one of the eight studs on top to a machine's connector, and that machine is on that leg",
                pdu, PonderPalette.INPUT, 130);

        // Name the tiers by ringing each machine in its own colour, in order.
        scene.overlay().showControls(util.vector().blockSurface(pdu, Direction.NORTH), Pointing.DOWN, 50).rightClick();
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Right-click the panel and sort your machines into five tiers, from Critical down to Optional",
                pdu, PonderPalette.BLUE, 120);
        for (int i = 0; i < 4; i++) {
            scene.overlay().showOutline(tierColour[i], tier[i], util.select().position(load[i]), 130);
            scene.idle(8);
        }
        scene.idle(10);
        PonderFx.aside(scene, util,
                "The flight computer is Critical, the readouts are High, the pump is Medium, and the decorative light is Optional",
                load[1], PonderPalette.WHITE, 130);

        // Everything served: full budget, every leg pulsing.
        final Vec3 meter = util.vector().centerOf(pdu).add(0, 1.9, 0);
        final Object[] flow = {new Object(), new Object(), new Object(), new Object()};
        PonderFx.budgetMeter(scene, meter, 8, 8f, 8f, PonderPalette.GREEN, 90);
        for (int i = 0; i < 4; i++) {
            PonderFx.energyPulse(scene, PonderPalette.GREEN, flow[i], List.of(pdu, term[i], load[i]), 5);
        }
        scene.idle(24);
        PonderFx.beat(scene, util,
                "While generation covers the whole demand the panel does nothing at all. Every leg is served and the bar above it stays full",
                pdu, PonderPalette.GREEN, 135);

        // Generation collapses. The bar sags in step with the dynamo, and the legs die bottom-up.
        PonderFx.look(scene, dynamo);
        scene.world().setKineticSpeed(supply, 96);
        scene.effects().rotationSpeedIndicator(dynamo);
        PonderFx.budgetMeter(scene, meter, 8, 8f, 5f, PonderPalette.GREEN, 40);
        scene.idle(20);
        PonderFx.brownout(scene, load[3], StageLightBlock.LIT, 0.18f, 0f, 26);
        leg(scene, util, pdu, term[3], PonderPalette.RED, 130);
        scene.idle(30);
        scene.overlay().showOutline(PonderPalette.RED, "shed3", util.select().position(load[3]), 120);
        PonderFx.beat(scene, util,
                "Slow the generators and the budget shrinks. The panel drops the bottom tier first: the lights go, and nothing else notices",
                load[3], PonderPalette.RED, 140);

        scene.world().setKineticSpeed(supply, 48);
        scene.effects().rotationSpeedIndicator(dynamo);
        PonderFx.budgetMeter(scene, meter, 8, 5f, 3f, PonderPalette.GREEN, 34);
        leg(scene, util, pdu, term[2], PonderPalette.RED, 110);
        scene.idle(36);
        scene.overlay().showOutline(PonderPalette.RED, "shed2", util.select().position(load[2]), 110);
        PonderFx.beat(scene, util,
                "Starve it further and Medium goes too. Each tier coasts on its own reserve on the way down, so shedding is soft rather than a blackout",
                load[2], PonderPalette.OUTPUT, 140);

        // Critical is still fed, which is the whole promise of the block.
        PonderFx.look(scene, load[0]);
        PonderFx.budgetMeter(scene, meter, 8, 3f, 3f, PonderPalette.GREEN, 60);
        PonderFx.energyPulse(scene, PonderPalette.GREEN, flow[0], List.of(pdu, term[0], load[0]), 5);
        PonderFx.energyPulse(scene, PonderPalette.GREEN, flow[1], List.of(pdu, term[1], load[1]), 5);
        scene.idle(24);
        PonderFx.beatOutlined(scene, util,
                "What is left goes upward. Critical is still running on a grid that can no longer light a lamp",
                util.select().position(load[0]), load[0], PonderPalette.GREEN, 130);
        scene.effects().indicateSuccess(load[0]);

        // Recovery, in priority order, which is the reassuring half.
        PonderFx.look(scene, dynamo);
        for (int rpm : new int[]{128, 200, 256}) {
            scene.world().setKineticSpeed(supply, rpm);
            scene.idle(6);
        }
        scene.effects().rotationSpeedIndicator(dynamo);
        PonderFx.budgetMeter(scene, meter, 8, 3f, 8f, PonderPalette.GREEN, 50);
        leg(scene, util, pdu, term[2], PonderPalette.OUTPUT, 120);
        scene.idle(18);
        leg(scene, util, pdu, term[3], PonderPalette.OUTPUT, 100);
        scene.world().modifyBlock(load[3], s -> s.setValue(StageLightBlock.LIT, true), true);
        scene.idle(24);
        PonderFx.beat(scene, util,
                "Bring the generators back and the tiers return in the order they matter, so the room recovers instead of stampeding",
                pdu, PonderPalette.INPUT, 135);
        PonderFx.beat(scene, util,
                "You keep your flight computer and lose the decorative lights, instead of losing everything at once",
                pdu, PonderPalette.WHITE, 125);
        scene.effects().indicateSuccess(pdu);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 5. Power Distribution Unit: Active Auto
    // ==================================================================================

    /** [7] */
    public static void powerDistributionUnitAuto(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_pdu_active_auto", "Power Distribution Unit: Active Auto");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.58f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        final BlockPos shaft = util.grid().at(1, 1, 4);
        final BlockPos dynamo = util.grid().at(1, 2, 4);
        final BlockPos trunkA = util.grid().at(2, 1, 4);
        final BlockPos trunkB = util.grid().at(3, 1, 4);
        final BlockPos pdu = util.grid().at(4, 1, 4);
        final BlockPos bank = util.grid().at(4, 1, 6);
        final BlockPos[] load = {
                util.grid().at(7, 1, 2), util.grid().at(7, 1, 4), util.grid().at(7, 1, 6)
        };
        final BlockPos[] term = {load[0].above(), load[1].above(), load[2].above()};

        put(scene, shaft, AllBlocks.SHAFT.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                        Direction.Axis.Y));
        put(scene, dynamo, ModBlocks.KINETIC_DYNAMO.getDefaultState());
        put(scene, trunkA, busDuct());
        put(scene, trunkB, busDuct());
        put(scene, pdu, ModBlocks.POWER_DISTRIBUTION_UNIT.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, load[0], ModBlocks.FLIGHT_CONTROL_COMPUTER.getDefaultState());
        put(scene, load[1], ModBlocks.FLUID_PUMP_MK1.getDefaultState()
                .setValue(com.createstats.block.FluidPumpBlock.FACING, Direction.EAST));
        put(scene, load[2], ModBlocks.STAGE_LIGHT.getDefaultState()
                .setValue(StageLightBlock.FACING, Direction.UP).setValue(StageLightBlock.LIT, true));
        for (int i = 0; i < 3; i++) {
            put(scene, term[i], connector(Direction.UP));
        }
        PonderFx.reveal(scene, util, Direction.DOWN, 4, shaft, dynamo, trunkA, trunkB, pdu, bank);
        for (int i = 0; i < 3; i++) {
            scene.world().showSection(
                    CreateStatsPonderScenes.select(util, load[i], term[i]), Direction.DOWN);
            scene.idle(5);
        }
        final Selection supply = CreateStatsPonderScenes.select(util, shaft, dynamo);
        scene.world().setKineticSpeed(supply, 256);
        for (int i = 0; i < 3; i++) {
            leg(scene, util, pdu, term[i], PonderPalette.OUTPUT, 250);
        }
        scene.idle(14);

        scene.overlay().showControls(util.vector().blockSurface(pdu, Direction.NORTH), Pointing.DOWN, 50).rightClick();
        PonderFx.beat(scene, util,
                "The panel has three modes. Off shares power evenly, Manual honours your tiers, and Active Auto manages the branch for you",
                pdu, PonderPalette.BLUE, 130);

        // Run one: Manual. The buffer is the sensor, so nothing happens until it is nearly gone.
        final Vec3 bufferBar = util.vector().centerOf(bank).add(0, 1.7, 0);
        final Object[] flow = {new Object(), new Object(), new Object()};
        PonderFx.look(scene, bank);
        PonderFx.beat(scene, util,
                "In Manual the reserve is what tells the panel anything is wrong, and a reserve only reports a shortfall once it is already draining",
                bank, PonderPalette.RED, 140);
        scene.world().setKineticSpeed(supply, 48);
        scene.effects().rotationSpeedIndicator(dynamo);
        PonderFx.budgetMeter(scene, bufferBar, 8, 8f, 0f, PonderPalette.OUTPUT, 70);
        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS, 0, 70);
        scene.idle(74);
        PonderFx.brownout(scene, load[2], StageLightBlock.LIT, 0.15f, 0f, 24);
        for (int i = 0; i < 3; i++) {
            leg(scene, util, pdu, term[i], PonderPalette.RED, 110);
        }
        scene.idle(28);
        PonderFx.beat(scene, util,
                "So the whole branch coasts on stored energy, and by the time the panel reacts the reserve you were saving for an outage is spent",
                bank, PonderPalette.RED, 145);

        // Run two: Active Auto. Generation is the sensor, so it acts on the way down.
        PonderFx.look(scene, dynamo);
        scene.world().setKineticSpeed(supply, 256);
        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, 0, CapacitorBankBE.LED_SEGMENTS, 30);
        for (int i = 0; i < 3; i++) {
            leg(scene, util, pdu, term[i], PonderPalette.OUTPUT, 200);
        }
        scene.world().modifyBlock(load[2], s -> s.setValue(StageLightBlock.LIT, true), true);
        scene.idle(36);
        scene.overlay().showControls(util.vector().blockSurface(pdu, Direction.WEST), Pointing.LEFT, 55)
                .withItem(ModBlocks.KINETIC_DYNAMO.asStack());
        PonderFx.beat(scene, util,
                "Active Auto measures what your dynamos can actually generate, rather than waiting for the buffer to run down",
                dynamo, PonderPalette.INPUT, 130);

        final Vec3 genBar = util.vector().centerOf(pdu).add(0, 1.9, 0);
        scene.world().setKineticSpeed(supply, 48);
        scene.effects().rotationSpeedIndicator(dynamo);
        PonderFx.budgetMeter(scene, genBar, 8, 8f, 3f, PonderPalette.GREEN, 30);
        scene.idle(14);
        PonderFx.brownout(scene, load[2], StageLightBlock.LIT, 0.15f, 0f, 20);
        leg(scene, util, pdu, term[2], PonderPalette.RED, 150);
        scene.idle(24);
        // The reserve never moves. That contrast against run one is the entire chapter.
        scene.overlay().showOutline(PonderPalette.GREEN, "reserve", util.select().position(bank), 140);
        PonderFx.beatOutlined(scene, util,
                "It sheds on the way down instead of after the fall, so the reserve is never touched at all",
                util.select().position(bank), bank, PonderPalette.GREEN, 135);
        PonderFx.beat(scene, util,
                "The buffer stops being the sensor and becomes what it should be: a reserve, held back so Critical keeps running through an outage",
                load[0], PonderPalette.GREEN, 140);
        scene.effects().indicateSuccess(load[0]);

        PonderFx.look(scene, load[1]);
        PonderFx.budgetMeter(scene, genBar, 8, 3f, 3f, PonderPalette.GREEN, 60);
        PonderFx.energyPulse(scene, PonderPalette.GREEN, flow[0], List.of(pdu, term[0], load[0]), 5);
        scene.idle(20);
        PonderFx.beat(scene, util,
                "Within a tier it sheds the hungriest machine first, so the most of your build keeps running for the power you have",
                load[1], PonderPalette.OUTPUT, 135);

        scene.overlay().showControls(util.vector().blockSurface(pdu, Direction.NORTH), Pointing.DOWN, 40).rightClick();
        PonderFx.beat(scene, util,
                "Right-click a machine in the list to lock it on so it is never shed, or off so it never runs. A lock overrules the panel entirely",
                pdu, PonderPalette.MEDIUM, 140);
        scene.effects().indicateSuccess(pdu);
        scene.idle(15);
        scene.markAsFinished();
    }
}
