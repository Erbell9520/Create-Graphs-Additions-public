package com.createstats.power;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;

public interface SecurityDevice {
    DeviceClass deviceClass();

    default Component deviceName() {
        if (this instanceof BlockEntity be) {
            return be.getBlockState().getBlock().getName();
        }
        return Component.empty();
    }

    default DeviceState deviceState() {
        return DeviceState.IDLE;
    }

    default float integrity() {
        return -1.0f;
    }

    @org.jetbrains.annotations.Nullable
    default net.minecraft.core.BlockPos governingPanel() {
        return null;
    }

    @org.jetbrains.annotations.Nullable
    default java.util.UUID governingHousehold() {
        return null;
    }

    enum DeviceClass {
        DOOR("door"),
        SENSOR("sensor"),
        LOCKER("locker"),
        SHUTTER("shutter"),

        REPELLER("repeller"),

        RELAY("relay"),

        COIL("coil");

        private static final DeviceClass[] ORDER = values();

        private final String key;

        DeviceClass(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public Component label() {
            return Component.translatable("device.create_stats." + key + ".plural");
        }

        public Component emptyLine() {
            return Component.translatable("device.create_stats." + key + ".empty");
        }

        public Component stateLabel(DeviceState state) {
            return Component.translatable("devicestate.create_stats." + key + "." + state.key());
        }

        public static DeviceClass byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : DOOR;
        }

        public static DeviceClass[] all() {
            return ORDER;
        }
    }

    enum DeviceState {
        IDLE,

        ACTIVE,

        DAMAGED,

        BREACHED,

        OFFLINE,

        UNPOWERED;

        private static final DeviceState[] ORDER = values();

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component label() {
            return Component.translatable("devicestate.create_stats." + key());
        }

        public boolean isFault() {
            return this == BREACHED || this == OFFLINE;
        }

        public static DeviceState byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : IDLE;
        }
    }
}
