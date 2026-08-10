package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.blockentity.CalculatorBE;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Calculator ponder, split out of CreateStatsPonderScenes (topic-class layout). */
public class CalculatorScenes {

    public static void calculatorGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("calculator_working_calculator", "Calculator: compute and graph in-world");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.7f);
        scene.setSceneOffsetY(-1);

        BlockPos calculator = util.grid().at(4, 2, 4);
        Selection calcSel = util.select().position(calculator);

        scene.world().modifyBlockEntityNBT(calcSel, CalculatorBE.class, tag -> {
                CompoundTag calc = tag.getCompound("CalculatorState");
                calc.putString("Expression", "12 * 8 + 4");
                calc.putString("Result", "100");
                calc.putString("Error", "");
                tag.put("CalculatorState", calc);
        });

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(calcSel, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(calculator, Direction.UP), Pointing.DOWN, 50)
                        .withItem(ModBlocks.CALCULATOR.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Calculator is a fully working calculator and function grapher built into a block", calculator, PonderPalette.BLUE, 95);

        scene.overlay().showOutline(PonderPalette.GREEN, calculator, calcSel, 70);
        CreateStatsPonderScenes.say(scene, util, "Its glowing face shows your expression and the result at a glance", calculator, PonderPalette.GREEN, 90);
        scene.idle(6);

        scene.overlay().showControls(util.vector().blockSurface(calculator, Direction.SOUTH), Pointing.UP, 50)
                        .rightClick();
        CreateStatsPonderScenes.say(scene, util, "Right-click to open the full screen, then type any expression, from simple sums to sin, cos, sqrt and powers", calculator, PonderPalette.BLUE, 100);
        scene.idle(8);

        scene.world().modifyBlockEntityNBT(calcSel, CalculatorBE.class, tag -> {
                CompoundTag calc = tag.getCompound("CalculatorState");
                calc.putString("Expression", "sin(x)");
                calc.putString("Result", "graph");
                tag.put("CalculatorState", calc);
        });
        CreateStatsPonderScenes.say(scene, util, "Switch to graph mode and plot functions of x as smooth curves over an adjustable range", calculator, PonderPalette.OUTPUT, 95);
        scene.idle(6);

        CreateStatsPonderScenes.say(scene, util, "Scientific mode, history, and pinned formulas keep your favourite equations one tap away", calculator, PonderPalette.MEDIUM, 95);
        scene.idle(6);

        Selection wide = CreateStatsPonderScenes.select(util,
                        util.grid().at(3, 2, 4), util.grid().at(4, 2, 4), util.grid().at(5, 2, 4),
                        util.grid().at(3, 3, 4), util.grid().at(4, 3, 4), util.grid().at(5, 3, 4));
        scene.overlay().showOutline(PonderPalette.INPUT, util.grid().at(4, 2, 4), wide, 80);
        CreateStatsPonderScenes.say(scene, util, "Place several together and they assemble into one big, high-resolution display", calculator, PonderPalette.INPUT, 95);
        scene.idle(8);

        scene.effects().indicateSuccess(calculator);
    }
}
