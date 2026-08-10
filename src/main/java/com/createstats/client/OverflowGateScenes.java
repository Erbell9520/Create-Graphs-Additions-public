package com.createstats.client;

import com.createstats.registry.ModBlocks;
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

/** Overflow Gate ponder, split out of CreateStatsPonderScenes (topic-class layout). */
public class OverflowGateScenes {

    public static void overflowGateGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("overflow_gate_smart_splitter", "Overflow Gate: smart belt splitter");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.62f);
        scene.setSceneOffsetY(-1);

        BlockPos gate = util.grid().at(4, 2, 4);
        Selection mainLine = CreateStatsPonderScenes.select(util,
                        util.grid().at(2, 1, 4), util.grid().at(3, 1, 4), util.grid().at(4, 1, 4),
                        util.grid().at(5, 1, 4), util.grid().at(6, 1, 4), gate, util.grid().at(6, 2, 4));
        Selection sideBranch = CreateStatsPonderScenes.select(util,
                        util.grid().at(4, 1, 5), util.grid().at(4, 1, 6), util.grid().at(4, 2, 6));
        Vec3 westTop = util.vector().topOf(util.grid().at(2, 1, 4));

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(mainLine, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(gate, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModBlocks.OVERFLOW_GATE.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Overflow Gate is a smart splitter that mounts on a belt and sorts the flow", gate, PonderPalette.BLUE, 100);

        for (int i = 0; i < 4; i++) {
                scene.world().createItemEntity(westTop.add(0, 0.4, 0), new Vec3(0.16, 0.0, 0.0), new ItemStack(Items.IRON_INGOT));
                scene.idle(10);
        }
        CreateStatsPonderScenes.say(scene, util, "By default items ride the belt straight through to your main line", util.grid().at(6, 1, 4), PonderPalette.GREEN, 95);

        scene.overlay().showControls(util.vector().blockSurface(gate, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(new ItemStack(Items.IRON_INGOT));
        CreateStatsPonderScenes.say(scene, util, "Set a filter and the gate keeps filtered goods on the main route, filtered first, then anything else", gate, PonderPalette.INPUT, 105);
        scene.idle(8);

        scene.world().showSection(sideBranch, Direction.NORTH);
        scene.idle(8);
        scene.overlay().showOutline(PonderPalette.OUTPUT, util.grid().at(4, 2, 6), sideBranch, 90);
        for (int i = 0; i < 3; i++) {
                scene.world().createItemEntity(util.vector().topOf(gate).add(0, 0.3, 0), new Vec3(0.0, 0.0, 0.14), new ItemStack(Items.GRAVEL));
                scene.idle(10);
        }
        CreateStatsPonderScenes.say(scene, util, "When the main line is blocked, excess spills to the side belt you pick (left, forward, or right) after a short delay", util.grid().at(4, 1, 6), PonderPalette.OUTPUT, 115);

        scene.overlay().showControls(util.vector().blockSurface(gate, Direction.SOUTH), Pointing.UP, 50)
                        .rightClick();
        CreateStatsPonderScenes.say(scene, util, "Excess waits in the gate's buffer; sneak-use to collect it, or use it to read the live routing summary", gate, PonderPalette.MEDIUM, 110);
        scene.idle(8);

        scene.effects().indicateSuccess(gate);
    }
}
