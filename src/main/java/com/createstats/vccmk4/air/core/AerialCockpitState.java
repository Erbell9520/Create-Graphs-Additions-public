package com.createstats.vccmk4.air.core;

// small, it goes to everyone near the ship every tick
public record AerialCockpitState(int flags, int mode, int leave, int orderQ, int engineQ, int turnQ, int climbQ,
                                 int trimQ, int altQ, int heldQ, int climbRateQ, int airQ, int headingQ,
                                 int heldHeadingQ, int pitchQ, int rollQ, int yawRateQ, int feelTurn) {
    public static final int PILOTED = 1;

    public static final int ON_CRAFT = 2;

    public static final int CAUTION = 4;

    public static final int HELD_ALT = 8;

    public static final int LIVE = 16;

    public static final int BYTES = 25;

    public static final int NONE16 = 0xFFFF;

    private static final int ORDER = 120;
    private static final int STICK = 127;
    private static final double ALT_STEPS = 8.0;
    private static final double CLIMB_STEPS = 16.0;
    private static final double AIR_STEPS = 8.0;
    private static final double DEG_STEPS = 4.0;

    public static final AerialCockpitState IDLE = new AerialCockpitState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NONE16,
            0, 0, 0, 8);

    public static AerialCockpitState of(boolean piloted, boolean onCraft, boolean caution, AerialPilot.Mode mode,
                                        LeaveMode leave, double order, double engine, double turn, double climb,
                                        double trim, double altitude, double held, double climbRate, double air,
                                        double heading, double heldHeading, double pitch, double roll, double yawRate,
                                        double feelTurn) {
        int f = LIVE;
        if (piloted) f |= PILOTED;
        if (onCraft) f |= ON_CRAFT;
        if (caution) f |= CAUTION;
        if (Double.isFinite(held)) f |= HELD_ALT;
        return new AerialCockpitState(f, mode == null ? 0 : mode.ordinal(), leave == null ? 0 : leave.ordinal(),
                q(order, ORDER, ORDER), q(engine, ORDER, ORDER), q(turn, STICK, STICK), q(climb, STICK, STICK),
                q(trim, STICK, STICK), s16(altitude * ALT_STEPS), Double.isFinite(held) ? s16(held * ALT_STEPS) : 0,
                s8(climbRate * CLIMB_STEPS), s8(air * AIR_STEPS), compass(heading),
                Double.isFinite(heldHeading) ? compass(heldHeading) : NONE16, s8(pitch * DEG_STEPS), s8(roll * DEG_STEPS),
                s8(yawRate * DEG_STEPS), (int) Math.max(1, Math.min(127, Math.round(Double.isFinite(feelTurn) ? feelTurn : 8.0))));
    }

    private static int q(double v, int scale, int max) {
        if (!Double.isFinite(v)) return 0;
        return (int) Math.max(-max, Math.min(max, Math.round(v * scale)));
    }

    private static int s8(double v) {
        if (!Double.isFinite(v)) return 0;
        return (int) Math.max(-127, Math.min(127, Math.round(v)));
    }

    private static int s16(double v) {
        if (!Double.isFinite(v)) return 0;
        return (int) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, Math.round(v)));
    }

    private static int compass(double deg) {
        if (!Double.isFinite(deg)) return 0;
        double d = deg % 360.0;
        if (d < 0) d += 360.0;
        return (int) Math.round(d * 100.0) % 36000;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public boolean live() {
        return has(LIVE);
    }

    public boolean piloted() {
        return has(PILOTED);
    }

    public boolean onCraft() {
        return has(ON_CRAFT);
    }

    public boolean caution() {
        return has(CAUTION);
    }

    public AerialPilot.Mode modeValue() {
        return AerialPilot.Mode.byOrdinal(mode);
    }

    public LeaveMode leaveValue() {
        LeaveMode[] v = LeaveMode.values();
        return leave >= 0 && leave < v.length ? v[leave] : LeaveMode.ANCHOR;
    }

    public double order() {
        return orderQ / (double) ORDER;
    }

    public double engine() {
        return engineQ / (double) ORDER;
    }

    public double turn() {
        return turnQ / (double) STICK;
    }

    public double climb() {
        return climbQ / (double) STICK;
    }

    public double trim() {
        return trimQ / (double) STICK;
    }

    public double altitude() {
        return altQ / ALT_STEPS;
    }

    public double held() {
        return has(HELD_ALT) ? heldQ / ALT_STEPS : Double.NaN;
    }

    public double climbRate() {
        return climbRateQ / CLIMB_STEPS;
    }

    public double air() {
        return airQ / AIR_STEPS;
    }

    public double heading() {
        return headingQ / 100.0;
    }

    public double heldHeading() {
        return heldHeadingQ == NONE16 ? Double.NaN : heldHeadingQ / 100.0;
    }

    public double pitch() {
        return pitchQ / DEG_STEPS;
    }

    public double roll() {
        return rollQ / DEG_STEPS;
    }

    public double yawRate() {
        return yawRateQ / DEG_STEPS;
    }

    public byte[] encode() {
        return new byte[]{
                (byte) flags, (byte) (mode | (leave << 4)), (byte) orderQ, (byte) engineQ, (byte) turnQ, (byte) climbQ,
                (byte) trimQ, (byte) (altQ >> 8), (byte) altQ, (byte) (heldQ >> 8), (byte) heldQ, (byte) climbRateQ,
                (byte) airQ, (byte) (headingQ >> 8), (byte) headingQ, (byte) (heldHeadingQ >> 8), (byte) heldHeadingQ,
                (byte) pitchQ, (byte) rollQ, (byte) yawRateQ, (byte) feelTurn, 0, 0, 0, 0};
    }

    public static AerialCockpitState decode(byte[] b) {
        if (b == null || b.length < BYTES) return IDLE;
        int modeLeave = b[1] & 0xFF;
        int heldHeading = ((b[15] & 0xFF) << 8) | (b[16] & 0xFF);
        int heading = ((b[13] & 0xFF) << 8) | (b[14] & 0xFF);
        return new AerialCockpitState(b[0] & 0xFF, Math.min(modeLeave & 0x0F, AerialPilot.Mode.values().length - 1),
                Math.min(modeLeave >> 4, LeaveMode.values().length - 1), clamp(b[2], ORDER), clamp(b[3], ORDER),
                clamp(b[4], STICK), clamp(b[5], STICK), clamp(b[6], STICK), (short) (((b[7] & 0xFF) << 8) | (b[8] & 0xFF)),
                (short) (((b[9] & 0xFF) << 8) | (b[10] & 0xFF)), b[11], b[12], heading % 36000,
                heldHeading == NONE16 ? NONE16 : heldHeading % 36000, b[17], b[18], b[19], Math.max(1, b[20] & 0x7F));
    }

    private static int clamp(int v, int max) {
        return Math.max(-max, Math.min(max, v));
    }
}
