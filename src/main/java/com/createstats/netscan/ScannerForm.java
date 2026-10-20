package com.createstats.netscan;

public enum ScannerForm {
    INLINE,

    CLAMP;

    public boolean isInline() {
        return this == INLINE;
    }
}
