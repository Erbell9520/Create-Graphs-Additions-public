package com.createstats.client.ponder.seismic;

import com.createstats.CreateStats;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.ShadowFade;
import com.createstats.client.ponder.fe.FeFlow;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.VeinVisibility;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class SeismicScenes {
    private SeismicScenes() {
    }

    private static final class Clock {
        int now;

        void idle(CreateSceneBuilder scene, int ticks) {
            scene.idle(ticks);
            now += ticks;
        }

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] seismic cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) {
                idle(scene, tick - now);
            }
        }
    }

    private static CreateSceneBuilder stage(SceneBuilder builder, String id, String title, int plate, float scale,
                                            float offsetY) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, plate);
        scene.scaleSceneView(scale);
        scene.setSceneOffsetY(offsetY);
        return scene;
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static Selection box(SceneBuildingUtil util, int x0, int y0, int z0, int x1, int y1, int z1) {
        return util.select().fromTo(x0, y0, z0, x1, y1, z1);
    }

    private static Vec3 lever(int[] rig) {
        return vec(SeismicPonderBeats.lever(rig));
    }

    private static Vec3 vec(double[] p) {
        return new Vec3(p[0], p[1], p[2]);
    }

    private static Vec3 strike(int[] rig) {
        return new Vec3(rig[0] + 0.5, rig[1], rig[2] + 0.5);
    }

    private static String ui(String key, Object... args) {
        return Component.translatable("create_stats.ponder.seismic." + key, args).getString();
    }

    private static KitInstruction dust(int[] rig, BlockState ground, float height, int at) {
        return new KitInstruction(at, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final Random r = new Random(at * 31L + rig[0]);
                final double px = rig[0] + SeismicGeometry.PLATE_X / 16.0;
                final double pz = rig[2] + SeismicGeometry.PLATE_Z / 16.0;
                final int dust = 8 + Math.round(24 * height);
                for (int i = 0; i < dust; i++) {
                    scene.getWorld().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            px + r.nextGaussian() * 0.3, rig[1] + 0.1 + r.nextGaussian() * 0.05, pz + r.nextGaussian() * 0.3,
                            r.nextGaussian() * 0.12, Math.abs(r.nextGaussian()) * 0.12, r.nextGaussian() * 0.12);
                }
                final int puff = 2 + Math.round(6 * height);
                for (int i = 0; i < puff; i++) {
                    scene.getWorld().addParticle(ParticleTypes.POOF, px + r.nextGaussian() * 0.25,
                            rig[1] + 0.15 + r.nextGaussian() * 0.02, pz + r.nextGaussian() * 0.25, r.nextGaussian() * 0.02,
                            r.nextGaussian() * 0.02, r.nextGaussian() * 0.02);
                }
            }
        };
    }

    private static KitInstruction bankLevel(BlockPos bank, RigScript rig, int capacity, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int shown = -1;
            private long given;

            @Override
            protected void begin(PonderScene scene) {
                shown = -1;
                given = 0;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                given += rig.at(tick).arriving();
                final float scaled = (float) (Math.max(0L, capacity - given) / (double) capacity * 8.0);
                int lit = Math.max(0, Math.min(8, Math.round(scaled)));
                if (shown >= 0 && Math.abs(scaled - shown) < 0.65f) {
                    lit = shown;
                }
                if (lit != shown) {
                    shown = lit;
                    final BlockState s = scene.getWorld().getBlockState(bank);
                    if (s.hasProperty(com.createstats.block.CapacitorBankBlock.LEVEL)) {
                        scene.getWorld().setBlock(bank, s.setValue(com.createstats.block.CapacitorBankBlock.LEVEL, lit), 2);
                        scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
                    }
                }
            }
        };
    }

    private static SeismicPonderBeats.Cue cue(List<SeismicPonderBeats.Cue> cues, String name) {
        for (final SeismicPonderBeats.Cue c : cues) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("no cue " + name);
    }

    private static TextElementBuilder say(CreateSceneBuilder scene, Clock c, SeismicPonderBeats.Cue cue) {
        c.until(scene, cue.tick());
        TextElementBuilder b = scene.overlay().showText(cue.hold());
        b = cue.at() == null ? b.independent(cue.independent()) : b.pointAt(new Vec3(cue.at()[0], cue.at()[1], cue.at()[2]));
        return cue.key() ? b.attachKeyFrame() : b;
    }

    private static KitInstruction camera(float pitch, float yaw) {
        return new KitInstruction(0, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                scene.getTransform().xRotation.startWithValue(pitch);
                scene.getTransform().yRotation.startWithValue(yaw);
            }
        };
    }

    // ponder's rotateCameraY is an exponential chase, jerks on tick one. aim it at a smoothstep instead
    private static KitInstruction orbit(float from, float degrees, int start, int ticks) {
        return new KitInstruction(start, ticks + 1) {
            private float base = from;

            @Override
            protected void begin(PonderScene scene) {
                base = scene.getTransform().yRotation.getChaseTarget();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final double u = Math.min(1.0, Math.max(0.0, (tick - start) / (double) ticks));
                final double k = u * u * (3.0 - 2.0 * u);
                scene.getTransform().yRotation.chase(base + degrees * k, 1.0, LerpedFloat.Chaser.EXP);
            }
        };
    }

    public static void setup(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "seismic_scanner_setup", "Setting up a seismic survey",
                SeismicStage.PAD_PLATE, SeismicStage.PAD_SCALE, SeismicStage.PAD_OFFSET_Y);
        final Clock c = new Clock();
        final RigScript hero = SeismicPonderBeats.Setup.hero();
        final RigScript decoy = SeismicPonderBeats.Setup.decoy();
        final int length = SeismicPonderBeats.Setup.LENGTH;
        final int ready = SeismicPonderBeats.Setup.ready();
        final int[] rig = SeismicStage.PAD_RIG;
        final int[] d = SeismicStage.PAD_DECOY;
        final int[][] g = SeismicStage.PAD_GROUND;
        final List<SeismicPonderBeats.Cue> cues = SeismicPonderBeats.Setup.cues();
        scene.addInstruction(camera(SeismicStage.PAD_PITCH, SeismicStage.YAW));
        scene.addInstruction(RigDriver.drive(pos(rig), hero, 0, length));
        scene.addInstruction(RigDriver.drive(pos(d), decoy, 0, length));

        scene.world().showSection(box(util, 0, 0, 0, SeismicStage.PAD_PLATE - 1, 0, SeismicStage.PAD_PLATE - 1), Direction.UP);
        c.until(scene, SeismicPonderBeats.Setup.DIRT);
        scene.world().showSection(box(util, g[0][0], 1, g[0][2], g[1][0], 1, g[1][2]), Direction.DOWN);
        c.until(scene, SeismicPonderBeats.Setup.GRASS);
        scene.world().showSection(box(util, g[0][0], 2, g[0][2], g[1][0], 2, g[1][2]), Direction.DOWN);
        c.until(scene, SeismicPonderBeats.Setup.RIG);
        scene.world().showSection(util.select().position(pos(rig)), Direction.DOWN);
        c.idle(scene, 3);
        scene.world().showSection(util.select().position(pos(rig).above()), Direction.DOWN);

        say(scene, c, cue(cues, "intro"))
                .text("The Seismic Scanner drops a weight, and its thump maps one target in the ground below")
                .colored(PonderPalette.BLUE);

        c.until(scene, SeismicPonderBeats.Setup.DECOY);
        scene.world().showSection(util.select().position(pos(SeismicStage.PAD_PLANK)), Direction.DOWN);
        c.idle(scene, 4);
        scene.world().showSection(util.select().position(pos(d)), Direction.DOWN);
        c.idle(scene, 3);
        scene.world().showSection(util.select().position(pos(d).above()), Direction.DOWN);
        say(scene, c, cue(cues, "red"))
                .text("On planks or anything built, the lantern flashes red: there is no natural ground")
                .colored(PonderPalette.RED);
        say(scene, c, cue(cues, "natural"))
                .text("Stand it on natural ground: stone, dirt, sand, gravel, ice and the like")
                .colored(PonderPalette.GREEN);
        c.until(scene, SeismicPonderBeats.Setup.DECOY_OUT);
        scene.world().hideSection(util.select().position(pos(d).above()), Direction.UP);
        c.idle(scene, 3);
        scene.world().hideSection(util.select().position(pos(d)), Direction.UP);
        c.idle(scene, 3);
        scene.world().hideSection(util.select().position(pos(SeismicStage.PAD_PLANK)), Direction.UP);

        c.until(scene, SeismicPonderBeats.Setup.HOLD);
        scene.overlay().showControls(vec(SeismicPonderBeats.grip(rig)), Pointing.DOWN, 40).rightClick();
        c.until(scene, SeismicPonderBeats.Setup.HUB);
        final int pick = SeismicPonderBeats.Setup.PICK;
        final int r48 = SeismicPonderBeats.Setup.R48;
        final int r64 = SeismicPonderBeats.Setup.R64;
        final int release = SeismicPonderBeats.Setup.RELEASE;
        final Vec3 hubAt = vec(SeismicPonderBeats.Setup.hub());
        scene.addInstruction(SeismicOverlays.hub(pt -> hubAt, SeismicPonderBeats.Setup.HUB_DX,
                t -> t >= pick ? SeismicPonderSurvey.IRON : "", t -> t >= r64 ? 64 : t >= r48 ? 48 : 32, c.now,
                release + SeismicOverlays.FADE_OUT - c.now));
        say(scene, c, cue(cues, "tune"))
                .text("Hold use on it with an empty hand to open the tuner, then pick a target")
                .colored(PonderPalette.INPUT);
        c.until(scene, SeismicPonderBeats.Setup.SCROLL);
        scene.overlay().showControls(vec(SeismicPonderBeats.grip(rig)), Pointing.DOWN, 40).scroll();
        say(scene, c, cue(cues, "range"))
                .text("Scroll for the radius: %s, %s or %s blocks", SeismicCharge.MIN_RADIUS,
                        SeismicCharge.MIN_RADIUS + SeismicCharge.RADIUS_STEP, SeismicCharge.MAX_RADIUS)
                .colored(PonderPalette.INPUT);

        c.until(scene, release + 8);
        final Vec3 cell = vec(SeismicPonderBeats.Setup.cell());
        scene.addInstruction(SeismicOverlays.tag(pt -> cell, t -> Component.translatable(Blocks.IRON_ORE.getDescriptionId())
                .getString(), 0xFFFFFF, SeismicPonderBeats.Setup.CELL_TAG[0], SeismicPonderBeats.Setup.CELL_TAG[1], c.now, 70));
        c.until(scene, SeismicPonderBeats.Setup.COST);
        final double[] dogsAt = SeismicPonderBeats.Setup.dogs();
        final Vec3 dogs = new Vec3(dogsAt[0], dogsAt[1], dogsAt[2]);
        final String cost = Component.translatable("block.create_stats.seismic_scanner.tuner.range", SeismicCharge.MAX_RADIUS)
                .getString() + ", " + Component.translatable("block.create_stats.seismic_scanner.tuner.cost",
                String.format(Locale.ROOT, "%,d", SeismicCharge.burstFor(SeismicCharge.MAX_RADIUS))).getString();
        scene.addInstruction(SeismicOverlays.tag(pt -> dogs, t -> cost, 0xFFAA33, SeismicPonderBeats.Setup.DOGS_TAG[0],
                SeismicPonderBeats.Setup.DOGS_TAG[1], c.now, 76));
        say(scene, c, cue(cues, "cost"))
                .text("Twice the radius costs eight times the FE: the trip dogs rise to mark it")
                .colored(PonderPalette.WHITE);

        c.until(scene, SeismicPonderBeats.Setup.BANK);
        scene.world().showSection(util.select().position(pos(SeismicStage.PAD_BANK)), Direction.DOWN);
        c.until(scene, SeismicPonderBeats.Setup.CABLE);
        scene.world().showSection(util.select().position(pos(SeismicStage.PAD_CABLE)), Direction.DOWN);
        c.until(scene, SeismicPonderBeats.Setup.FEED);
        final int[] bank = SeismicStage.PAD_BANK;
        final FePaths.Path run = SeismicPonderBeats.Setup.run();
        scene.addInstruction(FeFlow.beads("seismic_feed", List.of(new FeFlow.Stream(run, t -> hero.at(t).arriving())),
                FeFlow.GREEN, c.now, length - c.now));
        scene.addInstruction(bankLevel(pos(bank), hero, 1_024_000, c.now, length - c.now));
        say(scene, c, cue(cues, "feed"))
                .text("Feed FE into the back or either side of its base, up to %s",
                        String.format(Locale.ROOT, "%,d FE/t", SeismicCharge.WINCH_FE_PER_TICK))
                .colored(PonderPalette.INPUT);
        say(scene, c, cue(cues, "hoist"))
                .text("The winch hoists the weight: how high it hangs is how much FE it holds")
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "ready"))
                .text("At the trip dogs the pawl drops home and the lantern turns green: ready")
                .colored(PonderPalette.GREEN);
        c.until(scene, length);
    }

    public static void survey(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "seismic_scanner_survey", "Reading the ground",
                SeismicStage.RIG_PLATE, SeismicStage.RIG_SCALE, SeismicStage.RIG_OFFSET_Y);
        final Clock c = new Clock();
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Survey.ground();
        final RigScript hero = SeismicPonderBeats.Survey.hero(ground);
        final int length = SeismicPonderBeats.Survey.length();
        final int impact = SeismicPonderBeats.Survey.impact();
        final int[] rig = SeismicStage.RIG;
        final int[][] g = SeismicStage.RIG_GROUND;
        final int[][] n = SeismicStage.NOTCH;
        final List<SeismicPonderBeats.Cue> cues = SeismicPonderBeats.Survey.cues();
        scene.addInstruction(camera(SeismicStage.RIG_PITCH, SeismicStage.YAW));
        scene.addInstruction(RigDriver.drive(pos(rig), hero, 0, length));

        scene.world().showSection(box(util, 0, 0, 0, SeismicStage.RIG_PLATE - 1, 0, SeismicStage.RIG_PLATE - 1), Direction.UP);

        final List<ElementLink<WorldSectionElement>> notch = new ArrayList<>();
        for (int y = g[0][1]; y <= g[1][1]; y++) {
            c.until(scene, SeismicPonderBeats.Survey.FIRST_LAYER + (y - g[0][1]) * SeismicPonderBeats.Survey.LAYER_STEP);
            final Selection layer = box(util, g[0][0], y, g[0][2], g[1][0], y, g[1][2]);
            final Selection quarter = box(util, n[0][0], y, n[0][2], n[1][0], y, n[1][2]);
            scene.world().showSection(layer.substract(quarter), Direction.DOWN);
            notch.add(scene.world().showIndependentSection(quarter, Direction.DOWN));
        }
        c.until(scene, SeismicPonderBeats.Survey.RIG);
        scene.world().showSection(util.select().position(pos(rig)), Direction.DOWN);
        c.idle(scene, 3);
        scene.world().showSection(util.select().position(pos(rig).above()), Direction.DOWN);

        c.until(scene, SeismicPonderBeats.Survey.WITNESS);
        final double[] w = SeismicStage.RIG_WITNESS;
        final Walker.Walk stand = new Walker.Walk(w[1], new int[] {0}, new double[][] {{w[0], w[2]}})
                .look(new Vec3(rig[0] + 0.5, rig[1] + 0.8, rig[2] + 0.5), 0, Integer.MAX_VALUE);
        final Vec3 standAt = stand.at(0);
        scene.special().createBirb(standAt, () -> Walker.pose(stand, standAt, 0.0));

        c.until(scene, SeismicPonderBeats.Survey.CUT);
        for (final ElementLink<WorldSectionElement> l : notch) {
            scene.world().hideIndependentSection(l, Direction.NORTH);
        }
        say(scene, c, cue(cues, "bowl"))
                .text("It reads a bowl of ground under the plate, as deep as its radius, never above it")
                .colored(PonderPalette.BLUE);

        c.until(scene, SeismicPonderBeats.Survey.RECORDER);
        final String title = "Reading the ground";
        final int pull = SeismicPonderBeats.Survey.PULL;
        scene.addInstruction(SeismicOverlays.recorder(title, ui("recorder"), t -> t >= impact, impact,
                ground.strength(), ground.echoes(), SeismicPonderBeats.Survey.RECORDER_SPAN, c.now, length - c.now));

        c.until(scene, SeismicPonderBeats.Survey.LEVER);

        scene.overlay().showControls(lever(rig), Pointing.UP, SeismicPonderBeats.Survey.PULL - SeismicPonderBeats.Survey.LEVER)
                .rightClick();
        c.until(scene, pull);
        final java.util.function.IntUnaryOperator slow = SeismicPonderBeats.Survey.slow();
        final int slowEnd = impact + ground.lastEcho() + 16;
        scene.addInstruction(SlowMotion.slow(slow, c.now, slowEnd - c.now));
        scene.addInstruction(SeismicOverlays.slowNote(ui("slow", SeismicPonderBeats.SLOW), t -> slow.applyAsInt(t) > 2, c.now,
                slowEnd + 10 - c.now));
        c.until(scene, impact);
        scene.addInstruction(dust(rig, Blocks.GRASS_BLOCK.defaultBlockState(), hero.at(impact).dropHeight(), c.now));
        final Vec3 strike = strike(rig);
        scene.addInstruction(Wave.wave("seismic_wave", strike, rigFaces(), 9.0, ground, impact, c.now, 40));

        final String line = SeismicOverlays.readingLine(ground);
        final int readFrom = SeismicPonderBeats.Survey.reading();
        final int readFor = SeismicPonderBeats.Survey.readingTicks();
        scene.addInstruction(VeinView.show("seismic_veins", ground, impact, VeinView.fixed(stand.eye(0)), VeinView.never(),
                SeismicOverlays.readingClear(line, readFrom, readFor), c.now, length - c.now));

        say(scene, c, cue(cues, "drop"))
                .text("Pull the lever: the weight drops, and its thump runs out through the ground")
                .colored(PonderPalette.INPUT);

        say(scene, c, cue(cues, "pen"))
                .text("The recorder's pen marks the thump and every echo")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "echo"))
                .text("Each vein lights up as its echo gets back: the nearest first, the farthest last")
                .colored(PonderPalette.GREEN);
        c.until(scene, readFrom);
        scene.addInstruction(SeismicOverlays.reading(line, c.now, readFor));
        say(scene, c, cue(cues, "reading"))
                .text("When the last echo is in, everyone within %s blocks at the blow gets the reading", (int) SeismicScannerBE.WITNESS_RADIUS)
                .colored(PonderPalette.WHITE);

        c.until(scene, SeismicPonderBeats.Survey.orbit());
        scene.addInstruction(orbit(SeismicStage.YAW, SeismicPonderBeats.Survey.ORBIT_DEGREES, c.now,
                SeismicPonderBeats.Survey.ORBIT_TICKS));
        say(scene, c, cue(cues, "coal"))
                .text("Only the target answers: the coal stays dark on an iron survey")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "labels"))
                .text("Each vein shows its ore, its size in blocks and the level it lies at")
                .colored(PonderPalette.WHITE);

        c.until(scene, SeismicPonderBeats.Survey.back());
        for (int y = g[0][1]; y <= g[1][1]; y++) {
            scene.world().showIndependentSection(box(util, n[0][0], y, n[0][2], n[1][0], y, n[1][2]), Direction.DOWN);
        }
        say(scene, c, cue(cues, "back"))
                .text("Back in the ground, the veins glow through the rock for you to dig to")
                .colored(PonderPalette.GREEN);
        c.until(scene, length);
    }

    static List<Wave.Face> rigFaces() {
        final int[][] g = SeismicStage.RIG_GROUND;
        final int[][] n = SeismicStage.NOTCH;
        final double top = g[1][1] + 1;
        final double lo = g[0][0];
        final double hi = g[1][0] + 1;
        final double cut = n[1][0] + 1;
        return List.of(
                new Wave.Face(1, top, lo, hi, g[0][2], g[1][2] + 1, new double[] {lo, cut, g[0][2], n[1][2] + 1}, 1),
                new Wave.Face(2, g[0][2], cut, hi, g[0][1], top, null, -1),
                new Wave.Face(0, lo, n[1][2] + 1, g[1][2] + 1, g[0][1], top, null, -1),
                new Wave.Face(2, n[1][2] + 1, lo, cut, g[0][1], top, null, -1),
                new Wave.Face(0, cut, g[0][2], n[1][2] + 1, g[0][1], top, null, -1));
    }

    static List<Wave.Face> stripFaces() {
        final int[][] g = SeismicStage.STRIP_GROUND;
        final double top = g[1][1] + 1;
        return List.of(
                new Wave.Face(1, top, g[0][0], g[1][0] + 1, g[0][2], g[1][2] + 1, null, 1),
                new Wave.Face(2, g[0][2], g[0][0], g[1][0] + 1, g[0][1], top, null, -1));
    }

    public static void reading(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "seismic_scanner_reading", "Reading the map",
                SeismicStage.STRIP_PLATE, SeismicStage.STRIP_SCALE, SeismicStage.STRIP_OFFSET_Y);
        scene.configureBasePlate(SeismicStage.STRIP_PLATE_X, 0, SeismicStage.STRIP_PLATE);
        ShadowFade.removeIfUnavailable(scene);
        final Clock c = new Clock();
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Reading.ground();
        final RigScript hero = SeismicPonderBeats.Reading.hero(ground);
        final int length = SeismicPonderBeats.Reading.LENGTH;
        final int impact = SeismicPonderBeats.Reading.impact();
        final int[] rig = SeismicStage.STRIP_RIG;
        final int[][] g = SeismicStage.STRIP_GROUND;
        final Walker.Walk walk = SeismicPonderBeats.Reading.walk();
        final List<SeismicPonderBeats.Cue> cues = SeismicPonderBeats.Reading.cues();
        scene.addInstruction(camera(SeismicStage.STRIP_PITCH, SeismicStage.STRIP_YAW));
        scene.addInstruction(ShadowFade.fade(false, 0));
        scene.addInstruction(RigDriver.drive(pos(rig), hero, 0, length));

        scene.world().showSection(box(util, 0, 0, 0, SeismicStage.STRIP_LENGTH - 1, 0, SeismicStage.STRIP_DEPTH - 1),
                Direction.UP);
        final int chunk = SeismicPonderBeats.Reading.CHUNK;
        for (int i = 0; i * chunk <= g[1][0]; i++) {
            c.until(scene, SeismicPonderBeats.Reading.FIRST_CHUNK + i * SeismicPonderBeats.Reading.CHUNK_STEP);
            final int x1 = g[1][0] - i * chunk;
            final int x0 = Math.max(g[0][0], x1 - chunk + 1);
            scene.world().showSection(box(util, x0, g[0][1], g[0][2], x1, g[1][1], g[1][2]), Direction.DOWN);
        }
        c.until(scene, SeismicPonderBeats.Reading.RIG);
        scene.world().showSection(util.select().position(pos(rig)), Direction.DOWN);
        c.idle(scene, 3);
        scene.world().showSection(util.select().position(pos(rig).above()), Direction.DOWN);

        c.until(scene, SeismicPonderBeats.Reading.PARROT);
        final Vec3 start = walk.at(0);
        scene.special().createBirb(start, () -> Walker.pose(walk, start, 90.0));
        c.until(scene, SeismicPonderBeats.Reading.YOU);
        final int[] clock = {0};
        scene.addInstruction(new KitInstruction(c.now, length - c.now) {
            @Override
            protected void step(PonderScene s, int tick, boolean last) {
                clock[0] = tick;
            }
        });
        scene.addInstruction(SeismicOverlays.tag(pt -> walk.at(clock[0] - 1 + pt).add(0, 1.0, 0), t -> ui("you"), 0xFFFFFF,
                0, -6, c.now, length - c.now));
        c.until(scene, SeismicPonderBeats.Reading.RECORDER);
        scene.addInstruction(SeismicOverlays.recorder("Reading the map", ui("recorder"), t -> t >= impact, impact,
                ground.strength(), ground.echoes(), SeismicPonderBeats.Reading.RECORDER_SPAN, c.now, length - c.now));

        c.until(scene, SeismicPonderBeats.Reading.LEVER);
        scene.overlay().showControls(lever(rig), Pointing.UP, SeismicPonderBeats.Reading.PULL - SeismicPonderBeats.Reading.LEVER)
                .rightClick();

        final double top = g[1][1] + 1;
        final Walker.Walk atRig = new Walker.Walk(top, new int[] {0}, new double[][] {{rig[0] + 0.5, rig[2] + 0.5}});
        final List<Walker.Ring> witness = List.of(new Walker.Ring(SeismicScannerBE.WITNESS_RADIUS, Walker.WITNESS_RGB, 1f,
                ui("blocks", (int) SeismicScannerBE.WITNESS_RADIUS)));
        c.until(scene, SeismicPonderBeats.Reading.WITNESS_RING);
        scene.addInstruction(Walker.rings("seismic_witness", atRig, top, g[0][0], g[1][0] + 1, g[0][2], g[1][2] + 1, witness,
                c.now, SeismicPonderBeats.Reading.WITNESS_RING_END - c.now));
        c.until(scene, impact);
        scene.addInstruction(dust(rig, Blocks.GRASS_BLOCK.defaultBlockState(), hero.at(impact).dropHeight(), c.now));
        scene.addInstruction(Wave.wave("seismic_wave", strike(rig), stripFaces(), 36.0, ground, impact, c.now,
                SeismicWave.listenTicks(SeismicCharge.MAX_RADIUS)));
        scene.addInstruction(VeinView.show("seismic_veins", ground, impact, t -> walk.eye(t), walk::looking,
                VeinView.nothingKept(), c.now, length - c.now));

        say(scene, c, cue(cues, "witness"))
                .text("Stand within %s blocks when the weight lands, and the veins light for you", (int) SeismicScannerBE.WITNESS_RADIUS)
                .colored(PonderPalette.BLUE);
        say(scene, c, cue(cues, "overview"))
                .text("For %s seconds the whole survey stays lit", VeinVisibility.OVERVIEW_TICKS / 20)
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "drain"))
                .text("Then it fades to what is near you, the far veins first")
                .colored(PonderPalette.WHITE);

        c.until(scene, SeismicPonderBeats.Reading.RINGS);
        final int walkEnd = SeismicPonderBeats.Reading.walkEnd();
        scene.addInstruction(Walker.rings("seismic_rings", walk, top, g[0][0], g[1][0] + 1, g[0][2], g[1][2] + 1, List.of(
                new Walker.Ring(VeinVisibility.NEAR, Walker.NEAR_RGB, 1f, ui("blocks", (int) VeinVisibility.NEAR)),
                new Walker.Ring(VeinVisibility.FAR, Walker.FAR_RGB, 0.85f, ui("blocks", (int) VeinVisibility.FAR))),
                c.now, walkEnd + 20 - c.now));
        say(scene, c, cue(cues, "rings"))
                .text("A vein is plain within %s blocks of you, and gone past %s", (int) VeinVisibility.NEAR, (int) VeinVisibility.FAR)
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "walk"))
                .text("Walk on, and veins come up as you near them")
                .colored(PonderPalette.WHITE);

        c.until(scene, SeismicPonderBeats.Reading.LOOK - 4);
        scene.addInstruction(Walker.sight("seismic_sight", walk, c.now, length - c.now));
        say(scene, c, cue(cues, "look"))
                .text("Rest your eyes on the rig, or on its recorder up close, to light the whole map")
                .colored(PonderPalette.INPUT);

        c.until(scene, SeismicPonderBeats.Reading.TIP_RING);
        scene.addInstruction(Walker.rings("seismic_witness_again", atRig, top, g[0][0], g[1][0] + 1, g[0][2], g[1][2] + 1,
                witness, c.now, length - c.now));
        say(scene, c, cue(cues, "tip"))
                .text("After a relog, rest your eyes on its recorder within %s blocks to read its maps back",
                        (int) SeismicScannerBE.WITNESS_RADIUS)
                .colored(PonderPalette.INPUT);
        c.until(scene, length);
    }
}
