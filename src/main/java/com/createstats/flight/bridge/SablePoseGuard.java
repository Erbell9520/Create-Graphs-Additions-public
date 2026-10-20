package com.createstats.flight.bridge;

import com.createstats.CreateStats;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// sable's rust side panics on a missing body id and the panic kills the whole jvm, no crash report. guard it. sable tax #11
public final class SablePoseGuard {
    private static final Set<Integer> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile Method runtimeId;
    private static volatile Method logicalPose;
    private static volatile Method posePosition;
    private static volatile Method poseOrientation;

    private SablePoseGuard() {
    }

    public static int runtimeId(Object body) {
        try {
            Method m = runtimeId;
            if (m == null) {
                m = body.getClass().getMethod("getRuntimeId");
                m.trySetAccessible();
                runtimeId = m;
            }
            return (int) m.invoke(body);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static boolean answerFromLogicalPose(Object subLevel, Object dest, String where) {
        try {
            Method lp = logicalPose;
            if (lp == null) {
                lp = subLevel.getClass().getMethod("logicalPose");
                lp.trySetAccessible();
                logicalPose = lp;
            }
            Object pose = lp.invoke(subLevel);
            if (pose == null || dest == null) return false;
            Method pos = posePosition;
            Method ori = poseOrientation;
            if (pos == null || ori == null) {
                pos = pose.getClass().getMethod("position");
                ori = pose.getClass().getMethod("orientation");
                pos.trySetAccessible();
                ori.trySetAccessible();
                posePosition = pos;
                poseOrientation = ori;
            }
            Object fromPos = pos.invoke(pose);
            Object fromOri = ori.invoke(pose);
            Object toPos = pos.invoke(dest);
            Object toOri = ori.invoke(dest);
            if (!(fromPos instanceof Vector3dc fp) || !(fromOri instanceof Quaterniondc fo)
                    || !(toPos instanceof Vector3d tp) || !(toOri instanceof Quaterniond to)) {
                return false;
            }
            tp.set(fp);
            to.set(fo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        report(subLevel, where);
        return true;
    }

    public static void report(Object subLevel, String where) {
        int id = runtimeId(subLevel);
        if (!LOGGED.add(id)) return;
        CreateStats.LOGGER.warn("[Sable guard] {} asked for body {} ({}), which the physics engine no longer holds; "
                + "answered from its last pose instead of letting Rapier abort the game. Caller:", where, id, subLevel,
                new Throwable("stack of the skipped call"));
    }
}
