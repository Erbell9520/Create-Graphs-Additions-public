package com.createstats.statsmk3;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.train.smart.RailSnapshot;

// one railway for every test, two copies had drifted
final class RailFixtures {
    private RailFixtures() {
    }

    static Rect face(int blocksW, int blocksH, float faceScale) {
        final float ppb = 64.0f / faceScale;
        final float texel = ppb / 16.0f;
        return new Rect(2.0f * texel, 2.0f * texel,
                blocksW * ppb - 4.0f * texel, blocksH * ppb - 5.0f * texel);
    }

    static BufferedImage render(PanelModel model, Lod lod, int blocksW, int blocksH,
                                float faceScale) {
        final float ppb = 64.0f / faceScale;
        final int w = Math.round(blocksW * ppb);
        final int h = Math.round(blocksH * ppb);
        final RasterSurface surface = new RasterSurface(w, h);
        PanelPainter.paint(surface, face(blocksW, blocksH, faceScale), model, lod);
        return surface.finish();
    }

    static RailSnapshot network() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 1000L;
        snap.topologyChecksum = 42;

        final RailSnapshot.Station central1 = station("Central 1", -40, 10);
        final RailSnapshot.Station central2 = station("Central 2", -40, 26);
        final RailSnapshot.Station central3 = station("Central 3", -40, 42);
        final RailSnapshot.Station quarry = station("Quarry North", 180, -90);
        final RailSnapshot.Station quarrySouth = station("Quarry South", 210, -30);
        final RailSnapshot.Station docks = station("Docks", 60, 210);
        final RailSnapshot.Station foundry = station("Foundry", -190, -60);
        final RailSnapshot.Station yard = station("Yard", -150, 130);
        yard.assembling = true;
        docks.packagePorts = 2;
        snap.stations().addAll(List.of(central1, central2, central3, quarry, quarrySouth, docks,
                foundry, yard));

        snap.links().add(link(central1, quarry, 260.0, new float[]{
                -40, 10, 20, -20, 90, -50, 140, -75, 180, -90}));
        snap.links().add(link(quarry, quarrySouth, 90.0, new float[]{
                180, -90, 200, -70, 210, -30}));
        snap.links().add(link(central2, docks, 240.0,
                new float[]{-40, 26, 0, 90, 40, 160, 60, 210}));
        snap.links().add(link(central3, foundry, 200.0,
                new float[]{-40, 42, -90, 10, -150, -30, -190, -60}));
        snap.links().add(link(central1, yard, 160.0, new float[0]));

        snap.tracks().add(track(new float[]{-40, 10, 20, -20, 90, -50, 140, -75, 180, -90,
                200, -70, 210, -30}));
        snap.tracks().add(track(new float[]{-40, 26, 0, 90, 40, 160, 60, 210}));
        snap.tracks().add(track(new float[]{-40, 42, -90, 10, -150, -30, -190, -60}));
        snap.tracks().add(track(new float[]{-40, 10, -80, 60, -120, 100, -150, 130}));

        final RailSnapshot.TrainState express = train("Stone Express", 0.072f, 30, 120);
        express.destinationStation = quarry.id;
        express.destinationName = quarry.name;
        express.distanceToDestination = 160.0;
        express.distanceStartedAt = 260.0;
        express.stops.addAll(List.of("Central 1", "Quarry North", "Docks"));
        express.scheduleTitle = "Stone circuit";
        express.itemCount = 1728;
        express.topItem = "Cobblestone";
        express.occupiedBlocks = 1;
        express.reservedBlocks = 2;
        express.mapColor = 2;
        express.length = 38.0f;
        express.ticksInTransit = 640;
        express.legPlannedTicks = 620;
        cars(express, 5, 3);
        snap.trains().add(express);

        final RailSnapshot.TrainState held = train("Coal Shuttle", 0.0f, -10, 60);
        held.waitingForSignal = true;
        held.ticksWaitingForSignal = 900;
        held.distanceToSignal = 18.0;
        held.blockedByTrain = express.id;
        held.blockedAt = "Quarry Junction";
        held.destinationStation = docks.id;
        held.destinationName = docks.name;
        held.distanceToDestination = 180.0;
        held.distanceStartedAt = 240.0;
        held.stops.addAll(List.of("Central 2", "Docks"));
        held.scheduleTitle = "Coal run";
        held.ticksInTransit = 2400;
        held.legPlannedTicks = 1200;
        held.mapColor = 5;
        held.length = 24.0f;
        cars(held, 3, 0);
        snap.trains().add(held);

        final RailSnapshot.TrainState broken = train("Ore Hauler", 0.0f, 120, -40);
        broken.derailed = true;
        broken.stops.addAll(List.of("Quarry North", "Central 1"));
        broken.scheduleTitle = "Ore run";
        broken.mapColor = 0;
        snap.trains().add(broken);

        final RailSnapshot.TrainState parked = train("Spare Loco", 0.0f, -150, 130);
        parked.currentStation = yard.id;
        parked.hasSchedule = false;
        parked.conductorOk = false;
        parked.carriages = 1;
        snap.trains().add(parked);

        final RailSnapshot.TrainState timber = train("Timber Run", 0.058f, -110, -10);
        timber.destinationStation = foundry.id;
        timber.destinationName = foundry.name;
        timber.distanceToDestination = 60.0;
        timber.distanceStartedAt = 200.0;
        timber.stops.addAll(List.of("Central 3", "Foundry"));
        timber.scheduleTitle = "Timber shuttle";
        timber.ticksInTransit = 700;
        timber.legPlannedTicks = 1500;
        timber.itemCount = 384;
        timber.topItem = "Oak Log";
        timber.mapColor = 7;
        timber.length = 20.0f;
        cars(timber, 3, 2);
        snap.trains().add(timber);

        final RailSnapshot.TrainState tanker = train("Fuel Tanker", 0.0f, -40, 26);
        tanker.currentStation = central2.id;
        tanker.destinationStation = docks.id;
        tanker.destinationName = docks.name;
        tanker.waitingStatus = "Waiting for cargo";
        tanker.phase = RailSnapshot.SchedulePhase.POST_TRANSIT;
        tanker.stops.addAll(List.of("Central 2", "Docks"));
        tanker.scheduleTitle = "Fuel run";
        tanker.fluidAmount = 12000;
        tanker.topFluid = "Lava";
        tanker.fuelTicks = 900;
        tanker.mapColor = 4;
        tanker.length = 16.0f;
        cars(tanker, 2, 1);
        snap.trains().add(tanker);

        final RailSnapshot.TrainState mail = train("Night Mail", 0.041f, 80, 150);
        mail.manual = true;
        mail.backwards = true;
        mail.doubleEnded = true;
        mail.hasSchedule = false;
        mail.mapColor = 9;
        mail.length = 12.0f;
        cars(mail, 2, 0);
        snap.trains().add(mail);

        central1.presentTrain = parked.id;
        central1.imminentTrain = express.id;
        central2.presentTrain = tanker.id;

        snap.arrivals().add(arrival(express, "Central 1", 640));
        snap.arrivals().add(arrival(held, "Central 2", 2400));
        snap.arrivals().add(arrival(broken, "Central 1", -2));
        snap.arrivals().add(arrival(parked, "Central 1", 0));
        snap.arrivals().add(arrival(express, "Quarry North", 180));
        snap.arrivals().add(arrival(timber, "Central 3", 1500));
        snap.arrivals().add(arrival(tanker, "Central 2", 0));
        snap.arrivals().add(arrival(timber, "Foundry", 900));
        snap.arrivals().add(arrival(held, "Docks", 4800));
        snap.arrivals().add(arrival(express, "Docks", 7200));
        return snap;
    }

    static RailSnapshot deadlock() {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 9000L;
        snap.topologyChecksum = 71;

        final RailSnapshot.Station hub = station("Kreuz", 0, 0);
        final RailSnapshot.Station n = station("Nord", 0, -150);
        final RailSnapshot.Station e = station("Ost", 150, 0);
        final RailSnapshot.Station w = station("West", -150, 0);
        snap.stations().addAll(List.of(hub, n, e, w));
        snap.links().add(link(hub, n, 150.0, new float[]{0, 0, 0, -150}));
        snap.links().add(link(hub, e, 150.0, new float[]{0, 0, 150, 0}));
        snap.links().add(link(hub, w, 150.0, new float[]{0, 0, -150, 0}));
        snap.tracks().add(track(new float[]{0, -150, 0, 0, 150, 0}));
        snap.tracks().add(track(new float[]{-150, 0, 0, 0}));

        final RailSnapshot.TrainState a = train("Kohlezug", 0.0f, -12, -36);
        final RailSnapshot.TrainState b = train("Erzzug", 0.0f, 36, 11);
        final RailSnapshot.TrainState c = train("Holzzug", 0.0f, -36, 9);
        for (final RailSnapshot.TrainState t : List.of(a, b, c)) {
            t.waitingForSignal = true;
            t.ticksWaitingForSignal = 1100;
            t.stops.addAll(List.of("Kreuz", "Nord", "Ost", "West"));
            t.currentEntry = 1;
            snap.trains().add(t);
        }
        a.blockedByTrain = b.id;
        b.blockedByTrain = c.id;
        c.blockedByTrain = a.id;

        final RailSnapshot.TrainState wreck = train("Schotterzug", 0.0f, -140, 0);
        wreck.derailed = true;
        wreck.stops.addAll(List.of("West", "Kreuz"));
        final RailSnapshot.TrainState q1 = train("Personenzug", 0.0f, -104, 0);
        final RailSnapshot.TrainState q2 = train("Postzug", 0.0f, -70, 0);
        q1.waitingForSignal = true;
        q1.ticksWaitingForSignal = 1500;
        q1.blockedByTrain = wreck.id;
        q1.stops.addAll(List.of("West", "Kreuz"));
        q2.waitingForSignal = true;
        q2.ticksWaitingForSignal = 1300;
        q2.blockedByTrain = q1.id;
        q2.stops.addAll(List.of("West", "Kreuz"));
        snap.trains().addAll(List.of(wreck, q1, q2));
        return snap;
    }

    static RailSnapshot.Station station(String name, double x, double z) {
        final RailSnapshot.Station s = new RailSnapshot.Station();
        s.id = UUID.nameUUIDFromBytes(name.getBytes());
        s.name = name;
        s.dimension = "minecraft:overworld";
        s.x = x;
        s.z = z;
        return s;
    }

    static RailSnapshot.Link link(RailSnapshot.Station a, RailSnapshot.Station b, double length,
                                  float[] path) {
        final RailSnapshot.Link l = new RailSnapshot.Link();
        l.from = a.id;
        l.to = b.id;
        l.length = length;
        l.path = path;
        return l;
    }

    static RailSnapshot.Track track(float[] path) {
        final RailSnapshot.Track t = new RailSnapshot.Track();
        t.dimension = "minecraft:overworld";
        t.path = path;
        return t;
    }

    static RailSnapshot.TrainState train(String name, float speed, double x, double z) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = UUID.nameUUIDFromBytes(name.getBytes());
        t.name = name;
        t.owner = "Player";
        t.dimension = "minecraft:overworld";
        t.speed = speed;
        t.maxSpeed = 0.08f;
        t.carriages = 4;
        t.x = x;
        t.z = z;
        t.positionValid = true;
        t.hasSchedule = true;
        t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
        return t;
    }

    static void cars(RailSnapshot.TrainState t, int count, int loaded) {
        t.carriages = count;
        for (int i = 0; i < count; i++) {
            final RailSnapshot.Car car = new RailSnapshot.Car();
            car.spacing = i == 0 ? 3 : 5;
            car.conductor = i == 0;
            car.twoBogeys = i > 0;
            car.slots = 27;
            car.usedSlots = i < loaded ? 27 : 0;
            car.items = i < loaded ? 1728 : 0;
            t.cars.add(car);
        }
    }

    static RailSnapshot.Arrival arrival(RailSnapshot.TrainState t, String station, int ticks) {
        final RailSnapshot.Arrival a = new RailSnapshot.Arrival();
        a.trainId = t.id;
        a.trainName = t.name;
        a.station = station;
        a.scheduleTitle = t.scheduleTitle;
        a.ticks = ticks;
        return a;
    }
}
