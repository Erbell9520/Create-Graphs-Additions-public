package com.createstats.power;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorReadoutTest {
    private static final int BASE = 8_192;
    private static final int NONE = FeGridSolver.NO_CEILING;

    private static ConnectorReadout card(ConnectorTier tier, List<WireTier> spans, int cableFeed, int carried,
                                         int blockFlow, boolean servesBlock) {
        return new ConnectorReadout(tier.getDisplayName(), tier, BASE, spans, cableFeed, carried, blockFlow,
                servesBlock, NONE, 0, 0, 0, 16_000);
    }

    private static List<String> lines(ConnectorReadout card, boolean sneaking) {
        final List<Component> out = new ArrayList<>();
        card.appendTo(out, sneaking);
        final List<String> text = new ArrayList<>();
        for (Component line : out) {
            text.add(line.getString().trim());
        }
        return text;
    }

    private static boolean any(List<String> lines, String fragment) {
        for (String line : lines) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("every tier's card is titled with its own name")
    void eachTierNamesItself() {
        for (ConnectorTier tier : ConnectorTier.values()) {
            assertEquals(tier.getDisplayName(), lines(card(tier, List.of(), 0, 0, 0, true), false).get(0));
        }
    }

    @Test
    @DisplayName("a hub on a block that takes no power says what goes through it")
    void aHubShowsWhatItCarries() {
        final List<String> hub = lines(card(ConnectorTier.POWER, List.of(WireTier.INSULATED, WireTier.COPPER),
                0, 12_000, 0, false), false);
        assertTrue(any(hub, "Carrying: 12,000 FE/t"), hub.toString());
        assertFalse(any(hub, "Feeding block"), "no block line for a block that takes no power");
    }

    @Test
    @DisplayName("a connector serving only its block shows one figure, not the same one twice")
    void aPlainConnectorShowsItsBlock() {
        final List<String> plain = lines(card(ConnectorTier.POWER, List.of(WireTier.COPPER), 0, 600, 600, true),
                false);
        assertTrue(any(plain, "Feeding block: 600 FE/t"), plain.toString());
        assertFalse(any(plain, "Carrying"), plain.toString());
    }

    @Test
    @DisplayName("a connector partway along a chain shows both what it passes on and what its block takes")
    void aChainLinkShowsBoth() {
        final List<String> link = lines(card(ConnectorTier.ADVANCED, List.of(WireTier.SHIELDED, WireTier.SHIELDED),
                0, 9_000, 600, true), false);
        assertTrue(any(link, "Carrying: 9,000 FE/t") && any(link, "Feeding block: 600 FE/t"), link.toString());
        final List<String> idleBlock = lines(card(ConnectorTier.ADVANCED,
                List.of(WireTier.SHIELDED, WireTier.SHIELDED), 0, 9_000, 0, true), false);
        assertTrue(any(idleBlock, "Its block: idle"), "an idle block beside traffic is named, not a bare 'Idle'");
    }

    @Test
    @DisplayName("the wires line counts every grade, not the best one")
    void everyGradeIsCounted() {
        final ConnectorReadout mixed = card(ConnectorTier.POWER,
                List.of(WireTier.COPPER, WireTier.SHIELDED, WireTier.COPPER), 0, 0, 0, true);
        assertEquals("3 of 4 wires: 2 Copper Wire, 1 Shielded Wire", mixed.wiresLine());
        assertEquals("No wires, on a cable", card(ConnectorTier.POWER, List.of(), BASE, 0, 0, true).wiresLine());
    }

    @Test
    @DisplayName("capacity counts the cable it touches, and sneaking says why it is what it is")
    void capacityCountsCable() {
        final ConnectorReadout fed = card(ConnectorTier.POWER, List.of(WireTier.COPPER), 2 * BASE, 0, 0, true);
        assertEquals(2 * BASE, fed.capacity());
        final ConnectorReadout thin = card(ConnectorTier.INDUSTRIAL, List.of(WireTier.COPPER), 0, 0, 0, true);
        assertEquals(BASE, thin.capacity());
        assertTrue(any(lines(thin, true), "Rated 65,536 FE/t; its wiring carries 8,192"));
        assertFalse(any(lines(thin, false), "Rated"), "the reason is behind sneak");
    }

    @Test
    @DisplayName("a limiter feeding the branch is named, as the cable names it")
    void aLimiterIsNamed() {
        final ConnectorReadout limited = new ConnectorReadout("Power Connector", ConnectorTier.POWER, BASE,
                List.of(WireTier.INSULATED), 0, 1_024, 1_024, true, 1_024, 0, 0, 0, 16_000);
        assertTrue(any(lines(limited, false), "Branch held to 1,024 FE/t by an inline limiter"));
    }

    @Test
    @DisplayName("the connector buffer's fallback is the config's own default")
    void theBufferFallbackIsTheDefault() throws IOException {
        final String config = Files.readString(Path.of("src/main/java/com/createstats/ModConfiguration.java"),
                StandardCharsets.UTF_8);
        final Matcher m = Pattern.compile(
                "connectorBufferCapacity\\s*=\\s*builder[^;]*?defineInRange\\(\"bufferCapacity\",\\s*(\\d+)").matcher(config);
        assertTrue(m.find(), "the connector buffer's definition moved; this test guards nothing until it follows");
        assertEquals(Integer.parseInt(m.group(1)), com.createstats.blockentity.PowerConnectorBE.DEFAULT_BUFFER);
    }
}
