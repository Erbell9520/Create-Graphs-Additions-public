package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.block.LampBlock;
import com.createstats.block.SwitchBlock;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/** [1] */
public class SwitchScenes {

    /** The plane of wall every switch in these scenes is mounted on. */
    private static final int WALL_Z = 5;
    /** The row of blocks in front of the wall where the switches and cable actually sit. */
    private static final int FACE_Z = 4;

    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    /** Raises the andesite wall the whole set mounts against, and reveals it. */
    private static void showWall(CreateSceneBuilder scene, SceneBuildingUtil util) {
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                put(scene, util.grid().at(x, y, WALL_Z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        scene.world().showSection(util.select().fromTo(1, 1, WALL_Z, 7, 3, WALL_Z), Direction.SOUTH);
        scene.idle(10);
    }

    /** A switch mounted on the wall, so it faces out of it toward the viewer. */
    private static BlockState plate(com.tterrag.registrate.util.entry.BlockEntry<SwitchBlock> kind, boolean on) {
        return kind.getDefaultState()
                .setValue(SwitchBlock.FACING, Direction.NORTH)
                .setValue(SwitchBlock.POWERED, on);
    }

    /** Surface cable hugging the wall behind it. */
    private static BlockState wallCable() {
        return ModBlocks.SURFACE_CABLE.getDefaultState()
                .setValue(CableBlock.faceProperty(Direction.SOUTH), true);
    }

    // ==================================================================================
    // 1. The plates themselves
    // ==================================================================================

    public static void switchPlates(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("switch_plates", "Switches: the set and the signal");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);
        showWall(scene, util);

        BlockPos rocker = util.grid().at(1, 2, FACE_Z);
        BlockPos toggle = util.grid().at(2, 2, FACE_Z);
        BlockPos button = util.grid().at(3, 2, FACE_Z);
        BlockPos isolator = util.grid().at(4, 2, FACE_Z);
        BlockPos twin = util.grid().at(5, 2, FACE_Z);

        put(scene, rocker, plate(ModBlocks.ROCKER_SWITCH, false));
        put(scene, toggle, plate(ModBlocks.TOGGLE_SWITCH, false));
        put(scene, button, plate(ModBlocks.PUSH_BUTTON, false));
        put(scene, isolator, plate(ModBlocks.ISOLATOR_SWITCH, false));
        put(scene, twin, plate(ModBlocks.DOUBLE_SWITCH, false));
        PonderFx.reveal(scene, util, Direction.NORTH, 4, rocker, toggle, button, isolator, twin);
        scene.idle(10);

        scene.overlay().showControls(util.vector().blockSurface(toggle, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.TOGGLE_SWITCH.asStack());
        PonderFx.beat(scene, util,
                "Five plates that all mount flush on a wall, floor or ceiling, and all point away from the face you clicked",
                toggle, PonderPalette.BLUE, 105);

        // Throw them down the row. Particles on each so a throw reads as an act, not a redraw.
        scene.overlay().showControls(util.vector().blockSurface(rocker, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        scene.idle(8);
        for (BlockPos plate : List.of(rocker, toggle, isolator, button)) {
            scene.world().modifyBlock(plate, s -> s.setValue(SwitchBlock.POWERED, true), true);
            scene.idle(6);
        }
        scene.idle(8);
        PonderFx.beat(scene, util,
                "Every one of them latches: it stays where you put it and reads on or off from across a room, the push button included",
                button, PonderPalette.GREEN, 110);

        PonderFx.look(scene, isolator);
        PonderFx.beatOutlined(scene, util,
                "The Isolator is the chunky lockable rotary you put on the wall beside a machine. Its handle turns a quarter turn when thrown",
                util.select().position(isolator), isolator, PonderPalette.OUTPUT, 115);

        // Redstone shape: weak all round, strong into the mounting block. Shown by lighting a
        // vanilla lamp sitting on the far side of the wall from the switch.
        BlockPos behind = util.grid().at(1, 2, WALL_Z + 1);
        put(scene, behind, Blocks.REDSTONE_LAMP.defaultBlockState());
        scene.world().showSection(util.select().position(behind), Direction.NORTH);
        scene.idle(10);
        PonderFx.look(scene, behind);
        scene.world().modifyBlock(rocker, s -> s.setValue(SwitchBlock.POWERED, false), true);
        scene.idle(8);
        scene.world().modifyBlock(rocker, s -> s.setValue(SwitchBlock.POWERED, true), true);
        scene.effects().indicateRedstone(behind);
        scene.world().modifyBlock(behind, s -> s.setValue(BlockStateProperties.LIT, true), true);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "They emit redstone the way a lever does: weakly to everything around them, and strongly into the block they are mounted on",
                behind, PonderPalette.RED, 115);
        PonderFx.aside(scene, util,
                "So a switch on a wall drives whatever is on the other side of that wall, which is the whole reason to put one there",
                behind, PonderPalette.WHITE, 110);

        // The two-gang plate, one gang at a time so the halves are visibly independent.
        PonderFx.look(scene, twin);
        scene.overlay().showControls(util.vector().blockSurface(twin, Direction.NORTH), Pointing.RIGHT, 55)
                .rightClick()
                .withItem(ModBlocks.DOUBLE_SWITCH.asStack());
        scene.idle(10);
        scene.world().modifyBlock(twin, s -> s.setValue(SwitchBlock.POWERED, true), true);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "The Double Switch is two independent switches sharing one plate. Click the left half or the right half and only that gang moves",
                twin, PonderPalette.INPUT, 115);
        scene.world().modifyBlock(twin, s -> s.setValue(SwitchBlock.POWERED_B, true), true);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "Each gang powers its own side of the block so it can drive two circuits, and both still feed the block behind",
                twin, PonderPalette.MEDIUM, 110);
        scene.effects().indicateSuccess(twin);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. Switches in a circuit
    // ==================================================================================

    public static void switchCircuits(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("switch_circuits", "Switches: breaking the run");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);
        showWall(scene, util);

        BlockPos bank = util.grid().at(1, 2, FACE_Z);
        BlockPos runA = util.grid().at(2, 2, FACE_Z);
        BlockPos rocker = util.grid().at(3, 2, FACE_Z);
        BlockPos runB = util.grid().at(4, 2, FACE_Z);
        BlockPos lampA = util.grid().at(5, 2, FACE_Z);
        BlockPos runC = util.grid().at(6, 2, FACE_Z);
        BlockPos lampB = util.grid().at(7, 2, FACE_Z);

        final List<BlockPos> upstream = List.of(bank, runA, rocker);
        final List<BlockPos> downstream = List.of(runB, lampA, runC, lampB);
        final List<BlockPos> whole = List.of(bank, runA, rocker, runB, lampA, runC, lampB);

        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, runA, wallCable());
        put(scene, rocker, plate(ModBlocks.ROCKER_SWITCH, true));
        put(scene, runB, wallCable());
        put(scene, lampA, ModBlocks.PANEL_LAMP.getDefaultState()
                .setValue(LampBlock.FACING, Direction.NORTH).setValue(LampBlock.LIT, true));
        put(scene, runC, wallCable());
        put(scene, lampB, ModBlocks.PANEL_LAMP.getDefaultState()
                .setValue(LampBlock.FACING, Direction.NORTH).setValue(LampBlock.LIT, true));
        // Reveal along the direction the current will later flow, which sets up the whole scene.
        PonderFx.reveal(scene, util, Direction.NORTH, 4, bank, runA, rocker, runB, lampA, runC, lampB);
        scene.idle(12);

        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, whole, 4);
        scene.idle(32);
        PonderFx.beatOutlined(scene, util,
                "A switch is not a signal on the side of a circuit. It is a link in the run itself, wired in line like the real thing",
                CreateStatsPonderScenes.select(util, bank, runA, rocker, runB, lampA, runC, lampB),
                rocker, PonderPalette.GREEN, 115);

        // Open it. The pulse now dies at the switch, and the far half goes dark behind it.
        PonderFx.look(scene, rocker);
        scene.overlay().showControls(util.vector().blockSurface(rocker, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        scene.idle(8);
        scene.world().modifyBlock(rocker, s -> s.setValue(SwitchBlock.POWERED, false), true);
        scene.idle(6);
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, upstream, 4);
        scene.idle(6);
        scene.world().modifyBlock(lampA, s -> s.setValue(LampBlock.LIT, false), true);
        scene.idle(5);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, false), true);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.RED, "dead",
                CreateStatsPonderScenes.select(util, runB, lampA, runC, lampB), 110);
        PonderFx.beat(scene, util,
                "Open it and the contacts part. Everything downstream is cut off from the supply and goes dark, however long the run is",
                lampA, PonderPalette.RED, 115);
        PonderFx.aside(scene, util,
                "The half in front of the switch is still live. It is the circuit that is broken, not the power",
                bank, PonderPalette.INPUT, 105);

        // Close it. Both halves rediscover each other on the same tick.
        scene.overlay().showControls(util.vector().blockSurface(rocker, Direction.NORTH), Pointing.RIGHT, 40).rightClick();
        scene.idle(8);
        scene.world().modifyBlock(rocker, s -> s.setValue(SwitchBlock.POWERED, true), true);
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, whole, 3);
        scene.idle(14);
        scene.world().modifyBlock(lampA, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(4);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, true), true);
        scene.effects().indicateSuccess(rocker);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "Close it and both halves rediscover each other on the same tick, so the room comes back immediately rather than on the next block update",
                rocker, PonderPalette.INPUT, 120);

        // The wireless alternative, drawn as an actual link between the plate and the fitting.
        PonderFx.look(scene, lampB);
        scene.overlay().showControls(util.vector().blockSurface(rocker, Direction.NORTH), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(ModItems.SWITCH_LINK_CARD.asStack());
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.BLUE,
                util.vector().centerOf(rocker), util.vector().centerOf(lampB), 120);
        PonderFx.beat(scene, util,
                "When you cannot run cable through the switch, a Switch Link Card pairs it to fittings instead. Click the switch to hold it, then click lamps",
                rocker, PonderPalette.BLUE, 120);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, false), true);
        scene.idle(10);
        // The pulse still reaches it: linked does not mean unfed, which is the point.
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current, downstream, 4);
        scene.idle(20);
        PonderFx.beat(scene, util,
                "A linked lamp is still fed by its own cable. The switch only tells it whether to light, so one plate can control a room it is nowhere near",
                lampB, PonderPalette.MEDIUM, 120);
        PonderFx.aside(scene, util,
                "Sneak-click to let go of the held switch, and click a linked lamp again to unlink it",
                rocker, PonderPalette.WHITE, 100);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, true), true);
        scene.effects().indicateSuccess(lampB);
        scene.idle(15);
        scene.markAsFinished();
    }
}
