package com.createstats.holomk3.scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.blockentity.HolographicProjectorBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.statsmk3.instrument.Plot;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;

@OnlyIn(Dist.CLIENT)
public final class SceneBuilder {
    private static final float PAD = 0.08f;

    private static final long STALE_TICKS = 600L;

    private SceneBuilder() {
    }

    public static HoloScene build(Collection<DataChannel> channels, Set<String> hidden,
                                  HoloMk3Settings cfg, long gameTime, float seconds,
                                  float boot, int budget) {
        final HoloScene scene = new HoloScene();
        scene.w = cfg.getVolumeWidth();
        scene.h = cfg.getVolumeHeight();
        scene.d = cfg.getVolumeDepth();
        scene.boot = boot < 0.0f ? 0.0f : Math.min(boot, 1.0f);
        scene.sweep = sweepAt(cfg, seconds);

        final List<DataChannel> live = live(channels, hidden, cfg.getLaneLimit());
        scene.empty = live.isEmpty();
        if (scene.empty) {
            scene.title = "NO SIGNAL";
            scene.subtitle = "no channel is reporting";
            return scene;
        }

        final int focusIndex = focusIndex(live, cfg.getFocusChannelId());
        if (focusIndex > 0) {
            live.add(0, live.remove(focusIndex));
        }

        final int n = live.size();
        for (int i = 0; i < n; i++) {
            final Built built = lane(live.get(i), scene, cfg, i, n, gameTime, budget);
            scene.lanes.add(built.lane());
            if (i == 0) {
                scene.focus = built.lane();
                scene.title = built.lane().label;
                scene.axisValueLo = format(built.metric(), built.lane().lo);
                scene.axisValueHi = format(built.metric(), built.lane().hi);
                scene.axisTimeSpan = span(built.store());
            }
        }
        scene.subtitle = n == 1 ? built1Subtitle(scene.focus)
                : n + " channels";
        scene.axisDepth = n > 1 ? "channel" : "";
        scene.stale = allStale(scene.lanes);
        return scene;
    }

    private static String built1Subtitle(HoloScene.Lane lane) {
        if (lane == null) {
            return "";
        }
        return lane.unit.isEmpty() ? "1 channel" : lane.unit;
    }

    private static boolean allStale(List<HoloScene.Lane> lanes) {
        for (final HoloScene.Lane lane : lanes) {
            if (!lane.stale) {
                return false;
            }
        }
        return !lanes.isEmpty();
    }

    private static List<DataChannel> live(Collection<DataChannel> channels, Set<String> hidden, int limit) {
        final List<DataChannel> out = new ArrayList<>();
        if (channels == null) {
            return out;
        }
        for (final DataChannel ch : channels) {
            if (ch == null || ch.getStore().getSampleCount() <= 0) {
                continue;
            }
            if (hidden != null && hidden.contains(ch.getId())) {
                continue;
            }
            out.add(ch);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static int focusIndex(List<DataChannel> live, String focusId) {
        if (focusId == null || focusId.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i).getId().equals(focusId)) {
                return i;
            }
        }
        return 0;
    }

    private record Built(HoloScene.Lane lane, TimeSeriesStore store, MetricType metric) {
    }

    private static Built lane(DataChannel ch, HoloScene scene, HoloMk3Settings cfg,
                              int index, int count, long gameTime, int budget) {
        final TimeSeriesStore store = ch.getStore();
        final MetricType metric = store.getMetricType();
        final HoloScene.Lane lane = new HoloScene.Lane(ch.getId());
        lane.label = ch.getLabel();
        lane.unit = metric == null ? "" : metric.getUnit();
        lane.color = Tokens.seriesFor(ch.getId());
        lane.focused = index == 0;
        lane.latest = store.getLatestValue();
        lane.min = store.getMin();
        lane.max = store.getMax();
        lane.mean = store.getAverage();
        lane.valueText = metric == null ? String.valueOf(lane.latest) : metric.formatValue(lane.latest);
        lane.stale = store.hasValidTimestamps()
                && gameTime - store.getLastSampleTick() > STALE_TICKS;

        place(lane, scene, cfg, index, count);
        trace(lane, store, budget);
        return new Built(lane, store, metric);
    }

    private static void place(HoloScene.Lane lane, HoloScene scene, HoloMk3Settings cfg,
                              int index, int count) {
        if (cfg.getLayout() == HoloMk3Settings.Layout.ARRAY) {
            final float spread = Math.min(120.0f, 32.0f * Math.max(1, count - 1));
            final float a = count <= 1 ? 0.0f : -spread * 0.5f + spread * index / (count - 1.0f);
            final float rad = (float) Math.toRadians(a);
            final float r = scene.d * 0.35f;
            lane.yawDeg = a;
            lane.originX = (float) Math.sin(rad) * r;
            lane.originZ = r - (float) Math.cos(rad) * r;
            lane.z = lane.originZ;
            lane.planeW = scene.w / Math.max(1.0f, count * 0.62f);
            lane.planeH = scene.h * 0.82f;
            return;
        }

        lane.yawDeg = 0.0f;
        lane.originX = 0.0f;
        lane.planeW = scene.w;
        lane.planeH = scene.h;
        if (count <= 1) {
            lane.z = 0.0f;
        } else {
            final float front = scene.d * 0.42f;
            lane.z = front - scene.d * 0.84f * index / (count - 1.0f);
        }
        lane.originZ = lane.z;
    }

    private static void trace(HoloScene.Lane lane, TimeSeriesStore store, int budget) {
        final float[] window = store.getDataWindow(Math.max(2, budget));
        if (window.length < 2) {
            lane.flat = true;
            lane.lo = store.getMin();
            lane.hi = store.getMax();
            if (lane.hi - lane.lo < 1.0e-6f) {
                lane.lo -= 1.0f;
                lane.hi += 1.0f;
            }
            lane.latestY = lane.planeH * 0.5f;
            return;
        }

        final ChannelView view = new ChannelView(lane.id);
        view.history = window;
        view.historyCount = window.length;
        view.value = store.getLatestValue();
        view.min = store.getMin();
        view.max = store.getMax();

        final float res = Math.max(4.0f, budget * 0.5f);
        final Plot.Points p = Plot.map(view, new Rect(0.0f, 0.0f, res, res), budget, PAD);
        lane.lo = p.lo;
        lane.hi = p.hi;

        lane.count = p.count;
        lane.xy = new float[p.count * 2];
        for (int i = 0; i < p.count; i++) {
            final float sx = p.xy[i * 2] / res;
            final float sy = p.xy[i * 2 + 1] / res;
            lane.xy[i * 2] = -lane.planeW * 0.5f + lane.planeW * sx;
            lane.xy[i * 2 + 1] = lane.planeH * (1.0f - sy);
        }

        final float span = lane.hi - lane.lo;
        final float t = span <= 1.0e-6f ? 0.5f : (lane.latest - lane.lo) / span;
        lane.latestY = lane.planeH * Math.max(0.0f, Math.min(1.0f, t));
    }

    private static float sweepAt(HoloMk3Settings cfg, float seconds) {
        final float period = cfg.getSweepSeconds();
        if (period <= 0.05f) {
            return -1.0f;
        }
        final float phase = (seconds % period) / period;
        return phase < 0.0f ? phase + 1.0f : phase;
    }

    private static String format(MetricType metric, float value) {
        return metric == null ? trim(value) : metric.formatValue(value);
    }

    private static String trim(float value) {
        if (Math.abs(value) >= 100.0f) {
            return String.valueOf(Math.round(value));
        }
        return String.format("%.1f", value);
    }

    private static String span(TimeSeriesStore store) {
        final int seconds = store.getDurationSeconds();
        if (seconds <= 0) {
            return "";
        }
        if (seconds < 90) {
            return seconds + "s";
        }
        if (seconds < 5400) {
            return Math.round(seconds / 60.0f) + "m";
        }
        return Math.round(seconds / 3600.0f) + "h";
    }
}
