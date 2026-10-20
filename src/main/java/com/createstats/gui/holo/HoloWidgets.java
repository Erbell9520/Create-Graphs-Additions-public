package com.createstats.gui.holo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class HoloWidgets {
    private HoloWidgets() {
    }

    public static void pill(GuiGraphics g, int x, int y, float on, float alpha) {
        final int track = HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, on);
        final int knob = HoloUi.lerpColor(HoloUi.KNOB_OFF, HoloUi.TEXT_HI, on);
        HoloUi.roundRect(g, x, y, 26, 14, 7, HoloUi.fade(track, alpha));
        final int kx = x + 2 + Math.round(12.0f * on);
        HoloUi.roundRect(g, kx, y + 2, 10, 10, 5, HoloUi.fade(knob, alpha));
    }

    public static class Toggle extends HoloComponent {
        private final String label;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private final HoloUi.Anim knob;

        public Toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.knob = new HoloUi.Anim(getter.getAsBoolean() ? 1.0f : 0.0f, 160);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final boolean on = this.getter.getAsBoolean();
            this.knob.target(on ? 1.0f : 0.0f);
            final float hov = this.hover.value();
            final int fill = HoloUi.lerpColor(HoloUi.SURFACE, HoloUi.SURFACE_HI, hov);
            final int border = HoloUi.lerpColor(HoloUi.CARD_BORDER,
                    HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, 0.45f), hov);
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                    HoloUi.fade(border, alpha), HoloUi.fade(fill, alpha));
            final int textColor = HoloUi.lerpColor(HoloUi.TEXT_MID, HoloUi.TEXT_HI, on ? 1.0f : hov);
            HoloUi.text(g, font, HoloUi.clip(font, this.label, this.w - 48), this.x + 10,
                    this.y + 7, HoloUi.fade(textColor, alpha), true);
            pill(g, this.x + this.w - 36, this.y + 4, this.knob.value(), alpha);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            this.setter.accept(!this.getter.getAsBoolean());
            return true;
        }
    }

    public static class Segmented extends HoloComponent {
        private final Supplier<String[]> options;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final HoloUi.Anim slide;

        public Segmented(Supplier<String[]> options, IntSupplier getter, IntConsumer setter) {
            this.options = options;
            this.getter = getter;
            this.setter = setter;
            this.slide = new HoloUi.Anim(getter.getAsInt(), 200);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final String[] opts = this.options.get();
            if (opts.length == 0) {
                return;
            }
            final int selected = Mth.clamp(this.getter.getAsInt(), 0, opts.length - 1);
            this.slide.target(selected);
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                    HoloUi.fade(HoloUi.CARD_BORDER, alpha), HoloUi.fade(HoloUi.SURFACE, alpha));

            final int inner = this.w - 4;
            final float sel = this.slide.value();
            final int px = this.x + 2 + Math.round(inner * sel / opts.length);
            final int pw = Math.round(inner * (sel + 1) / opts.length) - Math.round(inner * sel / opts.length);
            HoloUi.roundRect(g, px, this.y + 2, pw, this.h - 4, 3, HoloUi.fade(HoloUi.ACCENT, alpha));

            for (int i = 0; i < opts.length; i++) {
                final int sx = this.x + 2 + inner * i / opts.length;
                final int ex = this.x + 2 + inner * (i + 1) / opts.length;

                final float t = Mth.clamp(1.0f - Math.abs(i - sel), 0.0f, 1.0f);
                final int color = HoloUi.lerpColor(HoloUi.TEXT_MID, HoloUi.ACCENT_DK, t);
                HoloUi.textCenter(g, font, HoloUi.clip(font, opts[i], ex - sx - 6),
                        (sx + ex) / 2, this.y + 7, HoloUi.fade(color, alpha), t < 0.5f);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            final int n = this.options.get().length;
            if (n == 0) {
                return false;
            }
            final int idx = Mth.clamp((int) ((mx - this.x - 2) * n / (this.w - 4)), 0, n - 1);
            this.setter.accept(idx);
            return true;
        }
    }

    public static class Slider extends HoloComponent {
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final Consumer<Double> setter;
        private final Supplier<String> readout;
        private final HoloUi.Anim fill;
        private boolean dragging;

        public Slider(String label, double min, double max, double step, DoubleSupplier getter,
                      Consumer<Double> setter, Supplier<String> readout) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.readout = readout;
            this.fill = new HoloUi.Anim((float) this.norm(getter.getAsDouble()), 140);
        }

        private double norm(double v) {
            return this.max <= this.min ? 0.0 : Mth.clamp((v - this.min) / (this.max - this.min), 0.0, 1.0);
        }

        private void applyFromMouse(double mx) {
            final int trackW = this.w - 10;
            final double frac = Mth.clamp((mx - this.x - 5) / Math.max(1, trackW), 0.0, 1.0);
            double value = this.min + frac * (this.max - this.min);
            if (this.step > 0.0) {
                value = this.min + Math.round((value - this.min) / this.step) * this.step;
            }
            this.setter.accept(Mth.clamp(value, this.min, this.max));
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final float target = (float) this.norm(this.getter.getAsDouble());
            if (this.dragging) {
                this.fill.snap(target);
            } else {
                this.fill.target(target);
            }
            final float hov = Math.max(this.hover.value(), this.dragging ? 1.0f : 0.0f);
            HoloUi.text(g, font, this.label, this.x, this.y,
                    HoloUi.fade(HoloUi.lerpColor(HoloUi.TEXT_MID, HoloUi.TEXT_HI, hov), alpha), true);
            HoloUi.textRight(g, font, this.readout.get(), this.x + this.w, this.y,
                    HoloUi.fade(HoloUi.ACCENT, alpha), true);

            final int ty = this.y + 13;
            HoloUi.roundRect(g, this.x, ty, this.w, 4, 2, HoloUi.fade(HoloUi.CARD_BORDER, alpha));
            final int trackW = this.w - 10;
            final int fillW = Math.round(trackW * this.fill.value());
            if (fillW > 0) {
                HoloUi.roundRect(g, this.x, ty, fillW + 5, 4, 2, HoloUi.fade(HoloUi.ACCENT, alpha));
            }
            final int hx = this.x + fillW;
            final int knob = HoloUi.lerpColor(HoloUi.TEXT_HI, 0xFFFFFFFF, hov);
            HoloUi.roundRect(g, hx, ty - 3, 10, 10, 5, HoloUi.fade(knob, alpha));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            this.dragging = true;
            this.applyFromMouse(mx);
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button) {
            if (!this.dragging) {
                return false;
            }
            this.applyFromMouse(mx);
            return true;
        }

        @Override
        public void mouseReleased(double mx, double my, int button) {
            this.dragging = false;
        }
    }

    public static class Stepper extends HoloComponent {
        private final String label;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int min;
        private final int max;
        private final HoloUi.Anim bump;

        public Stepper(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.bump = new HoloUi.Anim(0.0f, 180);
        }

        private boolean overMinus(double mx, double my) {
            return this.isOver(mx, my) && mx < this.x + 22;
        }

        private boolean overPlus(double mx, double my) {
            return this.isOver(mx, my) && mx >= this.x + this.w - 22;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final int value = this.getter.getAsInt();
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                    HoloUi.fade(HoloUi.CARD_BORDER, alpha),
                    HoloUi.fade(HoloUi.lerpColor(HoloUi.SURFACE, HoloUi.SURFACE_HI, this.hover.value() * 0.5f), alpha));

            final boolean canDown = value > this.min;
            final boolean canUp = value < this.max;
            final boolean hovMinus = this.overMinus(mouseX, mouseY);
            final boolean hovPlus = this.overPlus(mouseX, mouseY);
            HoloUi.roundRect(g, this.x + 2, this.y + 2, 18, this.h - 4, 3,
                    HoloUi.fade(hovMinus && canDown ? HoloUi.ACCENT_SOFT : HoloUi.SURFACE_HI, alpha));
            HoloUi.roundRect(g, this.x + this.w - 20, this.y + 2, 18, this.h - 4, 3,
                    HoloUi.fade(hovPlus && canUp ? HoloUi.ACCENT_SOFT : HoloUi.SURFACE_HI, alpha));
            HoloUi.textCenter(g, font, "-", this.x + 11, this.y + 7,
                    HoloUi.fade(canDown ? (hovMinus ? HoloUi.ACCENT : HoloUi.TEXT_MID) : HoloUi.TEXT_DIM, alpha), true);
            HoloUi.textCenter(g, font, "+", this.x + this.w - 11, this.y + 7,
                    HoloUi.fade(canUp ? (hovPlus ? HoloUi.ACCENT : HoloUi.TEXT_MID) : HoloUi.TEXT_DIM, alpha), true);

            HoloUi.text(g, font, this.label, this.x + 26, this.y + 7, HoloUi.fade(HoloUi.TEXT_MID, alpha), true);

            final int valueColor = HoloUi.lerpColor(HoloUi.TEXT_HI, HoloUi.ACCENT, this.bump.value());
            HoloUi.textRight(g, font, String.valueOf(value), this.x + this.w - 26, this.y + 7,
                    HoloUi.fade(valueColor, alpha), true);
            if (this.bump.goal() > 0.0f && this.bump.value() > 0.85f) {
                this.bump.target(0.0f);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled) {
                return false;
            }
            final int value = this.getter.getAsInt();
            if (this.overMinus(mx, my)) {
                this.setter.accept(Math.max(this.min, value - 1));
            } else if (this.overPlus(mx, my)) {
                this.setter.accept(Math.min(this.max, value + 1));
            } else {
                return false;
            }
            this.bump.snap(1.0f);
            this.bump.target(0.0f);
            return true;
        }
    }

    public static class Picker extends HoloComponent {
        private final String label;
        private final Supplier<String> value;
        private final IntConsumer cycle;

        public Picker(String label, Supplier<String> value, IntConsumer cycle) {
            this.label = label;
            this.value = value;
            this.cycle = cycle;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final float hov = this.hover.value();
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                    HoloUi.fade(HoloUi.lerpColor(HoloUi.CARD_BORDER,
                            HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, 0.45f), hov), alpha),
                    HoloUi.fade(HoloUi.lerpColor(HoloUi.SURFACE, HoloUi.SURFACE_HI, hov), alpha));
            HoloUi.text(g, font, this.label, this.x + 10, this.y + 7,
                    HoloUi.fade(HoloUi.TEXT_MID, alpha), true);
            final int room = this.w - 24 - font.width(this.label) - 14;
            HoloUi.textRight(g, font, HoloUi.clip(font, this.value.get(), Math.max(20, room)),
                    this.x + this.w - 20, this.y + 7,
                    HoloUi.fade(this.enabled ? HoloUi.TEXT_HI : HoloUi.TEXT_DIM, alpha), true);
            final int cx = this.x + this.w - 13;
            final int cy = this.y + 10;
            for (int i = 0; i < 3; i++) {
                g.fill(cx - 2 + i, cy + i, cx + 3 - i, cy + i + 1, HoloUi.fade(HoloUi.TEXT_DIM, alpha));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!this.enabled || !this.isOver(mx, my) || (button != 0 && button != 1)) {
                return false;
            }
            this.cycle.accept(button == 0 ? 1 : -1);
            return true;
        }
    }

    public static class Chip extends HoloComponent {
        private final Supplier<String> label;
        private final BooleanSupplier active;
        private final Runnable onPress;

        public Chip(Supplier<String> label, BooleanSupplier active, Runnable onPress) {
            this.label = label;
            this.active = active;
            this.onPress = onPress;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final boolean on = this.active.getAsBoolean();
            final float hov = this.hover.value();
            final int border = on ? HoloUi.ACCENT : HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, hov * 0.5f);
            final int fill = on ? HoloUi.ACCENT_SOFT : HoloUi.lerpColor(HoloUi.SURFACE, HoloUi.SURFACE_HI, hov);
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, this.h / 2,
                    HoloUi.fade(border, alpha), HoloUi.fade(fill, alpha));
            HoloUi.textCenter(g, font, this.label.get(), this.x + this.w / 2, this.y + (this.h - HoloUi.TEXT_H) / 2 + 1,
                    HoloUi.fade(on ? HoloUi.ACCENT : HoloUi.TEXT_MID, alpha), false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            this.onPress.run();
            return true;
        }
    }

    public static class Swatches extends HoloComponent {
        private final int[] backgrounds;
        private final int[] accents;
        private final IntSupplier getter;
        private final IntConsumer setter;

        public Swatches(int[] backgrounds, int[] accents, IntSupplier getter, IntConsumer setter) {
            this.backgrounds = backgrounds;
            this.accents = accents;
            this.getter = getter;
            this.setter = setter;
        }

        private int cellWidth() {
            final int n = this.backgrounds.length;
            return n == 0 ? 0 : (this.w - (n - 1) * 4) / n;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final int n = this.backgrounds.length;
            final int cell = this.cellWidth();
            final int selected = Mth.clamp(this.getter.getAsInt(), 0, Math.max(0, n - 1));
            for (int i = 0; i < n; i++) {
                final int sx = this.x + i * (cell + 4);
                final boolean isSel = i == selected;
                final boolean isHov = mouseX >= sx && mouseX < sx + cell
                        && mouseY >= this.y && mouseY < this.y + this.h;
                if (isSel) {
                    HoloUi.roundRect(g, sx - 1, this.y - 1, cell + 2, this.h + 2, 5,
                            HoloUi.fade(HoloUi.ACCENT, alpha));
                }
                final int border = isSel ? HoloUi.ACCENT
                        : (isHov ? HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, 0.6f) : HoloUi.CARD_BORDER);
                HoloUi.roundFrame(g, sx, this.y, cell, this.h, 4, HoloUi.fade(border, alpha),
                        HoloUi.fade(0xFF000000 | this.backgrounds[i], alpha));
                g.fill(sx + 3, this.y + this.h - 7, sx + cell - 3, this.y + this.h - 4,
                        HoloUi.fade(0xFF000000 | this.accents[i], alpha));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            final int cell = this.cellWidth();
            if (cell <= 0) {
                return false;
            }
            final int idx = Mth.clamp((int) ((mx - this.x) / (cell + 4)), 0, this.backgrounds.length - 1);
            this.setter.accept(idx);
            return true;
        }
    }

    public static class ChannelRow extends HoloComponent {
        private final String name;
        private final Supplier<String> value;
        private final BooleanSupplier visible;
        private final Runnable toggle;
        private final HoloUi.Anim knob;

        public ChannelRow(String name, Supplier<String> value, BooleanSupplier visible, Runnable toggle) {
            this.name = name;
            this.value = value;
            this.visible = visible;
            this.toggle = toggle;
            this.knob = new HoloUi.Anim(visible.getAsBoolean() ? 1.0f : 0.0f, 160);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final boolean on = this.visible.getAsBoolean();
            this.knob.target(on ? 1.0f : 0.0f);
            final float hov = this.hover.value();
            if (hov > 0.01f) {
                HoloUi.roundRect(g, this.x - 4, this.y, this.w + 8, this.h - 1, 3,
                        HoloUi.fade(HoloUi.SURFACE, alpha * hov));
            }
            final String readout = this.value.get();
            final int readoutW = font.width(readout);
            HoloUi.text(g, font, HoloUi.clip(font, this.name, this.w - readoutW - 44), this.x + 2, this.y + 6,
                    HoloUi.fade(on ? HoloUi.TEXT_HI : HoloUi.TEXT_DIM, alpha), true);
            HoloUi.textRight(g, font, readout, this.x + this.w - 34, this.y + 6,
                    HoloUi.fade(on ? HoloUi.ACCENT : HoloUi.TEXT_DIM, alpha), true);
            pill(g, this.x + this.w - 26, this.y + 3, this.knob.value(), alpha);
            HoloUi.hLine(g, this.x, this.y + this.h - 1, this.w, HoloUi.fade(HoloUi.DIVIDER, alpha));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            this.toggle.run();
            return true;
        }
    }

    public static class Button extends HoloComponent {
        private final Supplier<String> label;
        private final Runnable onPress;
        private final boolean primary;

        public Button(String label, boolean primary, Runnable onPress) {
            this(() -> label, primary, onPress);
        }

        public Button(Supplier<String> label, boolean primary, Runnable onPress) {
            this.label = label;
            this.primary = primary;
            this.onPress = onPress;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            final float hov = this.hover.value();
            if (this.primary) {
                final int fill = HoloUi.lerpColor(HoloUi.ACCENT, 0xFF8AECF6, hov);
                HoloUi.roundRect(g, this.x, this.y, this.w, this.h, 4, HoloUi.fade(fill, alpha));
                HoloUi.textCenter(g, font, this.label.get(), this.x + this.w / 2, this.y + 7,
                        HoloUi.fade(HoloUi.ACCENT_DK, alpha), false);
            } else {
                HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                        HoloUi.fade(HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, hov * 0.6f), alpha),
                        HoloUi.fade(HoloUi.lerpColor(HoloUi.SURFACE, HoloUi.SURFACE_HI, hov), alpha));
                HoloUi.textCenter(g, font, this.label.get(), this.x + this.w / 2, this.y + 7,
                        HoloUi.fade(HoloUi.lerpColor(HoloUi.TEXT_MID, HoloUi.TEXT_HI, hov), alpha), true);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !this.enabled || !this.isOver(mx, my)) {
                return false;
            }
            this.onPress.run();
            return true;
        }
    }

    // hand rolled, EditBox paints its own stone frame
    public static class NumberField extends HoloComponent {
        private final String label;
        private final Supplier<String> initial;
        private final Consumer<Float> commit;
        private String buffer;
        private boolean focused;

        public NumberField(String label, Supplier<String> initial, Consumer<Float> commit) {
            this.label = label;
            this.initial = initial;
            this.commit = commit;
            this.buffer = initial.get();
        }

        private void commitBuffer() {
            try {
                this.commit.accept(this.buffer.isEmpty() || this.buffer.equals("-")
                        ? 0.0f : Float.parseFloat(this.buffer));
            } catch (NumberFormatException ex) {
                this.buffer = this.initial.get();
            }
        }

        @Override
        public void loseFocus() {
            if (this.focused) {
                this.focused = false;
                this.commitBuffer();
            }
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float alpha) {
            if (!this.focused) {
                this.buffer = this.initial.get();
            }
            final float hov = this.hover.value();
            final int border = this.focused ? HoloUi.ACCENT
                    : HoloUi.lerpColor(HoloUi.CARD_BORDER, HoloUi.ACCENT, hov * 0.5f);
            HoloUi.roundFrame(g, this.x, this.y, this.w, this.h, 4,
                    HoloUi.fade(border, alpha), HoloUi.fade(HoloUi.SURFACE, alpha));
            HoloUi.text(g, font, this.label, this.x + 10, this.y + 7,
                    HoloUi.fade(HoloUi.TEXT_DIM, alpha), true);
            final int textRight = this.x + this.w - 10;
            HoloUi.textRight(g, font, this.buffer, textRight, this.y + 7,
                    HoloUi.fade(HoloUi.TEXT_HI, alpha), true);
            if (this.focused && (net.minecraft.Util.getMillis() / 500L) % 2L == 0L) {
                g.fill(textRight + 1, this.y + 6, textRight + 2, this.y + 16,
                        HoloUi.fade(HoloUi.ACCENT, alpha));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            final boolean over = this.isOver(mx, my);
            if (this.focused && !over) {
                this.loseFocus();
                return false;
            }
            if (button != 0 || !this.enabled || !over) {
                return false;
            }
            this.focused = true;
            this.buffer = this.initial.get();
            return true;
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            if (!this.focused) {
                return false;
            }
            if ((c >= '0' && c <= '9') || (c == '.' && !this.buffer.contains("."))
                    || (c == '-' && this.buffer.isEmpty())) {
                if (this.buffer.length() < 12) {
                    this.buffer += c;
                    this.commitBuffer();
                }
                return true;
            }
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scancode, int modifiers) {
            if (!this.focused) {
                return false;
            }
            if (key == 259 && !this.buffer.isEmpty()) {
                this.buffer = this.buffer.substring(0, this.buffer.length() - 1);
                this.commitBuffer();
                return true;
            }
            if (key == 257 || key == 335 || key == 256) {
                this.loseFocus();
                // esc unfocuses first, second press closes
                return true;
            }
            return true;
        }

        public boolean isFocused() {
            return this.focused;
        }
    }
}
