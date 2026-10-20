package com.createstats.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum DoorMaterial {
    IRON(MapColor.METAL, SoundType.METAL, 3.0f, 5.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.STACK_SLIDE, DoorOpening.PLUG_RETRACT),

    REINFORCED(MapColor.COLOR_GRAY, SoundType.METAL, 5.0f, 12.0f,
            DoorOpening.SLIDE, DoorOpening.STACK_SLIDE, DoorOpening.PLUG_RETRACT),

    QUARTZ(MapColor.QUARTZ, SoundType.METAL, 3.0f, 5.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.FOLD_RETRACT, DoorOpening.STACK_SLIDE),

    OAK(MapColor.WOOD),
    SPRUCE(MapColor.PODZOL),
    BIRCH(MapColor.SAND),
    JUNGLE(MapColor.DIRT),
    ACACIA(MapColor.COLOR_ORANGE),
    DARK_OAK(MapColor.COLOR_BROWN),

    ANDESITE(MapColor.STONE, SoundType.STONE, 2.5f, 6.0f,
            DoorOpening.SWING, DoorOpening.SLIDE),

    BRASS(MapColor.GOLD, SoundType.METAL, 3.0f, 6.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.STACK_SLIDE,
            DoorOpening.FOLD_RETRACT, DoorOpening.PLUG_RETRACT),

    COPPER(MapColor.COLOR_ORANGE, SoundType.COPPER, 3.0f, 6.0f,
            DoorOpening.SLIDE, DoorOpening.PLUG_RETRACT),

    ZINC(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL, 3.0f, 6.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.FOLD_RETRACT),

    INDUSTRIAL_IRON(MapColor.COLOR_GRAY, SoundType.METAL, 4.0f, 9.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.ROLL_UP),

    ONYX(MapColor.COLOR_BLACK, SoundType.METAL, 3.5f, 7.0f,
            DoorOpening.SLIDE, DoorOpening.STACK_SLIDE, DoorOpening.FOLD_RETRACT),

    SALON(MapColor.COLOR_BROWN, SoundType.METAL, 3.0f, 6.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.PLUG_RETRACT),

    CRYSTAL(MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS, 2.5f, 5.0f,
            DoorOpening.SLIDE, DoorOpening.STACK_SLIDE, DoorOpening.PLUG_RETRACT),

    ATRIUM(MapColor.COLOR_BROWN, SoundType.METAL, 4.0f, 10.0f,
            DoorOpening.SWING, DoorOpening.SLIDE, DoorOpening.FOLD_PARK, DoorOpening.CONCERTINA);

    private static final class Wood {
        static final Set<DoorOpening> OPENINGS = Collections.unmodifiableSet(EnumSet.of(
                DoorOpening.SWING, DoorOpening.SLIDE,
                DoorOpening.FOLD_PARK, DoorOpening.FOLD_RETRACT, DoorOpening.CONCERTINA));

        private Wood() {
        }
    }

    private static final DoorMaterial[] ORDER = values();

    private final String key;
    private final MapColor mapColor;
    private final SoundType sound;
    private final float hardness;
    private final float resistance;
    private final Set<DoorOpening> openings;

    DoorMaterial(MapColor mapColor, SoundType sound, float hardness, float resistance,
                 DoorOpening... openings) {
        this.key = name().toLowerCase(Locale.ROOT);
        this.mapColor = mapColor;
        this.sound = sound;
        this.hardness = hardness;
        this.resistance = resistance;
        final EnumSet<DoorOpening> set = EnumSet.noneOf(DoorOpening.class);
        set.addAll(java.util.Arrays.asList(openings));
        this.openings = Collections.unmodifiableSet(set);
    }

    DoorMaterial(MapColor mapColor) {
        this.key = name().toLowerCase(Locale.ROOT);
        this.mapColor = mapColor;
        this.sound = SoundType.WOOD;
        this.hardness = 3.0f;
        this.resistance = 5.0f;
        this.openings = Wood.OPENINGS;
    }

    public String key() {
        return this.key;
    }

    public String blockId() {
        return this.key + "_powered_door";
    }

    public boolean isWood() {
        return this.sound == SoundType.WOOD;
    }

    public Set<DoorOpening> openings() {
        return this.openings;
    }

    public float hardness() {
        return this.hardness;
    }

    public SoundType soundType() {
        return this.sound;
    }

    public DoorOpening defaultOpening() {
        for (DoorOpening style : DoorOpening.values()) {
            if (this.openings.contains(style)) {
                return style;
            }
        }
        return DoorOpening.SWING;
    }

    public BlockBehaviour.Properties apply(BlockBehaviour.Properties properties) {
        return properties.mapColor(this.mapColor).sound(this.sound)
                .strength(this.hardness, this.resistance).noOcclusion();
    }

    public static DoorMaterial byName(String name) {
        for (DoorMaterial material : ORDER) {
            if (material.key.equals(name) || material.name().equals(name)) {
                return material;
            }
        }
        return IRON;
    }

    public static DoorMaterial[] all() {
        return ORDER;
    }
}
