package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduRunHeadroomTest {
    private static final int UPKEEP = 51;
    private static final int CEILING = 98_304;
    private static final int WIRE = 32_768;

    private static final long T0 = 200;
    private static final long END = T0 + 600;

    private static final long WEATHER_PANEL = 1L;
    private static final long FIRST_HIGH_RANGE = 100L;
    private static final long FIRST_NORMAL_RANGE = 200L;
    private static final long LAMP = 304L;
    private static final long NEW_RANGE = 219L;
    private static final long REPELLER = 600L;

    @Test
    @DisplayName("KI-122: the run budget is the answer plus the ride and the spare, held to the wire")
    void theRuleItself() {
        assertEquals(32_717, FeBoardDeal.runBudget(16_619, 0, 16_098, CEILING));

        assertEquals(16_619, FeBoardDeal.runBudget(16_619, 0, 0, CEILING));

        assertEquals(175, FeBoardDeal.runBudget(100, 50, 25, 1_000));

        assertEquals(20_000, FeBoardDeal.runBudget(16_619, 0, 16_098, 20_000));

        assertEquals(0, FeBoardDeal.runBudget(-5, -5, -5, 1_000));
        assertEquals(Integer.MAX_VALUE, FeBoardDeal.runBudget(Integer.MAX_VALUE, 1, 1, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("the rig answers exactly what the save's board was answered")
    void theRigIsTheSave() {
        final Rig rig = new Rig(true, nominal(98_304), true);
        for (long now = 0; now < 50; now++) {
            rig.tick(now);
        }
        assertEquals(1, rig.onFile.asked().critical());
        assertEquals(16_669, rig.onFile.asked().running());
        assertEquals(0, rig.onFile.asked().restore());
        assertEquals(16_670, rig.onFile.total());
        assertEquals(16_098, rig.onFile.spare());
        assertEquals(71, rig.running.size());
    }

    @Test
    @DisplayName("KI-122: a 1 FE/t lamp switched on no longer sheds the 1,024 FE/t Mob Repeller")
    void aLampNoLongerShedsTheRepeller() {
        assertEquals(List.of("off 600 @0", "on 600 @60"), run(false, nominal(98_304), true, lampOn()),
                "as shipped: the repeller drops the tick the lamp lights and is back after the whole dwell");
        assertEquals(List.of(), run(true, nominal(98_304), true, lampOn()), "with the spare nothing is shed");
    }

    @Test
    @DisplayName("KI-122: a switch bringing ten ranges on sheds nothing that was running")
    void tenRangesSwitchedOnShedNothing() {
        final List<String> shipped = run(false, nominal(98_304), true, tenRangesOn());
        assertTrue(shipped.contains("off 600 @0"), "as shipped the repeller goes: " + shipped);
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L), ticks(shipped, "off"),
                "as shipped six ranges and the repeller go on the same tick: " + shipped);

        assertEquals(List.of(60L, 80L, 100L, 120L, 140L, 160L, 180L), ticks(shipped, "on"),
                "and come back one a second: " + shipped);
        assertEquals(List.of(), run(true, nominal(98_304), true, tenRangesOn()), "with the spare nothing is shed");
    }

    @Test
    @DisplayName("KI-122: before the repeller existed, the same switch shed ten Normal ranges instead")
    void withoutTheRepellerTheRangesWent() {
        final List<String> shipped = run(false, nominal(98_304), false, tenRangesOn());
        assertEquals(10, ticks(shipped, "off").size(), "as shipped a run of Normal ranges goes: " + shipped);
        assertEquals(240L, ticks(shipped, "on").get(9), "the last of them three seconds plus nine: " + shipped);
        assertEquals(List.of(), run(true, nominal(98_304), false, tenRangesOn()), "with the spare nothing is shed");
    }

    @Test
    @DisplayName("KI-122: a new range dialled Low, Medium, High sheds nothing")
    void aRangeDialledUpShedsNothing() {
        final Scenario dial = new Scenario(r -> r.set(NEW_RANGE, 0), List.of(
                new Step(0, r -> r.set(NEW_RANGE, 16)), new Step(20, r -> r.set(NEW_RANGE, 64)),
                new Step(40, r -> r.set(NEW_RANGE, 256))));
        assertEquals(List.of("off 600 @0", "on 600 @60"), run(false, nominal(98_304), true, dial),
                "as shipped the first click sheds the repeller");
        assertEquals(List.of(), run(true, nominal(98_304), true, dial), "with the spare nothing is shed");
    }

    @Test
    @DisplayName("KI-122: a real shortage still sheds, and the device returns only once the reserve grants it")
    void aRealShortageStillSheds() {
        final Scenario lampThenMore = new Scenario(r -> r.set(LAMP, 0), List.of(
                new Step(0, r -> r.set(LAMP, 1)), new Step(300, r -> r.offer = nominal(16_669 + 1 + 2_000))));
        final List<String> fixed = run(true, nominal(16_669), true, lampThenMore);
        assertEquals(List.of("off 600 @0", "on 600 @301"), fixed,
                "shed at once, held while nothing can fund it, back the tick after the reserve answers it");
        assertEquals(run(false, nominal(16_669), true, lampThenMore), fixed, "identical to the shipped board");
    }

    @Test
    @DisplayName("KI-122: under CONSERVE a lamp no longer strands the repeller until the stance lifts")
    void aRationingStanceNoLongerStrandsTheVictim() {
        assertEquals(List.of("off 600 @0"), run(false, conserve(98_304), true, lampOn()),
                "as shipped the repeller is shed and never restored while the controller conserves");
        assertEquals(List.of(), run(true, conserve(98_304), true, lampOn()), "with the spare nothing is shed");
    }

    @Test
    @DisplayName("KI-122: under CONSERVE a device shed for real is still not restored, whatever the spare (KI-109)")
    void aRationingStanceStillRestoresNothing() {
        final Scenario tight = new Scenario(r -> r.set(NEW_RANGE, 0), List.of(
                new Step(0, r -> r.set(NEW_RANGE, 256)), new Step(100, r -> r.offer = conserve(98_304))));
        final Rig rig = new Rig(true, conserve(16_669 + 1 + 100), true);
        assertEquals(List.of("off 600 @0"), rig.replay(tight), "shed for real, and never restored while conserving");
        assertFalse(rig.running.contains(REPELLER));
        assertTrue(rig.onFile.spare() >= 1_024, "the spare could have funded it: " + rig.onFile.spare());
        assertEquals(0, rig.onFile.restore(), "and the answer granted no restore");
    }

    private record Step(long at, Consumer<Rig> action) {
    }

    private record Scenario(Consumer<Rig> before, List<Step> steps) {
    }

    private static FeBoardDeal.Offer nominal(int pool) {
        return new FeBoardDeal.Offer(pool, pool, 875, FeReserveReport.Stance.NOMINAL, 45, false);
    }

    private static FeBoardDeal.Offer conserve(int pool) {
        return new FeBoardDeal.Offer(pool, pool, 875, FeReserveReport.Stance.CONSERVE, 25, false);
    }

    private static Scenario lampOn() {
        return new Scenario(r -> r.set(LAMP, 0), List.of(new Step(0, r -> r.set(LAMP, 1))));
    }

    private static Scenario tenRangesOn() {
        return new Scenario(r -> tenRanges(r, 0), List.of(new Step(0, r -> tenRanges(r, 256))));
    }

    private static void tenRanges(Rig r, int demand) {
        for (int i = 0; i < 5; i++) {
            r.set(FIRST_HIGH_RANGE + 35 + i, demand);
            r.set(FIRST_NORMAL_RANGE + 9 + i, demand);
        }
    }

    private static List<String> run(boolean fixed, FeBoardDeal.Offer offer, boolean withRepeller, Scenario s) {
        return new Rig(fixed, offer, withRepeller).replay(s);
    }

    private static List<Long> ticks(List<String> flips, String kind) {
        final List<Long> out = new ArrayList<>();
        for (String f : flips) {
            if (f.startsWith(kind + " ")) {
                out.add(Long.parseLong(f.substring(f.indexOf('@') + 1)));
            }
        }
        return out;
    }

    private static final class Rig {
        final Map<Long, int[]> devices = new LinkedHashMap<>();
        final PduAutoController controller = new PduAutoController();
        final boolean fixed;
        FeBoardDeal.Offer offer;
        FeBoardDeal.Answer onFile = FeBoardDeal.Answer.NONE;
        Set<Long> running = Set.of();

        Rig(boolean fixed, FeBoardDeal.Offer offer, boolean withRepeller) {
            this.fixed = fixed;
            this.offer = offer;
            final int high = FePriority.HIGH.ordinal();
            final int normal = FePriority.NORMAL.ordinal();
            devices.put(WEATHER_PANEL, new int[] {FePriority.CRITICAL.ordinal(), 1});
            for (int i = 0; i < 40; i++) {
                devices.put(FIRST_HIGH_RANGE + i, new int[] {high, 256});
            }
            for (int i = 0; i < 19; i++) {
                devices.put(FIRST_NORMAL_RANGE + i, new int[] {normal, 256});
            }
            for (int i = 0; i < 5; i++) {
                devices.put(300L + i, new int[] {normal, 1});
            }
            devices.put(400L, new int[] {normal, 240});
            devices.put(401L, new int[] {normal, 240});
            devices.put(500L, new int[] {normal, 4});
            devices.put(501L, new int[] {normal, 1});
            devices.put(502L, new int[] {normal, 0});
            if (withRepeller) {
                devices.put(REPELLER, new int[] {normal, 1_024});
            }
        }

        void set(long key, int demand) {
            devices.computeIfAbsent(key, k -> new int[] {FePriority.NORMAL.ordinal(), 0})[1] = demand;
        }

        List<String> replay(Scenario scenario) {
            scenario.before().accept(this);
            final List<String> flips = new ArrayList<>();
            Set<Long> previous = null;
            for (long now = 0; now <= END; now++) {
                for (Step step : scenario.steps()) {
                    if (now == T0 + step.at()) {
                        step.action().accept(this);
                    }
                }
                tick(now);
                if (previous != null && now >= T0) {
                    for (long key : previous) {
                        if (!running.contains(key)) {
                            flips.add("off " + key + " @" + (now - T0));
                        }
                    }
                    for (long key : running) {
                        if (!previous.contains(key)) {
                            flips.add("on " + key + " @" + (now - T0));
                        }
                    }
                }
                previous = running;
            }
            return flips;
        }

        void tick(long now) {
            final List<PduAutoController.Device> list = new ArrayList<>(devices.size());
            int critical = 0;
            int ordinary = 0;
            int runningOrdinary = 0;
            for (Map.Entry<Long, int[]> e : devices.entrySet()) {
                final int tier = e.getValue()[0];
                final int demand = e.getValue()[1];
                list.add(new PduAutoController.Device(e.getKey(), tier, demand, FeLock.NONE,
                        FePriority.NORMAL.ordinal()));
                if (tier == FePriority.CRITICAL.ordinal()) {
                    critical += demand;
                } else {
                    ordinary += demand;
                    if (controller.isRunning(e.getKey())) {
                        runningOrdinary += demand;
                    }
                }
            }

            final int askRunning = Math.min(ordinary, runningOrdinary);
            final FeBoardDeal.Request ask = new FeBoardDeal.Request(1L, critical, askRunning + UPKEEP,
                    ordinary - askRunning);
            final PduAutoController.Supply supply;
            if (onFile.isPresent()) {
                final int dealRun = Math.min(CEILING, Math.max(0, onFile.total() - UPKEEP));
                final int run = fixed ? FeBoardDeal.runBudget(dealRun, 0, onFile.spare(), CEILING)
                        : Math.min(CEILING, dealRun);
                final int admit = Math.min(run, dealRun);
                final int margin = PduAutoController.restoreMargin(Math.round(onFile.total() * 0.10f), admit,
                        (long) admit + onFile.spare());
                supply = new PduAutoController.Supply(run, admit, margin, false, onFile.restore() > 0, false,
                        Math.min(CEILING, Math.max(run, dealRun)), 0,
                        onFile.restore() <= 0 && onFile.stance().isRationing(), false, 0);
            } else {
                supply = PduAutoController.Supply.flat(CEILING, true);
            }
            running = controller.decide(list, supply, now).admitted();

            onFile = FeBoardDeal.split(2L, false, offer, List.of(ask), new int[] {WIRE}, null, now + 10).get(0);
        }
    }
}
