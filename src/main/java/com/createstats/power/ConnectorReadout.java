package com.createstats.power;

import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// one builder. two copies "kept line for line" by a comment drifted anyway, a comment can't fail
public record ConnectorReadout(String name, ConnectorTier tier, int base, List<WireTier> spans, int cableFeed,
                               int carried, int blockFlow, boolean servesBlock, int branchCeiling,
                               int bridges, int sendOnly, int buffer, int bufferCapacity) {
    public int rated() {
        return base * tier.multiplier();
    }

    public int capacity() {
        return FeRates.connectorThroughput(base, tier, spans, cableFeed);
    }

    public int wiring() {
        int sum = Math.max(0, cableFeed);
        for (WireTier span : spans) {
            sum = FeRates.plus(sum, base * (span == null ? WireTier.COPPER : span).multiplier());
        }
        return sum;
    }

    public boolean passesThrough() {
        return !servesBlock || carried > Math.abs(blockFlow);
    }

    public void appendTo(List<Component> tooltip, boolean sneaking) {
        final int capacity = capacity();
        tooltip.add(FeGoggle.title(name));
        if (passesThrough()) {
            tooltip.add(FeGoggle.carrying(carried, capacity));
        }
        if (servesBlock) {
            if (passesThrough() && blockFlow == 0) {
                tooltip.add(FeGoggle.dim("Its block: idle"));
            } else {
                tooltip.add(FeGoggle.transfer(blockFlow));
            }
        }
        tooltip.add(FeGoggle.capacity(capacity));

        if (branchCeiling < capacity) {
            tooltip.add(FeGoggle.warn(String.format(Locale.ROOT,
                    "Branch held to %,d FE/t by an inline limiter", Math.max(0, branchCeiling))));
        }
        tooltip.add(FeGoggle.dim(wiresLine()));
        if (bridges > 0) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "%d bridging to another mod's network", bridges)));
        }
        if (sendOnly > 0) {
            tooltip.add(FeGoggle.dim(sendOnly == bridges
                    ? "Send only: a New Age network cannot be drawn from"
                    : String.format(Locale.ROOT, "%d of them send only, to New Age", sendOnly)));
            tooltip.add(FeGoggle.dim("To take their power, bolt their connector to a terminal of ours"));
        }
        if (sneaking) {
            final int wiring = wiring();
            if ((spans.isEmpty() && cableFeed <= 0) || wiring >= rated()) {
                tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Rated %,d FE/t, its grade", rated())));
            } else {
                tooltip.add(FeGoggle.dim(String.format(Locale.ROOT,
                        "Rated %,d FE/t; its wiring carries %,d", rated(), wiring)));
            }
            if (cableFeed > 0) {
                tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Cable touching it: %,d FE/t", cableFeed)));
            }
            if (!servesBlock) {
                tooltip.add(FeGoggle.dim("Its block takes no power"));
            }

            tooltip.add(FeGoggle.amount("Buffer", buffer, bufferCapacity));
        } else {
            tooltip.add(FeGoggle.more());
        }
    }

    public String wiresLine() {
        if (spans.isEmpty()) {
            if (cableFeed > 0) {
                return "No wires, on a cable";
            }
            return servesBlock ? "No wires, serving the block it is on" : "No wires, no cable, and its block takes no power";
        }
        final Map<WireTier, Integer> byGrade = new EnumMap<>(WireTier.class);
        for (WireTier span : spans) {
            byGrade.merge(span == null ? WireTier.COPPER : span, 1, Integer::sum);
        }
        final StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%d of %d wire%s: ",
                spans.size(), tier.maxLinks(), tier.maxLinks() == 1 ? "" : "s"));
        boolean first = true;
        for (Map.Entry<WireTier, Integer> grade : byGrade.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(grade.getValue()).append(' ').append(grade.getKey().getDisplayName());
            first = false;
        }
        return out.toString();
    }
}
