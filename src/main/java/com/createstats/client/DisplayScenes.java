package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.block.StatusTowerLightBlock;
import com.createstats.block.DataDialBlock;
import com.createstats.block.MiniDisplayBlock;
import com.createstats.blockentity.DataDialBE;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Wall sensor/display block ponders, split out of CreateStatsPonderScenes (topic-class layout). */
public class DisplayScenes {

    public static void statusTowerLightGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("status_tower_light_operator_beacon", "Status Tower Light: operator beacon");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos post = util.grid().at(4, 1, 2);
        BlockPos light = util.grid().at(4, 2, 2);
        BlockPos lamp = util.grid().at(3, 2, 2);
        BlockPos tankLow = util.grid().at(6, 1, 2);
        BlockPos tankHigh = util.grid().at(6, 2, 2);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(CreateStatsPonderScenes.select(util, post, light), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(light), Pointing.DOWN, 50)
                        .withItem(ModBlocks.STATUS_TOWER_LIGHT.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Status Tower Light is an at-a-glance operator beacon for any process", light, PonderPalette.BLUE, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, tankLow, tankHigh), Direction.WEST);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.WHITE, tankHigh, CreateStatsPonderScenes.select(util, tankLow, tankHigh), 80);
        CreateStatsPonderScenes.say(scene, util, "Point it at a metric, a local neighbour or a linked display target like this tank", tankHigh, PonderPalette.WHITE, 95);

        scene.world().modifyBlock(light, s -> s
                        .setValue(StatusTowerLightBlock.VISUAL, StatusTowerLightBlock.VisualState.GREEN)
                        .setValue(StatusTowerLightBlock.GLOW, StatusTowerLightBlock.VisualState.GREEN), false);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "While the reading stays healthy it glows steady green, all clear", light, PonderPalette.GREEN, 90);

        scene.world().modifyBlock(light, s -> s
                        .setValue(StatusTowerLightBlock.VISUAL, StatusTowerLightBlock.VisualState.YELLOW)
                        .setValue(StatusTowerLightBlock.GLOW, StatusTowerLightBlock.VisualState.YELLOW), false);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "Cross the warning threshold, 75% by default, and it switches to amber", light, PonderPalette.OUTPUT, 90);

        scene.world().showSection(util.select().position(lamp), Direction.DOWN);
        scene.idle(5);
        scene.world().modifyBlock(light, s -> s
                        .setValue(StatusTowerLightBlock.VISUAL, StatusTowerLightBlock.VisualState.RED)
                        .setValue(StatusTowerLightBlock.GLOW, StatusTowerLightBlock.VisualState.RED), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.effects().indicateRedstone(lamp);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "At the critical threshold it turns red and emits a full redstone signal", light, PonderPalette.RED, 95);

        scene.overlay().showOutline(PonderPalette.RED, lamp, util.select().position(lamp), 70);
        CreateStatsPonderScenes.say(scene, util, "Wire that signal to a horn, a shutdown, or a Safety Clutch to react automatically", lamp, PonderPalette.RED, 95);
        scene.idle(6);

        scene.world().modifyBlock(light, s -> s
                        .setValue(StatusTowerLightBlock.VISUAL, StatusTowerLightBlock.VisualState.GREEN)
                        .setValue(StatusTowerLightBlock.GLOW, StatusTowerLightBlock.VisualState.GREEN), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, false), false);
        CreateStatsPonderScenes.say(scene, util, "When the process recovers it returns to green and the alarm clears on its own", light, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(light);
    }

    public static void dataDialGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("data_dial_wall_gauge", "Data Dial: wall gauge");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos postLow = util.grid().at(4, 1, 3);
        BlockPos postHigh = util.grid().at(4, 2, 3);
        BlockPos dial = util.grid().at(4, 2, 2);
        BlockPos lamp = util.grid().at(3, 2, 2);
        BlockPos tankLow = util.grid().at(6, 1, 2);
        BlockPos tankHigh = util.grid().at(6, 2, 2);

        Selection dialSel = util.select().position(dial);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(CreateStatsPonderScenes.select(util, postLow, postHigh), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(dialSel, Direction.SOUTH);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(dial, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModBlocks.DATA_DIAL.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Data Dial is a wall gauge that turns any reading into a sweeping analog needle", dial, PonderPalette.BLUE, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, tankLow, tankHigh), Direction.WEST);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.WHITE, tankHigh, CreateStatsPonderScenes.select(util, tankLow, tankHigh), 80);
        CreateStatsPonderScenes.say(scene, util, "Read a local neighbour, or link it to a distant source like this tank", tankHigh, PonderPalette.WHITE, 90);

        scene.world().modifyBlock(dial, s -> s.setValue(DataDialBlock.ACTIVE, true), false);
        scene.world().modifyBlockEntityNBT(dialSel, DataDialBE.class, tag -> {
                tag.putFloat("NormalizedValue", 0.28f);
                tag.putBoolean("HasData", true);
                tag.putBoolean("IsStale", false);
                tag.putString("ActiveMetricLabel", "Fill");
                tag.putString("ActiveMetricType", "GENERIC");
        });
        scene.idle(15);
        CreateStatsPonderScenes.say(scene, util, "Live data wakes it up and the needle sweeps to the current value", dial, PonderPalette.GREEN, 90);

        scene.world().modifyBlockEntityNBT(dialSel, DataDialBE.class, tag -> {
                tag.putFloat("NormalizedValue", 0.62f);
                tag.putBoolean("HasData", true);
        });
        scene.idle(15);
        CreateStatsPonderScenes.say(scene, util, "Higher readings push the needle further round and shift its colour from cool to warm", dial, PonderPalette.OUTPUT, 90);

        scene.world().modifyBlockEntityNBT(dialSel, DataDialBE.class, tag -> {
                tag.putFloat("NormalizedValue", 0.92f);
                tag.putBoolean("HasData", true);
        });
        scene.idle(15);
        CreateStatsPonderScenes.say(scene, util, "Near full scale it glows hot, an instant visual warning across the room", dial, PonderPalette.RED, 90);

        scene.world().showSection(util.select().position(lamp), Direction.DOWN);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.effects().indicateRedstone(lamp);
        scene.idle(6);
        scene.overlay().showOutline(PonderPalette.OUTPUT, lamp, util.select().position(lamp), 70);
        CreateStatsPonderScenes.say(scene, util, "The same reading is exported as a comparator signal from 0 to 15", lamp, PonderPalette.OUTPUT, 90);

        scene.overlay().showControls(util.vector().blockSurface(dial, Direction.SOUTH), Pointing.UP, 50)
                        .rightClick();
        scene.world().modifyBlockEntityNBT(dialSel, DataDialBE.class, tag -> {
                tag.putFloat("NormalizedValue", 0.45f);
                tag.putBoolean("HasData", true);
        });
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, false), false);
        CreateStatsPonderScenes.say(scene, util, "Right-click to cycle which metric it tracks: speed, stress, fill, and more", dial, PonderPalette.BLUE, 95);
        scene.effects().indicateSuccess(dial);
    }

    public static void miniDisplayGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mini_display_paired_screens", "Mini Display: paired screens");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos postL = util.grid().at(4, 1, 3);
        BlockPos wallL = util.grid().at(4, 2, 3);
        BlockPos left = util.grid().at(4, 2, 2);
        BlockPos postR = util.grid().at(3, 1, 3);
        BlockPos wallR = util.grid().at(3, 2, 3);
        BlockPos right = util.grid().at(3, 2, 2);
        BlockPos tankLow = util.grid().at(6, 1, 2);
        BlockPos tankHigh = util.grid().at(6, 2, 2);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(CreateStatsPonderScenes.select(util, postL, wallL), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(left), Direction.SOUTH);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(left, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModBlocks.MINI_DISPLAY.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Mini Display is a compact wall screen that prints a live readout", left, PonderPalette.BLUE, 95);

        scene.world().modifyBlock(left, s -> s.setValue(MiniDisplayBlock.ACTIVE, true), false);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "Aim it at a source and the screen lights up with the value", left, PonderPalette.GREEN, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, tankLow, tankHigh), Direction.WEST);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.WHITE, tankHigh, CreateStatsPonderScenes.select(util, tankLow, tankHigh), 80);
        CreateStatsPonderScenes.say(scene, util, "It can track items, fluids, boiler heat, or kinetic speed and stress", tankHigh, PonderPalette.WHITE, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, postR, wallR), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(right), Direction.SOUTH);
        scene.idle(8);
        CreateStatsPonderScenes.say(scene, util, "Place a second screen right beside the first", right, PonderPalette.BLUE, 80);

        scene.world().modifyBlock(left, s -> s.setValue(MiniDisplayBlock.SEGMENT, MiniDisplayBlock.Segment.LEFT)
                        .setValue(MiniDisplayBlock.ACTIVE, true), false);
        scene.world().modifyBlock(right, s -> s.setValue(MiniDisplayBlock.SEGMENT, MiniDisplayBlock.Segment.RIGHT)
                        .setValue(MiniDisplayBlock.ACTIVE, true), false);
        scene.idle(6);
        scene.overlay().showOutline(PonderPalette.OUTPUT, left, CreateStatsPonderScenes.select(util, left, right), 90);
        CreateStatsPonderScenes.say(scene, util, "The pair fuses into a single wide screen with room for longer labels and values", left, PonderPalette.OUTPUT, 95);

        scene.overlay().showControls(util.vector().blockSurface(left, Direction.SOUTH), Pointing.UP, 50)
                        .rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click to choose the source and metric, or link it to a distant machine", left, PonderPalette.BLUE, 95);
        scene.idle(6);

        CreateStatsPonderScenes.say(scene, util, "Tidy at-a-glance readouts you can scatter across a whole factory wall", left, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(left);
    }
}
