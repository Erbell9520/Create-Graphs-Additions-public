package com.createstats.stormcoil;

// two studs two circuits, it never conducts. the breaker lesson
public final class CoilStores {
    public record Terms(int zapCost, int capacitorSize, int inCap, int outCap, boolean zapsWanted, int strikeFe,
                        int bankSize, int targets) {
    }

    public enum Duty {
        LOAD, SOURCE
    }

    private int capacitor;
    private int bank;

    private long tick = Long.MIN_VALUE;
    private int inThisTick;
    private int outThisTick;
    private int inLastTick;
    private int outLastTick;

    private long totalIn;
    private long totalOut;
    private long totalStruck;
    private long totalZapped;

    public int capacitor() {
        return capacitor;
    }

    public int bank() {
        return bank;
    }

    public void restore(int capacitor, int bank) {
        this.capacitor = Math.max(0, capacitor);
        this.bank = Math.max(0, bank);
    }

    public boolean inDraws(Terms t) {
        return t.zapsWanted() && bank < t.zapCost() && capacitor < t.capacitorSize();
    }

    public int ratedDraw(Terms t, boolean engaged) {
        if (!t.zapsWanted() || bank >= t.zapCost()) {
            return 0;
        }
        if (!engaged && capacitor >= t.capacitorSize()) {
            return 0;
        }
        return Math.min(t.inCap(), CoilLaw.volleyRate(t.zapCost(), t.targets()));
    }

    public Duty duty(Terms t) {
        return bank > 0 && (bank >= t.zapCost() || !t.zapsWanted()) ? Duty.SOURCE : Duty.LOAD;
    }

    public int receiveIn(int offered, boolean simulate, long now, Terms t) {
        roll(now);
        if (offered <= 0 || !inDraws(t)) {
            return 0;
        }
        final int room = t.capacitorSize() - capacitor;
        final int allowance = Math.max(0, t.inCap() - inThisTick);
        final int n = Math.max(0, Math.min(offered, Math.min(room, allowance)));
        if (!simulate && n > 0) {
            capacitor += n;
            inThisTick += n;
            totalIn += n;
        }
        return n;
    }

    public int extractOut(int wanted, boolean simulate, long now, Terms t) {
        roll(now);
        if (wanted <= 0) {
            return 0;
        }
        final int allowance = Math.max(0, t.outCap() - outThisTick);
        final int n = Math.max(0, Math.min(wanted, Math.min(bank, allowance)));
        if (!simulate && n > 0) {
            bank -= n;
            outThisTick += n;
            totalOut += n;
        }
        return n;
    }

    public int strike(Terms t) {
        final int n = Math.max(0, Math.min(t.strikeFe(), t.bankSize() - bank));
        bank += n;
        totalStruck += n;
        return n;
    }

    public boolean canPay(int cost) {
        return cost > 0 && (long) bank + capacitor >= cost;
    }

    public boolean payZap(int cost) {
        if (!canPay(cost)) {
            return false;
        }
        final int fromBank = Math.min(bank, cost);
        bank -= fromBank;
        capacitor -= cost - fromBank;
        totalZapped += cost;
        return true;
    }

    public int inDrawn(long now) {
        return now == tick ? inLastTick : now == tick + 1 ? inThisTick : 0;
    }

    public int outGiven(long now) {
        return now == tick ? outLastTick : now == tick + 1 ? outThisTick : 0;
    }

    public long totalIn() {
        return totalIn;
    }

    public long totalOut() {
        return totalOut;
    }

    public long totalStruck() {
        return totalStruck;
    }

    public long totalZapped() {
        return totalZapped;
    }

    private void roll(long now) {
        if (now == tick) {
            return;
        }
        final boolean next = tick != Long.MIN_VALUE && now == tick + 1;
        inLastTick = next ? inThisTick : 0;
        outLastTick = next ? outThisTick : 0;
        inThisTick = 0;
        outThisTick = 0;
        tick = now;
    }
}
