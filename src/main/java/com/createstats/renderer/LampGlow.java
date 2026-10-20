package com.createstats.renderer;

public final class LampGlow {
    private LampGlow() {
    }

    public enum Family {
        INCANDESCENT,

        FLUORESCENT,

        HID,

        LED,
    }

    public record Box(float u0, float h0, float v0, float u1, float h1, float v1) {
    }

    public record Glow(Family family, Box[] boxes) {
    }

    private static final Glow CAGED_BULKHEAD_LAMP = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(5.20f, 3.20f, 5.20f, 10.80f, 7.20f, 10.80f),
    });

    private static final Glow HIGH_BAY_LAMP = new Glow(Family.HID, new Box[] {
            new Box(2.40f, 6.70f, 2.40f, 13.60f, 7.70f, 13.60f),
    });

    private static final Glow STRIP_LAMP = new Glow(Family.FLUORESCENT, new Box[] {
            new Box(2.20f, 2.40f, 6.20f, 13.80f, 4.10f, 9.80f),
    });

    private static final Glow DOME_LAMP = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(4.60f, 1.60f, 4.60f, 11.40f, 3.00f, 11.40f),
            new Box(5.60f, 3.00f, 5.60f, 10.40f, 4.20f, 10.40f),
            new Box(6.80f, 4.20f, 6.80f, 9.20f, 4.90f, 9.20f),
    });

    private static final Glow WALL_SCONCE = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(5.00f, 4.00f, 4.00f, 11.00f, 5.10f, 12.00f),
    });

    private static final Glow PENDANT_LAMP = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(3.40f, 11.90f, 3.40f, 12.60f, 12.70f, 12.60f),
    });

    private static final Glow FLOOD_LAMP = new Glow(Family.HID, new Box[] {
            new Box(4.30f, 7.20f, 3.90f, 11.70f, 8.50f, 12.10f),
    });

    private static final Glow VAPOUR_TIGHT_LAMP = new Glow(Family.FLUORESCENT, new Box[] {
            new Box(2.40f, 1.60f, 5.00f, 13.60f, 3.40f, 11.00f),
            new Box(2.60f, 3.40f, 5.40f, 13.40f, 4.40f, 10.60f),
            new Box(3.00f, 4.40f, 6.00f, 13.00f, 5.00f, 10.00f),
    });

    private static final Glow WELL_GLASS_LAMP = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(4.40f, 3.20f, 4.40f, 11.60f, 5.20f, 11.60f),
            new Box(5.00f, 5.20f, 5.00f, 11.00f, 7.00f, 11.00f),
            new Box(6.00f, 7.00f, 6.00f, 10.00f, 8.40f, 10.00f),
    });

    private static final Glow FILAMENT_BULB = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(7.00f, 3.20f, 7.00f, 9.00f, 4.00f, 9.00f),
            new Box(6.40f, 4.00f, 6.40f, 9.60f, 4.80f, 9.60f),
            new Box(5.80f, 4.80f, 5.80f, 10.20f, 5.60f, 10.20f),
            new Box(5.40f, 5.60f, 5.40f, 10.60f, 7.20f, 10.60f),
            new Box(5.90f, 7.20f, 5.90f, 10.10f, 8.00f, 10.10f),
            new Box(6.70f, 8.00f, 6.70f, 9.30f, 8.70f, 9.30f),
            new Box(7.40f, 8.70f, 7.40f, 8.60f, 9.10f, 8.60f),
    });

    private static final Glow EMERGENCY_LAMP = new Glow(Family.LED, new Box[] {
            new Box(4.30f, 7.00f, 6.70f, 6.50f, 7.70f, 9.30f),
            new Box(9.50f, 7.00f, 6.70f, 11.70f, 7.70f, 9.30f),
    });

    private static final Glow PANEL_LAMP = new Glow(Family.LED, new Box[] {
            new Box(2.10f, 0.60f, 2.10f, 7.70f, 1.50f, 7.70f),
            new Box(8.30f, 0.60f, 2.10f, 13.90f, 1.50f, 7.70f),
            new Box(2.10f, 0.60f, 8.30f, 7.70f, 1.50f, 13.90f),
            new Box(8.30f, 0.60f, 8.30f, 13.90f, 1.50f, 13.90f),
    });

    private static final Glow BANKERS_LAMP = new Glow(Family.INCANDESCENT, new Box[] {
            new Box(2.70f, 9.35f, 3.70f, 13.30f, 9.60f, 12.30f),
    });

    public static Glow of(net.minecraft.world.level.block.Block block) {
        if (block == com.createstats.registry.ModBlocks.CAGED_BULKHEAD_LAMP.get()) {
            return CAGED_BULKHEAD_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.HIGH_BAY_LAMP.get()) {
            return HIGH_BAY_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.STRIP_LAMP.get()) {
            return STRIP_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.DOME_LAMP.get()) {
            return DOME_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.WALL_SCONCE.get()) {
            return WALL_SCONCE;
        }
        if (block == com.createstats.registry.ModBlocks.PENDANT_LAMP.get()) {
            return PENDANT_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.FLOOD_LAMP.get()) {
            return FLOOD_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.VAPOUR_TIGHT_LAMP.get()) {
            return VAPOUR_TIGHT_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.WELL_GLASS_LAMP.get()) {
            return WELL_GLASS_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.FILAMENT_BULB.get()) {
            return FILAMENT_BULB;
        }
        if (block == com.createstats.registry.ModBlocks.EMERGENCY_LAMP.get()) {
            return EMERGENCY_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.PANEL_LAMP.get()) {
            return PANEL_LAMP;
        }
        if (block == com.createstats.registry.ModBlocks.BANKERS_LAMP.get()) {
            return BANKERS_LAMP;
        }
        return null;
    }
}
