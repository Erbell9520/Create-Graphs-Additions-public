package com.createstats.train.manifest;

public class FreightManifestEvaluationResult {
    private final boolean complete;
    private final String statusMessage;

    private FreightManifestEvaluationResult(boolean complete, String statusMessage) {
        this.complete = complete;
        this.statusMessage = statusMessage;
    }

    public static FreightManifestEvaluationResult complete() {
        return new FreightManifestEvaluationResult(true, "Cargo manifest complete");
    }

    public static FreightManifestEvaluationResult waiting(String statusMessage) {
        return new FreightManifestEvaluationResult(false, statusMessage);
    }

    public boolean isComplete() {
        return this.complete;
    }

    public String getStatusMessage() {
        return this.statusMessage;
    }
}
