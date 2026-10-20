package com.createstats.holomk3.scene;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoloScene {
    public float w = 3.2f;
    public float h = 2.2f;
    public float d = 2.0f;

    public final List<Lane> lanes = new ArrayList<>();

    public String title = "";
    public String subtitle = "";

    public float sweep = -1.0f;

    // client only, walk away and back and it boots again. correct
    public float boot = 1.0f;

    public boolean empty = true;

    public boolean stale;

    public int gridRows = 4;

    public int gridCols = 6;

    public String axisValueLo = "";
    public String axisValueHi = "";
    public String axisTimeSpan = "";
    public String axisDepth = "";

    public Lane focus;

    public Lane laneById(String id) {
        for (final Lane lane : this.lanes) {
            if (lane.id.equals(id)) {
                return lane;
            }
        }
        return null;
    }

    public static final class Lane {
        public final String id;
        public String label = "";
        public String unit = "";

        public String valueText = "";

        public int color;

        public float z;
        public float originX;
        public float originZ;
        public float yawDeg;

        public float planeW;
        public float planeH;

        public float[] xy = new float[0];
        public int count;

        public float lo;
        public float hi;

        public float min;
        public float max;
        public float mean;
        public float latest;

        public float latestY;

        public boolean focused;
        public boolean stale;

        public boolean flat;

        public Lane(String id) {
            this.id = id == null ? "" : id;
        }

        public float[] head() {
            if (this.count <= 0) {
                return null;
            }
            return new float[]{this.xy[(this.count - 1) * 2], this.xy[(this.count - 1) * 2 + 1]};
        }
    }
}
