package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// no clearance for shutters, nobody walks up to one
@OnlyIn(Dist.CLIENT)
public final class ShuttersView extends DevicesView {
    public ShuttersView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.SHUTTER);
    }

    @Override
    protected boolean usesClearance(SecuritySyncPacket.Row row) {
        return false;
    }

    @Override
    protected String noClearanceLine(SecuritySyncPacket.Row row) {
        return "A shutter never asks who you are. It is sealed by the house or it is not, and there "
                + "is no clearance that opens one, including the owner's.";
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        final boolean sealed = row.stateOf() == SecurityDevice.DeviceState.ACTIVE;
        final boolean may = this.ctx.mayEditDevices();

        SecurityTheme.head(s, r.x, y, "THIS BANK");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                sealed ? "Sealed" : "Open",
                sealed ? "SEAL BY CHOICE" : "OPEN",
                sealed, may, mx, my,
                () -> this.ctx.setShutterSealed(row.pos(), !sealed));
        y += SecurityTheme.OPT_H + 4.0f;

        if (this.ctx.released()) {
            y = note(s, r, y, "A fire release is holding every shutter open. That beats a lockdown "
                    + "and it beats this switch: a window is a smoke vent and an escape route.");
        } else if (this.ctx.houseSealed()) {
            y = note(s, r, y, "AWAY+ is holding every bank sealed. This switch still decides what "
                    + "this bank goes back to when the mode stands down, so set it now for the "
                    + "house you want to come back to.");
        } else if (this.ctx.lockedDown()) {
            y = note(s, r, y, "A lockdown is holding every shutter sealed. Clear it on the Overview "
                    + "and this switch goes back to deciding.");
        }
        y += Tokens.S2;

        SecurityTheme.head(s, r.x, y, "AT DUSK");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean dusk = duskFlag(row);
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                dusk ? "Closes at dusk" : "Stays as set",
                dusk ? "NIGHTLY" : "OFF",
                dusk, may, mx, my,
                () -> this.ctx.setShutterDusk(row.pos(), !dusk));
        y += SecurityTheme.OPT_H + 4.0f;
        y = note(s, r, y, "Per bank rather than house wide, so a bedroom can shutter itself at night "
                + "while the workshop stays open.");
        y += Tokens.S2;

        y = note(s, r, y, "Four things move a shutter: a fire release opens it, AWAY+ and a "
                + "lockdown seal it, and below those sit the switch above and dusk. There is no "
                + "hand release and no redstone, in or out.");
        return y + Tokens.S3;
    }

    private boolean duskFlag(SecuritySyncPacket.Row row) {
        final net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        return level != null
                && level.getBlockEntity(row.pos())
                        instanceof com.createstats.blockentity.BlastShutterBE shutter
                && shutter.duskFlag();
    }

    private float note(PanelSurface s, Rect r, float y, String text) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
