package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.CargoSnapshot;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.StopRules;
import com.createstats.surveyor.core.UnseenTravel;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.HeldKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

// never ticks, never takes a ticket
public final class GhostHost implements RouteHost {
    private final ServerLevel level;
    private final UUID console;
    private final UUID craft;
    private final String name;
    private final List<UUID> bodies;
    private final BlockPos plotPos;
    private final CompoundTag settings;
    private final double[] extents;
    private final CargoSnapshot cargo;
    private final RouteSensors.CargoWatch links = new RouteSensors.CargoWatch();
    private final Vcc4Route route;
    private UnseenTravel.Place place;
    private PathSpline spline = PathSpline.EMPTY;
    private double s;
    private double speed;
    private DriveModel.Output applied = DriveModel.Output.idle(0);
    private RouteSensors.Obstacle obstacle = RouteSensors.Obstacle.NONE;
    private boolean changed;

    public GhostHost(ServerLevel level, UUID console, UUID craft, String name, List<UUID> bodies, BlockPos plotPos,
                     CompoundTag settings, double[] extents, CargoSnapshot cargo, CompoundTag saved, double s,
                     double speed, UnseenTravel.Place lastPlace) {
        this.level = level; this.console = console; this.craft = craft; this.name = name; this.bodies = List.copyOf(bodies);
        this.plotPos = plotPos; this.settings = settings.copy(); this.extents = extents.clone(); this.cargo = cargo;
        this.s = s; this.speed = Math.max(0.0, speed); this.place = lastPlace;
        route = new Vcc4Route(this); route.loadTravel(saved); route.resume(level);
        if (route.runner() != null) { spline = route.runner().spline(); place = UnseenTravel.place(spline, s); }
    }

    public Vcc4Route route() { return route; }
    public double progress() { return s; }
    public double speed() { return speed; }
    public UnseenTravel.Place place() { return place; }
    public boolean takeChanged() { boolean out = changed; changed = false; return out; }
    public void obstruction(RouteSensors.Obstacle obstacle) { this.obstacle = obstacle; }
    public void holdForReturn() { speed = 0; }

    public void tick(long now) {
        double located = s;
        UUID before = route.pathwayId();
        PathSpline beforeSpline = route.runner() == null ? spline : route.runner().spline();
        if (route.runner() != null) {
            spline = route.runner().spline();
            place = UnseenTravel.place(spline, s);
        }
        cargo.readings(now, level.getDayTime(), poweredStop(), links::linkPowered);
        route.tick(level, now, HeldKeys.NONE, null);
        RouteRunner runner = route.runner();
        if (runner == null) { speed = 0.0; return; }
        spline = runner.spline();

        s = route.command().drive() || !java.util.Objects.equals(before, route.pathwayId()) || spline != beforeSpline
                ? runner.progress() : located;
        if (!java.util.Objects.equals(before, route.pathwayId())) changed = true;
        RouteRunner.Command c = route.command();
        speed = c.drive() ? UnseenTravel.speedAfter(speed, c.throttle(), c.brakeLevel(), route.plant(), 0.05) : 0.0;
        if (runner.state() == RouteRunner.State.WAITING) speed = 0.0;
        s = UnseenTravel.advance(spline, s, speed, 0.05);
        place = UnseenTravel.place(spline, s);
        applied = new DriveModel.Output(c.throttle(), c.steer(), c.brakeLevel() > 0, false, lockAt(speed), 1,
                c.brakeLevel() >= 1 && c.throttle() == 0, c.brakeLevel());
    }

    public CompoundTag save() {
        CompoundTag tag = route.save();
        if (tag.contains("Runner")) tag.getCompound("Runner").putDouble("S", s);
        return tag;
    }

    private boolean poweredStop() {
        if (route.runner() == null || route.runner().state() != RouteRunner.State.WAITING) return false;
        var node = route.runner().pathway().node(route.runner().waitingAt());
        if (node == null) return false;
        return RouteSensors.stopPowered(level, BlockPos.containing(node.x(), node.y() + 0.01, node.z()));
    }

    @Override public ServerLevel routeLevel() { return level; }
    @Override public UUID consoleId() { return console; }
    @Override public UUID craftId() { return craft; }
    @Override public BlockPos consolePos() { return plotPos; }
    @Override public BlockEntity consoleEntity() { return null; }
    @Override public Object craft() { return null; }
    @Override public Object serverSubLevel() { return null; }
    @Override public Vector3d referenceLocal() { return new Vector3d(place.x(), place.y(), place.z()); }
    @Override public Vector3d noseLocal() { return new Vector3d(Math.sin(place.heading()), 0, -Math.cos(place.heading())); }
    @Override public Vector3d rightLocal() { return new Vector3d(Math.cos(place.heading()), 0, Math.sin(place.heading())); }
    @Override public double kappaPerStick(double lock) { return settings.getDouble("Kappa"); }
    @Override public double lockAt(double speed) { return settings.getDouble("Lock"); }
    @Override public boolean skidSteer() { return settings.getBoolean("Skid"); }
    @Override public String cannotFollow() { return ""; }
    @Override public boolean jointSteered() { return false; }
    @Override public double halfTrack() { return settings.getDouble("HalfTrack"); }
    @Override public double measuredSpeed() { return speed; }
    @Override public boolean calibrated() { return true; }
    @Override public void armCalibration() {}
    @Override public DriveModel.Output appliedOutput() { return applied; }
    @Override public int maxRpm() { return settings.getInt("MaxRpm"); }
    @Override public String vehicleName() { return name; }
    @Override public List<UUID> ownBodies() { return bodies; }
    @Override public List<BlockEntity> cargoBlocks() { return List.of(); }
    @Override public boolean seatedDriver() { return false; }
    @Override public void routeChanged() { changed = true; }
    @Override public boolean present() { return false; }
    @Override public double[] extents() { return extents.clone(); }
    @Override public StopRules.Cargo frozenCargo() { return cargo; }
    @Override public RouteSensors.Obstacle unseenObstacle() { return obstacle; }
}
