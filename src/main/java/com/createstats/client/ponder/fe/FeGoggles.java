package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.Labels;
import com.createstats.power.ConnectorReadout;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import com.createstats.power.WireTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

public final class FeGoggles {
    static final int FADE = 8;

    private FeGoggles() {
    }

    static final class Card extends FeOverlay {
        Labels.Anchor anchor;
        List<Component> lines = List.of();
        float level;
        float prevLevel;

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float fade) {
            final float f = Mth.lerp(pt, prevLevel, level) * fade;
            if (f < 0.03f || lines.isEmpty() || anchor == null) return;
            final Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            final PoseStack ps = graphics.pose();
            ps.pushPose();

            ps.translate(Math.pow(1.0 - f, 3.0) * 8.0, 0.0, 0.0);
            final Color bg = BoxElement.COLOR_VANILLA_BACKGROUND.copy().scaleAlpha(0.75f * f);
            final Color top = BoxElement.COLOR_VANILLA_BORDER.getFirst().copy().scaleAlpha(f);
            final Color bot = BoxElement.COLOR_VANILLA_BORDER.getSecond().copy().scaleAlpha(f);
            final int x = Math.round(at.x) + 12;
            final int y = Math.round(at.y) - 12;
            ps.translate(0, 0, 400);
            GuiGameElement.of(AllItems.GOGGLES.asStack()).at(x + 10, y - 16, 450).render(graphics);
            RemovedGuiUtils.drawHoveringText(graphics, lines, x, y, screen.width, screen.height, -1, bg.getRGB(),
                    top.getRGB(), bot.getRGB(), screen.getFontRenderer());
            ps.popPose();
        }
    }

    public static List<Component> connector(ConnectorTier tier, int base, int flow, List<WireTier> spans) {
        final List<Component> out = new ArrayList<>();
        new ConnectorReadout(tier.getDisplayName(), tier, base, spans, 0, Math.max(0, flow), flow, true,
                com.createstats.power.FeGridSolver.NO_CEILING, 0, 0, 0,
                com.createstats.blockentity.PowerConnectorBE.DEFAULT_BUFFER).appendTo(out, false);
        return out;
    }

    public static KitInstruction card(Labels.Anchor anchor, IntFunction<List<Component>> lines, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Card card;

            @Override
            protected void begin(PonderScene scene) {
                card = new Card();
                card.anchor = anchor;
                card.setVisible(true);
                scene.addElement(card);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (card == null) return;
                card.lines = lines.apply(tick);
                card.prevLevel = card.level;
                final boolean shown = tick - start < totalTicks - FADE;
                card.level = shown ? Math.min(1f, card.level + 1f / FADE) : Math.max(0f, card.level - 1f / FADE);
                if (last) card.setVisible(false);
            }
        };
    }
}
