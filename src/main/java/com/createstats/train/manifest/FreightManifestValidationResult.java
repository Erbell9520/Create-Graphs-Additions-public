package com.createstats.train.manifest;

public class FreightManifestValidationResult {
    private final boolean valid;
    private final String message;

    private FreightManifestValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static FreightManifestValidationResult ok() {
        return new FreightManifestValidationResult(true, "");
    }

    public static FreightManifestValidationResult error(String message) {
        return new FreightManifestValidationResult(false, message);
    }

    public boolean isValid() {
        return this.valid;
    }

    public String getMessage() {
        return this.message;
    }
}
