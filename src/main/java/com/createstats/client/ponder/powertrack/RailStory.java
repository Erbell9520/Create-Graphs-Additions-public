package com.createstats.client.ponder.powertrack;

import com.createstats.power.CableTier;
import com.createstats.power.PortableFeDirectionLock;
import com.createstats.powertrack.CollectorGeometry;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.CollectorSwing;
import com.createstats.powertrack.core.FeederLamp;
import com.createstats.powertrack.core.FeederPlan;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;
import com.createstats.powertrack.core.RailTick;
import com.createstats.powertrack.core.SectionExchange;
import com.createstats.powertrack.core.SectionGraph;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class RailStory {
    public static final int LOAD_MEMORY = 40;

    public static final int SUB_STEPS = 3;

    public static final double REACH = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;

    public record Station(int capacity, int stored) {
    }

    public record FeederIn(int station, int lo, int hi, double at, int wiredAt) {
    }

    public record Setting(int tick, int feeder, FeederPlan.Mode mode, int dialFe, int floorPct) {
    }

    public record CarIn(int capacity, int stored, int authoredX, int[] collectors, CarRun run, int from) {
        public double centreX(int collector, double carX) {
            return authoredX + collectors[collector] + 0.5 + carX;
        }
    }

    public record Shoe(double angle, double angleOld, boolean touching, int section, double x, double y, double z,
                       double railTop, boolean arc) {
    }

    public record FeederFrame(FeederPlan.Mode mode, int dialFe, int floorPct, int buffer, FeederPlan.Reason reason,
                              int railFlow, int lamp, SectionView view, boolean autoSupplying, boolean floorHolding,
                              int circuitFlow, int stationStored) {
    }

    public record Frame(int tick, double carX, double carXOld, Shoe[] shoes, int carStored, int carFlow, int carSection,
                        FeederFrame[] feeders, SectionView[] sections) {
    }

    private final RailLayout layout;
    private final List<Station> stations;
    private final List<FeederIn> feeders;
    private final List<Setting> settings;
    private final CarIn car;
    private final int collectorY;
    private final int lineZ;
    private final int base;

    public RailStory(RailLayout layout, List<Station> stations, List<FeederIn> feeders, List<Setting> settings, CarIn car,
                     int collectorY, int lineZ, int base) {
        this.layout = layout;
        this.stations = stations;
        this.feeders = feeders;
        this.settings = settings;
        this.car = car;
        this.collectorY = collectorY;
        this.lineZ = lineZ;
        this.base = base;
    }

    private static final class FeederState {
        FeederPlan.Mode mode = FeederPlan.Mode.SUPPLY;
        int dialFe;
        int floorPct = 50;
        int buffer;
        boolean floorHolding;
        boolean autoSupplying = true;
        FeederPlan.Plan plan;
        boolean onRail;
    }

    public List<Frame> play(int ticks) {
        final int ceiling = RailPower.sectionCeiling(base);
        final int shoeRate = RailPower.collectorRate(base);
        final int cableRate = CableTier.SURFACE.rate(base);
        final int sectionCount = layout.sections().sectionCount();
        final int[] stationStored = new int[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            stationStored[i] = stations.get(i).stored();
        }
        final FeederState[] fs = new FeederState[feeders.size()];
        for (int i = 0; i < fs.length; i++) {
            fs[i] = new FeederState();
            fs[i].dialFe = ceiling;
        }
        final int shoes = car == null ? 0 : car.collectors().length;
        final CollectorSwing[] swings = new CollectorSwing[shoes];
        final double[] angle = new double[shoes];
        final boolean[] wasTouching = new boolean[shoes];
        for (int s = 0; s < shoes; s++) {
            swings[s] = new CollectorSwing();
        }
        int carStored = car == null ? 0 : car.stored();
        int lastLoadTick = Integer.MIN_VALUE / 2;
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final List<Frame> frames = new ArrayList<>(ticks);
        double carXOld = car == null ? 0.0 : car.run().at(0);
        for (int t = 0; t < ticks; t++) {
            for (Setting s : settings) {
                if (s.tick() == t) {
                    final FeederState f = fs[s.feeder()];
                    f.mode = s.mode();
                    f.dialFe = s.dialFe();
                    f.floorPct = s.floorPct();
                    f.onRail = true;
                }
            }

            final double carX = car == null ? 0.0 : car.run().at(t);
            final double[] angleOld = angle.clone();
            final int[] shoeSection = new int[shoes];
            final boolean[] touching = new boolean[shoes];
            final double[][] shoeAt = new double[shoes][];
            final double[] railTop = new double[shoes];
            for (int s = 0; s < shoes; s++) {
                final int trail = PowerTrackStage.trailSign(car.collectors()[s]);
                final Vec3 trailV = new Vec3(trail, 0, 0);
                CollectorPose.Rest rest = null;
                for (int k = 1; k <= SUB_STEPS; k++) {
                    final double x = carXOld + (carX - carXOld) * k / SUB_STEPS;
                    final Vec3 centre = new Vec3(car.centreX(s, x), collectorY + 0.5, lineZ + 0.5);
                    rest = CollectorGeometry.rest(centre, trailV, collectorY, this::railTopAt);
                    final double seconds = t * 0.05 + k / 60.0;
                    angle[s] = swings[s].step(seconds, rest.angleDeg(), rest.touching());
                }
                final Vec3 centre = new Vec3(car.centreX(s, carX), collectorY + 0.5, lineZ + 0.5);
                final Vec3 at = CollectorGeometry.shoeAt(centre, trailV, rest.angleDeg());
                final RailLayout.Hit hit = layout.nearest(at.x, at.y, at.z, REACH, 1.0, 2.0);
                touching[s] = rest.touching();
                shoeSection[s] = rest.touching() && hit != null && hit.contact() ? hit.section() : -1;
                railTop[s] = hit != null && hit.contact() ? hit.railTopY() : Double.NaN;
                shoeAt[s] = new double[] {at.x, at.y, at.z};
            }

            final int[] circuitFlow = new int[fs.length];
            for (int i = 0; i < fs.length; i++) {
                final FeederState f = fs[i];
                final int station = feeders.get(i).station();
                if (f.plan == null || t < feeders.get(i).wiredAt()) {
                    continue;
                }
                if (f.plan.chargeOpen()) {
                    final int room = Math.max(0, FeederPlan.bufferCapacity(f.dialFe) - f.buffer);
                    final int move = Math.min(Math.min(room, cableRate), stationStored[station]);
                    stationStored[station] -= move;
                    f.buffer += move;
                    circuitFlow[i] = move;
                } else if (f.plan.offersToGrid()) {
                    final int room = stations.get(station).capacity() - stationStored[station];
                    final int move = Math.min(Math.min(f.buffer, cableRate), Math.max(0, room));
                    stationStored[station] += move;
                    f.buffer -= move;
                    circuitFlow[i] = -move;
                }
            }

            final List<RailTick.FeederIn> offers = new ArrayList<>();
            final int[] lamp = new int[fs.length];
            final int[] feederSection = new int[fs.length];
            for (int i = 0; i < fs.length; i++) {
                final FeederState f = fs[i];
                final Station st = stations.get(feeders.get(i).station());

                final boolean wired = t >= feeders.get(i).wiredAt();
                f.plan = FeederPlan.plan(new FeederPlan.Input(f.mode, f.onRail, false, f.dialFe, f.buffer,
                        wired ? stationStored[feeders.get(i).station()] : 0, wired ? st.capacity() : 0, f.floorPct,
                        f.floorHolding, f.autoSupplying));
                f.floorHolding = f.plan.floorHolding();
                f.autoSupplying = f.plan.autoSupplying();
                feederSection[i] = f.onRail
                        ? layout.sections().sectionAt(feeders.get(i).lo(), feeders.get(i).hi(), feeders.get(i).at()) : -1;
                if (!f.onRail) {
                    continue;
                }
                final int capacity = FeederPlan.bufferCapacity(f.dialFe);
                final int available = Math.max(0, Math.min(f.plan.railAvailable(), f.buffer));
                final int room = Math.max(0, Math.min(f.plan.railRoom(), capacity - f.buffer));

                lamp[i] = FeederLamp.of(f.plan.role(), available, room);
                offers.add(new RailTick.FeederIn(feederSection[i],
                        new SectionExchange.Feeder(i, f.plan.role(), available, room, f.plan.trainFloorPct())));
            }

            final List<RailTick.CarIn> cars = new ArrayList<>();
            final boolean carThere = car != null && t >= car.from();
            if (carThere) {
                cars.add(new RailTick.CarIn(1L, shoeSection, carStored, car.capacity(), true, true));
            }
            final RailTick.Result result = RailTick.run(offers, cars, sectionCount, ceiling, shoeRate, lock, t);
            final int[] railFlow = new int[fs.length];
            int k = 0;
            for (int i = 0; i < fs.length; i++) {
                if (!fs[i].onRail) {
                    continue;
                }
                final int delta = result.feederDelta()[k++];
                fs[i].buffer += delta;
                railFlow[i] = -delta;
            }
            final int carFlow = carThere ? result.carDelta()[0] : 0;
            carStored += carFlow;
            final int carSection = carThere ? result.carSection()[0] : -1;

            final boolean underLoad = t - lastLoadTick <= LOAD_MEMORY;
            if (carFlow != 0) {
                lastLoadTick = t;
            }

            final SectionView[] views = new SectionView[sectionCount];
            for (int s = 0; s < sectionCount; s++) {
                final List<SectionExchange.Feeder> on = new ArrayList<>();
                for (RailTick.FeederIn o : offers) {
                    if (o.section() == s) {
                        on.add(o.feeder());
                    }
                }
                final SectionExchange.Plan plan = result.plans()[s];
                views[s] = new SectionView(SectionExchange.energised(on).ordinal(), on.size(), carSection == s ? 1 : 0,
                        plan == null ? 0 : plan.railFlow());
            }

            final Shoe[] shoeFrames = new Shoe[shoes];
            for (int s = 0; s < shoes; s++) {
                boolean arc = false;
                if (touching[s] && !wasTouching[s] && underLoad && shoeSection[s] >= 0) {
                    final int state = views[shoeSection[s]].state();
                    arc = state == SectionView.LIVE || state == SectionView.COLLECTING;
                }
                wasTouching[s] = touching[s];
                shoeFrames[s] = new Shoe(angle[s], angleOld[s], touching[s], shoeSection[s], shoeAt[s][0], shoeAt[s][1],
                        shoeAt[s][2], railTop[s], arc);
            }
            final FeederFrame[] feederFrames = new FeederFrame[fs.length];
            for (int i = 0; i < fs.length; i++) {
                final FeederState f = fs[i];
                final int station = feeders.get(i).station();
                feederFrames[i] = new FeederFrame(f.mode, f.dialFe, f.floorPct, f.buffer, f.onRail ? f.plan.reason()
                        : FeederPlan.Reason.NO_RAIL, railFlow[i], f.onRail ? lamp[i] : 0,
                        feederSection[i] >= 0 ? views[feederSection[i]] : SectionView.NONE, f.autoSupplying,
                        f.floorHolding, circuitFlow[i], stationStored[station]);
            }
            frames.add(new Frame(t, carX, carXOld, shoeFrames, carStored, carFlow, carSection, feederFrames, views));
            carXOld = carX;
        }
        return frames;
    }

    private double railTopAt(Vec3 p) {
        final RailLayout.Hit hit = layout.nearest(p.x, p.y, p.z, REACH, 1.0, 2.0);
        return hit != null && hit.contact() ? hit.railTopY() : Double.NaN;
    }

    public static SectionView lampView(SectionView[] views, SectionGraph sections, int lo, int hi, double cutAt, boolean west) {
        final double eps = 0.01;
        final int s = sections.sectionAt(lo, hi, west ? cutAt - eps : cutAt + eps);
        return s >= 0 && s < views.length && views[s] != null ? views[s] : SectionView.NONE;
    }
}
