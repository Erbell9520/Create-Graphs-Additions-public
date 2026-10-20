package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.block.MainBreakerBlock;
import com.createstats.chunkloader.ponder.CrownPonderScript;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.Labels;
import com.createstats.client.ponder.StudSpans;
import com.createstats.client.ponder.crown.CrownPonder;
import com.createstats.client.ponder.fe.FeProps;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public final class SparkCrownScenes {
    private SparkCrownScenes() {
    }

    private static final class Clock {
        int now;

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] spark crown cue {} is behind the scene clock {}", tick, now);
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

    private static TextElementBuilder caption(CreateSceneBuilder scene, Clock c, CrownPonderScript.Cue cue) {
        c.until(scene, cue.tick());
        TextElementBuilder b = scene.overlay().showText(cue.hold());
        b = cue.free() ? b.independent(cue.freeY())
                : b.pointAt(new Vec3(cue.target()[0], cue.target()[1], cue.target()[2]));
        return cue.keyframe() ? b.attachKeyFrame() : b;
    }

    private static List<Selection> tiles(SceneBuildingUtil util) {
        final List<Selection> out = new ArrayList<>();
        final int[] m = CrownPonderScript.MAP_CENTRE;
        for (int i = 0; i < CrownPonderScript.PICKS; i++) {
            final int[] t = CrownPonderScript.tile(i);
            out.add(util.select().position(m[0] + t[0], m[1], m[2] + t[1]));
        }
        return out;
    }

    private static List<IntPredicate> held() {
        final List<IntPredicate> out = new ArrayList<>();
        for (int i = 0; i < CrownPonderScript.PICKS; i++) {
            final int k = i;
            out.add(t -> CrownPonderScript.at(t).state().held() > k);
        }
        return out;
    }

    private static KitInstruction breaker(boolean closed, int at) {
        return FeProps.setState(pos(CrownPonderScript.BREAKER),
                s -> s.hasProperty(MainBreakerBlock.POWERED) ? s.setValue(MainBreakerBlock.POWERED, closed) : s, at);
    }

    public static void crown(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("spark_crown", "Keeping chunks loaded");
        scene.configureBasePlate(0, 0, CrownPonderScript.PLATE);
        scene.scaleSceneView(CrownPonderScript.SCALE);
        scene.setSceneOffsetY(CrownPonderScript.OFFSET_Y);
        final Clock c = new Clock();
        final int length = CrownPonderScript.LENGTH + 1;
        final BlockPos crown = pos(CrownPonderScript.CROWN);
        final BlockPos connA = pos(CrownPonderScript.CONNECTOR_A);
        final BlockPos connB = pos(CrownPonderScript.CONNECTOR_B);
        final BlockPos breaker = pos(CrownPonderScript.BREAKER);
        final double[] tagAt = CrownPonderScript.TAG_AT;
        final Vec3 top = new Vec3(tagAt[0], tagAt[1], tagAt[2]);

        scene.addInstruction(CrownPonder.drive(0, length));
        scene.addInstruction(StudSpans.sections(tiles(util), held(), 0, length));
        scene.addInstruction(CrownPonder.power("crown_power", 0, length));
        final int own = CrownPonderScript.firstHolding(1, 0);

        scene.addInstruction(Labels.tag(pt -> top, CrownPonder::drawText, CrownPonder::drawColour,
                CrownPonderScript.TAG_DX, CrownPonderScript.TAG_DY, own, length - own));

        scene.world().showSection(util.select().layer(0), Direction.UP);
        c.until(scene, 8);
        scene.world().showSection(util.select().position(connA.below()), Direction.DOWN);
        scene.world().showSection(util.select().position(connB.below()), Direction.DOWN);
        c.until(scene, 11);
        scene.world().showSection(util.select().position(connA), Direction.DOWN);
        scene.world().showSection(util.select().position(connB), Direction.DOWN);
        c.until(scene, 15);
        scene.world().showSection(util.select().position(breaker), Direction.DOWN);
        c.until(scene, 22);
        scene.world().showSection(util.select().position(crown), Direction.DOWN);

        caption(scene, c, CrownPonderScript.cue("what"))
                .text("The Spark Crown keeps chunks of its region loaded and running while nobody is there")
                .colored(PonderPalette.BLUE);

        c.until(scene, CrownPonderScript.LINE_A);
        scene.overlay().showControls(CrownPonder.stud(0), Pointing.DOWN, 20).rightClick()
                .withItem(new ItemStack(ModItems.HV_LINE.get()));
        scene.addInstruction(StudSpans.grow("crown_line_a", CrownPonder.stud(0), crown, connA, WireTier.HV,
                CrownPonderScript.LINE_A, CrownPonderScript.UNSPOOL + 1));
        c.until(scene, CrownPonderScript.LINE_B);
        scene.overlay().showControls(CrownPonder.stud(1), Pointing.DOWN, 20).rightClick()
                .withItem(new ItemStack(ModItems.HV_LINE.get()));
        scene.addInstruction(StudSpans.grow("crown_line_b", CrownPonder.stud(1), crown, connB, WireTier.HV,
                CrownPonderScript.LINE_B, CrownPonderScript.UNSPOOL + 1));

        caption(scene, c, CrownPonderScript.cue("lines"))
                .text("Wire an HV Line to each stud on its back: powered, it holds its own chunk first")
                .colored(PonderPalette.INPUT);

        c.until(scene, CrownPonderScript.MAP);
        scene.overlay().showControls(new Vec3(crown.getX() + 0.5, crown.getY() + 1.0, crown.getZ() + 0.5),
                Pointing.DOWN, 30).rightClick();
        caption(scene, c, CrownPonderScript.cue("map"))
                .text("Use it to open its region map, then click the chunks it should hold")
                .colored(PonderPalette.INPUT);
        caption(scene, c, CrownPonderScript.cue("price"))
                .text("Its own chunk costs 1,024 FE/t, and each further chunk 10% more than the last");
        caption(scene, c, CrownPonderScript.cue("lines_carry"))
                .text("One HV Line carries 20 chunks: the second stud is what takes it to all 25");
        caption(scene, c, CrownPonderScript.cue("ultimate"))
                .text("At 25 chunks it stands in its ULTIMATE form, drawing 100,707 FE/t")
                .colored(PonderPalette.GREEN);

        c.until(scene, CrownPonderScript.CUT - 6);
        scene.overlay().showControls(new Vec3(breaker.getX() + 0.5, breaker.getY() + 0.5, breaker.getZ() + 0.5),
                Pointing.DOWN, 20).rightClick();
        scene.addInstruction(breaker(false, CrownPonderScript.CUT));
        caption(scene, c, CrownPonderScript.cue("ride"))
                .text("Cut its power and it rides through on its store: 5 seconds of its draw")
                .colored(PonderPalette.RED);
        caption(scene, c, CrownPonderScript.cue("all_go"))
                .text("Then every chunk goes at once, its own included")
                .colored(PonderPalette.RED);

        c.until(scene, CrownPonderScript.RESTORE - 6);
        scene.overlay().showControls(new Vec3(breaker.getX() + 0.5, breaker.getY() + 0.5, breaker.getZ() + 0.5),
                Pointing.DOWN, 20).rightClick();
        scene.addInstruction(breaker(true, CrownPonderScript.RESTORE));
        caption(scene, c, CrownPonderScript.cue("comeback"))
                .text("With power back it takes them back one at a time, each with 5 seconds in store")
                .colored(PonderPalette.GREEN);

        c.until(scene, CrownPonderScript.LENGTH);
    }
}
