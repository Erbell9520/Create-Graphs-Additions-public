package com.createstats.flight.hinge;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;

public final class HingeJoint {
    public final UUID jointId;
    public final UUID subAId;
    public final UUID subBId;
    public final Vector3d pivotLocalA;
    public final Vector3d pivotLocalB;
    public final Vector3d axisLocalA;
    public final Vector3d axisLocalB;

    public transient Object handle;

    public HingeJoint(UUID jointId, UUID subAId, UUID subBId,
                      Vector3d pivotLocalA, Vector3d pivotLocalB,
                      Vector3d axisLocalA, Vector3d axisLocalB) {
        this.jointId = jointId;
        this.subAId = subAId;
        this.subBId = subBId;
        this.pivotLocalA = pivotLocalA;
        this.pivotLocalB = pivotLocalB;
        this.axisLocalA = axisLocalA;
        this.axisLocalB = axisLocalB;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("JointId", jointId);
        tag.putUUID("SubA", subAId);
        tag.putUUID("SubB", subBId);
        putVec(tag, "PivotA", pivotLocalA);
        putVec(tag, "PivotB", pivotLocalB);
        putVec(tag, "AxisA", axisLocalA);
        putVec(tag, "AxisB", axisLocalB);
        return tag;
    }

    public static HingeJoint load(CompoundTag tag) {
        return new HingeJoint(
            tag.getUUID("JointId"),
            tag.getUUID("SubA"),
            tag.getUUID("SubB"),
            getVec(tag, "PivotA"),
            getVec(tag, "PivotB"),
            getVec(tag, "AxisA"),
            getVec(tag, "AxisB"));
    }

    private static void putVec(CompoundTag tag, String key, Vector3d v) {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", v.x);
        t.putDouble("y", v.y);
        t.putDouble("z", v.z);
        tag.put(key, t);
    }

    private static Vector3d getVec(CompoundTag tag, String key) {
        CompoundTag t = tag.getCompound(key);
        return new Vector3d(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }
}
