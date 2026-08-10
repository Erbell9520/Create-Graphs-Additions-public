package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.createstats.block.StatusTowerLightBlock;
import com.createstats.block.DataDialBlock;
import com.createstats.block.MiniDisplayBlock;
import com.createstats.block.HolographicProjectorBlock;
import com.createstats.block.BattleScreenDisplayBlock;
import com.createstats.block.DamageObserverBlock;
import com.createstats.blockentity.DataDialBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.CalculatorBE;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.flight.block.FlightControlComputerBlock;
import com.createstats.block.StatsAlarmBlock;
import com.createstats.block.TableAlarmClockBlock;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.model.RouteGuidance;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
public class CreateStatsPonderScenes {

        // ===================================================================
        // Chapter 1 of 8 - Getting Started: place, merge, bind a source, open
        // ===================================================================
        public static void statsDisplayGettingStarted(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_getting_started", "Stats Display: Getting Started");
                BlockPos controller = util.grid().at(4, 1, 6);
                BlockPos panelTop = controller.above();
                BlockPos panelRight = util.grid().at(5, 1, 6);
                BlockPos panelTopRight = panelRight.above();
                BlockPos chest = util.grid().at(1, 1, 2);
                BlockPos link = chest.above();

                showBase(scene, util);
                scene.idle(5);
                scene.world().showSection(util.select().position(controller), Direction.NORTH);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 50)
                                .withItem(ModBlocks.STATS_DISPLAY_PANEL.asStack());
                say(scene, util, "The Stats Display is a wall panel that turns live machine data into a readout", controller, PonderPalette.BLUE, 85);

                scene.world().showSection(select(util, panelTop, panelRight, panelTopRight), Direction.NORTH);
                scene.idle(10);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, controller, new AABB(controller).minmax(new AABB(panelTopRight)), 90);
                say(scene, util, "Place panels with the same facing side by side and they merge into one big screen", panelTopRight, PonderPalette.GREEN, 90);
                scene.effects().indicateSuccess(controller);
                scene.idle(12);

                scene.rotateCameraY(-25);
                scene.world().showSection(util.select().position(chest), Direction.DOWN);
                scene.idle(12);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 45)
                                .withItem(AllBlocks.DISPLAY_LINK.asStack())
                                .rightClick();
                scene.idle(24);
                scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 50)
                                .withItem(AllBlocks.DISPLAY_LINK.asStack())
                                .rightClick();
                scene.idle(8);
                scene.world().showSection(util.select().position(link), Direction.UP);
                scene.world().flashDisplayLink(link);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, link, new AABB(chest).minmax(new AABB(controller.above())), 85);
                say(scene, util, "A Display Link carries data from a source to the panel. Click the panel, then the source", link, PonderPalette.OUTPUT, 100);
                scene.rotateCameraY(25);

                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 50)
                                .rightClick();
                say(scene, util, "Right-click the panel to open Home; sneak-right-click cycles the small in-world view", controller, PonderPalette.BLUE, 90);

                scene.overlay().showOutline(PonderPalette.RED, chest, util.select().position(chest), 75);
                say(scene, util, "Blank panel? Make sure the link sits on the source, then pick Stats Link in that source's menu", chest, PonderPalette.RED, 95);
                scene.effects().indicateSuccess(controller);
                scene.idle(12);
        }

        // ===================================================================
        // Chapter 2 of 8 - Reading Your Data: sources, channels, graphs, modes
        // ===================================================================
        public static void statsDisplayReadingData(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_reading_data", "Reading Your Data");
                BlockPos controller = util.grid().at(4, 1, 6);
                BlockPos chest = util.grid().at(1, 1, 2);
                BlockPos tank = util.grid().at(3, 1, 2);
                BlockPos stress = util.grid().at(5, 1, 2);
                BlockPos speed = util.grid().at(6, 1, 2);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(chest), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(tank), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(stress), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(speed), Direction.DOWN);
                scene.world().setKineticSpeed(select(util, stress, speed), 48);
                scene.effects().rotationSpeedIndicator(speed);
                scene.idle(8);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 45)
                                .rightClick();
                say(scene, util, "Open Home: every linked block becomes a source card, grouped by family", controller, PonderPalette.BLUE, 85);

                scene.overlay().showOutlineWithText(select(util, chest, tank, stress, speed), 95)
                                .text("Storage, fluids, and kinetics each expose their own channels")
                                .colored(PonderPalette.GREEN)
                                .pointAt(util.vector().centerOf(tank))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);

                scene.world().createItemEntity(util.vector().topOf(controller).add(-0.15, 0.40, 0.0), Vec3.ZERO, new ItemStack(Items.REDSTONE));
                scene.world().createItemEntity(util.vector().topOf(controller).add(0.15, 0.60, 0.0), Vec3.ZERO, new ItemStack(Items.CLOCK));
                say(scene, util, "A channel is one metric from one source: RPM, fill, item count, heat, or status", stress, PonderPalette.OUTPUT, 90);

                scene.overlay().showOutlineWithText(select(util, controller, controller.above()), 95)
                                .text("Line, bar, gauge, numeric, and capacity views read the same channel differently")
                                .colored(PonderPalette.BLUE)
                                .pointAt(util.vector().blockSurface(controller, Direction.NORTH))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);

                say(scene, util, "Output mode sets the goal: Single Channel, Dashboard, Specialized diagram, or Analog", controller, PonderPalette.GREEN, 95);
                scene.overlay().showOutline(PonderPalette.RED, tank, util.select().position(tank), 75);
                say(scene, util, "Use time range and autoscale for trends; hide noisy channels before you build a board", tank, PonderPalette.RED, 90);
                scene.effects().indicateSuccess(controller);
                scene.idle(12);
        }

        // ===================================================================
        // Chapter 3 of 8 - Dashboards & System Pages
        // ===================================================================
        public static void statsDisplayDashboards(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_dashboards", "Dashboards & System Pages");
                BlockPos lowerLeft = util.grid().at(4, 1, 6);
                BlockPos lowerRight = util.grid().at(5, 1, 6);
                BlockPos upperLeft = lowerLeft.above();
                BlockPos upperRight = lowerRight.above();
                BlockPos source = util.grid().at(5, 1, 2);
                BlockPos chest = util.grid().at(1, 1, 2);
                BlockPos factory = util.grid().at(7, 1, 2);
                BlockPos dispatch = util.grid().at(5, 1, 4);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(source), Direction.DOWN);
                scene.idle(8);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, lowerLeft, new AABB(lowerLeft).minmax(new AABB(upperRight)), 85);
                say(scene, util, "A Dashboard splits one large panel into operator cards from a single focused source", source, PonderPalette.BLUE, 90);

                scene.overlay().showOutline(PonderPalette.GREEN, upperLeft, util.select().position(upperLeft), 85);
                scene.overlay().showOutline(PonderPalette.OUTPUT, upperRight, util.select().position(upperRight), 85);
                scene.overlay().showOutline(PonderPalette.RED, lowerRight, util.select().position(lowerRight), 85);
                say(scene, util, "Promote one main channel, then let supporting cards fill the rest of the wall", upperRight, PonderPalette.GREEN, 90);
                say(scene, util, "Auto Dashboard arranges the cards for you; manual Dashboard is for tuned control rooms", lowerRight, PonderPalette.OUTPUT, 90);

                scene.rotateCameraY(-20);
                scene.world().showSection(select(util, chest, factory, dispatch), Direction.DOWN);
                scene.idle(10);
                scene.overlay().showOutline(PonderPalette.GREEN, chest, util.select().position(chest), 85);
                scene.overlay().showOutline(PonderPalette.RED, factory, util.select().position(factory), 85);
                scene.overlay().showOutline(PonderPalette.OUTPUT, dispatch, util.select().position(dispatch), 85);
                scene.world().createItemEntity(util.vector().topOf(chest).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.IRON_INGOT));
                scene.world().createItemEntity(util.vector().topOf(factory).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.COPPER_INGOT));
                say(scene, util, "Some data reads best as a page: Inventory Status, Factory Status, Topology, and Train Map", factory, PonderPalette.BLUE, 100);
                scene.rotateCameraY(20);
                say(scene, util, "When a family has a built-in board, open that page before hand-tuning graph settings", lowerLeft, PonderPalette.GREEN, 85);
                scene.effects().indicateSuccess(lowerLeft);
                scene.idle(15);
        }

        // ===================================================================
        // Chapter 4 of 8 - Create Machine Telemetry
        // ===================================================================
        public static void statsDisplayCreateTelemetry(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_create_telemetry", "Create Machine Telemetry");
                BlockPos stress = util.grid().at(5, 1, 2);
                BlockPos speed = util.grid().at(6, 1, 2);
                BlockPos chest = util.grid().at(1, 1, 2);
                BlockPos vault = util.grid().at(2, 1, 2);
                BlockPos tank = util.grid().at(3, 1, 2);
                BlockPos safety = util.grid().at(1, 1, 5);
                BlockPos transmission = util.grid().at(2, 1, 5);
                BlockPos control = util.grid().at(3, 1, 5);
                BlockPos balancer = util.grid().at(4, 1, 5);
                BlockPos mini = util.grid().at(5, 1, 5);
                BlockPos factory = util.grid().at(7, 1, 2);

                showBaseAndPanel(scene, util);
                scene.world().showSection(select(util, stress, speed), Direction.DOWN);
                scene.world().setKineticSpeed(select(util, stress, speed), 64);
                scene.effects().rotationSpeedIndicator(speed);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.BLUE, stress, util.select().position(stress), 85);
                scene.overlay().showOutline(PonderPalette.GREEN, speed, util.select().position(speed), 85);
                say(scene, util, "Stressometers and Speedometers become kinetic cards with live RPM and stress capacity", stress, PonderPalette.BLUE, 90);

                scene.world().showSection(select(util, chest, vault, tank), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutlineWithText(select(util, chest, vault, tank), 90)
                                .text("Chests, vaults, belts, and tanks turn into fill and capacity readouts")
                                .colored(PonderPalette.OUTPUT)
                                .pointAt(util.vector().centerOf(vault))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(95);

                scene.rotateCameraY(-25);
                scene.world().showSection(select(util, safety, transmission, control, balancer, mini, factory), Direction.DOWN);
                scene.idle(10);
                scene.overlay().showOutlineWithText(select(util, safety, transmission, control), 95)
                                .text("Safety, Auto-Transmission, and Control Clutches report why a branch is held or released")
                                .colored(PonderPalette.GREEN)
                                .pointAt(util.vector().centerOf(transmission))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);
                scene.overlay().showOutline(PonderPalette.BLUE, balancer, util.select().position(balancer), 85);
                scene.overlay().showOutline(PonderPalette.RED, mini, util.select().position(mini), 85);
                say(scene, util, "Load Balancers and Mini Displays can become control cards, not just charts", mini, PonderPalette.OUTPUT, 90);
                scene.rotateCameraY(25);
                say(scene, util, "Reach for Factory Controls and Factory Status when the question is operational", factory, PonderPalette.BLUE, 85);
                scene.effects().indicateSuccess(factory);
                scene.idle(12);
        }

        // ===================================================================
        // Chapter 5 of 8 - Diesel Telemetry (engines, fuel, distillation)
        // ===================================================================
        public static void statsDisplayDieselTelemetry(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_diesel_telemetry", "Diesel Telemetry");
                BlockPos engine = util.grid().at(2, 1, 4);
                BlockPos reserve = util.grid().at(3, 1, 2);
                BlockPos towerBase = util.grid().at(3, 1, 4);
                BlockPos towerTop = towerBase.above();
                BlockPos process = util.grid().at(6, 1, 4);
                BlockPos controller = util.grid().at(4, 1, 6);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(engine), Direction.DOWN);
                scene.idle(6);
                scene.world().showSection(util.select().position(reserve), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutlineWithText(util.select().position(engine), 90)
                                .text("Bind a Diesel Engine: single, modular, huge, or a whole engine group")
                                .colored(PonderPalette.BLUE)
                                .pointAt(util.vector().centerOf(engine))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(95);
                scene.overlay().showOutlineWithText(util.select().position(reserve), 90)
                                .text("Bind the fuel tank too: the engine page shows NO LINK without its reserve")
                                .colored(PonderPalette.OUTPUT)
                                .pointAt(util.vector().centerOf(reserve))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(95);

                scene.rotateCameraY(-20);
                scene.world().showSection(select(util, towerBase, towerTop, process), Direction.DOWN);
                scene.idle(10);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, towerBase, new AABB(towerBase).minmax(new AABB(towerTop)), 90);
                scene.world().createItemEntity(util.vector().topOf(reserve).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.LAVA_BUCKET));
                say(scene, util, "Distillation pages show crude input, heat, and the diesel or gasoline output lanes", towerTop, PonderPalette.GREEN, 95);
                scene.overlay().showOutline(PonderPalette.RED, process, util.select().position(process), 85);
                scene.world().createItemEntity(util.vector().topOf(towerTop).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.BLAZE_POWDER));
                say(scene, util, "Pumpjacks, burners, and tanks explain why a process is starved or backed up", process, PonderPalette.OUTPUT, 90);
                scene.rotateCameraY(20);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 45)
                                .rightClick();
                say(scene, util, "After binding, pick the engine source and choose Specialized for the full diagram", controller, PonderPalette.BLUE, 85);
                scene.effects().indicateSuccess(controller);
                scene.idle(15);
        }

        // ===================================================================
        // Chapter 6 of 8 - Vehicles & Logistics (flight deck, trains, freight)
        // ===================================================================
        public static void statsDisplayVehiclesLogistics(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_vehicles_logistics", "Vehicles & Logistics");
                BlockPos computer = util.grid().at(3, 1, 6);
                BlockPos controller = util.grid().at(4, 1, 6);
                BlockPos dispatch = util.grid().at(5, 1, 4);
                BlockPos manifest = util.grid().at(6, 1, 4);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(computer), Direction.UP);
                scene.idle(8);
                scene.overlay().showOutlineWithText(util.select().position(computer), 95)
                                .text("A Vehicle Control Center feeds the Aero Flight Deck: propulsion, attitude, altitude, sensors")
                                .colored(PonderPalette.BLUE)
                                .pointAt(util.vector().centerOf(computer))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);
                say(scene, util, "Aeroline Director adds route, heading, approach, and guidance for the same craft", computer, PonderPalette.GREEN, 90);

                scene.rotateCameraY(-25);
                scene.world().showSection(select(util, dispatch, manifest), Direction.DOWN);
                scene.idle(10);
                scene.overlay().showOutline(PonderPalette.OUTPUT, dispatch, util.select().position(dispatch), 85);
                scene.overlay().showControls(util.vector().topOf(manifest), Pointing.DOWN, 50)
                                .withItem(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.asStack());
                scene.world().createItemEntity(util.vector().topOf(dispatch).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.IRON_INGOT));
                scene.world().createItemEntity(util.vector().topOf(manifest).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.WHEAT));
                say(scene, util, "Train Stations, Dispatch Boards, and Freight Manifests are your logistics sources", dispatch, PonderPalette.OUTPUT, 95);
                say(scene, util, "Station data unlocks the Train Map; cargo sources show lines, orders, and history", manifest, PonderPalette.GREEN, 90);
                scene.rotateCameraY(25);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, controller, new AABB(controller).minmax(new AABB(util.grid().at(5, 2, 6))).inflate(0.25), 90);
                say(scene, util, "When station data is live, read the Train Map before chasing individual cargo channels", controller, PonderPalette.BLUE, 85);
                scene.effects().indicateSuccess(controller);
                scene.idle(15);
        }

        // ===================================================================
        // Chapter 7 of 8 - Combat Board & Tools
        // ===================================================================
        public static void statsDisplayCombatTools(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_combat_tools", "Combat Board & Tools");
                BlockPos observer = util.grid().at(1, 1, 4);
                BlockPos battle = util.grid().at(8, 1, 4);
                BlockPos recorder = util.grid().at(6, 1, 5);
                BlockPos projector = util.grid().at(8, 1, 2);
                BlockPos alarm = util.grid().at(8, 1, 3);
                BlockPos controller = util.grid().at(4, 1, 6);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(observer), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.BLUE, observer, util.select().position(observer), 85);
                say(scene, util, "Battle Observers scan a ship and tag every block as present, lost, or newly added", observer, PonderPalette.BLUE, 95);

                scene.world().showSection(util.select().position(battle), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.GREEN, battle, util.select().position(battle), 85);
                say(scene, util, "A Battle Bridge gathers those scans into one combat source for the panel", battle, PonderPalette.GREEN, 90);
                // Data streams from observer to bridge to board, then the board lights up.
                for (int i = 0; i < 4; i++) {
                        scene.world().createItemEntity(util.vector().topOf(observer).add(0, 0.4, 0), new Vec3(0.2, 0.05, 0), new ItemStack(Items.IRON_NUGGET));
                        scene.idle(11);
                }
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, controller, new AABB(controller).minmax(new AABB(util.grid().at(5, 2, 6))).inflate(0.25), 95);
                say(scene, util, "The board then shows integrity, lost-block composition, cargo state, and weapon readiness", controller, PonderPalette.RED, 95);

                scene.rotateCameraY(-20);
                scene.world().showSection(select(util, recorder, projector, alarm), Direction.DOWN);
                scene.idle(10);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 45)
                                .withItem(ModItems.STATS_CONFIGURATOR.asStack())
                                .rightClick();
                say(scene, util, "The Configurator copies, pastes, and edits panel layouts in place", controller, PonderPalette.BLUE, 80);
                scene.idle(6);
                scene.overlay().showControls(util.vector().blockSurface(controller, Direction.NORTH), Pointing.RIGHT, 45)
                                .withItem(ModItems.STATS_HANDHELD.asStack())
                                .rightClick();
                say(scene, util, "The Handheld opens any linked display from a distance", controller, PonderPalette.BLUE, 80);

                // Three tools, each with its own glow and a payoff: snapshot, mirror, alarm.
                scene.overlay().showOutline(PonderPalette.GREEN, recorder, util.select().position(recorder), 90);
                scene.world().createItemEntity(util.vector().topOf(recorder).add(0, 0.35, 0), Vec3.ZERO, new ItemStack(Items.PAPER));
                scene.overlay().showOutline(PonderPalette.OUTPUT, projector, util.select().position(projector), 90);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, projector, new AABB(projector).inflate(0.4), 70);
                scene.overlay().showOutline(PonderPalette.RED, alarm, util.select().position(alarm), 90);
                scene.effects().indicateRedstone(alarm);
                say(scene, util, "Recorder stores snapshots, Projector mirrors a panel elsewhere, Alarms react to thresholds", projector, PonderPalette.GREEN, 95);
                scene.rotateCameraY(20);
                scene.effects().indicateSuccess(controller);
                scene.idle(15);
        }

        // ===================================================================
        // Chapter 8 of 8 - Battle Tactical reconstruction (the 3D hull view)
        // ===================================================================
        public static void statsDisplayBattleTactical(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = statsDisplayScene(builder, "stats_display_battle_tactical", "Battle Tactical reconstruction");
                BlockPos battle = util.grid().at(8, 1, 4);
                BlockPos observer = util.grid().at(1, 1, 4);
                BlockPos controller = util.grid().at(4, 1, 6);

                BlockPos panelTopRight = util.grid().at(5, 2, 6);
                AABB deckLow = new AABB(controller).minmax(new AABB(util.grid().at(5, 1, 6))).inflate(0.2, 0.1, 0.2);
                AABB deckMid = deckLow.move(0, 0.55, 0);
                AABB deckTop = deckLow.move(0, 1.1, 0);
                AABB hullVolume = new AABB(controller).minmax(new AABB(panelTopRight)).inflate(0.25);
                AABB peeledHull = hullVolume.contract(0, 0.55, 0);

                showBaseAndPanel(scene, util);
                scene.world().showSection(util.select().position(observer), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.BLUE, observer, util.select().position(observer), 85);
                say(scene, util, "Battle Observers scan the ship and tag every block as present, lost, or newly added", observer, PonderPalette.BLUE, 95);
                for (int i = 0; i < 4; i++) {
                        scene.world().createItemEntity(util.vector().topOf(observer).add(0, 0.4, 0), new Vec3(0.18, 0.05, 0), new ItemStack(Items.IRON_NUGGET));
                        scene.idle(12);
                }

                scene.world().showSection(util.select().position(battle), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.GREEN, battle, util.select().position(battle), 85);
                say(scene, util, "A Battle Bridge gathers those scans into one combat source for the panel", battle, PonderPalette.GREEN, 90);

                // Reconstruct the hull deck by deck: the volume grows upward as data arrives.
                scene.rotateCameraY(-35);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, controller, deckLow, 30);
                scene.idle(14);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, controller, deckMid, 30);
                scene.idle(14);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, controller, deckTop, 90);
                say(scene, util, "Set that source to Tactical mode to build a live 3D reconstruction of the hull", controller, PonderPalette.OUTPUT, 100);

                // Slowly orbit to sell the 3D shape, then peel the upper deck away.
                scene.rotateCameraY(20);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, controller, hullVolume, 60);
                scene.rotateCameraY(25);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, controller, peeledHull, 95);
                say(scene, util, "Drag to orbit, right-click to freeze, and use the Y slider to peel away upper decks", controller, PonderPalette.OUTPUT, 100);
                scene.idle(20);

                scene.rotateCameraY(-30);
                say(scene, util, "The 2D / 3D switch flips between the flat integrity console and the rotatable hull", controller, PonderPalette.BLUE, 95);
                // A hit lands: deck flashes red as integrity drops.
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, deckTop, deckTop, 90);
                scene.effects().indicateRedstone(panelTopRight);
                say(scene, util, "Integrity, lost-block composition, and weapon readiness keep updating as the battle unfolds", battle, PonderPalette.RED, 95);

                scene.rotateCameraY(20);
                scene.effects().indicateSuccess(controller);
                scene.idle(20);
        }

    public static void inventoryDisplaySources(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("inventory_display", "Reading nearby inventories");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.82f);
        scene.setSceneOffsetY(-1);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        BlockPos chest = util.grid().at(2, 1, 3);
        BlockPos vault = util.grid().at(2, 1, 4);
        BlockPos detector = util.grid().at(3, 1, 3);
        BlockPos link = util.grid().at(2, 2, 3);
        BlockPos tank = util.grid().at(1, 1, 1);
        BlockPos boiler = util.grid().at(1, 1, 5);

        Selection storage = util.select().position(chest).add(util.select().position(vault));
        Selection display = util.select().position(detector).add(util.select().position(detector.above()));
        Selection nearbyReadout = util.select().position(chest).add(util.select().position(vault)).add(display);

        scene.world().showSection(storage, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(display, Direction.WEST);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(detector, Direction.WEST), Pointing.LEFT, 45)
                .withItem(ModBlocks.PRODUCTION_DETECTOR.asStack());
        scene.overlay().showOutlineWithText(nearbyReadout, 80)
                .text("Place it directly beside the chest, vault, belt, tank, or machine it should read")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().blockSurface(detector, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, detector, new AABB(chest).minmax(new AABB(detector.above())), 80);
        scene.overlay().showText(80)
                .text("The lower block scans; the upper screen shows the best matching readout")
                .pointAt(util.vector().blockSurface(detector.above(), Direction.WEST))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.overlay().showControls(util.vector().blockSurface(detector.above(), Direction.WEST), Pointing.LEFT, 50)
                .rightClick();
        scene.idle(12);
        scene.effects().indicateSuccess(detector);
        scene.idle(30);

        scene.world().createItemEntity(util.vector().topOf(detector).add(0, 0.35, 0), Vec3.ZERO, new ItemStack(Items.IRON_INGOT));
        scene.world().createItemEntity(util.vector().topOf(detector).add(0.25, 0.55, 0.1), Vec3.ZERO, new ItemStack(Items.COPPER_INGOT));
        scene.overlay().showText(75)
                .text("Item sources become compact top-item and capacity summaries")
                .pointAt(util.vector().topOf(detector.above()))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(85);

        scene.overlay().showControls(util.vector().blockSurface(detector.above(), Direction.WEST), Pointing.LEFT, 50)
                .withItem(AllBlocks.DISPLAY_LINK.asStack())
                .rightClick();
        scene.overlay().showText(70)
                .text("For linked setups, click the Inventory Display first")
                .pointAt(util.vector().blockSurface(detector.above(), Direction.WEST))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);
        scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 50)
                .withItem(AllBlocks.DISPLAY_LINK.asStack())
                .rightClick();
        scene.idle(4);
        scene.world().showSection(util.select().position(link), Direction.UP);
        scene.world().flashDisplayLink(link);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, link, new AABB(chest).minmax(new AABB(detector.above())), 70);
        scene.idle(25);
        scene.overlay().showText(80)
                .text("Then click the source; the Display Link appears on top of that source block")
                .pointAt(util.vector().centerOf(link))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(85);

        scene.world().hideSection(util.select().position(chest).add(util.select().position(vault)).add(util.select().position(link)), Direction.UP);
        scene.idle(8);
        scene.world().showSection(util.select().position(tank).add(util.select().position(boiler)), Direction.NORTH);
        scene.idle(12);
        scene.overlay().showOutline(PonderPalette.BLUE, tank, util.select().position(tank), 80);
        scene.overlay().showOutline(PonderPalette.RED, boiler, util.select().position(boiler), 80);
        scene.overlay().showText(80)
                .text("Fluid, energy, diesel, and boiler-style sources get their own specialized screens")
                .pointAt(util.vector().of(1.5, 2.0, 3.0))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("If the screen is blank, rotate it toward the source or open it and check the detected target")
                .pointAt(util.vector().blockSurface(detector, Direction.WEST))
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(90);
        scene.effects().indicateSuccess(detector);
        scene.idle(12);
    }

        // ===================================================================
        // Kinetic Flywheel - inertia bank guide
        // ===================================================================
        public static void kineticFlywheelGuide(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);
                scene.title("kinetic_flywheel_inertia_bank", "Kinetic Flywheel: banking momentum");
                scene.configureBasePlate(0, 0, 9);
                scene.scaleSceneView(0.76f);
                scene.setSceneOffsetY(-1);

                BlockPos motor = util.grid().at(2, 1, 2);
                BlockPos shaftA = util.grid().at(3, 1, 2);
                BlockPos shaftB = util.grid().at(4, 1, 2);
                BlockPos flywheel = util.grid().at(5, 1, 2);
                BlockPos link = util.grid().at(5, 2, 2);
                BlockPos comparator = util.grid().at(6, 1, 2);
                BlockPos lamp = util.grid().at(7, 1, 2);

                Selection motorSel = util.select().position(motor);
                Selection shafts = select(util, shaftA, shaftB);
                Selection bank = select(util, shaftA, shaftB, flywheel);
                Selection drivenLine = select(util, motor, shaftA, shaftB, flywheel);

                showBase(scene, util);

                // 1) Reveal the flywheel sitting on the end of a shaft.
                scene.world().showSection(util.select().position(flywheel), Direction.DOWN);
                scene.world().setKineticSpeed(util.select().position(flywheel), 0);
                scene.idle(8);
                scene.overlay().showControls(util.vector().topOf(flywheel), Pointing.DOWN, 50)
                                .withItem(ModBlocks.KINETIC_FLYWHEEL.asStack());
                say(scene, util, "The Kinetic Flywheel banks rotational momentum at the end of a shaft", flywheel, PonderPalette.BLUE, 80);

                // 2) Reveal the drive train and spin it up - the wheel and shaft start turning.
                scene.world().showSection(select(util, shaftA, shaftB), Direction.DOWN);
                scene.idle(6);
                scene.world().showSection(motorSel, Direction.DOWN);
                scene.idle(8);
                scene.world().setKineticSpeed(drivenLine, 48);
                scene.effects().rotationSpeedIndicator(flywheel);
                scene.idle(10);
                say(scene, util, "Drive it and it spools up, storing energy while it draws stress", shaftB, PonderPalette.GREEN, 80);

                // 3) Link a Stats Display panel to watch it charge.
                scene.world().showSection(panelSelection(util), Direction.NORTH);
                scene.idle(8);
                scene.world().showSection(util.select().position(link), Direction.DOWN);
                scene.world().flashDisplayLink(link);
                scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, link,
                                new AABB(flywheel).minmax(new AABB(util.grid().at(5, 2, 6))), 80);
                say(scene, util, "A Display Link feeds live Charge %, Stored RPM, and mode to a Stats Display", link, PonderPalette.OUTPUT, 90);

                // 4) Cut the engine - the flywheel keeps the line turning, then coasts down.
                scene.world().hideSection(motorSel, Direction.UP);
                scene.world().setKineticSpeed(motorSel, 0);
                scene.idle(6);
                scene.world().setKineticSpeed(bank, 40);
                scene.effects().rotationSpeedIndicator(flywheel);
                say(scene, util, "When the engine stalls, the flywheel becomes the source and keeps the network turning", flywheel, PonderPalette.BLUE, 90);

                // Friction coast-down: step the speed down so the wheel visibly slows.
                scene.world().setKineticSpeed(bank, 28);
                scene.idle(20);
                scene.world().setKineticSpeed(bank, 16);
                scene.idle(20);
                scene.world().setKineticSpeed(bank, 8);
                scene.overlay().showOutline(PonderPalette.RED, flywheel, util.select().position(flywheel), 80);
                say(scene, util, "It coasts down on a friction curve, a mechanical UPS for windmill and engine dips", flywheel, PonderPalette.RED, 90);
                scene.idle(10);

                // 5) Comparator low-reserve signal.
                scene.world().showSection(select(util, comparator, lamp), Direction.DOWN);
                scene.idle(8);
                scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
                say(scene, util, "A comparator reads the charge as a 0-15 signal for low-reserve automation", comparator, PonderPalette.OUTPUT, 85);

                // 6) Panel controls.
                scene.overlay().showControls(util.vector().blockSurface(util.grid().at(4, 1, 6), Direction.NORTH), Pointing.RIGHT, 50)
                                .rightClick();
                say(scene, util, "On the panel: Arm or disarm discharge, Force Park, and set the low-reserve threshold", util.grid().at(4, 2, 6), PonderPalette.GREEN, 90);
                scene.effects().indicateSuccess(flywheel);
        }

        // ===================================================================
        // Control Clutch - remotely engaged/disengaged by a Factory Logic Controller
        // ===================================================================

        private static CreateSceneBuilder statsDisplayScene(SceneBuilder builder, String sceneId, String title) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);
                scene.title(sceneId, title);
                scene.configureBasePlate(0, 0, 9);
                scene.scaleSceneView(0.76f);
                scene.setSceneOffsetY(-1);
                return scene;
        }

        static void showBase(CreateSceneBuilder scene, SceneBuildingUtil util) {
                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(8);
        }

        private static void showBaseAndPanel(CreateSceneBuilder scene, SceneBuildingUtil util) {
                showBase(scene, util);
                scene.world().showSection(panelSelection(util), Direction.NORTH);
                scene.idle(12);
        }

        private static Selection panelSelection(SceneBuildingUtil util) {
                BlockPos lowerLeft = util.grid().at(4, 1, 6);
                BlockPos lowerRight = util.grid().at(5, 1, 6);
                return select(util, lowerLeft, lowerLeft.above(), lowerRight, lowerRight.above());
        }

        static Selection select(SceneBuildingUtil util, BlockPos first, BlockPos... rest) {
                Selection selection = util.select().position(first);
                for (BlockPos position : rest) {
                        selection = selection.add(util.select().position(position));
                }
                return selection;
        }

        static void say(CreateSceneBuilder scene, SceneBuildingUtil util, String text, BlockPos target, PonderPalette color, int duration) {
                scene.overlay().showText(duration)
                                .text(text)
                                .pointAt(util.vector().centerOf(target))
                                .colored(color)
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(duration + 5);
        }

        /** Build an item stack from a registry id, so cross-mod items (Create, Aeronautics) work without a compile dependency. */
        static ItemStack stack(String id) {
                return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        }
}