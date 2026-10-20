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

    public HingeJoint findJointBetween(UUID a, UUID b) {
        for (HingeJoint joint : joints.values()) {
            if ((joint.subAId.equals(a) && joint.subBId.equals(b))
                    || (joint.subAId.equals(b) && joint.subBId.equals(a))) {
                return joint;
            }
        }
        return null;
    }

    public List<HingeJoint> jointsInvolving(UUID subId) {
        List<HingeJoint> out = new ArrayList<>();
        for (HingeJoint joint : joints.values()) {
            if (joint.subAId.equals(subId) || joint.subBId.equals(subId)) {
                out.add(joint);
            }
        }
        return out;
    }

    public int removeJointsInvolving(UUID subId) {
        List<HingeJoint> involved = jointsInvolving(subId);
        for (HingeJoint joint : involved) {
            removeJoint(joint.jointId);
        }
        return involved.size();
    }

    public void removeJoint(UUID jointId) {
        HingeJoint joint = joints.remove(jointId);
        if (joint != null) {
            HingeConstraintBridge.removeConstraint(joint.handle);
            joint.handle = null;
            setDirty();
        }
    }

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
                joint.handle = null;
                continue;
            }
            joint.handle = HingeConstraintBridge.createRotary(level, subA, subB,
                joint.pivotLocalA, joint.pivotLocalB, joint.axisLocalA, joint.axisLocalB);
        }
    }
}
