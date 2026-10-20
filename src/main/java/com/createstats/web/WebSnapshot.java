package com.createstats.web;

import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WebSnapshot {
    public static final int VERSION = 1;

    public static final int HISTORY_CAP = 240;

    private static final float TPS = 20.0f;

    private WebSnapshot() {
    }

    // never throws, a browser tab is not worth the tick loop
    public static String of(StatsDisplayPanelBE be, long uptimeMillis) {
        try {
            return build(be, uptimeMillis);
        } catch (final RuntimeException failed) {
            return unavailable("This panel could not be read this instant.", uptimeMillis);
        }
    }

    public static String unavailable(String reason, long uptimeMillis) {
        final WebJson j = new WebJson();
        j.beginObject();
        j.put("v", VERSION);
        j.put("t", uptimeMillis);
        j.put("ok", false);
        j.put("reason", reason == null ? "" : reason);
        j.name("panel").beginObject()
                .put("name", "No panel")
                .put("status", "NO DATA")
                .putColor("statusColor", Tokens.INK_3)
                .put("range", "")
                .put("rangeSeconds", 0)
                .put("mode", "INSTRUMENTS")
                .put("station", "")
                .put("pos", "")
                .endObject();
        writeStyle(j, new Mk3Settings());
        j.name("layout").beginObject()
                .put("cols", 0).put("rows", 0).put("preset", "")
                .name("tiles").beginArray().endArray()
                .endObject();
        writeAuto(j, new Mk3Settings());
        j.name("lamps").beginArray();
        for (int i = 0; i < 6; i++) {
            j.value(0);
        }
        j.endArray();
        j.name("channels").beginArray().endArray();
        WebPower.writeEmpty(j);
        j.endObject();
        return j.toString();
    }

    private static String build(StatsDisplayPanelBE be, long uptimeMillis) {
        if (be == null || be.isRemoved()) {
            return unavailable("The panel this was serving is gone.", uptimeMillis);
        }
        final Mk3Settings settings = be instanceof Mk3PanelBlockEntity mk3
                ? mk3.mk3Settings()
                : new Mk3Settings();
        final List<StatsDisplayPanelBE.DataChannel> channels = channelsOf(be);

        boolean alerting = false;
        for (final StatsDisplayPanelBE.DataChannel channel : channels) {
            alerting |= isAlerting(channel);
        }

        final WebJson j = new WebJson();
        j.beginObject();
        j.put("v", VERSION);
        j.put("t", uptimeMillis);
        j.put("ok", true);
        j.put("reason", "");

        writePanel(j, be, settings, channels, alerting);
        writeStyle(j, settings);
        writeLayout(j, settings.layout);
        writeAuto(j, settings);
        writeLamps(j, settings, channels);

        j.name("channels").beginArray();
        for (final StatsDisplayPanelBE.DataChannel channel : channels) {
            writeChannel(j, channel, settings.timeRangeSeconds);
        }
        j.endArray();

        WebPower.write(j, be);

        j.endObject();
        return j.toString();
    }

    private static void writePanel(WebJson j, StatsDisplayPanelBE be, Mk3Settings settings,
                                   List<StatsDisplayPanelBE.DataChannel> channels,
                                   boolean alerting) {
        final String status;
        final int statusColor;
        if (channels.isEmpty()) {
            status = "NO DATA";
            statusColor = Tokens.INK_3;
        } else if (alerting) {
            status = "ALERT";
            statusColor = Tokens.CRITICAL;
        } else {
            status = "LIVE";
            statusColor = Tokens.GOOD;
        }

        final BlockPos pos = be.getBlockPos();
        j.name("panel").beginObject()
                .put("name", titleFor(channels))
                .put("status", status)
                .putColor("statusColor", statusColor)
                .put("range", rangeLabel(settings.timeRangeSeconds))
                .put("rangeSeconds", settings.timeRangeSeconds)
                .put("mode", settings.faceMode == null ? "INSTRUMENTS" : settings.faceMode.name())
                .put("station", settings.boardStation == null ? "" : settings.boardStation)
                .put("pos", pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .endObject();
    }

    private static void writeStyle(WebJson j, Mk3Settings settings) {
        j.name("style").beginObject()
                .putColor("accent", settings.accent)
                .putColor("ground", settings.ground)
                .put("grid", settings.grid)
                .put("glass", settings.glass)
                .endObject();
    }

    private static void writeLayout(WebJson j, Mk3Layout layout) {
        j.name("layout").beginObject();
        if (layout == null) {
            j.put("cols", 0).put("rows", 0).put("preset", "")
                    .name("tiles").beginArray().endArray();
            j.endObject();
            return;
        }
        j.put("cols", layout.cols());
        j.put("rows", layout.rows());
        j.put("preset", layout.presetId == null ? "" : layout.presetId);
        j.name("tiles").beginArray();
        for (final Tile tile : layout.tiles()) {
            if (tile == null) {
                continue;
            }
            j.beginObject()
                    .put("x", tile.gx)
                    .put("y", tile.gy)
                    .put("w", Math.max(1, tile.gw))
                    .put("h", Math.max(1, tile.gh))
                    .put("ch", tile.channelId == null ? "" : tile.channelId)
                    .put("inst", tile.instrumentId == null ? "" : tile.instrumentId)
                    .put("label", tile.label)
                    .put("value", tile.value)
                    .endObject();
        }
        j.endArray();
        j.endObject();
    }

    private static void writeAuto(WebJson j, Mk3Settings settings) {
        j.name("auto").beginObject()
                .put("fallback", settings.instrument == null ? "area" : settings.instrument)
                .put("tiny", settings.autoTiny == null ? "numeric" : settings.autoTiny)
                .put("wide", settings.autoWide == null ? "spark" : settings.autoWide)
                .put("square", settings.autoSquare == null ? "arc" : settings.autoSquare)
                .endObject();
    }

    private static void writeLamps(WebJson j, Mk3Settings settings,
                                   List<StatsDisplayPanelBE.DataChannel> channels) {
        j.name("lamps").beginArray();
        for (int i = 0; i < 6; i++) {
            final String bound = settings.lampChannels != null && i < settings.lampChannels.length
                    ? settings.lampChannels[i]
                    : "";
            if (bound == null || bound.isEmpty()) {
                final StatsDisplayPanelBE.DataChannel at = i < channels.size()
                        ? channels.get(i)
                        : null;
                j.value(at == null ? 0 : (isAlerting(at) ? 2 : 1));
                continue;
            }
            int state = 0;
            for (final StatsDisplayPanelBE.DataChannel channel : channels) {
                if (bound.equals(channel.getId())) {
                    state = isAlerting(channel) ? 2 : 1;
                    break;
                }
            }
            j.value(state);
        }
        j.endArray();
    }

    private static void writeChannel(WebJson j, StatsDisplayPanelBE.DataChannel channel,
                                     int rangeSeconds) {
        final TimeSeriesStore store = channel.getStore();
        j.beginObject();
        j.put("id", channel.getId() == null ? "" : channel.getId());
        j.put("label", channel.getLabel() == null ? channel.getId() : channel.getLabel());
        j.putColor("color", Tokens.seriesFor(channel.getId()));
        j.put("alerting", isAlerting(channel));

        j.name("alert").beginObject()
                .put("on", channel.isAlertEnabled())
                .put("above", channel.isAlertAbove())
                .put("at", channel.getAlertThreshold())
                .endObject();

        if (store == null) {
            j.put("unit", "").put("value", 0.0).put("min", 0.0).put("max", 1.0)
                    .put("avg", 0.0).put("lo", 0.0).put("hi", 0.0).put("rate", 0.0)
                    .put("samples", 0).put("dt", 1.0).put("stale", true)
                    .name("h").beginArray().endArray();
            j.endObject();
            return;
        }

        final MetricType type = store.getMetricType();
        final float latest = store.getLatestValue();
        j.put("unit", type == null || type.getUnit() == null ? "" : type.getUnit());
        j.put("value", latest);

        float hi = Math.max(latest, store.getMax());
        if (!Float.isFinite(hi) || hi <= 0.0f) {
            hi = Math.max(1.0f, Math.abs(latest));
        }
        float lo = Math.min(0.0f, store.getMin());
        if (!Float.isFinite(lo)) {
            lo = 0.0f;
        }
        j.put("min", lo);
        j.put("max", hi * 1.08f + 1.0e-3f);
        j.put("avg", store.getAverage());
        j.put("lo", store.getMin());
        j.put("hi", store.getMax());
        j.put("rate", store.getRateOfChange());
        j.put("samples", store.getSampleCount());

        final int interval = Math.max(1, store.getSampleIntervalTicks());
        j.put("dt", interval / TPS);
        j.put("stale", store.getSampleCount() <= 0);

        j.name("h").beginArray();
        final float[] window = historyOf(store, rangeSeconds);
        for (final float sample : window) {
            j.value(sample);
        }
        j.endArray();
        j.endObject();
    }

    private static float[] historyOf(TimeSeriesStore store, int rangeSeconds) {
        final int budget = rangeSeconds <= 0
                ? HISTORY_CAP
                : Math.min(HISTORY_CAP, Math.max(1, store.getSampleCountForSeconds(rangeSeconds)));
        final float[] window = store.getDataWindow(budget);
        return window == null ? new float[0] : window;
    }

    private static List<StatsDisplayPanelBE.DataChannel> channelsOf(StatsDisplayPanelBE be) {
        final List<StatsDisplayPanelBE.DataChannel> visible = be.getVisibleChannels();
        return visible == null ? List.of() : visible;
    }

    private static boolean isAlerting(StatsDisplayPanelBE.DataChannel channel) {
        return channel != null && channel.isAlertEnabled() && channel.isAlertTriggered();
    }

    private static String rangeLabel(int seconds) {
        final String[] labels = Mk3Settings.TIME_RANGE_LABELS;
        final int index = Mk3Settings.timeRangeIndex(seconds);
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    private static String titleFor(List<StatsDisplayPanelBE.DataChannel> channels) {
        if (channels.isEmpty()) {
            return "Stats Display";
        }
        if (channels.size() == 1) {
            final String label = channels.get(0).getLabel();
            return label == null || label.isEmpty() ? "Stats Display" : label;
        }
        String source = null;
        for (final StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = sourceIdOf(channel.getId());
            if (source == null) {
                source = id;
            } else if (!source.equals(id)) {
                return channels.size() + " signals";
            }
        }
        return source == null || source.isEmpty()
                ? channels.size() + " signals"
                : pretty(source);
    }

    private static String sourceIdOf(String channelId) {
        if (channelId != null && channelId.startsWith("src_")) {
            final int auto = channelId.indexOf("_auto_");
            if (auto > 4) {
                return channelId.substring(4, auto);
            }
        }
        return channelId == null ? "" : channelId;
    }

    private static String pretty(String raw) {
        final String s = raw.replace('_', ' ').trim();
        return s.isEmpty() ? "Source" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
