package com.createstats.book.specimen;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ManualSpecimen {
    int specimenWidth();

    int specimenHeight();

    void renderSpecimen(GuiGraphics g, int mouseX, int mouseY);

    SpecimenAnatomy anatomy();

    default boolean clipsItsDrawing() {
        return false;
    }
}
