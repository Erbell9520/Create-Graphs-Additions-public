package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Stats Recorder ponder, split out of CreateStatsPonderScenes (topic-class layout). */
public class RecorderScenes {

    public static void statsRecorderGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("stats_recorder_data_logger", "Stats Recorder: logging data over time");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.setSceneOffsetY(-1);

        BlockPos recorder = util.grid().at(4, 1, 4);
        Selection recorderSel = util.select().position(recorder);
        Selection source = CreateStatsPonderScenes.select(util, util.grid().at(4, 1, 2), util.grid().at(4, 2, 2));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(recorderSel, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(recorder, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModBlocks.STATS_RECORDER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Stats Recorder quietly logs any metric over time into its memory", recorder, PonderPalette.BLUE, 90);

        scene.world().showSection(source, Direction.SOUTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.INPUT, util.grid().at(4, 2, 2), source, 80);
        CreateStatsPonderScenes.say(scene, util, "Sit it next to a stats display, or wire it to a display link, and whatever they show becomes its source", recorder, PonderPalette.INPUT, 95);

        scene.effects().indicateRedstone(recorder);
        scene.world().createItemEntity(util.vector().topOf(recorder).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.PAPER));
        scene.idle(14);
        scene.effects().indicateRedstone(recorder);
        scene.idle(14);
        CreateStatsPonderScenes.say(scene, util, "Tick after tick it samples the value and keeps the whole history", recorder, PonderPalette.GREEN, 90);

        scene.overlay().showControls(util.vector().blockSurface(recorder, Direction.UP), Pointing.DOWN, 50)
                        .rightClick().withItem(ModItems.MEMORY_COG.asStack());
        CreateStatsPonderScenes.say(scene, util, "Drop in a Memory Cog and it bottles up a snapshot of the recording you can carry away", recorder, PonderPalette.OUTPUT, 100);
        scene.idle(10);

        CreateStatsPonderScenes.say(scene, util, "Slot that cog into a display, or another recorder, to replay the exact data anywhere", recorder, PonderPalette.BLUE, 90);
        scene.idle(6);

        scene.overlay().showControls(util.vector().blockSurface(recorder, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModItems.SMALL_SIGNAL_ANTENNA.asStack());
        CreateStatsPonderScenes.say(scene, util, "It can auto-export to a CSV file, and a Signal Antenna lets it log machines from across your base", recorder, PonderPalette.OUTPUT, 100);
        scene.idle(8);

        scene.effects().indicateSuccess(recorder);
    }
}
