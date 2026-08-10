package com.createstats.dispatch;

import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.ManifestCargoKind;
import net.minecraft.resources.ResourceLocation;

/**
 * A single cargo summary slot for the board's cargo region.
 * Pairs a resource key with its total amount on the train.
 */
public class CargoSummaryEntry {

    private final FreightManifestResourceKey resourceKey;
    private final int amount;

    public CargoSummaryEntry(FreightManifestResourceKey resourceKey, int amount) {
        this.resourceKey = resourceKey;
        this.amount = amount;
    }

    public FreightManifestResourceKey getResourceKey() {
        return this.resourceKey;
    }

    public ManifestCargoKind getCargoKind() {
        return this.resourceKey.getCargoKind();
    }

    public ResourceLocation getResourceId() {
        return this.resourceKey.getResourceId();
    }

    public int getAmount() {
        return this.amount;
    }

    /**
     * Compact display string, e.g. "64 Iron Ingot" or "4000 mB Water".
     * Uses the resource ID path as a fallback display name.
     */
    public String getCompactLabel() {
        String name = this.resourceKey.getResourceId().getPath().replace('_', ' ');
        if (this.resourceKey.getCargoKind() == ManifestCargoKind.FLUID) {
            return this.amount + " mB " + name;
        }
        return this.amount + "x " + name;
    }
}
