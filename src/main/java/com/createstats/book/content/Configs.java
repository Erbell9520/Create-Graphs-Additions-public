package com.createstats.book.content;

import com.createstats.ModConfiguration;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Configs {
    private Configs() {
    }

    public static int dynamoBuffer() {
        return value(ModConfiguration.COMMON.dynamoBufferCapacity);
    }

    public static int dynamoTransferRate() {
        return value(ModConfiguration.COMMON.dynamoTransferRate);
    }

    public static int bankBlockCapacity() {
        return value(ModConfiguration.COMMON.capacitorBankCapacity);
    }

    public static double bankMergeExponent() {
        return value(ModConfiguration.COMMON.capacitorBankExponent);
    }

    public static int bankMaxFootprint() {
        return value(ModConfiguration.COMMON.capacitorBankMaxFootprint);
    }

    public static int bankMaxHeight() {
        return value(ModConfiguration.COMMON.capacitorBankMaxHeight);
    }

    public static int bankTransferRate() {
        return value(ModConfiguration.COMMON.capacitorBankTransferRate);
    }

    public static int batteryPortRate() {
        return value(ModConfiguration.COMMON.batteryControllerPortRate);
    }

    // never less than one tick of its three inputs
    public static int batteryBus() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max((long) value(ModConfiguration.COMMON.batteryControllerBuffer),
                (long) com.createstats.power.BatteryControllerPorts.INPUTS * batteryInputRate()));
    }

    public static int batteryInputRate() {
        return (int) Math.min(Integer.MAX_VALUE,
                (long) com.createstats.blockentity.BatteryControllerBE.INPUT_LINES * batteryPortRate());
    }

    public static com.createstats.power.FeReserveBand.Band batteryBand() {
        return new com.createstats.power.FeReserveBand.Band(value(ModConfiguration.COMMON.batteryControllerBandFloorPct),
                value(ModConfiguration.COMMON.batteryControllerBandCeilingPct),
                value(ModConfiguration.COMMON.batteryControllerBandHealthyPct));
    }

    public static com.createstats.power.FeReserveBand.Minutes batteryMinutes() {
        return new com.createstats.power.FeReserveBand.Minutes(
                value(ModConfiguration.COMMON.batteryControllerBandFloorMinutes),
                value(ModConfiguration.COMMON.batteryControllerBandCeilingMinutes),
                value(ModConfiguration.COMMON.batteryControllerBandHealthyMinutes));
    }

    public static int batteryRideTicks() {
        return value(ModConfiguration.COMMON.batteryControllerRideTicks);
    }

    public static int batteryClaimPct() {
        return value(ModConfiguration.COMMON.batteryControllerChargeClaimPct);
    }

    public static int portableMaxRate() {
        return value(ModConfiguration.COMMON.portableInterfaceMaxRate);
    }

    public static int portableFloorPct() {
        return value(ModConfiguration.COMMON.portableInterfaceFloorPct);
    }

    public static int connectorBuffer() {
        return value(ModConfiguration.COMMON.connectorBufferCapacity);
    }

    public static int wireReach() {
        return value(ModConfiguration.COMMON.connectorWireRange);
    }

    public static int pduBuffer() {
        return value(ModConfiguration.COMMON.pduBufferCapacity);
    }

    public static int pduIdleDrain() {
        return value(ModConfiguration.COMMON.pduIdleDrain);
    }

    public static int pduFloorPct() {
        return value(ModConfiguration.COMMON.pduReserveFloorPct);
    }

    public static int pduRestoreSocPct() {
        return value(ModConfiguration.COMMON.pduRestoreSocPct);
    }

    public static int pduBurstMarginPct() {
        return value(ModConfiguration.COMMON.pduBurstMarginPct);
    }

    public static int pduDwellTicks() {
        return value(ModConfiguration.COMMON.pduDwellTicks);
    }

    public static int pduRestoreIntervalTicks() {
        return value(ModConfiguration.COMMON.pduRestoreIntervalTicks);
    }

    public static int pduRideHorizonTicks() {
        return Math.max(1, value(ModConfiguration.COMMON.pduRideHorizonTicks));
    }

    public static boolean pduCoreProtection() {
        return value(ModConfiguration.COMMON.pduCoreProtection);
    }

    public static int pduCoreProtectionPct() {
        return value(ModConfiguration.COMMON.pduCoreProtectionPct);
    }

    public static boolean pduExportPolicy() {
        return value(ModConfiguration.COMMON.pduExportPolicy);
    }

    public static int outletBuffer() {
        return value(ModConfiguration.COMMON.fePowerProfiles.get(
                com.createstats.power.FeProfiles.KEY_WALL_OUTLET).bufferCapacity);
    }

    static <T> T value(ModConfigSpec.ConfigValue<T> v) {
        try {
            return v.get();
        } catch (RuntimeException notLoaded) {
            return v.getDefault();
        }
    }
}
