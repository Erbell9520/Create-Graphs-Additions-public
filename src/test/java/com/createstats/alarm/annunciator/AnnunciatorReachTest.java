package com.createstats.alarm.annunciator;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnunciatorReachTest {
    @Test
    @DisplayName("a fresh device reaches far enough to see the room it is in")
    void defaultReachIsUseful() {
        final AnnunciatorSettings settings = new AnnunciatorSettings();
        assertEquals(AnnunciatorSettings.DEFAULT_SCAN_RADIUS, settings.getScanRadius());

        assertTrue(settings.getScanRadius() >= 8,
                "a device that reaches less than eight blocks cannot see the panel beside it");
    }

    @Test
    @DisplayName("the radius clamps to its own range in both directions")
    void radiusClamps() {
        final AnnunciatorSettings settings = new AnnunciatorSettings();
        settings.setScanRadius(-40);
        assertEquals(AnnunciatorSettings.MIN_SCAN_RADIUS, settings.getScanRadius());
        settings.setScanRadius(9999);
        assertEquals(AnnunciatorSettings.MAX_SCAN_RADIUS, settings.getScanRadius());
    }

    @Test
    @DisplayName("a radius saved under the old 1..8 range is migrated, not merely clamped")
    void legacyRadiusIsMigrated() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("ScanRadius", 3);

        assertEquals(AnnunciatorSettings.DEFAULT_SCAN_RADIUS,
                AnnunciatorSettings.load(tag).getScanRadius());
    }

    @Test
    @DisplayName("a radius someone deliberately chose inside the new range survives a reload")
    void deliberateRadiusSurvives() {
        for (int radius : new int[]{AnnunciatorSettings.MIN_SCAN_RADIUS, 8, 20,
                AnnunciatorSettings.MAX_SCAN_RADIUS}) {
            final AnnunciatorSettings settings = new AnnunciatorSettings();
            settings.setScanRadius(radius);
            assertEquals(radius, AnnunciatorSettings.load(settings.save()).getScanRadius(),
                    "a legal radius must round trip unchanged: " + radius);
        }
    }

    @Test
    @DisplayName("an aerial is still worth fitting to a device already wound out to maximum")
    void aerialCapExceedsTheConfiguredMaximum() {
        assertTrue(AnnunciatorSettings.MAX_AERIAL_RADIUS > AnnunciatorSettings.MAX_SCAN_RADIUS,
                "a cap equal to the maximum makes an aerial silently do nothing on a wound-up device");
    }

    @Test
    @DisplayName("every fault reason survives a save and reload")
    void faultsRoundTrip() {
        for (RuleFault fault : RuleFault.values()) {
            assertEquals(fault, RuleFault.byName(fault.getSerializedName()));
            assertEquals(fault, RuleFault.byName(fault.name()));
        }

        assertEquals(RuleFault.NONE, RuleFault.byName("something_from_a_future_version"));
        assertEquals(RuleFault.NONE, RuleFault.byName(""));
        assertEquals(RuleFault.NONE, RuleFault.byName(null));
    }

    @Test
    @DisplayName("every fault reason has a line to show the player")
    void everyFaultIsTranslated() throws Exception {
        final Path lang = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
        if (!Files.exists(lang)) {
            return;
        }
        final String json = Files.readString(lang);
        for (RuleFault fault : RuleFault.values()) {
            assertTrue(json.contains('"' + fault.translationKey() + '"'),
                    "no en_us entry for " + fault.translationKey());
        }
    }

    @Test
    @DisplayName("re-stamping a rule's id keeps everything else about it")
    void copyWithIdKeepsTheRule() {
        final WatchRule rule = new WatchRule("r1");
        rule.setLabel("Bank Charge");
        rule.setBinding(RuleBinding.grid(GridFact.BANK_CHARGE));
        rule.setComparator(RuleComparator.BELOW);
        rule.setThreshold(20.0f);
        rule.setDeadband(2.0f);
        rule.setSeverity(AlarmSeverity.CRITICAL);
        rule.setMeasure(RuleMeasure.LEVEL);
        rule.setLatching(false);
        rule.setArmed(true);

        final WatchRule restamped = rule.copyWithId("r7");
        assertEquals("r7", restamped.getId());
        assertNotEquals(rule.getId(), restamped.getId());
        assertEquals(rule.getRawLabel(), restamped.getRawLabel());
        assertEquals(rule.getBinding(), restamped.getBinding());
        assertEquals(rule.getComparator(), restamped.getComparator());
        assertEquals(rule.getThreshold(), restamped.getThreshold());
        assertEquals(rule.getDeadband(), restamped.getDeadband());
        assertEquals(rule.getSeverity(), restamped.getSeverity());
        assertEquals(rule.getMeasure(), restamped.getMeasure());
        assertEquals(rule.isLatching(), restamped.isLatching());
        assertEquals(rule.isArmed(), restamped.isArmed());
    }

    @Test
    @DisplayName("two rules sharing an id share a status, which is why ids are re-stamped")
    void duplicateIdsCollapseIntoOneStatus() {
        final WatchRule first = armed("r1", 50.0f);
        final WatchRule second = armed("r1", 50.0f);
        final List<WatchRule> rules = new ArrayList<>(List.of(first, second));

        final AnnunciatorEngine.Outcome outcome = AnnunciatorEngine.evaluate(rules, java.util.Map.of(),
                rule -> AnnunciatorEngine.Reading.of(90.0f), false, false, false, 10L);

        assertEquals(2, outcome.statuses().size(), "one status per rule comes out of the engine");
        assertEquals(1, AnnunciatorEngine.index(outcome.statuses()).size(),
                "but two rules with one id collapse to a single status on the way back in");
    }

    private static WatchRule armed(String id, float threshold) {
        final WatchRule rule = new WatchRule(id);
        rule.setBinding(RuleBinding.grid(GridFact.BANK_CHARGE));
        rule.setComparator(RuleComparator.ABOVE);
        rule.setThreshold(threshold);
        rule.setArmed(true);
        return rule;
    }
}
