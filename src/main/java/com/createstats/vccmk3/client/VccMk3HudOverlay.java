package com.createstats.vccmk3.client;

import com.createstats.vccmk3.alert.AlertLevel;
import com.createstats.vccmk3.avionics.AvionicsChannel;
import com.createstats.vccmk3.network.VccMk3HudPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

// six numbers, an attitude, the caution line. not a second console
public final class VccMk3HudOverlay {
    private static final int PANEL = 0xE60E131A;
    private static final int PANEL_HEAD = 0xF01D2530;
    private static final int EDGE = 0xFF3D4C5C;
    private static final int TEXT = 0xFFE4EEF8;
    private static final int TEXT_DIM = 0xFF8CA1B4;
    private static final int TEXT_FAINT = 0xFF53677A;
    private static final int BRASS = 0xFFD4AF37;
    private static final int CYAN = 0xFF62D5FF;
    private static final int GOOD = 0xFF6EE7A8;
    private static final int BAD = 0xFFFF6B6B;
    private static final int HORIZON_SKY = 0xFF122E3A;
    private static final int HORIZON_GROUND = 0xFF3A281A;

    private static final int W = 176;
    private static final int H = 108;
    private static final int MARGIN = 6;

    private static int frame;

    private VccMk3HudOverlay() {}

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        if (minecraft.options.hideGui || minecraft.screen != null) return;

        VccMk3HudPacket packet = VccMk3HudState.packet();
        if (packet == null) return;
        frame++;

        graphics.drawManaged(() -> draw(graphics, minecraft, packet));
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, VccMk3HudPacket packet) {
        int x = MARGIN;
        int y = graphics.guiHeight() - H - MARGIN;

        graphics.fill(x, y, x + W, y + H, PANEL);
        outline(graphics, x, y, W, H, EDGE);
        graphics.fill(x + 1, y + 1, x + W - 1, y + 13, PANEL_HEAD);

        String name = packet.craftName().isEmpty() ? "VCC MK III" : packet.craftName();
        graphics.drawString(minecraft.font, trim(minecraft, name, W - 60), x + 5, y + 3, BRASS, false);

        String mode = packet.autoEngaged() ? "AUTO" : (packet.armed() ? "ARMED" : "SAFE");
        int modeColour = packet.autoEngaged() ? CYAN : (packet.armed() ? BAD : GOOD);
        graphics.drawString(minecraft.font, mode,
                x + W - 5 - minecraft.font.width(mode), y + 3, modeColour, false);

        if (VccMk3HudState.isStale()) {
            String warning = "NO SIGNAL";
            graphics.fill(x + 1, y + 13, x + W - 1, y + 24, 0x80401A1C);
            graphics.drawString(minecraft.font, warning,
                    x + (W - minecraft.font.width(warning)) / 2, y + 15, BAD, false);
        }

        int row = y + 26;
        row = reading(graphics, minecraft, x + 5, row, 96, "ALT",
                VccMk3HudPacket.channel(packet.altitude(), packet.altitudeSource()), 0, "m");
        row = reading(graphics, minecraft, x + 5, row, 96, "IAS",
                VccMk3HudPacket.channel(packet.airspeed(), packet.airspeedSource()), 1, "");
        row = reading(graphics, minecraft, x + 5, row, 96, "GS",
                VccMk3HudPacket.channel(packet.groundSpeed(), packet.groundSpeedSource()), 1, "");
        row = reading(graphics, minecraft, x + 5, row, 96, "V/S",
                VccMk3HudPacket.channel(packet.climbRate(), packet.climbSource()), 1, "");
        row = reading(graphics, minecraft, x + 5, row, 96, "HDG",
                VccMk3HudPacket.channel(packet.headingDeg(), packet.headingSource()), 0, "");
        reading(graphics, minecraft, x + 5, row, 96, "RA",
                VccMk3HudPacket.channel(packet.terrainClearance(), packet.terrainSource()), 0, "m");

        AvionicsChannel pitch = VccMk3HudPacket.channel(packet.pitchDeg(), packet.attitudeSource());
        AvionicsChannel roll = VccMk3HudPacket.channel(packet.rollDeg(), packet.attitudeSource());
        drawAttitude(graphics, x + W - 36, y + 52, 24, pitch, roll);

        AlertLevel level = AlertLevel.byOrdinal(packet.alertLevel());
        if (level != AlertLevel.OK) {
            int colour = level.color();
            if (level.shouldPulse()) {
                float phase = (float) ((Math.sin(frame * 0.2) + 1.0) * 0.5);
                colour = blend(level.color(), PANEL, 0.5f * (1f - phase));
            }
            graphics.fill(x + 1, y + H - 13, x + W - 1, y + H - 1, 0x40000000);
            graphics.fill(x + 1, y + H - 13, x + 4, y + H - 1, colour);
            graphics.drawString(minecraft.font, packet.alertCode(), x + 7, y + H - 11, colour, false);
            graphics.drawString(minecraft.font,
                    trim(minecraft, packet.alertMessage(), W - 16 - minecraft.font.width(packet.alertCode())),
                    x + 9 + minecraft.font.width(packet.alertCode()) + 4, y + H - 11, TEXT_DIM, false);
        }
    }

    private static int reading(GuiGraphics graphics, Minecraft minecraft, int x, int y, int w,
                               String label, AvionicsChannel channel, int decimals, String unit) {
        graphics.drawString(minecraft.font, label, x, y, TEXT_DIM, false);
        String text = channel.present() ? channel.format(decimals) + unit : "---";
        graphics.drawString(minecraft.font, text, x + 30, y, channel.present() ? TEXT : TEXT_FAINT, false);

        graphics.fill(x + w - 4, y + 1, x + w - 1, y + 7, channel.source().color());
        return y + 11;
    }

    private static void drawAttitude(GuiGraphics graphics, int cx, int cy, int r,
                                     AvionicsChannel pitch, AvionicsChannel roll) {
        if (pitch.present() || roll.present()) {
            double slope = Math.tan(Math.toRadians(roll.orElse(0.0)));
            double offset = pitch.orElse(0.0) * (r / 45.0);
            ConsoleDraw.horizonDisc(graphics, cx, cy, r, offset, slope, HORIZON_SKY, HORIZON_GROUND, CYAN);
        } else {
            ConsoleDraw.disc(graphics, cx, cy, r, HORIZON_SKY);
        }
        ConsoleDraw.ring(graphics, cx, cy, r, 1, EDGE);
        graphics.fill(cx - 14, cy, cx - 5, cy + 1, BRASS);
        graphics.fill(cx + 6, cy, cx + 15, cy + 1, BRASS);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, BRASS);
    }

    private static String trim(Minecraft minecraft, String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String cut = text;
        while (minecraft.font.width(cut + "..") > maxWidth && cut.length() > 2) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y, x + 1, y + h, argb);
        graphics.fill(x + w - 1, y, x + w, y + h, argb);
    }

    private static int blend(int a, int b, float t) {
        return ConsoleDraw.blend(a, b, t);
    }

    public static String formatHeading(double degrees) {
        return String.format(Locale.ROOT, "%03d", Math.floorMod((int) Math.round(degrees), 360));
    }
}
