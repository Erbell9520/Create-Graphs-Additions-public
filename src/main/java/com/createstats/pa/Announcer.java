package com.createstats.pa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.alarm.annunciator.AlarmSeverity;

// only one thing can be heard at a time. overlapping is noise
public final class Announcer {
    public static final int REPEAT_GUARD_TICKS = 240;

    public static final int MAX_WAIT_TICKS = 600;

    private static final int MAX_QUEUE = 12;

    public interface Gate {
        Gate OPEN = new Gate() {
            @Override
            public boolean open() {
                return true;
            }

            @Override
            public boolean affords(Utterance utterance) {
                return true;
            }
        };

        boolean open();

        boolean affords(Utterance utterance);
    }

    private record Queued(Utterance utterance, long queuedAt, long heldAt) {
    }

    private final Deque<Queued> queue = new ArrayDeque<>();
    private final Map<String, Long> lastSpokenAt = new LinkedHashMap<>();

    private Utterance speaking = Utterance.NOTHING;
    private long speakingStartedAt;
    private long speakingUntil;
    private boolean acknowledged;
    private long acknowledgedAt;

    private long heldTicks;

    private boolean held;
    private boolean starved;

    public boolean offer(Utterance utterance, long now) {
        if (utterance == null || utterance.isEmpty()) {
            return false;
        }

        if (this.acknowledged && utterance.severity() != AlarmSeverity.CRITICAL) {
            if (now - this.acknowledgedAt < REPEAT_GUARD_TICKS * 2L) {
                return false;
            }
            this.acknowledged = false;
        }
        final Long last = this.lastSpokenAt.get(utterance.dedupeKey());
        if (last != null && now - last < REPEAT_GUARD_TICKS
                && utterance.severity() != AlarmSeverity.CRITICAL) {
            return false;
        }

        for (Queued q : this.queue) {
            if (q.utterance().dedupeKey().equals(utterance.dedupeKey())) {
                return false;
            }
        }
        if (this.queue.size() >= MAX_QUEUE && !dropWeakest(utterance.severity())) {
            return false;
        }
        this.queue.addLast(new Queued(utterance, now, this.heldTicks));
        return true;
    }

    private long ageOf(Queued q, long now) {
        return now - q.queuedAt() - (this.heldTicks - q.heldAt());
    }

    public Utterance tick(long now) {
        return this.tick(now, Gate.OPEN);
    }

    public Utterance tick(long now, Gate gate) {
        this.starved = false;

        if (!gate.open()) {
            this.held = !this.queue.isEmpty();
            this.heldTicks++;
            if (now >= this.speakingUntil) {
                this.speaking = Utterance.NOTHING;
            }
            return null;
        }
        this.held = false;

        this.queue.removeIf(q -> this.ageOf(q, now) > MAX_WAIT_TICKS);

        if (this.queue.isEmpty()) {
            if (now >= this.speakingUntil) {
                this.speaking = Utterance.NOTHING;
            }
            return null;
        }

        final Queued best = pickBest();
        final boolean busy = now < this.speakingUntil && !this.speaking.isEmpty();
        if (busy && !best.utterance().bargesIn(this.speaking)) {
            return null;
        }
        if (!gate.affords(best.utterance())) {
            this.starved = true;
            return null;
        }

        this.queue.remove(best);
        this.speaking = best.utterance();
        this.speakingStartedAt = now;
        this.speakingUntil = now + best.utterance().estimatedTicks();
        this.lastSpokenAt.put(best.utterance().dedupeKey(), now);
        pruneGuards(now);
        return this.speaking;
    }

    private Queued pickBest() {
        Queued best = null;
        for (Queued q : this.queue) {
            if (best == null
                    || q.utterance().severity().ordinal() > best.utterance().severity().ordinal()
                    || (q.utterance().severity() == best.utterance().severity()
                        && q.queuedAt() < best.queuedAt())) {
                best = q;
            }
        }
        return best;
    }

    private boolean dropWeakest(AlarmSeverity incoming) {
        Queued weakest = null;
        for (Queued q : this.queue) {
            if (weakest == null
                    || q.utterance().severity().ordinal() < weakest.utterance().severity().ordinal()) {
                weakest = q;
            }
        }
        if (weakest == null || weakest.utterance().severity().ordinal() >= incoming.ordinal()) {
            return false;
        }
        this.queue.remove(weakest);
        return true;
    }

    private void pruneGuards(long now) {
        this.lastSpokenAt.entrySet().removeIf(e -> now - e.getValue() > REPEAT_GUARD_TICKS * 4L);
    }

    public void acknowledge(long now) {
        this.acknowledged = true;
        this.acknowledgedAt = now;
        this.queue.clear();
        this.speaking = Utterance.NOTHING;
        this.speakingUntil = now;
        this.held = false;
        this.starved = false;
    }

    public boolean isAcknowledged() {
        return this.acknowledged;
    }

    public boolean isSpeaking(long now) {
        return !this.speaking.isEmpty() && now < this.speakingUntil;
    }

    public Utterance speaking() {
        return this.speaking;
    }

    public int speakingWordIndex(long now) {
        if (this.speaking.isEmpty() || now >= this.speakingUntil) {
            return -1;
        }
        final long elapsed = Math.max(0, now - this.speakingStartedAt);
        return (int) Math.min(this.speaking.clips().size() - 1L,
                elapsed / Utterance.TICKS_PER_CLIP);
    }

    public int queued() {
        return this.queue.size();
    }

    public boolean isHeld() {
        return this.held;
    }

    public boolean isStarved() {
        return this.starved;
    }

    public List<Waiting> peekQueue(long now) {
        final List<Waiting> out = new ArrayList<>();
        for (Queued q : this.queue) {
            out.add(new Waiting(q.utterance(), (int) Math.max(0, this.ageOf(q, now))));
        }

        out.sort((a, b) -> {
            final int bySeverity = Integer.compare(b.utterance().severity().ordinal(),
                    a.utterance().severity().ordinal());
            return bySeverity != 0 ? bySeverity : Integer.compare(b.waitedTicks(), a.waitedTicks());
        });
        return out;
    }

    public record Waiting(Utterance utterance, int waitedTicks) {
    }
}
