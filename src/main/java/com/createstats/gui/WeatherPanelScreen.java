package com.createstats.gui;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.WeatherPanelBE;
import com.createstats.gui.weather.BarometerDial;
import com.createstats.gui.weather.DayStrip;
import com.createstats.gui.weather.NextStrip;
import com.createstats.gui.weather.SkyCard;
import com.createstats.gui.weather.WeatherAnim;
import com.createstats.gui.weather.WeatherContext;
import com.createstats.gui.weather.WeatherTheme;
import com.createstats.renderer.WeatherFaceTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WeatherPanelScreen extends Screen {
    private static final int CHIP_W = 46;
    private static final int CHIP_H = 12;
    private static final int CLOSE_W = 11;

    private static final float OPEN_TICKS = 3.6f;

    private static final float CARD_STAGGER = 1.6f;

    private final WeatherPanelBE panel;
    private int left;
    private int top;
    private float openedAt = Float.NaN;

    public WeatherPanelScreen(WeatherPanelBE panel) {
        super(Component.translatable("block.create_stats.weather_panel"));
        this.panel = panel;
    }

    @Override
    protected void init() {
        left = (width - WeatherTheme.PAGE_W) / 2;
        top = (height - WeatherTheme.PAGE_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x90101418);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        final float now = clock(partialTick);
        if (Float.isNaN(openedAt)) {
            openedAt = now;
        }
        final WeatherAnim anim = WeatherFaceTextureManager.animFor(panel);
        final float nightness = nightness(partialTick);
        final float elapsed = elapsedSinceSync(partialTick);

        feed(anim, now, partialTick);

        final boolean awake = panel.isAwake();
        final WeatherContext ctx = new WeatherContext(panel, anim, anim.palette(nightness),
                nightness, partialTick, elapsed, font, WeatherFaceTextureManager.fahrenheit(),
                !awake);

        final WeatherTheme.Palette p = ctx.palette();
        g.fill(left, top, left + WeatherTheme.PAGE_W, top + WeatherTheme.PAGE_H, p.ground());
        WeatherTheme.outline(g, left, top, WeatherTheme.PAGE_W, WeatherTheme.PAGE_H,
                WeatherTheme.alpha(p.ink(), 0.18f));

        header(g, mouseX, mouseY, ctx);

        card(g, ctx, 0, now, left + WeatherTheme.PAD, top + WeatherTheme.HERO_Y,
                WeatherTheme.SKY_W, WeatherTheme.HERO_H, SkyCard::render);
        card(g, ctx, 1, now, left + WeatherTheme.PAD + WeatherTheme.SKY_W + WeatherTheme.GAP,
                top + WeatherTheme.HERO_Y, WeatherTheme.BARO_W, WeatherTheme.HERO_H,
                BarometerDial::render);
        card(g, ctx, 2, now, left + WeatherTheme.PAD, top + WeatherTheme.NEXT_Y,
                WeatherTheme.CONTENT_W, WeatherTheme.NEXT_H, NextStrip::render);
        card(g, ctx, 3, now, left + WeatherTheme.PAD, top + WeatherTheme.DAYS_Y,
                WeatherTheme.CONTENT_W, WeatherTheme.DAYS_H, DayStrip::render);

        if (!awake) {
            staleChip(g, ctx);
        }
    }

    private interface CardDraw {
        void draw(GuiGraphics g, int x, int y, int w, int h, WeatherContext ctx);
    }

    private void card(GuiGraphics g, WeatherContext ctx, int index, float now,
                      int x, int y, int w, int h, CardDraw draw) {
        final float t = UiFx.clamp01((now - openedAt - index * CARD_STAGGER) / OPEN_TICKS);
        final float eased = UiFx.easeOut(t);
        final int rise = Math.round((1f - eased) * 6f);
        draw.draw(g, x, y - rise, w, h, ctx);
        if (eased < 1f) {
            g.fill(x, y - rise - 2, x + w, y - rise + h + 2,
                    WeatherTheme.alpha(ctx.palette().ground(), 1f - eased));
        }
    }

    private void header(GuiGraphics g, int mouseX, int mouseY, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        final int y = top + WeatherTheme.HEADER_Y;
        final int x = left + WeatherTheme.PAD;

        WeatherTheme.disc(g, x + 3.5f, y + 6.5f, 3.5f, p.accent());
        WeatherTheme.text(g, font, "Weather", x + 12, y + 3, WeatherTheme.BODY, p.ink());

        final String place = placeName();
        WeatherTheme.text(g, font, place, x + 62, y + 4, WeatherTheme.CAPTION,
                WeatherTheme.alpha(p.inkDim(), 0.95f));

        unitChip(g, mouseX, mouseY, ctx);

        final int closeX = left + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W;
        final boolean hot = inside(mouseX, mouseY, closeX, y + 2, CLOSE_W, CHIP_H);
        WeatherTheme.textCentre(g, font, "x", closeX + CLOSE_W / 2f, y + 4, WeatherTheme.BODY,
                hot ? p.ink() : p.inkDim());

        g.fill(x, y + WeatherTheme.HEADER_H - 3, left + WeatherTheme.PAGE_W - WeatherTheme.PAD,
                y + WeatherTheme.HEADER_H - 2, WeatherTheme.alpha(p.ink(), 0.10f));
    }

    private void unitChip(GuiGraphics g, int mouseX, int mouseY, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        final int x = chipX(left);
        final int y = top + WeatherTheme.HEADER_Y + 2;
        final boolean f = ctx.fahrenheit();

        WeatherTheme.pill(g, x, y, CHIP_W, CHIP_H, WeatherTheme.alpha(p.ink(), 0.08f));
        WeatherTheme.pill(g, f ? x + CHIP_W / 2 : x, y, CHIP_W / 2, CHIP_H, p.accent());
        WeatherTheme.textCentre(g, font, "°C", x + CHIP_W / 4f, y + 2, WeatherTheme.CAPTION,
                f ? p.inkDim() : p.card());
        WeatherTheme.textCentre(g, font, "°F", x + CHIP_W * 3 / 4f, y + 2, WeatherTheme.CAPTION,
                f ? p.card() : p.inkDim());
        if (inside(mouseX, mouseY, x, y, CHIP_W, CHIP_H)) {
            WeatherTheme.pill(g, x, y, CHIP_W, CHIP_H, 0x18FFFFFF);
        }
    }

    private void staleChip(GuiGraphics g, WeatherContext ctx) {
        final int w = 150;
        final int x = left + (WeatherTheme.PAGE_W - w) / 2;
        final int y = top + WeatherTheme.HEADER_Y + WeatherTheme.HEADER_H + 4;
        WeatherTheme.pill(g, x, y, w, 14, WeatherTheme.alpha(WeatherTheme.AMBER, 0.92f));
        final String age = panel.syncGameTime() == 0L ? "never read"
                : "last read " + NextStrip.formatTicks(Math.round(ctx.elapsedSinceSync())) + " ago";
        WeatherTheme.textCentre(g, font, "No supply, " + age, x + w / 2f, y + 3,
                WeatherTheme.CAPTION, 0xFF201404);
    }

    private String placeName() {
        final var level = panel.getLevel();
        if (level == null) {
            return "";
        }
        final var biome = level.getBiome(panel.getBlockPos());
        final String key = biome.unwrapKey()
                .map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath())
                .orElse("");
        final String name = key.isEmpty() ? "" : Component.translatable(key).getString();
        return name + (name.isEmpty() ? "y " : ", y ") + panel.getBlockPos().getY();
    }

    private static int chipX(int left) {
        return left + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W - 6 - CHIP_W;
    }

    public static boolean hitsUnitChip(int mouseX, int mouseY, int left, int top) {
        final int x = chipX(left);
        final int y = top + WeatherTheme.HEADER_Y + 2;
        return mouseX >= x && mouseX < x + CHIP_W && mouseY >= y && mouseY < y + CHIP_H;
    }

    public static boolean hitsClose(int mouseX, int mouseY, int left, int top) {
        final int x = left + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W;
        final int y = top + WeatherTheme.HEADER_Y + 2;
        return mouseX >= x && mouseX < x + CLOSE_W && mouseY >= y && mouseY < y + CHIP_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            final int mx = (int) mouseX;
            final int my = (int) mouseY;
            if (hitsUnitChip(mx, my, left, top)) {
                final boolean next = !WeatherFaceTextureManager.fahrenheit();
                ModConfiguration.CLIENT.weatherFahrenheit.set(next);
                ModConfiguration.CLIENT_SPEC.save();
                return true;
            }
            if (hitsClose(mx, my, left, top)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void feed(WeatherAnim anim, float now, float partialTick) {
        final var level = panel.getLevel();
        final float rain = level == null ? 0f : level.getRainLevel(partialTick);
        final int next = panel.ticksToNextChange();
        final boolean building = panel.condition() == com.createstats.weather.Condition.CLEAR
                && next >= 0;
        final float approaching = building ? 1f - Math.min(1f, next / 8000f) : 0f;
        anim.sync(panel.condition(), WeatherFaceTextureManager.displayTemperature(panel), panel.pressure(), panel.humidity(),
                Math.max(rain, approaching), rain, panel.forecast());
        anim.updateTo(now);
    }

    private float clock(float partialTick) {
        final var level = panel.getLevel();
        return level == null ? 0f : level.getGameTime() + partialTick;
    }

    private float elapsedSinceSync(float partialTick) {
        final var level = panel.getLevel();
        if (level == null || panel.syncGameTime() == 0L) {
            return 0f;
        }
        return Math.max(0f, level.getGameTime() + partialTick - panel.syncGameTime());
    }

    private float nightness(float partialTick) {
        final var level = panel.getLevel();
        if (level == null) {
            return 0f;
        }
        final float sun = (float) Math.cos(level.getTimeOfDay(partialTick) * Math.PI * 2.0);
        return UiFx.clamp01(0.5f - sun * 1.6f);
    }
}
