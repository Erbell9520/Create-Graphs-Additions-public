package com.createstats.vccmk3.hardware;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.item.FlightCardData;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.vccmk3.model.DeviceGeometry;
import com.createstats.vccmk3.model.VccDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HardwareScan {
    public record ScanResult(List<LinkSlot> slots, List<VccDevice> devices) {
        public ScanResult {
            slots = List.copyOf(slots == null ? List.of() : slots);
            devices = List.copyOf(devices == null ? List.of() : devices);
        }

        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());

        public List<LinkSlot> faults() {
            List<LinkSlot> out = new ArrayList<>();
            for (LinkSlot slot : slots) {
                if (slot.needsAttention()) out.add(slot);
            }
            return out;
        }

        public int commandableCount() {
            int n = 0;
            for (LinkSlot slot : slots) {
                if (slot.isCommandable()) n++;
            }
            return n;
        }

        @Nullable
        public LinkSlot worstFault() {
            LinkSlot worst = null;
            for (LinkSlot slot : slots) {
                if (!slot.needsAttention()) continue;
                if (worst == null || slot.health().severity().ordinal() > worst.health().severity().ordinal()) {
                    worst = slot;
                }
            }
            return worst;
        }
    }

    private HardwareScan() {}

    public static String identityKey(VccDevice device) {
        return device.category() == DeviceCategory.SIGNAL_LINK
                ? "row:" + device.cardRow()
                : "pos:" + device.actuatorPos().asLong();
    }

    public static ScanResult scan(@Nullable Level level,
                                  @Nullable ItemStackHandler cards,
                                  int rowCount,
                                  int linkRowMask,
                                  List<VccDevice> existing,
                                  @Nullable UUID controllerId,
                                  BlockPos controllerPos) {
        if (level == null || cards == null) return ScanResult.EMPTY;

        Map<String, VccDevice> byIdentity = new HashMap<>();
        for (VccDevice device : existing == null ? List.<VccDevice>of() : existing) {
            byIdentity.put(identityKey(device), device);
        }

        ResourceLocation myDimension = level.dimension().location();
        List<LinkSlot> slots = new ArrayList<>(rowCount);
        List<VccDevice> devices = new ArrayList<>();
        int index = 1;

        for (int row = 0; row < rowCount; row++) {
            ItemStack actuatorStack = cards.getStackInSlot(actuatorSlot(row));
            ItemStack governorStack = cards.getStackInSlot(governorSlot(row));

            if ((linkRowMask & (1 << row)) != 0) {
                LinkSlot signalSlot = scanSignalRow(row, actuatorStack, governorStack,
                        byIdentity, devices, controllerPos, index);
                slots.add(signalSlot);
                if (signalSlot.deviceId() != null) index++;
                continue;
            }

            boolean hasActuatorCard = actuatorStack.getItem() instanceof PropellerCardItem;
            boolean hasGovernorCard = governorStack.getItem() instanceof GovernorCardItem;

            if (!hasActuatorCard && !hasGovernorCard) {
                slots.add(LinkSlot.empty(row));
                continue;
            }
            if (!hasActuatorCard) {
                slots.add(LinkSlot.problem(row, LinkHealth.MISSING_ACTUATOR_CARD, null, null, ""));
                continue;
            }

            FlightCardData actuatorData = FlightCardData.get(actuatorStack, PropellerCardItem.TAG_KEY);
            if (actuatorData == null || !actuatorData.isLinked()) {
                slots.add(LinkSlot.problem(row, LinkHealth.CARD_BLANK, null, null, "Propeller Card"));
                continue;
            }
            if (!dimensionMatches(actuatorData, myDimension)) {
                slots.add(LinkSlot.problem(row, LinkHealth.CROSS_DIMENSION,
                        actuatorData.getLinkedPos(), null, "Propeller Card"));
                continue;
            }

            BlockPos actuatorPos = actuatorData.getLinkedPos();
            DeviceCategory category = HardwareClassifier.classify(level, actuatorPos);
            if (category == null) {
                boolean loaded = level.isLoaded(actuatorPos);
                slots.add(LinkSlot.problem(row,
                        loaded ? LinkHealth.WRONG_TARGET_TYPE : LinkHealth.ACTUATOR_MISSING,
                        actuatorPos, null,
                        loaded ? HardwareClassifier.describe(level, actuatorPos) : formatPos(actuatorPos)));
                continue;
            }

            BlockPos governorPos = null;
            if (category.requiresGovernor()) {
                if (!hasGovernorCard) {
                    slots.add(LinkSlot.problem(row, LinkHealth.MISSING_GOVERNOR_CARD, actuatorPos, null, ""));
                    continue;
                }
                FlightCardData governorData = FlightCardData.get(governorStack, GovernorCardItem.TAG_KEY);
                if (governorData == null || !governorData.isLinked()) {
                    slots.add(LinkSlot.problem(row, LinkHealth.CARD_BLANK, actuatorPos, null, "Governor Card"));
                    continue;
                }
                if (!dimensionMatches(governorData, myDimension)) {
                    slots.add(LinkSlot.problem(row, LinkHealth.CROSS_DIMENSION,
                            actuatorPos, governorData.getLinkedPos(), "Governor Card"));
                    continue;
                }
                governorPos = governorData.getLinkedPos();
                if (!HardwareClassifier.isGovernor(level, governorPos)) {
                    slots.add(LinkSlot.problem(row, LinkHealth.GOVERNOR_MISSING,
                            actuatorPos, governorPos, formatPos(governorPos)));
                    continue;
                }
                if (isClaimedByAnother(level, governorPos, controllerId)) {
                    slots.add(LinkSlot.problem(row, LinkHealth.GOVERNOR_CLAIMED,
                            actuatorPos, governorPos, formatPos(governorPos)));
                    continue;
                }
            } else if (hasGovernorCard) {
                FlightCardData governorData = FlightCardData.get(governorStack, GovernorCardItem.TAG_KEY);
                if (governorData != null && governorData.isLinked()) {
                    governorPos = governorData.getLinkedPos();
                }
            }

            VccDevice previous = byIdentity.get("pos:" + actuatorPos.asLong());
            VccDevice device = previous != null
                    // refresh the row number, fault messages point at it

                    ? previous.withCategory(category).withPositions(actuatorPos, governorPos).withCardRow(row)
                    : VccDevice.fromRow(UUID.randomUUID(), category, actuatorPos, governorPos, row, index);

            device = device.withGeometry(GeometryProbe.probe(level, actuatorPos, category));

            if (!device.enabled()) {
                slots.add(new LinkSlot(row, LinkHealth.DISABLED, category, actuatorPos, governorPos, device.id(), ""));
                devices.add(device);
                index++;
                continue;
            }

            devices.add(device);
            slots.add(LinkSlot.ok(row, category, actuatorPos, governorPos, device.id()));
            index++;
        }

        return new ScanResult(slots, devices);
    }

    private static LinkSlot scanSignalRow(int row, ItemStack first, ItemStack second,
                                          Map<String, VccDevice> byIdentity, List<VccDevice> devices,
                                          BlockPos controllerPos, int index) {
        if (first.isEmpty() && second.isEmpty()) {
            return new LinkSlot(row, LinkHealth.CARD_BLANK, DeviceCategory.SIGNAL_LINK,
                    null, null, null, "no frequency set");
        }
        VccDevice previous = byIdentity.get("row:" + row);
        VccDevice device = previous != null
                ? previous.withCategory(DeviceCategory.SIGNAL_LINK)
                : VccDevice.fromRow(UUID.randomUUID(), DeviceCategory.SIGNAL_LINK,
                        controllerPos, null, row, index);
        device = device.withGeometry(DeviceGeometry.notSpatial());
        devices.add(device);
        if (!device.enabled()) {
            return new LinkSlot(row, LinkHealth.DISABLED, DeviceCategory.SIGNAL_LINK,
                    controllerPos, null, device.id(), "");
        }
        return LinkSlot.ok(row, DeviceCategory.SIGNAL_LINK, controllerPos, null, device.id());
    }

    public static int actuatorSlot(int row) {
        return row * 2;
    }

    public static int governorSlot(int row) {
        return row * 2 + 1;
    }

    private static boolean dimensionMatches(FlightCardData data, ResourceLocation myDimension) {
        ResourceLocation cardDimension = data.getDimension();

        return cardDimension == null || cardDimension.equals(myDimension);
    }

    private static boolean isClaimedByAnother(Level level, @Nullable BlockPos pos, @Nullable UUID controllerId) {
        if (pos == null) return false;
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(be instanceof RpmGovernorBE governor)) return false;
        UUID owner = governor.getCommanderUuid();
        return owner != null && controllerId != null && !owner.equals(controllerId);
    }

    private static String formatPos(@Nullable BlockPos pos) {
        return pos == null ? "unknown" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
