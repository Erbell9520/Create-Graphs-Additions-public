package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicRig;

import java.util.ArrayList;
import java.util.List;

public final class RigScript {
    public record Frame(String target, int radius, int stored, SeismicRig.State state, boolean coupled, int arriving,
                        int intake, int dropTick, int fallTicks, float dropHeight, float blow, int impactTick, int reach,
                        int[] echoes) {
        public boolean tuned() {
            return !target.isEmpty();
        }

        public double height() {
            return SeismicCharge.heightOf(stored);
        }

        public SeismicRig.Lamp lamp() {
            return SeismicRig.lamp(state);
        }
    }

    private record Tune(int tick, String target, int radius) {
    }

    private final List<Tune> tunes = new ArrayList<>();
    private final List<Integer> pulls = new ArrayList<>();
    private String target;
    private int radius;
    private int stored;
    private boolean coupled = true;
    private int feedFrom = Integer.MAX_VALUE;
    private int feedRate;
    private SeismicPonderSurvey.Survey survey;
    private Frame[] frames;

    private RigScript(String target, int radius, int stored) {
        this.target = target;
        this.radius = SeismicCharge.clampRadius(radius);
        this.stored = stored;
    }

    public static RigScript of(String target, int radius, int stored) {
        return new RigScript(target, radius, stored);
    }

    public RigScript uncoupled() {
        coupled = false;
        return this;
    }

    public RigScript tune(int tick, String station, int range) {
        tunes.add(new Tune(tick, station, range));
        return this;
    }

    public RigScript feed(int tick, int fePerTick) {
        feedFrom = tick;
        feedRate = fePerTick;
        return this;
    }

    public RigScript pull(int tick) {
        pulls.add(tick);
        return this;
    }

    public RigScript ground(SeismicPonderSurvey.Survey s) {
        survey = s;
        return this;
    }

    public RigScript play(int length) {
        frames = new Frame[length];
        SeismicRig.State state = SeismicRig.State.of(0);
        int arrivedBefore = 0;
        double smoothed = 0.0;
        int dropTick = -1;
        int fall = 0;
        float height = 0.0f;
        float blow = 0.0f;
        int impact = -1;
        int reach = 0;
        int[] echoes = new int[0];
        int dropCharge = 0;
        for (int t = 0; t < length; t++) {
            for (final Tune tune : tunes) {
                if (tune.tick() == t) {
                    target = tune.target();
                    radius = SeismicCharge.clampRadius(tune.radius());
                }
            }
            final boolean tuned = !target.isEmpty();
            final boolean dropping = state == SeismicRig.State.DROPPING;

            int arriving = 0;
            if (SeismicRig.winchTakes(tuned, coupled, dropping, false, SeismicCharge.ready(radius, stored))) {
                arriving = SeismicCharge.winchTake(radius, stored, t >= feedFrom ? feedRate : 0, 0);
                stored += arriving;
            }

            if (pulls.contains(t) && tuned && state != SeismicRig.State.DROPPING) {
                dropTick = t;
                if (stored <= 0) {
                    fall = 0;
                    height = 0.0f;
                } else {
                    dropCharge = stored;
                    height = (float) SeismicCharge.heightOf(stored);
                    fall = SeismicCharge.fallTicks(height);
                    blow = (float) com.createstats.seismic.SeismicTrace.blowStrength(SeismicCharge.reachOf(stored));
                    stored = 0;
                    state = SeismicRig.State.DROPPING;
                    impact = -1;
                    reach = 0;
                    echoes = new int[0];
                }
            }

            if (state == SeismicRig.State.DROPPING && t >= dropTick + fall) {
                state = SeismicRig.State.WAITING;
                impact = t;
                reach = coupled ? SeismicCharge.surveyRadius(radius, dropCharge) : 0;
                echoes = reach > 0 && survey != null ? survey.echoes().clone() : new int[0];
                dropCharge = 0;
            }
            if (state != SeismicRig.State.DROPPING) {
                state = SeismicRig.state(tuned, coupled, false, false, SeismicCharge.ready(radius, stored), arrivedBefore);
            }
            smoothed += (arrivedBefore - smoothed) * 0.25;
            frames[t] = new Frame(target, radius, stored, state, coupled, arriving, (int) Math.round(smoothed), dropTick,
                    fall, height, blow, impact, reach, echoes);
            arrivedBefore = arriving;
        }
        return this;
    }

    public Frame at(int tick) {
        return frames[Math.max(0, Math.min(frames.length - 1, tick))];
    }

    public int length() {
        return frames.length;
    }

    public int first(SeismicRig.State state, int from) {
        for (int t = Math.max(0, from); t < frames.length; t++) {
            if (frames[t].state() == state) {
                return t;
            }
        }
        return -1;
    }
}
