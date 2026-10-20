package com.createstats.powertrack;

import com.createstats.CreateStats;
import com.createstats.blockentity.PortablePowerInterfaceBE;
import com.createstats.blockentity.TrackFeederBE;
import com.createstats.power.FeRates;
import com.createstats.power.PortableFeDirectionLock;
import com.createstats.power.PortableFeTransfer;
import com.createstats.power.TrainBankLedger;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;
import com.createstats.powertrack.core.RailTick;
import com.createstats.powertrack.core.SectionExchange;
import com.createstats.powertrack.core.SectionGraph;
import com.createstats.powertrack.net.CarCurrentPacket;
import com.createstats.powertrack.net.RailStatePacket;
import com.createstats.train.power.CarriedDeviceSync;
import com.createstats.train.power.ContraptionFeStores;
import com.createstats.train.power.TrainChargeProbe;
import com.createstats.train.smart.RailSnapshot;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class PowerTrackService {
    private static final int CHECK_INTERVAL = 10;

    private static final int STATE_INTERVAL = 10;

    private static final int TRAIN_FACT_TTL = 60;

    private static final Map<UUID, GraphRail> RAILS = new HashMap<>();
    private static final Map<UUID, Long> CHECKED = new HashMap<>();
    private static final Map<String, Integer> SENT_STATES = new HashMap<>();
    private static final Map<Integer, int[]> SENT_CURRENT = new HashMap<>();

    private static final Map<Long, Car> CARS = new LinkedHashMap<>();

    private static final PortableFeDirectionLock TRAIN_LOCK = new PortableFeDirectionLock();

    private static final Map<UUID, TrainFact> TRAINS = new HashMap<>();

    private PowerTrackService() {
    }

    private static final class Car {
        final ServerLevel level;
        final CarriageContraptionEntity entity;
        final Train train;
        final List<double[]> shoes = new ArrayList<>();
        ContraptionFeStores stores;
        TrainBankLedger ledger;

        boolean inGap;

        Car(ServerLevel level, CarriageContraptionEntity entity, Train train) {
            this.level = level;
            this.entity = entity;
            this.train = train;
        }
    }

    public record TrainFact(int state, int flow, long tick) {
        public static final int NO_COLLECTORS = 0;
        public static final int UNPOWERED = 1;
        public static final int LIVE_IDLE = 2;
        public static final int CHARGING = 3;
        public static final int GIVING = 4;

        public static final int IN_GAP = 5;
    }

    public static void noteShoe(MovementContext context) {
        if (!(context.world instanceof ServerLevel level) || context.contraption == null
                || !(context.contraption.entity instanceof CarriageContraptionEntity entity)) {
            return;
        }
        final Carriage carriage = entity.getCarriage();
        if (carriage == null || carriage.train == null) {
            return;
        }
        final Train train = carriage.train;
        final Vec3 trail = CollectorGeometry.trailDirection(context);
        final Vec3 side = CollectorGeometry.sideDirection(context);
        if (trail == null || side == null) {
            return;
        }
        final double offset = CollectorGeometry.centreOffset(context.contraption, context.localPos,
                CollectorGeometry.gearAxis(context.contraption, context.state));
        final Vec3 centre = context.position;
        final long key = ((long) level.dimension().location().hashCode() << 32) ^ (entity.getId() & 0xFFFFFFFFL);
        final Car car = CARS.computeIfAbsent(key, k -> new Car(level, entity, train));

        final double along = context.localPos.getX() * 31.0 + context.localPos.getZ();

        car.shoes.add(new double[]{centre.x, centre.y, centre.z, trail.x, trail.y, trail.z, along,
                side.x, side.y, side.z, offset});
    }

    static int shoeSection(RailLayout layout, double[] shoe, double reach) {
        final Vec3 centre = new Vec3(shoe[0], shoe[1], shoe[2]);
        final Vec3 trail = new Vec3(shoe[3], shoe[4], shoe[5]);
        final Vec3 side = new Vec3(shoe[7], shoe[8], shoe[9]);
        final CollectorGeometry.Placement placed = CollectorGeometry.place(layout::nearest, centre, trail, side,
                shoe[10], true);
        final CollectorPose.Rest rest = placed.rest();
        final Vec3 at = CollectorGeometry.shoeAt(placed.slid(), trail, rest.angleDeg());
        final RailLayout.Hit hit = layout.nearest(at.x, at.y, at.z, reach, 1.0, 2.0);
        if (!rest.touching()) {
            return hit != null && hit.piece() != null ? IN_GAP : -1;
        }
        return hit != null && hit.contact() ? hit.section() : -1;
    }

    static final int IN_GAP = -2;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            tick(event.getServer());
        } finally {
            CARS.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RAILS.clear();
        CHECKED.clear();
        SENT_STATES.clear();
        SENT_CURRENT.clear();
        CARS.clear();
        TRAINS.clear();
    }

    private static void tick(MinecraftServer server) {
        final long now = server.overworld().getGameTime();
        refreshGraphs(now);
        final int base = FeRates.base();
        final int ceiling = RailPower.sectionCeiling(base);
        final int shoeRate = RailPower.collectorRate(base);

        final Map<UUID, List<Car>> carsByGraph = new HashMap<>();
        for (Car car : CARS.values()) {
            final TrackGraph graph = car.train.graph;
            if (graph == null || car.train.derailed || car.train.invalid || car.entity.isRemoved()) {
                fact(car.train, TrainFact.UNPOWERED, 0, now);
                continue;
            }
            carsByGraph.computeIfAbsent(graph.id, g -> new ArrayList<>()).add(car);
        }

        for (GraphRail rail : RAILS.values()) {
            final List<Car> cars = carsByGraph.getOrDefault(rail.graphId, List.of());
            if (!rail.hasPower()) {
                offRailEverywhere(server, rail, now);
                for (Car car : cars) {
                    fact(car.train, TrainFact.UNPOWERED, 0, now);
                }
                continue;
            }
            for (Map.Entry<ResourceKey<Level>, RailLayout> en : rail.layouts().entrySet()) {
                final ServerLevel level = server.getLevel(en.getKey());
                if (level == null) {
                    continue;
                }
                final List<Car> here = new ArrayList<>();
                for (Car car : cars) {
                    if (car.level == level) {
                        here.add(car);
                    }
                }
                runLayout(server, level, rail, en.getValue(), here, ceiling, shoeRate, now);
            }
        }
        expireFacts(now);
    }

    private static void refreshGraphs(long now) {
        final Map<UUID, TrackGraph> graphs = Create.RAILWAYS.trackNetworks;
        boolean setChanged = false;
        for (Iterator<UUID> it = RAILS.keySet().iterator(); it.hasNext(); ) {
            final UUID id = it.next();
            if (!graphs.containsKey(id)) {
                it.remove();
                CHECKED.remove(id);

                final String prefix = id + "|";
                SENT_STATES.keySet().removeIf(k -> k.startsWith(prefix));
                setChanged = true;
            }
        }
        for (UUID id : graphs.keySet()) {
            setChanged |= !RAILS.containsKey(id);
        }
        for (TrackGraph graph : graphs.values()) {
            final Long last = CHECKED.get(graph.id);
            // split/merge = reread everything that tick

            final boolean due = setChanged || last == null
                    || Math.floorMod(now + graph.id.hashCode(), (long) CHECK_INTERVAL) == 0;
            if (!due) {
                continue;
            }
            CHECKED.put(graph.id, now);
            final GraphRail known = RAILS.get(graph.id);
            if (known == null || known.fingerprint != GraphRail.fingerprint(graph)) {
                RAILS.put(graph.id, GraphRail.read(graph));
            }
        }
    }

    private static void offRailEverywhere(MinecraftServer server, GraphRail rail, long now) {
        if (now % 10 != 0) {
            return;
        }
        for (ResourceKey<Level> dim : server.levelKeys()) {
            for (GraphRail.At<FeederPoint> f : rail.feeders(dim)) {
                final TrackFeederBE feeder = feederAt(server, f.point());
                if (feeder != null) {
                    feeder.offRail(now);
                }
            }
            for (GraphRail.At<InsulatorPoint> i : rail.insulators(dim)) {
                final SectionInsulatorBE insulator = insulatorAt(server, i.point());
                if (insulator != null) {
                    insulator.report(SectionInsulatorBE.Status.NOT_POWER_TRACK, SectionView.NONE, SectionView.NONE, now);
                }
            }
        }
    }

    private static void runLayout(MinecraftServer server, ServerLevel level, GraphRail rail, RailLayout layout,
                                  List<Car> cars, int ceiling, int shoeRate, long now) {
        final SectionGraph sections = layout.sections();
        final int sectionCount = sections.sectionCount();

        final List<TrackFeederBE> feeders = new ArrayList<>();
        final List<RailTick.FeederIn> feederIns = new ArrayList<>();
        for (GraphRail.At<FeederPoint> f : rail.feeders(level.dimension())) {
            final TrackFeederBE feeder = feederAt(server, f.point());
            if (feeder == null) {
                continue;
            }
            final int section = sections.sectionAt(f.a(), f.b(), f.position());
            if (section < 0) {
                feeder.offRail(now);
                continue;
            }
            final SectionExchange.Feeder offer = feeder.railOffer(now);
            if (offer == null) {
                continue;
            }
            feeders.add(feeder);
            feederIns.add(new RailTick.FeederIn(section, offer));
        }

        final PortableFeDirectionLock stationLock = PortablePowerInterfaceBE.directionLock(level);
        final List<RailTick.CarIn> carIns = new ArrayList<>();
        final List<Car> carList = new ArrayList<>();
        final double reach = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;
        for (Car car : cars) {
            car.shoes.sort(Comparator.comparingDouble(s -> s[6]));
            final int[] shoeSections = new int[car.shoes.size()];
            boolean onRail = false;
            for (int i = 0; i < shoeSections.length; i++) {
                final int s = shoeSection(layout, car.shoes.get(i), reach);
                car.inGap |= s == IN_GAP;
                shoeSections[i] = Math.max(-1, s);
                onRail |= shoeSections[i] >= 0;
            }

            car.stores = onRail ? TrainChargeProbe.storesOf(car.entity.getContraption()) : null;
            car.ledger = car.stores == null ? new TrainBankLedger(new int[0], new int[0]) : car.stores.ledger();
            final long id = car.entity.getId();
            carIns.add(new RailTick.CarIn(trainKey(car.train), shoeSections, car.ledger.stored(), car.ledger.capacity(),
                    stationLock.mayMove(id, PortableFeTransfer.Mode.CHARGE, level.getGameTime()),
                    stationLock.mayMove(id, PortableFeTransfer.Mode.DRAW, level.getGameTime())));
            carList.add(car);
        }

        if (feederIns.isEmpty() && carIns.isEmpty()) {
            reportInsulators(server, rail, layout, level, new SectionView[sectionCount], now);
            publishStates(level, rail, new SectionView[sectionCount], now);
            return;
        }

        final RailTick.Result result = RailTick.run(feederIns, carIns, sectionCount, ceiling, shoeRate, TRAIN_LOCK, now);

        final int[] actual = new int[carList.size()];
        for (int j = 0; j < carList.size(); j++) {
            final Car car = carList.get(j);
            final int delta = result.carDelta()[j];
            int moved = 0;
            if (delta > 0) {
                moved = car.ledger.fill(delta);
            } else if (delta < 0) {
                moved = -car.ledger.drain(-delta);
            }
            actual[j] = moved;
            if (moved != 0 && car.stores != null) {
                if (car.stores.commit(car.ledger)) {
                    CarriedDeviceSync.touched(car.entity);
                }
                stationLock.stamp(car.entity.getId(),
                        moved > 0 ? PortableFeTransfer.Mode.CHARGE : PortableFeTransfer.Mode.DRAW, level.getGameTime());
            }
        }
        final int[] feederSection = new int[feederIns.size()];
        for (int i = 0; i < feederSection.length; i++) {
            feederSection[i] = feederIns.get(i).section();
        }

        final int[] feederDelta = RailTick.settle(result.feederDelta(), feederSection, result.carDelta(), actual,
                result.carSection());

        final List<List<SectionExchange.Feeder>> feedersOn = new ArrayList<>(sectionCount);
        final int[] feederCount = new int[sectionCount];
        final int[] carCount = new int[sectionCount];
        for (int s = 0; s < sectionCount; s++) {
            feedersOn.add(new ArrayList<>());
        }
        for (RailTick.FeederIn f : feederIns) {
            feedersOn.get(f.section()).add(f.feeder());
            feederCount[f.section()]++;
        }
        for (int j = 0; j < carList.size(); j++) {
            final int s = result.carSection()[j];
            if (s >= 0 && s < sectionCount) {
                carCount[s]++;
            }
        }
        final SectionView[] views = new SectionView[sectionCount];
        for (int s = 0; s < sectionCount; s++) {
            final SectionExchange.Plan plan = result.plans()[s];
            views[s] = new SectionView(SectionExchange.energised(feedersOn.get(s)).ordinal(), feederCount[s],
                    carCount[s], plan == null ? 0 : plan.railFlow());
        }

        for (int i = 0; i < feeders.size(); i++) {
            final int s = feederIns.get(i).section();
            final SectionExchange.Plan plan = result.plans()[s];
            feeders.get(i).railResult(feederDelta[i], s, views[s], plan == null ? 0 : plan.wanted(), now);
        }

        final int rating = RailPower.carRate(FeRates.base(), RailPower.MAX_COLLECTORS_PER_CAR);
        for (int j = 0; j < carList.size(); j++) {
            final Car car = carList.get(j);
            final int s = result.carSection()[j];
            final int state;
            if (actual[j] > 0) {
                state = TrainFact.CHARGING;
            } else if (actual[j] < 0) {
                state = TrainFact.GIVING;
            } else if (s >= 0 && views[s].state() != SectionView.DEAD && views[s].state() != SectionView.IDLE) {
                state = TrainFact.LIVE_IDLE;
            } else if (s < 0 && car.inGap) {
                state = TrainFact.IN_GAP;
            } else {
                state = TrainFact.UNPOWERED;
            }
            fact(car.train, state, actual[j], now);
            sendCurrent(car.entity, actual[j], rating, now);
        }

        reportInsulators(server, rail, layout, level, views, now);
        publishStates(level, rail, views, now);
    }

    private static void reportInsulators(MinecraftServer server, GraphRail rail, RailLayout layout, ServerLevel level,
                                         SectionView[] views, long now) {
        final SectionGraph sections = layout.sections();
        for (GraphRail.At<InsulatorPoint> at : rail.insulators(level.dimension())) {
            final SectionInsulatorBE insulator = insulatorAt(server, at.point());
            if (insulator == null) {
                continue;
            }
            final RailLayout.Shape shape = layout.shape(at.a(), at.b());
            if (shape == null || !sections.isPower(at.a(), at.b())) {
                insulator.report(SectionInsulatorBE.Status.NOT_POWER_TRACK, SectionView.NONE, SectionView.NONE, now);
                continue;
            }
            final double canonAt = at.a() < at.b() ? at.position() : shape.length() - at.position();
            final double eps = 0.01;
            final int before = sections.sectionAt(shape.lo(), shape.hi(), Math.max(0, canonAt - eps));
            final int after = sections.sectionAt(shape.lo(), shape.hi(), Math.min(shape.length(), canonAt + eps));
            final double[] tangent = shape.tangent(canonAt);
            final double[] point = shape.point(canonAt);

            double nx = tangent[1];
            double nz = -tangent[0];
            final BlockPos pos = insulator.getBlockPos();
            if (nx * (pos.getX() + 0.5 - point[0]) + nz * (pos.getZ() + 0.5 - point[2]) < 0) {
                nx = -nx;
                nz = -nz;
            }
            insulator.orientOnce(Direction.getNearest(nx, 0, nz));
            final SectionView[] lamp = new SectionView[2];
            for (int i = 0; i < 2; i++) {
                final Direction d = insulator.lampDirection(i);
                final boolean ahead = d.getStepX() * tangent[0] + d.getStepZ() * tangent[1] >= 0;
                final int s = ahead ? after : before;
                lamp[i] = s >= 0 && s < views.length && views[s] != null ? views[s] : SectionView.NONE;
            }
            insulator.report(SectionInsulatorBE.Status.WORKING, lamp[0], lamp[1], now);
        }
    }

    private static void publishStates(ServerLevel level, GraphRail rail, SectionView[] views, long now) {
        final String key = rail.graphId + "|" + level.dimension().location();
        final List<SectionView> list = new ArrayList<>(views.length);
        for (SectionView v : views) {
            list.add(v == null ? SectionView.NONE : v);
        }
        final int hash = list.hashCode() * 31 + rail.fingerprint;
        final Integer sent = SENT_STATES.get(key);

        final boolean changed = sent == null || sent != hash;
        if ((changed && now % STATE_INTERVAL == 0) || now % 100 == 0) {
            SENT_STATES.put(key, hash);
            PacketDistributor.sendToPlayersInDimension(level, new RailStatePacket(rail.graphId, rail.fingerprint, list));
        }
    }

    private static void sendCurrent(CarriageContraptionEntity entity, int current, int rating, long now) {
        final int[] last = SENT_CURRENT.get(entity.getId());
        final int bucket = rating <= 0 ? current : current * 16 / rating;
        final boolean changed = last == null || last[0] != bucket;
        final boolean due = last != null && current != 0 && now - last[1] >= 10;
        if (changed || due) {
            SENT_CURRENT.put(entity.getId(), new int[]{bucket, (int) now});
            PacketDistributor.sendToPlayersTrackingEntity(entity, new CarCurrentPacket(entity.getId(), current, rating));
        }
    }

    private static void fact(Train train, int state, int flow, long now) {
        final TrainFact old = TRAINS.get(train.id);

        if (old != null && old.tick() == now) {
            final int best = rank(state) > rank(old.state()) ? state : old.state();
            TRAINS.put(train.id, new TrainFact(best, old.flow() + flow, now));
        } else {
            TRAINS.put(train.id, new TrainFact(state, flow, now));
        }
    }

    private static int rank(int state) {
        return switch (state) {
            case TrainFact.CHARGING, TrainFact.GIVING -> 4;
            case TrainFact.LIVE_IDLE -> 3;
            case TrainFact.IN_GAP -> 2;
            case TrainFact.UNPOWERED -> 1;
            default -> 0;
        };
    }

    private static void expireFacts(long now) {
        if (now % 200 != 0) {
            return;
        }
        TRAINS.values().removeIf(f -> now - f.tick() > TRAIN_FACT_TTL * 10L);
        SENT_CURRENT.entrySet().removeIf(e -> now - e.getValue()[1] > 1200);
    }

    public static void fillRail(Train train, RailSnapshot.TrainState t) {
        final TrainFact f = TRAINS.get(train.id);
        if (f == null || train.graph == null) {
            return;
        }
        final MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        final long now = server == null ? f.tick() : server.overworld().getGameTime();
        if (now - f.tick() > TRAIN_FACT_TTL) {
            return;
        }
        t.railState = f.state();
        t.railFlow = f.flow();
    }

    private static long trainKey(Train train) {
        return train.id.getMostSignificantBits() ^ train.id.getLeastSignificantBits();
    }

    private static TrackFeederBE feederAt(MinecraftServer server, FeederPoint point) {
        final BlockEntity be = loadedBlockEntity(server, point.blockEntityDimension, point.blockEntityPos);
        return be instanceof TrackFeederBE feeder ? feeder : null;
    }

    private static SectionInsulatorBE insulatorAt(MinecraftServer server, InsulatorPoint point) {
        final BlockEntity be = loadedBlockEntity(server, point.blockEntityDimension, point.blockEntityPos);
        return be instanceof SectionInsulatorBE insulator ? insulator : null;
    }

    private static BlockEntity loadedBlockEntity(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        if (dim == null || pos == null) {
            return null;
        }
        final ServerLevel level = server.getLevel(dim);
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }
}
