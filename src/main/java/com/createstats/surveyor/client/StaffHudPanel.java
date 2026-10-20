package com.createstats.surveyor.client;

import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.RouteWords;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.SegmentCheck;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllKeys;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// vanilla grey panel, below chat so F1 hides it
public final class StaffHudPanel {
    static final int OUTLINE = 0xFF000000;
    static final int HIGHLIGHT = 0xFFFFFFFF;
    static final int FACE = 0xFFC6C6C6;
    static final int RECESSED = 0xFF8B8B8B;
    static final int SHADOW = 0xFF555555;
    static final int LABEL = 0xFF404040;
    static final int WARN = 0xFFAA0000;
    static final int PAD = 5;

    static final int LIFT = 62;

    private StaffHudPanel() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null || !StaffInput.holding(player)) return;

        RenderSystem.depthMask(false);
        try {
            draw(g, mc.font, g.guiWidth(), g.guiHeight(), Rows.from(mc));
            if (StaffInput.mode() == StaffMode.WATCH) edgeArrows(g, mc, delta.getGameTimeDeltaPartialTick(false));
        } finally {
            g.flush();
            RenderSystem.depthMask(true);
        }
    }

    @EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layer {
        private Layer() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath("create_stats", "surveyor_staff"),
                    StaffHudPanel::render);
        }
    }

    record Rows(StaffMode mode, boolean menu, String pathway, int swatch, String facts, String here, int hereColour,
                String hint, String note) {
        static Rows from(Minecraft mc) {
            StaffMode mode = StaffInput.mode();
            boolean menu = AllKeys.TOOL_MENU.isPressed();
            PathwayClientCache.Entry e = PathwayClientCache.get(StaffInput.selected());
            String name = e == null ? I18n.get("create_stats.surveyor.hud.no_pathway") : e.pathway.name();
            int swatch = e == null ? 0 : PathwayRenderer.colour(e.pathway.colour());
            String facts = "";
            if (e != null) {
                Pathway p = e.pathway;
                int vehicles = PathwayClientCache.vehiclesOn(p.id()).size();
                facts = I18n.get("create_stats.surveyor.hud.facts", p.nodes().size(), Math.round(e.spline().length()), vehicles)
                        + (p.kind() == PathKind.LOOP ? "  " + I18n.get("create_stats.surveyor.kind.loop") : "");
            }
            String here = "";
            int hereColour = LABEL;
            StaffPicking.Pick h = StaffInput.hover();
            if (h.kind() == StaffPicking.Kind.NODE) {
                PathwayClientCache.Entry he = PathwayClientCache.get(h.pathway());
                PathNode n = he == null ? null : he.pathway.node(h.nodeId());
                if (n != null) {
                    String speed = n.speedLimit() > 0 ? PathwayRenderer.fmt(n.speedLimit()) + " b/s" : I18n.get("create_stats.surveyor.hud.cruise");
                    here = he.pathway.label(n.id()) + "  " + speed + "  " + I18n.get("create_stats.surveyor.corner." + n.corner().key)
                            + (n.isStop() ? "  " + RouteText.rules(n.stop()) : "");
                }
            } else if (h.kind() == StaffPicking.Kind.SEGMENT) {
                PathwayClientCache.Entry he = PathwayClientCache.get(h.pathway());
                if (he != null) {
                    here = he.pathway.name() + "  " + PathwayRenderer.fmt(he.plan().at(h.s())) + " b/s";
                    PathwayChecks.Result c = he.lastChecks();
                    if (c != null) {
                        for (SegmentCheck.Finding f : c.findings()) {
                            if (h.s() >= f.from() - 0.5 && h.s() <= f.to() + 0.5 && f.level() != SegmentCheck.Level.FINE) {
                                here += "  " + f.why();
                                if (f.level() == SegmentCheck.Level.RED) hereColour = WARN;
                                break;
                            }
                        }
                    }
                }
            } else if (h.kind() == StaffPicking.Kind.VEHICLE) {
                PathwayClientCache.Vehicle v = h.vehicle() == null ? null : PathwayClientCache.vehicle(h.vehicle());
                if (v != null && v.now != null) here = String.join("  ", RouteText.card(v.now).subList(0, Math.min(2, RouteText.card(v.now).size())));
            } else if (mode == StaffMode.LINK && StaffInput.linkVehicle() != null) {
                here = I18n.get("create_stats.surveyor.hud.link_ready", StaffInput.linkName());
            } else if (e == null && mode == StaffMode.SURVEY) {
                here = I18n.get("create_stats.surveyor.hud.start_hint");
            }
            String hint = I18n.get("create_stats.surveyor.hint." + mode.key);

            if (mode == StaffMode.EDIT && h.kind() == StaffPicking.Kind.NODE) hint = I18n.get("create_stats.surveyor.hint.edit_node");
            if (StaffInput.brush().size() > 0) hint = I18n.get("create_stats.surveyor.hint.brushing");
            String note = "";
            if (menu) note = I18n.get("create_stats.surveyor.hud.scroll_modes");
            else if (StaffInput.move() != null) note = I18n.get("create_stats.surveyor.hud.moving");
            else if (!StaffInput.undoNames().isEmpty() || !StaffInput.redoNames().isEmpty()) note = history();
            else if (StaffInput.undoOffered()) note = I18n.get("create_stats.surveyor.hud.undo");
            return new Rows(mode, menu, name, swatch, facts, here, hereColour, hint, note);
        }

        static String history() {
            StringBuilder b = new StringBuilder();
            if (!StaffInput.undoNames().isEmpty()) {
                b.append(I18n.get("create_stats.surveyor.hud.history", String.join(" / ", StaffInput.undoNames())));
            }
            if (!StaffInput.redoNames().isEmpty()) {
                if (b.length() > 0) b.append("   ");
                b.append(I18n.get("create_stats.surveyor.hud.history_redo", StaffInput.redoNames().get(0)));
            }
            return b.toString();
        }
    }

    static void draw(GuiGraphics g, Font font, int screenW, int screenH, Rows r) {
        int tabGap = 4;
        int tabsW = 0;
        for (StaffMode m : StaffMode.values()) tabsW += font.width(I18n.get("create_stats.surveyor.mode." + m.key)) + 8 + tabGap;
        int swatchW = r.swatch() == 0 ? 0 : 9;
        int nameW = font.width(r.pathway());
        int factsW = r.facts().isEmpty() ? 0 : font.width(r.facts()) + 8;
        int hereW = font.width(r.here());
        int hintW = font.width(r.hint());
        int noteW = r.note().isEmpty() ? 0 : font.width(r.note());
        int max = Math.min(screenW - 16, 520);
        int row1 = tabsW + 12 + swatchW + nameW + factsW;
        int row2 = hereW + (hereW > 0 ? 12 : 0) + hintW;
        int w = Math.min(max, Math.max(Math.max(row1, row2), noteW) + PAD * 2);
        int lines = r.note().isEmpty() ? 2 : 3;
        int h = PAD + 13 + 4 + 10 * (lines - 1) + PAD - 1;
        int x = (screenW - w) / 2;
        int y = screenH - LIFT - h;
        bevel(g, x, y, w, h);

        int tx = x + PAD;
        for (StaffMode m : StaffMode.values()) {
            String label = I18n.get("create_stats.surveyor.mode." + m.key);
            int tw = font.width(label) + 8;
            if (m == r.mode()) {
                g.fill(tx, y + PAD - 1, tx + tw, y + PAD + 12, OUTLINE);
                g.fill(tx + 1, y + PAD, tx + tw - 1, y + PAD + 11, HIGHLIGHT);
                g.fill(tx + 2, y + PAD + 1, tx + tw - 1, y + PAD + 11, FACE);
                g.fill(tx + 1, y + PAD + 9, tx + tw - 1, y + PAD + 11, m.colour);
                g.drawString(font, label, tx + 4, y + PAD + 1, LABEL, false);
            } else {
                g.drawString(font, label, tx + 4, y + PAD + 1, r.menu() ? LABEL : SHADOW, false);
            }
            tx += tw + tabGap;
        }

        int right = x + w - PAD;
        String facts = r.facts();
        int room = right - (tx + 8) - swatchW - nameW;
        if (factsW > room) facts = "";
        int nx = right - (facts.isEmpty() ? 0 : font.width(facts) + 8) - nameW;
        if (!facts.isEmpty()) g.drawString(font, facts, right - font.width(facts), y + PAD + 1, SHADOW, false);
        String name = fit(font, r.pathway(), Math.max(0, right - (tx + 8) - swatchW - (facts.isEmpty() ? 0 : font.width(facts) + 8)));
        nx = right - (facts.isEmpty() ? 0 : font.width(facts) + 8) - font.width(name);
        g.drawString(font, name, nx, y + PAD + 1, LABEL, false);
        if (r.swatch() != 0) {
            g.fill(nx - 10, y + PAD, nx - 2, y + PAD + 8, OUTLINE);
            g.fill(nx - 9, y + PAD + 1, nx - 3, y + PAD + 7, r.swatch());
        }

        int row = y + PAD + 13 + 3;
        g.fill(x + PAD, row - 2, x + w - PAD, row - 1, RECESSED);
        String hint = r.hint();
        int hintX = x + w - PAD - font.width(hint);
        String here = fit(font, r.here(), Math.max(0, hintX - 12 - (x + PAD)));
        if (here.isEmpty() && !r.here().isEmpty()) {
            hint = "";
            here = fit(font, r.here(), w - PAD * 2);
        }
        g.drawString(font, here, x + PAD, row, r.hereColour(), false);
        if (!hint.isEmpty()) g.drawString(font, hint, hintX, row, SHADOW, false);
        if (!r.note().isEmpty()) {
            g.drawString(font, fit(font, r.note(), w - PAD * 2), x + PAD, row + 10, r.menu() ? LABEL : SHADOW, false);
        }
    }

    private static void edgeArrows(GuiGraphics g, Minecraft mc, float partial) {
        Player p = mc.player;
        if (p == null) return;
        Vec3 eye = p.getEyePosition(partial);
        double yaw = Math.toRadians(p.getViewYRot(partial));
        double fov = Math.toRadians(mc.options.fov().get()) * 0.5 * g.guiWidth() / (double) Math.max(1, g.guiHeight());
        int w = g.guiWidth();
        int h = g.guiHeight();
        for (PathwayClientCache.Vehicle v : PathwayClientCache.vehicles()) {
            if (v.now == null) continue;
            double[] at = v.at(PathwayClientCache.clientTime() + partial);
            double dx = at[0] - eye.x;
            double dz = at[2] - eye.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 4.0) continue;

            double bearing = Math.atan2(-dx, dz);
            double rel = com.createstats.surveyor.core.PathSpline.wrapAngle(bearing - yaw);
            if (Math.abs(rel) < fov * 0.9) continue;
            boolean left = rel < 0;
            int ax = left ? 6 : w - 6;
            int ay = (int) (h * 0.5 + Math.max(-0.35, Math.min(0.35, (at[1] - eye.y) / Math.max(8.0, dist))) * -h);
            int col = PathwayRenderer.colour(colourOf(v));
            for (int i = 0; i < 5; i++) {
                int x0 = left ? ax + i : ax - i;
                g.fill(x0, ay - (4 - i), x0 + 1, ay + (5 - i), col);
            }
            String label = v.now.name() + " " + Math.round(dist) + " b";
            int lw = mc.font.width(label);
            int lx = left ? ax + 8 : ax - 8 - lw;
            g.fill(lx - 2, ay - 5, lx + lw + 2, ay + 5, 0x90000000);
            g.drawString(mc.font, label, lx, ay - 4, 0xFFFFFFFF, false);
        }
    }

    private static int colourOf(PathwayClientCache.Vehicle v) {
        PathwayClientCache.Entry e = PathwayClientCache.get(v.now.pathway());
        return e == null ? 0 : e.pathway.colour();
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }

    static String fit(Font font, String s, int max) {
        if (font.width(s) <= max) return s;
        String dots = "...";
        int dw = font.width(dots);
        if (max <= dw) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(b.toString() + s.charAt(i)) + dw > max) break;
            b.append(s.charAt(i));
        }
        return b.toString().stripTrailing() + dots;
    }

    @SuppressWarnings("unused")
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @SuppressWarnings("unused")
    private static List<String> none() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    private static String words(int s) {
        return RouteWords.seconds(s);
    }
}
