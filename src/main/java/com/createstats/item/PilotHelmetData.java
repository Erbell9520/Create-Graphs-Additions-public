package com.createstats.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class PilotHelmetData {

    public enum Mode {
        OTOMOBIL("Otomobil"),
        TANK("Tank"),
        FIGHTER("Fighter"),
        COMMERCIAL("Commercial");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    /** [1] */
    public enum NightVisionMode {
        CLASSIC("Classic"),
        TACTICAL("Tactical NVG");

        private final String displayName;

        NightVisionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    public static class LinkedNode {
        @Nullable
        private ResourceLocation dimensionId;
        @Nullable
        private BlockPos targetPos;
        private String targetName = "";

        public boolean isLinked() {
            return this.dimensionId != null && this.targetPos != null;
        }

        public void clear() {
            this.dimensionId = null;
            this.targetPos = null;
            this.targetName = "";
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (this.dimensionId != null) {
                tag.putString("Dimension", this.dimensionId.toString());
            }
            if (this.targetPos != null) {
                tag.putLong("TargetPos", this.targetPos.asLong());
            }
            tag.putString("TargetName", this.targetName);
            return tag;
        }

        public static LinkedNode load(CompoundTag tag) {
            LinkedNode node = new LinkedNode();
            if (tag == null || tag.isEmpty()) {
                return node;
            }
            if (tag.contains("Dimension")) {
                node.dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
            }
            if (tag.contains("TargetPos")) {
                node.targetPos = BlockPos.of(tag.getLong("TargetPos"));
            }
            node.targetName = tag.getString("TargetName");
            if (!node.isLinked()) {
                node.clear();
            }
            return node;
        }

        @Nullable
        public ResourceLocation getDimensionId() {
            return this.dimensionId;
        }

        public void setDimensionId(@Nullable ResourceLocation dimensionId) {
            this.dimensionId = dimensionId;
        }

        @Nullable
        public BlockPos getTargetPos() {
            return this.targetPos;
        }

        public void setTargetPos(@Nullable BlockPos targetPos) {
            this.targetPos = targetPos;
        }

        public String getTargetName() {
            return this.targetName;
        }

        public void setTargetName(String targetName) {
            this.targetName = targetName != null ? targetName : "";
        }
    }

    private Mode mode = Mode.FIGHTER;
    private boolean autoIfSeated = true;
    private boolean visorDeployed = false;
    private int vignetteStrength = 5;
    private float hudScaleFactor = 1.0f;
    private boolean nightVisionEnabled = false;
    private NightVisionMode nightVisionMode = NightVisionMode.CLASSIC;
    /** [2] */
    private int helmetEnergyFE = 0;
    private final LinkedNode fccLink = new LinkedNode();
    private final LinkedNode battleBridgeLink = new LinkedNode();
    private final LinkedNode statsDisplayLink = new LinkedNode();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", this.mode.name());
        tag.putBoolean("AutoIfSeated", this.autoIfSeated);
        tag.putBoolean("VisorDeployed", this.visorDeployed);
        tag.putInt("VignetteStrength", this.vignetteStrength);
        tag.putFloat("HudScaleFactor", this.hudScaleFactor);
        tag.putBoolean("NightVisionEnabled", this.nightVisionEnabled);
        tag.putString("NightVisionMode", this.nightVisionMode.name());
        tag.putInt("HelmetEnergyFE", this.helmetEnergyFE);
        tag.put("FccLink", this.fccLink.save());
        tag.put("BattleBridgeLink", this.battleBridgeLink.save());
        tag.put("StatsDisplayLink", this.statsDisplayLink.save());
        return tag;
    }

    public static PilotHelmetData load(CompoundTag tag) {
        PilotHelmetData data = new PilotHelmetData();
        if (tag == null || tag.isEmpty()) {
            return data;
        }
        try {
            data.mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            data.mode = Mode.FIGHTER;
        }
        data.autoIfSeated = !tag.contains("AutoIfSeated") || tag.getBoolean("AutoIfSeated");
        data.visorDeployed = tag.getBoolean("VisorDeployed");
        if (tag.contains("VignetteStrength")) {
            data.vignetteStrength = clampVignette(tag.getInt("VignetteStrength"));
        } else if (tag.contains("VignetteEnabled")) {
            // Back-compat: previous bool flag mapped to 5% (the prior baked-in level).
            data.vignetteStrength = tag.getBoolean("VignetteEnabled") ? 5 : 0;
        }
        if (tag.contains("HudScaleFactor")) {
            // Stored as float; back-compat tolerates the older int-typed key as well.
            data.hudScaleFactor = clampHudScale(tag.getFloat("HudScaleFactor"));
        } else if (tag.contains("HudScalePercent")) {
            // Back-compat: prior slider value (0..100, 20% == 1x).
            int pct = tag.getInt("HudScalePercent");
            data.hudScaleFactor = clampHudScale(pct / 20.0f);
        }
        if (tag.contains("NightVisionEnabled")) {
            data.nightVisionEnabled = tag.getBoolean("NightVisionEnabled");
        }
        if (tag.contains("NightVisionMode")) {
            try {
                data.nightVisionMode = NightVisionMode.valueOf(tag.getString("NightVisionMode"));
            } catch (IllegalArgumentException ignored) {
                data.nightVisionMode = NightVisionMode.CLASSIC;
            }
        }
        if (tag.contains("HelmetEnergyFE")) {
            data.helmetEnergyFE = Math.max(0, tag.getInt("HelmetEnergyFE"));
        }
        if (tag.contains("FccLink")) {
            copyNode(data.fccLink, LinkedNode.load(tag.getCompound("FccLink")));
        }
        if (tag.contains("BattleBridgeLink")) {
            copyNode(data.battleBridgeLink, LinkedNode.load(tag.getCompound("BattleBridgeLink")));
        }
        if (tag.contains("StatsDisplayLink")) {
            copyNode(data.statsDisplayLink, LinkedNode.load(tag.getCompound("StatsDisplayLink")));
        }
        return data;
    }

    private static void copyNode(LinkedNode target, LinkedNode source) {
        target.setDimensionId(source.getDimensionId());
        target.setTargetPos(source.getTargetPos());
        target.setTargetName(source.getTargetName());
    }

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.FIGHTER;
    }

    public boolean isAutoIfSeated() {
        return this.autoIfSeated;
    }

    public void setAutoIfSeated(boolean autoIfSeated) {
        this.autoIfSeated = autoIfSeated;
    }

    public boolean isVisorDeployed() {
        return this.visorDeployed;
    }

    public void setVisorDeployed(boolean visorDeployed) {
        this.visorDeployed = visorDeployed;
    }

    public int getVignetteStrength() {
        return this.vignetteStrength;
    }

    public void setVignetteStrength(int vignetteStrength) {
        this.vignetteStrength = clampVignette(vignetteStrength);
    }

    private static int clampVignette(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public float getHudScaleFactor() {
        return this.hudScaleFactor;
    }

    public void setHudScaleFactor(float hudScaleFactor) {
        this.hudScaleFactor = clampHudScale(hudScaleFactor);
    }

    private static float clampHudScale(float value) {
        return Math.max(1.0f, Math.min(3.0f, value));
    }

    public LinkedNode getFccLink() {
        return this.fccLink;
    }

    public LinkedNode getBattleBridgeLink() {
        return this.battleBridgeLink;
    }

    public LinkedNode getStatsDisplayLink() {
        return this.statsDisplayLink;
    }

    public boolean isNightVisionEnabled() {
        return this.nightVisionEnabled;
    }

    public void setNightVisionEnabled(boolean enabled) {
        this.nightVisionEnabled = enabled;
    }

    public NightVisionMode getNightVisionMode() {
        return this.nightVisionMode;
    }

    public void setNightVisionMode(NightVisionMode mode) {
        this.nightVisionMode = mode != null ? mode : NightVisionMode.CLASSIC;
    }

    /**
     * Bookkeeping value for the not-yet-wired Forge-Energy slot. See
     * {@link #helmetEnergyFE} field comment. Currently never decremented.
     */
    public int getHelmetEnergyFE() {
        return this.helmetEnergyFE;
    }

    public void setHelmetEnergyFE(int value) {
        this.helmetEnergyFE = Math.max(0, value);
    }
}
