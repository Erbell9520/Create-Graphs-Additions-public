package com.createstats.dispatch;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.StationTrainRoster;
import com.createstats.train.manifest.TrainCargoSnapshot;
import com.createstats.train.manifest.TrainSummarySnapshot;
import com.createstats.train.manifest.TrainVisitRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DispatchBoardSnapshot {
    private final BoardLayoutSpec layout;
    private final BoardHeaderState headerState;
    private final String stationName;
    private final String trainName;
    private final String summaryStatus;
    private final boolean trainPresent;
    private final boolean arrivalTracking;

    private final List<DispatchRowEntry> visibleRows;
    private final int totalRowCount;
    private final int completedRowCount;
    private final int dismissedRowCount;

    private final List<CargoSummaryEntry> cargoSummary;

    private final List<DeliveryOrderHistoryEntry> visibleHistory;

    private final List<TrainSummarySnapshot> rosterEntries;

    private final List<TrainVisitRecord> visibleVisits;

    private final long captureTick;

    private DispatchBoardSnapshot(BoardLayoutSpec layout, BoardHeaderState headerState,
                                  String stationName, String trainName, String summaryStatus,
                                  boolean trainPresent, boolean arrivalTracking,
                                  List<DispatchRowEntry> visibleRows, int totalRowCount,
                                  int completedRowCount, int dismissedRowCount,
                                  List<CargoSummaryEntry> cargoSummary,
                                  List<DeliveryOrderHistoryEntry> visibleHistory,
                                  List<TrainSummarySnapshot> rosterEntries,
                                  List<TrainVisitRecord> visibleVisits,
                                  long captureTick) {
        this.layout = layout;
        this.headerState = headerState;
        this.stationName = stationName;
        this.trainName = trainName;
        this.summaryStatus = summaryStatus;
        this.trainPresent = trainPresent;
        this.arrivalTracking = arrivalTracking;
        this.visibleRows = Collections.unmodifiableList(visibleRows);
        this.totalRowCount = totalRowCount;
        this.completedRowCount = completedRowCount;
        this.dismissedRowCount = dismissedRowCount;
        this.cargoSummary = Collections.unmodifiableList(cargoSummary);
        this.visibleHistory = Collections.unmodifiableList(visibleHistory);
        this.rosterEntries = Collections.unmodifiableList(rosterEntries);
        this.visibleVisits = Collections.unmodifiableList(visibleVisits);
        this.captureTick = captureTick;
    }

    public static DispatchBoardSnapshot assemble(int width, int height,
                                                  FreightStationLink link,
                                                  DeliveryOrderBoardView board,
                                                  FreightManifest manifest,
                                                  FreightStationCargoView cargoView,
                                                  StationTrainRoster roster,
                                                  List<TrainVisitRecord> visitHistory) {
        BoardLayoutSpec layout = BoardLayoutSpec.compute(width, height);
        BoardHeaderState headerState = BoardHeaderState.derive(link, board);
        String stationName = link.isLinked() ? link.getStationName() : "";
        boolean trainPresent = board.isTrainPresent();

        List<DispatchRowEntry> allRows = new ArrayList<>();
        for (int i = 0; i < board.getLineViews().size(); i++) {
            DeliveryOrderLineView line = board.getLineViews().get(i);
            DispatchRowState rowState = DispatchRowState.derive(line, trainPresent);
            allRows.add(new DispatchRowEntry(line, rowState, i));
        }

        allRows.removeIf(row -> row.getLineView().isDismissed());
        allRows.sort(Comparator
                .comparing((DispatchRowEntry row) -> row.getLineView().isComplete())
                .thenComparingInt(DispatchRowEntry::getRowIndex));

        int rowCap = layout.getDispatchRowCapacity();
        List<DispatchRowEntry> visibleRows = allRows.size() <= rowCap
                ? allRows
                : new ArrayList<>(allRows.subList(0, rowCap));

        List<CargoSummaryEntry> cargoSummary = buildCargoSummary(cargoView, layout.getCargoSummarySlots());

        List<DeliveryOrderHistoryEntry> historyEntries = board.getHistoryEntries();
        int histCap = layout.getHistoryEntryCapacity();
        List<DeliveryOrderHistoryEntry> visibleHistory = historyEntries.size() <= histCap
                ? new ArrayList<>(historyEntries)
                : new ArrayList<>(historyEntries.subList(0, histCap));

        List<TrainSummarySnapshot> rosterList = new ArrayList<>();
        if (layout.hasRosterRegion()) {
            if (roster.getPresentTrain() != null) {
                rosterList.add(roster.getPresentTrain());
            }
            for (TrainSummarySnapshot ts : roster.getAssociatedTrains()) {
                if (rosterList.size() >= layout.getRosterCapacity()) break;
                rosterList.add(ts);
            }
        }

        List<TrainVisitRecord> visibleVisits = new ArrayList<>();
        if (layout.getTier().showsVisitTimeline()) {
            int visitCap = Math.min(visitHistory.size(), layout.getHistoryEntryCapacity());
            for (int i = 0; i < visitCap; i++) {
                visibleVisits.add(visitHistory.get(i));
            }
        }

        return new DispatchBoardSnapshot(
                layout, headerState,
                stationName, board.getTrainName(), board.getSummaryStatus(),
                trainPresent, board.isArrivalTrackingActive(),
                visibleRows, board.getTotalOrders(), board.getCompletedOrders(),
                board.getDismissedOrders(),
                cargoSummary, visibleHistory, rosterList, visibleVisits,
                board.getCurrentTick());
    }

    private static List<CargoSummaryEntry> buildCargoSummary(FreightStationCargoView cargoView, int maxSlots) {
        if (maxSlots <= 0 || !cargoView.isTrainPresent()) {
            return List.of();
        }
        TrainCargoSnapshot totals = cargoView.getFreightTotals();
        List<Map.Entry<FreightManifestResourceKey, Integer>> sorted = new ArrayList<>(totals.getAmounts().entrySet());
        sorted.sort(Comparator.<Map.Entry<FreightManifestResourceKey, Integer>, Integer>comparing(Map.Entry::getValue).reversed());

        List<CargoSummaryEntry> result = new ArrayList<>();
        for (int i = 0; i < Math.min(sorted.size(), maxSlots); i++) {
            Map.Entry<FreightManifestResourceKey, Integer> e = sorted.get(i);
            result.add(new CargoSummaryEntry(e.getKey(), e.getValue()));
        }
        return result;
    }

    public BoardLayoutSpec getLayout() {
        return this.layout;
    }

    public BoardTier getTier() {
        return this.layout.getTier();
    }

    public BoardHeaderState getHeaderState() {
        return this.headerState;
    }

    public String getStationName() {
        return this.stationName;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public String getSummaryStatus() {
        return this.summaryStatus;
    }

    public boolean isTrainPresent() {
        return this.trainPresent;
    }

    public boolean isArrivalTracking() {
        return this.arrivalTracking;
    }

    public List<DispatchRowEntry> getVisibleRows() {
        return this.visibleRows;
    }

    public int getTotalRowCount() {
        return this.totalRowCount;
    }

    public int getCompletedRowCount() {
        return this.completedRowCount;
    }

    public int getDismissedRowCount() {
        return this.dismissedRowCount;
    }

    public boolean hasOverflowRows() {
        return this.visibleRows.size() >= this.layout.getDispatchRowCapacity()
                && this.totalRowCount - this.dismissedRowCount > this.visibleRows.size();
    }

    public List<CargoSummaryEntry> getCargoSummary() {
        return this.cargoSummary;
    }

    public List<DeliveryOrderHistoryEntry> getVisibleHistory() {
        return this.visibleHistory;
    }

    public List<TrainSummarySnapshot> getRosterEntries() {
        return this.rosterEntries;
    }

    public List<TrainVisitRecord> getVisibleVisits() {
        return this.visibleVisits;
    }

    public long getCaptureTick() {
        return this.captureTick;
    }

    public String getCompletionLabel() {
        if (this.totalRowCount <= 0) {
            return "No orders";
        }

        return this.completedRowCount + "/" + this.totalRowCount + " complete";
    }

    public int countRowsInState(DispatchRowState state) {
        int count = 0;
        for (DispatchRowEntry row : this.visibleRows) {
            if (row.getState() == state) {
                count++;
            }
        }
        return count;
    }

    public boolean hasExceptions() {
        for (DispatchRowEntry row : this.visibleRows) {
            if (row.getState() == DispatchRowState.INVALID
                    || row.getState() == DispatchRowState.NEEDS_SETUP) {
                return true;
            }
        }
        return false;
    }
}
