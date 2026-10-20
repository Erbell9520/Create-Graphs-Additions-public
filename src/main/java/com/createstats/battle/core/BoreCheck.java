package com.createstats.battle.core;

import java.util.List;
import java.util.Objects;

// walk CBC's own fire rules cell by cell. counting the load blew up cannons before 1.14.94
public final class BoreCheck {
    public static final int SETTLE = 10;

    private BoreCheck() {
    }

    public enum Content { EMPTY, CHARGE, SHELL, OTHER }

    public record Cell(Content content, boolean thick, float power, float stress, boolean lights, boolean safe,
                       boolean complete, float addedPower, float minPower, boolean canSquib) {
        public static Cell empty(boolean thick) {
            return new Cell(Content.EMPTY, thick, 0, 0, false, true, false, 0, 0, false);
        }

        public static Cell charge(boolean thick, float power, float stress, boolean lights, boolean safe) {
            return new Cell(Content.CHARGE, thick, power, stress, lights, safe, false, 0, 0, false);
        }

        public static Cell shell(boolean thick, boolean valid, boolean complete, float addedPower, float minPower,
                                 boolean canSquib) {
            return new Cell(Content.SHELL, thick, 0, 0, false, valid, complete, addedPower, minPower, canSquib);
        }

        public static Cell other(boolean thick) {
            return new Cell(Content.OTHER, thick, 0, 0, false, true, false, 0, 0, false);
        }
    }

    public record Chances(boolean canFail, float squib, float barrelBurst, float overload, float interrupted) {
        public static final Chances STOCK = new Chances(true, 0.25f, 0.2f, 0.5f, 0.33f);
    }

    public record Integrated(boolean safe, float power, float stress) {
    }

    public record Bore(List<Cell> cells, boolean breechOpen, boolean dropMortar, int maxSafe, double minVelocityPerBarrel,
                       float minimumSpread, Chances chances, boolean muzzleBlocked, Integrated integrated) {
        public Bore {
            cells = List.copyOf(cells);
        }
    }

    public enum Outcome { FIRES, WONT_FIRE, UNSAFE }

    public enum Reason {
        NONE(Outcome.FIRES),

        BREECH_OPEN(Outcome.WONT_FIRE),

        DROP_MORTAR(Outcome.WONT_FIRE),

        NOTHING(Outcome.WONT_FIRE),

        DEAD_CHARGE(Outcome.WONT_FIRE),

        STUCK_SHELL(Outcome.WONT_FIRE),

        NO_SHELL(Outcome.WONT_FIRE),

        NO_CHARGE(Outcome.WONT_FIRE),

        OVERLOAD(Outcome.UNSAFE),

        CHARGE_IN_BARREL(Outcome.UNSAFE),

        CHARGE_ORDER(Outcome.UNSAFE),

        OBSTRUCTED(Outcome.UNSAFE),

        BAD_SHELL(Outcome.UNSAFE),

        UNDERCHARGED(Outcome.UNSAFE);

        private final Outcome outcome;

        Reason(Outcome outcome) {
            this.outcome = outcome;
        }

        public Outcome outcome() {
            return outcome;
        }
    }

    public record Verdict(Reason reason, float speed, double burst, double dud, float stress, int maxSafe, int cell) {
        public boolean fires() {
            return reason == Reason.NONE;
        }
    }

    public static Verdict judge(Bore b) {
        if (b.dropMortar()) return stop(Reason.DROP_MORTAR, b, 0, 0, 0, -1);
        Verdict walked = walk(b);
        if (!b.breechOpen() || walked.reason().outcome() == Outcome.UNSAFE) return walked;
        return new Verdict(Reason.BREECH_OPEN, walked.speed(), 0, 0, walked.stress(), b.maxSafe(), -1);
    }

    private static Verdict walk(Bore b) {
        List<Cell> cells = b.cells();
        if (cells.isEmpty()) return stop(Reason.NOTHING, b, 0, 0, 0, 0);
        Chances ch = b.chances();
        boolean canFail = ch.canFail();
        float charges = 0;
        float stress = 0;
        boolean chargeSeen = false;
        boolean gap = false;
        int shellBlocks = 0;
        int lastShell = -1;
        Cell shell = null;
        int travelled = 0;

        double reach = 1;
        double burst = 0;
        double dud = 0;
        Reason risk = null;
        int riskCell = -1;
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            Content kind = c.content() == Content.SHELL && shell != null ? Content.OTHER : c.content();
            switch (kind) {
                case EMPTY -> {
                    if (i == 0) return stop(stuck(cells, Reason.NOTHING), b, 0, 0, 0, 0);
                    if (shell == null) {
                        if (shellBlocks == 0) {
                            gap = true;
                            charges = Math.max(charges - 1f, 0f);
                        } else if (canFail) {
                            return stop(Reason.BAD_SHELL, b, burst + reach, dud, stress, i);
                        }
                    } else {
                        travelled++;
                        if (canFail && shell.canSquib() && ch.squib() > 0 && mayGetStuck(b.minVelocityPerBarrel(), charges, travelled)) {
                            dud += reach * ch.squib();
                            reach *= 1 - ch.squib();
                        }
                    }
                }
                case CHARGE -> {
                    if (i == 0 && !c.lights()) return stop(stuck(cells, Reason.DEAD_CHARGE), b, 0, 0, 0, 0);
                    chargeSeen = true;
                    if (c.safe()) {
                        charges += Math.max(0f, c.power());
                        stress += c.stress();
                    } else if (canFail) {
                        return stop(Reason.CHARGE_ORDER, b, burst + reach, dud, stress, i);
                    }
                    if (canFail) {
                        double barrel = !c.thick() ? ch.barrelBurst() : 0;
                        double over = stress > b.maxSafe() ? ch.overload() : 0;
                        double p = 1 - (1 - barrel) * (1 - over);
                        if (p > 0) {
                            burst += reach * p;
                            reach *= 1 - p;
                            if (risk == null) {
                                risk = barrel > 0 ? Reason.CHARGE_IN_BARREL : Reason.OVERLOAD;
                                riskCell = i;
                            }
                        }
                        if (gap && ch.interrupted() > 0) {
                            dud += reach * ch.interrupted();
                            reach *= 1 - ch.interrupted();
                        }
                    }
                    gap = false;
                }
                case SHELL -> {
                    if (canFail && gap && ch.interrupted() > 0) {
                        dud += reach * ch.interrupted();
                        reach *= 1 - ch.interrupted();
                    }
                    shellBlocks++;
                    lastShell = i;
                    if (!c.safe()) return stop(Reason.BAD_SHELL, b, canFail ? burst + reach : burst, dud, stress, i);
                    if (c.complete()) {
                        shell = c;
                        charges += c.addedPower();
                        if (charges <= 0 || canFail && charges < c.minPower()) {
                            return stop(chargeSeen ? Reason.UNDERCHARGED : Reason.NO_CHARGE, b, burst, dud, stress, i);
                        }
                    }
                    gap = false;
                }
                case OTHER -> {
                    if (canFail) return stop(Reason.OBSTRUCTED, b, burst + reach, dud, stress, i);
                }
            }
        }
        if (shell == null) {
            if (shellBlocks > 0) return stop(Reason.BAD_SHELL, b, canFail ? burst + reach : burst, dud, stress, lastShell);
            return stop(chargeSeen ? Reason.NO_SHELL : Reason.NOTHING, b, 0, 0, stress, -1);
        }
        if (charges < b.minimumSpread()) charges = b.minimumSpread();
        Integrated in = b.integrated();
        if (in != null) {
            if (in.safe()) {
                charges += Math.max(0f, in.power());
                stress += in.stress();
            } else if (canFail) {
                return stop(Reason.CHARGE_ORDER, b, burst + reach, dud, stress, lastShell);
            }
        }
        if (b.muzzleBlocked() && canFail) return stop(Reason.OBSTRUCTED, b, burst + reach, dud, stress, cells.size());
        if (burst > 0) return new Verdict(risk, charges, burst, dud, stress, b.maxSafe(), riskCell);
        return new Verdict(Reason.NONE, charges, 0, dud, stress, b.maxSafe(), -1);
    }

    private static Verdict stop(Reason why, Bore b, double burst, double dud, float stress, int cell) {
        return new Verdict(why, 0, burst, dud, stress, b.maxSafe(), cell);
    }

    private static Reason stuck(List<Cell> cells, Reason otherwise) {
        Cell first = cells.get(0);
        if (first.content() == Content.CHARGE && first.power() > 0) return otherwise;
        for (int i = 1; i < cells.size(); i++) if (cells.get(i).content() == Content.SHELL) return Reason.STUCK_SHELL;
        return otherwise;
    }

    static boolean mayGetStuck(double minVelocityPerBarrel, float charges, int travelled) {
        if (minVelocityPerBarrel < 0) return true;
        return travelled >= 1 && minVelocityPerBarrel > charges / travelled;
    }

    public static boolean loaded(Bore b) {
        boolean shell = false;
        boolean charge = false;
        for (Cell c : b.cells()) {
            if (c.content() == Content.SHELL) shell = true;
            if (c.content() == Content.CHARGE && c.power() > 0) charge = true;
        }
        return shell && charge;
    }

    public static int signature(Bore b) {
        return Objects.hash(b.cells(), b.breechOpen(), b.dropMortar());
    }

    public static final class Watch {
        private boolean known;
        private int signature;

        private long readySince = -1;

        public boolean observe(Bore bore, Verdict verdict, long now) {
            int sig = signature(bore);
            boolean changed = !known || sig != signature;
            known = true;
            signature = sig;
            if (changed || !verdict.fires()) readySince = -1;
            if (verdict.fires() && readySince < 0) readySince = now;
            return changed;
        }

        public boolean settled(long now) {
            return readySince >= 0 && now - readySince >= SETTLE;
        }

        public void reset() {
            readySince = -1;
        }
    }
}
