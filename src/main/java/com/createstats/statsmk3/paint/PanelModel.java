package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PanelModel {
    public String title = "";

    public String status = "";
    public int statusColor;

    public String timeRange = "";

    public Mk3Layout layout;

    public String instrumentId = "area";
    public String autoTiny = "numeric";
    public String autoWide = "spark";
    public String autoSquare = "arc";

    public final List<ChannelView> channels = new ArrayList<>();

    public final List<Slot> slots = new ArrayList<>();

    public PanelStyle style = new PanelStyle();

    public com.createstats.statsmk3.Mk3Settings.FaceMode faceMode =
            com.createstats.statsmk3.Mk3Settings.FaceMode.INSTRUMENTS;

    public String boardStation = "";

    public String diagramLine = "";

    public String focusTrain = "";

    public com.createstats.statsmk3.Mk3Settings.MapFocus mapFocus =
            com.createstats.statsmk3.Mk3Settings.MapFocus.NETWORK;

    public int mapRadius = 256;

    public int faceFlags = com.createstats.statsmk3.Mk3Settings.FACE_ALL;

    public double faceX = Double.NaN;
    public double faceZ = Double.NaN;

    public boolean faceFlag(int bit) {
        return (this.faceFlags & bit) != 0;
    }

    public boolean hasPosition() {
        return !Double.isNaN(this.faceX) && !Double.isNaN(this.faceZ);
    }

    public final List<com.createstats.statsmk3.page.StorageReadings.Store> storage =
            new ArrayList<>();

    public String storageKind = "";

    public final List<com.createstats.statsmk3.page.TopoArt.Reading> topology = new ArrayList<>();

    public final List<com.createstats.statsmk3.page.RsReadings.Board> refinedStorage =
            new ArrayList<>();

    public String rsFocusItem = "";

    public int rsWindow = 1;

    public com.createstats.efficiency.EfficiencyScore efficiency;

    public com.createstats.efficiency.EfficiencyTrace efficiencyTrace;

    public float[] efficiencyTrend;

    public com.createstats.aerial.AerialFrame aero;

    public boolean aeroLinked;

    public com.createstats.ground.GroundFrame drive;

    public boolean driveLinked;

    public int driveLook;

    public com.createstats.statsmk3.page.SurveyArt.Model survey;

    public int[] lamps = new int[6];

    public float time;

    public boolean helperFiring;

    public ChannelView channelAt(int index) {
        if (this.channels.isEmpty()) {
            return null;
        }
        return this.channels.get(index % this.channels.size());
    }

    public ChannelView channelById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (final ChannelView ch : this.channels) {
            if (ch.id.equals(id)) {
                return ch;
            }
        }
        return null;
    }

    public boolean hasData() {
        return !this.channels.isEmpty();
    }

    // lostSignal draws dark and says so. never quietly swap in another channel
    public static final class Slot {
        public final Tile tile;
        public final ChannelView channel;
        public final boolean lostSignal;

        public Slot(Tile tile, ChannelView channel, boolean lostSignal) {
            this.tile = tile;
            this.channel = channel;
            this.lostSignal = lostSignal;
        }
    }
}
