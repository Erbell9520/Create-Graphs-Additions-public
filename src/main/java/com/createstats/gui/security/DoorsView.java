package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// entrance is a policy, not a tenth door block
@OnlyIn(Dist.CLIENT)
public final class DoorsView extends DevicesView {
    public DoorsView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.DOOR);
    }

    private static final int[] REACHES = {-1, 0, 2, 3, 5, 8};

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        y = drawReach(s, r, row, y, mx, my);
        SecurityTheme.head(s, r.x, y, "LOCKDOWN ENTRANCE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final boolean on = row.entrance();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                on ? "This is the entrance" : "Make this the entrance",
                on ? "OWNER ONLY" : "OFF",
                on, this.ctx.mayEditPanel(), mx, my,
                () -> this.ctx.setEntrance(row.pos()));
        y += SecurityTheme.OPT_H + 4.0f;

        for (String line : Text.wrap(s, this.ctx.houseSealed()
                        ? "During a lockdown this is the only door that opens, and only for the "
                                + "owner. In AWAY+ it is also the only one that opens on approach, "
                                + "again only for the owner, which is how they reach this panel to "
                                + "stand the mode down."
                        : "During a lockdown this is the only door that opens, and only for the "
                                + "owner. Every other door refuses everyone until the lockdown is "
                                + "cleared.",
                r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y + Tokens.S3;
    }

    private float drawReach(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                            float mx, float my) {
        SecurityTheme.head(s, r.x, y, "OPENS BY ITSELF");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final int house = this.ctx.view().proximity();
        final int set = row.range();

        final boolean may = this.ctx.mayEditDevices() && !this.ctx.houseSealed();
        final float gap = 2.0f;
        final float w = (r.w - gap * (REACHES.length - 1)) / REACHES.length;
        for (int i = 0; i < REACHES.length; i++) {
            final int blocks = REACHES[i];
            final Rect box = new Rect(r.x + i * (w + gap), y, w, SecurityTheme.OPT_H);
            final boolean on = blocks < 0 ? set < 0 : set == blocks;
            final boolean warm = may && hot(box, mx, my);
            s.card(box, Tokens.R_CHIP,
                    on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
            s.textCenterV(blocks < 0 ? "HOUSE" : blocks == 0 ? "OFF" : String.valueOf(blocks),
                    box.centerX(), box.centerY(),
                    on ? SecurityTheme.STEEL_DEEP : SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            if (may) {
                hit(box, () -> this.ctx.setDoorRange(row.pos(), blocks));
            }
        }
        y += SecurityTheme.OPT_H + 4.0f;

        if (this.ctx.houseSealed()) {
            y = wrap(s, r, y, "AWAY+ is on, so this has no effect. Interior doors do not scan at "
                    + "all while the house is sealed, and the entrance opens on approach for the "
                    + "owner alone. What is set here comes back when the mode stands down.");
            return y + Tokens.S3;
        }

        final int effective = set < 0 ? house : set;
        final String line = effective <= 0
                ? (set < 0
                        ? "Following the house, which is set to off. This door only opens when it is "
                                + "clicked or given a redstone signal."
                        : "Off. This door only opens when it is clicked or given a redstone signal.")
                : (set < 0
                        ? "Following the house: " + house + " blocks. Change it on the Setup tab and "
                                + "every door still on HOUSE moves with it."
                        : "Pinned to " + set + " blocks. The house is set to " + house
                                + " and this door no longer follows it.");
        for (String wrapped : Text.wrap(s, line, r.w, SecurityTheme.MICRO)) {
            s.text(wrapped, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }

        y = wrap(s, r, y, "Measured from the doorway itself, so a wide bay opens for somebody "
                + "standing at either end of it and not only in the middle.");
        return y + Tokens.S3;
    }

    private float wrap(PanelSurface s, Rect r, float y, String text) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
