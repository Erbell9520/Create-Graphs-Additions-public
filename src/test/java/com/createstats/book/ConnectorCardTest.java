package com.createstats.book;

import com.createstats.book.content.Part;
import com.createstats.book.content.WireSheets;
import com.createstats.book.layout.BookText;
import com.createstats.power.ConnectorReadout;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeRates;
import com.createstats.power.WireTier;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorCardTest {
    private static final int BASE = FeRates.base();
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final int DYNAMO = com.createstats.book.specimen.StoreFixtures.dynamoFlow();

    private static ConnectorReadout card(ConnectorTier tier, List<WireTier> spans, int cable, int carried, int block,
                                         boolean serves, int ceiling, int bridges, int sendOnly) {
        return new ConnectorReadout("", tier, BASE, spans, cable, carried, block, serves, ceiling, bridges, sendOnly, 0,
                com.createstats.book.content.Configs.connectorBuffer());
    }

    private static List<String> lines(ConnectorReadout r, boolean sneaking) {
        final List<Component> out = new ArrayList<>();
        r.appendTo(out, sneaking);
        return out.stream().map(c -> c.getString().strip()).toList();
    }

    static Set<String> everyLine() {
        final List<ConnectorReadout> states = List.of(

                card(ConnectorTier.POWER, List.of(WireTier.INSULATED), 0, DYNAMO, 0, true, NONE, 0, 0),

                card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, DYNAMO, DYNAMO, true, NONE, 0, 0),
                card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, DYNAMO, -DYNAMO, true, NONE, 0, 0),

                card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, 0, 0, false, NONE, 0, 0),
                card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, FeRates.wire(WireTier.COPPER), 0, false, NONE,
                        0, 0),

                card(ConnectorTier.POWER, List.of(WireTier.INSULATED), 0, 0, 0, true,
                        com.createstats.power.LimiterLadder.feAt(com.createstats.power.LimiterLadder.DEFAULT_NOTCH), 0, 0),

                card(ConnectorTier.INDUSTRIAL, List.of(WireTier.values()), 0, 0, 0, true, NONE, 0, 0),

                card(ConnectorTier.POWER, List.of(), FeRates.cable(com.createstats.power.CableTier.SURFACE), 0, 0, true,
                        NONE, 0, 0),
                card(ConnectorTier.POWER, List.of(), 0, 0, 0, true, NONE, 0, 0),
                card(ConnectorTier.POWER, List.of(), 0, 0, 0, false, NONE, 0, 0),

                card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, 0, 0, true, NONE, 1, 1));
        final Set<String> out = new LinkedHashSet<>();
        for (final ConnectorReadout r : states) {
            out.addAll(lines(r, false));
            out.addAll(lines(r, true));
        }
        return out;
    }

    @Test
    void everyQuotedLineIsOnTheCard() throws IOException {
        final BookText t = BookFixtures.text(BookFixtures.lang());
        final Set<String> card = everyLine();
        final List<String> missing = new ArrayList<>();
        int quoted = 0;
        for (final Part p : WireSheets.CONNECTORS.parts()) {
            if (p instanceof Part.Status st) {
                for (final Part.Word w : st.rows()) {
                    if (!w.wordKey().startsWith("create_stats.book.goggle.")) {
                        continue;
                    }
                    quoted++;
                    final String line = t.get(w.wordKey(), w.argsOrNone());
                    if (!card.contains(line)) {
                        missing.add(line);
                    }
                }
            }
        }
        assertTrue(quoted >= 15, "the sheet quotes the card: " + quoted);
        assertTrue(missing.isEmpty(), "quoted, but on no card the sheet describes: " + missing + "\ncards: " + card);
    }

    @Test
    void theWiresLineCountsByGrade() {
        final ConnectorReadout r = card(ConnectorTier.ADVANCED, List.of(WireTier.SHIELDED, WireTier.COPPER,
                WireTier.COPPER), 0, 0, 0, true, NONE, 0, 0);
        assertEquals("3 of 4 wires: 2 Copper Wire, 1 Shielded Wire", r.wiresLine());
    }
}
