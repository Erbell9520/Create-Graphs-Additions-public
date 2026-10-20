package com.createstats.battle.world;

import com.createstats.battle.core.BoreCheck;
import com.createstats.battle.core.FireSolution;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunDrive;
import com.createstats.battle.core.GunGraph;
import com.createstats.battle.core.GunState;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class GunRecord {
    public static final int KIND_BIG = 0;
    public static final int KIND_AUTO = 1;
    public static final int KIND_UNKNOWN = 2;

    public static final int MOUNT_NORMAL = 0;
    public static final int MOUNT_HANGING = 1;
    public static final int MOUNT_FIXED = 2;

    public static final int AIM_LEARNING = 0;
    public static final int AIM_LEARNED = 1;
    public static final int AIM_FAULT = 2;

    final int id;

    final long mount;

    @Nullable final UUID body;
    String name = "";
    int mode = GunState.MODE_TRACK;

    double yawRatio;
    double pitchRatio;

    int yawSense = 1;
    int pitchSense = 1;

    GunArc arc = GunArc.FREE;

    int mountType = MOUNT_NORMAL;
    long yawGov = GunGraph.NONE;
    long pitchGov = GunGraph.NONE;
    boolean yawConflict;
    boolean pitchConflict;
    boolean shared;

    int yawLeader;
    int pitchLeader;

    long seenAt;

    @Nullable GunDrive.Axis yaw;
    @Nullable GunDrive.Axis pitch;

    double yawLaw = GunDrive.yawLaw();
    double pitchLaw = GunDrive.DEG_PER_RPM_TICK;

    boolean handAimed;

    int pitchMirror;
    @Nullable FireSolution.Answer answer;
    GunState state = GunState.IDLE;
    int axis;
    float argA;
    float argB;
    String argS = "";
    int kind = KIND_UNKNOWN;
    int load = -1;
    double v0;
    double minPitch = -30;
    double maxPitch = 60;

    double maxRange;

    @Nullable String blockedBy;

    boolean loadingNow;

    long salvoUntil;
    float train;
    float elev;

    boolean signalHeld;
    long lastShotAt = Long.MIN_VALUE / 2;
    long lastAttemptAt = Long.MIN_VALUE / 2;
    int dryAttempts;
    long loadCheckedAt = Long.MIN_VALUE / 2;

    @Nullable BoreCheck.Verdict bore;

    final BoreCheck.Watch watch = new BoreCheck.Watch();

    final double[] muzzleHistory = new double[5 * 3];
    final long[] muzzleTicks = new long[5];
    int muzzleCount;
    double lastYawAngle = Double.NaN;
    double lastPitchAngle = Double.NaN;

    boolean commanding;
    double aimError;
    double miss;
    int yawRpm;
    int pitchRpm;
    boolean downAnnounced;

    GunRecord(int id, long mount, @Nullable UUID body) {
        this.id = id;
        this.mount = mount;
        this.body = body;
    }

    public int id() {
        return id;
    }

    public long mount() {
        return mount;
    }

    public String label() {
        return name.isEmpty() ? "G" + id : name;
    }

    public int mode() {
        return mode;
    }

    public GunState state() {
        return state;
    }

    GunDrive.Axis yawAxis() {
        if (yaw == null) yaw = new GunDrive.Axis(yawRatio, yawSense, true);
        return yaw;
    }

    GunDrive.Axis pitchAxis() {
        if (pitch == null) pitch = new GunDrive.Axis(pitchRatio, pitchSense, false);
        return pitch;
    }

    boolean keepLearned() {
        boolean changed = false;
        if (yaw != null && (yaw.ratio() != yawRatio || yaw.sense() != yawSense)) {
            yawRatio = yaw.ratio();
            yawSense = yaw.sense();
            changed = true;
        }
        if (pitch != null && (pitch.ratio() != pitchRatio || pitch.sense() != pitchSense)) {
            pitchRatio = pitch.ratio();
            pitchSense = pitch.sense();
            changed = true;
        }
        return changed;
    }

    int aimStatus(long now) {
        if ((yaw != null && yaw.faulted(now)) || (pitch != null && pitch.faulted(now))) return AIM_FAULT;
        boolean yawOk = yaw != null ? yaw.known() : yawRatio != 0;
        boolean pitchOk = pitch != null ? pitch.known() : pitchRatio != 0;
        return mountType == MOUNT_FIXED || (yawOk && pitchOk) ? AIM_LEARNED : AIM_LEARNING;
    }

    double yawGainShown() {
        GunDrive.Axis a = yaw != null ? yaw : new GunDrive.Axis(yawRatio, yawSense, true);
        return a.known() ? a.gain(yawLaw) : 0.0;
    }

    double pitchGainShown() {
        GunDrive.Axis a = pitch != null ? pitch : new GunDrive.Axis(pitchRatio, pitchSense, false);
        return a.known() ? a.gain(pitchLaw) : 0.0;
    }

    void relearn() {
        yawRatio = 0;
        yawSense = 1;
        pitchRatio = 0;
        pitchSense = 1;
        if (yaw != null) yaw.relearn();
        if (pitch != null) pitch.relearn();
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putLong("Mount", mount);
        if (body != null) t.putUUID("Body", body);
        if (!name.isEmpty()) t.putString("Name", name);
        t.putByte("Mode", (byte) mode);
        keepLearned();

        t.putDouble("YawK", yawRatio);
        t.putByte("YawS", (byte) yawSense);
        t.putDouble("PitchK", pitchRatio);
        t.putByte("PitchS", (byte) pitchSense);
        t.putByte("MountType", (byte) mountType);
        if (!arc.free()) {
            t.putShort("ArcFrom", (short) arc.from());
            t.putShort("ArcTo", (short) arc.to());
            t.putByte("ArcLow", (byte) arc.low());
            t.putByte("ArcHigh", (byte) arc.high());
        }
        return t;
    }

    static GunRecord load(CompoundTag t) {
        GunRecord g = new GunRecord(t.getInt("Id"), t.getLong("Mount"), t.hasUUID("Body") ? t.getUUID("Body") : null);
        g.name = t.getString("Name");
        g.mode = Math.max(GunState.MODE_HOLD, Math.min(GunState.MODE_FIRE, t.getByte("Mode")));
        g.yawRatio = t.getDouble("YawK");
        g.yawSense = t.getByte("YawS") < 0 ? -1 : 1;
        g.pitchRatio = t.getDouble("PitchK");
        g.pitchSense = t.getByte("PitchS") < 0 ? -1 : 1;
        g.mountType = t.getByte("MountType");
        if (t.contains("ArcFrom")) g.arc = new GunArc(t.getShort("ArcFrom"), t.getShort("ArcTo"), t.getByte("ArcLow"), t.getByte("ArcHigh"));
        return g;
    }
}
