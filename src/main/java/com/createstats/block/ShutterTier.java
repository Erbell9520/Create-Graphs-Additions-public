package com.createstats.block;

import com.createstats.sound.DoorClip;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Locale;

public enum ShutterTier {
    WINDOW("window_shutter", 12, 6, 2.0f, 6.0f, DoorClip.LATCH, 0.9f, 1.15f, MapColor.TERRACOTTA_WHITE),

    BLAST("blast_shutter", 20, 10, 4.0f, 1200.0f, DoorClip.SEAL_HISS, 1.0f, 0.85f, MapColor.COLOR_GRAY);

    private static final ShutterTier[] ORDER = values();

    private final String blockId;
    private final int travelTicks;
    private final int perBlockTicks;
    private final float hardness;
    private final float resistance;
    private final DoorClip depart;
    private final float gain;
    private final float pitch;
    private final MapColor mapColor;

    ShutterTier(String blockId, int travelTicks, int perBlockTicks, float hardness, float resistance,
                DoorClip depart, float gain, float pitch, MapColor mapColor) {
        this.blockId = blockId;
        this.travelTicks = travelTicks;
        this.perBlockTicks = perBlockTicks;
        this.hardness = hardness;
        this.resistance = resistance;
        this.depart = depart;
        this.gain = gain;
        this.pitch = pitch;
        this.mapColor = mapColor;
    }

    public String blockId() {
        return this.blockId;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int travelTicks() {
        return this.travelTicks;
    }

    public int perBlockTicks() {
        return this.perBlockTicks;
    }

    public int travelTicksFor(int height) {
        final int clamped = Math.max(BlastShutterBlock.MIN_HEIGHT,
                Math.min(BlastShutterBlock.MAX_HEIGHT, height));
        return this.travelTicks + this.perBlockTicks * (clamped - BlastShutterBlock.MIN_HEIGHT);
    }

    public DoorClip depart() {
        return this.depart;
    }

    public float gain() {
        return this.gain;
    }

    public float pitch() {
        return this.pitch;
    }

    // noOcclusion or the wall around the hole gets culled
    public BlockBehaviour.Properties apply(BlockBehaviour.Properties properties) {
        return properties
                .mapColor(this.mapColor)
                .strength(this.hardness, this.resistance)
                .sound(this == BLAST ? SoundType.NETHERITE_BLOCK : SoundType.COPPER)
                .noOcclusion()
                .requiresCorrectToolForDrops();
    }

    public float integrity() {
        return -1.0f;
    }

    public static ShutterTier[] all() {
        return ORDER;
    }

    public static ShutterTier byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : WINDOW;
    }

    public static ShutterTier byName(String name) {
        for (ShutterTier tier : ORDER) {
            if (tier.name().equalsIgnoreCase(name) || tier.blockId.equals(name)) {
                return tier;
            }
        }
        return WINDOW;
    }
}
