package com.createstats.client;

import com.createstats.registry.ModItems;
import com.createstats.flight.block.FlightControlComputerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** VCC (Vehicle Control Computer) ponders, split out of CreateStatsPonderScenes (topic-class layout). */
public class VccScenes {

    public static void vccOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vcc_overview", "VCC: it spins your propellers");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.74f);
        scene.setSceneOffsetY(-1);

        BlockPos propeller = util.grid().at(1, 2, 4);
        BlockPos bearing = util.grid().at(2, 2, 4);
        BlockPos shaftA = util.grid().at(3, 2, 4);
        BlockPos governor = util.grid().at(4, 2, 4);
        BlockPos shaftB = util.grid().at(5, 2, 4);
        BlockPos motor = util.grid().at(6, 2, 4);
        BlockPos vcc = util.grid().at(4, 2, 5);
        BlockPos seat = util.grid().at(6, 2, 5);

        Selection deck = util.select().layer(1);
        Selection engineLine = CreateStatsPonderScenes.select(util, motor, shaftB, governor, shaftA);
        Selection craft = CreateStatsPonderScenes.select(util, propeller, bearing, shaftA, governor, shaftB, motor, seat);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(deck, Direction.UP);
        scene.idle(8);
        scene.world().showSection(craft, Direction.DOWN);
        scene.world().setKineticSpeed(engineLine, 32);
        scene.effects().rotationSpeedIndicator(governor);
        scene.idle(12);
        scene.world().showSection(util.select().position(vcc), Direction.SOUTH);
        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.OFF), false);
        scene.idle(8);
        CreateStatsPonderScenes.say(scene, util, "The engine spins, but the propeller stays parked until the VCC drives it", propeller, PonderPalette.SLOW, 90);

        scene.overlay().showControls(util.vector().blockSurface(propeller, Direction.WEST), Pointing.LEFT, 50).withItem(ModItems.PROPELLER_CARD.asStack()).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Tap the propeller with the Propeller Card", propeller, PonderPalette.INPUT, 80);

        scene.overlay().showControls(util.vector().topOf(governor), Pointing.DOWN, 50).withItem(ModItems.GOVERNOR_CARD.asStack()).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Tap its RPM Governor with the Governor Card", governor, PonderPalette.INPUT, 80);

        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.STANDBY), false);
        scene.overlay().showControls(util.vector().blockSurface(vcc, Direction.SOUTH), Pointing.UP, 45).withItem(ModItems.PROPELLER_CARD.asStack());
        scene.idle(16);
        scene.overlay().showControls(util.vector().blockSurface(vcc, Direction.SOUTH), Pointing.UP, 45).withItem(ModItems.GOVERNOR_CARD.asStack());
        CreateStatsPonderScenes.say(scene, util, "Drop both cards into the VCC, now they are a pair", vcc, PonderPalette.GREEN, 90);

        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.ACTIVE), false);
        ElementLink<WorldSectionElement> blades = scene.world().makeSectionIndependent(util.select().position(propeller));
        scene.world().rotateSection(blades, 360 * 5, 0, 0, 100);
        scene.effects().indicateSuccess(vcc);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, propeller, new AABB(propeller).minmax(new AABB(vcc)), 70);
        CreateStatsPonderScenes.say(scene, util, "Now the VCC spins that propeller for you", propeller, PonderPalette.GREEN, 85);

        scene.world().rotateSection(blades, -360 * 3, 0, 0, 70);
        CreateStatsPonderScenes.say(scene, util, "It sets the exact speed, forward or reverse, on command", propeller, PonderPalette.BLUE, 85);
        scene.world().rotateSection(blades, 360 * 3, 0, 0, 60);
        scene.idle(12);
        scene.effects().indicateSuccess(vcc);
    }

    public static void vccLinked(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vcc_linked", "VCC Linked Mode: pair with Redstone Links");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos burner = util.grid().at(4, 2, 3);
        BlockPos vcc = util.grid().at(4, 2, 6);
        BlockPos domeTop = util.grid().at(4, 5, 3);

        Selection deck = util.select().layer(1);
        Selection burnerSel = util.select().position(burner);
        Selection canopy = util.select().fromTo(3, 4, 2, 5, 4, 4).add(util.select().position(domeTop));
        Selection vccSel = util.select().position(vcc);
        Selection craft = util.select().layersFrom(1);

        ItemStack redstoneLink = CreateStatsPonderScenes.stack("create:redstone_link");
        ItemStack linkedController = CreateStatsPonderScenes.stack("create:linked_controller");

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(deck, Direction.UP);
        scene.idle(8);
        scene.world().showSection(burnerSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(canopy, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(vccSel, Direction.SOUTH);
        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.OFF), false);
        scene.idle(8);
        CreateStatsPonderScenes.say(scene, util, "Not every device wants a card, some pair by Redstone Link", vcc, PonderPalette.BLUE, 90);

        scene.overlay().showControls(util.vector().blockSurface(burner, Direction.WEST), Pointing.LEFT, 50).withItem(redstoneLink).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Set a Redstone Link on the burner and choose a frequency", burner, PonderPalette.INPUT, 85);
        scene.world().createItemEntity(util.vector().topOf(burner).add(-0.22, 0.55, 0.0), Vec3.ZERO, new ItemStack(Items.REDSTONE));
        scene.world().createItemEntity(util.vector().topOf(burner).add(0.22, 0.70, 0.0), Vec3.ZERO, new ItemStack(Items.CLOCK));
        scene.idle(18);

        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.STANDBY), false);
        scene.overlay().showControls(util.vector().blockSurface(vcc, Direction.SOUTH), Pointing.UP, 45).withItem(redstoneLink);
        CreateStatsPonderScenes.say(scene, util, "Drop a matching Link into the VCC, same frequency, now they are paired", vcc, PonderPalette.GREEN, 95);
        scene.idle(8);

        scene.world().modifyBlock(vcc, s -> s.setValue(FlightControlComputerBlock.VISUAL_STATE, FlightControlComputerBlock.VisualState.ACTIVE), false);
        scene.effects().indicateRedstone(vcc);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, burner, new AABB(vcc).minmax(new AABB(burner)), 75);
        scene.effects().indicateRedstone(burner);
        CreateStatsPonderScenes.say(scene, util, "The VCC throttles the burner by radio, a clean 0 to 15 signal", burner, PonderPalette.OUTPUT, 95);

        scene.overlay().showControls(util.vector().blockSurface(vcc, Direction.SOUTH), Pointing.RIGHT, 50).withItem(linkedController).rightClick();
        scene.effects().indicateRedstone(vcc);
        scene.effects().indicateRedstone(burner);
        CreateStatsPonderScenes.say(scene, util, "Or a Linked Controller drives those links from across the build", vcc, PonderPalette.GREEN, 90);

        ElementLink<WorldSectionElement> craftLink = scene.world().makeSectionIndependent(craft);
        CreateStatsPonderScenes.say(scene, util, "Heat builds and the whole craft lifts, linked, never carded", burner, PonderPalette.GREEN, 95);
        scene.world().moveSection(craftLink, new Vec3(0, 1.4, 0), 60);
        scene.effects().indicateSuccess(burner);
        scene.idle(64);
        scene.world().moveSection(craftLink, new Vec3(0, -1.0, 0), 55);
        CreateStatsPonderScenes.say(scene, util, "Ease the command down and the link relaxes the burner; it settles to a hover", domeTop, PonderPalette.BLUE, 95);
        scene.idle(8);
        scene.effects().indicateSuccess(vcc);
    }
}
