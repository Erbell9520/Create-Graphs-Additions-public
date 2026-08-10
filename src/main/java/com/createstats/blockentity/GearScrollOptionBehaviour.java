package com.createstats.blockentity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** [1] */
public class GearScrollOptionBehaviour<E extends Enum<E> & INamedIconOptions> extends ScrollOptionBehaviour<E> {

    private final BehaviourType<?> type;
    private final String nbtKey;
    private final int netId;

    public GearScrollOptionBehaviour(Class<E> options, Component label, SmartBlockEntity be,
                                     ValueBoxTransform slot, BehaviourType<?> type, String nbtKey, int netId) {
        super(options, label, be, slot);
        this.type = type;
        this.nbtKey = nbtKey;
        this.netId = netId;
    }

    @Override
    public BehaviourType<?> getType() {
        return type;
    }

    /** [2] */
    @Override
    public int netId() {
        return netId;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        // Deliberately skip the super implementation (which writes the shared "ScrollValue" key).
        nbt.putInt(nbtKey, value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        value = nbt.getInt(nbtKey);
    }
}
