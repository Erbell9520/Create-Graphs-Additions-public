package com.createstats.cinecam;

import com.createstats.battle.world.SablePlots;
import com.createstats.cinecam.core.AutoZoom;
import com.createstats.cinecam.core.CineHead;
import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.CinePower;
import com.createstats.cinecam.core.CineRig;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.core.FocusPull;
import com.createstats.cinecam.core.FocusRange;
import com.createstats.cinecam.core.PhotoPrint;
import com.createstats.cinecam.core.RunLog;
import com.createstats.cinecam.core.SightLine;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGoggle;
import com.createstats.power.FePowerFx;
import com.createstats.power.FeProfiles;
import com.createstats.power.IFeConsumer;
import com.createstats.registry.ModDataComponents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

// line of sight every few ticks, a camera doesn't see through hills
public class CineCameraBE extends BlockEntity implements IFeConsumer, IHaveGoggleInformation {
    public static final int IDLE = 0;
    public static final int TRACKING = 1;
    public static final int LOST = 2;
    public static final int KIND_NONE = 0;
    public static final int KIND_ENTITY = 1;
    public static final int KIND_SHIP = 2;

    public static final double MAX_RANGE = 256.0;

    public static final double REACH = 8.0;

    public static final double AWAY_RANGE = 160.0;
    static final int LOS_EVERY = 10;
    static final int SAMPLE_EVERY = 10;

    static final int RUN_END = 100;

    static final int HAND_HOLD = 20;
    static final double DT = 0.05;

    private int mode;
    private float yaw;
    private float pitch;
    private int targetKind = KIND_NONE;
    private UUID targetId = new UUID(0L, 0L);
    private String targetName = "";
    private double frames;
    private float runSeconds, runDistance, runTopSpeed, runTopHeight, runBestClimb;
    private boolean runRough;
    private String runName = "";

    private float tStop = (float) FocusPull.OFF;

    private int filmRate = FilmFormat.DEFAULT_RATE;
    private FilmFormat.Resolution filmResolution = FilmFormat.DEFAULT_RESOLUTION;
    private FilmFormat.Quality filmQuality = FilmFormat.DEFAULT_QUALITY;
    private FilmFormat.Shape filmShape = FilmFormat.DEFAULT_SHAPE;

    private FilmFormat.Speed filmSpeed = FilmFormat.DEFAULT_SPEED;

    private boolean filmSound = true;

    private AutoZoom.Framing autoZoom = AutoZoom.DEFAULT;

    private PhotoPrint.Target photoTarget = PhotoPrint.DEFAULT_TARGET;
    private int photoSide = PhotoPrint.DEFAULT_SIDE;

    private final FeConsumer feConsumer = FeConsumer.forBlockEntity(this, FeProfiles.settings(FeProfiles.KEY_CINE_CAMERA));

    private int status = IDLE;
    private int targetEntityId = -1;
    private float bearing, elevation, range, rangeSigma, speed, climb, heading, height;
    private int photoSeq, markSeq;

    private boolean recording;

    private float handYaw, handPitch;
    private long handAt = -1;
    private long lostSince = -1;
    private boolean los = true;
    private RunLog run = new RunLog();
    private final double[][] history = new double[21][];
    private int historyHead;
    private boolean dirty;
    private boolean urgent;
    private int sinceSync;
    private final CineShips ships = new CineShips();
    @Nullable private UUID recorder;

    private long recordingSince;

    public float dispYaw, prevYaw, dispPitch, prevPitch;
    public float reel, prevReel, reelSpeed;
    public double driver, prevDriver, driverTarget;
    public float plunger, prevPlunger;
    private int shownMode = -1;
    private int seenPhoto = -1, seenMark = -1;
    private boolean clientPrimed;

    public CineCameraBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int mode() {
        return mode;
    }

    public CineMode cineMode() {
        return CineMode.byIndex(mode);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public int status() {
        return status;
    }

    public int targetKind() {
        return targetKind;
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    public int targetEntityId() {
        return targetEntityId;
    }

    public long frames() {
        return (long) frames;
    }

    public float bearing() {
        return bearing;
    }

    public float elevation() {
        return elevation;
    }

    public float range() {
        return range;
    }

    public float rangeSigma() {
        return rangeSigma;
    }

    public float speed() {
        return speed;
    }

    public float climb() {
        return climb;
    }

    public float heading() {
        return heading;
    }

    public float height() {
        return height;
    }

    public String runName() {
        return runName;
    }

    public float runSeconds() {
        return runSeconds;
    }

    public float runDistance() {
        return runDistance;
    }

    public float runTopSpeed() {
        return runTopSpeed;
    }

    public float runTopHeight() {
        return runTopHeight;
    }

    public float runBestClimb() {
        return runBestClimb;
    }

    public boolean runRough() {
        return runRough;
    }

    public double tStop() {
        return tStop;
    }

    @Override
    public FeConsumer feConsumer() {
        return feConsumer;
    }

    public boolean runs() {
        FeConsumer c = feConsumer;
        return CinePower.canRun(c.requiresPower(), c.isPowered(), c.getEnergyStored(), c.getMaxEnergyStored(),
                c.settings().activeDrain(), c.rebootThresholdPct());
    }

    public PhotoPrint.Target photoTarget() {
        return photoTarget;
    }

    public int photoSide() {
        return photoSide;
    }

    public int photoFormat() {
        return PhotoPrint.pack(photoTarget, photoSide);
    }

    public void setPhotoFormat(int packed) {
        PhotoPrint.Target t = PhotoPrint.targetOf(packed);
        int side = PhotoPrint.sideOf(packed);
        if (t == photoTarget && side == photoSide) return;
        photoTarget = t;
        photoSide = side;
        markUrgent();
    }

    public int filmRate() {
        return filmRate;
    }

    public FilmFormat.Resolution filmResolution() {
        return filmResolution;
    }

    public FilmFormat.Quality filmQuality() {
        return filmQuality;
    }

    public FilmFormat.Shape filmShape() {
        return filmShape;
    }

    public FilmFormat.Speed filmSpeed() {
        return filmSpeed;
    }

    public boolean filmSound() {
        return filmSound;
    }

    public AutoZoom.Framing autoZoom() {
        return autoZoom;
    }

    public void setFilmSound(boolean on) {
        if (recording || on == filmSound) return;
        filmSound = on;
        markUrgent();
    }

    public void setAutoZoom(int index) {
        AutoZoom.Framing f = AutoZoom.Framing.byIndex(index);
        if (f == autoZoom) return;
        autoZoom = f;
        markUrgent();
    }

    public int filmFormat() {
        return FilmFormat.pack(filmRate, filmResolution, filmQuality, filmShape, filmSpeed);
    }

    public boolean recording() {
        return recording;
    }

    public long recordingTicks() {
        return recording && level != null ? Math.max(0L, level.getGameTime() - recordingSince) : 0L;
    }

    public boolean filming() {
        return cineMode() == CineMode.FILM ? recording : status == TRACKING;
    }

    public double facingDegrees() {
        return CineCameraBlock.facingDegrees(getBlockState());
    }

    public int comparatorSignal() {
        return status == TRACKING ? 15 : status == LOST ? 8 : 0;
    }

    public Vec3 trunnionLocal() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + CineRig.TRUNNION_Y / 16.0,
                worldPosition.getZ() + 0.5);
    }

    @Nullable
    public Vec3 trunnionWorld() {
        return SableBridge.worldPointOf(this, trunnionLocal());
    }

    @Nullable
    private Quaterniond body() {
        return SableBridge.bodyOrientation(SableBridge.subLevelAccessOf(this));
    }

    public double[] toWorldDir(double[] local) {
        Quaterniond q = body();
        if (q == null) return local;
        Vector3d v = q.transform(new Vector3d(local[0], local[1], local[2]));
        return new double[]{v.x, v.y, v.z};
    }

    public double[] toLocalDir(double[] world) {
        Quaterniond q = body();
        if (q == null) return world;
        Vector3d v = new Quaterniond(q).conjugate().transform(new Vector3d(world[0], world[1], world[2]));
        return new double[]{v.x, v.y, v.z};
    }

    public double[] sightWorld() {
        return toWorldDir(CineHead.sight(facingDegrees(), yaw, pitch));
    }

    public void setMode(int m) {
        int next = Math.floorMod(m, CineMode.values().length);
        if (next == mode) return;
        mode = next;
        markUrgent();
    }

    public void handAim(float wantYaw, float wantPitch) {
        if (level == null) return;
        handYaw = (float) CineHead.wrap180(wantYaw);
        handPitch = (float) CineHead.clampPitch(wantPitch);
        handAt = level.getGameTime();
    }

    public void lock(int kind, UUID id, String name) {
        if (kind == KIND_NONE) {
            release();
            return;
        }

        if (!runs()) return;
        if (kind == targetKind && id.equals(targetId)) return;
        endRun();
        targetKind = kind;
        targetId = id;
        targetName = name == null ? "" : name;
        targetEntityId = -1;
        lostSince = -1;
        los = true;
        markUrgent();
    }

    public void release() {
        if (targetKind == KIND_NONE) return;
        endRun();
        targetKind = KIND_NONE;
        targetId = new UUID(0L, 0L);
        targetName = "";
        targetEntityId = -1;
        status = IDLE;
        markUrgent();
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    public void mark() {
        markSeq++;
        markUrgent();
    }

    public void photographed() {
        photoSeq++;
        markUrgent();
    }

    public void setRecording(boolean on, @Nullable ServerPlayer by) {
        if (on && !runs()) on = false;
        recorder = on && by != null ? by.getUUID() : null;
        if (on == recording) return;
        recording = on;
        if (on && level != null) recordingSince = level.getGameTime();
        if (on && by != null) com.createstats.advancement.ModAdvancements.award(by, com.createstats.advancement.ModAdvancements.ACTION);

        if (level instanceof ServerLevel sl) {
            if (on && by != null && filmSpeed.slow()) CineSlowMotion.begin(sl, worldPosition, by, filmSpeed.factor());
            else if (!on) CineSlowMotion.end(sl, worldPosition);
        }
        markUrgent();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) CineSlowMotion.end(sl, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel sl) CineSlowMotion.end(sl, worldPosition);
    }

    public void setTStop(double t) {
        float next = (float) FocusPull.clampStop(t);
        if (next == tStop) return;
        tStop = next;
        markUrgent();
    }

    public void setFilmFormat(int packed) {
        if (recording) return;
        int rate = FilmFormat.rateOf(packed);
        FilmFormat.Resolution res = FilmFormat.resolutionOf(packed);
        FilmFormat.Quality quality = FilmFormat.qualityOf(packed);
        FilmFormat.Shape shape = FilmFormat.shapeOf(packed);
        FilmFormat.Speed speed = FilmFormat.speedOf(packed);
        if (rate == filmRate && res == filmResolution && quality == filmQuality && shape == filmShape
                && speed == filmSpeed) return;
        filmRate = rate;
        filmResolution = res;
        filmQuality = quality;
        filmShape = shape;
        filmSpeed = speed;
        markUrgent();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();

        boolean powered = feConsumer.serviceTick(filming());
        FePowerFx.handleTransition(sl, worldPosition, feConsumer);
        if (!powered && recording) setRecording(false, null);
        CineMode m = cineMode();
        Vec3 lens = trunnionWorld();
        Vec3 tp = targetKind != KIND_NONE && lens != null ? resolve(sl) : null;
        boolean seen = false;
        if (tp != null && tp.distanceTo(lens) <= MAX_RANGE) {
            if (now % LOS_EVERY == 0) los = clear(sl, lens, tp, targetKind == KIND_SHIP);
            seen = los;
        }
        int was = status;
        status = targetKind == KIND_NONE ? IDLE : seen ? TRACKING : LOST;
        if (recording && now % 20 == 0) {
            ServerPlayer op = recorder == null ? null : sl.getServer().getPlayerList().getPlayer(recorder);
            boolean near = op != null && op.level() == sl && (lens == null || op.getEyePosition().distanceTo(lens) <= AWAY_RANGE);
            if (m != CineMode.FILM || !near) setRecording(false, null);
        }
        if (filming()) frames += 24.0 / 20.0;
        if (seen) {
            lostSince = -1;
            if (powered) steer(lens, tp);
            measure(now, lens, tp);
        } else {
            if (status == LOST) {
                if (lostSince < 0) lostSince = now;
                else if (now - lostSince == RUN_END) endRun();
            }
            if (!powered) {
            } else if (tp != null) {
                steer(lens, tp);
            } else if (handAt >= 0 && now - handAt <= HAND_HOLD) {
                step(handYaw, handPitch, CineHead.TAU_HAND);
            }
        }
        if (was != status) {
            sl.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            markUrgent();
        }
        sync(sl);
    }

    private void steer(Vec3 lens, Vec3 tp) {
        double[] local = toLocalDir(new double[]{tp.x - lens.x, tp.y - lens.y, tp.z - lens.z});
        step(CineHead.yawFor(CineHead.bearing(local[0], local[2]), facingDegrees()),
                CineHead.clampPitch(CineHead.elevation(local[0], local[1], local[2])), CineHead.TAU_TRACK);
    }

    private void step(double wantYaw, double wantPitch, double tau) {
        float ny = (float) CineHead.wrap180(CineHead.follow(yaw, wantYaw, DT, tau, CineHead.AZ_RATE, true));
        float np = (float) CineHead.follow(pitch, wantPitch, DT, tau, CineHead.EL_RATE, false);
        if (Math.abs(CineHead.wrap180(ny - yaw)) > 1e-4 || Math.abs(np - pitch) > 1e-4) dirty = true;
        yaw = ny;
        pitch = np;
    }

    @Nullable
    private Vec3 resolve(ServerLevel sl) {
        if (targetKind == KIND_ENTITY) {
            Entity e = sl.getEntity(targetId);
            if (e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive())) return null;
            if (targetEntityId != e.getId()) {
                targetEntityId = e.getId();
                markUrgent();
            }
            if (targetName.isEmpty()) targetName = e.getName().getString();
            return e.getBoundingBox().getCenter();
        }
        if (targetKind == KIND_SHIP) {
            Object sub = ships.find(sl, targetId, sl.getGameTime());
            if (sub == null) return null;
            Vector3d c = SableBridge.subLevelWorldCenter(sub);
            if (c == null) return null;
            String nm = SableBridge.subLevelName(sub);
            if (nm != null && !nm.isEmpty() && !nm.equals(targetName)) {
                targetName = nm;
                markUrgent();
            }
            return new Vec3(c.x, c.y, c.z);
        }
        return null;
    }

    boolean clear(ServerLevel sl, Vec3 lens, Vec3 aim, boolean aimIsTargetShip) {
        Vec3 d = aim.subtract(lens);
        double len = d.length();
        if (len < 1e-6) return true;
        Vec3 u = d.scale(1.0 / len);
        double[] local = toLocalDir(new double[]{u.x, u.y, u.z});
        double start = SightLine.exit(0.5, CineRig.TRUNNION_Y / 16.0, 0.5, local[0], local[1], local[2]) + SightLine.CLEAR;
        if (start >= len) return true;
        BlockHitResult hit = sl.clip(new ClipContext(lens.add(u.scale(start)), aim, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return true;
        UUID ship = SablePlots.shipAt(sl, hit.getBlockPos());
        Vec3 at = ship == null ? hit.getLocation() : SableBridge.projectOutOfSubLevel(sl, hit.getLocation());
        return SightLine.reached(false, aimIsTargetShip && targetId.equals(ship), at.distanceTo(aim));
    }

    private void measure(long now, Vec3 lens, Vec3 tp) {
        double t = now / 20.0;
        double[] sight = sightWorld();
        bearing = (float) CineHead.bearing(sight[0], sight[2]);
        elevation = (float) CineHead.elevation(sight[0], sight[1], sight[2]);
        double trueRange = tp.distanceTo(lens);
        double r = FocusRange.reading(trueRange, worldPosition.asLong() * 0x9E3779B97F4A7C15L, t);
        range = (float) r;
        rangeSigma = (float) FocusRange.sigma(trueRange);
        double[] pos = {lens.x + sight[0] * r, lens.y + sight[1] * r, lens.z + sight[2] * r};
        height = (float) pos[1];

        double[] old = history[historyHead];
        history[historyHead] = pos;
        historyHead = (historyHead + 1) % history.length;
        if (old != null) {
            double seconds = (history.length - 1) * DT;
            double vx = (pos[0] - old[0]) / seconds;
            double vy = (pos[1] - old[1]) / seconds;
            double vz = (pos[2] - old[2]) / seconds;
            speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            climb = (float) vy;
            if (Math.hypot(vx, vz) > 0.2) heading = (float) CineHead.bearing(vx, vz);
        }
        if (now % SAMPLE_EVERY == 0) run.add(t, pos[0], pos[1], pos[2]);
        dirty = true;
    }

    private void endRun() {
        java.util.Arrays.fill(history, null);
        if (run.isEmpty()) return;
        RunLog.Summary s = run.summary();
        if (s.samples() >= 2) {
            runSeconds = (float) s.seconds();
            runDistance = (float) s.distance();
            runTopSpeed = (float) s.topSpeed();
            runTopHeight = (float) s.topHeight();
            runBestClimb = (float) s.bestClimb();

            runRough = true;
            runName = targetName;
        }
        run = new RunLog();
        markUrgent();
    }

    private void markUrgent() {
        dirty = true;
        urgent = true;
        setChanged();
    }

    private void sync(ServerLevel sl) {
        sinceSync++;
        if (urgent || (dirty && sinceSync >= 4)) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            urgent = false;
            dirty = false;
            sinceSync = 0;
        }
    }

    public void clientTick() {
        if (!clientPrimed) {
            dispYaw = prevYaw = yaw;
            dispPitch = prevPitch = pitch;
            shownMode = mode;
            driver = prevDriver = driverTarget = cineMode().slot() * 360.0;
            seenPhoto = photoSeq;
            seenMark = markSeq;
            clientPrimed = true;
        }
        prevYaw = dispYaw;
        prevPitch = dispPitch;
        dispYaw = (float) CineHead.follow(dispYaw, yaw, DT, 0.1, 400.0, true);
        dispPitch = (float) CineHead.follow(dispPitch, pitch, DT, 0.1, 300.0, false);

        prevReel = reel;
        float want = filming() ? 420f : 0f;
        reelSpeed += (want - reelSpeed) * (float) (1.0 - Math.exp(-DT / 0.18));
        reel += reelSpeed * (float) DT;
        if (markSeq != seenMark) {
            reel += 30f;
            plunger = 0.4f;
            seenMark = markSeq;
        }

        prevDriver = driver;
        if (mode != shownMode) {
            driverTarget += 360.0 * CineMode.stepsBetween(CineMode.byIndex(shownMode), cineMode());
            shownMode = mode;
        }
        double d = driverTarget - driver;
        driver += Math.signum(d) * Math.min(Math.abs(d), 720.0 * DT);
        prevPlunger = plunger;
        if (photoSeq != seenPhoto) {
            plunger = 0.4f;
            seenPhoto = photoSeq;
        } else if (plunger > 0f) {
            plunger = Math.max(0f, plunger - 0.08f);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ModeKey", cineMode().key());
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putInt("TargetKind", targetKind);
        tag.putUUID("Target", targetId);
        tag.putString("TargetName", targetName);
        tag.putDouble("Frames", frames);
        CompoundTag r = new CompoundTag();
        r.putString("Name", runName);
        r.putFloat("Seconds", runSeconds);
        r.putFloat("Distance", runDistance);
        r.putFloat("TopSpeed", runTopSpeed);
        r.putFloat("TopHeight", runTopHeight);
        r.putFloat("BestClimb", runBestClimb);
        r.putBoolean("Rough", runRough);
        tag.put("LastRun", r);
        tag.putFloat("TStop", tStop);
        CompoundTag f = new CompoundTag();
        f.putInt("Rate", filmRate);
        f.putString("Resolution", filmResolution.key());
        f.putString("Quality", filmQuality.key());
        f.putString("Shape", filmShape.key());
        f.putString("Speed", filmSpeed.key());
        f.putBoolean("Sound", filmSound);
        tag.put("Film", f);
        tag.putString("AutoZoom", autoZoom.key());
        CompoundTag ph = new CompoundTag();
        ph.putString("Target", photoTarget.key());
        ph.putInt("Side", photoSide);
        tag.put("Photo", ph);
        feConsumer.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        CineMode m = CineMode.fromSavedIndex(tag.getInt("Mode"));
        if (tag.contains("ModeKey")) {
            for (CineMode c : CineMode.values()) {
                if (c.key().equals(tag.getString("ModeKey"))) m = c;
            }
        }
        mode = m.ordinal();
        yaw = tag.getFloat("Yaw");
        pitch = tag.getFloat("Pitch");
        targetKind = tag.getInt("TargetKind");
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : new UUID(0L, 0L);
        targetName = tag.getString("TargetName");
        frames = tag.getDouble("Frames");
        CompoundTag r = tag.getCompound("LastRun");
        runName = r.getString("Name");
        runSeconds = r.getFloat("Seconds");
        runDistance = r.getFloat("Distance");
        runTopSpeed = r.getFloat("TopSpeed");
        runTopHeight = r.getFloat("TopHeight");
        runBestClimb = r.getFloat("BestClimb");
        runRough = r.getBoolean("Rough");

        tStop = (float) FocusPull.clampStop(tag.getFloat("TStop"));

        CompoundTag f = tag.getCompound("Film");
        filmRate = f.contains("Rate") ? FilmFormat.clampRate(f.getInt("Rate")) : FilmFormat.DEFAULT_RATE;
        filmResolution = FilmFormat.Resolution.byKey(f.getString("Resolution"));
        filmQuality = FilmFormat.Quality.byKey(f.getString("Quality"));
        filmShape = FilmFormat.Shape.byKey(f.getString("Shape"));

        filmSpeed = FilmFormat.Speed.byKey(f.getString("Speed"));

        filmSound = !f.contains("Sound") || f.getBoolean("Sound");
        autoZoom = AutoZoom.Framing.byKey(tag.getString("AutoZoom"));

        CompoundTag ph = tag.getCompound("Photo");
        photoTarget = PhotoPrint.Target.byKey(ph.getString("Target"));
        photoSide = ph.contains("Side") ? PhotoPrint.clampSide(ph.getInt("Side")) : PhotoPrint.DEFAULT_SIDE;
        feConsumer.load(tag);
        if (tag.contains("Status")) {
            status = tag.getInt("Status");
            targetEntityId = tag.getInt("EntityId");
            bearing = tag.getFloat("Bearing");
            elevation = tag.getFloat("Elevation");
            range = tag.getFloat("Range");
            rangeSigma = tag.getFloat("RangeSigma");
            speed = tag.getFloat("Speed");
            climb = tag.getFloat("Climb");
            heading = tag.getFloat("Heading");
            height = tag.getFloat("Height");
            photoSeq = tag.getInt("PhotoSeq");
            markSeq = tag.getInt("MarkSeq");
            recording = tag.getBoolean("Recording");
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        int fe = feConsumer.getEnergyStored();
        if (fe > 0) builder.set(ModDataComponents.STORED_FE.get(), fe);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer fe = input.get(ModDataComponents.STORED_FE.get());
        if (fe == null || fe <= 0) return;
        CompoundTag charge = new CompoundTag();
        charge.putInt("FeEnergy", fe);
        feConsumer.load(charge);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        tag.putInt("Status", status);
        tag.putInt("EntityId", targetEntityId);
        tag.putFloat("Bearing", bearing);
        tag.putFloat("Elevation", elevation);
        tag.putFloat("Range", range);
        tag.putFloat("RangeSigma", rangeSigma);
        tag.putFloat("Speed", speed);
        tag.putFloat("Climb", climb);
        tag.putFloat("Heading", heading);
        tag.putFloat("Height", height);
        tag.putInt("PhotoSeq", photoSeq);
        tag.putInt("MarkSeq", markSeq);
        tag.putBoolean("Recording", recording);
        return tag;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FeConsumer.Settings s = feConsumer.settings();
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.cine_camera")));
        tooltip.add(FeGoggle.loadState(false, runs(), feConsumer.isBurstStarved()));
        tooltip.add(FeGoggle.stored(feConsumer.getEnergyStored(), feConsumer.getMaxEnergyStored()));
        tooltip.add(FeGoggle.dim(Component.translatable("create_stats.cine_camera.goggle.draw",
                s.activeDrain(), s.idleDrain()).getString()));
        tooltip.add(FeGoggle.dim(Component.translatable("create_stats.cine_camera.goggle.battery",
                CinePower.duration(CinePower.ticksAt(feConsumer.getEnergyStored(), s.activeDrain()))).getString()));
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
