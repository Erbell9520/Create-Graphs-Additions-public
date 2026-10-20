package com.createstats.book.specimen;

import com.createstats.book.BookTokens;
import com.createstats.client.wire.WireTensionScreen;
import com.createstats.client.wire.WireTensionWrenchPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WireTensionSpecimen {
    private WireTensionSpecimen() {
    }

    private static String language;

    public static void useGameText() {
        final String now = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        if (!now.equals(language)) {
            language = now;
            RingPlates.useText(key -> Language.getInstance().getOrDefault(key));
        }
    }

    public static ManualSpecimen create(String id) {
        useGameText();
        final int w = RingPlates.width(id);
        final int h = RingPlates.height(id);
        final BlockSpecimen world = new BlockSpecimen(RingPlates.world(id));
        if (RingPlates.WRENCH.equals(id)) {
            return WireTensionWrenchPanel.specimen(w, h, RingPlates.wrenchView(), RingPlates.wrenchAt(RingPlates.MEASURE),
                    RingPlates.aim(), g -> sky(g, world, w, h), () -> RingPlates.anatomy(id));
        }
        return WireTensionScreen.specimen(w, h, RingPlates.panels(), RingPlates.all(), RingPlates.hubSub(),
                RingPlates.hint(), RingPlates.resting(), (g, mouse) -> sky(g, world, w, h), () -> RingPlates.anatomy(id));
    }

    private static void sky(GuiGraphics g, BlockSpecimen world, int w, int h) {
        g.fill(0, 0, w, h, BookTokens.WORLD_SKY);
        world.renderSpecimen(g, -10_000, -10_000);
    }
}
