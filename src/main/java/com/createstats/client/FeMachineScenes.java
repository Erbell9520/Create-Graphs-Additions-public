package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.block.FeMotorBlock;
import com.createstats.block.FluidPumpBlock;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/** [1] */
public class FeMachineScenes {

    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    private static BlockState shaft(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis);
    }

    /** Surface cable lying on the floor, which is how power reaches a machine standing on it. */
    private static BlockState floorCable() {
        return ModBlocks.SURFACE_CABLE.getDefaultState()
                .setValue(CableBlock.faceProperty(Direction.DOWN), true);
    }

    // ==================================================================================
    // 1. Electric Drive
    // ==================================================================================

    public static void electricDrive(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_electric_drive", "Electric Drive: energy into rotation");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos bank = util.grid().at(1, 1, 4);
        BlockPos cableA = util.grid().at(2, 1, 4);
        BlockPos motor = util.grid().at(3, 1, 4);
        BlockPos shaftA = util.grid().at(4, 1, 4);
        BlockPos shaftB = util.grid().at(5, 1, 4);
        BlockPos driven = util.grid().at(6, 1, 4);

        final Selection driveline = CreateStatsPonderScenes.select(util, motor, shaftA, shaftB, driven);

        put(scene, motor, ModBlocks.FE_MOTOR_MK1.getDefaultState()
                .setValue(FeMotorBlock.FACING, Direction.EAST));
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(14);

        scene.overlay().showControls(util.vector().blockSurface(motor, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.FE_MOTOR_MK1.asStack());
        PonderFx.beat(scene, util,
                "The Electric Drive is the exact inverse of the Kinetic Dynamo: energy in, rotation out",
                motor, PonderPalette.BLUE, 105);

        // Supply arrives, and the pulse lands on the machine itself rather than on a connector.
        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, cableA, floorCable());
        PonderFx.reveal(scene, util, Direction.DOWN, 5, bank, cableA);
        scene.idle(8);
        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, List.of(bank, cableA, motor), 5);
        scene.idle(18);
        PonderFx.beatOutlined(scene, util,
                "It is its own Power Connector. Do not mount one on top: the wire lands on the two studs of the terminal box on the machine itself",
                util.select().position(motor), motor, PonderPalette.OUTPUT, 125);

        // The driveline, then a real spin-up rather than an instant snap to speed.
        put(scene, shaftA, shaft(Direction.Axis.X));
        put(scene, shaftB, shaft(Direction.Axis.X));
        put(scene, driven, AllBlocks.FLYWHEEL.getDefaultState());
        PonderFx.reveal(scene, util, Direction.DOWN, 4, shaftA, shaftB, driven);
        scene.idle(10);
        for (int rpm : new int[]{16, 48, 96, 128}) {
            scene.world().setKineticSpeed(driveline, rpm);
            scene.idle(6);
        }
        scene.effects().rotationSpeedIndicator(motor);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "The shaft comes out of the face it points at, and the Drive is a kinetic source: it provides both a speed and a stress capacity",
                shaftA, PonderPalette.GREEN, 120);

        PonderFx.look(scene, motor);
        PonderFx.beat(scene, util,
                "MK I sources 128 RPM with 4,096 stress units behind it, for 512 energy a tick",
                motor, PonderPalette.INPUT, 110);

        // Step up to the MK II in place so the two grades are one comparison, not two scenes.
        scene.world().setBlock(motor, ModBlocks.FE_MOTOR_MK2.getDefaultState()
                .setValue(FeMotorBlock.FACING, Direction.EAST), true);
        scene.idle(8);
        for (int rpm : new int[]{160, 200, 256}) {
            scene.world().setKineticSpeed(driveline, rpm);
            scene.idle(5);
        }
        scene.effects().rotationSpeedIndicator(motor);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "MK II sources 256 RPM, Create's top speed, with 16,384 units for 1,024 a tick. Both cost the same four energy per RPM",
                motor, PonderPalette.INPUT, 125);
        PonderFx.aside(scene, util,
                "So the bill scales with the speed you asked for and nothing else. You are never punished for building the bigger grade",
                motor, PonderPalette.MEDIUM, 115);

        // The obvious exploit, refused up front.
        PonderFx.beatOutlined(scene, util,
                "Feeding a Drive's shaft into a dynamo always loses energy. It moves power across the kinetic world; it never mints it",
                driveline, motor, PonderPalette.RED, 125);

        // Redstone cut-out, shown as a spin-down rather than an instant stop.
        BlockPos lever = util.grid().at(3, 2, 4);
        put(scene, lever, Blocks.REDSTONE_BLOCK.defaultBlockState());
        scene.world().showSection(util.select().position(lever), Direction.DOWN);
        scene.effects().indicateRedstone(motor);
        scene.idle(6);
        for (int rpm : new int[]{160, 96, 32, 0}) {
            scene.world().setKineticSpeed(driveline, rpm);
            scene.idle(5);
        }
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Redstone is a cut-out, as it is everywhere else in the power system, and a Distribution Unit can shed a Drive like any other load",
                motor, PonderPalette.WHITE, 125);

        scene.world().setBlock(lever, Blocks.AIR.defaultBlockState(), true);
        scene.idle(6);
        for (int rpm : new int[]{64, 160, 256}) {
            scene.world().setKineticSpeed(driveline, rpm);
            scene.idle(5);
        }
        ElementLink<ParrotElement> birb = scene.special().createBirb(
                util.vector().topOf(driven).add(0, 0.2, 0), ParrotPose.FacePointOfInterestPose::new);
        scene.special().changeBirbPose(birb, ParrotPose.DancePose::new);
        scene.effects().indicateSuccess(motor);
        scene.idle(20);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. FE Fluid Pump
    // ==================================================================================

    public static void fluidPump(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fe_fluid_pump", "FE Fluid Pump: pressure you dial");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos source = util.grid().at(2, 1, 4);
        BlockPos pump = util.grid().at(3, 1, 4);
        BlockPos meter = util.grid().at(4, 1, 4);
        BlockPos sink = util.grid().at(5, 1, 4);
        BlockPos bank = util.grid().at(3, 1, 2);
        BlockPos cable = util.grid().at(3, 1, 3);

        put(scene, source, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        put(scene, sink, AllBlocks.FLUID_TANK.getDefaultState());
        put(scene, pump, ModBlocks.FLUID_PUMP_MK1.getDefaultState()
                .setValue(FluidPumpBlock.FACING, Direction.EAST));
        PonderFx.reveal(scene, util, Direction.DOWN, 5, source, pump, sink);
        scene.idle(12);

        scene.overlay().showControls(util.vector().blockSurface(pump, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.FLUID_PUMP_MK1.asStack());
        PonderFx.beat(scene, util,
                "The FE Fluid Pump is a pipe segment that drives fluid along its run, pulling in through the back and pushing out of the front",
                pump, PonderPalette.BLUE, 120);

        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, cable, floorCable());
        PonderFx.reveal(scene, util, Direction.DOWN, 5, bank, cable);
        scene.idle(8);
        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, List.of(bank, cable, pump), 5);
        scene.idle(18);
        PonderFx.beatOutlined(scene, util,
                "Like the Electric Drive it is its own Power Connector: wire straight to the studs on its terminal box, with no connector block on top",
                util.select().position(pump), pump, PonderPalette.OUTPUT, 130);

        // The dial, using the affordance Create uses for every scroll value in the game.
        PonderFx.look(scene, pump);
        scene.overlay().showCenteredScrollInput(pump, Direction.UP, 70);
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Scroll the power dial to choose the flow you want in millibuckets a tick. It costs energy per millibucket actually moved",
                pump, PonderPalette.INPUT, 125);
        PonderFx.aside(scene, util,
                "Dialling it down is a real saving rather than a throttle, because a pump that is asked for less is charged for less",
                pump, PonderPalette.MEDIUM, 115);

        // Grade, shown as a swap in place with particles, then said in Create's own units.
        scene.world().setBlock(pump, ModBlocks.FLUID_PUMP_MK2.getDefaultState()
                .setValue(FluidPumpBlock.FACING, Direction.EAST), true);
        scene.idle(10);
        scene.overlay().showCenteredScrollInput(pump, Direction.UP, 50);
        scene.idle(8);
        PonderFx.beat(scene, util,
                "MK I tops out at 64 millibuckets a tick, exactly a Create pump spun at 128 RPM. MK II reaches 128, matching Create's top speed",
                pump, PonderPalette.GREEN, 135);
        PonderFx.aside(scene, util,
                "Pinning the grades to numbers you already know from Create is what makes the throughput mean something",
                pump, PonderPalette.WHITE, 110);

        put(scene, meter, ModBlocks.FLOW_METER.getDefaultState());
        scene.world().showSection(util.select().position(meter), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(meter, Direction.NORTH), Pointing.RIGHT, 55)
                .withItem(ModBlocks.FLOW_METER.asStack());
        PonderFx.beat(scene, util,
                "Splice a Flow Meter into the same run to see what the dial is actually achieving downstream",
                meter, PonderPalette.INPUT, 110);
        scene.effects().indicateSuccess(pump);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 3. Induction Range
    // ==================================================================================

    /** [2] */
    public static void inductionRange(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("induction_range", "Induction Range: an electric hob");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos range = util.grid().at(4, 1, 4);
        BlockPos basin = util.grid().at(4, 2, 4);
        BlockPos bank = util.grid().at(1, 1, 4);
        BlockPos cableA = util.grid().at(2, 1, 4);
        BlockPos cableB = util.grid().at(3, 1, 4);

        put(scene, range, ModBlocks.INDUCTION_RANGE.getDefaultState());
        scene.world().showSection(util.select().position(range), Direction.DOWN);
        scene.idle(14);

        scene.overlay().showControls(util.vector().blockSurface(range, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.INDUCTION_RANGE.asStack());
        PonderFx.beat(scene, util,
                "The Induction Range is an electric hob: energy in, heat out, at whichever of three settings you dial",
                range, PonderPalette.BLUE, 110);

        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, cableA, floorCable());
        put(scene, cableB, floorCable());
        PonderFx.reveal(scene, util, Direction.DOWN, 4, bank, cableA, cableB);
        scene.idle(8);
        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, List.of(bank, cableA, cableB, range), 5);
        scene.idle(22);
        PonderFx.beat(scene, util,
                "Feed it with Surface Cable or a Power Connector, and it is placed with its control panel facing you rather than into the wall",
                range, PonderPalette.INPUT, 120);

        // Walk the dial. Each click is an event, so each gets particles and a beat of its own.
        scene.overlay().showControls(util.vector().blockSurface(range, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        scene.idle(10);
        scene.effects().indicateSuccess(range);
        scene.idle(14);
        PonderFx.beat(scene, util,
                "Right-click to turn the dial. Low costs 16 energy a tick and cooks like a campfire, Medium 64 and also smelts, High 256 and also blasts",
                range, PonderPalette.OUTPUT, 140);

        put(scene, basin, AllBlocks.BASIN.getDefaultState());
        scene.world().showSection(util.select().position(basin), Direction.DOWN);
        scene.idle(12);
        PonderFx.beatOutlined(scene, util,
                "Medium is exactly Create's Heated and High exactly Superheated, so a Basin or a Boiler behaves as it would on a Blaze Burner or a blaze cake",
                util.select().position(basin), basin, PonderPalette.GREEN, 145);

        // The bank rule. Neighbours light up in a spreading ring, which is what the rule looks like.
        PonderFx.look(scene, range);
        final BlockPos[] ring = {
                util.grid().at(3, 1, 3), util.grid().at(4, 1, 3), util.grid().at(5, 1, 3),
                util.grid().at(5, 1, 4), util.grid().at(5, 1, 5), util.grid().at(4, 1, 5),
                util.grid().at(3, 1, 5)
        };
        for (BlockPos pos : ring) {
            scene.world().setBlock(pos, ModBlocks.INDUCTION_RANGE.getDefaultState(), true);
            scene.world().showSection(util.select().position(pos), Direction.DOWN);
            scene.idle(3);
        }
        scene.idle(12);
        PonderFx.beatOutlined(scene, util,
                "A range also powers every range within one block of it, so a three by three bank runs off a single cable landing anywhere on its edge",
                util.select().fromTo(3, 1, 3, 5, 1, 5), range, PonderPalette.MEDIUM, 145);

        PonderFx.aside(scene, util,
                "Belts and funnels load and unload it, and a funnel only ever pulls a finished batch rather than half-cooked items",
                range, PonderPalette.INPUT, 120);

        scene.effects().indicateRedstone(range);
        PonderFx.beat(scene, util,
                "If the grid cannot hold the setting you asked for, the range says so rather than quietly dropping a level. That is your cue to look at the generators",
                range, PonderPalette.RED, 140);
        scene.effects().indicateSuccess(range);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 4. Flow Meter
    // ==================================================================================

    public static void flowMeter(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("flow_meter_reading", "Flow Meter: reading a pipe");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos source = util.grid().at(2, 1, 5);
        BlockPos pump = util.grid().at(3, 1, 5);
        BlockPos meter = util.grid().at(4, 1, 5);
        BlockPos sink = util.grid().at(5, 1, 5);
        BlockPos link = util.grid().at(4, 2, 5);
        BlockPos panelLow = util.grid().at(6, 1, 5);
        BlockPos panelHigh = util.grid().at(6, 2, 5);

        put(scene, source, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        put(scene, pump, ModBlocks.FLUID_PUMP_MK1.getDefaultState()
                .setValue(FluidPumpBlock.FACING, Direction.EAST));
        put(scene, meter, ModBlocks.FLOW_METER.getDefaultState());
        put(scene, sink, AllBlocks.FLUID_TANK.getDefaultState());
        PonderFx.reveal(scene, util, Direction.DOWN, 4, source, pump, meter, sink);
        scene.idle(12);

        scene.overlay().showControls(util.vector().blockSurface(meter, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.FLOW_METER.asStack());
        PonderFx.beat(scene, util,
                "The Flow Meter is a length of pipe that counts what goes through it. Fluid moves exactly as it would through copper pipe",
                meter, PonderPalette.BLUE, 120);

        // Flow, drawn as a pulse walking the line so idle and flowing are visibly different later.
        final Object flow = new Object();
        PonderFx.energyPulse(scene, PonderPalette.INPUT, flow, List.of(source, pump, meter, sink), 5);
        scene.idle(22);
        PonderFx.aside(scene, util,
                "Splice it into a line and it auto-aligns with whatever pipe or tank it finds beside it. It can be waterlogged like any pipe",
                meter, PonderPalette.INPUT, 120);

        PonderFx.look(scene, panelHigh);
        put(scene, link, AllBlocks.DISPLAY_LINK.getDefaultState());
        put(scene, panelLow, ModBlocks.STATS_DISPLAY_PANEL.getDefaultState());
        put(scene, panelHigh, ModBlocks.STATS_DISPLAY_PANEL.getDefaultState());
        PonderFx.reveal(scene, util, Direction.DOWN, 5, panelLow, panelHigh, link);
        scene.world().flashDisplayLink(link);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(link, Direction.UP), Pointing.DOWN, 55)
                .withItem(AllBlocks.DISPLAY_LINK.asStack());
        PonderFx.beat(scene, util,
                "Point a Display Link from the meter at a Stats Display and the readings become a panel you can read across a room",
                link, PonderPalette.OUTPUT, 130);

        PonderFx.beatOutlined(scene, util,
                "It reports four things: which fluid, which way it is going, the rate in millibuckets a tick, and the total volume moved so far",
                CreateStatsPonderScenes.select(util, panelLow, panelHigh), panelHigh, PonderPalette.GREEN, 140);
        PonderFx.aside(scene, util,
                "Switch the panel to Specialized mode for an animated pipe with a flow gauge and a direction arrow",
                panelHigh, PonderPalette.MEDIUM, 115);

        scene.effects().indicateRedstone(meter);
        PonderFx.beat(scene, util,
                "Set a redstone trigger from that panel and the meter emits a signal while fluid is flowing, or when the rate crosses a number you pick",
                meter, PonderPalette.OUTPUT, 140);

        // The blocked-line case, and the flow visibly stopping is the whole explanation.
        PonderFx.look(scene, sink);
        scene.world().setBlock(sink, AllBlocks.FLUID_TANK.getDefaultState(), true);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.RED, "full", util.select().position(sink), 120);
        PonderFx.energyPulse(scene, PonderPalette.RED, flow, List.of(source, pump), 6);
        scene.idle(16);
        PonderFx.beat(scene, util,
                "One reading that surprises people: if the tank in front is full the fluid cannot move, so the meter correctly reads idle while the pump keeps pushing",
                sink, PonderPalette.RED, 145);
        scene.effects().indicateSuccess(meter);
        scene.idle(15);
        scene.markAsFinished();
    }
}
