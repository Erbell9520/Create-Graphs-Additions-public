package com.createstats.train.manifest;

public final class DeliveryOrderLifecyclePolicy {
    private DeliveryOrderLifecyclePolicy() {
    }

    public static boolean isArchived(DeliveryOrderResetMode resetMode, boolean complete,
                                     boolean explicitlyDismissed) {
        return complete && (explicitlyDismissed || resetMode == DeliveryOrderResetMode.ONCE);
    }

    public static boolean archivesOnCompletion(DeliveryOrderResetMode resetMode) {
        return resetMode == DeliveryOrderResetMode.ONCE;
    }

    public static boolean retiresFromActiveManifest(DeliveryOrderResetMode resetMode, boolean complete) {
        return resetMode == DeliveryOrderResetMode.ONCE && complete;
    }

    public record Direction(ManifestOperation operation, ManifestCargoKind cargoKind) {
    }

    public static boolean publishKeepsOnceGate(Direction gate, java.util.Collection<Direction> published) {
        return !published.isEmpty() && !published.contains(gate);
    }
}
