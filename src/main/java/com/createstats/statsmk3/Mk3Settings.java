package com.createstats.statsmk3;

import com.createstats.statsmk3.layout.GridSpec;
import com.createstats.statsmk3.layout.LayoutPresets;
import com.createstats.statsmk3.layout.Mk3Layout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class Mk3Settings {
    private static final String TAG = "Mk3";

    public String template = "SPLIT";

    public String instrument = "area";

    public String autoTiny = "numeric";

    public String autoWide = "spark";

    public String autoSquare = "arc";

    public final Mk3Layout layout = new Mk3Layout();

    public int ground = 0xFF05070A;
    public int accent = 0xFFFF5C90;
    public boolean grid = true;
    public boolean glass = true;

    public float glow = 0.62f;

    public float density = 1.0f;

    public float faceScale = DEFAULT_FACE_SCALE;

    public static final float[] FACE_SCALES = {0.3f, 0.5f, 0.7f, 1.0f};
    public static final String[] FACE_SCALE_LABELS = {"30%", "50%", "70%", "100%"};
    public static final float DEFAULT_FACE_SCALE = 0.5f;

    public static final float MIN_FACE_SCALE = 0.25f;
    public static final float MAX_FACE_SCALE = 1.5f;

    public static int faceScaleIndex(float scale) {
        int best = 1;
        float bestDelta = Float.MAX_VALUE;
        for (int i = 0; i < FACE_SCALES.length; i++) {
            final float delta = Math.abs(FACE_SCALES[i] - scale);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    public String[] lampChannels = new String[6];

    public enum FaceMode {
        INSTRUMENTS,

        TRAIN_BOARD,

        TRAIN_MAP,

        TRAIN_DIAGRAM,

        TRAIN_ROUTE,

        TRAIN_FOCUS,

        TRAIN_STATUS,

        STORAGE,

        TOPOLOGY,

        REFINED_STORAGE,

        EFFICIENCY,

        AERO_DECK,

        AERO_MAP,

        AERO_BUDGET,

        DRIVE_DASH,

        DRIVE_GARAGE,

        DRIVE_PIT,

        DRIVE_TRAIN,

        AERO_TELEGRAPH,

        AERO_VOYAGE,

        AERO_SCOPE,

        SURVEY_SCAN,

        SURVEY_FINDS,

        SURVEY_DEPTH,

        SURVEY_ECHO;

        public static FaceMode parse(String raw) {
            if (raw != null) {
                for (final FaceMode m : values()) {
                    if (m.name().equals(raw)) {
                        return m;
                    }
                }
            }
            return INSTRUMENTS;
        }

        public boolean isTrain() {
            return this == TRAIN_BOARD || this == TRAIN_MAP || this == TRAIN_DIAGRAM
                    || this == TRAIN_ROUTE || this == TRAIN_FOCUS || this == TRAIN_STATUS;
        }

        public boolean isAero() {
            return this == AERO_DECK || this == AERO_MAP || this == AERO_BUDGET || this == AERO_TELEGRAPH
                    || this == AERO_VOYAGE || this == AERO_SCOPE;
        }

        public boolean isDrive() {
            return this == DRIVE_DASH || this == DRIVE_GARAGE || this == DRIVE_PIT || this == DRIVE_TRAIN;
        }

        public boolean isSurvey() {
            return this == SURVEY_SCAN || this == SURVEY_FINDS || this == SURVEY_DEPTH || this == SURVEY_ECHO;
        }
    }

    public enum MapFocus {
        NETWORK,

        LINE,

        TRAIN,

        STATION,

        NEAR;

        public static MapFocus parse(String raw) {
            if (raw != null) {
                for (final MapFocus m : values()) {
                    if (m.name().equals(raw)) {
                        return m;
                    }
                }
            }
            return NETWORK;
        }
    }

    public FaceMode faceMode = FaceMode.INSTRUMENTS;

    public int driveLook;

    public final SurveySettings survey = new SurveySettings();

    public String boardStation = "";

    public String focusTrain = "";

    public MapFocus mapFocus = MapFocus.NETWORK;

    public int mapRadius = 256;

    public static final int MIN_MAP_RADIUS = 32;
    public static final int MAX_MAP_RADIUS = 4096;

    public int faceFlags = FACE_ALL;

    public static final int FACE_SPEED = 1;

    public static final int FACE_CALLING = 1 << 1;

    public static final int FACE_CAUSE = 1 << 2;

    public static final int FACE_CARGO = 1 << 3;

    public static final int FACE_LABELS = 1 << 4;

    public static final int FACE_TRACK = 1 << 5;

    public static final int FACE_PLANNED = 1 << 6;

    public static final int FACE_ALL = FACE_SPEED | FACE_CALLING | FACE_CAUSE | FACE_CARGO
            | FACE_LABELS | FACE_TRACK | FACE_PLANNED;

    public boolean faceFlag(int bit) {
        return (this.faceFlags & bit) != 0;
    }

    public void toggleFaceFlag(int bit) {
        this.faceFlags ^= bit;
    }

    public String diagramLine = "";

    public String storageKind = "";

    public String rsFocusItem = "";

    public int rsWindow = 1;

    public int timeRangeSeconds;

    private final java.util.LinkedHashMap<String, YAxisRange> liveYRanges =
            new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, YAxisRange> alertYRanges =
            new java.util.LinkedHashMap<>();

    private static final int MAX_PAGE_Y_RANGES = 128;
    private static final int MAX_CHANNEL_ID_LENGTH = 256;

    public static final int[] TIME_RANGES = {60, 300, 900, 0};
    public static final String[] TIME_RANGE_LABELS = {"1m", "5m", "15m", "All"};

    public static int timeRangeIndex(int seconds) {
        for (int i = 0; i < TIME_RANGES.length; i++) {
            if (TIME_RANGES[i] == seconds) {
                return i;
            }
        }
        return TIME_RANGES.length - 1;
    }

    public Mk3Settings() {
        java.util.Arrays.fill(this.lampChannels, "");
    }

    public YAxisRange liveYRange(String channelId) {
        return pageRange(this.liveYRanges, channelId);
    }

    public YAxisRange alertYRange(String channelId) {
        return pageRange(this.alertYRanges, channelId);
    }

    public void setLiveYRange(String channelId, YAxisRange range) {
        setPageRange(this.liveYRanges, channelId, range);
    }

    public void setAlertYRange(String channelId, YAxisRange range) {
        setPageRange(this.alertYRanges, channelId, range);
    }

    public java.util.Map<String, YAxisRange> liveYRanges() {
        return java.util.Collections.unmodifiableMap(this.liveYRanges);
    }

    public java.util.Map<String, YAxisRange> alertYRanges() {
        return java.util.Collections.unmodifiableMap(this.alertYRanges);
    }

    private static YAxisRange pageRange(java.util.Map<String, YAxisRange> ranges, String id) {
        if (id == null || id.isEmpty()) {
            return YAxisRange.auto();
        }
        return ranges.getOrDefault(id, YAxisRange.auto());
    }

    private static void setPageRange(java.util.LinkedHashMap<String, YAxisRange> ranges,
                                     String channelId, YAxisRange range) {
        if (channelId == null || channelId.isEmpty() || channelId.length() > MAX_CHANNEL_ID_LENGTH) {
            return;
        }
        final YAxisRange value = range == null ? YAxisRange.auto() : range;
        if (!value.isFixed()) {
            ranges.remove(channelId);
            return;
        }
        if (!ranges.containsKey(channelId) && ranges.size() >= MAX_PAGE_Y_RANGES) {
            ranges.remove(ranges.keySet().iterator().next());
        }
        ranges.put(channelId, value);
    }

    public void save(CompoundTag parent) {
        final CompoundTag t = new CompoundTag();
        t.putString("Template", this.template);
        t.putString("Instrument", this.instrument);
        t.putString("AutoTiny", this.autoTiny);
        t.putString("AutoWide", this.autoWide);
        t.putString("AutoSquare", this.autoSquare);
        t.putInt("Ground", this.ground);
        t.putInt("Accent", this.accent);
        t.putBoolean("Grid", this.grid);
        t.putBoolean("Glass", this.glass);
        t.putFloat("Glow", this.glow);
        t.putFloat("Density", this.density);
        t.putFloat("FaceScale", this.faceScale);
        for (int i = 0; i < this.lampChannels.length; i++) {
            t.putString("Lamp" + i, this.lampChannels[i] == null ? "" : this.lampChannels[i]);
        }
        t.putInt("TimeRange", this.timeRangeSeconds);
        t.putString("FaceMode", this.faceMode.name());
        t.putInt("DriveLook", this.driveLook);
        t.putString("BoardStation", this.boardStation);
        t.putString("DiagramLine", this.diagramLine);
        t.putString("FocusTrain", this.focusTrain);
        t.putString("MapFocus", this.mapFocus.name());
        t.putInt("MapRadius", this.mapRadius);
        t.putInt("FaceFlags", this.faceFlags);
        t.putString("StorageKind", this.storageKind);
        t.putString("RsFocusItem", this.rsFocusItem);
        t.putInt("RsWindow", this.rsWindow);
        this.survey.save(t);
        savePageRanges(t, "LiveY", this.liveYRanges);
        savePageRanges(t, "AlertY", this.alertYRanges);
        this.layout.save(t);
        parent.put(TAG, t);
    }

    public void load(CompoundTag parent) {
        if (!parent.contains(TAG)) {
            return;
        }
        final CompoundTag t = parent.getCompound(TAG);
        if (t.contains("Template")) {
            this.template = t.getString("Template");
        }
        if (t.contains("Instrument")) {
            this.instrument = t.getString("Instrument");
        }

        if (t.contains("AutoTiny")) {
            this.autoTiny = t.getString("AutoTiny");
        }
        if (t.contains("AutoWide")) {
            this.autoWide = t.getString("AutoWide");
        }
        if (t.contains("AutoSquare")) {
            this.autoSquare = t.getString("AutoSquare");
        }

        if (t.contains("Ground")) {
            this.ground = opaque(t.getInt("Ground"));
        }
        if (t.contains("Accent")) {
            this.accent = opaque(t.getInt("Accent"));
        }
        if (t.contains("Grid")) {
            this.grid = t.getBoolean("Grid");
        }
        if (t.contains("Glass")) {
            this.glass = t.getBoolean("Glass");
        }
        if (t.contains("Glow")) {
            this.glow = clamp01(t.getFloat("Glow"));
        }
        if (t.contains("Density")) {
            final float d = t.getFloat("Density");
            this.density = Float.isNaN(d) ? 1.0f : Math.max(0.6f, Math.min(1.5f, d));
        }

        if (t.contains("FaceScale")) {
            final float s = t.getFloat("FaceScale");
            this.faceScale = Float.isNaN(s) ? DEFAULT_FACE_SCALE
                    : Math.max(MIN_FACE_SCALE, Math.min(MAX_FACE_SCALE, s));
        }
        for (int i = 0; i < this.lampChannels.length; i++) {
            this.lampChannels[i] = t.contains("Lamp" + i) ? t.getString("Lamp" + i) : "";
        }
        if (t.contains("TimeRange")) {
            this.timeRangeSeconds = Math.max(0, Math.min(86_400, t.getInt("TimeRange")));
        }
        if (t.contains("FaceMode")) {
            this.faceMode = FaceMode.parse(t.getString("FaceMode"));
        }
        if (t.contains("DriveLook")) {
            this.driveLook = Math.max(0, Math.min(1, t.getInt("DriveLook")));
        }
        if (t.contains("BoardStation")) {
            final String name = t.getString("BoardStation");
            this.boardStation = name.length() > 256 ? name.substring(0, 256) : name;
        }
        if (t.contains("DiagramLine")) {
            final String line = t.getString("DiagramLine");
            this.diagramLine = line.length() > 96 ? line.substring(0, 96) : line;
        }
        if (t.contains("FocusTrain")) {
            final String train = t.getString("FocusTrain");
            this.focusTrain = train.length() > 96 ? train.substring(0, 96) : train;
        }
        if (t.contains("MapFocus")) {
            this.mapFocus = MapFocus.parse(t.getString("MapFocus"));
        }
        if (t.contains("MapRadius")) {
            this.mapRadius = Math.max(MIN_MAP_RADIUS,
                    Math.min(MAX_MAP_RADIUS, t.getInt("MapRadius")));
        }
        if (t.contains("FaceFlags")) {
            this.faceFlags = t.getInt("FaceFlags") & FACE_ALL;
        }
        if (t.contains("StorageKind")) {
            final String kind = t.getString("StorageKind");
            this.storageKind = kind.length() > 16 ? kind.substring(0, 16) : kind;
        }
        if (t.contains("RsFocusItem")) {
            final String item = t.getString("RsFocusItem");
            this.rsFocusItem = item.length() > 128 ? "" : item;
        }
        if (t.contains("RsWindow")) {
            this.rsWindow = Math.floorMod(t.getInt("RsWindow"),
                    com.createstats.data.WindowedTrend.Window.values().length);
        }

        this.survey.load(t);
        loadPageRanges(t, "LiveY", this.liveYRanges);
        loadPageRanges(t, "AlertY", this.alertYRanges);

        this.layout.load(t);
    }

    private static void savePageRanges(CompoundTag parent, String key,
                                       java.util.Map<String, YAxisRange> ranges) {
        final ListTag list = new ListTag();
        for (final java.util.Map.Entry<String, YAxisRange> entry : ranges.entrySet()) {
            if (!entry.getValue().isFixed()) {
                continue;
            }
            final CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey());
            row.putFloat("Min", entry.getValue().min());
            row.putFloat("Max", entry.getValue().max());
            list.add(row);
        }
        if (!list.isEmpty()) {
            parent.put(key, list);
        }
    }

    private static void loadPageRanges(CompoundTag parent, String key,
                                       java.util.LinkedHashMap<String, YAxisRange> ranges) {
        ranges.clear();
        if (!parent.contains(key)) {
            return;
        }
        final ListTag list = parent.getList(key, 10);
        for (int i = 0; i < list.size() && ranges.size() < MAX_PAGE_Y_RANGES; i++) {
            final CompoundTag row = list.getCompound(i);
            final String id = row.getString("Id");
            if (id.isEmpty() || id.length() > MAX_CHANNEL_ID_LENGTH) {
                continue;
            }
            final YAxisRange range = YAxisRange.fixed(row.getFloat("Min"), row.getFloat("Max"));
            if (range.isFixed()) {
                ranges.put(id, range);
            }
        }
    }

    public boolean ensureLayout(int cols, int rows, int channelCount) {
        if (this.layout.isEmpty()) {
            final Mk3Layout seeded = this.template == null || this.template.isEmpty()
                    ? LayoutPresets.defaultFor(cols, rows, channelCount)
                    : LayoutPresets.fromTemplate(this.template, cols, rows, channelCount);
            adopt(seeded);
            return true;
        }
        return this.layout.fitTo(cols, rows);
    }

    public void adopt(Mk3Layout other) {
        if (other == null) {
            return;
        }
        this.layout.clear();
        this.layout.fitTo(other.cols(), other.rows());
        for (final com.createstats.statsmk3.layout.Tile tile : other.tiles()) {
            this.layout.tiles().add(tile.copy());
        }
        this.layout.presetId = other.presetId;
        this.layout.normalise();
    }

    public static int colsFor(int multiblockWidth) {
        return GridSpec.colsFor(multiblockWidth);
    }

    public static int rowsFor(int multiblockHeight) {
        return GridSpec.rowsFor(multiblockHeight);
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.0f : v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }
}
