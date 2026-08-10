package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.block.LampBlock;
import com.createstats.block.StageLightBlock;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** [1] */
public class LightingScenes {

    /** Wipes everything the host schematic left above the base plate so a scene starts clean. */
    private static void clearStage(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().replaceBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
    }

    private static void put(CreateSceneBuilder scene, BlockPos pos, BlockState state) {
        scene.world().setBlock(pos, state, false);
    }

    /** A lamp of the given kind mounted so that it points in the given direction. */
    private static BlockState lamp(com.tterrag.registrate.util.entry.BlockEntry<LampBlock> kind, Direction facing) {
        return kind.getDefaultState().setValue(LampBlock.FACING, facing);
    }

    /** Surface cable occupying the named faces of its own block position. */
    private static BlockState cable(Direction... faces) {
        BlockState state = ModBlocks.SURFACE_CABLE.getDefaultState();
        for (Direction face : faces) {
            state = state.setValue(CableBlock.faceProperty(face), true);
        }
        return state;
    }

    // ==================================================================================
    // 1. The six fittings
    // ==================================================================================

    public static void lampFittings(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("lamp_fittings", "Lamps: fittings and colour");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        // A ceiling to hang the set from, so every fitting is read the way it is meant to be seen.
        for (int x = 1; x <= 6; x++) {
            put(scene, util.grid().at(x, 4, 4), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        scene.world().showSection(util.select().fromTo(1, 4, 4, 6, 4, 4), Direction.DOWN);
        scene.idle(10);

        BlockPos caged = util.grid().at(1, 3, 4);
        BlockPos highBay = util.grid().at(2, 3, 4);
        BlockPos strip = util.grid().at(3, 3, 4);
        BlockPos dome = util.grid().at(4, 3, 4);
        BlockPos panel = util.grid().at(5, 3, 4);
        BlockPos sconceWall = util.grid().at(7, 3, 4);
        BlockPos sconce = util.grid().at(6, 3, 4);

        put(scene, caged, lamp(ModBlocks.CAGED_BULKHEAD_LAMP, Direction.DOWN));
        put(scene, highBay, lamp(ModBlocks.HIGH_BAY_LAMP, Direction.DOWN));
        put(scene, strip, lamp(ModBlocks.STRIP_LAMP, Direction.DOWN));
        put(scene, dome, lamp(ModBlocks.DOME_LAMP, Direction.DOWN));
        put(scene, panel, lamp(ModBlocks.PANEL_LAMP, Direction.DOWN));
        // One at a time, the way Create reveals a build. A lump reveal wastes the only moment the
        // player is looking at each fitting on its own.
        PonderFx.reveal(scene, util, Direction.DOWN, 4, caged, highBay, strip, dome, panel);
        scene.idle(8);

        scene.overlay().showControls(util.vector().blockSurface(dome, Direction.DOWN), Pointing.UP, 60)
                .withItem(ModBlocks.DOME_LAMP.asStack());
        PonderFx.beat(scene, util,
                "Six fittings share one behaviour and differ only in shape and brightness, so you can light a room in the style it deserves",
                dome, PonderPalette.BLUE, 100);

        // Light them in a wave so the brightness difference is a comparison rather than a claim.
        for (BlockPos fitting : List.of(caged, highBay, strip, dome, panel)) {
            scene.world().modifyBlock(fitting, s -> s.setValue(LampBlock.LIT, true), true);
            scene.idle(4);
        }
        scene.idle(10);
        PonderFx.beatOutlined(scene, util,
                "The High Bay and the Panel reach full daylight, the Caged Bulkhead and Strip sit just under, and the Dome and Sconce are softer",
                CreateStatsPonderScenes.select(util, highBay, panel), highBay, PonderPalette.OUTPUT, 110);

        // The sconce is the one fitting that wants a wall rather than a ceiling.
        PonderFx.look(scene, sconce);
        put(scene, sconceWall, Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(scene, sconce, lamp(ModBlocks.WALL_SCONCE, Direction.WEST).setValue(LampBlock.LIT, true));
        scene.world().showSection(CreateStatsPonderScenes.select(util, sconceWall, sconce), Direction.WEST);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(sconce, Direction.WEST), Pointing.RIGHT, 50)
                .withItem(ModBlocks.WALL_SCONCE.asStack());
        PonderFx.beat(scene, util,
                "Every fitting mounts on any face and points away from whatever you clicked, so the same lamp works on a ceiling, a wall or a floor",
                sconce, PonderPalette.GREEN, 105);

        // Dye. Sixteen colours, applied in world with no menu. Particles on each so it reads as an act.
        PonderFx.look(scene, strip);
        scene.overlay().showControls(util.vector().blockSurface(strip, Direction.DOWN), Pointing.UP, 60)
                .rightClick()
                .withItem(new ItemStack(Items.ORANGE_DYE));
        scene.idle(8);
        scene.world().modifyBlock(strip, s -> s.setValue(LampBlock.COLOR, DyeColor.ORANGE), true);
        PonderFx.beat(scene, util,
                "Right-click a fitting with any dye to recolour it. All sixteen colours work, and no menu is involved",
                strip, PonderPalette.INPUT, 100);
        scene.world().modifyBlock(dome, s -> s.setValue(LampBlock.COLOR, DyeColor.LIGHT_BLUE), true);
        scene.idle(6);
        scene.world().modifyBlock(caged, s -> s.setValue(LampBlock.COLOR, DyeColor.LIME), true);
        scene.idle(6);
        scene.world().modifyBlock(panel, s -> s.setValue(LampBlock.COLOR, DyeColor.MAGENTA), true);
        scene.idle(12);

        ElementLink<ParrotElement> birb = scene.special().createBirb(
                util.vector().topOf(util.grid().at(3, 1, 6)), ParrotPose.FacePointOfInterestPose::new);
        scene.idle(10);
        PonderFx.aside(scene, util,
                "Colour is per fitting, so one run of cable can carry a whole scheme rather than a single tint",
                dome, PonderPalette.WHITE, 95);
        scene.special().changeBirbPose(birb, ParrotPose.DancePose::new);
        scene.effects().indicateSuccess(strip);
        scene.idle(20);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 2. Powering and switching a run
    // ==================================================================================

    public static void lampCircuits(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("lamp_circuits", "Lamps: power and switching");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        for (int x = 1; x <= 7; x++) {
            put(scene, util.grid().at(x, 4, 4), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        scene.world().showSection(util.select().fromTo(1, 4, 4, 7, 4, 4), Direction.DOWN);
        scene.idle(8);

        BlockPos bank = util.grid().at(1, 3, 4);
        BlockPos run1 = util.grid().at(2, 3, 4);
        BlockPos lampA = util.grid().at(3, 3, 4);
        BlockPos run2 = util.grid().at(4, 3, 4);
        BlockPos lampB = util.grid().at(5, 3, 4);
        BlockPos run3 = util.grid().at(6, 3, 4);
        BlockPos lampC = util.grid().at(7, 3, 4);

        put(scene, lampA, lamp(ModBlocks.CAGED_BULKHEAD_LAMP, Direction.DOWN));
        put(scene, lampB, lamp(ModBlocks.CAGED_BULKHEAD_LAMP, Direction.DOWN));
        put(scene, lampC, lamp(ModBlocks.CAGED_BULKHEAD_LAMP, Direction.DOWN));
        PonderFx.reveal(scene, util, Direction.DOWN, 4, lampA, lampB, lampC);
        scene.idle(10);
        PonderFx.beat(scene, util,
                "A fitting with nothing feeding it is simply a dark fitting. Lamps run on cable power whether or not FE Dependent Mode is switched on",
                lampB, PonderPalette.RED, 115);

        // The supply, then the run, laid block by block toward the fittings.
        put(scene, bank, ModBlocks.CAPACITOR_BANK.getDefaultState()
                .setValue(CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS));
        scene.world().showSection(util.select().position(bank), Direction.DOWN);
        scene.idle(10);
        put(scene, run1, cable(Direction.UP));
        put(scene, run2, cable(Direction.UP));
        put(scene, run3, cable(Direction.UP));
        PonderFx.reveal(scene, util, Direction.DOWN, 3, run1, run2, run3);
        scene.idle(8);
        scene.overlay().showControls(util.vector().blockSurface(run2, Direction.DOWN), Pointing.UP, 55)
                .withItem(ModBlocks.SURFACE_CABLE.asStack());
        PonderFx.beat(scene, util,
                "Surface Cable lies on block faces and turns corners. Click to start a run, aim to preview it, click again to lay it",
                run2, PonderPalette.INPUT, 110);

        // Energy visibly travels down the run and each fitting catches as the pulse reaches it.
        final Object current = new Object();
        PonderFx.energyPulse(scene, PonderPalette.OUTPUT, current,
                List.of(bank, run1, lampA, run2, lampB, run3, lampC), 4);
        scene.idle(8);
        scene.world().modifyBlock(lampA, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(8);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(8);
        scene.world().modifyBlock(lampC, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(14);

        PonderFx.beatOutlined(scene, util,
                "Each fitting is a piece of the circuit, not just something hanging off it, so the run carries on through each housing into the next",
                CreateStatsPonderScenes.select(util, bank, run1, lampA, run2, lampB, run3, lampC),
                lampB, PonderPalette.GREEN, 115);
        PonderFx.aside(scene, util,
                "That is why one cable landing anywhere on a row lights the whole row. A lamp costs one energy a tick, so a single alternator carries hundreds",
                lampC, PonderPalette.OUTPUT, 115);

        // Dye as circuit identity.
        scene.overlay().showControls(util.vector().blockSurface(run2, Direction.DOWN), Pointing.UP, 50)
                .rightClick()
                .withItem(new ItemStack(Items.BLUE_DYE));
        scene.idle(8);
        scene.world().modifyBlock(run1, s -> s.setValue(CableBlock.COLOR, DyeColor.BLUE), true);
        scene.idle(2);
        scene.world().modifyBlock(run2, s -> s.setValue(CableBlock.COLOR, DyeColor.BLUE), true);
        scene.idle(2);
        scene.world().modifyBlock(run3, s -> s.setValue(CableBlock.COLOR, DyeColor.BLUE), true);
        PonderFx.beat(scene, util,
                "Dye the cable too: colour is the circuit, so two runs of different colours can share a wall without joining",
                run2, PonderPalette.MEDIUM, 110);

        // Way one to darken a fed lamp: a wireless switch link. Drawn as an actual link.
        PonderFx.look(scene, lampC);
        BlockPos plate = util.grid().at(7, 1, 6);
        put(scene, util.grid().at(7, 1, 7), Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(scene, plate, ModBlocks.ROCKER_SWITCH.getDefaultState()
                .setValue(com.createstats.block.SwitchBlock.FACING, Direction.NORTH)
                .setValue(com.createstats.block.SwitchBlock.POWERED, true));
        scene.world().showSection(
                CreateStatsPonderScenes.select(util, util.grid().at(7, 1, 7), plate), Direction.DOWN);
        scene.idle(12);
        scene.overlay().showControls(util.vector().blockSurface(plate, Direction.NORTH), Pointing.RIGHT, 55)
                .rightClick()
                .withItem(ModItems.SWITCH_LINK_CARD.asStack());
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.BLUE,
                util.vector().centerOf(plate), util.vector().centerOf(lampC), 110);
        PonderFx.beat(scene, util,
                "A Switch Link Card pairs a switch to fittings it is not wired to. Click the switch to hold it, then click lamps",
                lampC, PonderPalette.BLUE, 110);
        scene.world().modifyBlock(plate, s -> s.setValue(com.createstats.block.SwitchBlock.POWERED, false), true);
        scene.idle(4);
        scene.world().modifyBlock(lampC, s -> s.setValue(LampBlock.LIT, false), true);
        scene.idle(12);
        PonderFx.aside(scene, util,
                "A linked lamp is still being fed. The switch only decides whether it lights, which is what lets one plate control a room it is nowhere near",
                lampC, PonderPalette.MEDIUM, 115);

        // Way two: redstone, which works backwards from vanilla.
        PonderFx.look(scene, lampB);
        BlockPos lever = util.grid().at(5, 4, 5);
        put(scene, lever, Blocks.REDSTONE_BLOCK.defaultBlockState());
        scene.world().showSection(util.select().position(lever), Direction.DOWN);
        scene.idle(6);
        scene.effects().indicateRedstone(lampB);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, false), true);
        scene.idle(12);
        PonderFx.beat(scene, util,
                "Redstone is the third way, and it works backwards from vanilla: a signal force-kills a fitting rather than lighting it",
                lampB, PonderPalette.RED, 115);
        PonderFx.aside(scene, util,
                "That keeps redstone from becoming a second way to power a lamp, so vanilla logic can sit on top of the wiring without replacing it",
                lampB, PonderPalette.WHITE, 110);

        // Way three: the supply itself failing, shown with the real brownout curve.
        PonderFx.look(scene, lampA);
        scene.world().setBlock(lever, Blocks.AIR.defaultBlockState(), true);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(10);
        PonderFx.beat(scene, util,
                "The third is the supply itself. Each fitting holds a few seconds in reserve, so a dip as machines start up never shows as a flicker",
                lampA, PonderPalette.INPUT, 115);
        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, CapacitorBankBE.LED_SEGMENTS, 0, 70);
        PonderFx.brownout(scene, lampA, LampBlock.LIT, 1.0f, 0.0f, 70);
        PonderFx.brownout(scene, lampB, LampBlock.LIT, 1.0f, 0.0f, 70);
        scene.idle(74);
        PonderFx.aside(scene, util,
                "Drain it for real and you get this instead: the fittings sag, flicker, and go out. A flickering room means look at your generators",
                bank, PonderPalette.RED, 115);

        PonderFx.sweepGauge(scene, bank, CapacitorBankBlock.LEVEL, 0, CapacitorBankBE.LED_SEGMENTS, 40);
        scene.idle(20);
        scene.world().modifyBlock(lampA, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(3);
        scene.world().modifyBlock(lampB, s -> s.setValue(LampBlock.LIT, true), true);
        scene.idle(20);
        scene.effects().indicateSuccess(lampA);
        scene.idle(15);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 3. Stage Light
    // ==================================================================================

    public static void stageLightGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("stage_light_aiming", "Stage Light: aiming the beam");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos mount = util.grid().at(4, 4, 4);
        BlockPos light = util.grid().at(4, 3, 4);

        put(scene, mount, Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(scene, light, ModBlocks.STAGE_LIGHT.getDefaultState()
                .setValue(StageLightBlock.FACING, Direction.DOWN));
        scene.world().showSection(CreateStatsPonderScenes.select(util, mount, light), Direction.DOWN);
        scene.idle(14);

        scene.overlay().showControls(util.vector().blockSurface(light, Direction.DOWN), Pointing.UP, 60)
                .withItem(ModBlocks.STAGE_LIGHT.asStack());
        PonderFx.beat(scene, util,
                "The Stage Light is a moving-head spotlight. Unlike the fixed fittings it aims wherever you point it, on any mounting face",
                light, PonderPalette.BLUE, 110);

        scene.world().modifyBlock(light, s -> s.setValue(StageLightBlock.LIT, true), true);
        scene.effects().indicateSuccess(light);
        scene.idle(12);

        scene.overlay().showControls(util.vector().blockSurface(light, Direction.DOWN), Pointing.UP, 50).rightClick();
        PonderFx.beat(scene, util,
                "Right-click it for the aiming panel: a pan compass and a tilt slider, with a live preview of where the beam lands",
                light, PonderPalette.INPUT, 110);

        // The head actually moves. Driven through the block entity's own applyConfig, eased, so it
        // accelerates and settles the way a lantern head does rather than snapping like a turret.
        PonderFx.sweepAim(scene, light, 0, 0, -55, 35, 45);
        scene.idle(48);
        PonderFx.sweepAim(scene, light, -55, 35, 60, 20, 55);
        scene.idle(58);
        PonderFx.beat(scene, util,
                "Range sets how far it throws, brightness how hard, and focus tightens it from a wide flood to a narrow spot",
                light, PonderPalette.OUTPUT, 110);

        PonderFx.sweepAim(scene, light, 60, 20, 0, 60, 40);
        scene.idle(44);
        PonderFx.aside(scene, util,
                "Beam width and light spread are separate on purpose: the visible cone and the light it actually casts can be sized independently",
                light, PonderPalette.MEDIUM, 115);

        scene.overlay().showControls(util.vector().blockSurface(light, Direction.WEST), Pointing.RIGHT, 55)
                .rightClick()
                .withItem(new ItemStack(Items.MAGENTA_STAINED_GLASS));
        scene.idle(10);
        scene.effects().indicateSuccess(light);
        PonderFx.beat(scene, util,
                "Slot stained glass as a beam filter to tint it, exactly like a gel in front of a real lantern",
                light, PonderPalette.GREEN, 105);

        PonderFx.aside(scene, util,
                "Strobe, pulse and rainbow effects run on a speed you set, and redstone can aim the head as well as the panel can",
                light, PonderPalette.WHITE, 110);

        PonderFx.beat(scene, util,
                "One caveat worth knowing before you build: a light mounted on a moving contraption needs LambDynamicLights installed to actually shine",
                light, PonderPalette.RED, 120);
        scene.markAsFinished();
    }

    // ==================================================================================
    // 4. Stage Light MK II
    // ==================================================================================

    public static void stageLightMk2Guide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("stage_light_mk2_reach", "Stage Light MK II: reach and upgrade");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);
        clearStage(scene, util);

        CreateStatsPonderScenes.showBase(scene, util);

        BlockPos mountA = util.grid().at(3, 4, 4);
        BlockPos mk1 = util.grid().at(3, 3, 4);
        BlockPos mountB = util.grid().at(5, 4, 4);
        BlockPos mk2 = util.grid().at(5, 3, 4);

        put(scene, mountA, Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(scene, mountB, Blocks.POLISHED_ANDESITE.defaultBlockState());
        put(scene, mk1, ModBlocks.STAGE_LIGHT.getDefaultState()
                .setValue(StageLightBlock.FACING, Direction.DOWN).setValue(StageLightBlock.LIT, true));
        put(scene, mk2, ModBlocks.STAGE_LIGHT_MK2.getDefaultState()
                .setValue(StageLightBlock.FACING, Direction.DOWN).setValue(StageLightBlock.LIT, true));
        scene.world().showSection(CreateStatsPonderScenes.select(util, mountA, mk1), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(CreateStatsPonderScenes.select(util, mountB, mk2), Direction.DOWN);
        scene.idle(14);

        // Sweep both together so the reach difference is a side-by-side rather than a claim.
        PonderFx.sweepAim(scene, mk1, 0, 0, -40, 40, 40);
        PonderFx.sweepAim(scene, mk2, 0, 0, 40, 40, 40);
        scene.idle(44);

        PonderFx.beatOutlined(scene, util,
                "The MK II is the same lantern in gold, with every control the original has and double the reach at 128 blocks",
                util.select().position(mk2), mk2, PonderPalette.BLUE, 115);

        PonderFx.look(scene, mk2);
        scene.overlay().showControls(util.vector().blockSurface(mk2, Direction.WEST), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(new ItemStack(Items.BEACON));
        scene.idle(10);
        scene.effects().indicateSuccess(mk2);
        PonderFx.beat(scene, util,
                "Right-click it with a Beacon to slot a range upgrade and double it again, out to 256 blocks",
                mk2, PonderPalette.GREEN, 105);

        scene.overlay().showControls(util.vector().blockSurface(mk2, Direction.WEST), Pointing.RIGHT, 55)
                .rightClick()
                .withItem(com.simibubi.create.AllItems.WRENCH.asStack());
        scene.idle(8);
        scene.world().createItemEntity(util.vector().topOf(mk2).add(0, 0.5, 0),
                util.vector().of(0, 0.15, 0), new ItemStack(Items.BEACON));
        scene.idle(10);
        PonderFx.beat(scene, util,
                "A wrench pops the Beacon back out, so the upgrade is a loan rather than a one-way craft",
                mk2, PonderPalette.INPUT, 105);

        PonderFx.sweepAim(scene, mk1, -40, 40, 0, 15, 30);
        PonderFx.sweepAim(scene, mk2, 40, 40, 0, 15, 30);
        scene.idle(34);
        PonderFx.beat(scene, util,
                "That reach is what makes it a floodlight for an airship or a quarry rather than a room light. At close range the original is the cheaper choice",
                mk1, PonderPalette.WHITE, 120);
        scene.markAsFinished();
    }
}
