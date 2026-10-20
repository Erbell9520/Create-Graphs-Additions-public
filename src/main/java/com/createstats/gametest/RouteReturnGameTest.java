package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.createstats.surveyor.PathwayStore;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.vccmk4.Vcc4BE;
import com.createstats.vccmk4.route.RouteNbt;
import com.createstats.vccmk4.route.GhostHost;
import com.createstats.vccmk4.route.RouteHost;
import com.createstats.vccmk4.route.Vcc4Route;
import com.createstats.surveyor.core.CargoSnapshot;
import com.createstats.surveyor.core.UnseenTravel;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.lang.reflect.Proxy;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteReturnGameTest {
    private RouteReturnGameTest() {}

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void loadedConsoleDoesNotPauseBeforeItsFirstVehicleScan(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.VCC_MK4.getDefaultState());
        Vcc4BE be = (Vcc4BE) h.getBlockEntity(pos);
        BlockPos world = h.absolutePos(pos);
        Pathway path = Pathway.create(UUID.randomUUID(), "Return startup", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{world.getX(), world.getY(), world.getZ()},
                        new double[]{world.getX() + 60, world.getY(), world.getZ()}));
        PathwayStore.get(h.getLevel()).put(path);
        CompoundTag route = new CompoundTag();
        route.putUUID("Pathway", path.id());
        route.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 8,
                -1, -1, 0, -1, false, 0, -1)));
        be.route().load(route);
        be.serverTick();
        h.assertTrue(be.route().state() != RouteRunner.State.PAUSED,
                "route paused on the initial EMPTY vehicle scan: " + be.route().runner().reason());
        PathwayStore.get(h.getLevel()).remove(path.id());
        be.route().unassign(h.getLevel(), "test complete");
        h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void completeHandBackResumesAfterTheHostBecomesReady(GameTestHelper h) {
        BlockPos w = h.absolutePos(new BlockPos(4, 1, 4));
        Pathway path = Pathway.create(UUID.randomUUID(), "Return continuity", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{w.getX(), w.getY(), w.getZ()}, new double[]{w.getX() + 100, w.getY(), w.getZ()}));
        PathwayStore.get(h.getLevel()).put(path);
        UUID console = UUID.randomUUID();
        CompoundTag tag = new CompoundTag(); tag.putUUID("Pathway", path.id());
        tag.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 20,
                -1, -1, 0, -1, false, 2, 12)));
        CompoundTag settings = new CompoundTag(); settings.putDouble("Kappa", .15); settings.putDouble("Height", 3);
        GhostHost ghost = new GhostHost(h.getLevel(), console, UUID.randomUUID(), "Test vehicle", List.of(), w,
                settings, new double[]{2, 2, 1, 3}, CargoSnapshot.unavailable(0), tag, 20, 0,
                new UnseenTravel.Place(w.getX() + 20, w.getY(), w.getZ(), Math.PI / 2));
        CompoundTag returned = ghost.save(); returned.putBoolean("ReturnResume", true);
        boolean[] ready = {false};
        RouteHost host = (RouteHost) Proxy.newProxyInstance(RouteHost.class.getClassLoader(), new Class[]{RouteHost.class},
                (self, method, args) -> switch (method.getName()) {
                    case "present" -> true;
                    case "returnReady" -> ready[0];
                    case "cannotFollow" -> ready[0] ? "" : "not on a vehicle";
                    default -> method.invoke(ghost, args);
                });
        Vcc4Route route = new Vcc4Route(host); route.load(returned); route.resume(h.getLevel());
        long now = h.getLevel().getGameTime();
        route.tick(h.getLevel(), now, null, null);
        h.assertTrue(route.state() == RouteRunner.State.RUNNING && route.awaitingVehicle(), "startup lost the running intent");
        ready[0] = true;
        route.tick(h.getLevel(), now + 1, null, null);
        h.assertTrue(route.drives() && !route.awaitingVehicle(), "driver did not take the restored vehicle automatically");
        h.assertTrue(route.runner().laps() == 2 && Math.abs(route.runner().progress() - 20) < .1, "hand-back lost progress or laps");
        route.unassign(h.getLevel(), "test complete"); PathwayStore.get(h.getLevel()).remove(path.id()); h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void unseenDriverServesAStopWithSavedCargoWithoutAConsole(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        Pathway path = Pathway.create(UUID.randomUUID(), "Unseen stop", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0}, new double[]{100, 64, 0}))
                .update(2, n -> n.withStop(Stop.DEFAULT.withRules(List.of(List.of(
                        new StopCondition.Items(StopCondition.Op.GREATER, 10, false, "minecraft:iron_ingot"), new StopCondition.Wait(2))))));
        PathwayStore.get(level).put(path);
        CompoundTag saved = new CompoundTag(); saved.putUUID("Pathway", path.id());
        saved.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 0, 0,
                -1, 0, -1, false, 0, -1)));
        saved.put("Plant", RouteNbt.writePlant(new com.createstats.surveyor.core.PlantModel(8, 1, .2, 100, 10, 0)));
        CompoundTag settings = new CompoundTag(); settings.putDouble("Kappa", .15); settings.putDouble("Height", 3);
        UUID console = UUID.randomUUID(), craft = UUID.randomUUID();
        CargoSnapshot cargo = new CargoSnapshot(Map.of("minecraft:iron_ingot", 20L), Map.of(), Map.of(), 0, 0, true);
        GhostHost ghost = new GhostHost(level, console, craft, "Ghost test", List.of(craft), BlockPos.ZERO, settings,
                new double[]{2, 2, 1, 3}, cargo, saved, 0, 0, new UnseenTravel.Place(0, 64, 0, Math.PI / 2));
        boolean arrived = false, rebuilt = false; int waited = 0;
        for (int tick = 1; tick < 2500; tick++) {
            ghost.tick(tick);
            if (ghost.route().state() == RouteRunner.State.WAITING) {
                arrived = true;
                if (++waited == 20) {
                    ghost = new GhostHost(level, console, craft, "Ghost test", List.of(craft), BlockPos.ZERO, settings,
                            new double[]{2, 2, 1, 3}, cargo, ghost.save(), ghost.progress(), ghost.speed(), ghost.place());
                    rebuilt = true;
                }
            }
            if (ghost.route().state() == RouteRunner.State.FINISHED) break;
        }
        h.assertTrue(arrived && rebuilt && waited >= 40 && waited < 50, "unseen stop or remaining wait failed: " + waited);
        h.assertTrue(ghost.route().state() == RouteRunner.State.FINISHED, "unseen driver did not finish its one-way route");
        h.assertTrue(ghost.route().plant().samples() == 100, "synthetic motion relearned its own model");
        ghost.route().removed(level); PathwayStore.get(level).remove(path.id()); h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void unseenFollowerKeepsItsGapToAPausedVehicle(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        Pathway path = Pathway.create(UUID.randomUUID(), "Mixed traffic", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{100, 64, 0}));
        PathwayStore.get(level).put(path);
        UUID lead = UUID.randomUUID(), console = UUID.randomUUID(), craft = UUID.randomUUID();
        com.createstats.surveyor.PathwayTraffic.report(level, new com.createstats.surveyor.RouteTrack(lead, path.id(), "Paused leader",
                RouteRunner.State.PAUSED.ordinal(), "paused", 35, 64, 0, (float) (Math.PI / 2), 0, 35,
                -1, -1, -1, "", 0, -1, 2, 2, 0, 0, UUID.randomUUID(), 0, 1));
        CompoundTag saved = new CompoundTag(); saved.putUUID("Pathway", path.id());
        saved.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 0, -1, -1, 0, -1, false, 0, -1)));
        saved.put("Plant", RouteNbt.writePlant(new com.createstats.surveyor.core.PlantModel(8, 1, .2, 100, 10, 0)));
        CompoundTag settings = new CompoundTag(); settings.putDouble("Kappa", .15); settings.putDouble("Height", 3);
        GhostHost ghost = new GhostHost(level, console, craft, "Follower", List.of(craft), BlockPos.ZERO, settings,
                new double[]{2, 2, 1, 3}, CargoSnapshot.unavailable(0), saved, 0, 0, new UnseenTravel.Place(0, 64, 0, Math.PI / 2));
        for (int tick = 1; tick < 1000; tick++) ghost.tick(tick);
        h.assertTrue(ghost.progress() < 31 && ghost.speed() < .3,
                "unseen follower passed the paused leader: " + ghost.progress() + " speed " + ghost.speed());
        com.createstats.surveyor.PathwayTraffic.forget(level, lead);
        for (int tick = 1000; tick < 2000; tick++) ghost.tick(tick);
        h.assertTrue(ghost.route().state() == RouteRunner.State.FINISHED, "follower did not go on after the road cleared");
        ghost.route().removed(level); PathwayStore.get(level).remove(path.id()); h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void pausedUnseenVehicleKeepsItsPlaceThroughAnEditAndDeletion(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        Pathway path = Pathway.create(UUID.randomUUID(), "Edit unseen", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{100, 64, 0}));
        PathwayStore.get(level).put(path);
        CompoundTag saved = new CompoundTag(); saved.putUUID("Pathway", path.id());
        saved.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.PAUSED, "owner paused", 40, -1, -1, 0, -1, false, 0, -1)));
        CompoundTag settings = new CompoundTag(); settings.putDouble("Kappa", .15); settings.putDouble("Height", 3);
        UUID craft = UUID.randomUUID();
        GhostHost ghost = new GhostHost(level, UUID.randomUUID(), craft, "Paused edit", List.of(craft), BlockPos.ZERO,
                settings, new double[]{2, 2, 1, 3}, CargoSnapshot.unavailable(0), saved, 40, 0,
                new UnseenTravel.Place(40, 64, 0, Math.PI / 2));
        PathwayStore.get(level).put(path.update(1, n -> n.at(-20, 64, 0)));
        ghost.tick(10);
        h.assertTrue(ghost.route().state() == RouteRunner.State.PAUSED && Math.abs(ghost.place().x() - 40) < .1,
                "edited route moved a paused vehicle to a different world place: " + ghost.place().x());
        PathwayStore.get(level).remove(path.id()); ghost.tick(11);
        h.assertTrue(!ghost.route().assigned() && Math.abs(ghost.place().x() - 40) < .1,
                "deleted route did not retain its last unassigned return place");
        ghost.route().removed(level); h.succeed();
    }

    @GameTest(template = "fe_bench", timeoutTicks = 20)
    public static void unseenScheduleCrossesAJunctionAndSurvivesTwoReloads(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        UUID owner = UUID.randomUUID();
        String station = "Finish " + owner.toString().substring(0, 8);
        Pathway a = Pathway.create(UUID.randomUUID(), "Inbound", 3, owner)
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{50, 64, 0}));
        Pathway b = Pathway.create(UUID.randomUUID(), "Outbound", 3, owner)
                .appendAll(List.of(new double[]{50, 64, 0}, new double[]{100, 64, 0}))
                .update(2, n -> n.withName(station).withStop(Stop.DEFAULT));
        PathwayStore.get(level).put(a); PathwayStore.get(level).put(b);
        CompoundTag saved = new CompoundTag(); saved.putUUID("Pathway", a.id());
        saved.put("Runner", RouteNbt.writeSaved(new RouteRunner.Saved(RouteRunner.State.RUNNING, "", 0, -1, -1, 0, -1, false, 0, -1)));
        saved.put("Plant", RouteNbt.writePlant(new com.createstats.surveyor.core.PlantModel(8, 1, .2, 100, 10, 0)));
        CompoundTag settings = new CompoundTag(); settings.putDouble("Kappa", .15); settings.putDouble("Height", 3);
        UUID console = UUID.randomUUID(), craft = UUID.randomUUID();
        GhostHost ghost = new GhostHost(level, console, craft, "Scheduled unseen", List.of(craft), BlockPos.ZERO,
                settings, new double[]{2, 2, 1, 3}, CargoSnapshot.unavailable(0), saved, 0, 0,
                new UnseenTravel.Place(0, 64, 0, Math.PI / 2));
        var schedule = new com.createstats.surveyor.core.RouteSchedule(List.of(new com.createstats.surveyor.core.RouteSchedule.Entry(
                new com.createstats.surveyor.core.RouteSchedule.TravelTo(station), List.of(List.of(new StopCondition.Wait(2))), List.of())), false);
        ghost.route().installSchedule(level, schedule, 0, null);
        boolean movingReload = false, waitReload = false, crossed = false, completed = false;
        int waited = 0;
        for (int tick = 1; tick < 3000; tick++) {
            ghost.tick(tick);
            crossed |= b.id().equals(ghost.route().pathwayId());
            boolean reload = !movingReload && a.id().equals(ghost.route().pathwayId()) && ghost.progress() > 20;
            if (reload) movingReload = true;
            if (ghost.route().state() == RouteRunner.State.WAITING && ++waited == 20) { reload = true; waitReload = true; }
            if (reload) {
                ghost = new GhostHost(level, console, craft, "Scheduled unseen", List.of(craft), BlockPos.ZERO,
                        settings, new double[]{2, 2, 1, 3}, CargoSnapshot.unavailable(0), ghost.save(),
                        ghost.progress(), ghost.speed(), ghost.place());
            }
            if (ghost.route().state() == RouteRunner.State.PAUSED && ghost.route().runner().reason().contains("completed")) {
                completed = true; break;
            }
        }
        h.assertTrue(movingReload && crossed && waitReload && completed,
                "scheduled network continuity failed: moving=" + movingReload + " crossed=" + crossed + " wait=" + waitReload
                        + " state=" + ghost.route().state() + " reason=" + ghost.route().runner().reason()
                        + " s=" + ghost.progress() + " v=" + ghost.speed() + " x=" + ghost.place().x()
                        + " mode=" + ghost.route().runner().mode() + " target=" + ghost.route().runner().nextStopNode()
                        + " line=" + ghost.route().pathwayId() + " expected=" + b.id() + " command=" + ghost.route().command());
        h.assertTrue(waited >= 40 && waited < 50, "scheduled wait restarted after load: " + waited);
        ghost.route().removed(level); PathwayStore.get(level).remove(a.id()); PathwayStore.get(level).remove(b.id()); h.succeed();
    }
}
