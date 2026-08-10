package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.block.HolographicProjectorBlock;
import com.createstats.block.BattleScreenDisplayBlock;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;

/** Holographic Projector / Battle Screen / Damage Observer ponders, split out of CreateStatsPonderScenes. */
public class SensorConsoleScenes {

    public static void holographicProjectorGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("holographic_projector_data_hologram", "Holographic Projector: live data hologram");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.6f);
        scene.setSceneOffsetY(-1);

        BlockPos projector = util.grid().at(4, 1, 4);
        BlockPos chest = util.grid().at(2, 1, 4);
        BlockPos tankLow = util.grid().at(6, 1, 4);
        BlockPos tankHigh = util.grid().at(6, 2, 4);
        Selection projSel = util.select().position(projector);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(projSel, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(projector, Direction.UP), Pointing.DOWN, 50).withItem(ModBlocks.HOLOGRAPHIC_PROJECTOR.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Holographic Projector beams a floating 3D data graph into the air above it", projector, PonderPalette.BLUE, 95);

        scene.world().showSection(util.select().position(chest), Direction.EAST);
        scene.world().showSection(CreateStatsPonderScenes.select(util, tankLow, tankHigh), Direction.WEST);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.INPUT, chest, util.select().position(chest), 80);
        scene.overlay().showOutline(PonderPalette.INPUT, tankHigh, CreateStatsPonderScenes.select(util, tankLow, tankHigh), 80);
        CreateStatsPonderScenes.say(scene, util, "It scans neighbouring machines automatically, or you can link distant sources and display links", projector, PonderPalette.INPUT, 95);

        scene.world().modifyBlock(projector, s -> s.setValue(HolographicProjectorBlock.ACTIVE, true), false);
        scene.world().modifyBlockEntityNBT(projSel, HolographicProjectorBE.class, tag -> injectHologramChannel(tag, "net_stress", "Stress %", "STRESS_PERCENT", new float[] { 18f, 30f, 42f, 38f, 50f, 58f }, "58%"));
        scene.idle(18);
        CreateStatsPonderScenes.say(scene, util, "Live readings stream in and a translucent hologram blooms above the projector", projector, PonderPalette.GREEN, 95);

        scene.world().modifyBlockEntityNBT(projSel, HolographicProjectorBE.class, tag -> injectHologramChannel(tag, "net_stress", "Stress %", "STRESS_PERCENT", new float[] { 18f, 30f, 42f, 38f, 50f, 58f, 54f, 66f, 72f, 68f, 80f, 86f, 82f, 90f }, "90%"));
        scene.idle(20);
        CreateStatsPonderScenes.say(scene, util, "Every sample is plotted over time, so rising or falling trends are obvious at a glance", projector, PonderPalette.OUTPUT, 95);

        scene.overlay().showControls(util.vector().blockSurface(projector, Direction.UP), Pointing.DOWN, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click for settings: switch between line and bar styles, colour themes, height and spin speed", projector, PonderPalette.BLUE, 95);
        scene.idle(8);

        scene.overlay().showOutline(PonderPalette.RED, projector, projSel, 80);
        CreateStatsPonderScenes.say(scene, util, "It can also fire a redstone signal whenever a tracked value crosses a threshold you set", projector, PonderPalette.RED, 95);

        CreateStatsPonderScenes.say(scene, util, "A dramatic, readable overview of your whole factory floating in mid-air", projector, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(projector);
    }

    private static void injectHologramChannel(CompoundTag tag, String id, String label, String metric, float[] values, String lastText) {
        ListTag channels = new ListTag();
        CompoundTag channel = new CompoundTag();
        channel.putString("Id", id);
        channel.putString("Label", label);
        CompoundTag store = new CompoundTag();
        CompoundTag buffer = new CompoundTag();
        buffer.putInt("Capacity", Math.max(1, values.length));
        ListTag data = new ListTag();
        for (float v : values) data.add(FloatTag.valueOf(v));
        buffer.put("Data", data);
        store.put("Buffer", buffer);
        store.putString("MetricType", metric);
        channel.put("Store", store);
        channel.putString("LastText", lastText);
        channels.add(channel);
        tag.put("Channels", channels);
        tag.putBoolean("HasData", true);
        tag.putBoolean("StockpileMetricsV2", true);
    }

    public static void battleScreenDisplayGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("battle_screen_display_tactical_hologram", "Battle Screen: tactical command hologram");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.6f);
        scene.setSceneOffsetY(-1);

        BlockPos console = util.grid().at(4, 1, 4);
        Selection consoleSel = util.select().position(console);
        Selection hull = CreateStatsPonderScenes.select(util, util.grid().at(3, 1, 1), util.grid().at(4, 1, 1), util.grid().at(5, 1, 1), util.grid().at(4, 2, 1));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(consoleSel, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(console, Direction.UP), Pointing.DOWN, 50).withItem(ModBlocks.BATTLE_SCREEN_DISPLAY.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Battle Screen is a tactical command console for your warships", console, PonderPalette.BLUE, 95);

        scene.world().showSection(hull, Direction.SOUTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.INPUT, util.grid().at(4, 2, 1), hull, 80);
        CreateStatsPonderScenes.say(scene, util, "Damage Observers watch each section of the hull and its weapons, then feed this console", console, PonderPalette.INPUT, 95);

        scene.world().modifyBlock(console, s -> s.setValue(BattleScreenDisplayBlock.ACTIVE, true), false);
        scene.world().modifyBlockEntityNBT(consoleSel, BattleScreenDisplayBE.class, tag -> injectBattleObservers(tag, 5, 100.0, 0));
        scene.idle(20);
        CreateStatsPonderScenes.say(scene, util, "Link them and a live battle hologram rises, green rings and pips mean all systems nominal", console, PonderPalette.GREEN, 95);

        scene.world().modifyBlockEntityNBT(consoleSel, BattleScreenDisplayBE.class, tag -> injectBattleObservers(tag, 5, 62.0, 3));
        scene.idle(18);
        CreateStatsPonderScenes.say(scene, util, "Take a hit and the hologram flares amber as sections report missing armour", console, PonderPalette.OUTPUT, 95);

        scene.world().modifyBlockEntityNBT(consoleSel, BattleScreenDisplayBE.class, tag -> injectBattleObservers(tag, 5, 28.0, 9));
        scene.idle(18);
        CreateStatsPonderScenes.say(scene, util, "Critical losses turn it red, so you read your fleet's condition at a glance", console, PonderPalette.RED, 95);

        scene.overlay().showControls(util.vector().blockSurface(console, Direction.UP), Pointing.DOWN, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click to open the tactical screen: aim and fire autocannons, enable auto-aim, or bind a linked controller", console, PonderPalette.BLUE, 100);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(consoleSel, BattleScreenDisplayBE.class, tag -> injectBattleObservers(tag, 5, 100.0, 0));
        scene.idle(12);
        CreateStatsPonderScenes.say(scene, util, "Repair the hull and the hologram returns to a calm, healthy green", console, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(console);
    }

    private static void injectBattleObservers(CompoundTag tag, int count, double health, int missingEach) {
        ListTag list = new ListTag();
        for (int i = 0; i < count; i++) {
                CompoundTag e = new CompoundTag();
                e.putString("Name", "Section " + (i + 1));
                e.putLong("SourcePos", new BlockPos(20 + i, 64, 20).asLong());
                e.putLong("Anchor", new BlockPos(20 + i, 64, 22).asLong());
                e.putInt("Diameter", 5);
                e.putInt("Volume", 125);
                e.putInt("Distance", 6);
                e.putString("Dimension", "minecraft:overworld");
                e.putLong("LastSeenTick", 1L);
                e.putDouble("HealthPercent", health);
                e.putInt("WatchedBlocks", 100);
                e.putInt("BaselineBlocks", 100);
                e.putInt("PresentBaselineBlocks", (int) Math.round(health));
                e.putInt("MissingBlocks", missingEach);
                list.add(e);
        }
        tag.put("LinkedObservers", list);
    }

    public static void damageObserverGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("damage_observer_hull_sentry", "Damage Observer: structural sentry");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.setSceneOffsetY(-1);

        BlockPos observer = util.grid().at(4, 1, 4);
        Selection observerSel = util.select().position(observer);
        Selection hullSel = CreateStatsPonderScenes.select(util,
                        util.grid().at(3, 1, 1), util.grid().at(4, 1, 1), util.grid().at(5, 1, 1),
                        util.grid().at(3, 2, 1), util.grid().at(4, 2, 1), util.grid().at(5, 2, 1),
                        util.grid().at(3, 3, 1), util.grid().at(4, 3, 1), util.grid().at(5, 3, 1));
        BlockPos[] damaged = new BlockPos[]{
                        util.grid().at(3, 1, 1), util.grid().at(5, 1, 1),
                        util.grid().at(3, 2, 1), util.grid().at(5, 2, 1),
                        util.grid().at(3, 3, 1), util.grid().at(5, 3, 1)
        };

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(observerSel, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(observer, Direction.UP), Pointing.DOWN, 50).withItem(ModBlocks.DAMAGE_OBSERVER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Damage Observer is a sentry that watches a structure for damage", observer, PonderPalette.BLUE, 90);

        scene.world().showSection(hullSel, Direction.SOUTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.INPUT, util.grid().at(4, 2, 1), hullSel, 80);
        CreateStatsPonderScenes.say(scene, util, "Point it at a hull section and it scans the whole volume in front of it", observer, PonderPalette.INPUT, 90);

        scene.world().modifyBlockEntityNBT(observerSel, DamageObserverBE.class, tag -> injectObserverScan(tag, 9, 9, 0));
        scene.idle(16);
        CreateStatsPonderScenes.say(scene, util, "Right-click to calibrate: it memorises every block as a healthy baseline", observer, PonderPalette.GREEN, 90);

        for (BlockPos pos : damaged) {
                scene.world().modifyBlock(pos, s -> Blocks.AIR.defaultBlockState(), false);
                scene.effects().indicateRedstone(pos);
        }
        scene.world().modifyBlockEntityNBT(observerSel, DamageObserverBE.class, tag -> injectObserverScan(tag, 9, 3, 6));
        scene.idle(18);
        CreateStatsPonderScenes.say(scene, util, "When blocks are blasted away it reports the missing count instantly and its lens turns red", observer, PonderPalette.RED, 95);

        scene.overlay().showOutline(PonderPalette.OUTPUT, observer, observerSel, 70);
        CreateStatsPonderScenes.say(scene, util, "It feeds live health and damage to Battle Screens and to displays through a display link", observer, PonderPalette.OUTPUT, 100);
        scene.idle(8);

        for (BlockPos pos : damaged) {
                scene.world().modifyBlock(pos, s -> Blocks.IRON_BLOCK.defaultBlockState(), false);
        }
        scene.world().modifyBlockEntityNBT(observerSel, DamageObserverBE.class, tag -> injectObserverScan(tag, 9, 9, 0));
        scene.idle(12);
        CreateStatsPonderScenes.say(scene, util, "Repair the structure and the baseline is met again, back to a healthy green", observer, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(observer);
    }

    private static void injectObserverScan(CompoundTag tag, int baseline, int present, int missing) {
        tag.putBoolean("BaselineCalibrated", true);
        tag.putInt("WatchedBlocks", baseline);
        tag.putInt("BaselineBlocks", baseline);
        tag.putInt("PresentBaselineBlocks", present);
        tag.putInt("MissingBlocks", missing);
    }
}
