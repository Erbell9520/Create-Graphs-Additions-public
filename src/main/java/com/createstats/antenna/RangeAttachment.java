package com.createstats.antenna;

public interface RangeAttachment {
    AntennaTier getAntennaTier();

    void setAntennaTier(AntennaTier tier);

    default boolean hasAntenna() {
        return this.getAntennaTier().isAttached();
    }

    default void removeAntenna() {
        this.setAntennaTier(AntennaTier.NONE);
    }
}
