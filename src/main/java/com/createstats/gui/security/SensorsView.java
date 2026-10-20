package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.SensorKind;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// show the armed toggle in every mode, hiding it in AWAY would lie
@OnlyIn(Dist.CLIENT)
public final class SensorsView extends DevicesView {
    private static final int[] RANGES = {2, 4, 6, 10, 16};

    public SensorsView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.SENSOR);
    }

    @Override
    protected void rowIcon(PanelSurface s, float cx, float cy, int color,
                           SecuritySyncPacket.Row row) {
        SecurityIcons.sensor(s, cx, cy, color, row.sensorKindOf());
    }

    @Override
    protected Rect stateChip(PanelSurface s, float rightX, float centerY,
                             SecuritySyncPacket.Row row) {
        final SecurityDevice.DeviceState state = row.stateOf();
        final String word = this.deviceClass.stateLabel(state).getString()
                .toUpperCase(java.util.Locale.ROOT);
        return switch (state) {
            case UNPOWERED, BREACHED, OFFLINE -> SecurityTheme.chipRight(s, rightX, centerY, word,
                    SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);

            case ACTIVE -> SecurityTheme.chipRight(s, rightX, centerY, word,
                    SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);
            case DAMAGED -> SecurityTheme.chipRight(s, rightX, centerY, word,
                    SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_INK, SecurityTheme.AMBER_EDGE);

            default -> row.isHazard()
                    ? SecurityTheme.chipRight(s, rightX, centerY, "ALWAYS ON",
                            SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_INK,
                            SecurityTheme.AMBER_EDGE)
                    : row.armed()
                            ? SecurityTheme.chipRight(s, rightX, centerY, "ARMED",
                                    SecurityTheme.GREEN_WASH, SecurityTheme.GREEN_DEEP,
                                    SecurityTheme.GREEN_EDGE)
                            : SecurityTheme.chipRight(s, rightX, centerY, "LOG ONLY",
                                    SecurityTheme.CANVAS, SecurityTheme.TEXT_DIM,
                                    SecurityTheme.LINE);
        };
    }

    @Override
    protected boolean usesClearance(SecuritySyncPacket.Row row) {
        return !row.isHazard();
    }

    @Override
    protected String passLabel() {
        return " pass unseen";
    }

    @Override
    protected String noClearanceLine(SecuritySyncPacket.Row row) {
        return "Fire and water do not carry a keycard. A hazard sensor reports what it sees "
                + "whoever is standing next to it, so there is no clearance that walks past one.";
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        y = drawWatchesFor(s, r, row, y);
        y = drawArming(s, r, row, y, mx, my);
        return drawRange(s, r, row, y, mx, my);
    }

    private float drawWatchesFor(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y) {
        SecurityTheme.head(s, r.x, y, "WATCHES FOR");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final SensorKind kind = row.sensorKindOf();
        final String what = kind == null ? "Unknown" : switch (kind) {
            case MOTION -> "Players without clearance, and hostile mobs. Animals, villagers and "
                    + "your own golems are ignored.";
            case TAMPER -> "Blocks broken or placed by anyone without clearance, and any explosion "
                    + "nobody cleared set off.";
            case FIRE -> "Fire, lava, a lit campfire, or any creature that is burning.";
            case WATER -> "Water and flowing fluid, at or below its own height.";
        };
        return note(s, r, y, what) + Tokens.S3;
    }

    private float drawArming(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                             float mx, float my) {
        SecurityTheme.head(s, r.x, y, "ARMING");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        if (row.isHazard()) {
            final Rect box = new Rect(r.x, y, r.w, SecurityTheme.OPT_H);
            s.card(box, Tokens.R_CHIP, SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_EDGE);
            s.textV("Always watching", box.x + Tokens.S2, box.centerY(), SecurityTheme.AMBER_INK,
                    SecurityTheme.MICRO);
            s.textRightV("HAZARD", box.right() - Tokens.S2, box.centerY(), SecurityTheme.AMBER_INK,
                    SecurityTheme.MICRO);
            y += SecurityTheme.OPT_H + 4.0f;
            y = note(s, r, y, "A hazard sensor ignores the house mode. There is no setting that "
                    + "turns it off.");
            return y + Tokens.S3;
        }

        final boolean custom = this.ctx.mode().usesPerSensorFlag();
        final boolean may = custom && this.ctx.mayEditDevices();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                row.armed() ? "Armed" : "Logging only",
                row.armed() ? "TRIPS THE ALARM" : "LOG ONLY",
                row.armed(), may, mx, my,
                () -> this.ctx.setSensorArmed(row.pos(), !row.armed()));
        y += SecurityTheme.OPT_H + 4.0f;

        y = note(s, r, y, switch (this.ctx.mode()) {
            case HOME -> "HOME is forcing this to log only. Switch to CUSTOM to arm it on its own.";
            case AWAY -> "AWAY is forcing this armed. Switch to CUSTOM to arm sensors one by one.";
            case CUSTOM -> "CUSTOM reads this switch. An armed sensor seals the house when it trips.";

            case AWAY_PLUS -> "AWAY+ is forcing this armed, and the house is already sealed. "
                    + "Stand the mode down on the Overview to change it, which takes the password.";
        });
        return y + Tokens.S3;
    }

    private float drawRange(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                            float mx, float my) {
        SecurityTheme.head(s, r.x, y, "RANGE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final SensorKind kind = row.sensorKindOf();
        final boolean may = this.ctx.mayEditDevices();
        final float gap = 2.0f;
        final float w = (r.w - gap * (RANGES.length - 1)) / RANGES.length;
        for (int i = 0; i < RANGES.length; i++) {
            final int blocks = RANGES[i];
            final Rect box = new Rect(r.x + i * (w + gap), y, w, SecurityTheme.OPT_H);
            final boolean on = row.range() == blocks;
            final boolean warm = may && hot(box, mx, my);
            s.card(box, Tokens.R_CHIP,
                    on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
            s.textCenterV(String.valueOf(blocks), box.centerX(), box.centerY(),
                    on ? SecurityTheme.STEEL_DEEP : SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            if (may) {
                hit(box, () -> this.ctx.setSensorRange(row.pos(), blocks));
            }
        }
        y += SecurityTheme.OPT_H + 4.0f;

        return note(s, r, y, kind == SensorKind.WATER
                ? "Blocks, measured downward and outward. Water falls, so this only watches at or "
                        + "below its own height."
                : "Blocks, in every direction.");
    }

    private float note(PanelSurface s, Rect r, float y, String text) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
