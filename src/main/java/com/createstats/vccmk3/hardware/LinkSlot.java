package com.createstats.vccmk3.hardware;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

// every row gets a diagnosis, nothing dropped
public record LinkSlot(
        int row,
        LinkHealth health,
        @Nullable DeviceCategory category,
        @Nullable BlockPos actuatorPos,
        @Nullable BlockPos governorPos,
        @Nullable UUID deviceId,
        String detail
) {
    public LinkSlot {
        if (health == null) health = LinkHealth.EMPTY;
        if (detail == null) detail = "";
    }

    public static LinkSlot empty(int row) {
        return new LinkSlot(row, LinkHealth.EMPTY, null, null, null, null, "");
    }

    public static LinkSlot problem(int row, LinkHealth health, @Nullable BlockPos actuator,
                                   @Nullable BlockPos governor, String detail) {
        return new LinkSlot(row, health, null, actuator, governor, null, detail);
    }

    public static LinkSlot ok(int row, DeviceCategory category, BlockPos actuator,
                              @Nullable BlockPos governor, UUID deviceId) {
        return new LinkSlot(row, LinkHealth.OK, category, actuator, governor, deviceId, "");
    }

    public boolean isCommandable() {
        return health.isCommandable() && deviceId != null;
    }

    public boolean needsAttention() {
        return health.needsAttention();
    }

    public String advice() {
        if (health == LinkHealth.OK) {
            return category == null ? health.problem() : category.label() + " linked and ready";
        }
        String base = health.problem();
        if (!detail.isEmpty()) base = base + " (" + detail + ")";
        return base + ". " + health.fix();
    }

    public String statusWord() {
        return switch (health) {
            case OK -> "READY";
            case EMPTY -> "EMPTY";
            case DISABLED -> "OFF";
            case MISSING_ACTUATOR_CARD, MISSING_GOVERNOR_CARD, CARD_BLANK -> "SETUP";
            case CROSS_DIMENSION, WRONG_TARGET_TYPE -> "WRONG";
            case ACTUATOR_MISSING, GOVERNOR_MISSING -> "GONE";
            case GOVERNOR_CLAIMED -> "TAKEN";
            case OFFLINE -> "OFFLINE";
        };
    }
}
