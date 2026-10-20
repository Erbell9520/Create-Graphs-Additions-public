package com.createstats.client.ponder;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public final class Motion {
    private Motion() {
    }

    public static KitInstruction ship(FlightRig rig, int start, int ticks) {
        return new Follow(start, ticks, rig, rig::ship, rig::shipOffset, true);
    }

    public static KitInstruction world(FlightRig rig, ElementLink<WorldSectionElement> link, boolean ground,
                                       int start, int ticks) {
        return new Follow(start, ticks, rig, () -> link, t -> rig.worldOffset(t, ground), false);
    }

    public static KitInstruction offsets(FlightRig rig, ElementLink<WorldSectionElement> link,
                                         IntFunction<Vec3> offset, int start, int ticks) {
        return new Follow(start, ticks, rig, () -> link, offset, false);
    }

    private static final class Follow extends KitInstruction {
        private final FlightRig rig;
        private final java.util.function.Supplier<ElementLink<WorldSectionElement>> link;
        private final IntFunction<Vec3> offset;
        private final boolean clock;

        Follow(int start, int ticks, FlightRig rig, java.util.function.Supplier<ElementLink<WorldSectionElement>> link,
               IntFunction<Vec3> offset, boolean clock) {
            super(start, ticks);
            this.rig = rig;
            this.link = link;
            this.offset = offset;
            this.clock = clock;
        }

        private boolean placed;

        @Override
        protected void begin(PonderScene scene) {
            placed = false;
        }

        @Override
        protected void step(PonderScene scene, int tick, boolean last) {
            if (clock) rig.shown(tick);
            WorldSectionElement e = element(scene);
            if (e == null) return;

            e.setAnimatedOffset(offset.apply(tick), !placed);
            placed = true;
        }

        private WorldSectionElement element(PonderScene scene) {
            ElementLink<WorldSectionElement> l = link.get();
            return l == null ? null : scene.resolve(l);
        }
    }

    public static KitInstruction glide(ElementLink<WorldSectionElement> link, Vec3 delta, Ease ease, int start, int ticks) {
        return new Glide(start, ticks, link, delta, ease, false);
    }

    public static KitInstruction turn(ElementLink<WorldSectionElement> link, Vec3 delta, Ease ease, int start, int ticks) {
        return new Glide(start, ticks, link, delta, ease, true);
    }

    private static final class Glide extends KitInstruction {
        private final ElementLink<WorldSectionElement> link;
        private final Vec3 delta;
        private final Ease ease;
        private final boolean rotate;
        private Vec3 from = Vec3.ZERO;

        Glide(int start, int ticks, ElementLink<WorldSectionElement> link, Vec3 delta, Ease ease, boolean rotate) {
            super(start, ticks);
            this.link = link;
            this.delta = delta;
            this.ease = ease;
            this.rotate = rotate;
        }

        @Override
        protected void begin(PonderScene scene) {
            WorldSectionElement e = scene.resolve(link);
            if (e != null) from = rotate ? e.getAnimatedRotation() : e.getAnimatedOffset();
        }

        @Override
        protected void step(PonderScene scene, int tick, boolean last) {
            WorldSectionElement e = scene.resolve(link);
            if (e == null) return;
            double k = ease.clamped((tick - start + 1) / (double) totalTicks);
            Vec3 v = from.add(delta.scale(k));
            if (rotate) e.setAnimatedRotation(v, false);
            else e.setAnimatedOffset(v, false);
        }
    }

    public static KitInstruction treadmill(FlightRig rig, ElementLink<WorldSectionElement> plate, int plateLength,
                                           List<Selection> chunks, int x0, int length, int viewMin, int viewMax,
                                           int start, int ticks) {
        return new Treadmill(start, ticks, rig, plate, plateLength, chunks, x0, length, viewMin, viewMax);
    }

    private static final class Treadmill extends KitInstruction {
        private static final int FADE = 12;

        private final FlightRig rig;
        private final ElementLink<WorldSectionElement> plateLink;
        private final int plateLength;
        private final List<Selection> selections;
        private final int x0;
        private final int length;
        private final int viewMin;
        private final int viewMax;
        private final List<Chunk> chunks = new ArrayList<>();
        private int plateFade = -1;

        Treadmill(int start, int ticks, FlightRig rig, ElementLink<WorldSectionElement> plate, int plateLength,
                  List<Selection> chunks, int x0, int length, int viewMin, int viewMax) {
            super(start, ticks);
            this.rig = rig;
            this.plateLink = plate;
            this.plateLength = plateLength;
            this.selections = chunks;
            this.x0 = x0;
            this.length = length;
            this.viewMin = viewMin;
            this.viewMax = viewMax;
        }

        @Override
        protected void begin(PonderScene scene) {
            chunks.clear();
            plateFade = -1;
            for (int i = 0; i < selections.size(); i++) {
                WorldSectionElementImpl e = new WorldSectionElementImpl(selections.get(i));
                scene.addElement(e);
                e.setVisible(false);
                e.forceApplyFade(0.0f);
                chunks.add(new Chunk(e, x0 + i * length));
            }
        }

        @Override
        protected void step(PonderScene scene, int tick, boolean last) {
            double run = rig.along(tick);
            double fall = rig.frame(tick);
            int loop = selections.size() * length;
            for (Chunk c : chunks) c.step(run, fall, loop);
            WorldSectionElement plate = plateLink == null ? null : scene.resolve(plateLink);
            if (plate != null) {
                plate.setAnimatedOffset(new Vec3(-run, -fall, 0.0), false);
                if (plateFade < 0 && plateLength - run < viewMin) {
                    plateFade = 0;
                    plate.setFadeVec(new Vec3(0.5, 0.0, 0.0));
                }
                if (plateFade >= 0 && plateFade <= FADE) {
                    float f = 1.0f - plateFade / (float) FADE;
                    plate.setFade(f * f);
                    if (plateFade == FADE) plate.setVisible(false);
                    plateFade++;
                }
            }
        }

        private final class Chunk {
            final WorldSectionElementImpl e;
            final int base;

            int lap;

            int fading;
            boolean shown;

            Chunk(WorldSectionElementImpl e, int base) {
                this.e = e;
                this.base = base;
            }

            void step(double run, double fall, int loop) {
                double x = base - run + lap * (double) loop;
                boolean moved = false;

                while (x + length < viewMin - 2 && !shown && fading == 0) {
                    lap++;
                    x += loop;
                    moved = true;
                }
                Vec3 offset = new Vec3(x - base, -fall, 0.0);
                e.setAnimatedOffset(offset, moved);
                boolean wanted = x < viewMax && x + length > viewMin;

                if (wanted && !shown && fading == 0) {
                    e.setVisible(true);
                    e.setFadeVec(new Vec3(-0.5, 0.0, 0.0));
                    fading = 1;
                    shown = true;
                } else if (!wanted && shown && fading == 0) {
                    e.setFadeVec(new Vec3(0.5, 0.0, 0.0));
                    fading = -1;
                    shown = false;
                }
                if (fading > 0) {
                    float t = Math.min(1.0f, fading / (float) FADE);
                    e.setFade((float) Ease.OUT_CUBIC.at(t));
                    fading = fading >= FADE ? 0 : fading + 1;
                } else if (fading < 0) {
                    float t = Math.min(1.0f, -fading / (float) FADE);
                    e.setFade(1.0f - (float) Ease.IN_CUBIC.at(t));
                    if (-fading >= FADE) {
                        e.setVisible(false);
                        fading = 0;
                    } else {
                        fading--;
                    }
                }
            }
        }
    }

    public static KitInstruction pilot(FlightRig rig, ElementLink<ParrotElement> parrot, Vec3 from, int flight,
                                       int start, int ticks) {
        return new Pilot(start, ticks, rig, parrot, from, flight);
    }

    private static final class Pilot extends KitInstruction {
        private final FlightRig rig;
        private final ElementLink<ParrotElement> link;
        private final Vec3 from;
        private final int flight;

        Pilot(int start, int ticks, FlightRig rig, ElementLink<ParrotElement> link, Vec3 from, int flight) {
            super(start, ticks);
            this.rig = rig;
            this.link = link;
            this.from = from;
            this.flight = Math.max(0, flight);
        }

        @Override
        protected void step(PonderScene scene, int tick, boolean last) {
            ParrotElement p = scene.resolve(link);

            if (p != null) p.setPositionOffset(position(tick), tick - start < 2);
        }

        private Vec3 position(int tick) {
            Vec3 ship = rig.shipOffset(tick);
            int into = tick - start;
            if (flight == 0 || into >= flight) return ship.subtract(from);
            double t = Ease.OUT_CUBIC.at(into / (double) flight);

            Vec3 line = from.scale(-t);
            double hop = Math.sin(Math.PI * t) * 1.2 * (1.0 - t);
            return ship.add(line.x, line.y + hop, line.z);
        }
    }
}
