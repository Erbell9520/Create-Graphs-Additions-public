package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.block.PowerConnectorBlock;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** [1] */
public class CableScenes {

    private static final int WALL_Z = 5;
    private static final int FACE_Z = 4;

    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    /** Cable of the given grade hugging the wall behind it. */
    private static BlockState run(com.tterrag.registrate.util.entry.BlockEntry<CableBlock> grade) {
        return grade.getDefaultState().setValue(CableBlock.faceProperty(Direction.SOUTH), true);
    }

    /** The left-to-right path along one of the three runs. */
    private static List<BlockPos> lane(SceneBuildingUtil util, int y) {
        final List<BlockPos> path = new ArrayList<>();
        for (int x = 1; x <= 7; x++) {
            path.add(util.grid().at(x, y, FACE_Z));
        }
        return path;
    }

    // ==================================================================================
    // 1. The three grades
    // ==================================================================================

    public static void cableGrades(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cable_grades", "Cable: laying a run");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                put(scene, util.grid().at(x, y, WALL_Z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        scene.world().showSection(util.select().fromTo(1, 1, WALL_Z, 7, 3, WALL_Z), Direction.SOUTH);
        scene.idle(10);

        // Lay the starting grade one block at a time with particles, which is how the tool feels.
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(1, 1, FACE_Z), Direction.NORTH),
                        Pointing.RIGHT, 40)
                .rightClick()
                .withItem(ModBlocks.SURFACE_CABLE.asStack());
        scene.idle(10);
        for (int x = 1; x <= 7; x++) {
            final BlockPos pos = util.grid().at(x, 1, FACE_Z);
            scene.world().setBlock(pos, run(ModBlocks.SURFACE_CABLE), true);
            scene.world().showSection(util.select().position(pos), Direction.NORTH);
            scene.idle(3);
        }
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Surface Cable lies on block faces and turns corners, for wiring that stays inside a build rather than hanging across it",
                util.grid().at(4, 1, FACE_Z), PonderPalette.BLUE, 115);
        PonderFx.aside(scene, util,
                "Click to start a run, aim where it should go to preview it, then click again to lay it. Click a machine to finish on it",
                util.grid().at(2, 1, FACE_Z), PonderPalette.INPUT, 115);
        PonderFx.aside(scene, util,
                "Sneak while running to stop there, or sneak on a finished run to pull all of it back into your pocket",
                util.grid().at(6, 1, FACE_Z), PonderPalette.MEDIUM, 110);

        // The heavier grades, stacked above so the thickness difference is the point.
        for (int x = 1; x <= 7; x++) {
            put(scene, util.grid().at(x, 2, FACE_Z), run(ModBlocks.CABLE_TRAY));
        }
        scene.world().showSection(util.select().fromTo(1, 2, FACE_Z, 7, 2, FACE_Z), Direction.NORTH);
        scene.idle(10);
        for (int x = 1; x <= 7; x++) {
            put(scene, util.grid().at(x, 3, FACE_Z), run(ModBlocks.BUS_DUCT));
        }
        scene.world().showSection(util.select().fromTo(1, 3, FACE_Z, 7, 3, FACE_Z), Direction.NORTH);
        scene.idle(14);

        PonderFx.beatOutlined(scene, util,
                "Three grades: Surface Cable at the base rate, Cable Tray at three times it, and Bus Duct at six",
                util.select().fromTo(1, 1, FACE_Z, 7, 3, FACE_Z),
                util.grid().at(4, 2, FACE_Z), PonderPalette.GREEN, 115);

        // Capacity as simultaneous pulses: one, three, six. Not speed, which would be a lie.
        final List<BlockPos> surface = lane(util, 1);
        final List<BlockPos> tray = lane(util, 2);
        final List<BlockPos> duct = lane(util, 3);
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, new Object(), surface, 5);
        for (int i = 0; i < 3; i++) {
            PonderFx.energyPulse(scene, PonderPalette.OUTPUT, new Object(), tray, 5);
            scene.idle(4);
        }
        for (int i = 0; i < 6; i++) {
            PonderFx.energyPulse(scene, PonderPalette.OUTPUT, new Object(), duct, 5);
            scene.idle(2);
        }
        scene.idle(30);
        PonderFx.beat(scene, util,
                "Width is the readout. A heavier grade is drawn visibly fatter, so you can tell a trunk from a spur across a room without opening anything",
                util.grid().at(4, 3, FACE_Z), PonderPalette.OUTPUT, 120);
        PonderFx.aside(scene, util,
                "It carries more, not sooner. The grid is solved whole every tick, so power already reaches the far end of any run immediately",
                util.grid().at(6, 3, FACE_Z), PonderPalette.WHITE, 120);
        PonderFx.aside(scene, util,
                "Spend the Bus Duct on the run between your generators and your distribution panel, where everything else has to fit through",
                util.grid().at(2, 3, FACE_Z), PonderPalette.MEDIUM, 120);

        // Colour as circuit identity.
        PonderFx.look(scene, util.grid().at(4, 2, FACE_Z));
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(4, 2, FACE_Z), Direction.NORTH),
                        Pointing.RIGHT, 55)
                .rightClick()
                .withItem(new ItemStack(Items.LIME_DYE));
        scene.idle(10);
        for (int x = 1; x <= 7; x++) {
            final BlockPos pos = util.grid().at(x, 2, FACE_Z);
            scene.world().modifyBlock(pos, s -> s.setValue(CableBlock.COLOR, DyeColor.LIME), true);
            scene.idle(2);
        }
        scene.idle(10);
        PonderFx.beat(scene, util,
                "One cable block carries one circuit. Two circuits down one corridor means two cables side by side, exactly as a real install looks",
                util.grid().at(4, 2, FACE_Z), PonderPalette.INPUT, 120);
        PonderFx.beat(scene, util,
                "Dye is how you tell them apart: colour is the circuit, so two runs of different colours can share a wall without joining",
                util.grid().at(4, 2, FACE_Z), PonderPalette.MEDIUM, 120);
        scene.effects().indicateSuccess(util.grid().at(4, 2, FACE_Z));
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. Rated terminals and wires
    // ==================================================================================

    public static void ratedHops(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cable_rated_hops", "Connectors and wire: rating a hop");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.66f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos bank = util.grid().at(2, 1, 4);
        BlockPos bankConn = util.grid().at(2, 2, 4);
        BlockPos sink = util.grid().at(6, 1, 4);
        BlockPos sinkConn = util.grid().at(6, 2, 4);

        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        put(scene, sink, ModBlocks.CAPACITOR_BANK.getDefaultState());
        put(scene, bankConn, ModBlocks.POWER_CONNECTOR.getDefaultState()
                .setValue(PowerConnectorBlock.FACING, Direction.UP));
        put(scene, sinkConn, ModBlocks.POWER_CONNECTOR.getDefaultState()
                .setValue(PowerConnectorBlock.FACING, Direction.UP));
        PonderFx.reveal(scene, util, Direction.DOWN, 5, bank, sink, bankConn, sinkConn);
        scene.idle(12);

        scene.overlay().showControls(util.vector().blockSurface(bankConn, Direction.UP), Pointing.DOWN, 55)
                .rightClick()
                .withItem(ModItems.COPPER_WIRE.asStack());
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.OUTPUT,
                util.vector().topOf(bankConn), util.vector().topOf(sinkConn), 130);
        PonderFx.beat(scene, util,
                "A hop through open air is made of three rated parts: the terminal at each end, and the wire strung between them",
                bankConn, PonderPalette.BLUE, 120);
        PonderFx.aside(scene, util,
                "Copper Wire moves the base rate, Insulated Wire twice it, and Shielded Wire three times",
                bankConn, PonderPalette.OUTPUT, 110);

        // Upgrade both terminals, with particles, so it reads as a swap rather than a redraw.
        PonderFx.look(scene, sinkConn);
        scene.world().setBlock(bankConn, ModBlocks.ADVANCED_POWER_CONNECTOR.getDefaultState()
                .setValue(PowerConnectorBlock.FACING, Direction.UP), true);
        scene.idle(6);
        scene.world().setBlock(sinkConn, ModBlocks.ADVANCED_POWER_CONNECTOR.getDefaultState()
                .setValue(PowerConnectorBlock.FACING, Direction.UP), true);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(sinkConn, Direction.UP), Pointing.DOWN, 55)
                .withItem(ModBlocks.ADVANCED_POWER_CONNECTOR.asStack());
        PonderFx.beat(scene, util,
                "The Advanced Power Connector is the same terminal rated for three times the throughput, and mounts exactly like the plain one",
                sinkConn, PonderPalette.GREEN, 120);

        // The weakest-link rule. One thin pulse despite two upgraded ends.
        final Object hop = new Object();
        PonderFx.energyPulse(scene, PonderPalette.RED, hop, List.of(bankConn, sinkConn), 8);
        scene.idle(20);
        PonderFx.beatOutlined(scene, util,
                "Here is the rule that catches people out: a hop only moves as much as its weakest part",
                util.select().position(bankConn), bankConn, PonderPalette.RED, 110);
        PonderFx.beat(scene, util,
                "Two Advanced connectors joined by plain Copper Wire still move the base rate. The upgrade is wasted until the wire matches",
                sinkConn, PonderPalette.RED, 125);

        // Match the wire and the hop finally carries three times as much: three pulses, not faster ones.
        scene.overlay().showControls(util.vector().blockSurface(bankConn, Direction.UP), Pointing.DOWN, 55)
                .rightClick()
                .withItem(ModItems.SHIELDED_WIRE.asStack());
        scene.idle(10);
        for (int i = 0; i < 3; i++) {
            PonderFx.energyPulse(scene, PonderPalette.GREEN, new Object(), List.of(bankConn, sinkConn), 8);
            scene.idle(5);
        }
        scene.idle(20);
        scene.effects().indicateSuccess(bankConn);
        scene.effects().indicateSuccess(sinkConn);
        PonderFx.beat(scene, util,
                "Upgrade both ends and the wire between them and the hop finally carries three times the base rate",
                bankConn, PonderPalette.INPUT, 115);
        PonderFx.aside(scene, util,
                "The base rate, and every multiplier above it, is tunable in the [power.wiring] config section",
                sinkConn, PonderPalette.WHITE, 105);
        scene.idle(10);
        scene.markAsFinished();
    }
}
