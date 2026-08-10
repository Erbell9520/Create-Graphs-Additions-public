package com.createstats.train.manifest;

public class FreightTransferProgress {

    private final TrainCargoSnapshot currentSnapshot;
    private final TrainCargoSnapshot movedToStation;
    private final TrainCargoSnapshot movedToTrain;

    public FreightTransferProgress(TrainCargoSnapshot currentSnapshot, TrainCargoSnapshot movedToStation, TrainCargoSnapshot movedToTrain) {
        this.currentSnapshot = currentSnapshot.copy();
        this.movedToStation = movedToStation.copy();
        this.movedToTrain = movedToTrain.copy();
    }

    public static FreightTransferProgress empty(TrainCargoSnapshot currentSnapshot) {
        return new FreightTransferProgress(currentSnapshot, new TrainCargoSnapshot(), new TrainCargoSnapshot());
    }

    public TrainCargoSnapshot getCurrentSnapshot() {
        return this.currentSnapshot.copy();
    }

    public TrainCargoSnapshot getMovedToStation() {
        return this.movedToStation.copy();
    }

    public TrainCargoSnapshot getMovedToTrain() {
        return this.movedToTrain.copy();
    }
}
