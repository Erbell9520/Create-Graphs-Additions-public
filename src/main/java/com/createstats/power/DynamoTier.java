package com.createstats.power;

/**
 * The three grades of rotation-to-FE generator, and what each trades away.
 *
 * {@link #STANDARD} is pinned to the Crafts and Additions alternator: <b>360 FE/t at 256 RPM</b>,
 * Create's top speed. matching a generator players already own is what makes the numbers mean
 * anything - a figure picked in isolation is just a number (the mod's own dynamo used to make
 * 1024 FE/t, nearly three alternators, without that ever being a decision anyone actually made).
 *
 * power alone would make this a straight upgrade ladder where the only question is what you can
 * afford. so the axis that actually matters is <b>FE per unit of stress</b>, and the cheap one
 * wins it:
 *
 * <table>
 *   <tr><th>Tier</th><th>Power</th><th>Stress</th><th>FE per SU</th><th>At 256 RPM</th></tr>
 *   <tr><td>Open Frame</td><td>x0.75</td><td>x0.50</td><td>0.0330</td><td>270 FE/t, 8192 SU</td></tr>
 *   <tr><td>Kinetic</td><td>x1.00</td><td>x1.00</td><td>0.0220</td><td>360 FE/t, 16384 SU</td></tr>
 *   <tr><td>Industrial</td><td>x1.50</td><td>x1.20</td><td>0.0275</td><td>540 FE/t, 19661 SU</td></tr>
 * </table>
 *
 * a player with rotation to spare and no room builds Industrial; a player whose windmills are
 * already groaning builds Open Frame and gets half again as much energy for the same stress. the
 * middle one's what you build early, and what every figure gets quoted against.
 *
 * multipliers live here, base values live in config - same split as
 * {@link com.createstats.antenna.AntennaTier}: the enum is the identity and the relationship
 * between tiers, config is the absolute level. a pack author retuning the generator moves one
 * number and all three tiers stay in proportion, which is the part that was actually designed.
 */
public enum DynamoTier {

    /** Victorian open-frame dynamo. Least power, least stress, best value per SU. */
    OPEN_FRAME(0.75, 0.50),
    /** The baseline, equal to a Crafts and Additions alternator. */
    STANDARD(1.00, 1.00),
    /** Compact modern alternator. Most power, and more stress than power. */
    INDUSTRIAL(1.50, 1.20);

    /**
     * FE per RPM at {@link #STANDARD}, so 256 RPM gives exactly 360 FE/t.
     *
     * <p>Used only when the config has not loaded yet, which happens on the client before a world is
     * joined; the real value comes from {@code power.generator.fePerRpm}.</p>
     */
    public static final double BASE_FE_PER_RPM = 1.40625;

    /**
     * SU per RPM at {@link #STANDARD}, matching the Crafts and Additions alternator's 16384 SU at
     * 256 RPM.
     *
     * pinned to the same generator the output is pinned to, so "matches an alternator" is true on
     * both axes, not just the flattering one. was briefly 11.25, which kept the old 0.125 FE/SU
     * economy but made the dynamo 5.5x cheaper on stress than the block it's supposed to match -
     * too big an advantage to hand out quietly.
     *
     * so yeah, running these costs real stress on purpose. that's what makes Open Frame the obvious
     * first build once you're short on rotation: half the stress for three quarters the output.
     */
    public static final double BASE_STRESS_PER_RPM = 64.0;

    private final double powerScale;
    private final double stressScale;

    DynamoTier(double powerScale, double stressScale) {
        this.powerScale = powerScale;
        this.stressScale = stressScale;
    }

    /** FE generated per RPM of shaft speed. */
    public double fePerRpm() {
        return baseFePerRpm() * powerScale;
    }

    /** Stress units placed on the kinetic network per RPM. */
    public double stressPerRpm() {
        return baseStressPerRpm() * stressScale;
    }

    /** FE produced per unit of stress consumed, which is the number the three tiers really differ on. */
    public double fePerStress() {
        final double stress = stressPerRpm();
        return stress <= 0.0 ? 0.0 : fePerRpm() / stress;
    }

    private static double baseFePerRpm() {
        try {
            return com.createstats.ModConfiguration.COMMON.dynamoFePerRpm.get();
        } catch (RuntimeException ignored) {
            return BASE_FE_PER_RPM;
        }
    }

    private static double baseStressPerRpm() {
        try {
            return com.createstats.ModConfiguration.COMMON.dynamoStressImpact.get();
        } catch (RuntimeException ignored) {
            return BASE_STRESS_PER_RPM;
        }
    }
}
