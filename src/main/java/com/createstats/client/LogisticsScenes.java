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

/** Factory automation + train logistics ponders, split out of CreateStatsPonderScenes. */
public class LogisticsScenes {

    public static void factoryLogicControllerGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("factory_logic_controller_automation_brain", "Factory Logic Controller: automate your factory");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.setSceneOffsetY(-1);

        BlockPos controller = util.grid().at(4, 2, 4);
        Selection controllerSel = util.select().position(controller);
        Selection machines = CreateStatsPonderScenes.select(util,
                        util.grid().at(6, 1, 4), util.grid().at(7, 1, 4),
                        util.grid().at(6, 2, 4), util.grid().at(7, 2, 4));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(controllerSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(controller, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModBlocks.FACTORY_LOGIC_CONTROLLER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Factory Logic Controller is a programmable brain for your whole factory", controller, PonderPalette.BLUE, 95);

        scene.overlay().showOutline(PonderPalette.GREEN, controller, controllerSel, 70);
        CreateStatsPonderScenes.say(scene, util, "Its living screen tracks up to sixteen automation rules, each glowing with its status", controller, PonderPalette.GREEN, 95);
        scene.idle(6);

        scene.overlay().showControls(util.vector().blockSurface(controller, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModItems.MACHINE_SELECTION_CARD.asStack());
        CreateStatsPonderScenes.say(scene, util, "Slot in Machine Selection Cards to choose exactly which machines each rule governs", controller, PonderPalette.INPUT, 100);
        scene.idle(8);

        scene.world().showSection(machines, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, util.grid().at(6, 2, 4), machines, 80);
        scene.world().createItemEntity(util.vector().topOf(util.grid().at(6, 2, 4)).add(0, 0.3, 0), Vec3.ZERO, new ItemStack(Items.COPPER_INGOT));
        CreateStatsPonderScenes.say(scene, util, "Link Control or Safety Clutches with a link card and it starts and stops those machines for you, with no redstone wiring", controller, PonderPalette.OUTPUT, 110);

        scene.effects().indicateRedstone(controller);
        CreateStatsPonderScenes.say(scene, util, "Each rule watches its machines and runs them only while there is work to do. When they sit idle past your timeout, it stops them to save power", controller, PonderPalette.MEDIUM, 110);
        scene.idle(6);

        scene.overlay().showControls(util.vector().blockSurface(controller, Direction.SOUTH), Pointing.UP, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click to open the editor and program every rule, then let your factory run itself", controller, PonderPalette.BLUE, 100);
        scene.idle(8);
        scene.effects().indicateSuccess(controller);
    }

    public static void freightManifestClipboardGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("freight_manifest_clipboard_station_manifest", "Freight Manifest Clipboard: plan train cargo");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.65f);
        scene.setSceneOffsetY(-1);

        BlockPos clipboard = util.grid().at(3, 2, 4);
        Selection clipboardSel = util.select().position(clipboard);
        BlockPos station = util.grid().at(5, 1, 4);
        Selection depot = CreateStatsPonderScenes.select(util, station,
                        util.grid().at(6, 1, 4), util.grid().at(7, 1, 4), util.grid().at(6, 2, 4));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(clipboardSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(clipboard, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Freight Manifest Clipboard is a logistics planner for your whole train network", clipboard, PonderPalette.BLUE, 100);

        scene.world().showSection(depot, Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.INPUT, station, depot, 80);
        CreateStatsPonderScenes.say(scene, util, "Hold it and right-click any Create Train Station to bind the clipboard to that depot", station, PonderPalette.INPUT, 105);

        scene.overlay().showControls(util.vector().blockSurface(clipboard, Direction.SOUTH), Pointing.UP, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click the placed clipboard to write a manifest: which goods, how many, and where they go", clipboard, PonderPalette.GREEN, 105);
        scene.idle(8);

        CreateStatsPonderScenes.say(scene, util, "It turns each line into a delivery order, load or unload with a target amount, and validates every line before the schedule runs", clipboard, PonderPalette.MEDIUM, 105);
        scene.idle(6);

        scene.effects().indicateRedstone(station);
        scene.world().createItemEntity(util.vector().topOf(util.grid().at(6, 1, 4)).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.IRON_INGOT));
        scene.world().createItemEntity(util.vector().topOf(util.grid().at(7, 1, 4)).add(0, 0.4, 0), Vec3.ZERO, new ItemStack(Items.REDSTONE));
        CreateStatsPonderScenes.say(scene, util, "Then it tracks live transfer progress as freight loads and unloads at the station", station, PonderPalette.OUTPUT, 100);
        scene.idle(8);

        scene.overlay().showControls(util.vector().blockSurface(clipboard, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModItems.SMALL_SIGNAL_ANTENNA.asStack());
        CreateStatsPonderScenes.say(scene, util, "Pipe it into a Display Link or stats screen, and add a Signal Antenna to read distant stations", clipboard, PonderPalette.WHITE, 105);
        scene.idle(8);
        scene.effects().indicateSuccess(clipboard);
    }
}
