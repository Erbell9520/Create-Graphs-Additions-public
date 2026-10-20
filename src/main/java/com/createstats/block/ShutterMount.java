package com.createstats.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

public enum ShutterMount implements StringRepresentable {
    CENTER("center", 0.0f),

    FRONT("front", -5.0f),

    BACK("back", 5.0f);

    private final String name;
    private final float depth;

    ShutterMount(String name, float depth) {
        this.name = name;
        this.depth = depth;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public float depth() {
        return this.depth;
    }

    public boolean flush() {
        return this != CENTER;
    }

    public ShutterMount next() {
        final ShutterMount[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static ShutterMount forClickedFace(@Nullable Direction face) {
        return face != null && face.getAxis().isHorizontal() ? BACK : CENTER;
    }
}
