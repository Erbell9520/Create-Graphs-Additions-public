package com.createstats.statsmk3.page;

import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AeroArt {
    private AeroArt() {
    }

    static int countAtLeast(List<AerialAlerts.Alert> alerts, AerialAlerts.Level level) {
        int n = 0;
        for (final AerialAlerts.Alert a : alerts) {
            if (a.level().compareTo(level) >= 0) {
                n++;
            }
        }
        return n;
    }

    // veil, don't clear. the last known state is exactly what you need
    public static void staleVeil(PanelSurface s, Rect r, AerialFrame f) {
        Shapes.rect(s, r, Tokens.alpha(Tokens.CANVAS, 0.45f));
        s.advanceLayer();
        final String text = AeroTabs.first(s, r.w - 24.0f,
                String.format(Locale.ROOT, "NO FEED FOR %s: the last report, held", AerialAlerts.duration(f.age())),
                String.format(Locale.ROOT, "NO FEED FOR %s", AerialAlerts.duration(f.age())), "NO FEED");
        if (text.isEmpty()) {
            return;
        }
        final float w = s.textWidth(text, Text.META) + 20.0f;
        final Rect pill = new Rect(r.centerX() - w * 0.5f, r.centerY() - 9.0f, w, 18.0f);
        Shapes.roundedRect(s, pill, 9.0f, Tokens.CRITICAL);
        s.textCenterV(text, pill.centerX(), pill.centerY(), Tokens.CANVAS, Text.META);
    }

    public static void empty(PanelSurface s, Rect r, boolean linked) {
        final String head = linked ? "Waiting for the Aerial's first report" : "No VCC Mk IV Aerial linked";
        final String[] lines = linked
                ? new String[]{"The link is there; the ship has not answered yet.", "A panel without power does not ask."}
                : new String[]{"Point a Display Link from the Aerial at this panel,", "and pick the VCC Mk IV Aerial source."};
        float w = s.textWidth(head, Text.SUB);
        for (final String l : lines) {
            w = Math.max(w, s.textWidth(l, Text.META));
        }
        w = Math.min(r.w - 8.0f, w + 32.0f);
        final Rect c = new Rect(r.centerX() - w * 0.5f, r.centerY() - 38.0f, w, 76.0f);
        Shapes.card(s, c, 8.0f, Tokens.RAISED, Tokens.LINE);
        final float hs = s.textWidth(head, Text.SUB) <= c.w - 16.0f ? Text.SUB : Text.META;
        s.textCenter(AeroTabs.fit(s, head, c.w - 16.0f), c.centerX(), c.y + 14.0f, Tokens.INK, hs);
        float y = c.y + 38.0f;
        for (final String l : lines) {
            s.textCenter(AeroTabs.fit(s, l, c.w - 16.0f), c.centerX(), y, Tokens.INK_3, Text.META);
            y += 13.0f;
        }
    }
}
