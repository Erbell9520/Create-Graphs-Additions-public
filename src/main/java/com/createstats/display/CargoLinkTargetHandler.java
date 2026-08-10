package com.createstats.display;

import com.createstats.CreateStats;
import com.createstats.blockentity.DataDialBE;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.data.MetricType;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightManifestValidationResult;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** [1] */
public final class CargoLinkTargetHandler {

    private static final String SOURCE_TYPE_DISPATCH = "Dispatch Board";
    private static final String SOURCE_TYPE_MANIFEST = "Freight Manifest";

    private CargoLinkTargetHandler() {}

    /**
     * Returns true if the active display source is a CargoLinkSource.
     */
    public static boolean isCargoLinkSource(@Nullable DisplayLinkContext context) {
        return context != null
            && context.blockEntity() != null
            && context.blockEntity().activeSource instanceof CargoLinkSource;
    }

    // rich target: Stats Display Panel, full freight state

    /**
     * Handles cargo link data for StatsDisplayPanelBE.
     * DispatchBoardBE sources are registered as freight manifest links (rich data).
     * FreightManifestClipboardBE sources are already handled by existing code.
     *
     * @return true if the source was handled, false if it should fall through
     */
    public static boolean handleForPanel(StatsDisplayPanelBE panel, BlockPos sourcePos, BlockEntity sourceBE) {
        if (sourceBE instanceof DispatchBoardBE board) {
            DispatchBoardBE controller = board.getControllerBE();
            panel.registerOrUpdateSource(sourcePos, SOURCE_TYPE_DISPATCH);

            // Provide cargo metrics as named data channels on the panel
            CargoSnapshot snapshot = captureFromDispatchBoard(controller);
            pushCargoMetricsToPanel(panel, sourcePos, SOURCE_TYPE_DISPATCH, snapshot);
            return true;
        }
        // FreightManifestClipboardBE is already handled by registerOrUpdateFreightManifest
        return false;
    }

    // rich target: Holographic Projector, limited summary

    /**
     * Handles cargo link data for HolographicProjectorBE.
     * Provides a limited cargo summary (completion, train state, line counts).
     */
    public static void handleForProjector(HolographicProjectorBE projector, BlockPos sourcePos, BlockEntity sourceBE) {
        CargoSnapshot snapshot = captureSnapshot(sourceBE);
        if (snapshot == null) {
            return;
        }
        String sourceType = snapshot.sourceType;
        projector.onAutoData(sourcePos, sourceType, "completion_pct", "Completion", snapshot.completionPercent, MetricType.FILL_LEVEL);
        projector.onAutoData(sourcePos, sourceType, "train_present", "Train Present", snapshot.trainPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        projector.onAutoData(sourcePos, sourceType, "total_orders", "Total Orders", snapshot.totalOrders, MetricType.ITEM_COUNT);
        projector.onAutoData(sourcePos, sourceType, "completed_orders", "Completed", snapshot.completedOrders, MetricType.ITEM_COUNT);
        projector.onAutoData(sourcePos, sourceType, "manifest_lines", "Manifest Lines", snapshot.manifestLines, MetricType.ITEM_COUNT);
    }

    // compact target: Status Tower Light, berth/completion state only

    /**
     * Handles cargo link data for StatusTowerLightBE.
     * Only provides berth state and completion %, no dense manifest rows.
     */
    public static void handleForTowerLight(StatusTowerLightBE towerLight, BlockPos sourcePos, BlockEntity sourceBE) {
        CargoSnapshot snapshot = captureSnapshot(sourceBE);
        if (snapshot == null) {
            return;
        }
        towerLight.onReceiveData(sourcePos, snapshot.sourceType, 0,
            snapshot.completionPercent, MetricType.FILL_LEVEL,
            "Completion: " + String.format("%.0f%%", snapshot.completionPercent));
        towerLight.onReceiveData(sourcePos, snapshot.sourceType, 1,
            snapshot.trainPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG,
            "Train: " + (snapshot.trainPresent ? "Present" : "Absent"));
    }

    // compact target: Data Dial, completion % only

    /**
     * Handles cargo link data for DataDialBE.
     * Only provides completion % and train state, no manifest row data.
     */
    public static void handleForDataDial(DataDialBE dial, BlockPos sourcePos, BlockEntity sourceBE) {
        CargoSnapshot snapshot = captureSnapshot(sourceBE);
        if (snapshot == null) {
            return;
        }
        Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        metrics.put("completion_pct", new InstrumentSourceHelper.MetricSample(
            "completion_pct", "Completion", snapshot.completionPercent, MetricType.FILL_LEVEL));
        metrics.put("train_present", new InstrumentSourceHelper.MetricSample(
            "train_present", "Train Present", snapshot.trainPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG));
        dial.replaceAutoData(sourcePos, snapshot.sourceType, metrics);
    }

    // compact target: Mini Display, compact summary only

    /**
     * Handles cargo link data for MiniDisplayBE.
     * Provides completion %, train state, and manifest line count, no dense rows.
     */
    public static void handleForMiniDisplay(MiniDisplayBE miniDisplay, BlockPos sourcePos, BlockEntity sourceBE) {
        CargoSnapshot snapshot = captureSnapshot(sourceBE);
        if (snapshot == null) {
            return;
        }
        Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        metrics.put("completion_pct", new InstrumentSourceHelper.MetricSample(
            "completion_pct", "Completion", snapshot.completionPercent, MetricType.FILL_LEVEL));
        metrics.put("train_present", new InstrumentSourceHelper.MetricSample(
            "train_present", "Train Present", snapshot.trainPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG));
        metrics.put("manifest_lines", new InstrumentSourceHelper.MetricSample(
            "manifest_lines", "Manifest Lines", snapshot.manifestLines, MetricType.ITEM_COUNT));
        metrics.put("completed_orders", new InstrumentSourceHelper.MetricSample(
            "completed_orders", "Completed", snapshot.completedOrders, MetricType.ITEM_COUNT));
        miniDisplay.replaceAutoData(sourcePos, snapshot.sourceType, metrics);
    }

    // rejected target: Stats Recorder, cargo is not time-series metric data

    /**
     * Cargo data is not meaningful for time-series recording.
     * Logs and returns without pushing data.
     */
    public static void rejectForRecorder(BlockPos sourcePos) {
        CreateStats.LOGGER.debug("[CargoLink] Rejecting cargo data for StatsRecorder at {}; cargo is not time-series metric data", sourcePos);
    }

    // snapshot capture

    @Nullable
    private static CargoSnapshot captureSnapshot(BlockEntity sourceBE) {
        if (sourceBE instanceof DispatchBoardBE board) {
            return captureFromDispatchBoard(board.getControllerBE());
        }
        if (sourceBE instanceof FreightManifestClipboardBE clipboard) {
            return captureFromClipboard(clipboard);
        }
        return null;
    }

    private static CargoSnapshot captureFromDispatchBoard(DispatchBoardBE controller) {
        FreightManifest manifest = controller.getManifest();
        DeliveryOrderBoardView deliveryBoard = controller.getDeliveryBoard();
        FreightStationCargoView cargoView = controller.getCargoView();

        int total = deliveryBoard.getTotalOrders();
        int completed = deliveryBoard.getCompletedOrders();
        float completionPct = total > 0 ? (completed * 100.0f / total) : 0.0f;
        int loadLines = countLines(manifest, ManifestOperation.LOAD);
        int unloadLines = countLines(manifest, ManifestOperation.UNLOAD);

        return new CargoSnapshot(
            SOURCE_TYPE_DISPATCH,
            completionPct,
            cargoView.isTrainPresent(),
            cargoView.getTrainName(),
            manifest.getLines().size(),
            total,
            completed,
            deliveryBoard.getDismissedOrders(),
            loadLines,
            unloadLines
        );
    }

    @Nullable
    private static CargoSnapshot captureFromClipboard(FreightManifestClipboardBE clipboard) {
        FreightStationLink stationLink = clipboard.getStationLink();
        if (!stationLink.isLinked()) {
            return new CargoSnapshot(SOURCE_TYPE_MANIFEST, 0, false, "", 0, 0, 0, 0, 0, 0);
        }
        if (clipboard.getLevel() == null || clipboard.getLevel().getServer() == null) {
            return new CargoSnapshot(SOURCE_TYPE_MANIFEST, 0, false, "", 0, 0, 0, 0, 0, 0);
        }

        ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(
            clipboard.getLevel().getServer(), stationLink);
        FreightManifest manifest = new FreightManifest();
        if (stationLevel != null && stationLink.getStationId() != null) {
            FreightManifest stored = FreightManifestSavedData.get(stationLevel)
                .getManifest(stationLink.getStationId());
            if (stored != null) {
                manifest = stored;
            }
        }

        DeliveryOrderBoardView deliveryBoard = FreightManifestStationResolver.captureDeliveryBoard(
            clipboard.getLevel().getServer(), stationLink);
        FreightStationCargoView cargoView = FreightManifestStationResolver.captureCargoView(
            clipboard.getLevel().getServer(), stationLink);

        int total = deliveryBoard.getTotalOrders();
        int completed = deliveryBoard.getCompletedOrders();
        float completionPct = total > 0 ? (completed * 100.0f / total) : 0.0f;
        int loadLines = countLines(manifest, ManifestOperation.LOAD);
        int unloadLines = countLines(manifest, ManifestOperation.UNLOAD);

        return new CargoSnapshot(
            SOURCE_TYPE_MANIFEST,
            completionPct,
            cargoView.isTrainPresent(),
            cargoView.getTrainName(),
            manifest.getLines().size(),
            total,
            completed,
            deliveryBoard.getDismissedOrders(),
            loadLines,
            unloadLines
        );
    }

    // panel helper: push cargo metrics as named data channels

    private static void pushCargoMetricsToPanel(StatsDisplayPanelBE panel, BlockPos sourcePos,
                                                 String sourceType, CargoSnapshot snapshot) {
        panel.onReceiveData(sourcePos, sourceType, 0,
            snapshot.completionPercent, MetricType.FILL_LEVEL,
            "Completion: " + String.format("%.0f%%", snapshot.completionPercent));
        panel.onReceiveData(sourcePos, sourceType, 1,
            snapshot.trainPresent ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG,
            "Train: " + (snapshot.trainPresent ? snapshot.trainName : "Absent"));
        panel.onReceiveData(sourcePos, sourceType, 2,
            snapshot.totalOrders, MetricType.ITEM_COUNT,
            "Orders: " + snapshot.completedOrders + "/" + snapshot.totalOrders);
        panel.onReceiveData(sourcePos, sourceType, 3,
            snapshot.manifestLines, MetricType.ITEM_COUNT,
            "Lines: " + snapshot.manifestLines + " (L:" + snapshot.loadLines + " U:" + snapshot.unloadLines + ")");
    }

    // helpers

    private static int countLines(FreightManifest manifest, ManifestOperation operation) {
        int count = 0;
        for (var line : manifest.getLines()) {
            if (line.getOperation() == operation) {
                count++;
            }
        }
        return count;
    }

    /**
     * Constrained cargo snapshot, only the fields that compact targets may consume.
     */
    private static class CargoSnapshot {
        final String sourceType;
        final float completionPercent;
        final boolean trainPresent;
        final String trainName;
        final int manifestLines;
        final int totalOrders;
        final int completedOrders;
        final int dismissedOrders;
        final int loadLines;
        final int unloadLines;

        CargoSnapshot(String sourceType, float completionPercent, boolean trainPresent, String trainName,
                      int manifestLines, int totalOrders, int completedOrders, int dismissedOrders,
                      int loadLines, int unloadLines) {
            this.sourceType = sourceType;
            this.completionPercent = completionPercent;
            this.trainPresent = trainPresent;
            this.trainName = trainName != null ? trainName : "";
            this.manifestLines = manifestLines;
            this.totalOrders = totalOrders;
            this.completedOrders = completedOrders;
            this.dismissedOrders = dismissedOrders;
            this.loadLines = loadLines;
            this.unloadLines = unloadLines;
        }
    }
}
