package com.createstats.statsmk3.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.Mk3ConfigPacket;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.SourceNames;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelModelCache;

import net.minecraft.client.gui.Font;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class StudioContext {
    public final StatsDisplayPanelBE panel;
    public final Font font;

    public String focusedChannelId = "";

    public String focusedSourceId = "";

    public float time;

    public PanelModel currentModel;

    private float commitCooldown;
    private boolean commitPending;

    public StudioContext(StatsDisplayPanelBE panel, Font font) {
        this.panel = panel;
        this.font = font;
        final List<StatsDisplayPanelBE.DataChannel> channels = panel.getVisibleChannels();
        if (channels != null && !channels.isEmpty()) {
            this.focusedChannelId = channels.get(0).getId();
        }
    }

    public Mk3Settings settings() {
        return this.panel instanceof Mk3PanelBlockEntity mk3 ? mk3.mk3Settings() : new Mk3Settings();
    }

    public void commit() {
        this.commitPending = true;
        if (this.panel instanceof Mk3PanelBlockEntity mk3) {
            mk3.holdClientEdits();
        }
    }

    public void tick(float dt) {
        this.time += dt;
        if (!this.commitPending) {
            return;
        }
        this.commitCooldown -= dt;
        if (this.commitCooldown <= 0.0f) {
            this.commitCooldown = 0.15f;
            this.commitPending = false;
            send();
        }
    }

    private void send() {
        if (this.panel instanceof Mk3PanelBlockEntity mk3) {
            mk3.holdClientEdits();
        }
        PacketDistributor.sendToServer(new Mk3ConfigPacket(this.panel.getBlockPos(), settings()));
    }

    public void flush() {
        if (this.commitPending) {
            this.commitPending = false;
            send();
        }
    }

    public List<StatsDisplayPanelBE.DataChannel> channels() {
        final List<StatsDisplayPanelBE.DataChannel> all = allChannels();
        if (all.isEmpty()) {
            return new ArrayList<>();
        }
        if (this.focusedSourceId.isEmpty()) {
            return all;
        }
        final List<StatsDisplayPanelBE.DataChannel> filtered = new ArrayList<>();
        for (final StatsDisplayPanelBE.DataChannel ch : all) {
            if (sourceIdOf(ch.getId()).equals(this.focusedSourceId)) {
                filtered.add(ch);
            }
        }
        return filtered.isEmpty() ? all : filtered;
    }

    public StatsDisplayPanelBE.DataChannel focusedChannel() {
        final List<StatsDisplayPanelBE.DataChannel> all = this.panel.getVisibleChannels();
        if (all == null || all.isEmpty()) {
            return null;
        }
        for (final StatsDisplayPanelBE.DataChannel ch : all) {
            if (ch.getId().equals(this.focusedChannelId)) {
                return ch;
            }
        }
        return all.get(0);
    }

    public List<StatsDisplayPanelBE.DataChannel> allChannels() {
        final Map<String, StatsDisplayPanelBE.DataChannel> all = this.panel.getAllChannels();
        final List<StatsDisplayPanelBE.DataChannel> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (final StatsDisplayPanelBE.DataChannel ch : all.values()) {
            if (ch != null && ch.getStore() != null && ch.getStore().getSampleCount() > 0) {
                out.add(ch);
            }
        }
        return out;
    }

    public boolean isHidden(String channelId) {
        return this.panel.getSettings().isChannelHidden(channelId);
    }

    public void setHidden(String channelId, boolean hidden) {
        if (channelId == null || channelId.isEmpty() || isHidden(channelId) == hidden) {
            return;
        }
        this.panel.getSettings().toggleChannelVisibility(channelId);
        pushPanelSettings();
    }

    public void setSourceHidden(String sourceId, boolean hidden) {
        boolean changed = false;
        for (final StatsDisplayPanelBE.DataChannel ch : allChannels()) {
            if (sourceIdOf(ch.getId()).equals(sourceId)
                    && this.panel.getSettings().isChannelHidden(ch.getId()) != hidden) {
                this.panel.getSettings().toggleChannelVisibility(ch.getId());
                changed = true;
            }
        }
        if (changed) {
            pushPanelSettings();
        }
    }

    private void pushPanelSettings() {
        this.panel.setSettings(this.panel.getSettings());

        this.panel.holdClientSettings();
        PacketDistributor.sendToServer(com.createstats.network.ConfigSyncPacket.settingsChange(
                this.panel.getBlockPos(), this.panel.getSettings()));
    }

    public List<Source> sources() {
        final Map<String, Source> map = new LinkedHashMap<>();
        final LinkedHashMap<String, SourceConfig> registry = this.panel.getSourceRegistry();

        final List<StatsDisplayPanelBE.DataChannel> all = allChannels();
        if (all != null) {
            for (final StatsDisplayPanelBE.DataChannel ch : all) {
                final String sid = sourceIdOf(ch.getId());
                map.computeIfAbsent(sid, Source::new).channels.add(ch);
            }
        }
        if (registry != null) {
            for (final Map.Entry<String, SourceConfig> e : registry.entrySet()) {
                final Source s = map.get(e.getKey());
                if (s != null) {
                    s.config = e.getValue();
                }
            }
        }
        final List<Source> out = new ArrayList<>(map.values());
        name(out);
        return out;
    }

    // naming needs the whole set, a single source can't know if its name is ambiguous
    static void name(List<Source> sources) {
        final List<SourceConfig> configs = new ArrayList<>(sources.size());
        for (final Source s : sources) {
            if (s.config != null) {
                configs.add(s.config);
            }
        }
        for (final Source s : sources) {
            s.name = s.config == null
                    ? pretty(s.id)
                    : SourceNames.label(s.config, false, SourceNames.ambiguous(configs, s.config));
        }
    }

    public String sourceNameOf(String channelId) {
        final String sid = sourceIdOf(channelId);
        for (final Source s : sources()) {
            if (s.id.equals(sid)) {
                return s.displayName();
            }
        }
        return pretty(sid);
    }

    public PanelModel model(int historyBudget) {
        return PanelModelCache.get(this.panel.getBlockPos())
                .build(this.panel, historyBudget, this.time);
    }

    public static String sourceIdOf(String channelId) {
        if (channelId != null && channelId.startsWith("src_")) {
            final int auto = channelId.indexOf("_auto_");
            if (auto > 4) {
                return channelId.substring(4, auto);
            }
        }
        return channelId == null ? "" : channelId;
    }

    public static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Source";
        }
        final String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) {
            return "Source";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static final class Source {
        public final String id;
        public final List<StatsDisplayPanelBE.DataChannel> channels = new ArrayList<>();
        public SourceConfig config;

        private String name = "";

        Source(String id) {
            this.id = id;
        }

        public String displayName() {
            return this.name.isEmpty() ? pretty(this.id) : this.name;
        }
    }

    public int visibleCount(Source source) {
        int n = 0;
        for (final StatsDisplayPanelBE.DataChannel ch : source.channels) {
            if (!isHidden(ch.getId())) {
                n++;
            }
        }
        return n;
    }
}
