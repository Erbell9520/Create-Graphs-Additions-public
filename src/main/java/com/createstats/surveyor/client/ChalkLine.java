package com.createstats.surveyor.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

public final class ChalkLine {
    static final double CAST_SECONDS = 0.25;
    static final double REWIND_SECONDS = 0.45;

    static final long STALE_NANOS = 250_000_000L;

    static final int COLOUR = 0xF03A72D6;
    static final int HOOK_TOP = 0xFFE9C46E;
    static final int HOOK_SIDE = 0xFF9C6A26;

    private enum Phase {
        IDLE, CAST, REWIND
    }

    private static Phase phase = Phase.IDLE;
    @Nullable private static Vec3 anchor;
    @Nullable private static Vec3 rewindFrom;
    private static long phaseAt;
    private static double paidOut;

    private static boolean handView;
    private static float ndcX;
    private static float ndcY;
    private static float eyeZ;
    private static double worldX;
    private static double worldY;
    private static double worldZ;
    private static long nozzleAt = Long.MIN_VALUE;

    private ChalkLine() {
    }

    static void handNozzle(float x, float y, float depth, long now) {
        handView = true;
        ndcX = x;
        ndcY = y;
        eyeZ = depth;
        nozzleAt = now;
    }

    static void worldNozzle(double x, double y, double z, long now) {
        handView = false;
        worldX = x;
        worldY = y;
        worldZ = z;
        nozzleAt = now;
    }

    static boolean out() {
        return phase != Phase.IDLE;
    }

    static double paidOut() {
        return phase == Phase.IDLE ? 0.0 : paidOut;
    }

    static void draw(VertexConsumer vc, Matrix4f m, Camera camera, Matrix4f projection) {
        final long now = Util.getNanos();
        final Vec3 from = StaffInput.paintingFrom();
        final Vec3 nozzle = nozzle(camera, projection, now);
        switch (phase) {
            case IDLE -> {
                if (from != null) cast(from, now);
            }
            case CAST -> {
                if (from == null) {
                    final Vec3 far = nozzle == null ? anchor : far(nozzle, now);
                    phase = Phase.REWIND;
                    rewindFrom = far;
                    phaseAt = now;
                } else if (!from.equals(anchor)) {
                    cast(from, now);
                }
            }
            case REWIND -> {
                if (from != null) {
                    cast(from, now);
                } else if ((now - phaseAt) / 1.0e9 >= REWIND_SECONDS) {
                    phase = Phase.IDLE;
                    anchor = null;
                    rewindFrom = null;
                }
            }
        }
        if (phase == Phase.IDLE || nozzle == null) return;
        final Vec3 far = far(nozzle, now);
        paidOut = nozzle.distanceTo(far);
        line(vc, m, camera.getPosition(), nozzle, far);

        PathwayRenderer.box(vc, m, far.x - 0.035, far.y, far.z - 0.035, far.x + 0.035, far.y + 0.05, far.z + 0.035,
                HOOK_TOP, HOOK_SIDE);
    }

    private static void cast(Vec3 from, long now) {
        phase = Phase.CAST;
        anchor = from;
        rewindFrom = null;
        phaseAt = now;
    }

    private static Vec3 far(Vec3 nozzle, long now) {
        final double s = (now - phaseAt) / 1.0e9;
        if (phase == Phase.REWIND && rewindFrom != null) {
            return rewindFrom.lerp(nozzle, smooth(s / REWIND_SECONDS));
        }
        if (anchor == null) return nozzle;
        final double t = Math.min(1.0, Math.max(0.0, s / CAST_SECONDS));
        final double out = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
        return nozzle.lerp(anchor, out);
    }

    private static double smooth(double t) {
        final double u = Math.min(1.0, Math.max(0.0, t));
        return u * u * (3.0 - 2.0 * u);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b) {
        final double length = a.distanceTo(b);
        if (length < 1.0e-3) return;
        final int segs = Math.max(2, Math.min(24, (int) Math.ceil(length / 1.5)));
        final double sag = Math.min(0.25, length * 0.015);
        Vec3 prev = a;
        for (int s = 1; s <= segs; s++) {
            final double t = s / (double) segs;
            final Vec3 p = a.lerp(b, t).add(0.0, -Math.sin(Math.PI * t) * sag, 0.0);
            final double d = prev.add(p).scale(0.5).distanceTo(cam);
            PathwayRenderer.beam(vc, m, prev, p, (float) Math.max(0.006, 0.0015 * d), COLOUR);
            prev = p;
        }
    }

    @Nullable
    private static Vec3 nozzle(Camera camera, Matrix4f projection, long now) {
        if (nozzleAt == Long.MIN_VALUE || now - nozzleAt > STALE_NANOS) return null;
        if (handView != Minecraft.getInstance().options.getCameraType().isFirstPerson()) return null;
        if (!handView) return new Vec3(worldX, worldY, worldZ);

        final Matrix4f inverse = new Matrix4f(projection).invert();
        final Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1f, 1f));
        final Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1f, 1f));
        if (Math.abs(near.w) < 1e-9f || Math.abs(far.w) < 1e-9f) return null;
        near.div(near.w);
        far.div(far.w);
        final float dz = far.z - near.z;
        if (Math.abs(dz) < 1e-9f) return null;
        final float t = (eyeZ - near.z) / dz;
        final Vector3f view = new Vector3f(near.x + t * (far.x - near.x), near.y + t * (far.y - near.y), eyeZ);
        camera.rotation().transform(view);
        return camera.getPosition().add(view.x, view.y, view.z);
    }
}
