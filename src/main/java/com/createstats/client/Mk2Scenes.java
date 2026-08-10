package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.vccmk2.VccMk2Block;
import com.createstats.vccmk2.VccMk2VisualState;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** [1] */
public class Mk2Scenes {

    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    // ==================================================================================
    // 1. Stats Display Mk II
    // ==================================================================================

    public static void statsDisplayMk2(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("stats_display_mk2", "Stats Display Mk II: the studio");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.68f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos lowerLeft = util.grid().at(3, 1, 5);
        BlockPos lowerRight = util.grid().at(4, 1, 5);
        BlockPos upperLeft = lowerLeft.above();
        BlockPos upperRight = lowerRight.above();
        BlockPos chest = util.grid().at(1, 1, 5);
        BlockPos link = util.grid().at(1, 2, 5);

        // One panel first, so the merge that follows is visibly a merge.
        put(scene, lowerLeft, ModBlocks.STATS_DISPLAY_MK2.getDefaultState());
        scene.world().showSection(util.select().position(lowerLeft), Direction.NORTH);
        scene.idle(14);
        scene.overlay().showControls(util.vector().blockSurface(lowerLeft, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.STATS_DISPLAY_MK2.asStack());
        PonderFx.beat(scene, util,
                "The Stats Display Mk II is the same panel with a full-screen configuration studio behind it",
                lowerLeft, PonderPalette.BLUE, 110);

        put(scene, lowerRight, ModBlocks.STATS_DISPLAY_MK2.getDefaultState());
        put(scene, upperLeft, ModBlocks.STATS_DISPLAY_MK2.getDefaultState());
        put(scene, upperRight, ModBlocks.STATS_DISPLAY_MK2.getDefaultState());
        PonderFx.reveal(scene, util, Direction.NORTH, 5, lowerRight, upperLeft, upperRight);
        scene.idle(12);
        PonderFx.beatOutlined(scene, util,
                "Everything you already know carries over: matching panels placed side by side with the same facing merge into one larger screen",
                CreateStatsPonderScenes.select(util, lowerLeft, lowerRight, upperLeft, upperRight),
                upperRight, PonderPalette.GREEN, 130);

        PonderFx.look(scene, chest);
        put(scene, chest, Blocks.CHEST.defaultBlockState());
        put(scene, link, AllBlocks.DISPLAY_LINK.getDefaultState());
        PonderFx.reveal(scene, util, Direction.DOWN, 5, chest, link);
        scene.world().flashDisplayLink(link);
        scene.idle(12);
        scene.overlay().showBigLine(PonderPalette.OUTPUT,
                util.vector().centerOf(link), util.vector().centerOf(lowerLeft), 110);
        PonderFx.beat(scene, util,
                "It reads exactly the same sources through exactly the same Display Links, so a Mk I wall can be upgraded panel by panel",
                link, PonderPalette.INPUT, 130);

        PonderFx.look(scene, lowerLeft);
        scene.overlay().showControls(util.vector().blockSurface(lowerLeft, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        scene.idle(10);
        scene.effects().indicateSuccess(lowerLeft);
        PonderFx.beat(scene, util,
                "Right-click opens the studio instead of the old screen. Sneak-right-click still cycles the small in-world view",
                lowerLeft, PonderPalette.OUTPUT, 125);

        PonderFx.beat(scene, util,
                "The eight Stats Display chapters cover sources, channels, dashboards and the specialized diagrams. All of them apply here unchanged",
                upperLeft, PonderPalette.WHITE, 135);
        scene.idle(10);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. VCC Mk II
    // ==================================================================================

    public static void vccMk2(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vcc_mk2", "VCC Mk II: the flight deck so far");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.68f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos console = util.grid().at(4, 1, 4);
        put(scene, console, ModBlocks.VCC_MK_II.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        scene.world().showSection(util.select().position(console), Direction.DOWN);
        scene.idle(16);

        scene.overlay().showControls(util.vector().blockSurface(console, Direction.NORTH), Pointing.RIGHT, 60)
                .withItem(ModBlocks.VCC_MK_II.asStack());
        PonderFx.beat(scene, util,
                "The VCC Mk II is a gold-cased cockpit console with a live CRT flight deck drawn on its screen",
                console, PonderPalette.BLUE, 120);

        // Turn to the screen. This one block is entirely about how it looks, so show the face.
        scene.rotateCameraY(35);
        scene.idle(16);
        PonderFx.beat(scene, util,
                "Place it on a Sable contraption and the screen shows a glass-cockpit readout of the craft it is riding",
                console, PonderPalette.INPUT, 125);

        // walks the four themes, that's all this block does today
        scene.overlay().showControls(util.vector().blockSurface(console, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        scene.idle(10);
        PonderFx.beat(scene, util,
                "Right-click with an empty hand to cycle the screen state, so you can see all four CRT themes in place before you commit to a cockpit layout",
                console, PonderPalette.OUTPUT, 140);
        for (VccMk2VisualState state : VccMk2VisualState.values()) {
            scene.world().modifyBlock(console, s -> s.setValue(VccMk2Block.VISUAL_STATE, state), true);
            scene.idle(18);
        }
        scene.idle(10);
        scene.rotateCameraY(-35);
        scene.idle(12);

        ElementLink<ParrotElement> birb = scene.special().createBirb(
                util.vector().topOf(console).add(0, 0.15, 0), ParrotPose.FacePointOfInterestPose::new);
        scene.idle(8);
        PonderFx.beatOutlined(scene, util,
                "Worth knowing before you build around it: this is a look-first milestone. The full control interface is still being built",
                util.select().position(console), console, PonderPalette.RED, 135);
        scene.special().changeBirbPose(birb, ParrotPose.FaceCursorPose::new);
        PonderFx.beat(scene, util,
                "It is also a separate system from the Flight Control Computer rather than a replacement for it, so the FCC's controls are not hiding in here",
                console, PonderPalette.WHITE, 140);
        scene.idle(10);
        scene.markAsFinished();
    }
}
