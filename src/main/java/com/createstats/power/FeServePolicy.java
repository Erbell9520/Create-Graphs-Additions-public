package com.createstats.power;

public interface FeServePolicy {
    FeServePolicy DEFAULT = new FeServePolicy() {
        @Override
        public int tierOrdinalOf(FeGridNode node) {
            return node.attachedStorage() instanceof FeConsumer fe
                    ? fe.priority().ordinal() : FePriority.DEFAULT.ordinal();
        }

        @Override
        public void markShed(FeGridNode node, boolean shed) {
            if (node.attachedStorage() instanceof FeConsumer fe) {
                fe.setLoadShed(shed);
            }
        }
    };

    int tierOrdinalOf(FeGridNode node);

    void markShed(FeGridNode node, boolean shed);
}
