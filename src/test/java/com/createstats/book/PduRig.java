package com.createstats.book;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.book.layout.Rect;
import com.createstats.book.specimen.PduPlates;
import com.createstats.book.specimen.VanillaFont;
import com.createstats.gui.PowerDistributionUnitScreen;
import com.createstats.gui.pdu.PduRect;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// the game's own screen in the test jvm
public final class PduRig {
    private PduRig() {
    }

    static sun.misc.Unsafe unsafe() {
        try {
            final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Font font;

    static synchronized Font font() {
        if (font == null) {
            final Font f = new Font(rl -> null, false);
            try {
                final Field splitter = Font.class.getDeclaredField("splitter");
                unsafe().putObject(f, unsafe().objectFieldOffset(splitter),
                        new StringSplitter((cp, style) -> VanillaFont.advance((char) cp) + (style.isBold() ? 1 : 0)));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
            font = f;
        }
        return font;
    }

    static Level clientLevel() {
        try {
            final Level level = (Level) unsafe().allocateInstance(ClientSideLevel.class);
            unsafe().putBoolean(level, unsafe().objectFieldOffset(Level.class.getDeclaredField("isClientSide")), true);
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> names;

    static synchronized String name(String key) {
        if (names == null) {
            try {
                names = BookFixtures.lang();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return names.getOrDefault(key, key);
    }

    static PowerDistributionUnitBE board() {
        return board(true);
    }

    public static PowerDistributionUnitBE board(boolean studWires) {
        final PowerDistributionUnitBE be = new PowerDistributionUnitBE(null, new BlockPos(0, -2048, 0), null) {
            @Override
            public boolean isValidBlockState(BlockState state) {
                return true;
            }
        };
        be.setLevel(clientLevel());
        be.handleUpdateTag(PduPlates.fixtureTag(PduRig::name, studWires), RegistryAccess.EMPTY);
        return be;
    }

    static PowerDistributionUnitScreen screen(String id) {
        return PowerDistributionUnitScreen.specimen(board(), PduPlates.view(id), PduPlates.W, PduPlates.H, null,
                font(), PduPlates.selected(id), PduPlates.selectAll(id), () -> PduPlates.anatomy(id),
                PduPlates.HISTORY_RANGE, null);
    }

    static PduPlates.Drawing measure(String id) {
        final Canvas g = Canvas.of(null);
        final Map<String, PduRect> parts = screen(id).measureSpecimen(g);
        final Map<String, int[]> out = new LinkedHashMap<>();
        for (final Map.Entry<String, PduRect> e : parts.entrySet()) {
            final PduRect r = e.getValue();
            out.put(e.getKey(), new int[]{r.x(), r.y(), r.w(), r.h()});
        }
        return new PduPlates.Drawing(out, g.texts);
    }

    static Map<String, PduPlates.Drawing> measureAll() {
        final Map<String, PduPlates.Drawing> out = new LinkedHashMap<>();
        for (final String id : PduPlates.IDS) {
            out.put(id, measure(id));
        }
        return out;
    }

    static Map<String, PduPlates.Drawing> record() {
        final Map<String, PduPlates.Drawing> out = new LinkedHashMap<>();
        for (final Map.Entry<String, PduPlates.Drawing> e : measureAll().entrySet()) {
            out.put(e.getKey(), e.getValue().withPins(PduPlates.choose(e.getKey(), e.getValue()).controls()));
        }
        return out;
    }

    public static void main(String[] args) throws IOException {
        final java.nio.file.Path out = java.nio.file.Path.of("src/main/resources" + PduPlates.RECORD);
        final String json = PduPlates.json(record());
        java.nio.file.Files.writeString(out, json, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("wrote " + out + ", " + json.length() + " chars");
    }

    static void paint(String id, PngBookCanvas panel, int mx, int my) {
        screen(id).renderSpecimen(Canvas.of(panel), mx, my);
    }

    static final class Canvas extends GuiGraphics {
        PngBookCanvas panel;
        List<PduPlates.Text> texts;
        Deque<Rect> scissor;

        private Canvas() {
            super(null, null);
        }

        static Canvas of(PngBookCanvas panel) {
            try {
                final Canvas c = (Canvas) unsafe().allocateInstance(Canvas.class);
                c.panel = panel;
                c.texts = new ArrayList<>();
                c.scissor = new ArrayDeque<>();
                return c;
            } catch (InstantiationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            if (panel != null) {
                panel.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), argb);
            }
        }

        private int run(Font font, String s, int x, int y, int argb, boolean bold) {
            final int w = font.width(FormattedText.of(s, Style.EMPTY.withBold(bold)));
            if (!s.isEmpty()) {
                texts.add(new PduPlates.Text(s, x, y, w, 8));
            }
            if (panel != null && !s.isEmpty()) {
                panel.text(s, x, y, (argb & 0xFC000000) == 0 ? argb | 0xFF000000 : argb, bold);
            }
            return x + w;
        }

        @Override
        public int drawString(Font font, String s, int x, int y, int argb, boolean shadow) {
            return run(font, s == null ? "" : s, x, y, argb, false);
        }

        @Override
        public int drawString(Font font, Component s, int x, int y, int argb, boolean shadow) {
            final int[] at = {x};
            s.visit((style, part) -> {
                at[0] = run(font, part, at[0], y, style.getColor() == null ? argb
                        : 0xFF000000 | style.getColor().getValue(), style.isBold());
                return Optional.empty();
            }, Style.EMPTY);
            return at[0];
        }

        @Override
        public int drawString(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
            final StringBuilder b = new StringBuilder();
            final boolean[] bold = {false};
            final int[] at = {x};
            s.accept((i, style, cp) -> {
                if (style.isBold() != bold[0] && b.length() > 0) {
                    at[0] = run(font, b.toString(), at[0], y, argb, bold[0]);
                    b.setLength(0);
                }
                bold[0] = style.isBold();
                b.appendCodePoint(cp);
                return true;
            });
            if (b.length() > 0) {
                at[0] = run(font, b.toString(), at[0], y, argb, bold[0]);
            }
            return at[0];
        }

        @Override
        public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int argb) {
            for (final FormattedText line : font.getSplitter().splitLines(text, width, Style.EMPTY)) {
                final int[] at = {x};
                final int lineY = y;
                line.visit((style, part) -> {
                    at[0] = run(font, part, at[0], lineY, argb, style.isBold());
                    return Optional.empty();
                }, Style.EMPTY);
                y += 9;
            }
        }

        @Override
        public void enableScissor(int x0, int y0, int x1, int y1) {
            Rect r = new Rect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
            final Rect outer = scissor.peek();
            if (outer != null) {
                final int ix0 = Math.max(outer.x(), r.x());
                final int iy0 = Math.max(outer.y(), r.y());
                final int ix1 = Math.min(outer.right(), r.right());
                final int iy1 = Math.min(outer.bottom(), r.bottom());
                r = new Rect(ix0, iy0, Math.max(0, ix1 - ix0), Math.max(0, iy1 - iy0));
            }
            scissor.push(r);
            if (panel != null) {
                panel.clip(r);
            }
        }

        @Override
        public void disableScissor() {
            scissor.poll();
            if (panel != null) {
                panel.clip(scissor.peek());
            }
        }

        @Override
        public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        }

        @Override
        public void drawManaged(Runnable r) {
            r.run();
        }
    }
}
