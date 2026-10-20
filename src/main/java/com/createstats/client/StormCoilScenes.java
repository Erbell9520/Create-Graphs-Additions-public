package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.client.ponder.StudSpans;
import com.createstats.client.ponder.coil.CoilPonder;
import com.createstats.client.ponder.fe.FeProps;
import com.createstats.registry.ModItems;
import com.createstats.stormcoil.StormCoilStuds;
import com.createstats.stormcoil.ponder.CoilPonderScript;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class StormCoilScenes {
    private StormCoilScenes() {
    }

    private static final class Clock {
        int now;

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] storm coil cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) {
                scene.idle(tick - now);
                now = tick;
            }
        }
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static TextElementBuilder caption(CreateSceneBuilder scene, Clock c, CoilPonderScript.Cue cue) {
        c.until(scene, cue.tick());
        TextElementBuilder b = scene.overlay().showText(cue.hold());
        b = cue.free() ? b.independent(cue.freeY())
                : b.pointAt(new Vec3(cue.target()[0], cue.target()[1], cue.target()[2]));
        return cue.keyframe() ? b.attachKeyFrame() : b;
    }

    public static void coil(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("storm_coil", "The Storm Coil");
        scene.configureBasePlate(0, 0, CoilPonderScript.PLATE);
        scene.scaleSceneView(CoilPonderScript.SCALE);
        scene.setSceneOffsetY(CoilPonderScript.OFFSET_Y);
        final Clock c = new Clock();
        final int length = CoilPonderScript.LENGTH + 1;
        final BlockPos coil = pos(CoilPonderScript.COIL);
        final BlockPos inConn = pos(CoilPonderScript.IN_CONNECTOR);
        final BlockPos outConn = pos(CoilPonderScript.OUT_CONNECTOR);
        final BlockPos breaker = pos(CoilPonderScript.BREAKER);
        final BlockPos range = pos(CoilPonderScript.RANGE);

        scene.addInstruction(CoilPonder.drive(0, length));
        scene.addInstruction(CoilPonder.power("coil_power", 0, length));

        scene.addInstruction(FeProps.range(range, t -> 3, t -> CoilPonderScript.at(t).out() <= 0, 0, length));
        scene.addInstruction(CoilPonder.mobs(CoilPonderScript.MOBS, 0, length));
        scene.addInstruction(CoilPonder.rain(CoilPonderScript.STORM, CoilPonderScript.STORM_END - CoilPonderScript.STORM));
        scene.addInstruction(CoilPonder.strike("coil_bolt", CoilPonderScript.STRIKE, 10));

        scene.world().showSection(util.select().layer(0), Direction.UP);
        c.until(scene, 8);
        scene.world().showSection(util.select().position(inConn.below()), Direction.DOWN);
        scene.world().showSection(util.select().position(range), Direction.DOWN);
        c.until(scene, 11);
        scene.world().showSection(util.select().position(inConn), Direction.DOWN);
        scene.world().showSection(util.select().position(outConn), Direction.DOWN);
        c.until(scene, 15);
        scene.world().showSection(util.select().position(breaker), Direction.DOWN);
        c.until(scene, 22);
        scene.world().showSection(util.select().position(coil), Direction.DOWN);

        caption(scene, c, CoilPonderScript.cue("what"))
                .text("The Storm Coil arcs to mobs on FE, and in a storm it catches lightning")
                .colored(PonderPalette.BLUE);

        c.until(scene, CoilPonderScript.IN_WIRE);
        scene.overlay().showControls(CoilPonder.stud(StormCoilStuds.IN), Pointing.DOWN, 20).rightClick()
                .withItem(new ItemStack(ModItems.SHIELDED_WIRE.get()));
        scene.addInstruction(StudSpans.grow("coil_in", CoilPonder.stud(StormCoilStuds.IN), coil, inConn,
                CoilPonderScript.IN_WIRE_TIER, CoilPonderScript.IN_WIRE, CoilPonderScript.UNSPOOL + 1));
        caption(scene, c, CoilPonderScript.cue("in"))
                .text("Wire its IN stud to your power: IN charges the capacitor its arcs are paid from")
                .colored(PonderPalette.INPUT);

        c.until(scene, CoilPonderScript.OUT_WIRE);
        scene.overlay().showControls(CoilPonder.stud(StormCoilStuds.OUT), Pointing.DOWN, 20).rightClick()
                .withItem(new ItemStack(ModItems.COPPER_WIRE.get()));
        scene.addInstruction(StudSpans.grow("coil_out", CoilPonder.stud(StormCoilStuds.OUT), coil, outConn,
                CoilPonderScript.OUT_WIRE_TIER, CoilPonderScript.OUT_WIRE, CoilPonderScript.UNSPOOL + 1));
        caption(scene, c, CoilPonderScript.cue("out"))
                .text("OUT gives only what lightning banked: power from IN never passes through to OUT")
                .colored(PonderPalette.RED);

        c.until(scene, CoilPonderScript.HEIGHT - 6);
        scene.overlay().showControls(new Vec3(coil.getX() + 0.5, coil.getY() + 0.6, coil.getZ() + 1.0), Pointing.DOWN,
                3 * CoilPonderScript.STAGE_STEP).scroll();
        caption(scene, c, CoilPonderScript.cue("height"))
                .text("Raise it on its Height setting: each stage reaches farther and fires at more mobs")
                .colored(PonderPalette.INPUT);

        caption(scene, c, CoilPonderScript.cue("hostile"))
                .text("In Hostile mode it arcs at hostile mobs only, a volley a second: the pig is spared");

        caption(scene, c, CoilPonderScript.cue("storm"))
                .text("In a thunderstorm, the tallest thing around, it calls lightning down onto itself")
                .colored(PonderPalette.BLUE);
        caption(scene, c, CoilPonderScript.cue("bank"))
                .text("Each strike banks 9,830,400 FE: its storm bank holds ten")
                .colored(PonderPalette.GREEN);
        caption(scene, c, CoilPonderScript.cue("out_gives"))
                .text("Now OUT gives the banked lightning out, and IN rests while the bank can pay")
                .colored(PonderPalette.GREEN);

        c.until(scene, CoilPonderScript.LENGTH);
    }
}
