package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.RouteGuidance;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Pathfinder + Path Scanner ponders and NBT helpers, split out of CreateStatsPonderScenes. */
public class NavigationScenes {

    private static void pathfinderScannerLink(CompoundTag tag) {
        tag.putLong("LinkedScannerPos", new BlockPos(40, 64, 40).asLong());
        tag.putUUID("LinkedScannerId", UUID.randomUUID());
        tag.putLong("LinkedLastSeen", 1_000_000L);
        tag.putInt("LinkedExpected", 25);
        tag.putInt("LinkedLoaded", 25);
    }

    private static CompoundTag pathfinderRouteTag() {
        return new RouteGuidance(UUID.randomUUID(), "Depot Run", "scanner", 0.92f,
                        List.of(new Vec3(4, 2, 4), new Vec3(5, 2, 5), new Vec3(6, 2, 6)),
                        List.of(), List.of(), 0.0, 0L, 0).save();
    }

    private static void injectPathfinderThinking(CompoundTag tag) {
        pathfinderScannerLink(tag);
        tag.putInt("LinkedQueue", 3);
        tag.putString("PlannerStatus", "Planning route");
        tag.putInt("PlannerProgress", 55);
        tag.putInt("PlannerExpanded", 2400);
    }

    private static void injectPathfinderRouteReady(CompoundTag tag) {
        pathfinderScannerLink(tag);
        tag.put("ActiveGuidance", pathfinderRouteTag());
        tag.putString("PlannerStatus", "Route ready");
        tag.putInt("PlannerProgress", 100);
    }

    private static void injectPathfinderDriving(CompoundTag tag) {
        pathfinderScannerLink(tag);
        tag.put("ActiveGuidance", pathfinderRouteTag());
        tag.putString("PlannerStatus", "Route ready");
        tag.putLong("LinkedFccPos", new BlockPos(40, 64, 36).asLong());
        tag.putLong("LinkedFccSeen", 1_000_000L);
        tag.putString("LinkedFccStatus", "Driving route");
    }

    private static void injectPathfinderNoRoute(CompoundTag tag) {
        pathfinderScannerLink(tag);
        tag.putString("PlannerStatus", "No route within budget");
    }

    public static void pathfinderComputerGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pathfinder_computer_route_planner", "Pathfinder Computer: route planner");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos pc = util.grid().at(4, 2, 4);
        BlockPos pillar = util.grid().at(4, 1, 4);
        Selection pcSel = CreateStatsPonderScenes.select(util, pc);
        Selection all = CreateStatsPonderScenes.select(util, pc, pillar);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(all, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(pc, Direction.SOUTH), Pointing.RIGHT, 60).withItem(ModBlocks.PATHFINDER_COMPUTER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Pathfinder Computer plans a safe route across the world for a Create vehicle", pc, PonderPalette.BLUE, 100);

        scene.world().modifyBlockEntityNBT(pcSel, PathfinderComputerBE.class, NavigationScenes::injectPathfinderThinking);
        scene.overlay().showControls(util.vector().blockSurface(pc, Direction.SOUTH), Pointing.UP, 50).withItem(ModBlocks.PATH_SCANNER.asStack());
        CreateStatsPonderScenes.say(scene, util, "Link a Path Scanner and it reads the terrain, the input port lights up and the planner gets to work", pc, PonderPalette.INPUT, 110);
        scene.idle(8);

        CreateStatsPonderScenes.say(scene, util, "It explores thousands of cells, weighing slopes, gaps and hazards to find the cheapest safe path", pc, PonderPalette.MEDIUM, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(pcSel, PathfinderComputerBE.class, NavigationScenes::injectPathfinderRouteReady);
        scene.effects().indicateSuccess(pc);
        CreateStatsPonderScenes.say(scene, util, "When a route is found the screen draws it with a confidence score and total distance", pc, PonderPalette.GREEN, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(pcSel, PathfinderComputerBE.class, NavigationScenes::injectPathfinderDriving);
        CreateStatsPonderScenes.say(scene, util, "Link a Flight Control Computer and the output port goes green, the route is handed over to be driven", pc, PonderPalette.OUTPUT, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(pcSel, PathfinderComputerBE.class, NavigationScenes::injectPathfinderNoRoute);
        scene.effects().indicateRedstone(pc);
        CreateStatsPonderScenes.say(scene, util, "If the terrain is blocked or the search budget runs out it shows a broken route and keeps the last good one", pc, PonderPalette.RED, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(pcSel, PathfinderComputerBE.class, NavigationScenes::injectPathfinderDriving);
        scene.effects().indicateSuccess(pc);
    }

    private static void putScannerCache(CompoundTag tag, int loadedChunks, int unknownChunks, int passable, int marginal, int blocked) {
        ListTag cache = new ListTag();
        int total = loadedChunks + unknownChunks;
        for (int i = 0; i < total; i++) {
                boolean loaded = i < loadedChunks;
                int p = (loaded && i == 0) ? passable : 0;
                int m = (loaded && i == 0) ? marginal : 0;
                int b = (loaded && i == 0) ? blocked : 0;
                ChunkScanCacheEntry entry = new ChunkScanCacheEntry(i, 0, 1_000_000L, 0L, loaded, 0, new byte[256], new byte[0], p, m, b, 0);
                cache.add(entry.save());
        }
        tag.put("Cache", cache);
    }

    private static void injectScannerCoverage(CompoundTag tag) {
        tag.putInt("ExpectedChunks", 25);
        putScannerCache(tag, 25, 0, 1840, 260, 140);
    }

    private static void injectScannerUnknown(CompoundTag tag) {
        tag.putInt("ExpectedChunks", 25);
        putScannerCache(tag, 19, 6, 1410, 210, 120);
    }

    public static void pathScannerGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("path_scanner_terrain_scan", "Path Scanner: mapping the terrain");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos scanner = util.grid().at(4, 2, 4);
        BlockPos pillar = util.grid().at(4, 1, 4);
        Selection scannerSel = CreateStatsPonderScenes.select(util, scanner);
        Selection core = CreateStatsPonderScenes.select(util, scanner, pillar);
        Selection terrain = CreateStatsPonderScenes.select(util,
                        util.grid().at(2, 1, 6), util.grid().at(3, 1, 6), util.grid().at(4, 1, 6),
                        util.grid().at(5, 1, 6), util.grid().at(6, 1, 6),
                        util.grid().at(2, 1, 7), util.grid().at(3, 1, 7), util.grid().at(4, 1, 7),
                        util.grid().at(5, 1, 7), util.grid().at(6, 1, 7), util.grid().at(6, 2, 7));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(core, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(scanner, Direction.SOUTH), Pointing.RIGHT, 60).withItem(ModBlocks.PATH_SCANNER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Path Scanner turns the world around it into a navigation grid for vehicles", scanner, PonderPalette.BLUE, 100);

        scene.world().showSection(terrain, Direction.UP);
        scene.idle(6);
        scene.overlay().showOutline(PonderPalette.INPUT, terrain, terrain, 80);
        CreateStatsPonderScenes.say(scene, util, "It reads the chunks in front of it, classifying every cell as passable, marginal or blocked", scanner, PonderPalette.INPUT, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(scannerSel, PathScannerBE.class, NavigationScenes::injectScannerCoverage);
        scene.effects().indicateSuccess(scanner);
        CreateStatsPonderScenes.say(scene, util, "Once a region is fully loaded the lens glows and the coverage bar fills, the map is ready", scanner, PonderPalette.GREEN, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(scannerSel, PathScannerBE.class, NavigationScenes::injectScannerUnknown);
        scene.effects().indicateRedstone(scanner);
        CreateStatsPonderScenes.say(scene, util, "Cells it cannot see yet stay unknown, the amber bar shows the gaps until those chunks load", scanner, PonderPalette.MEDIUM, 110);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(scannerSel, PathScannerBE.class, NavigationScenes::injectScannerCoverage);
        scene.overlay().showControls(util.vector().blockSurface(scanner, Direction.SOUTH), Pointing.UP, 50).withItem(ModBlocks.PATHFINDER_COMPUTER.asStack());
        CreateStatsPonderScenes.say(scene, util, "Link it to a Pathfinder Computer and this grid becomes the map the route planner searches", scanner, PonderPalette.OUTPUT, 110);
        scene.idle(8);

        CreateStatsPonderScenes.say(scene, util, "Tune the scan radius and chunk budget to balance coverage against performance", scanner, PonderPalette.WHITE, 100);
        scene.effects().indicateSuccess(scanner);
    }
}
