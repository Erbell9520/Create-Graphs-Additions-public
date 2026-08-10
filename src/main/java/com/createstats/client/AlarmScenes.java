package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.createstats.block.StatsAlarmBlock;
import com.createstats.block.TableAlarmClockBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Alarm block ponders, split out of CreateStatsPonderScenes (topic-class layout). */
public class AlarmScenes {

    public static void statsAlarmGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("stats_alarm_threshold_alert", "Stats Alarm: threshold alert");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos alarm = util.grid().at(4, 2, 4);
        BlockPos alarmPillar = util.grid().at(4, 1, 4);
        BlockPos lamp = util.grid().at(5, 2, 4);
        BlockPos lampPillar = util.grid().at(5, 1, 4);
        Selection alarmSel = CreateStatsPonderScenes.select(util, alarm, alarmPillar);
        Selection lampSel = CreateStatsPonderScenes.select(util, lamp, lampPillar);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(alarmSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(alarm, Direction.SOUTH), Pointing.RIGHT, 60)
                        .withItem(ModBlocks.STATS_ALARM.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Stats Alarm watches the channels on a nearby Stats Display Panel and reacts the moment one of them goes into alert", alarm, PonderPalette.BLUE, 100);

        scene.overlay().showControls(util.vector().blockSurface(alarm, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModItems.SMALL_SIGNAL_ANTENNA.asStack());
        CreateStatsPonderScenes.say(scene, util, "It only needs to sit near the panel, add an antenna to stretch its watch range across larger builds", alarm, PonderPalette.INPUT, 110);
        scene.idle(8);

        scene.world().showSection(lampSel, Direction.DOWN);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "Its side face is a redstone source, wire the output to lamps, doors or contraptions", alarm, PonderPalette.OUTPUT, 110);
        scene.idle(8);

        scene.world().modifyBlock(alarm, s -> s.setValue(StatsAlarmBlock.ACTIVE, true), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.effects().indicateRedstone(alarm);
        CreateStatsPonderScenes.say(scene, util, "When a watched channel trips its limit it lights up, sounds an alarm and emits a full redstone signal", alarm, PonderPalette.RED, 120);
        scene.idle(10);

        scene.world().modifyBlock(alarm, s -> s.setValue(StatsAlarmBlock.ACTIVE, false), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, false), false);
        scene.effects().indicateSuccess(alarm);
        CreateStatsPonderScenes.say(scene, util, "As soon as that channel leaves its alert range the alarm clears itself automatically", alarm, PonderPalette.GREEN, 110);
        scene.idle(8);

        CreateStatsPonderScenes.say(scene, util, "Pick its alarm sound, particles and whether it drives redstone; the above or below limit itself is set on the panel's channel", alarm, PonderPalette.WHITE, 100);
        scene.effects().indicateSuccess(alarm);
        scene.idle(12);
    }

    public static void tableAlarmClockGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("table_alarm_clock_alarm_timer", "Table Alarm Clock: alarm & timer");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos clock = util.grid().at(4, 2, 4);
        BlockPos clockBase = util.grid().at(4, 1, 4);
        BlockPos lamp = util.grid().at(5, 2, 4);
        BlockPos lampBase = util.grid().at(5, 1, 4);
        Selection clockSel = CreateStatsPonderScenes.select(util, clock, clockBase);
        Selection lampSel = CreateStatsPonderScenes.select(util, lamp, lampBase);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(clockSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(clock, Direction.SOUTH), Pointing.RIGHT, 60)
                        .withItem(ModBlocks.TABLE_ALARM_CLOCK.asStack());
        CreateStatsPonderScenes.say(scene, util, "A desk clock for your base: alarm, countdown timer, stopwatch and a shared calendar", clock, PonderPalette.BLUE, 100);

        scene.overlay().showControls(util.vector().blockSurface(clock, Direction.SOUTH), Pointing.DOWN, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click to open it and set an alarm time, a countdown, or start the stopwatch", clock, PonderPalette.INPUT, 110);
        scene.idle(8);

        scene.world().showSection(lampSel, Direction.DOWN);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "Its side face is a redstone source, hook it up to wake your machines on schedule", clock, PonderPalette.OUTPUT, 110);
        scene.idle(8);

        scene.world().modifyBlock(clock, s -> s.setValue(TableAlarmClockBlock.ACTIVE, true), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.effects().indicateRedstone(clock);
        CreateStatsPonderScenes.say(scene, util, "When the time comes it rings, lights up and powers its redstone output until you stop it", clock, PonderPalette.RED, 120);
        scene.idle(10);

        scene.overlay().showControls(util.vector().blockSurface(clock, Direction.SOUTH), Pointing.DOWN, 50).rightClick();
        scene.world().modifyBlock(clock, s -> s.setValue(TableAlarmClockBlock.ACTIVE, false), false);
        scene.world().modifyBlock(lamp, s -> s.setValue(BlockStateProperties.LIT, false), false);
        scene.effects().indicateSuccess(clock);
        CreateStatsPonderScenes.say(scene, util, "Sneak right-click to silence it, the ring and the redstone signal switch off", clock, PonderPalette.GREEN, 110);
        scene.idle(8);

        CreateStatsPonderScenes.say(scene, util, "Switch to Calendar mode to plan dated events and invite other players by name; the schedule is saved on the server", clock, PonderPalette.WHITE, 100);
        scene.effects().indicateSuccess(clock);
    }
}
