package com.createstats.client;

import com.createstats.blockentity.InductionHeaterBE;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Kinetic actuator/clutch ponders, split out of CreateStatsPonderScenes (topic-class layout). */
public class KineticControlScenes {

    public static void inductionHeaterGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("induction_heater_kinetic_kitchen", "Induction Heater: the kinetic kitchen");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.72f);
        scene.setSceneOffsetY(-1);

        BlockPos motorA = util.grid().at(2, 1, 4);
        BlockPos heaterA = util.grid().at(2, 2, 4);
        BlockPos motorB = util.grid().at(6, 1, 4);
        BlockPos heaterB = util.grid().at(6, 2, 4);
        BlockPos basinB = util.grid().at(6, 3, 4);

        Selection towerA = CreateStatsPonderScenes.select(util, motorA, heaterA);
        Selection towerB = CreateStatsPonderScenes.select(util, motorB, heaterB);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(heaterA), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(heaterA), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(heaterA), Pointing.DOWN, 50).withItem(ModBlocks.INDUCTION_HEATER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Induction Heater is a stove that runs on rotation - no lava, no Blaze, just RPM", heaterA, PonderPalette.BLUE, 90);

        scene.world().showSection(util.select().position(motorA), Direction.DOWN);
        scene.idle(5);
        CreateStatsPonderScenes.say(scene, util, "It only accepts power through its underside - run a shaft straight up into its base", motorA, PonderPalette.BLUE, 85);

        scene.world().setKineticSpeed(towerA, 64);
        scene.effects().rotationSpeedIndicator(heaterA);
        scene.world().modifyBlock(heaterA, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING), false);
        CreateStatsPonderScenes.say(scene, util, "At 64 RPM the plate idles Warm: a faint glow and gentle passive heat", heaterA, PonderPalette.GREEN, 85);

        scene.world().setKineticSpeed(towerA, 128);
        scene.effects().rotationSpeedIndicator(heaterA);
        scene.world().modifyBlock(heaterA, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED), false);
        CreateStatsPonderScenes.say(scene, util, "128 RPM reaches Kindled: true cooking heat, billed to the network as 2048 SU", heaterA, PonderPalette.GREEN, 90);

        CreateStatsPonderScenes.say(scene, util, "Goggles read the live tier, thermal charge, stress draw, and coil health at a glance", heaterA, PonderPalette.BLUE, 85);

        scene.overlay().showControls(util.vector().topOf(heaterA), Pointing.DOWN, 40).rightClick().withItem(ModItems.INDUCTION_COIL.asStack());
        scene.idle(10);
        scene.world().setKineticSpeed(towerA, 256);
        scene.effects().rotationSpeedIndicator(heaterA);
        scene.world().modifyBlock(heaterA, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING), false);
        CreateStatsPonderScenes.say(scene, util, "Slot in an Induction Coil and hold 256 RPM to go Superheated - the coil slowly burns away while it blazes, so keep spares in a hopper", heaterA, PonderPalette.RED, 110);

        scene.world().showSection(towerB.add(util.select().position(basinB)), Direction.DOWN);
        scene.world().setKineticSpeed(towerB, 256);
        scene.world().modifyBlock(heaterB, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING), false);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.GREEN, basinB, util.select().position(basinB), 80);
        CreateStatsPonderScenes.say(scene, util, "A Basin or Boiler on top just works - the plate counts as a Blaze Burner, and Superheated counts as Seething", basinB, PonderPalette.GREEN, 100);

        scene.world().setKineticSpeed(towerA, 128);
        scene.world().modifyBlock(heaterA, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED), false);
        scene.overlay().showControls(util.vector().topOf(heaterA), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.BEEF, 64));
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(util.select().position(heaterA), InductionHeaterBE.class, tag -> {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", "minecraft:beef");
            stack.putInt("count", 64);
            tag.put("CookStack", stack);
            tag.putFloat("CookProgress", 30);
            tag.putInt("CookTime", 600);
            tag.putBoolean("Cooked", false);
        });
        CreateStatsPonderScenes.say(scene, util, "It is also literally a stove: right-click with raw food to lay a whole stack on the plate", heaterA, PonderPalette.BLUE, 90);

        CreateStatsPonderScenes.say(scene, util, "All 64 share one timer and finish together - half the book time at Kindled, under a third when Superheated", heaterA, PonderPalette.GREEN, 95);

        scene.world().modifyBlockEntityNBT(util.select().position(heaterA), InductionHeaterBE.class, tag -> {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", "minecraft:cooked_beef");
            stack.putInt("count", 64);
            tag.put("CookStack", stack);
            tag.putFloat("CookProgress", 600);
            tag.putInt("CookTime", 600);
            tag.putBoolean("Cooked", true);
        });
        scene.effects().indicateSuccess(heaterA);
        CreateStatsPonderScenes.say(scene, util, "Ding! Dinner stays on the plate for you or a hopper to collect - nothing is flung onto the floor", heaterA, PonderPalette.GREEN, 95);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, heaterA, new AABB(heaterA).inflate(0.1).move(0, 0.4, 0), 70);
        CreateStatsPonderScenes.say(scene, util, "And mind your boots - the plate cannot tell chef from chicken at Kindled and above", heaterA, PonderPalette.RED, 90);
    }

    public static void rpmGovernorGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("rpm_governor_overview", "RPM Governor: commanded output speed");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos inA = util.grid().at(2, 1, 2);
        BlockPos inB = util.grid().at(3, 1, 2);
        BlockPos governor = util.grid().at(4, 1, 2);
        BlockPos outA = util.grid().at(5, 1, 2);
        BlockPos outB = util.grid().at(6, 1, 2);
        BlockPos cog = util.grid().at(7, 1, 2);
        BlockPos fcc = util.grid().at(4, 1, 4);

        Selection inputLine = CreateStatsPonderScenes.select(util, motor, inA, inB, governor);
        Selection outputLine = CreateStatsPonderScenes.select(util, outA, outB, cog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(governor), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(governor), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(governor), Pointing.DOWN, 50).withItem(ModBlocks.RPM_GOVERNOR.asStack());
        CreateStatsPonderScenes.say(scene, util, "The RPM Governor is an inline actuator that sits along a kinetic line", governor, PonderPalette.BLUE, 80);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(6);
        scene.world().setKineticSpeed(inputLine, 32);
        scene.effects().rotationSpeedIndicator(governor);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "Upstream kinetics drive its input side, just like any shaft", inB, PonderPalette.GREEN, 80);

        scene.world().showSection(CreateStatsPonderScenes.select(util, outA, outB, cog), Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(outputLine, 0);
        scene.idle(8);
        CreateStatsPonderScenes.say(scene, util, "Until it is commanded the governor parks its output at zero; it is an actuator, not a pass-through gear", cog, PonderPalette.SLOW, 90);

        scene.world().showSection(util.select().position(fcc), Direction.NORTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, fcc, util.select().position(fcc), 80);
        scene.overlay().showControls(util.vector().topOf(fcc), Pointing.DOWN, 50).withItem(ModBlocks.FLIGHT_CONTROL_COMPUTER.asStack());
        CreateStatsPonderScenes.say(scene, util, "A Flight Control Computer claims it and commands an exact, signed output RPM", fcc, PonderPalette.OUTPUT, 90);

        scene.world().setKineticSpeed(outputLine, 64);
        scene.effects().rotationSpeedIndicator(cog);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, governor, new AABB(governor).minmax(new AABB(cog)), 80);
        CreateStatsPonderScenes.say(scene, util, "It drives the output branch to that RPM, independent of how fast the input turns", outB, PonderPalette.BLUE, 90);

        scene.world().setKineticSpeed(outputLine, -48);
        scene.effects().rotationSpeedIndicator(cog);
        CreateStatsPonderScenes.say(scene, util, "A negative command even spins the output the opposite way, reverse thrust on demand", cog, PonderPalette.BLUE, 90);
        scene.idle(10);

        scene.world().hideSection(util.select().position(fcc), Direction.UP);
        scene.idle(6);
        scene.world().setKineticSpeed(outputLine, 0);
        scene.effects().rotationSpeedIndicator(governor);
        scene.overlay().showOutline(PonderPalette.RED, cog, outputLine, 80);
        CreateStatsPonderScenes.say(scene, util, "If the FCC stops heart-beating the governor zeroes its output, a failsafe against runaway thrust", cog, PonderPalette.RED, 95);
        scene.idle(8);
        scene.effects().indicateSuccess(governor);
    }

    public static void smartGearshiftGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_gearshift_gear_stages", "Smart Gearshift: redstone-picked RPM");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos inA = util.grid().at(2, 1, 2);
        BlockPos inB = util.grid().at(3, 1, 2);
        BlockPos gearshift = util.grid().at(4, 1, 2);
        BlockPos outA = util.grid().at(5, 1, 2);
        BlockPos outB = util.grid().at(6, 1, 2);
        BlockPos cog = util.grid().at(7, 1, 2);
        BlockPos comparator = util.grid().at(4, 1, 3);
        BlockPos lamp = util.grid().at(4, 1, 4);

        Selection outputLine = CreateStatsPonderScenes.select(util, outA, outB, cog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(gearshift), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(gearshift), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(gearshift), Pointing.DOWN, 50).withItem(ModBlocks.SMART_GEARSHIFT.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Smart Gearshift is an inline gearbox that locks its output to an exact RPM", gearshift, PonderPalette.BLUE, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(CreateStatsPonderScenes.select(util, outA, outB, cog), Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(CreateStatsPonderScenes.select(util, motor, inA, inB, gearshift, outA, outB, cog), 64);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "With no redstone signal it stays in Neutral and just hands the rotation through", inB, PonderPalette.GREEN, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, comparator, lamp), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(gearshift), Pointing.DOWN, 50).withItem(new ItemStack(Items.REDSTONE));
        CreateStatsPonderScenes.say(scene, util, "Feed it a redstone signal of 1 to 15 and each level picks a fixed target RPM", comparator, PonderPalette.OUTPUT, 90);

        scene.world().setKineticSpeed(outputLine, 16);
        scene.effects().rotationSpeedIndicator(cog);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, gearshift, new AABB(gearshift).minmax(new AABB(cog)), 80);
        CreateStatsPonderScenes.say(scene, util, "A low gear throttles the output down to its target, even while the input races ahead", outB, PonderPalette.RED, 90);

        scene.world().setKineticSpeed(outputLine, 192);
        scene.effects().rotationSpeedIndicator(cog);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, gearshift, new AABB(gearshift).minmax(new AABB(cog)), 80);
        CreateStatsPonderScenes.say(scene, util, "A high gear boosts it the other way, the output holds that exact RPM no matter the drive", outB, PonderPalette.BLUE, 90);
        scene.idle(8);

        scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
        CreateStatsPonderScenes.say(scene, util, "A comparator reads back the active gear as a 0-15 signal for automation", lamp, PonderPalette.OUTPUT, 90);
        scene.effects().indicateSuccess(gearshift);
    }

    public static void controlClutchGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("control_clutch_remote_engage", "Control Clutch: remote engagement");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos inA = util.grid().at(2, 1, 2);
        BlockPos inB = util.grid().at(3, 1, 2);
        BlockPos clutch = util.grid().at(4, 1, 2);
        BlockPos outA = util.grid().at(5, 1, 2);
        BlockPos outB = util.grid().at(6, 1, 2);
        BlockPos cog = util.grid().at(7, 1, 2);
        BlockPos controller = util.grid().at(4, 1, 4);
        BlockPos comparator = util.grid().at(4, 1, 1);
        BlockPos lamp = util.grid().at(4, 1, 0);

        Selection wholeLine = CreateStatsPonderScenes.select(util, motor, inA, inB, clutch, outA, outB, cog);
        Selection outputLine = CreateStatsPonderScenes.select(util, outA, outB, cog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(clutch), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(clutch), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(clutch), Pointing.DOWN, 50).withItem(ModBlocks.CONTROL_CLUTCH.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Control Clutch sits inline on a shaft and can break the drive on command", clutch, PonderPalette.BLUE, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(CreateStatsPonderScenes.select(util, outA, outB, cog), Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(wholeLine, 64);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "Engaged by default, it hands the full input speed to the output", outB, PonderPalette.GREEN, 85);

        scene.world().showSection(util.select().position(controller), Direction.NORTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, controller, util.select().position(controller), 80);
        scene.overlay().showControls(util.vector().topOf(controller), Pointing.DOWN, 50).withItem(ModBlocks.FACTORY_LOGIC_CONTROLLER.asStack());
        CreateStatsPonderScenes.say(scene, util, "Link it to a Factory Logic Controller, which can address many clutches by row", controller, PonderPalette.OUTPUT, 95);

        scene.world().setKineticSpeed(outputLine, 0);
        scene.effects().rotationSpeedIndicator(clutch);
        scene.overlay().showOutline(PonderPalette.RED, cog, outputLine, 80);
        CreateStatsPonderScenes.say(scene, util, "On a Stop command it disengages, the output halts while the input keeps turning", outB, PonderPalette.RED, 95);
        scene.idle(8);

        scene.world().setKineticSpeed(outputLine, 64);
        scene.effects().rotationSpeedIndicator(cog);
        CreateStatsPonderScenes.say(scene, util, "A Run command re-engages it and the output spins back up", outB, PonderPalette.GREEN, 90);
        scene.idle(8);

        scene.world().showSection(CreateStatsPonderScenes.select(util, comparator, lamp), Direction.DOWN);
        scene.idle(6);
        scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
        CreateStatsPonderScenes.say(scene, util, "The controller's 0-15 signal passes through to a comparator for downstream logic", lamp, PonderPalette.OUTPUT, 90);
        scene.idle(6);

        scene.world().hideSection(util.select().position(controller), Direction.UP);
        scene.idle(6);
        CreateStatsPonderScenes.say(scene, util, "If the controller stops responding the clutch safely defaults back to engaged", clutch, PonderPalette.BLUE, 90);
        scene.effects().indicateSuccess(clutch);
    }

    public static void safetyClutchGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("safety_clutch_stress_breaker", "Safety Clutch: stress breaker");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos inA = util.grid().at(2, 1, 2);
        BlockPos inB = util.grid().at(3, 1, 2);
        BlockPos clutch = util.grid().at(4, 1, 2);
        BlockPos outA = util.grid().at(5, 1, 2);
        BlockPos outB = util.grid().at(6, 1, 2);
        BlockPos cog = util.grid().at(7, 1, 2);
        BlockPos comparator = util.grid().at(4, 1, 1);
        BlockPos lamp = util.grid().at(4, 1, 0);

        Selection wholeLine = CreateStatsPonderScenes.select(util, motor, inA, inB, clutch, outA, outB, cog);
        Selection outputLine = CreateStatsPonderScenes.select(util, outA, outB, cog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(clutch), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(clutch), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(clutch), Pointing.DOWN, 50).withItem(ModBlocks.SAFETY_CLUTCH.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Safety Clutch is a stress circuit-breaker wired inline on a shaft", clutch, PonderPalette.BLUE, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(CreateStatsPonderScenes.select(util, outA, outB, cog), Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(wholeLine, 48);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "While the network is healthy it stays engaged and drives the load", outB, PonderPalette.GREEN, 85);

        scene.world().showSection(CreateStatsPonderScenes.select(util, comparator, lamp), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
        CreateStatsPonderScenes.say(scene, util, "A comparator reports live network stress as a 0-15 signal", lamp, PonderPalette.OUTPUT, 90);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, clutch, new AABB(clutch).inflate(0.35), 80);
        CreateStatsPonderScenes.say(scene, util, "Cross the stress threshold, 80% by default, or overstress, and it instantly trips", clutch, PonderPalette.RED, 95);
        scene.world().setKineticSpeed(outputLine, 0);
        scene.effects().rotationSpeedIndicator(clutch);
        scene.overlay().showOutline(PonderPalette.RED, cog, outputLine, 80);
        CreateStatsPonderScenes.say(scene, util, "Tripping disengages the output, sheltering your machines from overload", outB, PonderPalette.RED, 95);
        scene.idle(8);

        scene.overlay().showOutline(PonderPalette.OUTPUT, lamp, util.select().position(lamp), 70);
        CreateStatsPonderScenes.say(scene, util, "Once tripped it also emits a full redstone signal you can alarm or interlock on", lamp, PonderPalette.OUTPUT, 90);
        scene.idle(6);

        scene.overlay().showControls(util.vector().blockSurface(clutch, Direction.WEST), Pointing.RIGHT, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "Latch mode holds it open until you reset it; Auto-Reset retries after a few seconds", clutch, PonderPalette.BLUE, 95);
        scene.idle(6);

        scene.world().setKineticSpeed(outputLine, 48);
        scene.effects().rotationSpeedIndicator(cog);
        CreateStatsPonderScenes.say(scene, util, "Reset it once stress settles and the drive smoothly resumes", outB, PonderPalette.GREEN, 90);
        scene.effects().indicateSuccess(clutch);
    }

    public static void autoTransmissionClutchGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("auto_transmission_clutch_stress_governor", "Auto-Transmission Clutch: stress governor");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos inA = util.grid().at(2, 1, 2);
        BlockPos inB = util.grid().at(3, 1, 2);
        BlockPos clutch = util.grid().at(4, 1, 2);
        BlockPos outA = util.grid().at(5, 1, 2);
        BlockPos outB = util.grid().at(6, 1, 2);
        BlockPos cog = util.grid().at(7, 1, 2);
        BlockPos comparator = util.grid().at(4, 1, 1);
        BlockPos lamp = util.grid().at(4, 1, 0);

        Selection wholeLine = CreateStatsPonderScenes.select(util, motor, inA, inB, clutch, outA, outB, cog);
        Selection outputLine = CreateStatsPonderScenes.select(util, outA, outB, cog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(clutch), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(clutch), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(clutch), Pointing.DOWN, 50).withItem(ModBlocks.AUTO_TRANSMISSION_CLUTCH.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Auto-Transmission Clutch is a self-tuning gearbox wired inline on a shaft", clutch, PonderPalette.BLUE, 90);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(CreateStatsPonderScenes.select(util, outA, outB, cog), Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(wholeLine, 64);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "It constantly adjusts output speed to keep network stress inside a target band", outB, PonderPalette.GREEN, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, comparator, lamp), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
        CreateStatsPonderScenes.say(scene, util, "A comparator reports the live stress reading, measured in SU or as a percentage", lamp, PonderPalette.OUTPUT, 95);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, clutch, new AABB(clutch).inflate(0.35), 80);
        CreateStatsPonderScenes.say(scene, util, "When stress sits below the band there is spare capacity, so it speeds the output up", clutch, PonderPalette.GREEN, 95);
        scene.world().setKineticSpeed(outputLine, 128);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, clutch, new AABB(clutch).inflate(0.35), 80);
        CreateStatsPonderScenes.say(scene, util, "When stress climbs past the band it eases the output down to protect the network", clutch, PonderPalette.RED, 95);
        scene.world().setKineticSpeed(outputLine, 32);
        scene.effects().rotationSpeedIndicator(cog);
        scene.idle(10);

        scene.overlay().showControls(util.vector().blockSurface(clutch, Direction.WEST), Pointing.RIGHT, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "You set the stress band plus minimum and maximum RPM limits; it alarms if it cannot hold the floor", clutch, PonderPalette.BLUE, 100);
        scene.idle(6);

        scene.world().setKineticSpeed(outputLine, 80);
        scene.effects().rotationSpeedIndicator(cog);
        CreateStatsPonderScenes.say(scene, util, "The result is hands-off throughput that always rides the edge of your stress budget", outB, PonderPalette.GREEN, 95);
        scene.effects().indicateSuccess(clutch);
    }

    public static void loadBalancerGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("load_balancer_twin_branch", "Load Balancer: twin-branch split");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.76f);
        scene.setSceneOffsetY(-1);

        BlockPos motor = util.grid().at(4, 1, 1);
        BlockPos inA = util.grid().at(4, 1, 2);
        BlockPos inB = util.grid().at(4, 1, 3);
        BlockPos balancer = util.grid().at(4, 1, 4);
        BlockPos leftA = util.grid().at(3, 1, 4);
        BlockPos leftB = util.grid().at(2, 1, 4);
        BlockPos leftCog = util.grid().at(1, 1, 4);
        BlockPos rightA = util.grid().at(5, 1, 4);
        BlockPos rightB = util.grid().at(6, 1, 4);
        BlockPos rightCog = util.grid().at(7, 1, 4);
        BlockPos comparator = util.grid().at(4, 1, 5);
        BlockPos lamp = util.grid().at(4, 1, 6);

        Selection inputLine = CreateStatsPonderScenes.select(util, motor, inA, inB, balancer);
        Selection leftLine = CreateStatsPonderScenes.select(util, leftA, leftB, leftCog);
        Selection rightLine = CreateStatsPonderScenes.select(util, rightA, rightB, rightCog);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(util.select().position(balancer), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(balancer), 0);
        scene.idle(8);
        scene.overlay().showControls(util.vector().topOf(balancer), Pointing.DOWN, 50).withItem(ModBlocks.LOAD_BALANCER.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Load Balancer takes one input shaft and feeds two separate output branches", balancer, PonderPalette.BLUE, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, inA, inB), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(motor), Direction.SOUTH);
        scene.idle(5);
        scene.world().setKineticSpeed(inputLine, 64);
        scene.effects().rotationSpeedIndicator(inB);
        scene.idle(8);
        CreateStatsPonderScenes.say(scene, util, "Power arrives on the input face from a single source", inA, PonderPalette.WHITE, 80);

        scene.world().showSection(leftLine, Direction.WEST);
        scene.idle(5);
        scene.world().showSection(rightLine, Direction.EAST);
        scene.idle(5);
        scene.world().setKineticSpeed(leftLine, 64);
        scene.world().setKineticSpeed(rightLine, 64);
        scene.effects().rotationSpeedIndicator(leftCog);
        scene.effects().rotationSpeedIndicator(rightCog);
        scene.idle(10);
        CreateStatsPonderScenes.say(scene, util, "By default it shares power evenly, a balanced 50/50 split across both branches", balancer, PonderPalette.GREEN, 95);

        scene.world().showSection(CreateStatsPonderScenes.select(util, comparator, lamp), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, comparator, util.select().position(comparator), 80);
        CreateStatsPonderScenes.say(scene, util, "A comparator reports the live network stress as a 0-15 signal", lamp, PonderPalette.OUTPUT, 85);

        scene.world().setKineticSpeed(leftLine, 96);
        scene.effects().rotationSpeedIndicator(leftCog);
        scene.world().setKineticSpeed(rightLine, 32);
        scene.effects().rotationSpeedIndicator(rightCog);
        scene.overlay().showOutline(PonderPalette.BLUE, leftCog, leftLine, 80);
        CreateStatsPonderScenes.say(scene, util, "Set the share to anything you like and it biases more speed onto the chosen branch", leftCog, PonderPalette.BLUE, 95);
        scene.idle(8);

        scene.overlay().showControls(util.vector().topOf(balancer), Pointing.DOWN, 50).rightClick();
        CreateStatsPonderScenes.say(scene, util, "It also caps each branch to hold both sub-networks inside a stress band, with min and max RPM limits", balancer, PonderPalette.RED, 100);
        scene.idle(6);

        scene.world().setKineticSpeed(leftLine, 64);
        scene.world().setKineticSpeed(rightLine, 64);
        scene.effects().rotationSpeedIndicator(leftCog);
        scene.effects().rotationSpeedIndicator(rightCog);
        CreateStatsPonderScenes.say(scene, util, "One shaft in, two perfectly managed shafts out, no manual tuning required", balancer, PonderPalette.GREEN, 95);
        scene.effects().indicateSuccess(balancer);
    }
}
