package com.createstats.battle.world;

import com.createstats.CreateStats;
import com.createstats.battle.core.BoreCheck;
import com.createstats.battle.core.FireSolution;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunDrive;
import com.createstats.battle.core.GunGraph;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.LineOfFire;
import com.createstats.battle.core.SalvoResult;
import com.createstats.battle.core.Trajectory;
import com.createstats.compat.CreateBigCannonsCompat;
import com.createstats.compat.CreateBigCannonsReflectionAdapter;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FireControl {
    static final int SALVO_HOLD = 20;

    static final int BIG_RETRY = 10;

    static final int DRY_LIMIT = 3;

    static final double PLAYER_RANGE = 96;

    static final double PLAYER_PATH = 64;

    static final double PLAYER_GROW = 1.0;

    static final int LOAD_EVERY = 10;

    static final double G_AUTO = -0.025;
    static final double G_BIG = -0.05;
    static final double K_STOCK = 0.01;

    private static volatile boolean warned;
    private static volatile boolean boreWarned;

    private FireControl() {
    }

    static boolean tick(ShipRecord r, ServerLevel level, BlockPos bridgePos, Vec3 bridgeWorld, boolean powered, long now) {
        if (!r.firingCleared) {
            clearRecorded(r, level);
            r.firingCleared = true;
        }
        if (!powered) {
            letGoAll(r, level);
            for (GunRecord g : r.guns) judgeUnpowered(r, level, g);
            r.salvoWanted = false;
            return false;
        }
        if (now >= r.nextGunScan || r.gunScanWanted) {
            GunFinder.scan(r, level, now);
            r.nextGunScan = now + GunFinder.EVERY;
            r.gunScanWanted = false;
        }
        ShipScanner.Area area = ShipScanner.area(r, level);
        Object body = area == null ? null : area.subLevel();
        recordBridge(r, bridgeWorld, now);
        upkeepTarget(r, level, bridgeWorld, bridgePos, now);
        r.targetLine = TargetFinder.line(r, body, bridgeWorld, r.bridgeVel);
        if ((r.contactsDue || now % TargetFinder.CONTACT_EVERY == 0) && r.watched(now)) {
            r.contactsDue = false;
            r.contacts = TargetFinder.contacts(r, level, bridgeWorld, body, area == null ? null : area.bounds(), r.bridgeVel, now);
        }

        boolean salvo = r.salvoWanted;
        ServerPlayer caller = r.salvoCaller;
        r.salvoWanted = false;
        r.salvoCaller = null;
        SalvoResult.Builder answer = salvo ? new SalvoResult.Builder() : null;

        boolean active = false;
        Crowd crowd = new Crowd(level, area == null ? null : area.bounds(), area != null && area.subLevel() != null);
        for (GunRecord g : r.guns) {
            boolean aimTick = ((now + g.id) & 1) == 0;
            try {
                active |= judge(r, level, g, bridgePos, body, area == null ? null : area.bounds(), aimTick, answer, crowd, now);
            } catch (RuntimeException e) {
                if (!warned) {
                    warned = true;
                    CreateStats.LOGGER.warn("[Battle Bridge] fire control failed for G{}: {}", g.id, e.toString());
                }
                letGo(r, level, g);
            }
            if (g.keepLearned()) r.setDirty();
        }
        if (r.designationCaller != null && now > r.designationAt + 1) {
            if (r.target != null) r.designationCaller.displayClientMessage(BattleCrew.designated(r), true);
            r.designationCaller = null;
        }
        if (answer != null) {
            SalvoResult result = answer.build();
            r.salvoAt = now;
            r.salvo = result.encode();
            r.log(now, ShipRecord.LOG_SALVO, result.fired().size(), result.notReady().size(), caller == null ? r.salvoBy : caller.getGameProfile().getName());
            if (caller != null) caller.displayClientMessage(describe(result), true);
            r.changed();
        }
        return active;
    }

    private static void upkeepTarget(ShipRecord r, ServerLevel level, Vec3 bridgeWorld, BlockPos bridgePos, long now) {
        TargetFinder.Target t = r.target;
        if (t == null) return;
        switch (TargetFinder.track(r, level, bridgeWorld, now)) {
            case DOWN -> clearTarget(r, level, bridgePos, now, ShipRecord.LOG_TARGET_DOWN, true);
            case GONE -> clearTarget(r, level, bridgePos, now, ShipRecord.LOG_TARGET_GONE, true);
            case LOST -> {
                if (t.lostSince < 0) {
                    t.lostSince = now;
                    r.log(now, ShipRecord.LOG_TARGET_LOST, 0, 0, t.name);
                    r.changed();
                }
                if (t.track.expired(now)) clearTarget(r, level, bridgePos, now, ShipRecord.LOG_TARGET_DROPPED, false);
            }
            case SEEN -> {
                if (t.lostSince >= 0) {
                    t.lostSince = -1L;
                    r.changed();
                }
            }
            default -> {
            }
        }
    }

    static void clearTarget(ShipRecord r, ServerLevel level, BlockPos bridgePos, long now, byte why, boolean tone) {
        TargetFinder.Target t = r.target;
        if (t == null) return;
        r.log(now, why, 0, 0, t.name);
        r.target = null;
        r.setDirty();
        r.changed();
        if (tone) r.targetDownSeq++;
        for (GunRecord g : r.guns) letGo(r, level, g);
    }

    private static void judgeUnpowered(ShipRecord r, ServerLevel level, GunRecord g) {
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, BlockPos.of(g.mount));
        boolean present = GunFinder.isAnyMount(be);
        g.state = !present ? GunState.DOWN : g.mode == GunState.MODE_HOLD ? GunState.HOLD : GunState.NO_POWER;
        g.argS = "";
    }

    private static boolean judge(ShipRecord r, ServerLevel level, GunRecord g, BlockPos bridgePos, @Nullable Object ownBody,
                                 @Nullable AABB ownBounds, boolean aimTick, @Nullable SalvoResult.Builder salvo, Crowd crowd,
                                 long now) {
        BlockPos mountPos = BlockPos.of(g.mount);
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, mountPos);
        if (!GunFinder.isAnyMount(be)) {
            if (!g.downAnnounced) {
                g.downAnnounced = true;
                r.gunDownSeq++;
                r.log(now, ShipRecord.LOG_GUN_DOWN, g.id, 0, g.label());
            }
            letGo(r, level, g);
            g.state = GunState.DOWN;
            int packed = r.pack(mountPos);
            g.argS = packed < 0 || g.body != null ? "" : r.namer().geometry(packed);
            if (salvo != null && g.mode != GunState.MODE_HOLD) salvo.notReady(g.label(), g.state, -1);
            return false;
        }
        g.downAnnounced = false;
        boolean fixed = GunFinder.isFixedMount(be);
        CreateBigCannonsReflectionAdapter.MountedAimInspection aim = CreateBigCannonsReflectionAdapter.inspectMountedAim(be);
        boolean assembled = aim.available() && aim.running() && aim.mountedEntityId() != Integer.MIN_VALUE;
        if (assembled && (now - g.loadCheckedAt >= LOAD_EVERY || now - g.lastShotAt < 4)) {
            readLoad(g, be, level, now);
        } else if (assembled && g.kind == GunRecord.KIND_BIG && r.target != null && g.mode != GunState.MODE_HOLD) {
            readBore(g, be, level, now);
        }
        if (!assembled) {
            g.bore = null;
            g.watch.reset();
            g.loadCheckedAt = Long.MIN_VALUE / 2;
        }
        g.handAimed = assembled && !fixed && !mountCanTurn(be);
        if (assembled && !fixed) g.pitchLaw = pitchLaw(be, g.pitchLaw);

        Object body = SableBridge.subLevelAccessOf(be);
        Vec3 o = aim.aimOriginLocation();
        Vec3 f = aim.aimForwardLocation();
        double[] barrelL = Frames.normalize(new double[]{f.x - o.x, f.y - o.y, f.z - o.z});
        double[] barrelW = Frames.dirToWorld(body, barrelL);
        double back = g.kind == GunRecord.KIND_AUTO ? 1.5 : 2.0;
        double[] muzzleW = Frames.toWorld(body, f.x - barrelL[0] * back, f.y - barrelL[1] * back, f.z - barrelL[2] * back);
        recordMuzzle(g, muzzleW, now);
        double[] trainElev = TargetFinder.bearing(r, ownBody, barrelW);
        g.train = (float) trainElev[0];
        g.elev = (float) trainElev[1];

        RpmGovernorBE yawGov = fixed ? null : governor(level, g.yawGov);
        RpmGovernorBE pitchGov = fixed ? null : governor(level, g.pitchGov);
        boolean yawDrive = fixed || (yawGov != null && !g.yawConflict && !g.shared);
        boolean pitchDrive = fixed || (pitchGov != null && !g.pitchConflict && !g.shared);
        boolean yawTurning = fixed || yawGov == null || turning(yawGov);
        boolean pitchTurning = fixed || pitchGov == null || turning(pitchGov);
        boolean yawHeld = heldElsewhere(r, yawGov);
        boolean pitchHeld = heldElsewhere(r, pitchGov);
        boolean over = (yawGov != null && yawGov.isOverStressed()) || (pitchGov != null && pitchGov.isOverStressed());
        boolean fault = !fixed && ((g.yaw != null && g.yaw.faulted(now)) || (g.pitch != null && g.pitch.faulted(now)));

        double curYaw = FireSolution.yawOf(barrelL[0], barrelL[2]);
        double curPitch = FireSolution.pitchOf(barrelL[0], barrelL[1], barrelL[2]);
        double fieldYaw = aim.yawDegrees();
        double fieldPitch = aim.pitchDegrees();
        double[] limits = pitchLimits(be, g);
        boolean atTop = fieldPitch >= limits[1] - 0.5;
        boolean atBottom = fieldPitch <= -limits[0] + 0.5;
        if (!fixed && g.commanding) {
            if (yawGov != null && !Double.isNaN(g.lastYawAngle) && g.yawLeader == 0) {
                GunDrive.observe(g.yawAxis(), g.yawLaw, fieldYaw - g.lastYawAngle, yawGov.getAppliedRpm(), aim.yawSpeed(),
                        false, yawGov.isOverStressed(), now);
            }
            if (pitchGov != null && !Double.isNaN(g.lastPitchAngle) && g.pitchLeader == 0) {
                GunDrive.observe(g.pitchAxis(), g.pitchLaw, fieldPitch - g.lastPitchAngle, pitchGov.getAppliedRpm(),
                        aim.pitchSpeed(), atTop || atBottom, pitchGov.isOverStressed(), now);
            }
        }
        g.lastYawAngle = fieldYaw;
        g.lastPitchAngle = fieldPitch;

        if (Math.abs(fieldPitch) > 2 && Math.abs(curPitch) > 1) g.pitchMirror = Math.signum(curPitch) == Math.signum(fieldPitch) ? 1 : -1;
        else if (g.pitchMirror == 0) g.pitchMirror = g.mountType == GunRecord.MOUNT_HANGING ? -1 : 1;

        TargetFinder.Target t = r.target;

        boolean speedKnown = g.v0 > 0.05;
        boolean engaged = assembled && !g.handAimed && g.mode != GunState.MODE_HOLD && t != null && t.track.hasFix()
                && t.lostSince < 0;
        boolean driveOk = fixed || (yawDrive && pitchDrive && yawTurning && pitchTurning && !yawHeld && !pitchHeld && !fault);

        int solution = 0;
        boolean onTarget = false;
        boolean blocked = false;
        g.argS = "";
        g.axis = GunState.AXIS_YAW;
        g.argA = 0;
        g.argB = 0;
        double[] wantedW = null;
        if (engaged) {
            Trajectory shell = shell(g, level);

            double[] tp = SpotterLink.corrected(r, t.track.predict(now, 0), now);
            double[] tv = t.track.velocity();
            if (aimTick || g.answer == null) {
                g.answer = speedKnown ? FireSolution.solve(shell, g.v0, muzzleW, tp, tv, -89, 89, g.answer) : straightAt(muzzleW, tp);
            }
            FireSolution.Answer a = g.answer;
            if (a.kind() == FireSolution.Kind.OUT_OF_RANGE) {
                solution = 1;
                g.argA = (float) a.range();
                g.argB = (float) g.maxRange;
            } else {
                wantedW = FireSolution.direction(a.yaw(), a.pitch());
                double[] wantedL = Frames.dirToLocal(body, wantedW);
                double wantPitch = FireSolution.pitchOf(wantedL[0], wantedL[1], wantedL[2]);
                boolean hanging = g.mountType == GunRecord.MOUNT_HANGING;
                double lo = hanging ? -limits[1] : -limits[0];
                double hi = hanging ? limits[0] : limits[1];

                GunArc arc = fixed ? GunArc.FREE : g.arc;
                double wantTrain = TargetFinder.bearing(r, ownBody, wantedW)[0];
                double aimElev = fixed ? wantPitch : arc.aimElevation(wantPitch, lo, hi);
                if (!fixed && !arc.trains(wantTrain, 0.0)) {
                    solution = 2;
                    g.axis = GunState.AXIS_YAW;
                    g.argA = (float) wantTrain;
                } else if (!fixed && Math.abs(aimElev - wantPitch) > 0.5) {
                    solution = 2;
                    g.axis = GunState.AXIS_PITCH;
                    g.argA = (float) wantPitch;
                    g.argB = (float) aimElev;
                }
                double alpha = FireSolution.angleDeg(barrelW, wantedW);
                g.aimError = alpha;
                g.miss = a.range() * Math.tan(Math.toRadians(Math.min(alpha, 89)));
                double tol = Math.max(0.75, Math.min(6, t.radius));

                onTarget = g.miss <= tol && (fixed || (arc.trains(g.train, GunArc.SLACK) && arc.elevates(g.elev, GunArc.SLACK)));
                if (solution == 0 && fixed && !onTarget) {
                    g.argA = (float) FireSolution.wrap(FireSolution.yawOf(wantedL[0], wantedL[2]), curYaw);
                    g.argB = (float) (wantPitch - curPitch);
                } else if (solution == 0 && !onTarget) {
                    g.argA = (float) slewSeconds(g, alpha);
                }

                double[] driveL = wantedL;
                if (!fixed && (!arc.allRound() || aimElev != wantPitch)) {
                    double train = arc.steer(g.train, arc.aimBearing(wantTrain, g.train));
                    driveL = Frames.dirToLocal(body, TargetFinder.fromBearing(r, ownBody, train, aimElev));
                }
                if (!fixed && aimTick && driveOk) {
                    double rate = g.kind == GunRecord.KIND_AUTO ? GunDrive.RATE_AUTOCANNON : GunDrive.RATE_BIG_CANNON;
                    double wantYaw = FireSolution.yawOf(driveL[0], driveL[2]);
                    double drivePitch = FireSolution.pitchOf(driveL[0], driveL[1], driveL[2]);
                    if (g.yawLeader == 0 && yawGov != null) {
                        g.yawRpm = GunDrive.command(g.yawAxis(), g.yawLaw, wantYaw, fieldYaw, rate, false, false, now);
                        yawHeld |= !command(r, yawGov, bridgePos, g.yawRpm);
                    }
                    if (g.pitchLeader == 0 && pitchGov != null) {
                        g.pitchRpm = GunDrive.command(g.pitchAxis(), g.pitchLaw, drivePitch * g.pitchMirror, fieldPitch, rate,
                                atTop, atBottom, now);
                        pitchHeld |= !command(r, pitchGov, bridgePos, g.pitchRpm);
                    }
                    g.commanding = true;
                } else if (!fixed && g.commanding && !aimTick) {
                    heartbeat(r, yawGov, bridgePos, g.yawLeader == 0);
                    heartbeat(r, pitchGov, bridgePos, g.pitchLeader == 0);
                }
                // fly the BARREL not the solution, every tick. close crossing target + 6m miss allowance = rounds into our own envelope

                if (solution == 0 && onTarget && speedKnown) {
                    double[] v0vec = {barrelW[0] * g.v0, barrelW[1] * g.v0, barrelW[2] * g.v0};
                    g.blockedBy = lineOfFire(r, g, shell, muzzleW, v0vec, ownBody, t, crowd);
                } else {
                    g.blockedBy = null;
                }
                blocked = g.blockedBy != null;
                if (blocked) g.argS = g.blockedBy;
            }
        } else {
            g.answer = null;
            g.blockedBy = null;
            if (g.commanding) letGoGovernors(r, level, g);
        }
        if (!engaged || !driveOk) {
            if (g.commanding && !driveOk) letGoGovernors(r, level, g);
        }

        boolean empty = g.kind == GunRecord.KIND_UNKNOWN ? false : g.load == 0;
        boolean loading = g.loadingNow;
        boolean unsafe = false;
        if (g.kind == GunRecord.KIND_BIG && assembled) {
            BoreCheck.Verdict v = g.bore;
            if (v == null || v.reason().outcome() == BoreCheck.Outcome.UNSAFE) unsafe = true;
            else if (v.reason() == BoreCheck.Reason.BREECH_OPEN) loading = true;
            else if (!v.fires()) empty = true;
            else if (!g.watch.settled(now)) loading = true;
        }
        if (g.dryAttempts >= DRY_LIMIT) empty = true;

        if (!speedKnown && g.kind != GunRecord.KIND_UNKNOWN) empty = true;
        boolean salvoReadyNow = false;
        boolean mayFire = engaged && speedKnown && solution == 0 && onTarget && !blocked && !empty && !loading && !unsafe
                && driveOk;
        GunState.Inputs in = new GunState.Inputs(true, assembled, g.mode, true, fixed, g.handAimed, yawDrive, pitchDrive,
                yawTurning, pitchTurning, yawHeld || pitchHeld, over, fault, unsafe, t != null && t.lostSince < 0, solution,
                onTarget, blocked, empty, loading, r.weaponsFree, g.signalHeld || now - g.lastShotAt < 20);
        GunState before = GunState.of(in);
        if (salvo != null && g.mode != GunState.MODE_HOLD) {
            if (before.salvoReady() && mayFire) {
                salvoReadyNow = true;
                salvo.fired(g.label());
                if (g.kind == GunRecord.KIND_AUTO) g.salvoUntil = now + SALVO_HOLD;
            } else {
                salvo.notReady(g.label(), before, before == GunState.SLEWING ? (int) Math.ceil(g.argA) : -1);
            }
        }

        boolean automatic = g.mode == GunState.MODE_FIRE && r.weaponsFree && t != null && t.kind != TargetFinder.KIND_PLAYER;
        boolean active = g.commanding;
        if (g.kind == GunRecord.KIND_AUTO) {
            boolean want = mayFire && (automatic || now < g.salvoUntil);
            if (want) {
                CreateBigCannonsReflectionAdapter.setMountedFireSignal(be, true, 15);
                if (!g.signalHeld) {
                    g.signalHeld = true;
                    r.firing.add(g.id);
                    r.setDirty();
                }
                active = true;
            } else {
                g.salvoUntil = 0;
                clearSignal(r, level, g);
            }
        } else if (g.kind == GunRecord.KIND_BIG && mayFire && (automatic || salvoReadyNow) && now - g.lastAttemptAt >= BIG_RETRY) {
            g.lastAttemptAt = now;
            if (CreateBigCannonsReflectionAdapter.tryFireMountedCannon(be, level)) {
                g.lastShotAt = now;
                g.dryAttempts = 0;
                g.loadCheckedAt = Long.MIN_VALUE / 2;
                g.watch.reset();
            } else {
                g.dryAttempts++;
            }
            active = true;
        }
        in = new GunState.Inputs(true, assembled, g.mode, true, fixed, g.handAimed, yawDrive, pitchDrive, yawTurning, pitchTurning,
                yawHeld || pitchHeld, over, fault, unsafe, t != null && t.lostSince < 0, solution, onTarget, blocked, empty,
                loading, r.weaponsFree, g.signalHeld || now - g.lastShotAt < 20);
        g.state = GunState.of(in);
        sentence(level, g, yawGov, pitchGov, yawTurning, yawHeld, t, now);
        return active;
    }

    private static void sentence(ServerLevel level, GunRecord g, @Nullable RpmGovernorBE yawGov,
                                 @Nullable RpmGovernorBE pitchGov, boolean yawTurning, boolean yawHeld,
                                 @Nullable TargetFinder.Target t, long now) {
        switch (g.state) {
            case NO_YAW_DRIVE -> {
                g.axis = GunState.AXIS_YAW;
                g.argA = g.shared ? 2 : g.yawConflict ? 1 : 0;
                g.argB = g.mountType == GunRecord.MOUNT_HANGING ? 1 : 0;
            }
            case NO_PITCH_DRIVE -> {
                g.axis = GunState.AXIS_PITCH;
                g.argA = g.shared ? 2 : g.pitchConflict ? 1 : 0;
            }
            case NO_ROTATION -> g.axis = yawTurning ? GunState.AXIS_PITCH : GunState.AXIS_YAW;
            case HELD_ELSEWHERE -> {
                g.axis = yawHeld ? GunState.AXIS_YAW : GunState.AXIS_PITCH;
                RpmGovernorBE gov = yawHeld ? yawGov : pitchGov;
                g.argS = holder(level, gov);
            }
            case OVERSTRESSED -> g.axis = yawGov != null && yawGov.isOverStressed() ? GunState.AXIS_YAW : GunState.AXIS_PITCH;
            case AIM_FAULT -> g.axis = g.yaw != null && g.yaw.faulted(now) ? GunState.AXIS_YAW : GunState.AXIS_PITCH;
            case EMPTY -> g.argA = g.dryAttempts >= DRY_LIMIT ? GunState.EMPTY_DRY
                    : g.kind == GunRecord.KIND_AUTO ? GunState.EMPTY_AUTO : emptyWhy(g.bore);
            case LOADING -> g.argA = loadingWhy(g);
            case UNSAFE -> unsafeWhy(g);
            default -> {
            }
        }
        if (t != null && (g.state == GunState.OUT_OF_RANGE || g.state == GunState.OUT_OF_ARC || g.state == GunState.BLOCKED)) {
            if (g.state != GunState.BLOCKED) g.argS = t.name;
        }
    }

    private static int emptyWhy(@Nullable BoreCheck.Verdict v) {
        if (v == null) return GunState.EMPTY_BIG;
        return switch (v.reason()) {
            case NO_CHARGE -> GunState.EMPTY_NO_CHARGE;
            case NO_SHELL -> GunState.EMPTY_NO_SHELL;
            case DEAD_CHARGE -> GunState.EMPTY_DEAD_CHARGE;
            case STUCK_SHELL -> GunState.EMPTY_STUCK_SHELL;
            case DROP_MORTAR -> GunState.EMPTY_DROP_MORTAR;
            default -> GunState.EMPTY_BIG;
        };
    }

    private static int loadingWhy(GunRecord g) {
        BoreCheck.Verdict v = g.kind == GunRecord.KIND_BIG ? g.bore : null;
        if (v != null && v.reason() == BoreCheck.Reason.BREECH_OPEN) return GunState.LOADING_BREECH;
        if (!g.loadingNow && v != null && v.fires()) return GunState.LOADING_SETTLE;
        return GunState.LOADING_STROKE;
    }

    private static void unsafeWhy(GunRecord g) {
        BoreCheck.Verdict v = g.bore;
        g.argA = v == null ? GunState.UNSAFE_UNREADABLE : switch (v.reason()) {
            case OVERLOAD -> GunState.UNSAFE_OVERLOAD;
            case CHARGE_IN_BARREL -> GunState.UNSAFE_BARREL;
            case CHARGE_ORDER -> GunState.UNSAFE_ORDER;
            case OBSTRUCTED -> GunState.UNSAFE_OBSTRUCTED;
            case BAD_SHELL -> GunState.UNSAFE_SHELL;
            case UNDERCHARGED -> GunState.UNSAFE_UNDERCHARGED;
            default -> GunState.UNSAFE_UNREADABLE;
        };
        if (v == null) return;
        g.argB = (float) Math.round(v.burst() * 100);
        if (v.reason() == BoreCheck.Reason.OVERLOAD) g.argS = figure(v.stress()) + "/" + v.maxSafe();
    }

    private static String figure(float x) {
        return x == Math.rint(x) ? String.valueOf((int) x) : String.format(java.util.Locale.ROOT, "%.1f", x);
    }

    static final String UNSURVEYED = "?survey";

    static final String TOO_FAR = "?far";

    @Nullable
    private static String lineOfFire(ShipRecord r, GunRecord g, Trajectory shell, double[] muzzleW, double[] v0vec,
                                     @Nullable Object ownBody, TargetFinder.Target t, Crowd crowd) {
        int[] bounds = r.refBounds();
        if (bounds == null) return UNSURVEYED;
        double[] shipVel = muzzleVelocity(g);
        int[] skipMin = null;
        int[] skipMax = null;
        if (g.body == null) {
            BlockPos m = BlockPos.of(g.mount);
            skipMin = new int[]{m.getX(), m.getY(), m.getZ()};
            skipMax = skipMin;
        }
        int[] hit = LineOfFire.ownShip(shell, muzzleW, v0vec, shipVel,
                (w, out) -> {
                    double[] l = Frames.toLocal(ownBody, w);
                    out[0] = l[0];
                    out[1] = l[1];
                    out[2] = l[2];
                },
                (x, y, z) -> r.intactAt(x, y, z),
                new int[]{bounds[0], bounds[1], bounds[2]}, new int[]{bounds[3], bounds[4], bounds[5]}, skipMin, skipMax);
        if (hit == LineOfFire.UNSURE) return TOO_FAR;
        if (hit != null) {
            int packed = r.pack(new BlockPos(hit[0], hit[1], hit[2]));
            return packed < 0 ? "" : r.namer().geometry(packed);
        }

        List<Player> players = crowd.near(muzzleW);
        AABB aboard = crowd.aboard();
        double[] relVec = {v0vec[0] - shipVel[0], v0vec[1] - shipVel[1], v0vec[2] - shipVel[2]};
        for (boolean crew : new boolean[]{true, false}) {
            List<double[]> boxes = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Player p : players) {
                if (t.kind == TargetFinder.KIND_PLAYER && p.getUUID().equals(t.id)) continue;
                boolean onBoard = aboard != null && aboard.contains(p.position());
                if (onBoard != crew) continue;
                AABB b = p.getBoundingBox().inflate(PLAYER_GROW);
                boxes.add(new double[]{b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ});
                names.add(p.getGameProfile().getName());
            }
            if (boxes.isEmpty()) continue;
            int i = LineOfFire.firstBox(shell, muzzleW, crew ? relVec : v0vec, PLAYER_PATH, boxes.toArray(new double[0][]));
            if (i >= 0) return "p:" + names.get(i);
        }
        return null;
    }

    static final class Crowd {
        private final ServerLevel level;
        @Nullable
        private final AABB bounds;
        private final boolean ship;
        @Nullable
        private List<Player> all;

        Crowd(ServerLevel level, @Nullable AABB bounds, boolean ship) {
            this.level = level;
            this.bounds = bounds;
            this.ship = ship;
        }

        @Nullable
        AABB aboard() {
            return ship && bounds != null ? bounds.inflate(1.5) : null;
        }

        List<Player> near(double[] muzzle) {
            AABB box = new AABB(muzzle[0], muzzle[1], muzzle[2], muzzle[0], muzzle[1], muzzle[2]).inflate(PLAYER_RANGE);
            if (bounds == null) return level.getEntitiesOfClass(Player.class, box, p -> !p.isSpectator());

            if (all == null) all = level.getEntitiesOfClass(Player.class, bounds.inflate(PLAYER_RANGE + 8), p -> !p.isSpectator());
            List<Player> out = new ArrayList<>();
            for (Player p : all) if (box.intersects(p.getBoundingBox())) out.add(p);
            return out;
        }
    }

    private static void readLoad(GunRecord g, BlockEntity mount, ServerLevel level, long now) {
        g.loadCheckedAt = now;
        List<CreateBigCannonsReflectionAdapter.VirtualBlockInspection> cells =
                CreateBigCannonsReflectionAdapter.inspectMountedCannon(mount, mount.getBlockPos().asLong());
        int projectiles = 0;
        int propellant = 0;
        int loadingBlocks = 0;
        int barrels = 0;
        int rounds = 0;
        boolean creative = false;
        boolean auto = false;
        boolean big = false;
        CreateBigCannonsReflectionAdapter.AutocannonBallisticsInspection ballistics =
                CreateBigCannonsReflectionAdapter.AutocannonBallisticsInspection.none();
        for (CreateBigCannonsReflectionAdapter.VirtualBlockInspection cell : cells) {
            CreateBigCannonsCompat.WeaponPart part = CreateBigCannonsCompat.classifyPart(cell.state());
            CreateBigCannonsCompat.WeaponKind kind = CreateBigCannonsCompat.weaponKind(cell.state());
            if (kind == CreateBigCannonsCompat.WeaponKind.AUTOCANNON) auto = true;
            else if (kind == CreateBigCannonsCompat.WeaponKind.BIG_CANNON) big = true;
            if (part == CreateBigCannonsCompat.WeaponPart.BARREL) barrels++;
            if (part == CreateBigCannonsCompat.WeaponPart.PROJECTILE) projectiles++;
            if (part == CreateBigCannonsCompat.WeaponPart.PROPELLANT) propellant++;
            CreateBigCannonsReflectionAdapter.LoadInspection li = CreateBigCannonsReflectionAdapter.inspect(cell.blockEntity(), cell.state());
            projectiles += li.projectileBlocks();
            propellant += li.propellantBlocks();
            loadingBlocks += li.loadingBlocks();
            if (part == CreateBigCannonsCompat.WeaponPart.AMMO_CONTAINER && cell.blockEntity() != null) {
                CreateBigCannonsReflectionAdapter.AmmoContainerInspection a = CreateBigCannonsReflectionAdapter.inspectAmmoContainer(cell.blockEntity());
                rounds += a.totalRounds();
                creative |= a.creative();
            }
            BreechLoad breech = part == CreateBigCannonsCompat.WeaponPart.BREECH ? breechLoad(cell.blockEntity()) : null;
            if (breech != null) {
                rounds += breech.rounds();
                creative |= breech.creative() && breech.rounds() > 0;
            }
            if (!ballistics.available()) ballistics = CreateBigCannonsReflectionAdapter.inspectAutocannonBallistics(cell.state(), 0);
        }
        double oldV0 = g.v0;
        if (auto) {
            g.kind = GunRecord.KIND_AUTO;
            g.load = creative ? 9999 : rounds;
            g.loadingNow = false;
            if (ballistics.available()) {
                int inc = Math.min(Math.max(0, barrels), ballistics.maxSpeedIncreases());
                g.v0 = ballistics.baseSpeed() + ballistics.speedIncreasePerBarrel() * inc;
            }
        } else if (big) {
            g.kind = GunRecord.KIND_BIG;
            readBore(g, mount, level, now);
            if (g.bore == null) {
                g.load = projectiles > 0 && propellant > 0 ? 1 : 0;
                float power = CreateBigCannonsReflectionAdapter.resolveBigCannonChargePower(mount);
                g.v0 = power > 0 ? power : g.v0;
            }
            g.loadingNow = loadingBlocks > 0 && g.load == 0;
        } else {
            g.kind = GunRecord.KIND_UNKNOWN;
            g.load = -1;
        }
        if (g.v0 != oldV0 || g.maxRange <= 0) rangeFor(g, mount, level);
    }

    private static void readBore(GunRecord g, BlockEntity mount, ServerLevel level, long now) {
        double oldV0 = g.v0;
        BoreCheck.Bore bore = CreateBigCannonsReflectionAdapter.readBore(mount);
        if (bore == null) {
            if (!boreWarned) {
                boreWarned = true;
                CreateStats.LOGGER.warn("[Battle Bridge] cannot read the bore of G{}; big cannons hold fire", g.id);
            }
            g.bore = null;
            g.watch.reset();
            return;
        }
        BoreCheck.Verdict v = BoreCheck.judge(bore);
        g.bore = v;
        if (g.watch.observe(bore, v, now)) g.dryAttempts = 0;
        boolean whole = v.fires() || v.reason().outcome() == BoreCheck.Outcome.UNSAFE
                || v.reason() == BoreCheck.Reason.BREECH_OPEN && BoreCheck.loaded(bore);
        g.load = whole ? 1 : 0;
        if (v.speed() > 0.05f) g.v0 = v.speed();
        if (g.v0 != oldV0 || g.maxRange <= 0) rangeFor(g, mount, level);
    }

    private static void rangeFor(GunRecord g, BlockEntity mount, ServerLevel level) {
        double[] lim = pitchLimits(mount, g);
        g.maxRange = g.v0 > 0.05 ? FireSolution.maxRange(shell(g, level), g.v0, -lim[0], lim[1]) : 0;
    }

    private record BreechLoad(int rounds, boolean creative) {
    }

    // buffer then magazine item, like cbc's extractNextInput. counting container blocks read every autocannon empty
    @Nullable
    private static BreechLoad breechLoad(@Nullable BlockEntity be) {
        if (be == null) return null;
        try {
            Method bufferMethod = be.getClass().getMethod("getInputBuffer");
            Method magazineMethod = be.getClass().getMethod("getMagazine");
            int rounds = 0;
            if (bufferMethod.invoke(be) instanceof Iterable<?> buffer) {
                for (Object o : buffer) {
                    if (o instanceof ItemStack s && !s.isEmpty()) rounds += s.getCount();
                }
            }
            boolean creative = false;
            if (magazineMethod.invoke(be) instanceof ItemStack magazine && !magazine.isEmpty()) {
                Object item = magazine.getItem();
                Object total = item.getClass().getMethod("getTotalAmmoCount", ItemStack.class).invoke(null, magazine);
                if (total instanceof Number n) rounds += Math.max(0, n.intValue());
                creative = item.getClass().getMethod("isCreative").invoke(item) instanceof Boolean b && b;
            }
            return new BreechLoad(rounds, creative);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static Trajectory shell(GunRecord g, Level level) {
        double gravity = g.kind == GunRecord.KIND_AUTO ? G_AUTO : G_BIG;
        double drag = K_STOCK;
        if (Level.NETHER.equals(level.dimension())) {
            gravity *= 1.1;
            drag *= 1.1;
        } else if (Level.END.equals(level.dimension())) {
            gravity *= 0.8;
            drag = 0;
        }
        return new Trajectory(gravity, drag, false);
    }

    private static double[] pitchLimits(BlockEntity mount, GunRecord g) {
        try {
            Method c = mount.getClass().getMethod("getContraption");
            Object entity = c.invoke(mount);
            if (entity != null) {
                Object dep = entity.getClass().getMethod("maximumDepression").invoke(entity);
                Object elev = entity.getClass().getMethod("maximumElevation").invoke(entity);
                if (dep instanceof Number d && elev instanceof Number e) {
                    g.minPitch = d.doubleValue();
                    g.maxPitch = e.doubleValue();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return new double[]{g.minPitch, g.maxPitch};
    }

    private static double slewSeconds(GunRecord g, double alphaDeg) {
        double max = g.kind == GunRecord.KIND_AUTO ? GunDrive.RATE_AUTOCANNON : GunDrive.RATE_BIG_CANNON;
        double rate = 0;
        if (g.yaw != null) rate = Math.max(rate, GunDrive.fullRate(g.yaw, g.yawLaw, max));
        if (rate <= 1e-6) rate = GunDrive.DEG_PER_RPM_TICK * GunDrive.PROBE_RPM;
        return Math.max(1, Math.ceil(alphaDeg / rate / 20.0));
    }

    private static FireSolution.Answer straightAt(double[] muzzle, double[] target) {
        double dx = target[0] - muzzle[0];
        double dy = target[1] - muzzle[1];
        double dz = target[2] - muzzle[2];
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new FireSolution.Answer(FireSolution.Kind.OK, FireSolution.yawOf(dx, dz), FireSolution.pitchOf(dx, dy, dz), 0,
                target.clone(), range, 0, 0);
    }

    private static boolean mountCanTurn(BlockEntity mount) {
        try {
            Object entity = mount.getClass().getMethod("getContraption").invoke(mount);
            if (entity == null) return true;
            for (Method m : entity.getClass().getMethods()) {
                if (m.getName().equals("canBeTurnedByController") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(mount)) {
                    return !(m.invoke(entity, mount) instanceof Boolean b) || b;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return true;
    }

    private static double pitchLaw(BlockEntity mount, double last) {
        try {
            if (mount.getClass().getMethod("getContraptionDirection").invoke(mount) instanceof net.minecraft.core.Direction d) {
                return GunDrive.pitchLaw(d.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE,
                        d.getAxis() == net.minecraft.core.Direction.Axis.X);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return last;
    }

    @Nullable
    private static RpmGovernorBE governor(ServerLevel level, long key) {
        if (key == GunGraph.NONE) return null;
        return SableBridge.getBlockEntityOnContraption(level, BlockPos.of(key)) instanceof RpmGovernorBE g ? g : null;
    }

    private static boolean turning(RpmGovernorBE g) {
        return g.hasSource() && Math.abs(g.getTheoreticalSpeed()) > 0.01f;
    }

    private static boolean heldElsewhere(ShipRecord r, @Nullable RpmGovernorBE g) {
        if (g == null) return false;
        UUID owner = g.getCommanderUuid();
        return owner != null && !owner.equals(r.controlId) && !g.isFailSafeActive();
    }

    private static String holder(ServerLevel level, @Nullable RpmGovernorBE g) {
        if (g == null || g.getCommanderPos() == null) return "";
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, g.getCommanderPos());
        return be != null && be.getClass().getName().contains("vccmk4") ? "vcc" : "";
    }

    private static boolean command(ShipRecord r, RpmGovernorBE g, BlockPos bridgePos, int rpm) {
        if (g.getCommandedRpm() == rpm && r.controlId.equals(g.getCommanderUuid())) return g.heartbeat(bridgePos, r.controlId);
        return g.setCommand(bridgePos, r.controlId, rpm);
    }

    private static void heartbeat(ShipRecord r, @Nullable RpmGovernorBE g, BlockPos bridgePos, boolean leads) {
        if (g != null && leads) g.heartbeat(bridgePos, r.controlId);
    }

    static void letGo(ShipRecord r, ServerLevel level, GunRecord g) {
        letGoGovernors(r, level, g);
        g.salvoUntil = 0;
        clearSignal(r, level, g);
    }

    private static void letGoGovernors(ShipRecord r, ServerLevel level, GunRecord g) {
        RpmGovernorBE y = governor(level, g.yawGov);
        RpmGovernorBE p = governor(level, g.pitchGov);
        if (y != null) y.releaseOwnership(r.controlId);
        if (p != null) p.releaseOwnership(r.controlId);
        g.commanding = false;
        g.yawRpm = 0;
        g.pitchRpm = 0;
        if (g.yaw != null) g.yaw.reset();
        if (g.pitch != null) g.pitch.reset();
    }

    static void letGoAll(ShipRecord r, ServerLevel level) {
        for (GunRecord g : r.guns) letGo(r, level, g);
    }

    static void clearSignal(ShipRecord r, ServerLevel level, GunRecord g) {
        if (!g.signalHeld && !r.firing.contains(g.id)) return;
        BlockPos pos = BlockPos.of(g.mount);
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (GunFinder.isAnyMount(be)) {
            CreateBigCannonsReflectionAdapter.setMountedFireSignal(be, false, 0);
            be.getLevel().scheduleTick(be.getBlockPos(), be.getBlockState().getBlock(), 1);
        }
        g.signalHeld = false;
        r.firing.remove(g.id);
        r.setDirty();
    }

    private static void clearRecorded(ShipRecord r, ServerLevel level) {
        if (r.firing.isEmpty()) return;
        for (GunRecord g : r.guns) {
            if (r.firing.contains(g.id)) {
                g.signalHeld = true;
                clearSignal(r, level, g);
            }
        }
        r.firing.clear();
        r.setDirty();
    }

    private static void recordMuzzle(GunRecord g, double[] w, long now) {
        int n = g.muzzleTicks.length;
        System.arraycopy(g.muzzleHistory, 0, g.muzzleHistory, 3, (n - 1) * 3);
        System.arraycopy(g.muzzleTicks, 0, g.muzzleTicks, 1, n - 1);
        g.muzzleHistory[0] = w[0];
        g.muzzleHistory[1] = w[1];
        g.muzzleHistory[2] = w[2];
        g.muzzleTicks[0] = now;
        g.muzzleCount = Math.min(n, g.muzzleCount + 1);
    }

    private static double[] muzzleVelocity(GunRecord g) {
        int last = Math.min(4, g.muzzleCount - 1);
        if (last <= 0) return new double[3];
        double dt = g.muzzleTicks[0] - g.muzzleTicks[last];
        if (dt <= 0 || dt > 10) return new double[3];
        return new double[]{(g.muzzleHistory[0] - g.muzzleHistory[last * 3]) / dt,
                (g.muzzleHistory[1] - g.muzzleHistory[last * 3 + 1]) / dt,
                (g.muzzleHistory[2] - g.muzzleHistory[last * 3 + 2]) / dt};
    }

    private static void recordBridge(ShipRecord r, Vec3 w, long now) {
        if (r.bridgeAt != Long.MIN_VALUE && now > r.bridgeAt && now - r.bridgeAt <= 10) {
            double dt = now - r.bridgeAt;
            double[] v = {(w.x - r.bridgeLast[0]) / dt, (w.y - r.bridgeLast[1]) / dt, (w.z - r.bridgeLast[2]) / dt};
            for (int i = 0; i < 3; i++) r.bridgeVel[i] = 0.65 * v[i] + 0.35 * r.bridgeVel[i];
        }
        r.bridgeLast[0] = w.x;
        r.bridgeLast[1] = w.y;
        r.bridgeLast[2] = w.z;
        r.bridgeAt = now;
    }

    static MutableComponent describe(SalvoResult result) {
        if (result.fired().isEmpty() && result.notReady().isEmpty()) return BattleText.tr("salvo.none");
        MutableComponent reasons = Component.empty();
        for (int i = 0; i < result.notReady().size(); i++) {
            SalvoResult.NotReady n = result.notReady().get(i);
            if (i > 0) reasons.append(", ");
            MutableComponent word = BattleText.tr("gun.word." + n.state().key());
            reasons.append(n.seconds() >= 0 ? BattleText.tr("salvo.item_eta", n.gun(), word, n.seconds())
                    : BattleText.tr("salvo.item", n.gun(), word));
        }
        if (result.fired().isEmpty()) return BattleText.tr("salvo.nothing", reasons);
        MutableComponent head = BattleText.tr("salvo.fired", String.join(", ", result.fired()));
        return result.notReady().isEmpty() ? head : head.append(BattleText.tr("salvo.not_ready", reasons));
    }
}
