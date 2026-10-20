package com.createstats.gui.security;

import com.createstats.blockentity.MobRepellerBE;
import com.createstats.client.tuner.TunerSurveys;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.repeller.RepellerDraw;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.tuner.TunerStation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class RepellersView extends DevicesView {
    private static final float STATION_H = 14.0f;

    private static final int MAX_ID = 64;

    public RepellersView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.REPELLER);
    }

    @Override
    protected String passLabel() {
        return " can tune";
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        final MobRepellerBE be = repeller(row);
        if (be == null) {
            SecurityTheme.head(s, r.x, y, "TRANSMITTER");
            y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
            return note(s, r, y, "Too far from you to read its dial. Walk within sight of it and its "
                    + "controls appear here; the panel still switches it with the Overview's buttons.",
                    SecurityTheme.TEXT_FAINT) + Tokens.S3;
        }
        final boolean may = this.ctx.mayEditDevices();

        SecurityTheme.head(s, r.x, y, "TRANSMITTER");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        y = status(s, r, be, y);
        final boolean on = !be.panelOff();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                on ? "Transmitting when it can" : "Switched off from this panel",
                on ? "ON" : "OFF", on, may, mx, my,
                () -> this.ctx.setRepellerOn(row.pos(), !on));
        y += SecurityTheme.OPT_H + Tokens.S3;

        y = drawStations(s, r, row, be, y, mx, my, may);

        y = drawRange(s, r, row, be, y, mx, my, may);

        SecurityTheme.head(s, r.x, y, "AT THE BLOCK");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean locked = be.locked();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                locked ? "Locked: tuned from this panel only" : "Tuner and wrench need clearance",
                locked ? "LOCKED" : "OPEN", locked, may, mx, my,
                () -> this.ctx.setRepellerLocked(row.pos(), !locked));
        y += SecurityTheme.OPT_H + 2.0f;
        final boolean hears = be.hearsRedstone();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                hears ? "A redstone signal switches it off" : "Redstone is ignored",
                hears ? "OBEYS" : "IGNORES", !hears, may, mx, my,
                () -> this.ctx.setRepellerRedstone(row.pos(), !hears));
        y += SecurityTheme.OPT_H + 4.0f;
        y = note(s, r, y, locked
                ? "Nobody can retune it with the ring or a wrench, the owner included. Every attempt "
                        + "is written to the log."
                : "Anybody below the clearance above is refused at the block, and the log says who "
                        + "tried.", SecurityTheme.TEXT_FAINT);
        y = note(s, r, y + 2.0f, "A lever is a change made at the block too. A Time Switch needs it "
                + "to obey; ignoring redstone and locking it make this panel its only switch. Taking "
                + "it down is logged, and in AWAY a stranger doing it seals the house.",
                SecurityTheme.TEXT_FAINT);
        return y + Tokens.S3;
    }

    private float status(PanelSurface s, Rect r, MobRepellerBE be, float y) {
        final String mob = mobName(be.station());
        final String covering = String.format(java.util.Locale.ROOT, "%.0f", be.effectiveRadius());
        final String line;
        final int ink;
        switch (be.state()) {
            case RUNNING -> {
                line = "Repelling " + mob + " across " + covering + " blocks";
                ink = SecurityTheme.GREEN_DEEP;
            }
            case SHORT -> {
                line = "Short of power: covering " + covering + " of " + be.radius() + " blocks";
                ink = SecurityTheme.AMBER_INK;
            }
            case OFF -> {
                line = "No power: " + mob + " can spawn";
                ink = SecurityTheme.RED;
            }
            case SHED -> {
                line = "Shed at a power board: " + mob + " can spawn";
                ink = SecurityTheme.RED;
            }
            case DISABLED -> {
                line = "Held off by a redstone signal";
                ink = SecurityTheme.TEXT_DIM;
            }
            case PANEL_OFF -> {
                line = "Off: switched off from this panel";
                ink = SecurityTheme.TEXT_DIM;
            }
            default -> {
                line = "Not tuned, so it draws nothing";
                ink = SecurityTheme.TEXT_DIM;
            }
        }
        y = note(s, r, y, line, ink);
        final String numbers = be.measuredDrawFePerTick() + " FE/t  .  "
                + be.repelledLastMinute() + " spawns repelled in the last minute";
        s.text(Text.truncate(s, numbers, r.w, SecurityTheme.MICRO), r.x, y, SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
        return y + Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
    }

    private float drawStations(PanelSurface s, Rect r, SecuritySyncPacket.Row row, MobRepellerBE be,
                               float y, float mx, float my, boolean may) {
        SecurityTheme.head(s, r.x, y, "STATION");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final List<TunerStation> survey = TunerSurveys.of(row.pos());
        if (TunerSurveys.due(row.pos())) {
            this.ctx.surveyRepeller(row.pos());
        }
        final String tuned = be.station();
        final int radius = be.radius();

        y = stationRow(s, r, y, "Off", "draws nothing", tuned.isEmpty(), may, mx, my,
                () -> this.ctx.tuneRepeller(row.pos(), "", radius));
        if (survey == null) {
            return note(s, r, y + 2.0f, "Listening for what can spawn round it...",
                    SecurityTheme.TEXT_FAINT) + Tokens.S3;
        }
        final List<TunerStation> sorted = new ArrayList<>(survey);
        sorted.sort(Comparator.comparingInt(TunerStation::dial));
        boolean anyReachable = false;
        for (TunerStation st : sorted) {
            final boolean reach = st.minRange() > 0 && st.minRange() <= radius;
            anyReachable |= reach;
            final String foot = st.minRange() <= 0 ? st.subLabel() + "  .  not seen here"
                    : reach ? st.subLabel() : "needs " + st.minRange();
            final int wanted = Math.max(radius, st.minRange());
            final boolean sendable = st.id().length() <= MAX_ID;
            y = stationRow(s, r, y, Component.translatable(st.labelKey()).getString(), foot,
                    st.id().equals(tuned), may && sendable, mx, my,
                    () -> this.ctx.tuneRepeller(row.pos(), st.id(), wanted));
        }
        if (!anyReachable) {
            y = note(s, r, y + 2.0f, "Nothing spawns within " + RepellerDraw.MAX_RADIUS
                    + " blocks of it, so there is nothing yet to repel.", SecurityTheme.TEXT_FAINT);
        }
        return y + Tokens.S3;
    }

    private float stationRow(PanelSurface s, Rect r, float y, String label, String foot, boolean on,
                             boolean may, float mx, float my, Runnable action) {
        final Rect box = new Rect(r.x, y, r.w, STATION_H);
        final boolean warm = may && !on && hot(box, mx, my);
        s.card(box, Tokens.R_CHIP,
                on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
        final int ink = on ? SecurityTheme.STEEL_DEEP : may ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF;
        final float footW = s.textWidth(foot, SecurityTheme.MICRO);
        s.textV(Text.truncate(s, label, box.w - footW - Tokens.S2 * 3, SecurityTheme.MICRO),
                box.x + Tokens.S2, box.centerY(), ink, SecurityTheme.MICRO);
        s.textRightV(foot, box.right() - Tokens.S2, box.centerY(),
                on ? ink : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        if (may && !on) {
            hit(box, action);
        }
        return y + STATION_H + 2.0f;
    }

    private float drawRange(PanelSurface s, Rect r, SecuritySyncPacket.Row row, MobRepellerBE be,
                            float y, float mx, float my, boolean may) {
        SecurityTheme.head(s, r.x, y, "RANGE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final int radius = be.radius();
        final String station = be.station();
        final int down = MobRepellerBE.RANGE.step(radius, -1);
        final int up = MobRepellerBE.RANGE.step(radius, 1);
        final float bw = SecurityTheme.BTN_H + Tokens.S2;
        final Rect minus = new Rect(r.x, y, bw, SecurityTheme.BTN_H);
        final Rect plus = new Rect(r.right() - bw, y, bw, SecurityTheme.BTN_H);
        final boolean canDown = may && down != radius;
        final boolean canUp = may && up != radius;
        SecurityTheme.button(s, minus, "-", false, canDown && hot(minus, mx, my), canDown);
        SecurityTheme.button(s, plus, "+", false, canUp && hot(plus, mx, my), canUp);
        if (canDown) {
            hit(minus, () -> this.ctx.tuneRepeller(row.pos(), station, down));
        }
        if (canUp) {
            hit(plus, () -> this.ctx.tuneRepeller(row.pos(), station, up));
        }

        final int cost = station.isEmpty() ? 0 : RepellerDraw.drawFor(radius);
        final String shape = be.column() ? "Column " : "Sphere ";
        s.textCenterV(shape + radius + " blocks  .  " + cost + " FE/t", r.centerX(), minus.centerY(),
                SecurityTheme.TEXT, SecurityTheme.MICRO);
        y += SecurityTheme.BTN_H + 4.0f;
        final TunerStation picked = TunerSurveys.find(row.pos(), station);
        final Component cord = be.tunerNote(picked, radius);
        if (cord != null) {
            y = note(s, r, y, cord.getString(), SecurityTheme.AMBER_INK);
        }
        return y + Tokens.S3;
    }

    @Nullable
    private static MobRepellerBE repeller(SecuritySyncPacket.Row row) {
        final net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(row.pos()) instanceof MobRepellerBE be ? be : null;
    }

    private String mobName(String id) {
        if (id.isEmpty()) {
            return "nothing";
        }
        final net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key != null) {
            final var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(key);
            if (type.isPresent()) {
                return type.get().getDescription().getString();
            }
        }
        return id;
    }

    private float note(PanelSurface s, Rect r, float y, String text, int ink) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, ink, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
