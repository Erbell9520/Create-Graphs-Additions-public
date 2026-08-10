package com.createstats.flight.hinge;

import com.createstats.flight.bridge.HingeConstraintBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** [1] */
public final class HingeJointManager extends SavedData {

    private static final String DATA_NAME = "create_stats_hinges";

    private final Map<UUID, HingeJoint> joints = new HashMap<>();

    public static HingeJointManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(HingeJointManager::new, HingeJointManager::load), DATA_NAME);
    }

    private static HingeJointManager load(CompoundTag tag, HolderLookup.Provider registries) {
        HingeJointManager data = new HingeJointManager();
        NBTHelper.iterateCompoundList(tag.getList("Joints", Tag.TAG_COMPOUND), entryTag -> {
            HingeJoint joint = HingeJoint.load(entryTag);
            data.joints.put(joint.jointId, joint);
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (HingeJoint joint : joints.values()) {
            list.add(joint.save());
        }
        tag.put("Joints", list);
        return tag;
    }

    public void addJoint(HingeJoint joint) {
        joints.put(joint.jointId, joint);
        setDirty();
    }

    public List<HingeJoint> all() {
        return new ArrayList<>(joints.values());
    }

    /** The joint directly linking these two ships (in either order), or {@code null}. */
    public HingeJoint findJointBetween(UUID a, UUID b) {
        for (HingeJoint joint : joints.values()) {
            if ((joint.subAId.equals(a) && joint.subBId.equals(b))
                    || (joint.subAId.equals(b) && joint.subBId.equals(a))) {
                return joint;
            }
        }
        return null;
    }

    /** All joints that involve the given ship (as either side). */
    public List<HingeJoint> jointsInvolving(UUID subId) {
        List<HingeJoint> out = new ArrayList<>();
        for (HingeJoint joint : joints.values()) {
            if (joint.subAId.equals(subId) || joint.subBId.equals(subId)) {
                out.add(joint);
            }
        }
        return out;
    }

    /** Remove every joint touching the given ship, tearing down their constraints. Returns the count. */
    public int removeJointsInvolving(UUID subId) {
        List<HingeJoint> involved = jointsInvolving(subId);
        for (HingeJoint joint : involved) {
            removeJoint(joint.jointId);
        }
        return involved.size();
    }

    /** Remove a joint and tear down its live Sable constraint. */
    public void removeJoint(UUID jointId) {
        HingeJoint joint = joints.remove(jointId);
        if (joint != null) {
            HingeConstraintBridge.removeConstraint(joint.handle);
            joint.handle = null;
            setDirty();
        }
    }

    /**
     * (Re)create the Sable constraint for any joint whose handle is missing/invalid and whose two
     * ships are both currently loaded. Cheap to call repeatedly; throttle from the caller.
     */
    public void ensureHandles(ServerLevel level) {
        if (!HingeConstraintBridge.isAvailable() || joints.isEmpty()) {
            return;
        }
        for (HingeJoint joint : joints.values()) {
            if (HingeConstraintBridge.isConstraintValid(joint.handle)) {
                continue;
            }
            Object subA = HingeConstraintBridge.subLevelById(level, joint.subAId);
            Object subB = HingeConstraintBridge.subLevelById(level, joint.subBId);
            if (subA == null || subB == null) {
                joint.handle = null; // one ship not loaded yet; retry later
                continue;
            }
            joint.handle = HingeConstraintBridge.createRotary(level, subA, subB,
                joint.pivotLocalA, joint.pivotLocalB, joint.axisLocalA, joint.axisLocalB);
        }
    }
}
