package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.dispatch.BoardHeaderState;
import com.createstats.dispatch.BoardTier;
import com.createstats.dispatch.CargoSummaryEntry;
import com.createstats.dispatch.DispatchBoardSnapshot;
import com.createstats.dispatch.DispatchRowEntry;
import com.createstats.dispatch.DispatchRowState;
import com.createstats.train.egg.ManifestChoreography;
import com.createstats.train.egg.ManifestEgg;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderHistoryType;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.TrainSummarySnapshot;
import com.createstats.train.manifest.TrainVisitRecord;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class DispatchBoardTextureManager {
    private static final Map<BlockPos, TextureEntry> TEXTURES = new HashMap<>();
    private static final int BASE_RESOLUTION = 192;
    private static final int UPDATE_INTERVAL = 5;
    private static final int MAX_IDLE_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 100;
    private static int cleanupCounter;
    private static long currentFrameTime;

    private static final int BG_DARK = 0xFF1A1814;
    private static final int BG_PANEL = 0xFF231F1A;
    private static final int BORDER_OUTER = 0xFF3D342A;
    private static final int BORDER_INNER = 0xFF554A3C;
    private static final int BRASS_ACCENT = 0xFFD4B254;
    private static final int BRASS_DIM = 0xFF9A8240;
    private static final int HEADER_BG = 0xFF2E2820;
    private static final int ROW_BG_EVEN = 0xFF262118;
    private static final int ROW_BG_ODD = 0xFF2A2519;
    private static final int ROW_SEPARATOR = 0xFF3A3228;
    private static final int TEXT_PRIMARY = 0xFFE8D8B4;
    private static final int TEXT_DIM = 0xFF8D857C;
    private static final int TEXT_BRIGHT = 0xFFFFF0D0;
    private static final int SECTION_HEADER = 0xFF9A8240;
    private static final int TRAIN_PANEL_BG = 0xFF2A261E;
    private static final int TRAIN_DOCKED_COLOR = 0xFF74D87F;
    private static final int TRAIN_SIGNAL_COLOR = 0xFFFFCC00;
    private static final int TRAIN_ENROUTE_COLOR = 0xFF55CCFF;

    private static final boolean[][] ICON_TRAIN = {
        {false, false, true,  true,  true,  true,  true,  false, false},
        {false, true,  true,  true,  true,  true,  true,  true,  false},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {true,  true,  true,  true,  true,  true,  true,  true,  true},
        {false, true,  false, true,  false, true,  false, true,  false},
        {false, false, false, false, false, false, false, false, false},
    };

    private static final boolean[][] ICON_CARGO = {
        {true,  true,  true,  true,  true,  true,  true},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  false, false, true},
        {true,  true,  true,  true,  true,  true,  true},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  false, false, true},
        {true,  true,  true,  true,  true,  true,  true},
    };

    private static final boolean[][] ICON_CLOCK = {
        {false, false, true,  true,  true,  false, false},
        {false, true,  false, false, false, true,  false},
        {true,  false, false, true,  false, false, true},
        {true,  false, false, true,  true,  false, true},
        {true,  false, false, false, false, false, true},
        {false, true,  false, false, false, true,  false},
        {false, false, true,  true,  true,  false, false},
    };

    private static final boolean[][] ICON_TRACKS = {
        {true,  false, true,  false, true,  false, true},
        {false, false, false, false, false, false, false},
        {true,  true,  true,  true,  true,  true,  true},
        {false, false, false, false, false, false, false},
        {true,  true,  true,  true,  true,  true,  true},
        {false, false, false, false, false, false, false},
        {true,  false, true,  false, true,  false, true},
    };

    private static void drawPixelIcon(NativeImage image, boolean[][] icon, int x, int y, int color, int pixScale) {
        for (int iy = 0; iy < icon.length; iy++) {
            for (int ix = 0; ix < icon[iy].length; ix++) {
                if (icon[iy][ix]) {
                    for (int sy = 0; sy < pixScale; sy++) {
                        for (int sx = 0; sx < pixScale; sx++) {
                            int px = x + ix * pixScale + sx;
                            int py = y + iy * pixScale + sy;
                            if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                                image.setPixelRGBA(px, py, color);
                            }
                        }
                    }
                }
            }
        }
    }

    private DispatchBoardTextureManager() {
    }

    private static int brightenColor(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int pulseColor(int argb, float speed, float amount) {
        float p = 0.5f + 0.5f * (float) Math.sin(currentFrameTime * speed);
        return brightenColor(argb, amount * p);
    }

    private static void drawBarSheen(NativeImage image, int x, int y, int fillW, int h, int baseColor, float speed) {
        if (fillW <= 4) {
            return;
        }
        int sheen = brightenColor(baseColor, 0.5f);
        int period = 11;
        int phase = (int) ((currentFrameTime * speed) % period);
        for (int sx = x + phase; sx < x + fillW; sx += period) {
            TextureGraphRenderer.fillRect(image, sx, y, 1, h, sheen);
        }
    }

    public static ResourceLocation getOrUpdateTexture(DispatchBoardBE be) {
        BlockPos pos = be.getBlockPos();
        TextureEntry entry = TEXTURES.get(pos);
        int mbW = be.getMultiblockWidth();
        int mbH = be.getMultiblockHeight();
        int texW = mbW * BASE_RESOLUTION;
        int texH = mbH * BASE_RESOLUTION;

        if (entry == null || entry.texWidth != texW || entry.texHeight != texH) {
            if (entry != null) {
                disposeEntry(entry);
                TEXTURES.remove(pos);
            }
            entry = createTextureEntry(pos, texW, texH);
            if (entry == null) {
                return null;
            }
            TEXTURES.put(pos, entry);
        }

        long currentTick = getGameTime();
        entry.lastAccessTick = currentTick;
        if (currentTick - entry.lastUpdateTick >= UPDATE_INTERVAL || entry.needsRedraw
                || manifestFrameDue(be, entry.lastUpdateTick, currentTick)) {
            updateTexture(entry, be, currentTick);
            entry.lastUpdateTick = currentTick;
            entry.needsRedraw = false;
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStaleTextures(currentTick);
        }

        return entry.textureLocation;
    }

    public static void markDirty(BlockPos pos) {
        TextureEntry entry = TEXTURES.get(pos);
        if (entry != null) {
            entry.needsRedraw = true;
        }
    }

    public static void disposeTexture(BlockPos pos) {
        TextureEntry entry = TEXTURES.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
    }

    public static void disposeAll() {
        for (TextureEntry entry : TEXTURES.values()) {
            disposeEntry(entry);
        }
        TEXTURES.clear();
    }

    private static TextureEntry createTextureEntry(BlockPos pos, int w, int h) {
        try {
            NativeImage image = new NativeImage(w, h, false);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            String texName = "create_stats/dispatch_board_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(texName, dynamicTexture);
            TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynamicTexture = dynamicTexture;
            entry.textureLocation = location;
            entry.texWidth = w;
            entry.texHeight = h;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            return entry;
        } catch (Exception e) {
            CreateStats.LOGGER.warn("Failed to create Dispatch Board texture", e);
            return null;
        }
    }

    private static void updateTexture(TextureEntry entry, DispatchBoardBE be, long gameTime) {
        renderBoard(entry.image, be, gameTime);
        entry.dynamicTexture.upload();
    }

    private static void renderBoard(NativeImage image, DispatchBoardBE be, long gameTime) {
        currentFrameTime = gameTime;
        int w = image.getWidth();
        int h = image.getHeight();

        TextureGraphRenderer.fillRect(image, 0, 0, w, h, BG_DARK);

        int borderT = Math.max(2, Math.min(w, h) / 64);
        TextureGraphRenderer.drawBorder(image, 0, 0, w, h, borderT, BORDER_OUTER);
        TextureGraphRenderer.drawBorder(image, borderT, borderT, w - borderT * 2, h - borderT * 2, 1, BORDER_INNER);

        int margin = borderT + 2;
        int innerW = w - margin * 2;
        int innerH = h - margin * 2;
        TextureGraphRenderer.fillRect(image, margin, margin, innerW, innerH, BG_PANEL);
        TextureGraphRenderer.fillRect(image, margin, margin, innerW, 1, BRASS_ACCENT);
        TextureGraphRenderer.fillRect(image, margin, margin + innerH - 1, innerW, 1, BRASS_ACCENT);
        TextureGraphRenderer.fillRect(image, margin, margin, 1, innerH, BRASS_DIM);
        TextureGraphRenderer.fillRect(image, margin + innerW - 1, margin, 1, innerH, BRASS_DIM);

        DispatchBoardSnapshot snapshot = be.getBoardSnapshot();
        if (snapshot == null) {
            BitmapFont.drawCentered(image, "Initializing...", margin, margin + innerH / 2 - 4, innerW, TEXT_DIM, 1);
            return;
        }

        BoardTier tier = snapshot.getTier();
        int contentX = margin + 3;
        int contentY = margin + 3;
        int contentW = innerW - 6;
        int contentH = innerH - 6;

        if (!be.feConsumer().isPowered()) {
            renderNoPower(image, contentX, contentY, contentW, contentH);
            return;
        }

        final long showUntil = be.getManifestShowUntil();
        if (ManifestEgg.isShowing(gameTime, showUntil)) {
            renderManifestShow(image, contentX, contentY, contentW, contentH, gameTime, showUntil);
            return;
        }

        switch (tier) {
            case MINI -> renderMini(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case COMPACT -> renderCompact(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case STANDARD -> renderStandard(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case RICH -> renderRich(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
            case FULL -> renderFull(image, snapshot, contentX, contentY, contentW, contentH, gameTime);
        }
    }

    // unpowered says so instead of looking out of sync
    private static void renderNoPower(NativeImage image, int x, int y, int w, int h) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, BG_PANEL);
        int scale = Math.max(1, Math.min(w, h) / 190);
        int lineH = BitmapFont.textHeight(scale) + 5 * scale;
        int cy = y + h / 2 - lineH;
        BitmapFont.drawCentered(image, "NO POWER", x, cy, w, 0xFFFF6B5E, scale);
        cy += lineH;
        BitmapFont.drawCentered(image, "LIVE DATA PAUSED", x, cy, w, TEXT_PRIMARY, scale);
        cy += lineH;
        BitmapFont.drawCentered(image, "CONNECT FE TO RESUME", x, cy, w, TEXT_DIM, scale);
    }

    private static final int SHOW_SKY_TOP = 0xFF1E0F36;
    private static final int SHOW_SKY_LOW = 0xFF0C0718;
    private static final int SHOW_DECK_TOP = 0xFF2A1C3C;
    private static final int SHOW_DECK_LOW = 0xFF0D0914;
    private static final int SHOW_DECK_EDGE = 0xFF4A3A60;
    private static final int SHOW_TRUSS = 0xFF3A3446;
    private static final int SHOW_TRUSS_LIT = 0xFF5E5672;
    private static final int SHOW_LAMP = 0xFF151119;
    private static final int SHOW_BULB = 0xFF4A3C30;
    private static final int SHOW_BULB_LIT = 0xFFFFE6A8;
    private static final int SHOW_TITLE = 0xFFFFFFFF;
    private static final int SHOW_CAPTION = 0xFFBDB3D1;

    private static final float SHOW_BEAM_TOP = 0.34f;
    private static final float SHOW_BEAM_LOW = 0.12f;

    private static final float SHOW_POOL = 0.55f;
    private static final String SHOW_TITLE_TEXT = "MANIFEST";
    private static final String SHOW_CAPTION_TEXT = "SIX GIRLS. ONE MOTION.";
    private static final String SHOW_SHOUT_TEXT = "YAAA!";

    private static final int[] SHOW_ARRANGEMENTS = {6, 3, 2, 1};

    private static final int[] SHOW_DEPTH = {2, 1, 0, 0, 1, 2};

    private static final int[] SHOW_SPARKLE_X = {-12, 110, 18, 84, -6, 104};
    private static final int[] SHOW_SPARKLE_Y = {10, 0, -40, -34, 92, 96};

    // frame on the half beat of the game clock, not per board phase. two walls at one platform danced out of step
    private static boolean manifestFrameDue(DispatchBoardBE be, long lastDrawn, long now) {
        return ManifestEgg.isShowing(now, be.getManifestShowUntil())
                && Math.floorDiv(now, (long) ManifestEgg.HALF_BEAT_TICKS)
                        != Math.floorDiv(lastDrawn, (long) ManifestEgg.HALF_BEAT_TICKS);
    }

    private static void renderManifestShow(NativeImage image, int x, int y, int w, int h, long gameTime,
            long showUntil) {
        final int girls = ManifestEgg.CARRIAGES;
        final int frame = ManifestChoreography.frameAt(gameTime);
        final boolean onBeat = (frame & 1) == 0;
        final boolean downbeat = frame == 0;
        final boolean finale = ManifestEgg.isFinalBeat(gameTime, showUntil);
        final int lead = (int) Math.floorMod(Math.floorDiv(gameTime, (long) ManifestEgg.BEAT_TICKS), (long) girls);

        final int u = Math.max(1, Math.min(w, h) / 88);

        final int ts = Math.max(1, Math.min(w * 3 / 5 / BitmapFont.measureString(SHOW_TITLE_TEXT, 1), h / 80));
        final int titleY = y + u * 2;
        final int ruleY = titleY + BitmapFont.textHeight(ts) + u * 2;
        final int ruleH = Math.max(1, u / 2);

        int cs = Math.max(1, Math.min(ts / 2, (w - u * 4) / BitmapFont.measureString(SHOW_CAPTION_TEXT, 1)));
        if (BitmapFont.measureString(SHOW_CAPTION_TEXT, cs) > w - u * 2) {
            cs = 0;
        }
        final int captionY = y + h - u * 2 - (cs > 0 ? BitmapFont.textHeight(cs) : 0);

        final int trussY = ruleY + ruleH + u * 2;
        final int trussH = u;
        final int lampY = trussY + trussH;
        final int lampH = u * 2;
        final int beamY = lampY + lampH;
        final int deckBottom = captionY - u * 2;
        final int floorY = deckBottom - u * 3;

        final int pw = ManifestChoreography.WIDTH;
        final int ph = ManifestChoreography.HEIGHT;
        final int availW = w - u * 4;
        final int availH = (floorY - beamY) * 2 / 3;
        int cols = girls;
        int scale = 0;
        for (int c : SHOW_ARRANGEMENTS) {
            final int r = girls / c;
            final int s = Math.min(availW / (c * pw), availH / (r * ph + (r - 1) * 2));
            if (s > scale) {
                scale = s;
                cols = c;
            }
        }
        scale = Math.max(1, scale);
        final int rows = girls / cols;

        final int cellW = Math.min(availW / cols, pw * scale * 3 / 2);
        final int left = x + u * 2 + (availW - cellW * cols) / 2;
        final int[] dx = new int[girls];
        final int[] dy = new int[girls];
        final int[] footX = new int[girls];
        final int[] footY = new int[girls];
        int deckTop = Integer.MAX_VALUE;
        int headTop = Integer.MAX_VALUE;
        for (int i = 0; i < girls; i++) {
            final int col = i % cols;
            final int row = i / cols;
            dx[i] = left + col * cellW + (cellW - pw * scale) / 2;
            int fy = floorY - (rows - 1 - row) * (ph + 2) * scale;
            if (rows == 1) {
                fy -= SHOW_DEPTH[i] * scale;
            }
            footY[i] = fy;
            dy[i] = fy - ph * scale;
            footX[i] = dx[i] + pw * scale / 2;
            deckTop = Math.min(deckTop, fy);
            headTop = Math.min(headTop, dy[i]);
        }
        deckTop -= scale * 2;
        final int centre = x + w / 2;
        // six lamps, each aimed at its own girl

        final int lampPitch = Math.max(1, cellW * cols * 2 / (5 * girls));
        final int[] lampX = new int[girls];
        for (int i = 0; i < girls; i++) {
            lampX[i] = centre + (2 * i - (girls - 1)) * lampPitch / 2;
        }

        final float gain = finale ? 1.5f : downbeat ? 1.25f : onBeat ? 1.0f : 0.55f;
        final float lensHalf = Math.max(1.0f, u * 0.75f);
        final float footHalf = pw * scale * 0.5f;
        final float poolRx = pw * scale * 0.62f;
        final float poolRy = Math.max(1.5f, scale * 1.6f);
        final float poolGain = SHOW_POOL * Math.min(1.0f, gain);
        final int writeFrom = Math.max(0, x);
        final int writeTo = Math.min(image.getWidth(), x + w);
        final int rowsTo = Math.min(image.getHeight(), y + h);
        final float[] red = new float[w];
        final float[] green = new float[w];
        final float[] blue = new float[w];
        for (int py = Math.max(0, y); py < rowsTo; py++) {
            final int base = py < deckTop
                    ? showMix(SHOW_SKY_TOP, SHOW_SKY_LOW, (py - y) / (float) Math.max(1, deckTop - y))
                    : showMix(SHOW_DECK_TOP, SHOW_DECK_LOW, (py - deckTop) / (float) Math.max(1, y + h - deckTop));
            java.util.Arrays.fill(red, base >> 16 & 0xFF);
            java.util.Arrays.fill(green, base >> 8 & 0xFF);
            java.util.Arrays.fill(blue, base & 0xFF);
            final float cy = py + 0.5f;
            for (int i = 0; i < girls; i++) {
                final int colour = ManifestEgg.COLOURS[i];
                final float cr = colour >> 16 & 0xFF;
                final float cg = colour >> 8 & 0xFF;
                final float cb = colour & 0xFF;
                if (py >= beamY && py < footY[i]) {
                    final float p = (cy - beamY) / (footY[i] - beamY);
                    final float cx = lampX[i] + (footX[i] - lampX[i]) * p;
                    final float hw = lensHalf + (footHalf - lensHalf) * p;
                    final float strength = gain * (SHOW_BEAM_TOP + (SHOW_BEAM_LOW - SHOW_BEAM_TOP) * p);
                    final int from = Math.max(x, (int) Math.floor(cx - hw));
                    final int to = Math.min(x + w, (int) Math.ceil(cx + hw));
                    for (int px = from; px < to; px++) {
                        final float edge = (1.0f - Math.abs(px + 0.5f - cx) / hw) / 0.45f;
                        final float a = strength * Math.max(0.0f, Math.min(1.0f, edge));
                        red[px - x] += cr * a;
                        green[px - x] += cg * a;
                        blue[px - x] += cb * a;
                    }
                }
                final float pdy = (cy - footY[i]) / poolRy;
                if (Math.abs(pdy) < 1.0f) {
                    final float span = poolRx * (float) Math.sqrt(1.0f - pdy * pdy);
                    final int from = Math.max(x, (int) Math.floor(footX[i] - span));
                    final int to = Math.min(x + w, (int) Math.ceil(footX[i] + span));
                    for (int px = from; px < to; px++) {
                        final float pdx = (px + 0.5f - footX[i]) / poolRx;
                        final float a = poolGain * Math.max(0.0f, Math.min(1.0f, 1.0f - (pdx * pdx + pdy * pdy)));
                        red[px - x] += cr * a;
                        green[px - x] += cg * a;
                        blue[px - x] += cb * a;
                    }
                }
            }
            for (int px = writeFrom; px < writeTo; px++) {
                final int r = Math.min(255, (int) red[px - x]);
                final int g = Math.min(255, (int) green[px - x]);
                final int b = Math.min(255, (int) blue[px - x]);
                image.setPixelRGBA(px, py, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }

        TextureGraphRenderer.fillRect(image, x, deckTop, w, Math.max(1, scale / 2), SHOW_DECK_EDGE);
        final int bulb = Math.max(1, u);
        final int bulbPitch = bulb * 3;
        final int bulbs = (w - u * 2) / bulbPitch;
        final int bulbX = x + (w - (bulbs * bulbPitch - bulb * 2)) / 2;
        for (int k = 0; k < bulbs; k++) {
            final boolean lit = finale || (k + frame) % 3 == 0;
            TextureGraphRenderer.fillRect(image, bulbX + k * bulbPitch, deckBottom - bulb, bulb, bulb,
                    lit ? SHOW_BULB_LIT : SHOW_BULB);
        }

        TextureGraphRenderer.fillRect(image, x + u, trussY, w - u * 2, trussH, SHOW_TRUSS);
        TextureGraphRenderer.fillRect(image, x + u, trussY, w - u * 2, Math.max(1, trussH / 2), SHOW_TRUSS_LIT);
        final int lensH = Math.max(1, u / 2);
        for (int i = 0; i < girls; i++) {
            TextureGraphRenderer.fillRect(image, lampX[i] - u, lampY, u * 2 + 1, lampH, SHOW_LAMP);
            final int lens = brightenColor(0xFF000000 | ManifestEgg.COLOURS[i],
                    finale ? 0.85f : onBeat ? 0.55f : 0.15f);
            TextureGraphRenderer.fillRect(image, lampX[i] - u + 1, lampY + lampH - lensH - 1, u * 2 - 1, lensH, lens);
        }

        final String[] pose = ManifestChoreography.poseAt(gameTime);
        final int eyeDot = Math.max(1, scale / 2);
        final int eyeOff = (scale - eyeDot) / 2;
        for (int i = 0; i < girls; i++) {
            final int outfit = 0xFF000000 | ManifestEgg.COLOURS[i];
            final int shade = scaleColor(outfit, 0.62f);
            final int hair = 0xFF000000 | ManifestChoreography.HAIR[i];
            for (int r = 0; r < pose.length; r++) {
                final String line = pose[r];
                for (int c = 0; c < line.length(); c++) {
                    final int ink = showInk(line.charAt(c), outfit, shade, hair);
                    if (ink == 0) {
                        continue;
                    }
                    final int px = dx[i] + c * scale;
                    final int py = dy[i] + r * scale;
                    TextureGraphRenderer.fillRect(image, px, py, scale, scale, ink);
                    if (line.charAt(c) == 'e') {
                        TextureGraphRenderer.fillRect(image, px + eyeOff, py + eyeOff, eyeDot, eyeDot,
                                0xFF000000 | ManifestChoreography.EYE);
                    }
                }
            }
        }

        final int tw = BitmapFont.measureString(SHOW_TITLE_TEXT, ts);
        final int tx = x + (w - tw) / 2;
        final int shadow = Math.max(1, ts / 2);
        BitmapFont.drawString(image, SHOW_TITLE_TEXT, tx + shadow, titleY + shadow,
                scaleColor(0xFF000000 | ManifestEgg.COLOURS[lead], 0.8f), ts);
        BitmapFont.drawString(image, SHOW_TITLE_TEXT, tx, titleY, SHOW_TITLE, ts);
        final int segment = Math.max(1, tw / girls);
        final int ruleX = x + (w - segment * girls) / 2;
        for (int i = 0; i < girls; i++) {
            TextureGraphRenderer.fillRect(image, ruleX + i * segment, ruleY, segment, ruleH,
                    0xFF000000 | ManifestEgg.COLOURS[i]);
        }
        if (cs > 0) {
            BitmapFont.drawCentered(image, SHOW_CAPTION_TEXT, x, captionY, w, SHOW_CAPTION, cs);
        }

        if (finale) {
            final int headRoom = headTop - beamY - u * 2;
            final int ys = Math.max(1, Math.min(Math.min((w - u * 8) / BitmapFont.measureString(SHOW_SHOUT_TEXT, 1),
                    headRoom / 7), ts * 2 + 1));
            final int sw = BitmapFont.measureString(SHOW_SHOUT_TEXT, ys);
            final int sx = x + (w - sw) / 2;
            final int sy = beamY + Math.max(0, (headRoom - BitmapFont.textHeight(ys)) / 2);
            final int o = Math.max(1, ys / 3);
            final int edgeColour = 0xFF000000 | ManifestEgg.COLOURS[lead];
            for (int ox = -o; ox <= o; ox += o) {
                for (int oy = -o; oy <= o; oy += o) {
                    if (ox != 0 || oy != 0) {
                        BitmapFont.drawString(image, SHOW_SHOUT_TEXT, sx + ox, sy + oy, edgeColour, ys);
                    }
                }
            }
            BitmapFont.drawString(image, SHOW_SHOUT_TEXT, sx, sy, SHOW_TITLE, ys);
            final int arm = Math.max(1, ys / 2) * ((frame & 1) == 1 ? 2 : 1);
            final int bar = Math.max(1, ys / 3);
            for (int i = 0; i < girls; i++) {
                final int spx = Math.max(x + arm, Math.min(x + w - arm - 1, sx + SHOW_SPARKLE_X[i] * sw / 100));
                final int spy = Math.max(beamY + arm,
                        Math.min(headTop - arm - 1, sy + SHOW_SPARKLE_Y[i] * BitmapFont.textHeight(ys) / 100));
                drawSparkle(image, spx, spy, arm, bar, 0xFF000000 | ManifestEgg.COLOURS[i], SHOW_TITLE);
            }
        }
    }

    private static int showInk(char c, int outfit, int shade, int hair) {
        return switch (c) {
            case 'k', 'e' -> 0xFF000000 | ManifestChoreography.SKIN;
            case 'h' -> hair;
            case 'c' -> outfit;
            case 'd' -> shade;
            case 'w' -> 0xFF000000 | ManifestChoreography.WHITE;
            default -> 0;
        };
    }

    private static int scaleColor(int argb, float f) {
        final int r = Math.min(255, (int) ((argb >> 16 & 0xFF) * f));
        final int g = Math.min(255, (int) ((argb >> 8 & 0xFF) * f));
        final int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int showMix(int a, int b, float f) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final int ca = a >> shift & 0xFF;
            final int cb = b >> shift & 0xFF;
            out |= (int) (ca + (cb - ca) * f) << shift;
        }
        return out;
    }

    private static void drawSparkle(NativeImage image, int cx, int cy, int arm, int bar, int colour, int core) {
        TextureGraphRenderer.fillRect(image, cx - arm, cy - bar / 2, arm * 2 + 1, bar, colour);
        TextureGraphRenderer.fillRect(image, cx - bar / 2, cy - arm, bar, arm * 2 + 1, colour);
        TextureGraphRenderer.fillRect(image, cx - bar / 2, cy - bar / 2, bar, bar, core);
    }

    private static void renderMini(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = 1;
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH");
            return;
        }

        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;

        int barW = 3;
        TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 2, HEADER_BG);
        TextureGraphRenderer.fillRect(image, x, cy, barW, lineH + 2, indicatorColor);
        String stationName = truncate(snap.getStationName(), w / (6 * scale) - 4);
        BitmapFont.drawString(image, stationName, x + barW + 2, cy + 1, TEXT_BRIGHT, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy + 1, indicatorColor, scale);
        cy += lineH + 3;

        TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
        cy += 2;
        if (snap.isTrainPresent()) {
            String tName = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), w / (6 * scale) - 6);
            BitmapFont.drawString(image, tName, x + 1, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", x + 1, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", x + 1, cy, TEXT_DIM, scale);
        }
        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, cy, indicatorColor, scale);
        cy += lineH + 1;

        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int barH = 4;
        int rowSpace = y + h - cy - barH - 3;
        int maxRows = Math.min(rows.size(), rowSpace / (lineH + 1));
        for (int i = 0; i < maxRows; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 1, cy, w - 2, lineH, scale, false,
                    i == 0 && !rows.get(i).getLineView().isComplete());
            cy += lineH + 1;
        }

        if (rows.isEmpty() && snap.getTotalRowCount() == 0) {
            BitmapFont.drawCentered(image, "No orders", x, cy, w, TEXT_DIM, scale);
            cy += lineH;
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
            cy += lineH;
        }

        drawProgressBar(image, snap, x + 1, y + h - barH - 1, w - 2, barH);
    }

    private static void renderCompact(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 160);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH BOARD");
            return;
        }

        int headerH = lineH + 4;
        drawHeaderBar(image, snap, x, cy, w, headerH, scale);
        cy += headerH + 1;

        drawSummaryStrip(image, snap, x, cy, w, lineH, scale);
        cy += lineH + 1;

        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int bottomReserved = hasExc ? lineH + 3 : 0;
        int barH = 4;
        int rowEndY = y + h - bottomReserved - barH - 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        for (int i = 0; i < rows.size() && cy + lineH < rowEndY; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true,
                    i == 0 && !rows.get(i).getLineView().isComplete());
            cy += lineH + 1;
        }
        if (rows.isEmpty()) {
            BitmapFont.drawCentered(image, "No orders yet", x, cy, w, TEXT_DIM, scale);
            cy += lineH;
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
        }

        drawProgressBar(image, snap, x + 1, y + h - bottomReserved - barH - 1, w - 2, barH);

        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - lineH - 2, w, lineH + 1, scale);
        }
    }

    private static void renderStandard(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 192);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "DISPATCH BOARD");
            return;
        }

        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH + 1;

        drawSummaryStrip(image, snap, x, cy, w, lineH, scale);
        cy += lineH + 1;

        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_ACCENT);
        cy += 2;

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 3 : 0;

        int cargoEntryCount = Math.min(snap.getCargoSummary().size(), snap.getLayout().getCargoSummarySlots());
        int cargoH = 0;
        if (snap.getLayout().hasCargoRegion() && cargoEntryCount > 0) {
            cargoH = lineH * (cargoEntryCount + 1) + 6;
        }
        int histEntryCount = Math.min(snap.getVisibleHistory().size(), snap.getLayout().getHistoryEntryCapacity());
        int histH = 0;
        if (snap.getLayout().hasHistoryRegion() && histEntryCount > 0) {
            histH = lineH * (histEntryCount + 1) + 6;
        }
        int bottomH = Math.max(cargoH, histH);
        int rowEndY = y + h - excH - bottomH - 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        for (int i = 0; i < rows.size() && cy + lineH < rowEndY; i++) {
            int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
            TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
            drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true,
                    i == 0 && !rows.get(i).getLineView().isComplete());
            cy += lineH + 1;
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
            cy += 1;
        }
        if (rows.isEmpty()) {
            BitmapFont.drawCentered(image, "No dispatch orders", x, cy + lineH, w, TEXT_DIM, scale);
        } else if (snap.hasOverflowRows()) {
            BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rows.size()) + " more", x + w - 1, cy, TEXT_DIM, scale);
        }

        if (bottomH > 0) {
            cy = y + h - excH - bottomH;
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
            cy += 2;
            if (cargoH > 0 && histH > 0) {
                int halfW = (w - 4) / 2;
                drawCargoSummary(image, snap, x, cy, halfW, bottomH - 3, scale);
                drawHistory(image, snap, x + halfW + 4, cy, halfW, bottomH - 3, scale);
            } else if (cargoH > 0) {
                drawCargoSummary(image, snap, x, cy, w, bottomH - 3, scale);
            } else {
                drawHistory(image, snap, x, cy, w, bottomH - 3, scale);
            }
        }

        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - lineH - 2, w, lineH + 1, scale);
        }
    }

    private static void renderRich(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 192);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "STATION DISPATCH BOARD");
            return;
        }

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 4 : 0;
        int progressH = 5;

        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH;
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int trainPanelH = drawTrainDetailPanel(image, snap, x, cy, w, lineH, scale);
        cy += trainPanelH;
        if (trainPanelH > 0) {
            TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_ACCENT);
            cy += 3;
        }

        drawPixelIcon(image, ICON_CARGO, x + 2, cy + 1, SECTION_HEADER, scale);
        int iconW = 7 * scale + 4;
        BitmapFont.drawString(image, "ORDERS (" + snap.getTotalRowCount() + ")", x + 2 + iconW, cy, SECTION_HEADER, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_DIM, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int bottomReserved = excH + progressH + 6;
        int widgetMinH = lineH * 5;
        int maxRowPixels = (y + h) - cy - bottomReserved - widgetMinH;
        int maxRows = Math.max(1, maxRowPixels / (lineH + 2));
        int rowsToDraw = Math.min(rows.size(), maxRows);

        if (rows.isEmpty()) {
            BitmapFont.drawString(image, "No dispatch orders", x + 4, cy, TEXT_DIM, scale);
            cy += lineH + 2;
        } else {
            for (int i = 0; i < rowsToDraw; i++) {
                int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
                drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true,
                        i == 0 && !rows.get(i).getLineView().isComplete());
                cy += lineH + 1;
                TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
                cy += 1;
            }
            if (snap.hasOverflowRows()) {
                BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rowsToDraw) + " more",
                        x + w - 1, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int widgetEndY = y + h - excH - progressH - 2;
        int widgetH = widgetEndY - cy;
        int thirdW = (w - 10) / 3;

        drawSectionWithIcon(image, "CARGO", ICON_CARGO, x, cy, thirdW, widgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawCargoContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + thirdW + 3, cy, 1, widgetH, BRASS_DIM);
        drawSectionWithIcon(image, "HISTORY", ICON_CLOCK, x + thirdW + 6, cy, thirdW, widgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawHistoryContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + thirdW * 2 + 7, cy, 1, widgetH, BRASS_DIM);

        if (snap.getLayout().hasRosterRegion() && !snap.getRosterEntries().isEmpty()) {
            drawSectionWithIcon(image, "TRAINS", ICON_TRAIN, x + thirdW * 2 + 10, cy, thirdW, widgetH, scale,
                    (img, sx, sy, sw, sh, sc) -> drawTrainRosterContent(img, snap, sx, sy, sw, sh, sc));
        } else {
            drawSectionWithIcon(image, "VISITS", ICON_TRACKS, x + thirdW * 2 + 10, cy, thirdW, widgetH, scale,
                    (img, sx, sy, sw, sh, sc) -> drawVisitsContent(img, snap, sx, sy, sw, sh, sc));
        }

        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - excH - progressH - 1, w, lineH + 2, scale);
        }

        drawProgressBar(image, snap, x + 1, y + h - progressH - 1, w - 2, progressH);
    }

    private static void renderFull(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, long gameTime) {
        int scale = Math.max(1, Math.min(w, h) / 256);
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            renderEmptyState(image, snap, x, y, w, h, scale, "STATION DISPATCH BOARD");
            return;
        }

        boolean hasExc = snap.getLayout().hasExceptionStrip() && snap.hasExceptions();
        int excH = hasExc ? lineH + 4 : 0;
        int progressH = 6;

        int headerH = lineH * 2 + 6;
        drawHeaderZone(image, snap, x, cy, w, headerH, scale);
        cy += headerH;
        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int trainPanelH = drawTrainDetailPanel(image, snap, x, cy, w, lineH, scale);
        cy += trainPanelH;
        if (trainPanelH > 0) {
            TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
            cy += 4;
        }

        drawPixelIcon(image, ICON_CARGO, x + 2, cy + 1, SECTION_HEADER, scale);
        int iconW = 7 * scale + 4;
        BitmapFont.drawString(image, "ORDERS (" + snap.getTotalRowCount() + ")", x + 2 + iconW, cy, SECTION_HEADER, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_DIM, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DispatchRowEntry> rows = snap.getVisibleRows();
        int bottomReserved = excH + progressH + 8;
        int widgetMinH = lineH * 6;
        int maxRowPixels = (y + h) - cy - bottomReserved - widgetMinH;
        int maxRows = Math.max(1, maxRowPixels / (lineH + 2));
        int rowsToDraw = Math.min(rows.size(), maxRows);

        if (rows.isEmpty()) {
            BitmapFont.drawString(image, "No dispatch orders", x + 4, cy, TEXT_DIM, scale);
            cy += lineH + 2;
        } else {
            for (int i = 0; i < rowsToDraw; i++) {
                int rowBg = (i % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
                TextureGraphRenderer.fillRect(image, x, cy, w, lineH + 1, rowBg);
                drawDispatchRow(image, rows.get(i), x + 2, cy, w - 4, lineH, scale, true,
                        i == 0 && !rows.get(i).getLineView().isComplete());
                cy += lineH + 1;
                TextureGraphRenderer.fillRect(image, x, cy, w, 1, ROW_SEPARATOR);
                cy += 1;
            }
            if (snap.hasOverflowRows()) {
                BitmapFont.drawRight(image, "+" + (snap.getTotalRowCount() - rowsToDraw) + " more",
                        x + w - 1, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        TextureGraphRenderer.fillRect(image, x, cy, w, 2, BRASS_ACCENT);
        cy += 4;

        int widgetEndY = y + h - excH - progressH - 2;
        int widgetTotalH = widgetEndY - cy;
        int halfW = (w - 6) / 2;
        int halfH = (widgetTotalH - 4) / 2;

        drawSectionWithIcon(image, "CARGO", ICON_CARGO, x, cy, halfW, halfH, scale,
                (img, sx, sy, sw, sh, sc) -> drawCargoContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + halfW + 2, cy, 1, halfH, BRASS_DIM);
        drawSectionWithIcon(image, "TRAINS", ICON_TRAIN, x + halfW + 5, cy, halfW, halfH, scale,
                (img, sx, sy, sw, sh, sc) -> drawTrainRosterContent(img, snap, sx, sy, sw, sh, sc));

        cy += halfH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        int bottomWidgetH = widgetEndY - cy;
        drawSectionWithIcon(image, "HISTORY", ICON_CLOCK, x, cy, halfW, bottomWidgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawHistoryContent(img, snap, sx, sy, sw, sh, sc));
        TextureGraphRenderer.fillRect(image, x + halfW + 2, cy, 1, bottomWidgetH, BRASS_DIM);
        drawSectionWithIcon(image, "VISITS", ICON_TRACKS, x + halfW + 5, cy, halfW, bottomWidgetH, scale,
                (img, sx, sy, sw, sh, sc) -> drawVisitsContent(img, snap, sx, sy, sw, sh, sc));

        if (hasExc) {
            drawExceptionStrip(image, snap, x, y + h - excH - progressH - 1, w, lineH + 2, scale);
        }

        drawProgressBar(image, snap, x + 1, y + h - progressH - 1, w - 2, progressH);
    }

    private static void renderEmptyState(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale, String title) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int centerY = y + h / 2;

        int gridSpacing = 24 * scale;
        for (int gx = x + gridSpacing; gx < x + w; gx += gridSpacing) {
            for (int gy = y; gy < y + h; gy++) {
                if (gx >= 0 && gx < image.getWidth() && gy >= 0 && gy < image.getHeight()) {
                    image.setPixelRGBA(gx, gy, 0xFF2A2520);
                }
            }
        }
        for (int gy = y + gridSpacing; gy < y + h; gy += gridSpacing) {
            for (int gx = x; gx < x + w; gx++) {
                if (gx >= 0 && gx < image.getWidth() && gy >= 0 && gy < image.getHeight()) {
                    image.setPixelRGBA(gx, gy, 0xFF2A2520);
                }
            }
        }

        BitmapFont.drawCentered(image, title, x, centerY - lineH * 2, w, BRASS_ACCENT, scale);

        BitmapFont.drawCentered(image, "Link to station", x, centerY - lineH / 2, w, TEXT_DIM, scale);
        BitmapFont.drawCentered(image, "with clipboard", x, centerY + lineH, w, TEXT_DIM, scale);
    }

    private static void drawProgressBar(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xFF3A3228);

        if (snap.getTotalRowCount() > 0) {
            float pct = Math.min(1.0f, (float) snap.getCompletedRowCount() / snap.getTotalRowCount());
            int fillW = Math.round(w * pct);
            if (fillW > 0) {
                int barColor = pct >= 1.0f ? 0xFF74D87F : 0xFFFFCC00;
                TextureGraphRenderer.fillRect(image, x, y, fillW, h, barColor);
                if (pct < 1.0f) {
                    drawBarSheen(image, x, y, fillW, h, barColor, 1.4f);
                }
            }
        }

        TextureGraphRenderer.fillRect(image, x, y, w, 1, BRASS_DIM);
    }

    private static void drawHeaderZoneFull(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;
        int lineH = BitmapFont.textHeight(scale) + 2;

        int barW = Math.max(4, 5 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textX = x + barW + 3;
        int cy = y + 2;
        int maxChars = (w - barW - 6) / (6 * scale);

        String stationName = truncate(snap.getStationName(), maxChars);
        BitmapFont.drawString(image, stationName, textX, cy, TEXT_BRIGHT, scale);
        cy += lineH;

        if (snap.isTrainPresent()) {
            String trainLabel = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), maxChars / 2);
            BitmapFont.drawString(image, trainLabel, textX, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", textX, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", textX, cy, TEXT_DIM, scale);
        }
        cy += lineH;

        BitmapFont.drawString(image, snap.getHeaderState().getDisplayName(), textX, cy, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, cy, TEXT_PRIMARY, scale);

        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, y + 2, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, y + 2 + lineH, TEXT_PRIMARY, scale);
    }

    private static int drawTrainDetailPanel(NativeImage image, DispatchBoardSnapshot snap,
                                             int x, int y, int w, int lineH, int scale) {
        if (!snap.isTrainPresent() && !snap.isArrivalTracking()) {
            TextureGraphRenderer.fillRect(image, x, y, w, lineH + 4, TRAIN_PANEL_BG);
            drawPixelIcon(image, ICON_TRAIN, x + 4, y + 2, TEXT_DIM, scale);
            int iconW = 9 * scale + 6;
            BitmapFont.drawString(image, "Awaiting train", x + iconW, y + 2, TEXT_DIM, scale);
            return lineH + 6;
        }

        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        TrainSummarySnapshot train = !roster.isEmpty() ? roster.get(0) : null;

        int cy = y;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        int panelStartY = cy;

        int trainColor = snap.isTrainPresent() ? TRAIN_DOCKED_COLOR : TRAIN_ENROUTE_COLOR;
        drawPixelIcon(image, ICON_TRAIN, x + 4, cy + 1, trainColor, scale);
        int iconW = 9 * scale + 6;
        String trainName = snap.getTrainName().isEmpty() ? "Train" : snap.getTrainName();
        BitmapFont.drawString(image, trainName, x + iconW, cy, trainColor, scale);
        if (train != null && train.isWaitingForSignal()) {
            BitmapFont.drawRight(image, "SIGNAL WAIT", x + w - 1, cy, pulseColor(TRAIN_SIGNAL_COLOR, 0.6f, 0.5f), scale);
        } else if (snap.isTrainPresent()) {
            BitmapFont.drawRight(image, "DOCKED", x + w - 1, cy, pulseColor(TRAIN_DOCKED_COLOR, 0.3f, 0.4f), scale);
        } else {
            BitmapFont.drawRight(image, "ARRIVING", x + w - 1, cy, pulseColor(TRAIN_ENROUTE_COLOR, 0.5f, 0.5f), scale);
        }
        cy += lineH + 1;

        if (train != null) {
            int cx = x + iconW;
            cx += BitmapFont.drawString(image, train.getCarriageCount() + " cars", cx, cy, TEXT_PRIMARY, scale);
            cx += 4 * scale;
            TextureGraphRenderer.fillRect(image, cx, cy + 2, 1, lineH - 4, BRASS_DIM);
            cx += 4 * scale;
            String speedStr = String.format("%.1f m/t", train.getSpeed());
            cx += BitmapFont.drawString(image, speedStr, cx, cy, TEXT_PRIMARY, scale);
            if (!train.getNextDestination().isEmpty()) {
                cx += 4 * scale;
                TextureGraphRenderer.fillRect(image, cx, cy + 2, 1, lineH - 4, BRASS_DIM);
                cx += 4 * scale;
                BitmapFont.drawString(image, "> " + truncate(train.getNextDestination(), 16), cx, cy, TRAIN_ENROUTE_COLOR, scale);
            }
            cy += lineH;

            if (!train.getCurrentStationName().isEmpty()) {
                BitmapFont.drawString(image, "At: " + truncate(train.getCurrentStationName(), 24), x + iconW, cy, TEXT_DIM, scale);
                cy += lineH;
            }

            List<String> stops = train.getScheduleStops();
            if (!stops.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int maxChars = (w - iconW - 4) / (6 * scale);
                for (int i = 0; i < stops.size(); i++) {
                    String stop = stops.get(i);
                    String addition = (i == 0 ? "" : " > ") + stop;
                    if (sb.length() + addition.length() > maxChars) {
                        sb.append(" (+").append(stops.size() - i).append(")");
                        break;
                    }
                    sb.append(addition);
                }
                BitmapFont.drawString(image, sb.toString(), x + iconW, cy, TEXT_DIM, scale);
                cy += lineH;
            }
        }

        cy += 2;

        int panelH = cy - panelStartY;

        TextureGraphRenderer.fillRect(image, x, panelStartY, 3 * scale, panelH, pulseColor(trainColor, 0.4f, 0.35f));

        return cy - y;
    }

    @FunctionalInterface
    private interface SectionContentRenderer {
        void render(NativeImage image, int x, int y, int w, int h, int scale);
    }

    private static void drawSectionWithIcon(NativeImage image, String title, boolean[][] icon,
                                             int x, int y, int w, int h, int scale,
                                             SectionContentRenderer contentRenderer) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        int iconW = icon[0].length * scale;
        drawPixelIcon(image, icon, x + 2, cy + 1, SECTION_HEADER, scale);
        BitmapFont.drawString(image, title, x + iconW + 5, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        int contentH = y + h - cy;
        if (contentH > 0) {
            contentRenderer.render(image, x, cy, w, contentH, scale);
        }
    }

    private static void drawCargoContent(NativeImage image, DispatchBoardSnapshot snap,
                                          int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<CargoSummaryEntry> entries = snap.getCargoSummary();
        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No cargo", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            CargoSummaryEntry entry = entries.get(i);
            String label = truncate(entry.getCompactLabel(), w / (6 * scale));
            BitmapFont.drawString(image, label, x + 2, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawHistoryContent(NativeImage image, DispatchBoardSnapshot snap,
                                            int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<DeliveryOrderHistoryEntry> entries = snap.getVisibleHistory();
        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No events", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            DeliveryOrderHistoryEntry entry = entries.get(i);
            int typeColor = switch (entry.getType()) {
                case COMPLETED, MANUAL_COMPLETE -> 0xFF74D87F;
                case AUTO_RESET, MANUAL_RESET -> 0xFF9999FF;
                case HIDDEN -> TEXT_DIM;
                case SHOWN -> TEXT_PRIMARY;
            };
            int dotSize = Math.max(3, 4 * scale);
            TextureGraphRenderer.fillRect(image, x + 2, cy + 2, dotSize, dotSize, typeColor);
            String title = truncate(entry.getTitle(), (w - dotSize - 6) / (6 * scale));
            BitmapFont.drawString(image, title, x + dotSize + 5, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawTrainRosterContent(NativeImage image, DispatchBoardSnapshot snap,
                                                int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        if (roster.isEmpty()) {
            BitmapFont.drawString(image, "No trains", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < roster.size() && cy + lineH * 2 < y + h; i++) {
            TrainSummarySnapshot train = roster.get(i);
            int trainColor = train.isStoppedAtStation() ? TRAIN_DOCKED_COLOR
                    : train.isInboundToStation() ? TRAIN_ENROUTE_COLOR
                    : train.isWaitingForSignal() ? TRAIN_SIGNAL_COLOR : TEXT_PRIMARY;

            drawPixelIcon(image, ICON_TRAIN, x + 2, cy + 1, trainColor, Math.max(1, scale - 1));
            int miniIconW = 9 * Math.max(1, scale - 1) + 4;
            String state = train.isStoppedAtStation() ? "DOCKED "
                    : train.isInboundToStation() ? "NEXT " : "";
            String trainName = truncate(state + train.getTrainName(),
                    (w - miniIconW - 2) / (6 * scale));
            BitmapFont.drawString(image, trainName, x + miniIconW + 2, cy, trainColor, scale);
            cy += lineH;

            String detail = train.getCarriageCount() + " cars | " + trainCargoBrief(train);
            BitmapFont.drawString(image, detail, x + miniIconW + 2, cy, TEXT_DIM, scale);
            cy += lineH + 1;
        }
    }

    private static void drawVisitsContent(NativeImage image, DispatchBoardSnapshot snap,
                                           int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;
        List<TrainVisitRecord> visits = snap.getVisibleVisits();
        if (visits.isEmpty()) {
            BitmapFont.drawString(image, "No visits", x + 2, cy, TEXT_DIM, scale);
            return;
        }
        for (int i = 0; i < visits.size() && cy + lineH < y + h; i++) {
            TrainVisitRecord visit = visits.get(i);
            int visitColor = visit.isStillPresent() ? TRAIN_DOCKED_COLOR : TEXT_DIM;
            String bullet = visit.isStillPresent() ? ">" : "-";
            BitmapFont.drawString(image, bullet, x + 2, cy, visitColor, scale);
            String label = truncate(visit.getTrainName(), (w - 8 * scale) / (6 * scale));
            BitmapFont.drawString(image, label, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }
    }

    private static void drawHeaderBar(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;

        int barW = Math.max(2, 3 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int textX = x + barW + 2;
        int maxChars = (w - barW - 4) / (6 * scale);
        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            BitmapFont.drawString(image, "Unlinked", textX, textY, TEXT_DIM, scale);
        } else {
            String compLabel = snap.getCompletionLabel();
            int compW = BitmapFont.measureString(compLabel, scale);
            int nameMax = (w - barW - compW - 8) / (6 * scale);
            String name = truncate(snap.getStationName(), Math.max(6, nameMax));
            BitmapFont.drawString(image, name, textX, textY, TEXT_BRIGHT, scale);
            BitmapFont.drawRight(image, compLabel, x + w - 1, textY, indicatorColor, scale);
        }
    }

    private static void drawHeaderZone(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, HEADER_BG);
        int indicatorColor = snap.getHeaderState().getIndicatorColor() | 0xFF000000;
        int lineH = BitmapFont.textHeight(scale) + 2;

        int barW = Math.max(3, 4 * scale);
        TextureGraphRenderer.fillRect(image, x, y, barW, h, indicatorColor);

        int textX = x + barW + 3;
        int cy = y + 2;
        int maxChars = (w - barW - 6) / (6 * scale);

        if (snap.getHeaderState() == BoardHeaderState.UNLINKED) {
            BitmapFont.drawString(image, "Dispatch Board", textX, cy, BRASS_ACCENT, scale);
            cy += lineH;
            BitmapFont.drawString(image, "Not linked to a station", textX, cy, TEXT_DIM, scale);
            return;
        }

        String stationName = truncate(snap.getStationName(), maxChars);
        BitmapFont.drawString(image, stationName, textX, cy, TEXT_BRIGHT, scale);

        cy += lineH;
        if (snap.isTrainPresent()) {
            String trainLabel = snap.getTrainName().isEmpty() ? "Train present" : truncate(snap.getTrainName(), maxChars / 2);
            BitmapFont.drawString(image, trainLabel, textX, cy, 0xFF74D87F, scale);
        } else if (snap.isArrivalTracking()) {
            BitmapFont.drawString(image, "Arriving...", textX, cy, 0xFF55CCFF, scale);
        } else {
            BitmapFont.drawString(image, "No train", textX, cy, TEXT_DIM, scale);
        }

        BitmapFont.drawRight(image, snap.getHeaderState().getDisplayName(), x + w - 1, y + 2, indicatorColor, scale);
        BitmapFont.drawRight(image, snap.getCompletionLabel(), x + w - 1, y + 2 + lineH, TEXT_PRIMARY, scale);
    }

    private static void drawSummaryStrip(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int cx = x + 1;
        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int gap = 4 * scale;

        int active = snap.countRowsInState(DispatchRowState.IN_PROGRESS);
        int waiting = snap.countRowsInState(DispatchRowState.WAITING);
        int complete = snap.countRowsInState(DispatchRowState.COMPLETE);
        int invalid = snap.countRowsInState(DispatchRowState.INVALID) + snap.countRowsInState(DispatchRowState.NEEDS_SETUP);

        if (active > 0) {
            cx += BitmapFont.drawString(image, active + " active", cx, textY, 0xFFFFCC00, scale) + gap;
        }
        if (waiting > 0) {
            cx += BitmapFont.drawString(image, waiting + " wait", cx, textY, TEXT_DIM, scale) + gap;
        }
        if (complete > 0) {
            cx += BitmapFont.drawString(image, complete + " done", cx, textY, 0xFF74D87F, scale) + gap;
        }
        if (invalid > 0) {
            BitmapFont.drawString(image, invalid + " err", cx, textY, 0xFFFF5555, scale);
        }
    }

    private static void drawDispatchRow(NativeImage image, DispatchRowEntry row, int x, int y, int w, int h,
                                        int scale, boolean showDetail, boolean next) {
        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int stateColor = row.getStateColor() | 0xFF000000;
        int dotSize = Math.max(3, 4 * scale);

        TextureGraphRenderer.fillRect(image, x, textY + 1, dotSize, dotSize, stateColor);
        int cx = x + dotSize + 2 * scale;

        if (next) {
            cx += BitmapFont.drawString(image, ">", cx, textY, BRASS_ACCENT, scale);
            cx += scale;
        }

        String opLabel = row.getOperation() == ManifestOperation.LOAD ? "L" : "U";
        int opColor = row.getOperation() == ManifestOperation.LOAD ? 0xFF88AAFF : 0xFFFFAA66;
        cx += BitmapFont.drawString(image, opLabel, cx, textY, opColor, scale);
        cx += scale;

        int rightReserved = showDetail ? 50 * scale : 24 * scale;
        int nameSpace = w - (cx - x) - rightReserved;
        int maxNameChars = Math.max(4, nameSpace / (6 * scale));
        String cargoName = truncate(row.getDisplayName(), maxNameChars);
        cx += BitmapFont.drawString(image, cargoName, cx, textY, TEXT_PRIMARY, scale);
        cx += 3 * scale;

        if (row.getTargetAmount() > 0) {
            int progressTextW = BitmapFont.measureString(row.getDeliveredAmount() + "/" + row.getTargetAmount(), scale);
            int badgeW = 0;
            if (showDetail && !row.getResetBadge().isEmpty()) {
                badgeW = BitmapFont.measureString(row.getResetBadge(), scale) + 4 * scale;
            }
            int barMaxW = Math.max(12, x + w - cx - progressTextW - badgeW - 4 * scale);
            int barH = Math.max(2, 3 * scale);
            int barY = textY + (BitmapFont.textHeight(scale) - barH) / 2;
            TextureGraphRenderer.fillRect(image, cx, barY, barMaxW, barH, 0xFF3A3228);
            int fillW = Math.round(barMaxW * row.getProgress());
            if (fillW > 0) {
                TextureGraphRenderer.fillRect(image, cx, barY, fillW, barH, stateColor);
                if (row.getState() == DispatchRowState.IN_PROGRESS) {
                    drawBarSheen(image, cx, barY, fillW, barH, stateColor, 1.3f);
                }
            }
            cx += barMaxW + 2 * scale;

            String progressText = row.getDeliveredAmount() + "/" + row.getTargetAmount();
            BitmapFont.drawString(image, progressText, cx, textY, TEXT_DIM, scale);
        }

        if (showDetail) {
            String badge = row.getResetBadge();
            if (!badge.isEmpty()) {
                BitmapFont.drawRight(image, badge, x + w, textY, BRASS_DIM, scale);
            }
        }
    }

    private static void drawCargoSummary(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        BitmapFont.drawString(image, "CARGO", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<CargoSummaryEntry> entries = snap.getCargoSummary();
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            CargoSummaryEntry entry = entries.get(i);
            String label = truncate(entry.getCompactLabel(), w / (6 * scale));
            BitmapFont.drawString(image, label, x + 2, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No cargo", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawHistory(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        BitmapFont.drawString(image, "HISTORY", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<DeliveryOrderHistoryEntry> entries = snap.getVisibleHistory();
        for (int i = 0; i < entries.size() && cy + lineH < y + h; i++) {
            DeliveryOrderHistoryEntry entry = entries.get(i);
            int typeColor = switch (entry.getType()) {
                case COMPLETED, MANUAL_COMPLETE -> 0xFF74D87F;
                case AUTO_RESET, MANUAL_RESET -> 0xFF9999FF;
                case HIDDEN -> TEXT_DIM;
                case SHOWN -> TEXT_PRIMARY;
            };
            String title = truncate(entry.getTitle(), w / (6 * scale) - 4);
            BitmapFont.drawString(image, "*", x + 2, cy, typeColor, scale);
            BitmapFont.drawString(image, title, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (entries.isEmpty()) {
            BitmapFont.drawString(image, "No events", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawTrainRoster(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        BitmapFont.drawString(image, "TRAINS", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        List<TrainSummarySnapshot> roster = snap.getRosterEntries();
        for (int i = 0; i < roster.size() && cy + lineH * 2 < y + h; i++) {
            TrainSummarySnapshot train = roster.get(i);

            int trainColor = train.isStoppedAtStation() ? 0xFF74D87F
                    : train.isInboundToStation() ? TRAIN_ENROUTE_COLOR
                    : train.isWaitingForSignal() ? 0xFFFFCC00 : TEXT_PRIMARY;
            String state = train.isStoppedAtStation() ? "DOCKED "
                    : train.isInboundToStation() ? "NEXT " : "";
            String trainName = truncate(state + train.getTrainName(), w / (6 * scale) - 2);
            BitmapFont.drawString(image, trainName, x + 2, cy, trainColor, scale);
            cy += lineH;

            String detail = train.getCarriageCount() + " cars | " + trainCargoBrief(train);
            BitmapFont.drawString(image, detail, x + 8, cy, TEXT_DIM, scale);
            cy += lineH;
        }

        if (roster.isEmpty()) {
            BitmapFont.drawString(image, "No trains", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static String trainCargoBrief(TrainSummarySnapshot train) {
        Map.Entry<com.createstats.train.manifest.FreightManifestResourceKey, Integer> largest = null;
        Map<com.createstats.train.manifest.FreightManifestResourceKey, Integer> amounts =
                train.getCargoSnapshot().getAmounts();
        for (Map.Entry<com.createstats.train.manifest.FreightManifestResourceKey, Integer> entry
                : amounts.entrySet()) {
            if (largest == null || entry.getValue() > largest.getValue()) {
                largest = entry;
            }
        }
        if (largest == null) {
            return "empty";
        }
        String label = new CargoSummaryEntry(largest.getKey(), largest.getValue()).getCompactLabel();
        return amounts.size() > 1 ? label + " +" + (amounts.size() - 1) : label;
    }

    private static void drawVisitTimeline(NativeImage image, List<TrainVisitRecord> visits, int x, int y, int w, int h, int scale) {
        int lineH = BitmapFont.textHeight(scale) + 2;
        int cy = y;

        BitmapFont.drawString(image, "VISITS", x + 2, cy, SECTION_HEADER, scale);
        cy += lineH + 1;
        TextureGraphRenderer.fillRect(image, x, cy, w, 1, BRASS_DIM);
        cy += 2;

        for (int i = 0; i < visits.size() && cy + lineH < y + h; i++) {
            TrainVisitRecord visit = visits.get(i);
            int visitColor = visit.isStillPresent() ? 0xFF74D87F : TEXT_DIM;
            String label = truncate(visit.getTrainName(), w / (6 * scale) - 6);

            BitmapFont.drawString(image, visit.isStillPresent() ? ">" : "-", x + 2, cy, visitColor, scale);
            BitmapFont.drawString(image, label, x + 2 + 6 * scale, cy, TEXT_PRIMARY, scale);
            cy += lineH;
        }

        if (visits.isEmpty()) {
            BitmapFont.drawString(image, "No visits", x + 2, cy, TEXT_DIM, scale);
        }
    }

    private static void drawExceptionStrip(NativeImage image, DispatchBoardSnapshot snap, int x, int y, int w, int h, int scale) {
        TextureGraphRenderer.fillRect(image, x, y, w, h, 0xFF3A1010);
        TextureGraphRenderer.fillRect(image, x, y, w, 1, 0xFFFF5555);

        int textY = y + (h - BitmapFont.textHeight(scale)) / 2;
        int invalid = snap.countRowsInState(DispatchRowState.INVALID);
        int setup = snap.countRowsInState(DispatchRowState.NEEDS_SETUP);
        String msg = "";
        if (invalid > 0) msg += invalid + " invalid";
        if (setup > 0) msg += (msg.isEmpty() ? "" : " | ") + setup + " need setup";
        if (msg.isEmpty()) msg = "Exceptions present";
        BitmapFont.drawCentered(image, "! " + msg, x, textY, w, 0xFFFF8888, scale);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    private static void cleanupStaleTextures(long currentTick) {
        Iterator<Map.Entry<BlockPos, TextureEntry>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, TextureEntry> e = iterator.next();
            if (currentTick - e.getValue().lastAccessTick > MAX_IDLE_TICKS) {
                disposeEntry(e.getValue());
                iterator.remove();
            }
        }
    }

    private static void disposeEntry(TextureEntry entry) {
        try {
            if (entry.dynamicTexture != null) {
                entry.dynamicTexture.close();
            }
        } catch (RuntimeException ex) {
            CreateStats.LOGGER.warn("Failed to dispose Dispatch Board texture", ex);
        }
    }

    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private static class TextureEntry {
        private NativeImage image;
        private DynamicTexture dynamicTexture;
        private ResourceLocation textureLocation;
        private int texWidth;
        private int texHeight;
        private long lastUpdateTick;
        private long lastAccessTick;
        private boolean needsRedraw;
    }
}
