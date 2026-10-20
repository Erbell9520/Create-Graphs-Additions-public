package com.createstats.holomk3.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.holomk3.render.HoloPainter;
import com.createstats.holomk3.render.ScreenHoloSink;
import com.createstats.holomk3.scene.HoloScene;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Slider;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.ui.Widget;

@OnlyIn(Dist.CLIENT)
public final class HoloConsole {
    private HoloConsole() {
    }

    public enum Tab {
        PROJECTION("Projection"),
        VOLUME("Volume"),
        DATA("Data");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    public static final class Model {
        public HoloMk3Settings cfg = new HoloMk3Settings();
        public Tab tab = Tab.PROJECTION;

        public String title = "Holographic Projector Mk III";
        public String position = "";

        public float power = -1.0f;

        public final List<Channel> channels = new ArrayList<>();
        public final Set<String> hidden = new LinkedHashSet<>();

        public boolean scanEnabled;
        public boolean redstoneEnabled;
        public String redstoneChannelId = "";
        public float redstoneThreshold;
        public boolean redstoneAbove = true;
        public int redstoneMode;

        public HoloScene preview;

        public boolean dirty;

        public void touch() {
            this.dirty = true;
        }

        public boolean isHidden(String id) {
            return this.hidden.contains(id);
        }

        public void toggleHidden(String id) {
            if (!this.hidden.remove(id)) {
                this.hidden.add(id);
            }
            touch();
        }
    }

    public record Channel(String id, String label, String value, boolean live) {
    }

    public record Layout(Rect frame, Rect header, Rect rail, Rect body, Rect preview, Rect footer) {
    }

    public static Layout layout(int width, int height) {
        final float w = Math.min(width - 24.0f, 620.0f);
        final float h = Math.min(height - 24.0f, 360.0f);
        final Rect frame = new Rect(Math.round((width - w) * 0.5f), Math.round((height - h) * 0.5f),
                w, h);
        final Rect inner = frame.inset(Tokens.S3);
        final Rect header = inner.top(30.0f);
        final Rect rest = inner.below(30.0f + Tokens.S2);
        final Rect footer = new Rect(rest.x, rest.bottom() - 16.0f, rest.w, 16.0f);
        final Rect above = rest.withHeight(rest.h - 20.0f);
        final Rect rail = above.left(84.0f);
        final Rect afterRail = above.afterLeft(84.0f + Tokens.S2);

        final float previewW = Math.max(190.0f, afterRail.w * 0.46f);
        final Rect body = afterRail.withWidth(afterRail.w - previewW - Tokens.S2);
        final Rect preview = new Rect(afterRail.right() - previewW, afterRail.y, previewW,
                afterRail.h);
        return new Layout(frame, header, rail, body, preview, footer);
    }

    public static Rect row(Rect body, int index) {
        final float rowH = 22.0f;
        return new Rect(body.x, body.y + index * rowH, body.w, rowH - 4.0f);
    }

    public static Rect control(Rect row) {
        final float labelW = Math.min(96.0f, row.w * 0.42f);
        return new Rect(row.x + labelW, row.y, row.w - labelW, row.h);
    }

    public static List<Widget> controls(Model m, Layout layout) {
        final List<Widget> out = new ArrayList<>();
        final Rect body = layout.body();
        switch (m.tab) {
            case PROJECTION -> {
                out.add(segmented(body, 0, names(HoloMk3Settings.Style.values()),
                        () -> m.cfg.getStyle().ordinal(),
                        i -> {
                            m.cfg.setStyle(HoloMk3Settings.Style.values()[i]);
                            m.touch();
                        }));
                out.add(segmented(body, 1, names(HoloMk3Settings.Layout.values()),
                        () -> m.cfg.getLayout().ordinal(),
                        i -> {
                            m.cfg.setLayout(HoloMk3Settings.Layout.values()[i]);
                            m.touch();
                        }));
                out.add(segmented(body, 2, names(HoloMk3Settings.Motion.values()),
                        () -> m.cfg.getMotion().ordinal(),
                        i -> {
                            m.cfg.setMotion(HoloMk3Settings.Motion.values()[i]);
                            m.touch();
                        }));
                out.add(slider(body, 3, m, m.cfg.getSpinSpeed() / 4.0f,
                        v -> m.cfg.setSpinSpeed(v * 4.0f)));
                out.add(slider(body, 4, m, (m.cfg.getGlow() - 0.1f) / 1.4f,
                        v -> m.cfg.setGlow(0.1f + v * 1.4f)));
                out.add(toggle(body, 5, m, m.cfg::isShaderPath, m.cfg::setShaderPath));
            }
            case VOLUME -> {
                out.add(slider(body, 0, m, extent(m.cfg.getVolumeWidth()),
                        v -> m.cfg.setVolumeWidth(blocks(v))));
                out.add(slider(body, 1, m, extent(m.cfg.getVolumeHeight()),
                        v -> m.cfg.setVolumeHeight(blocks(v))));
                out.add(slider(body, 2, m, extent(m.cfg.getVolumeDepth()),
                        v -> m.cfg.setVolumeDepth(blocks(v))));
                out.add(slider(body, 3, m, m.cfg.getLift() / 6.0f, v -> m.cfg.setLift(v * 6.0f)));
                out.add(slider(body, 4, m, m.cfg.getDepthCue(), m.cfg::setDepthCue));
                out.add(slider(body, 5, m, m.cfg.getSweepSeconds() / 30.0f,
                        v -> m.cfg.setSweepSeconds(v * 30.0f)));
                out.add(toggle(body, 6, m, m.cfg::isGraticule, m.cfg::setGraticule));
                out.add(toggle(body, 7, m, m.cfg::isFloorShadows, m.cfg::setFloorShadows));
            }
            case DATA -> {
                out.add(toggle(body, 0, m, () -> m.scanEnabled, v -> {
                    m.scanEnabled = v;
                    m.touch();
                }));
                out.add(slider(body, 1, m,
                        (m.cfg.getLaneLimit() - HoloMk3Settings.MIN_LANES)
                                / (float) (HoloMk3Settings.MAX_LANES - HoloMk3Settings.MIN_LANES),
                        v -> m.cfg.setLaneLimit(Math.round(HoloMk3Settings.MIN_LANES
                                + v * (HoloMk3Settings.MAX_LANES - HoloMk3Settings.MIN_LANES)))));
                out.add(toggle(body, 2, m, () -> m.redstoneEnabled, v -> {
                    m.redstoneEnabled = v;
                    m.touch();
                }));
                out.add(segmented(body, 3, new String[]{"Threshold", "Proportional"},
                        () -> m.redstoneMode,
                        i -> {
                            m.redstoneMode = i;
                            m.touch();
                        }));
                out.add(segmented(body, 4, new String[]{"Above", "Below"},
                        () -> m.redstoneAbove ? 0 : 1,
                        i -> {
                            m.redstoneAbove = i == 0;
                            m.touch();
                        }));
            }
            default -> {
            }
        }
        return out;
    }

    public static String[] labels(Tab tab) {
        return switch (tab) {
            case PROJECTION -> new String[]{"Style", "Layout", "Motion", "Spin", "Glow", "Shader"};
            case VOLUME -> new String[]{"Width", "Height", "Depth", "Lift", "Depth cue", "Sweep",
                    "Graticule", "Drop lines"};
            case DATA -> new String[]{"Nearby scan", "Channels shown", "Redstone out", "Mode",
                    "Compare"};
        };
    }

    public static String value(Model m, Tab tab, int index) {
        return switch (tab) {
            case PROJECTION -> switch (index) {
                case 3 -> String.format("%.2f deg/t", m.cfg.getSpinSpeed());
                case 4 -> Math.round(m.cfg.getGlow() * 100.0f) + "%";
                default -> "";
            };
            case VOLUME -> switch (index) {
                case 0 -> blocksLabel(m.cfg.getVolumeWidth());
                case 1 -> blocksLabel(m.cfg.getVolumeHeight());
                case 2 -> blocksLabel(m.cfg.getVolumeDepth());
                case 3 -> blocksLabel(m.cfg.getLift());
                case 4 -> Math.round(m.cfg.getDepthCue() * 100.0f) + "%";
                case 5 -> m.cfg.getSweepSeconds() < 0.1f ? "off"
                        : String.format("%.0fs", m.cfg.getSweepSeconds());
                default -> "";
            };
            case DATA -> index == 1 ? String.valueOf(m.cfg.getLaneLimit()) : "";
        };
    }

    private static String blocksLabel(float v) {
        return String.format("%.1f", v) + (v == 1.0f ? " block" : " blocks");
    }

    private static float extent(float blocks) {
        return (blocks - HoloMk3Settings.MIN_EXTENT)
                / (HoloMk3Settings.MAX_EXTENT - HoloMk3Settings.MIN_EXTENT);
    }

    private static float blocks(float t) {
        return HoloMk3Settings.MIN_EXTENT
                + t * (HoloMk3Settings.MAX_EXTENT - HoloMk3Settings.MIN_EXTENT);
    }

    private static Widget segmented(Rect body, int index, String[] options,
                                    java.util.function.IntSupplier selected,
                                    java.util.function.IntConsumer onChange) {
        final Segmented seg = new Segmented(options, selected, onChange);
        seg.textScale(Text.MICRO);
        seg.setBounds(control(row(body, index)));
        return seg;
    }

    private static Widget slider(Rect body, int index, Model m, float value,
                                 java.util.function.Consumer<Float> apply) {
        final float[] held = {value};
        final Slider s = new Slider(() -> held[0], v -> {
            held[0] = v;
            apply.accept(v);
            m.touch();
        });
        final Rect r = control(row(body, index));
        s.setBounds(new Rect(r.x, r.y + (r.h - Slider.HEIGHT) * 0.5f, r.w, Slider.HEIGHT));
        return s;
    }

    private static Widget toggle(Rect body, int index, Model m,
                                 java.util.function.BooleanSupplier value,
                                 java.util.function.Consumer<Boolean> apply) {
        final Toggle t = new Toggle(value, v -> {
            apply.accept(v);
            m.touch();
        });
        final Rect r = control(row(body, index));
        t.setBounds(new Rect(r.x, r.y + (r.h - Toggle.HEIGHT) * 0.5f, Toggle.WIDTH, Toggle.HEIGHT));
        return t;
    }

    private static String[] names(Object[] values) {
        final String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            final Object v = values[i];
            if (v instanceof HoloMk3Settings.Style style) {
                out[i] = style.getDisplayName();
            } else if (v instanceof HoloMk3Settings.Layout lay) {
                out[i] = lay.getDisplayName();
            } else if (v instanceof HoloMk3Settings.Motion motion) {
                out[i] = motion.getDisplayName();
            } else {
                out[i] = String.valueOf(v);
            }
        }
        return out;
    }

    public static void paint(PanelSurface s, Model m, Layout layout, List<Widget> widgets,
                             float seconds) {
        final Rect frame = layout.frame();
        Shapes.roundedRect(s, frame, Tokens.R_PANEL, Tokens.CANVAS);
        Shapes.roundedRectOutline(s, frame, Tokens.R_PANEL, 1.0f, Tokens.LINE);

        header(s, m, layout.header());
        rail(s, m, layout.rail());
        body(s, m, layout.body(), widgets);
        preview(s, m, layout.preview(), seconds);
        footer(s, m, layout.footer());
    }

    private static void header(PanelSurface s, Model m, Rect r) {
        s.text(m.title, r.x, r.y + 2.0f, Tokens.INK, Text.SUB);
        final String sub = m.position.isEmpty() ? "" : m.position;
        s.text(sub, r.x, r.y + 15.0f, Tokens.INK_3, Text.MICRO);

        if (m.power >= 0.0f) {
            final float barW = 54.0f;
            final Rect bar = new Rect(r.right() - barW, r.y + 6.0f, barW, 5.0f);
            Shapes.rect(s, bar.x, bar.y, bar.w, bar.h, Tokens.LINE);
            Shapes.rect(s, bar.x, bar.y, bar.w * Math.max(0.0f, Math.min(1.0f, m.power)), bar.h,
                    m.power < 0.25f ? Tokens.CRITICAL : m.power < 0.6f ? Tokens.CAUTION : Tokens.GOOD);
            s.textRight(Math.round(m.power * 100.0f) + "% buffer", r.right(), r.y + 15.0f,
                    Tokens.INK_3, Text.MICRO);
        } else {
            s.textRight("creative energy", r.right(), r.y + 8.0f, Tokens.INK_3,
                    Text.MICRO);
        }
    }

    private static void rail(PanelSurface s, Model m, Rect r) {
        for (int i = 0; i < Tab.values().length; i++) {
            final Tab tab = Tab.values()[i];
            final Rect pill = tabRect(r, i);
            if (tab == m.tab) {
                Shapes.roundedRect(s, pill, Tokens.R_CHIP, Tokens.RAISED);
                Shapes.rect(s, pill.x, pill.y + 3.0f, 2.0f, pill.h - 6.0f, Tokens.ACCENT);
            }
            s.text(tab.label, pill.x + 9.0f, pill.y + 6.0f,
                    tab == m.tab ? Tokens.INK : Tokens.INK_2, Text.META);
        }
    }

    public static Rect tabRect(Rect rail, int index) {
        return new Rect(rail.x, rail.y + index * 24.0f, rail.w, 21.0f);
    }

    private static void body(PanelSurface s, Model m, Rect r, List<Widget> widgets) {
        final String[] names = labels(m.tab);
        for (int i = 0; i < names.length; i++) {
            final Rect line = row(r, i);
            s.text(names[i], line.x, line.y + 6.0f, Tokens.INK_2, Text.META);

            final String v = value(m, m.tab, i);
            if (!v.isEmpty()) {
                s.textRight(v, control(line).x - 6.0f, line.y + 6.0f, Tokens.INK_3, Text.MICRO);
            }
        }
        for (final Widget widget : widgets) {
            widget.draw(s);
        }
        if (m.tab == Tab.DATA) {
            channels(s, m, new Rect(r.x, row(r, 5).y + 6.0f, r.w, r.bottom() - row(r, 5).y - 6.0f));
        }
    }

    private static void channels(PanelSurface s, Model m, Rect r) {
        Shapes.rect(s, r.x, r.y - 4.0f, r.w, 1.0f, Tokens.LINE);
        if (m.channels.isEmpty()) {
            s.text("no channels yet", r.x, r.y + 4.0f, Tokens.INK_3, Text.MICRO);
            s.text("link a source, or switch the nearby scan on", r.x, r.y + 14.0f, Tokens.INK_3,
                    Text.MICRO);
            return;
        }
        final int shown = Math.min(m.channels.size(), (int) (r.h / 14.0f));
        for (int i = 0; i < shown; i++) {
            final Channel ch = m.channels.get(i);
            final Rect line = channelRect(r, i);
            final boolean hidden = m.isHidden(ch.id());
            final boolean focused = ch.id().equals(m.cfg.getFocusChannelId());
            if (focused) {
                Shapes.roundedRect(s, new Rect(line.x - 2.0f, line.y, line.w + 4.0f, line.h),
                        Tokens.R_CHIP, Tokens.RAISED);
            }
            final int dot = hidden ? Tokens.LINE_HI : Tokens.seriesFor(ch.id());
            Shapes.circle(s, line.x + 4.0f, line.centerY(), 3.0f, dot);
            s.text(ch.label(), line.x + 12.0f, line.y + 2.0f,
                    hidden ? Tokens.INK_3 : Tokens.INK_2, Text.MICRO);
            s.textRight(ch.live() ? ch.value() : "stale", line.right(), line.y + 2.0f,
                    ch.live() ? Tokens.INK_2 : Tokens.CAUTION, Text.MICRO);
        }
        if (m.channels.size() > shown) {
            s.text("+" + (m.channels.size() - shown) + " more", r.x, r.bottom() - 9.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    public static Rect channelRect(Rect list, int index) {
        return new Rect(list.x, list.y + 2.0f + index * 14.0f, list.w, 12.0f);
    }

    public static Rect channelList(Layout layout, Model m) {
        if (m.tab != Tab.DATA) {
            return Rect.EMPTY;
        }
        final Rect r = layout.body();
        return new Rect(r.x, row(r, 5).y + 6.0f, r.w, r.bottom() - row(r, 5).y - 6.0f);
    }

    private static void preview(PanelSurface s, Model m, Rect r, float seconds) {
        Shapes.roundedRect(s, r, Tokens.R_CARD, 0xFF07090C);
        Shapes.roundedRectOutline(s, r, Tokens.R_CARD, 1.0f, Tokens.LINE);
        s.clip(r);

        final HoloMk3Settings cfg = m.cfg;
        final float span = Math.max(cfg.getVolumeWidth(),
                Math.max(cfg.getVolumeHeight(), cfg.getVolumeDepth()));
        final Vector3f target = new Vector3f(0.0f, cfg.getVolumeHeight() * 0.5f, 0.0f);
        final double a = Math.toRadians(-26.0 + seconds * 12.0);
        final float dist = span * 3.05f;
        final Vector3f eye = new Vector3f(
                (float) Math.sin(a) * dist * 0.92f,
                target.y + span * 0.36f,
                (float) Math.cos(a) * dist * 0.92f);

        final ScreenHoloSink sink = new ScreenHoloSink(s, r, eye, target, 46.0f);
        if (m.preview != null) {
            HoloPainter.paint(sink, m.preview, cfg, seconds);
            sink.flushSolids();
        }
        s.unclip();

        s.text(cfg.getStyle().getDisplayName() + " / " + cfg.getLayout().getDisplayName(),
                r.x + 6.0f, r.bottom() - 12.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void footer(PanelSurface s, Model m, Rect r) {
        final String note = m.dirty ? "unsaved" : "saved";
        s.text("sneak-right-click the block to step the style", r.x, r.y + 3.0f, Tokens.INK_3,
                Text.MICRO);
        s.textRight(note, r.right(), r.y + 3.0f, m.dirty ? Tokens.CAUTION : Tokens.INK_3,
                Text.MICRO);
    }
}
