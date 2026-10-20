package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FreightManifestLogic {
    public static final int MAX_LINES = 32;

    private static final String ARRIVAL_ORDERS_KEY = "create_stats_manifest_arrival_orders";

    private FreightManifestLogic() {
    }

    public static FreightManifestValidationResult validate(Iterable<FreightManifestLine> lines) {
        int index = 0;
        for (FreightManifestLine line : lines) {
            index++;
            if (index > MAX_LINES) {
                return FreightManifestValidationResult.error("Manifests may not exceed " + MAX_LINES + " lines.");
            }
            if (!line.hasSelection()) {
                return FreightManifestValidationResult.error("Line " + index + " is missing an item or fluid.");
            }
            if (line.getAmount() <= 0) {
                return FreightManifestValidationResult.error("Line " + index + " must have an amount greater than 0.");
            }
            if (line.getResetMode().isIntervalBased() && line.getResetIntervalValue() <= 0) {
                return FreightManifestValidationResult.error("Line " + index + " needs a reset interval greater than 0.");
            }
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null) {
                return FreightManifestValidationResult.error("Line " + index + " is missing an item or fluid.");
            }
        }
        return FreightManifestValidationResult.ok();
    }

    public static Set<UUID> findDuplicateLineIds(Iterable<FreightManifestLine> lines) {
        Map<LineDuplicateKey, UUID> firstSeen = new HashMap<>();
        Set<UUID> duplicates = new HashSet<>();
        for (FreightManifestLine line : lines) {
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null) {
                continue;
            }
            LineDuplicateKey duplicateKey = new LineDuplicateKey(line.getOperation(), key);
            UUID firstId = firstSeen.putIfAbsent(duplicateKey, line.getLineId());
            if (firstId != null) {
                duplicates.add(firstId);
                duplicates.add(line.getLineId());
            }
        }
        return duplicates;
    }

    // the station owns the accounting. create throws the condition's tag away the moment it returns true
    public static FreightManifestEvaluationResult evaluateStation(FreightManifestSavedData savedData, UUID stationId,
                                                                  @Nullable Train train, long currentTick,
                                                                  @Nullable CompoundTag context) {
        FreightManifestStationState stationState = savedData.getStationState(stationId);
        if (stationState == null) {
            return FreightManifestEvaluationResult.complete();
        }

        if (stationState.retireCompletedOnceOrders()) {
            savedData.setDirty();
        }
        FreightManifest manifest = stationState.peekManifest();
        DeliveryOrderBoard persistentBoard = stationState.getMutablePersistentOrders();

        if (train != null) {
            GlobalStation dockedAt = train.getCurrentStation();
            if (dockedAt == null || !stationId.equals(dockedAt.getId())) {
                train = null;
            }
        }

        if (train == null) {
            boolean changed = stationState.endVisit(currentTick);
            if (changed) {
                ManifestUnloadTracker.endVisit(stationId);
            }
            if (stationState.claimAllocationTick(currentTick)) {
                changed |= refreshPersistentCycles(manifest, persistentBoard, currentTick, stationState, false);
            }
            if (changed) {
                savedData.setDirty();
            }
            return manifest.isEmpty() ? FreightManifestEvaluationResult.complete()
                : FreightManifestEvaluationResult.waiting("No train currently present at this station.");
        }
        if (train.carriages.isEmpty()) {
            return FreightManifestEvaluationResult.waiting("Train has no cargo carriages.");
        }

        DeliveryOrderBoard arrivalBoard = stationState.getMutableVisitOrders();

        if (stationState.claimAllocationTick(currentTick)) {
            String trainName = train.name != null ? train.name.getString() : "Train";
            boolean changed = stationState.beginVisit(train.id, trainName, currentTick);

            arrivalBoard = stationState.getMutableVisitOrders();
            changed |= refreshPersistentCycles(manifest, persistentBoard, currentTick, stationState, true);

            TrainCargoSnapshot currentSnapshot = TrainCargoSnapshot.capture(train);
            if (!stationState.isVisitSnapshotTaken()) {
                stationState.setVisitLastSnapshot(currentSnapshot);
                changed = true;
            } else {
                TrainCargoSnapshot stepToStation = new TrainCargoSnapshot();
                TrainCargoSnapshot stepToTrain = new TrainCargoSnapshot();
                applyStepDelta(stationState.getVisitLastSnapshot(), currentSnapshot, stepToStation, stepToTrain);
                stationState.setVisitLastSnapshot(currentSnapshot);
                stationState.recordVisitMovement(stepToStation, stepToTrain);

                AllocationResult allocation = allocateStepDeltas(manifest, arrivalBoard, persistentBoard,
                    stepToStation, stepToTrain, currentTick, stationState);
                changed |= allocation.arrivalChanged() || allocation.persistentChanged();
                stationState.setLastUnaccounted(
                    describeUnaccountedCargo(manifest, allocation.remainingToStation(), allocation.remainingToTrain()));
            }

            changed |= stationState.retireCompletedOnceOrders();

            changed |= stationState.recordVisitOrders(arrivalBoard, train.id, trainName, currentTick);
            if (changed) {
                savedData.setDirty();
            }
        }

        if (context != null) {
            saveArrivalBoard(context, arrivalBoard);
        }

        return evaluateCompletion(manifest, arrivalBoard, persistentBoard, stationState.getLastUnaccounted());
    }

    public static void sweepDepartedVisits(net.minecraft.server.level.ServerLevel level) {
        FreightManifestSavedData savedData = FreightManifestSavedData.peek(level);
        if (savedData == null) {
            return;
        }
        List<UUID> openVisits = savedData.getStationsWithOpenVisit();
        if (openVisits.isEmpty()) {
            return;
        }
        Set<UUID> docked = new HashSet<>();
        for (Train train : com.simibubi.create.Create.RAILWAYS.sided(level).trains.values()) {
            GlobalStation station = train.getCurrentStation();
            if (station != null && station.getId() != null) {
                docked.add(station.getId());
            }
        }
        long currentTick = level.getGameTime();
        boolean changed = false;
        for (UUID stationId : openVisits) {
            if (docked.contains(stationId)) {
                continue;
            }
            FreightManifestStationState stationState = savedData.getStationState(stationId);
            if (stationState != null && stationState.endVisit(currentTick)) {
                ManifestUnloadTracker.endVisit(stationId);
                changed = true;
            }
        }
        if (changed) {
            savedData.setDirty();
        }
    }

    public static FreightManifestEvaluationResult previewEvaluation(FreightManifest manifest, FreightStationCargoView cargoView) {
        if (manifest == null || manifest.isEmpty()) {
            return FreightManifestEvaluationResult.complete();
        }
        if (cargoView == null || !cargoView.isTrainPresent()) {
            String status = cargoView != null ? cargoView.getStatusMessage() : "No train currently present at this station.";
            return FreightManifestEvaluationResult.waiting(status);
        }
        return evaluatePreview(manifest, new FreightTransferProgress(cargoView.getFreightTotals(), cargoView.getMovedToStation(), cargoView.getMovedToTrain()));
    }

    public static Map<FreightManifestResourceKey, Integer> getExpectedAmounts(FreightManifest manifest, ManifestOperation operation) {
        if (manifest == null || manifest.isEmpty()) {
            return new HashMap<>();
        }
        return buildExpectedAmounts(manifest, operation);
    }

    public static boolean refreshPersistentCycles(FreightManifest manifest, DeliveryOrderBoard persistentBoard, long currentTick,
                                                  @Nullable FreightManifestStationState stationState) {
        return refreshPersistentCycles(manifest, persistentBoard, currentTick, stationState, false);
    }

    public static boolean refreshPersistentCycles(FreightManifest manifest, DeliveryOrderBoard persistentBoard, long currentTick,
                                                  @Nullable FreightManifestStationState stationState, boolean trainDocked) {
        boolean changed = false;
        for (FreightManifestLine line : manifest.getLines()) {
            if (!line.getResetMode().isPersistent()) {
                persistentBoard.remove(line.getLineId());
                continue;
            }
            DeliveryOrderLineState state = persistentBoard.getOrCreate(line.getLineId(), currentTick);
            state.reconcileTarget(line.getAmount(), currentTick);
            if (!trainDocked && shouldResetPersistentLine(line, state, currentTick)) {
                boolean hadActivity = state.hasProgress() || state.isDismissed() || state.getLastCompletionTick() >= 0L;
                state.reset(currentTick);
                if (hadActivity && stationState != null) {
                    stationState.addHistory(DeliveryOrderHistoryType.AUTO_RESET, currentTick, "Cycle Reset",
                        describeLine(line) + " | " + getResetHistoryReason(line));
                }
                changed = true;
            }
        }
        return changed;
    }

    public static DeliveryOrderBoardView buildDeliveryOrderBoardView(FreightManifest manifest, DeliveryOrderBoard persistentBoard,
                                                                     @Nullable DeliveryOrderBoard arrivalBoard,
                                                                     FreightStationCargoView cargoView, long currentTick,
                                                                     boolean arrivalTrackingActive, boolean arrivalHistoric,
                                                                     List<DeliveryOrderHistoryEntry> historyEntries) {
        if (manifest == null || manifest.isEmpty()) {
            boolean present = cargoView != null && cargoView.isTrainPresent();
            return new DeliveryOrderBoardView(present, arrivalTrackingActive,
                present ? cargoView.getTrainName() : "", "No active delivery orders.", currentTick,
                0, 0, 0, List.of(), historyEntries);
        }

        List<DeliveryOrderLineView> lines = new ArrayList<>();
        int completed = 0;
        int dismissed = 0;
        for (FreightManifestLine line : manifest.getLines()) {
            DeliveryOrderLineState state = getStateForLine(line, arrivalBoard, persistentBoard, currentTick, false);
            int deliveredAmount = state == null ? 0 : Math.min(state.getDeliveredAmount(), Math.max(0, line.getAmount()));
            boolean complete = deliveredAmount >= line.getAmount() && line.getAmount() > 0;

            boolean dismissedThisCycle = state != null && DeliveryOrderLifecyclePolicy.isArchived(
                line.getResetMode(), complete, state.isDismissed());
            if (complete) {
                completed++;
            }
            if (dismissedThisCycle) {
                dismissed++;
            }

            FreightManifestResourceKey key = line.getResourceKey();
            int freightAmount = key == null || cargoView == null ? 0 : cargoView.getFreightTotals().getAmount(key);
            long ticksUntilReset = getTicksUntilReset(line, state, currentTick);
            boolean lineHistoric = arrivalHistoric && !line.getResetMode().isPersistent();
            String statusText = buildLineStatus(line, state, complete, dismissedThisCycle,
                cargoView != null && cargoView.isTrainPresent(), arrivalTrackingActive, lineHistoric, currentTick);
            lines.add(new DeliveryOrderLineView(line.getLineId(), line.getCargoKind(), line.getOperation(), line.getResetMode(),
                line.getResetIntervalValue(), line.getDisplayStack(), line.getDisplayName(), line.getAmount(), deliveredAmount,
                freightAmount, dismissedThisCycle, ticksUntilReset, statusText));
        }

        String summaryStatus = buildBoardSummary(lines.size(), completed, dismissed, cargoView, arrivalTrackingActive);
        String trainName = cargoView != null && cargoView.isTrainPresent() ? cargoView.getTrainName() : "";
        boolean trainPresent = cargoView != null && cargoView.isTrainPresent();
        return new DeliveryOrderBoardView(trainPresent, arrivalTrackingActive, trainName, summaryStatus, currentTick,
            lines.size(), completed, dismissed, lines, historyEntries);
    }

    public static FreightTransferProgress captureVisitProgress(@Nullable FreightManifestStationState stationState, Train train) {
        return captureVisitProgress(stationState, train, Long.MIN_VALUE);
    }

    public static FreightTransferProgress captureVisitProgress(@Nullable FreightManifestStationState stationState,
                                                               Train train, long currentTick) {
        if (stationState == null || !stationState.isVisitActive(train.id)) {
            return FreightTransferProgress.empty(TrainCargoSnapshot.capture(train));
        }
        TrainCargoSnapshot current = stationState.isVisitSnapshotTaken()
            && stationState.getVisitLastSeenTick() == currentTick
                ? stationState.getVisitLastSnapshot()
                : TrainCargoSnapshot.capture(train);
        return new FreightTransferProgress(current, stationState.getVisitMovedToStation(), stationState.getVisitMovedToTrain());
    }

    public static DeliveryOrderBoard loadArrivalBoard(CompoundTag context) {
        if (context.contains(ARRIVAL_ORDERS_KEY)) {
            return DeliveryOrderBoard.load(context.getCompound(ARRIVAL_ORDERS_KEY));
        }
        return new DeliveryOrderBoard();
    }

    public static void saveArrivalBoard(CompoundTag context, DeliveryOrderBoard board) {
        context.put(ARRIVAL_ORDERS_KEY, board.save());
    }

    @Nullable
    public static CompoundTag findActiveManifestContext(Train train) {
        return train == null ? null : findActiveManifestContext(train.runtime);
    }

    public static boolean applyOrderAction(FreightManifestLine line, DeliveryOrderLineState state, DeliveryOrderAction action, long currentTick) {
        return switch (action) {
            case MARK_COMPLETE -> {
                if (state.isComplete(line.getAmount())) {
                    yield false;
                }
                state.markComplete(line.getAmount(), currentTick);
                if (DeliveryOrderLifecyclePolicy.archivesOnCompletion(line.getResetMode())) {
                    state.setDismissed(true);
                }
                yield true;
            }
            case TOGGLE_DISMISSED -> {
                if (DeliveryOrderLifecyclePolicy.archivesOnCompletion(line.getResetMode())
                        && state.isComplete(line.getAmount())) {
                    yield false;
                }
                if (!state.isComplete(line.getAmount()) && !state.isDismissed()) {
                    yield false;
                }
                state.setDismissed(!state.isDismissed());
                yield true;
            }
            case RESET_CYCLE -> {
                state.reset(currentTick);
                yield true;
            }
            case HIDE_COMPLETED -> {
                if (!state.isComplete(line.getAmount()) || state.isDismissed()) {
                    yield false;
                }
                state.setDismissed(true);
                yield true;
            }
            case SHOW_HIDDEN -> {
                if (!state.isDismissed()
                        || (DeliveryOrderLifecyclePolicy.archivesOnCompletion(line.getResetMode())
                            && state.isComplete(line.getAmount()))) {
                    yield false;
                }
                state.setDismissed(false);
                yield true;
            }
            case RESET_COMPLETED -> {
                if (!state.isComplete(line.getAmount())) {
                    yield false;
                }
                state.reset(currentTick);
                yield true;
            }
        };
    }

    public static int applyBulkOrderAction(FreightManifest manifest, DeliveryOrderBoard board, DeliveryOrderAction action, long currentTick) {
        int changed = 0;
        for (FreightManifestLine line : manifest.getLines()) {
            DeliveryOrderLineState state = board.get(line.getLineId());
            if (state == null) {
                continue;
            }
            boolean lineChanged = switch (action) {
                case HIDE_COMPLETED, SHOW_HIDDEN, RESET_COMPLETED -> applyOrderAction(line, state, action, currentTick);
                default -> false;
            };
            if (lineChanged) {
                changed++;
            }
        }
        return changed;
    }

    public static String describeLine(FreightManifestLine line) {
        return line.getOperation().getDisplayName() + " " + formatAmount(line.getCargoKind(), line.getAmount()) + " " + line.getDisplayName();
    }

    private static FreightManifestEvaluationResult evaluatePreview(FreightManifest manifest, FreightTransferProgress progress) {
        TrainCargoSnapshot movedToStation = progress.getMovedToStation();
        TrainCargoSnapshot movedToTrain = progress.getMovedToTrain();
        Map<FreightManifestResourceKey, Integer> expectedToStation = buildExpectedAmounts(manifest, ManifestOperation.UNLOAD);
        Map<FreightManifestResourceKey, Integer> expectedToTrain = buildExpectedAmounts(manifest, ManifestOperation.LOAD);

        for (Map.Entry<FreightManifestResourceKey, Integer> entry : movedToStation.getAmounts().entrySet()) {
            if (expectedToStation.getOrDefault(entry.getKey(), 0) == 0) {
                return FreightManifestEvaluationResult.waiting("Unexpected unload to station: " + describeResource(entry.getKey()));
            }
        }

        for (Map.Entry<FreightManifestResourceKey, Integer> entry : movedToTrain.getAmounts().entrySet()) {
            if (expectedToTrain.getOrDefault(entry.getKey(), 0) == 0) {
                return FreightManifestEvaluationResult.waiting("Unexpected load from station: " + describeResource(entry.getKey()));
            }
        }

        for (Map.Entry<FreightManifestResourceKey, Integer> entry : expectedToStation.entrySet()) {
            FreightManifestResourceKey key = entry.getKey();
            int expectedAmount = entry.getValue();
            int actualAmount = movedToStation.getAmount(key);
            if (actualAmount > expectedAmount) {
                return FreightManifestEvaluationResult.waiting("Preview over-unloaded "
                    + formatAmount(key.getCargoKind(), actualAmount - expectedAmount) + " " + describeResource(key));
            }
            if (actualAmount < expectedAmount) {
                return FreightManifestEvaluationResult.waiting("Preview needs unload " + formatAmount(key.getCargoKind(), expectedAmount - actualAmount)
                    + " " + describeResource(key));
            }
        }

        for (Map.Entry<FreightManifestResourceKey, Integer> entry : expectedToTrain.entrySet()) {
            FreightManifestResourceKey key = entry.getKey();
            int expectedAmount = entry.getValue();
            int actualAmount = movedToTrain.getAmount(key);
            if (actualAmount > expectedAmount) {
                return FreightManifestEvaluationResult.waiting("Preview over-loaded "
                    + formatAmount(key.getCargoKind(), actualAmount - expectedAmount) + " " + describeResource(key));
            }
            if (actualAmount < expectedAmount) {
                return FreightManifestEvaluationResult.waiting("Preview needs load " + formatAmount(key.getCargoKind(), expectedAmount - actualAmount)
                    + " " + describeResource(key));
            }
        }

        return FreightManifestEvaluationResult.complete();
    }

    // stray cargo is reported but never holds a train
    private static FreightManifestEvaluationResult evaluateCompletion(FreightManifest manifest, DeliveryOrderBoard arrivalBoard,
                                                                     DeliveryOrderBoard persistentBoard, String unaccountedNote) {
        List<String> outstanding = new ArrayList<>();
        for (FreightManifestLine line : manifest.getLines()) {
            DeliveryOrderLineState state = getStateForLine(line, arrivalBoard, persistentBoard, 0L, false);
            if (state == null || !state.isComplete(line.getAmount())) {
                outstanding.add(buildWaitingMessage(line, state));
            }
        }
        if (outstanding.isEmpty()) {
            return FreightManifestEvaluationResult.complete();
        }
        String waiting = outstanding.size() == 1
            ? outstanding.get(0)
            : outstanding.get(0) + " (+" + (outstanding.size() - 1) + " more)";
        return FreightManifestEvaluationResult.waiting(
            unaccountedNote == null || unaccountedNote.isEmpty() ? waiting : waiting + " | " + unaccountedNote);
    }

    private static String describeUnaccountedCargo(FreightManifest manifest, TrainCargoSnapshot remainingToStation,
                                                   TrainCargoSnapshot remainingToTrain) {
        Set<FreightManifestResourceKey> configuredUnloads = buildConfiguredKeys(manifest, ManifestOperation.UNLOAD);
        Set<FreightManifestResourceKey> configuredLoads = buildConfiguredKeys(manifest, ManifestOperation.LOAD);
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : remainingToStation.getAmounts().entrySet()) {
            if (entry.getValue() > 0) {
                String amount = formatAmount(entry.getKey().getCargoKind(), entry.getValue());
                return configuredUnloads.contains(entry.getKey())
                    ? "unaccounted unload of " + amount + " " + describeResource(entry.getKey())
                    : "unexpected unload of " + amount + " " + describeResource(entry.getKey());
            }
        }
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : remainingToTrain.getAmounts().entrySet()) {
            if (entry.getValue() > 0) {
                String amount = formatAmount(entry.getKey().getCargoKind(), entry.getValue());
                return configuredLoads.contains(entry.getKey())
                    ? "unaccounted load of " + amount + " " + describeResource(entry.getKey())
                    : "unexpected load of " + amount + " " + describeResource(entry.getKey());
            }
        }
        return "";
    }

    private static String buildWaitingMessage(FreightManifestLine line, @Nullable DeliveryOrderLineState state) {
        FreightManifestResourceKey key = line.getResourceKey();
        if (key == null) {
            return "Waiting for a valid cargo selection.";
        }
        int delivered = state == null ? 0 : Math.min(state.getDeliveredAmount(), Math.max(0, line.getAmount()));
        int remaining = Math.max(0, line.getAmount() - delivered);
        String action = line.getOperation() == ManifestOperation.UNLOAD ? "unload" : "load";
        String progress = delivered > 0
            ? " (" + formatAmount(line.getCargoKind(), delivered) + " / " + formatAmount(line.getCargoKind(), line.getAmount()) + " this cycle)"
            : "";
        return "Waiting to " + action + " " + formatAmount(line.getCargoKind(), remaining) + " " + describeResource(key) + progress;
    }

    private static boolean shouldResetPersistentLine(FreightManifestLine line, DeliveryOrderLineState state, long currentTick) {
        return switch (line.getResetMode()) {
            case EVERY_ARRIVAL, ONCE -> false;
            case MINECRAFT_DAY -> currentTick / 24000L > state.getCycleStartTick() / 24000L;
            case EVERY_X_MINUTES, EVERY_X_HOURS -> {
                long intervalTicks = line.getResetMode().getIntervalTicks(line.getResetIntervalValue());
                yield intervalTicks > 0L && currentTick - state.getCycleStartTick() >= intervalTicks;
            }
        };
    }

    private static long getTicksUntilReset(FreightManifestLine line, @Nullable DeliveryOrderLineState state, long currentTick) {
        return switch (line.getResetMode()) {
            case EVERY_ARRIVAL, ONCE -> -1L;
            case MINECRAFT_DAY -> {
                long dayTime = Math.floorMod(currentTick, 24000L);
                yield dayTime == 0L ? 24000L : 24000L - dayTime;
            }
            case EVERY_X_MINUTES, EVERY_X_HOURS -> {
                long intervalTicks = line.getResetMode().getIntervalTicks(line.getResetIntervalValue());
                long cycleStart = state == null ? currentTick : state.getCycleStartTick();
                yield Math.max(0L, cycleStart + intervalTicks - currentTick);
            }
        };
    }

    private static String buildLineStatus(FreightManifestLine line, @Nullable DeliveryOrderLineState state, boolean complete,
                                          boolean dismissed, boolean trainPresent, boolean arrivalTrackingActive,
                                          boolean historic, long currentTick) {
        if (!line.hasSelection() || line.getAmount() <= 0) {
            return "Line needs setup";
        }
        if (complete) {
            if (historic) {
                return dismissed ? "Delivered last visit (dismissed)" : "Delivered last visit";
            }
            return switch (line.getResetMode()) {
                case EVERY_ARRIVAL -> dismissed ? "Complete for this arrival (dismissed)" : "Complete for this arrival";
                case ONCE -> dismissed ? "Fulfilled (dismissed)" : "Fulfilled - one-shot order";
                case MINECRAFT_DAY -> dismissed ? "Complete until next day (dismissed)" : "Complete until next day";
                case EVERY_X_MINUTES, EVERY_X_HOURS -> dismissed
                    ? "Complete until refresh (dismissed)"
                    : "Complete until refresh";
            };
        }
        int delivered = state == null ? 0 : Math.min(state.getDeliveredAmount(), Math.max(0, line.getAmount()));
        if (delivered > 0) {
            String figures = formatAmount(line.getCargoKind(), delivered) + " / " + formatAmount(line.getCargoKind(), line.getAmount());
            return historic ? "Last visit: " + figures + " - short" : "In progress: " + figures;
        }

        if (trainPresent && !arrivalTrackingActive) {
            return line.getOperation() == ManifestOperation.UNLOAD
                ? "Unloading - add Cargo Manifest Met to hold the train"
                : "Loading - add Cargo Manifest Met to hold the train";
        }
        if (line.getResetMode() == DeliveryOrderResetMode.EVERY_ARRIVAL) {
            return "Waiting for next arrival";
        }
        long ticksUntilReset = getTicksUntilReset(line, state, currentTick);
        if (ticksUntilReset > 0L && line.getResetMode().isPersistent()) {
            return "Open this cycle for " + formatTicks(ticksUntilReset);
        }
        return line.getOperation() == ManifestOperation.UNLOAD ? "Waiting to unload" : "Waiting to load";
    }

    private static String buildBoardSummary(int totalOrders, int completedOrders, int dismissedOrders,
                                            FreightStationCargoView cargoView, boolean arrivalTrackingActive) {
        if (totalOrders <= 0) {
            return "No delivery orders configured.";
        }
        String summary = completedOrders + "/" + totalOrders + " complete";
        if (dismissedOrders > 0) {
            summary += " | " + dismissedOrders + " dismissed";
        }
        if (cargoView != null && cargoView.isTrainPresent()) {
            summary += arrivalTrackingActive ? " | train held for manifest" : " | train present, not held";
        }
        return summary;
    }

    @Nullable
    private static DeliveryOrderLineState getStateForLine(FreightManifestLine line, @Nullable DeliveryOrderBoard arrivalBoard,
                                                          DeliveryOrderBoard persistentBoard, long currentTick, boolean createMissing) {
        if (line.getResetMode().isPersistent()) {
            return createMissing ? persistentBoard.getOrCreate(line.getLineId(), currentTick) : persistentBoard.get(line.getLineId());
        }
        if (arrivalBoard == null) {
            return null;
        }
        return createMissing ? arrivalBoard.getOrCreate(line.getLineId(), currentTick) : arrivalBoard.get(line.getLineId());
    }

    private static AllocationResult allocateStepDeltas(FreightManifest manifest, DeliveryOrderBoard arrivalBoard,
                                                       DeliveryOrderBoard persistentBoard, TrainCargoSnapshot stepToStation,
                                                       TrainCargoSnapshot stepToTrain, long currentTick,
                                                       @Nullable FreightManifestStationState stationState) {
        TrainCargoSnapshot remainingToStation = stepToStation.copy();
        TrainCargoSnapshot remainingToTrain = stepToTrain.copy();
        boolean arrivalChanged = false;
        boolean persistentChanged = false;
        for (FreightManifestLine line : manifest.getLines()) {
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null || line.getAmount() <= 0) {
                continue;
            }
            DeliveryOrderLineState state = getStateForLine(line, arrivalBoard, persistentBoard, currentTick, true);
            if (state == null || state.isComplete(line.getAmount())) {
                continue;
            }
            TrainCargoSnapshot source = line.getOperation() == ManifestOperation.UNLOAD ? remainingToStation : remainingToTrain;
            int available = source.getAmount(key);
            if (available <= 0) {
                continue;
            }
            int leftover = state.addDelivered(available, line.getAmount(), currentTick);
            int consumed = available - leftover;
            if (consumed <= 0) {
                continue;
            }
            source.addAmount(key, -consumed);
            if (line.getResetMode().isPersistent()) {
                persistentChanged = true;
            } else {
                arrivalChanged = true;
            }
            if (state.isComplete(line.getAmount()) && stationState != null) {
                boolean oneShot = DeliveryOrderLifecyclePolicy.archivesOnCompletion(line.getResetMode());
                if (oneShot) {
                    state.setDismissed(true);
                }
                String train = stationState.getVisitTrainName();
                String detail = describeLine(line) + (train.isEmpty() ? "" : " | " + train);
                stationState.addHistory(DeliveryOrderHistoryType.COMPLETED, currentTick,
                    oneShot ? "One-shot Archived" : "Completed", detail);
            }
        }
        return new AllocationResult(remainingToStation, remainingToTrain, arrivalChanged, persistentChanged);
    }

    private static Map<FreightManifestResourceKey, Integer> buildExpectedAmounts(FreightManifest manifest, ManifestOperation operation) {
        Map<FreightManifestResourceKey, Integer> expected = new HashMap<>();
        for (FreightManifestLine line : manifest.getLines()) {
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null || line.getOperation() != operation) {
                continue;
            }
            expected.merge(key, line.getAmount(), Integer::sum);
        }
        return expected;
    }

    private static Set<FreightManifestResourceKey> buildConfiguredKeys(FreightManifest manifest, ManifestOperation operation) {
        Set<FreightManifestResourceKey> keys = new HashSet<>();
        for (FreightManifestLine line : manifest.getLines()) {
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null || line.getOperation() != operation) {
                continue;
            }
            keys.add(key);
        }
        return keys;
    }

    private static void applyStepDelta(TrainCargoSnapshot previousSnapshot, TrainCargoSnapshot currentSnapshot,
                                       TrainCargoSnapshot stepToStation, TrainCargoSnapshot stepToTrain) {
        Set<FreightManifestResourceKey> keys = new HashSet<>();
        keys.addAll(previousSnapshot.getAmounts().keySet());
        keys.addAll(currentSnapshot.getAmounts().keySet());
        for (FreightManifestResourceKey key : keys) {
            int delta = currentSnapshot.getAmount(key) - previousSnapshot.getAmount(key);
            if (delta > 0) {
                stepToTrain.addAmount(key, delta);
            } else if (delta < 0) {
                stepToStation.addAmount(key, -delta);
            }
        }
    }

    @Nullable
    private static CompoundTag findActiveManifestContext(@Nullable ScheduleRuntime runtime) {
        if (runtime == null || runtime.state != ScheduleRuntime.State.POST_TRANSIT || runtime.getSchedule() == null) {
            return null;
        }
        if (runtime.currentEntry < 0 || runtime.currentEntry >= runtime.getSchedule().entries.size()) {
            return null;
        }
        var groups = runtime.getSchedule().entries.get(runtime.currentEntry).conditions;
        int limit = Math.min(groups.size(), Math.min(runtime.conditionContext.size(), runtime.conditionProgress.size()));
        for (int groupIndex = 0; groupIndex < limit; groupIndex++) {
            List<ScheduleWaitCondition> group = groups.get(groupIndex);
            Integer progress = runtime.conditionProgress.get(groupIndex);
            if (progress == null || progress < 0 || progress >= group.size()) {
                continue;
            }
            if (group.get(progress) instanceof CargoManifestMetCondition) {
                return runtime.conditionContext.get(groupIndex);
            }
        }
        return null;
    }

    public static String describeResource(FreightManifestResourceKey key) {
        if (key.getCargoKind() == ManifestCargoKind.ITEM) {
            Optional<net.minecraft.world.item.Item> item = BuiltInRegistries.ITEM.getOptional(key.getResourceId());
            return item.map(value -> new ItemStack(value).getHoverName().getString()).orElse(key.getResourceId().toString());
        }
        Optional<net.minecraft.world.level.material.Fluid> fluid = BuiltInRegistries.FLUID.getOptional(key.getResourceId());
        return fluid.map(value -> new FluidStack(value, 1000).getDisplayName().getString()).orElse(key.getResourceId().toString());
    }

    public static String formatAmount(ManifestCargoKind cargoKind, int amount) {
        if (cargoKind == ManifestCargoKind.FLUID) {
            return amount + cargoKind.getUnitLabel();
        }
        return Integer.toString(amount);
    }

    public static String formatTicks(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes + ":" + String.format("%02d", remainingSeconds);
    }

    private static String getResetHistoryReason(FreightManifestLine line) {
        return switch (line.getResetMode()) {
            case MINECRAFT_DAY -> "new day";
            case EVERY_X_MINUTES -> "every " + Math.max(1, line.getResetIntervalValue()) + " minutes";
            case EVERY_X_HOURS -> "every " + Math.max(1, line.getResetIntervalValue()) + " hours";
            case ONCE -> "once";
            case EVERY_ARRIVAL -> "arrival";
        };
    }

    private record AllocationResult(TrainCargoSnapshot remainingToStation, TrainCargoSnapshot remainingToTrain,
                                    boolean arrivalChanged, boolean persistentChanged) {
    }

    private record LineDuplicateKey(ManifestOperation operation, FreightManifestResourceKey resourceKey) {
    }
}
