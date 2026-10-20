package com.createstats.train.manifest;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public class FreightManifestResourceKey {
    private final ManifestCargoKind cargoKind;
    private final ResourceLocation resourceId;

    public FreightManifestResourceKey(ManifestCargoKind cargoKind, ResourceLocation resourceId) {
        this.cargoKind = cargoKind;
        this.resourceId = resourceId;
    }

    public ManifestCargoKind getCargoKind() {
        return this.cargoKind;
    }

    public ResourceLocation getResourceId() {
        return this.resourceId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FreightManifestResourceKey other)) {
            return false;
        }
        return this.cargoKind == other.cargoKind && this.resourceId.equals(other.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.cargoKind, this.resourceId);
    }
}
