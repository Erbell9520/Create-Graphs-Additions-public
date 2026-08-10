package com.createstats.display;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class VehicleForwardAuthority {
    private static final double MIN_HORIZONTAL_SQ = 1.0E-8d;

    private VehicleForwardAuthority() {
    }

    public static Resolution resolve(Vector3d upAxis, @Nullable Vector3d bodyForward, @Nullable Vector3d velocityAxis, @Nullable Vector3d motionVector,
                                     @Nullable Vector3d cachedForward, @Nullable StatsDisplaySettings.NoseSetting noseSetting) {
        final Vector3d up = normalizeUp(upAxis);
        final StatsDisplaySettings.NoseSetting nose = (noseSetting != null) ? noseSetting : StatsDisplaySettings.NoseSetting.UNSET;
        final Vector3d bodyHorizontal = normalizeHorizontal(bodyForward, up);
        final Vector3d velocityHorizontalRaw = normalizeHorizontal(velocityAxis, up);
        if (bodyHorizontal != null && nose != StatsDisplaySettings.NoseSetting.UNSET) {
            final Vector3d explicit = rotateForNose(bodyHorizontal, up, nose);
            return new Resolution(explicit, yawFromForward(explicit), ForwardSource.EXPLICIT_NOSE, 100.0f, true, velocityHorizontalRaw != null);
        }
        if (velocityHorizontalRaw != null) {
            final Vector3d velocityHorizontal = rotateForNose(velocityHorizontalRaw, up, nose);
            return new Resolution(velocityHorizontal, yawFromForward(velocityHorizontal), ForwardSource.VELOCITY_AXIS, 76.0f, nose != StatsDisplaySettings.NoseSetting.UNSET, true);
        }
        if (bodyHorizontal != null) {
            return new Resolution(bodyHorizontal, yawFromForward(bodyHorizontal), ForwardSource.BODY_Z, 92.0f, false, false);
        }
        final Vector3d cachedHorizontal = normalizeHorizontal(cachedForward, up);
        final Vector3d motionHorizontal = normalizeHorizontal(motionVector, up);
        if (motionHorizontal != null) {
            if (cachedHorizontal != null && motionHorizontal.dot(cachedHorizontal) < -0.35d) {
                return new Resolution(cachedHorizontal, yawFromForward(cachedHorizontal), ForwardSource.CACHED_LAST, 45.0f, false, false);
            }
            return new Resolution(motionHorizontal, yawFromForward(motionHorizontal), ForwardSource.MOTION_VECTOR, 62.0f, false, false);
        }
        if (cachedHorizontal != null) {
            return new Resolution(cachedHorizontal, yawFromForward(cachedHorizontal), ForwardSource.CACHED_LAST, 45.0f, false, false);
        }
        return new Resolution(null, 0.0f, ForwardSource.NONE, 0.0f, false, false);
    }

    public static Vector3d normalizeUp(@Nullable Vector3d upAxis) {
        if (upAxis != null && upAxis.lengthSquared() > MIN_HORIZONTAL_SQ) {
            return new Vector3d(upAxis).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    @Nullable
    public static Vector3d normalizeHorizontal(@Nullable Vector3d vector, Vector3d upAxis) {
        if (vector == null) {
            return null;
        }
        final Vector3d projected = new Vector3d(vector).sub(new Vector3d(upAxis).mul(vector.dot(upAxis)));
        if (projected.lengthSquared() <= MIN_HORIZONTAL_SQ) {
            return null;
        }
        return projected.normalize();
    }

    public static float yawFromForward(Vector3d forward) {
        return Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(forward.z, forward.x)));
    }

    public static PoseAxes applyNoseToPoseAxes(Vector3d forward, Vector3d right, StatsDisplaySettings.NoseSetting noseSetting) {
        final StatsDisplaySettings.NoseSetting nose = (noseSetting != null) ? noseSetting : StatsDisplaySettings.NoseSetting.UNSET;
        final Vector3d normalizedForward = new Vector3d(forward).normalize();
        final Vector3d normalizedRight = new Vector3d(right).normalize();
        return switch (nose) {
            case RIGHT -> new PoseAxes(normalizedRight, new Vector3d(normalizedForward).negate());
            case BEHIND -> new PoseAxes(new Vector3d(normalizedForward).negate(), new Vector3d(normalizedRight).negate());
            case LEFT -> new PoseAxes(new Vector3d(normalizedRight).negate(), normalizedForward);
            default -> new PoseAxes(normalizedForward, normalizedRight);
        };
    }

    public static Vector3d rotateForNose(Vector3d forward, Vector3d upAxis, StatsDisplaySettings.NoseSetting noseSetting) {
        final StatsDisplaySettings.NoseSetting nose = (noseSetting != null) ? noseSetting : StatsDisplaySettings.NoseSetting.UNSET;
        if (nose == StatsDisplaySettings.NoseSetting.UNSET || nose == StatsDisplaySettings.NoseSetting.AHEAD) {
            return new Vector3d(forward).normalize();
        }
        final Vector3d normalizedForward = new Vector3d(forward).normalize();
        final Vector3d right = new Vector3d(upAxis).cross(normalizedForward);
        if (right.lengthSquared() <= MIN_HORIZONTAL_SQ) {
            return normalizedForward;
        }
        right.normalize();
        return switch (nose) {
            case RIGHT -> right;
            case BEHIND -> normalizedForward.negate();
            case LEFT -> right.negate();
            default -> normalizedForward;
        };
    }

    public record Resolution(@Nullable Vector3d forward, float yawDegrees, ForwardSource source, float confidence, boolean explicitNoseApplied,
                             boolean velocityAssistAvailable) {
        public boolean hasForward() {
            return this.forward != null && this.forward.lengthSquared() > MIN_HORIZONTAL_SQ;
        }

        public float forwardX() {
            return this.hasForward() ? (float)this.forward.x : Float.NaN;
        }

        public float forwardZ() {
            return this.hasForward() ? (float)this.forward.z : Float.NaN;
        }

        public String sourceLabel() {
            return this.source.label;
        }

        public String sourceTag() {
            return this.source.tag;
        }
    }

    public record PoseAxes(Vector3d forward, Vector3d right) {
    }

    public enum ForwardSource {
        EXPLICIT_NOSE("NOSE", "Explicit nose"),
        BODY_Z("BODY", "Body +Z"),
        VELOCITY_AXIS("VELO", "Velocity axis"),
        MOTION_VECTOR("MOTION", "Motion vector"),
        CACHED_LAST("CACHED", "Last heading"),
        NONE("UNRESOLVED", "Unavailable");

        private final String tag;
        private final String label;

        ForwardSource(String tag, String label) {
            this.tag = tag;
            this.label = label;
        }

        public String getTag() {
            return this.tag;
        }

        public String getLabel() {
            return this.label;
        }
    }
}
