package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// never an empty column
@OnlyIn(Dist.CLIENT)
public final class SecurityDock {
    private SecurityDock() {
    }

    public static void circuit(PanelSurface s, SecurityContext ctx, Rect r) {
        final SecuritySyncPacket view = ctx.view();
        float y = r.y;
        SecurityTheme.head(s, r.x, y, "CIRCUIT");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final int doors = count(ctx, SecurityDevice.DeviceClass.DOOR);
        final int sensors = count(ctx, SecurityDevice.DeviceClass.SENSOR);
        int linked = 0;
        for (SecuritySyncPacket.Row row : ctx.devices()) {
            if (row.manual()) {
                linked++;
            }
        }

        final Rect card = new Rect(r.x, y, r.w, 76.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CANVAS, SecurityTheme.LINE);
        float cy = card.y + Tokens.S2;

        s.circle(card.x + Tokens.S2 + 3.0f, cy + 4.0f, 3.0f,
                view.awake() ? SecurityTheme.STEEL : SecurityTheme.OFF);
        s.textV("Home Security Panel", card.x + Tokens.S4 + 4.0f, cy + 4.0f, SecurityTheme.TEXT,
                SecurityTheme.MICRO);
        s.textRightV("1 FE/t", card.right() - Tokens.S2, cy + 4.0f, SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
        cy += 14.0f;

        line(s, card, cy, doors + (doors == 1 ? " door" : " doors"),
                doors == 0 ? SecurityTheme.TEXT_FAINT : SecurityTheme.TEXT_DIM);
        cy += 12.0f;
        line(s, card, cy, sensors + (sensors == 1 ? " sensor" : " sensors"),
                sensors == 0 ? SecurityTheme.TEXT_FAINT : SecurityTheme.TEXT_DIM);
        cy += 12.0f;
        line(s, card, cy, linked + " linked by hand",
                linked == 0 ? SecurityTheme.TEXT_FAINT : SecurityTheme.TEXT_DIM);
        cy += 14.0f;

        s.hairlineH(card.x + Tokens.S2, cy, card.w - Tokens.S4, SecurityTheme.LINE);
        y = card.bottom() + Tokens.S3;

        for (String s2 : Text.wrap(s,
                "The scan stops at the Wire Terminal Junction, so one panel is one circuit is one "
                        + "house. End a wing's wiring at a junction box to give it its own panel.",
                r.w, SecurityTheme.MICRO)) {
            s.text(s2, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        y += Tokens.S3;

        SecurityTheme.head(s, r.x, y, "UNDER A DISTRIBUTION UNIT");
        y += Text.lineHeight(SecurityTheme.MICRO) + 3.0f;
        for (String s2 : Text.wrap(s,
                "Every block here is a row on a Power Distribution Unit. This panel and the Fire "
                        + "and Motion Sensors are Critical, so a short branch sheds the kitchen "
                        + "before it stops watching the house. Tamper and Water sit one rung down.",
                r.w, SecurityTheme.MICRO)) {
            s.text(s2, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        y += Tokens.S3;

        SecurityTheme.head(s, r.x, y, "FUTURE CLASSES LAND HERE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 3.0f;
        for (String s2 : Text.wrap(s,
                "The tabs are built from the registered device classes, not written out. A new one "
                        + "brings its own list and inspector without touching this screen.",
                r.w, SecurityTheme.MICRO)) {
            s.text(s2, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
    }

    private static void line(PanelSurface s, Rect card, float y, String label, int ink) {
        s.hairlineV(card.x + Tokens.S2 + 3.0f, y - 2.0f, 12.0f, SecurityTheme.LINE);
        s.textV(label, card.x + Tokens.S4 + 4.0f, y + 4.0f, ink, SecurityTheme.MICRO);
    }

    private static int count(SecurityContext ctx, SecurityDevice.DeviceClass cls) {
        return ctx.devicesOf(cls).size();
    }
}
