package com.createstats.power;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface Adjustable {
    int getMode();

    void setMode(int mode);

    List<Component> modeLabels();

    default Component modeLabel() {
        final List<Component> labels = modeLabels();
        final int index = getMode();
        return index >= 0 && index < labels.size() ? labels.get(index) : Component.empty();
    }
}
