package com.createstats.efficiency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EfficiencyModelTest {
    private static ODEState settled(float startE) {
        final ODEState s = new ODEState();
        s.currentE = startE;
        s.observations = EfficiencyGrader.WARMUP_OBSERVATIONS + 1;
        s.uptimeSeeded = true;
        return s;
    }

    private static EfficiencyInputs healthy() {
        return inputs(1000.0f, 700.0f, 780.0f, 0.03f, true, 0.02f, true, false, 0.0f, true);
    }

    private static EfficiencyInputs inputs(float capacity, float stress, float peak,
                                           float stressCv, boolean stressCvKnown,
                                           float rpmCv, boolean rpmKnown,
                                           boolean overstressed, float longestRun,
                                           boolean episodesKnown) {
        return new EfficiencyInputs(capacity, stress, peak, stressCv, stressCvKnown, rpmCv,
                rpmKnown, overstressed, longestRun, episodesKnown, 0.0f, episodesKnown, 0L);
    }

    @Nested
    @DisplayName("The grade table, which used to contradict itself in three places")
    class Grades {
        @Test
        @DisplayName("colour is a function of the letter's tier, not of a second threshold list")
        void colourFollowsTheLetter() {
            final java.util.Map<Character, java.util.Set<Integer>> byTier = new java.util.HashMap<>();
            for (int i = 0; i < GradeScale.count(); i++) {
                byTier.computeIfAbsent(GradeScale.letterAt(i).charAt(0), k -> new java.util.HashSet<>())
                        .add(GradeScale.colorAt(i));
            }
            assertEquals(5, byTier.size(), "expected the tiers A B C D F");
            final java.util.Set<Integer> distinct = new java.util.HashSet<>();
            byTier.forEach((tier, colours) -> {
                assertEquals(1, colours.size(),
                        "tier " + tier + " is painted in " + colours.size() + " different colours");
                distinct.addAll(colours);
            });
            assertEquals(5, distinct.size(), "two tiers share a colour, so they cannot be told apart");
        }

        @Test
        @DisplayName("every score in the range resolves to a letter and a matching colour")
        void everyScoreResolves() {
            for (int hundredths = 0; hundredths <= 10_000; hundredths++) {
                final float score = hundredths / 100.0f;
                final int index = GradeScale.indexFor(score);
                assertEquals(GradeScale.colorAt(index), EfficiencyGrader.gradeColor(score),
                        "colour disagrees with letter at " + score);
                assertEquals(GradeScale.letterAt(index), EfficiencyGrader.gradeFromScore(score),
                        "letter lookup disagrees with itself at " + score);
                assertTrue(score >= GradeScale.minScoreAt(index),
                        "score " + score + " assigned a grade it does not reach");
            }
        }

        @Test
        @DisplayName("the D- floor is 40, matching the five point cadence of every other band")
        void dMinusFloorIsForty() {
            assertEquals("D-", EfficiencyGrader.gradeFromScore(40.0f));
            assertEquals("F", EfficiencyGrader.gradeFromScore(39.99f));
            assertEquals("F", EfficiencyGrader.gradeFromScore(35.0f));
        }

        @Test
        @DisplayName("bands are contiguous and descend, with no gap or overlap")
        void bandsAreWellFormed() {
            for (int i = 1; i < GradeScale.count(); i++) {
                assertTrue(GradeScale.minScoreAt(i) < GradeScale.minScoreAt(i - 1),
                        "band " + i + " does not sit below its predecessor");
            }
            assertEquals(0.0f, GradeScale.minScoreAt(GradeScale.count() - 1),
                    "the worst grade must catch everything down to zero");
        }

        @Test
        @DisplayName("a score sitting on a boundary keeps the letter it already has")
        void hysteresisHoldsTheBoundary() {
            final int bPlus = GradeScale.indexFor(80.0f);
            final int aMinus = GradeScale.indexFor(85.0f);
            assertNotEquals(bPlus, aMinus);

            assertEquals(bPlus, GradeScale.resolve(85.0f, bPlus));
            assertEquals(bPlus, GradeScale.resolve(85.0f + GradeScale.HYSTERESIS / 2.0f, bPlus));

            assertEquals(aMinus, GradeScale.resolve(85.0f + GradeScale.HYSTERESIS, bPlus));

            assertEquals(aMinus, GradeScale.resolve(85.0f - GradeScale.HYSTERESIS / 2.0f, aMinus));
            assertEquals(bPlus, GradeScale.resolve(85.0f - GradeScale.HYSTERESIS, aMinus));
        }

        @Test
        @DisplayName("a cold start takes the letter the score actually earns")
        void coldStartHasNoHysteresis() {
            assertEquals(GradeScale.indexFor(72.0f), GradeScale.resolve(72.0f, ODEState.NO_GRADE));
        }
    }

    @Nested
    @DisplayName("Kernels")
    class Kernels {
        @Test
        @DisplayName("Load is perfect across a band, not at a single point")
        void loadHasAPlateau() {
            assertEquals(1.0f, EfficiencyKernels.load(EfficiencyKernels.LOAD_BAND_LO), 1.0E-4f);
            assertEquals(1.0f, EfficiencyKernels.load(0.70f), 1.0E-4f);
            assertEquals(1.0f, EfficiencyKernels.load(EfficiencyKernels.LOAD_BAND_HI), 1.0E-4f);
            assertTrue(EfficiencyKernels.load(0.40f) > 0.75f,
                    "a factory at 40% load is under-used, not failing");
        }

        @Test
        @DisplayName("Load collapses past capacity and is zero when idle")
        void loadCollapsesPastCapacity() {
            assertEquals(0.0f, EfficiencyKernels.load(0.0f), 1.0E-6f);
            assertTrue(EfficiencyKernels.load(1.00f) < 0.6f);
            assertTrue(EfficiencyKernels.load(1.20f) < 0.01f);
        }

        @Test
        @DisplayName("Load is monotonic up to the band and monotonic down past it")
        void loadIsMonotonicOnBothSides() {
            for (int i = 1; i <= 54; i++) {
                assertTrue(EfficiencyKernels.load(i / 100.0f) >= EfficiencyKernels.load((i - 1) / 100.0f),
                        "Load dipped while rising towards the band at u=" + i / 100.0f);
            }
            for (int i = 91; i <= 150; i++) {
                assertTrue(EfficiencyKernels.load(i / 100.0f) <= EfficiencyKernels.load((i - 1) / 100.0f),
                        "Load rose while falling past the band at u=" + i / 100.0f);
            }
        }

        @Test
        @DisplayName("Reserve reads the peak, so a spiky network cannot show a full margin")
        void reserveIsDrivenByThePeak() {
            assertEquals(0.0f, EfficiencyKernels.reserve(1.0f), 1.0E-6f);
            assertTrue(EfficiencyKernels.reserve(0.95f) < 0.5f);
            assertTrue(EfficiencyKernels.reserve(0.50f) > 0.98f);
        }

        @Test
        @DisplayName("Rhythm keeps v1's published numbers, which were the one correct part")
        void rhythmMatchesItsDocumentedCurve() {
            assertEquals(1.00f, EfficiencyKernels.rhythm(0.00f), 1.0E-4f);
            assertEquals(0.61f, EfficiencyKernels.rhythm(0.10f), 0.01f);
            assertEquals(0.14f, EfficiencyKernels.rhythm(0.20f), 0.01f);
        }

        @Test
        @DisplayName("Smoothness is looser than Rhythm, because load is meant to breathe")
        void smoothnessIsLooserThanRhythm() {
            assertTrue(EfficiencyKernels.smoothness(0.15f) > EfficiencyKernels.rhythm(0.15f));
        }

        @Test
        @DisplayName("Resilience separates many short stalls from one long one")
        void resilienceSeesEpisodeLength() {
            assertEquals(1.0f, EfficiencyKernels.resilience(0.0f), 1.0E-6f);
            assertTrue(EfficiencyKernels.resilience(3.0f) > 0.85f, "a flicker is survivable");
            assertTrue(EfficiencyKernels.resilience(120.0f) < 0.05f, "a two minute stall is not");
        }

        @Test
        @DisplayName("overstress severity scales with how far over the line it is")
        void severityIsContinuous() {
            assertEquals(0.5f, EfficiencyKernels.overstressSeverity(1.0f), 1.0E-4f);
            assertEquals(1.0f, EfficiencyKernels.overstressSeverity(1.5f), 1.0E-4f);
            assertEquals(1.0f, EfficiencyKernels.overstressSeverity(3.0f), 1.0E-4f,
                    "severity must saturate rather than run away");
        }
    }

    @Nested
    @DisplayName("Weights and observability")
    class Observability {
        @Test
        @DisplayName("the nominal weights sum to exactly one")
        void weightsSumToOne() {
            assertEquals(1.0f, EfficiencyDimension.weightSum(), 1.0E-5f);
        }

        @Test
        @DisplayName("an unmeasured dimension leaves the mean instead of scoring zero")
        void unknownIsNotZero() {
            final EfficiencyInputs withRpm = healthy();
            final EfficiencyInputs withoutRpm = inputs(1000.0f, 700.0f, 780.0f, 0.03f, true,
                    0.0f, false, false, 0.0f, true);

            final EfficiencyScore a = EfficiencyGrader.calculate(withRpm, new ODEState());
            final EfficiencyScore b = EfficiencyGrader.calculate(withoutRpm, new ODEState());

            assertTrue(a.compositeScore() > 90.0f,
                    "the reference healthy network should grade well: " + a.compositeScore());
            assertTrue(b.compositeScore() > 85.0f,
                    "losing a channel must cost precision, not 30 points: " + b.compositeScore());
            assertTrue(Math.abs(a.compositeScore() - b.compositeScore()) < 6.0f,
                    "an unmeasured dimension moved the score by "
                            + Math.abs(a.compositeScore() - b.compositeScore()));

            assertFalse(b.isKnown(EfficiencyDimension.STABILITY));
            assertTrue(b.isPartial());
            assertEquals(1.0f - EfficiencyDimension.STABILITY.getWeight(), b.observability(), 1.0E-4f);
        }

        @Test
        @DisplayName("a fully measured sample reports full observability and hides no caveat")
        void fullyMeasuredIsNotPartial() {
            final EfficiencyScore s = EfficiencyGrader.calculate(healthy(), new ODEState());
            assertEquals(1.0f, s.observability(), 1.0E-4f);
            assertFalse(s.isPartial());
            for (EfficiencyDimension d : EfficiencyDimension.values()) {
                assertTrue(s.isKnown(d), d + " should be measured in the reference sample");
            }
        }

        @Test
        @DisplayName("the known mask and the observability figure tell the same story")
        void maskAgreesWithObservability() {
            final EfficiencyInputs sparse = inputs(1000.0f, 700.0f, 780.0f, 0.0f, false,
                    0.0f, false, false, 0.0f, false);
            final EfficiencyScore s = EfficiencyGrader.calculate(sparse, new ODEState());
            float expected = 0.0f;
            for (EfficiencyDimension d : EfficiencyDimension.values()) {
                if (s.isKnown(d)) {
                    expected += d.getWeight();
                }
            }
            assertEquals(expected, s.observability(), 1.0E-4f);
        }
    }

    @Nested
    @DisplayName("Dimensions that must not restate each other")
    class Independence {
        @Test
        @DisplayName("same mean load, different peak, different score")
        void reserveIsIndependentOfLoad() {
            final EfficiencyInputs steady = inputs(1000.0f, 600.0f, 640.0f, 0.04f, true,
                    0.02f, true, false, 0.0f, true);
            final EfficiencyInputs spiky = inputs(1000.0f, 600.0f, 990.0f, 0.34f, true,
                    0.02f, true, false, 0.0f, true);

            final float a = EfficiencyGrader.calculate(steady, new ODEState()).compositeScore();
            final float b = EfficiencyGrader.calculate(spiky, new ODEState()).compositeScore();
            assertTrue(a - b > 15.0f,
                    "a network spiking to 99% of capacity scored " + b + " against " + a);
        }

        @Test
        @DisplayName("same overstress fraction, different episode length, different score")
        void resilienceIsIndependentOfReliability() {
            final EfficiencyInputs flickers = inputs(1000.0f, 700.0f, 1000.0f, 0.10f, true,
                    0.02f, true, false, 2.0f, true);
            final EfficiencyInputs stalls = inputs(1000.0f, 700.0f, 1000.0f, 0.10f, true,
                    0.02f, true, false, 110.0f, true);

            final float a = EfficiencyGrader.calculate(flickers, new ODEState()).compositeScore();
            final float b = EfficiencyGrader.calculate(stalls, new ODEState()).compositeScore();
            assertTrue(a - b > 5.0f,
                    "one long stall scored " + b + " against " + a + " for the same time lost");
        }
    }

    @Nested
    @DisplayName("The integrator, which used to assume its own timestep")
    class Integration {
        @Test
        @DisplayName("one long step lands exactly where many short ones land")
        void stepSizeDoesNotChangeTheAnswer() {
            final EfficiencyInputs in = healthy();

            final ODEState one = settled(20.0f);
            EfficiencyGrader.calculate(in, one, 200);

            final ODEState many = settled(20.0f);
            for (int i = 0; i < 10; i++) {
                EfficiencyGrader.calculate(in, many, 20);
            }

            assertEquals(one.currentE, many.currentE, 0.05f,
                    "splitting one 10 second step into ten moved the score by "
                            + Math.abs(one.currentE - many.currentE));
        }

        @Test
        @DisplayName("a long catch up step converges towards the target, never past it")
        void largeStepsAreStable() {
            final EfficiencyInputs in = healthy();
            final ODEState state = settled(0.0f);
            final float target = EfficiencyGrader.calculate(in, state, 20_000).equilibrium();
            assertTrue(state.currentE <= target + 0.01f,
                    "overshot the equilibrium: " + state.currentE + " past " + target);
            assertTrue(state.currentE > 0.0f, "a long step should still make progress");
        }

        @Test
        @DisplayName("the score falls faster than it climbs")
        void relaxationIsAsymmetric() {
            assertTrue(EfficiencyGrader.ALPHA_DOWN > EfficiencyGrader.ALPHA_UP);

            final EfficiencyInputs in = healthy();
            final float target = EfficiencyGrader.calculate(in, settled(50.0f), 40).equilibrium();

            final ODEState rising = settled(target - 20.0f);
            EfficiencyGrader.calculate(in, rising, 40);
            final float climbed = rising.currentE - (target - 20.0f);

            final ODEState falling = settled(target + 20.0f);
            EfficiencyGrader.calculate(in, falling, 40);
            final float dropped = (target + 20.0f) - falling.currentE;

            assertTrue(dropped > climbed,
                    "fell " + dropped + " but climbed " + climbed + " over the same gap");
        }

        @Test
        @DisplayName("an absence is a gap in observation, not an hour of penalty")
        void theStepIsClamped() {
            final EfficiencyInputs overstressed = inputs(1000.0f, 1400.0f, 1400.0f, 0.2f, true,
                    0.05f, true, true, 60.0f, true);
            final ODEState state = settled(90.0f);
            EfficiencyGrader.calculate(overstressed, state, 72_000);
            assertTrue(state.currentE > 0.0f,
                    "an hour away emptied the score to " + state.currentE);
        }

        @Test
        @DisplayName("the score stays inside [0, 100] under abuse")
        void scoreStaysBounded() {
            final EfficiencyInputs brutal = inputs(1000.0f, 9000.0f, 9000.0f, 3.0f, true,
                    2.0f, true, true, 600.0f, true);
            final ODEState state = settled(100.0f);
            for (int i = 0; i < 500; i++) {
                final EfficiencyScore s = EfficiencyGrader.calculate(brutal, state, 40);
                assertTrue(s.compositeScore() >= 0.0f && s.compositeScore() <= 100.0f,
                        "escaped the range at step " + i + ": " + s.compositeScore());
            }
        }
    }

    @Nested
    @DisplayName("The transient penalty, which used to be three orders of magnitude too small")
    class Penalty {
        @Test
        @DisplayName("a sudden swing in load actually costs something")
        void swingingTheLoadCostsPoints() {
            final EfficiencyInputs low = inputs(1000.0f, 550.0f, 800.0f, 0.03f, true,
                    0.02f, true, false, 0.0f, true);
            final EfficiencyInputs high = inputs(1000.0f, 800.0f, 800.0f, 0.03f, true,
                    0.02f, true, false, 0.0f, true);

            final ODEState calm = settled(80.0f);
            EfficiencyGrader.calculate(high, calm, 40);
            final float calmBefore = calm.currentE;
            EfficiencyGrader.calculate(high, calm, 40);
            final float calmMove = calm.currentE - calmBefore;

            final ODEState jumpy = settled(80.0f);
            EfficiencyGrader.calculate(low, jumpy, 40);
            final float jumpyBefore = jumpy.currentE;
            EfficiencyGrader.calculate(high, jumpy, 40);
            final float jumpyMove = jumpy.currentE - jumpyBefore;

            assertTrue(calmMove - jumpyMove > 0.5f,
                    "a 25% of capacity swing cost only " + (calmMove - jumpyMove) + " points");
        }

        @Test
        @DisplayName("the transient penalty is capped, so one wild sample cannot empty the score")
        void theTransientPenaltyIsCapped() {
            final EfficiencyInputs empty = inputs(1000.0f, 0.0f, 0.0f, 0.03f, true,
                    0.02f, true, false, 0.0f, true);
            final EfficiencyInputs full = inputs(1000.0f, 1000.0f, 1000.0f, 0.03f, true,
                    0.02f, true, false, 0.0f, true);
            final ODEState state = settled(100.0f);
            EfficiencyGrader.calculate(empty, state, 40);
            final float before = state.currentE;
            EfficiencyGrader.calculate(full, state, 40);
            final float cost = before - state.currentE;
            final float ceiling = EfficiencyGrader.PSI_TRANSIENT_CAP
                    * (EfficiencyGrader.DEFAULT_INTERVAL_TICKS / 20.0f)
                    + EfficiencyGrader.BETA_OVERSTRESS * 2.0f + 100.0f * 0.1f;
            assertTrue(cost <= ceiling, "a single swing cost " + cost + " points");
        }

        @Test
        @DisplayName("no penalty is charged on the very first sample, which has no predecessor")
        void firstSampleHasNoRateOfChange() {
            final ODEState state = new ODEState();
            assertEquals(ODEState.NO_PREVIOUS_U, state.previousU, 1.0E-6f);
            final EfficiencyScore s = EfficiencyGrader.calculate(healthy(), state, 40);
            assertEquals(s.equilibrium(), s.compositeScore(), 0.01f,
                    "the first sample should land on the equilibrium, not below it");
        }
    }

    @Nested
    @DisplayName("Warm start and the uptime memory")
    class WarmStart {
        @Test
        @DisplayName("a fresh panel reaches a true reading in well under a minute")
        void warmStartConvergesQuickly() {
            final EfficiencyInputs in = healthy();
            final ODEState state = new ODEState();
            EfficiencyScore s = null;
            for (int i = 0; i < EfficiencyGrader.WARMUP_OBSERVATIONS; i++) {
                s = EfficiencyGrader.calculate(in, state, 40);
            }
            assertEquals(s.equilibrium(), s.compositeScore(), 1.0f,
                    "still " + Math.abs(s.equilibrium() - s.compositeScore()) + " away after warmup");
            assertFalse(s.isWarmingUp(), "warmup should be finished by now");
        }

        @Test
        @DisplayName("warmup progress is reported honestly while it is happening")
        void warmupIsAdvertised() {
            final ODEState state = new ODEState();
            final EfficiencyScore first = EfficiencyGrader.calculate(healthy(), state, 40);
            assertTrue(first.isWarmingUp());
            assertTrue(first.warmup() < 1.0f);
        }

        @Test
        @DisplayName("the uptime memory is seeded from observed history, not from optimism")
        void uptimeIsSeeded() {
            final EfficiencyInputs thrashing = new EfficiencyInputs(1000.0f, 700.0f, 1000.0f,
                    0.1f, true, 0.02f, true, false, 8.0f, true, 0.60f, true, 0L);
            final ODEState state = new ODEState();
            EfficiencyGrader.calculate(thrashing, state, 40);
            assertEquals(0.40f, state.uptimeMemory, 0.02f,
                    "a network overstressed 60% of the window should not read as reliable");
        }

        @Test
        @DisplayName("seeding happens once, then the memory is the integrator's own")
        void uptimeIsSeededOnlyOnce() {
            final EfficiencyInputs thrashing = new EfficiencyInputs(1000.0f, 700.0f, 1000.0f,
                    0.1f, true, 0.02f, true, false, 8.0f, true, 0.60f, true, 0L);
            final ODEState state = new ODEState();
            EfficiencyGrader.calculate(thrashing, state, 40);
            final float afterSeed = state.uptimeMemory;
            EfficiencyGrader.calculate(thrashing, state, 40);
            assertTrue(state.uptimeMemory > afterSeed,
                    "a clean sample should recover the memory, not re-seed it");
            assertTrue(state.uptimeMemory - afterSeed < 0.05f,
                    "recovery should be gradual, not a jump");
        }
    }

    @Nested
    @DisplayName("End to end behaviour")
    class EndToEnd {
        @Test
        @DisplayName("no network is graded as unknown, not as a failure")
        void noNetworkIsNotAnF() {
            final EfficiencyInputs none = inputs(0.0f, 0.0f, 0.0f, 0.0f, false,
                    0.0f, false, false, 0.0f, false);
            final EfficiencyScore s = EfficiencyGrader.calculate(none, new ODEState());
            assertEquals(GradeScale.UNKNOWN_LETTER, s.grade());
            assertEquals(0.0f, s.observability(), 1.0E-6f);
        }

        @Test
        @DisplayName("a textbook network settles into the A band and stays there")
        void healthyNetworkSettlesHigh() {
            final ODEState state = new ODEState();
            EfficiencyScore s = null;
            for (int i = 0; i < 200; i++) {
                s = EfficiencyGrader.calculate(healthy(), state, 40);
            }
            assertTrue(s.compositeScore() >= 90.0f,
                    "settled at " + s.compositeScore() + " (" + s.grade() + ")");
            assertSame(String.class, s.grade().getClass());
            assertTrue(s.grade().startsWith("A"), "graded " + s.grade());
        }

        @Test
        @DisplayName("a network held over capacity ends up failing, and says why")
        void overstressedNetworkFails() {
            final EfficiencyInputs broken = inputs(1000.0f, 1300.0f, 1400.0f, 0.25f, true,
                    0.18f, true, true, 90.0f, true);
            final ODEState state = new ODEState();
            EfficiencyScore s = null;
            for (int i = 0; i < 200; i++) {
                s = EfficiencyGrader.calculate(broken, state, 40);
            }
            assertEquals("F", s.grade(), "graded " + s.grade() + " at " + s.compositeScore());
            assertTrue(s.getDimensionPercent(EfficiencyDimension.UTILIZATION) < 5.0f);
            assertTrue(s.getDimensionPercent(EfficiencyDimension.HEADROOM) < 5.0f);
        }

        @Test
        @DisplayName("an idle network scores worse than a busy one and better than a broken one")
        void idleSitsBetweenHealthyAndBroken() {
            final EfficiencyInputs idle = inputs(1000.0f, 90.0f, 140.0f, 0.05f, true,
                    0.02f, true, false, 0.0f, true);
            final EfficiencyInputs broken = inputs(1000.0f, 1300.0f, 1400.0f, 0.25f, true,
                    0.18f, true, true, 90.0f, true);

            final float healthyScore = settle(healthy());
            final float idleScore = settle(idle);
            final float brokenScore = settle(broken);

            assertTrue(healthyScore > idleScore,
                    "idle " + idleScore + " should not beat healthy " + healthyScore);
            assertTrue(idleScore > brokenScore,
                    "idle " + idleScore + " should beat broken " + brokenScore);
        }

        private float settle(EfficiencyInputs in) {
            final ODEState state = new ODEState();
            float last = 0.0f;
            for (int i = 0; i < 300; i++) {
                last = EfficiencyGrader.calculate(in, state, 40).compositeScore();
            }
            return last;
        }

        @Test
        @DisplayName("every dimension score reported is a real fraction")
        void dimensionScoresAreFractions() {
            final ODEState state = new ODEState();
            final EfficiencyScore s = EfficiencyGrader.calculate(healthy(), state, 40);
            for (EfficiencyDimension d : EfficiencyDimension.values()) {
                final float pct = s.getDimensionPercent(d);
                assertTrue(pct >= 0.0f && pct <= 100.0f, d + " reported " + pct + "%");
            }
        }
    }

    @Nested
    @DisplayName("The trace behind the live math page, which must be the step that was taken")
    class Trace {
        @Test
        @DisplayName("the trace agrees with the score it came with")
        void traceMatchesTheScore() {
            final ODEState state = settled(60.0f);
            final EfficiencyGrader.Evaluation ev = EfficiencyGrader.evaluate(healthy(), state, 40);
            final EfficiencyTrace t = ev.trace();

            assertEquals(ev.score().compositeScore(), t.eNew, 1.0E-4f, "score and trace disagree");
            assertEquals(ev.score().equilibrium(), t.equilibrium, 1.0E-4f, "E_eq disagrees");
            assertEquals(ev.score().observability(), t.observability, 1.0E-4f, "observability disagrees");
            assertEquals(ev.score().knownMask(), t.knownMask, "known mask disagrees");
            assertEquals(state.currentE, t.eNew, 1.0E-4f, "the integrator ended somewhere else");
            for (EfficiencyDimension d : EfficiencyDimension.values()) {
                assertEquals(ev.score().dimensionScores()[d.ordinal()], t.phi[d.ordinal()], 1.0E-5f,
                        d + " differs between score and trace");
            }
        }

        @Test
        @DisplayName("the printed steps chain into the printed answer")
        void theStepsChain() {
            final EfficiencyInputs spiky = inputs(1000.0f, 1200.0f, 1300.0f, 0.3f, true,
                    0.1f, true, true, 45.0f, true);
            final ODEState state = settled(80.0f);
            state.previousU = 0.4f;
            final EfficiencyTrace t = EfficiencyGrader.evaluate(spiky, state, 40).trace();

            assertEquals(t.ePrev + t.relaxBlend * (t.equilibrium - t.ePrev), t.eRelaxed, 1.0E-3f,
                    "the relaxation line does not produce the number after it");
            assertEquals(t.eRelaxed - t.penaltyPoints, t.eBeforeClamp, 1.0E-3f,
                    "the penalty line does not produce the number after it");
            assertEquals(t.psi * t.dtSeconds, t.penaltyPoints, 1.0E-4f,
                    "points and points per second were mixed up, which is the v1 bug");
            assertTrue(t.penaltyPoints > 0.0f, "an overstressed spiky network should be penalised");
        }

        @Test
        @DisplayName("the printed terms sum to the printed mean")
        void termsSumToTheMean() {
            final EfficiencyInputs sparse = inputs(1000.0f, 600.0f, 700.0f, 0.0f, false,
                    0.0f, false, false, 0.0f, false);
            final EfficiencyTrace t = EfficiencyGrader.evaluate(sparse, settled(50.0f), 40).trace();

            float sum = 0.0f;
            float weight = 0.0f;
            for (EfficiencyDimension d : EfficiencyDimension.values()) {
                sum += t.term[d.ordinal()];
                weight += t.weightApplied[d.ordinal()];
                if (!t.isKnown(d)) {
                    assertEquals(0.0f, t.weightApplied[d.ordinal()], 0.0f,
                            d + " was unmeasured but still carried weight");
                }
            }
            assertEquals(t.meanSum, sum, 1.0E-4f, "the terms do not add up to the sum shown");
            assertEquals(1.0f, weight, 1.0E-3f, "renormalised weights should sum to 1");
            assertEquals(100.0f * (float) Math.pow(t.meanSum, 1.0f / EfficiencyGrader.KAPPA),
                    t.equilibrium, 0.05f, "E_eq is not the root of the sum shown");
        }

        @Test
        @DisplayName("no network produces no trace at all")
        void noNetworkHasNoTrace() {
            final EfficiencyInputs none = inputs(0.0f, 0.0f, 0.0f, 0.0f, false, 0.0f, false,
                    false, 0.0f, false);
            assertNull(EfficiencyGrader.evaluate(none, new ODEState(), 40).trace());
        }

        @Test
        @DisplayName("a capped transient is recorded as capped")
        void theCapIsVisible() {
            final ODEState state = settled(90.0f);
            state.previousU = 0.0f;
            final EfficiencyInputs slam = inputs(1000.0f, 1000.0f, 1000.0f, 0.4f, true,
                    0.05f, true, false, 0.0f, true);
            final EfficiencyTrace t = EfficiencyGrader.evaluate(slam, state, 40).trace();

            assertEquals(EfficiencyGrader.PSI_TRANSIENT_CAP, t.swingApplied, 1.0E-3f);
            assertTrue(t.swingRaw > t.swingApplied, "the uncapped value was not kept");
        }
    }
}
