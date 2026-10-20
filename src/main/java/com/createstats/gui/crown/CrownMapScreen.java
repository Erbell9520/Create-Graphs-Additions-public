package com.createstats.gui.crown;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.CrownConfig;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.map.CrownMapTooltip;
import com.createstats.chunkloader.map.CrownMapView;
import com.createstats.chunkloader.map.CrownPlate;
import com.createstats.chunkloader.net.CrownMapDataPacket;
import com.createstats.chunkloader.net.CrownMapRequestPacket;
import com.createstats.chunkloader.net.CrownPickPacket;
import com.createstats.chunkloader.net.CrownPickReplyPacket;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.gui.meter.plate.GlassFont;
import com.createstats.gui.meter.plate.PlateInk;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class CrownMapScreen extends Screen {
    static final int ROSE_FILL = 0x70FF5C94;
    static final int ROSE_EDGE = 0xFFFFC2D6;
    static final int RIDE_FILL = 0x70FF9630;
    static final int RIDE_EDGE = 0xFFFFD8A0;
    static final int EMBER = 0x8A2448;
    static final int WAIT_EDGE = 0xFFB8406E;
    static final int RING = 0xC8FFF6DC;
    static final int OTHER = 0xE6EBEBF5;
    static final int GRID = 0x18000000;
    static final int REFUSED = 0x66101010;
    static final int REGION_EDGE = 0xB0FFFFFF;
    static final int OWN_RING = 0xFFFFC2D6;
    static final int PAINT_FILL = 0x40FFFFFF;
    static final int HOVER_FILL = 0x30FFFFFF;
    static final int BACKING = 0x90000000;
    static final int NOTE = 0xFFB0B0B0;

    static final int BEYOND = 0xFF161616;

    static final int PLATE = 0xFFAEB4B9;

    static final int HATCH_ALPHA = 77;
    static final int PULSE_TICKS = 6;
    static final int EMBER_TICKS = 40;
    static final int NOTICE_TICKS = 100;

    private static final ResourceLocation HATCH = ResourceLocation.fromNamespaceAndPath("create_stats",
            "dynamic/crown_map_hatch");
    private static final ResourceLocation ARROWS = ResourceLocation.fromNamespaceAndPath("create_stats",
            "dynamic/crown_map_player");
    private static final ResourceLocation PLAYER = ResourceLocation.withDefaultNamespace(
            "textures/map/decorations/player.png");
    private static final int HATCH_SIZE = 320;
    private static DynamicTexture hatch;
    private static DynamicTexture arrows;

    private final SparkCrownBE be;
    private final BlockPos pos;
    private final int regionX;
    private final int regionZ;
    private CrownMapView view;
    private CrownMapPicture picture;
    private CrownLaw law;
    private final LongOpenHashSet others = new LongOpenHashSet();
    private int ticks;
    private int lastHeartbeat = Integer.MIN_VALUE;
    private int pulseAt = -1000;
    private Component notice;
    private int noticeColour;
    private int noticeUntil;

    private boolean pressed;
    private double pressX;
    private double pressY;
    private boolean panning;
    private boolean painting;
    private boolean paintRect;
    private boolean paintDrop;
    private final List<Long> paintSamples = new ArrayList<>();

    public CrownMapScreen(SparkCrownBE be) {
        super(Component.translatable("block.create_stats.spark_crown"));
        this.be = be;
        this.pos = be.getBlockPos();
        this.regionX = CrownRegion.regionOf(pos.getX() >> 4);
        this.regionZ = CrownRegion.regionOf(pos.getZ() >> 4);
    }

    BlockPos pos() {
        return pos;
    }

    private int mapHeight() {
        return height - CrownPlate.HEIGHT;
    }

    @Override
    protected void init() {
        if (view == null) {
            view = new CrownMapView(regionX, regionZ, width, mapHeight());
        } else {
            view.resize(width, mapHeight());
        }
        GlassFont.ensure();
        ensureTextures();
        if (picture == null) {
            picture = new CrownMapPicture(view.windowChunkX(), view.windowChunkZ());
            PacketDistributor.sendToServer(new CrownMapRequestPacket(pos, view.windowChunkX(), view.windowChunkZ(),
                    CrownMapView.WINDOW));
        }
    }

    @Override
    public void removed() {
        if (picture != null) {
            picture.close();
            picture = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || be.isRemoved() || mc.level.getBlockEntity(pos) != be
                || !SableBridge.isPlayerInInteractionRangeFor(be, mc.player)) {
            onClose();
            return;
        }
        final int beat = be.crown().heartbeat();
        if (lastHeartbeat != Integer.MIN_VALUE && beat != lastHeartbeat) {
            pulseAt = ticks;
        }
        lastHeartbeat = beat;
    }

    void accept(CrownMapDataPacket p) {
        law = p.law();
        for (long c : p.others()) {
            others.add(c);
        }
        if (picture != null) {
            picture.accept(p);
        }
    }

    void reply(CrownPickReplyPacket p) {
        final CrownMapTooltip.Line line = CrownMapTooltip.reply(crown(), p.drop(), p.asked(), p.verdictList(),
                p.refusedAt());
        if (line != null) {
            notice = component(line);
            noticeColour = line.colour();
            noticeUntil = ticks + NOTICE_TICKS;
        }
    }

    private CrownLaw law() {
        return law != null ? law : CrownConfig.law();
    }

    private CrownMapTooltip.Crown crown() {
        final UUID owner = be.owner();
        final Player me = Minecraft.getInstance().player;
        return new CrownMapTooltip.Crown(pos.getX() >> 4, pos.getZ() >> 4, be.picks(), be.crown(), be.clientBoard(),
                law(), owner != null && me != null && owner.equals(me.getUUID()), owner != null, be.ownerName());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final CrownMapTooltip.Crown c = crown();
        final int mapH = mapHeight();
        if (picture != null) {
            picture.refresh();
        }
        final boolean overMap = mouseY < mapH && mouseX >= 0 && mouseX < width && mouseY >= 0;
        final Long hover = overMap && !panning ? view.chunkAt(mouseX, mouseY) : null;
        final List<Long> paint = painting ? paintChunks(c) : List.of();

        g.enableScissor(0, 0, width, mapH);
        g.fill(0, 0, width, mapH, BEYOND);
        drawGround(g);
        drawCrossing(g, mapH);
        drawMarks(g, c, mapH, partialTick, painting ? null : hover, paint);
        g.disableScissor();

        drawTitle(g);
        drawPlate(g, c, mapH);

        if (painting && !paint.isEmpty()) {
            g.renderComponentTooltip(font, components(CrownMapTooltip.paint(c, paintDrop, paint)), mouseX, mouseY);
        } else if (hover != null && !painting) {
            g.renderComponentTooltip(font, components(CrownMapTooltip.lines(c, CrownRegion.x(hover),
                    CrownRegion.z(hover))), mouseX, mouseY);
        }
    }

    private void drawGround(GuiGraphics g) {
        if (picture == null) {
            return;
        }
        final int x = view.pixelX(view.windowChunkX() * 16.0);
        final int y = view.pixelZ(view.windowChunkZ() * 16.0);
        if (view.zoom() < CrownMapView.OPEN_ZOOM) {
            final int s = CrownMapPicture.BLOCKS / 2;
            g.blit(picture.halfId, x, y, s, s, 0f, 0f, s, s, s, s);
        } else {
            final int s = (int) (CrownMapPicture.BLOCKS * view.scale());
            g.blit(picture.fullId, x, y, s, s, 0f, 0f, CrownMapPicture.BLOCKS, CrownMapPicture.BLOCKS,
                    CrownMapPicture.BLOCKS, CrownMapPicture.BLOCKS);
        }
    }

    private void drawCrossing(GuiGraphics g, int mapH) {
        final int[] r = view.regionRect();
        final int[][] strips = {
                {0, 0, width, Math.max(0, r[1])},
                {0, Math.min(mapH, r[3]), width, mapH},
                {0, Math.max(0, r[1]), Math.max(0, r[0]), Math.min(mapH, r[3])},
                {Math.min(width, r[2]), Math.max(0, r[1]), width, Math.min(mapH, r[3])}};
        for (int[] s : strips) {
            if (s[2] <= s[0] || s[3] <= s[1]) {
                continue;
            }
            g.enableScissor(s[0], s[1], s[2], s[3]);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (int ty = (s[1] / HATCH_SIZE) * HATCH_SIZE; ty < s[3]; ty += HATCH_SIZE) {
                for (int tx = (s[0] / HATCH_SIZE) * HATCH_SIZE; tx < s[2]; tx += HATCH_SIZE) {
                    g.blit(HATCH, tx, ty, 0f, 0f, HATCH_SIZE, HATCH_SIZE, HATCH_SIZE, HATCH_SIZE);
                }
            }
            RenderSystem.disableBlend();
            g.disableScissor();
        }
    }

    private void drawMarks(GuiGraphics g, CrownMapTooltip.Crown c, int mapH, float pt, Long hover, List<Long> paint) {
        final int ppc = view.zoom();
        final int[] r = view.regionRect();
        final int rx0 = regionX * CrownRegion.CHUNKS;
        final int rz0 = regionZ * CrownRegion.CHUNKS;
        final long[] picks = c.picks();
        final int held = c.held();
        final CrownState s = c.state();
        final boolean riding = s.phase() == CrownHold.Phase.RIDING;

        if (ppc >= CrownMapView.OPEN_ZOOM) {
            for (int k = 1; k < CrownRegion.CHUNKS; k++) {
                final int gx = view.chunkPixelX(rx0 + k);
                final int gz = view.chunkPixelZ(rz0 + k);
                g.fill(gx, Math.max(0, r[1]), gx + 1, Math.min(mapH, r[3]), GRID);
                g.fill(Math.max(0, r[0]), gz, Math.min(width, r[2]), gz + 1, GRID);
            }
        }
        // at a cap, dim what a click can't take. never hide it
        if (capRefuses(c, picks)) {
            for (int cx = rx0; cx < rx0 + CrownRegion.CHUNKS; cx++) {
                for (int cz = rz0; cz < rz0 + CrownRegion.CHUNKS; cz++) {
                    if (c.indexOf(CrownRegion.pack(cx, cz)) < 0) {
                        final int x0 = view.chunkPixelX(cx);
                        final int y0 = view.chunkPixelZ(cz);
                        g.fill(x0, y0, x0 + ppc, y0 + ppc, REFUSED);
                    }
                }
            }
        }
        final Set<Long> heldSet = new HashSet<>();
        for (int i = 0; i < held; i++) {
            heldSet.add(picks[i]);
        }

        final Set<Long> ring = new HashSet<>();
        for (long h : heldSet) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    final long q = CrownRegion.pack(CrownRegion.x(h) + ox, CrownRegion.z(h) + oz);
                    if (!heldSet.contains(q)) {
                        ring.add(q);
                    }
                }
            }
        }
        for (long q : ring) {
            final int qx = CrownRegion.x(q);
            final int qz = CrownRegion.z(q);
            final int x0 = view.chunkPixelX(qx);
            final int y0 = view.chunkPixelZ(qz);
            final int x1 = x0 + ppc - 1;
            final int y1 = y0 + ppc - 1;
            if (outside(qx, qz - 1, ring, heldSet)) {
                for (int k = x0; k <= x1; k += 2) {
                    g.fill(k, y0, k + 1, y0 + 1, RING);
                }
            }
            if (outside(qx, qz + 1, ring, heldSet)) {
                for (int k = x0; k <= x1; k += 2) {
                    g.fill(k, y1, k + 1, y1 + 1, RING);
                }
            }
            if (outside(qx - 1, qz, ring, heldSet)) {
                for (int k = y0; k <= y1; k += 2) {
                    g.fill(x0, k, x0 + 1, k + 1, RING);
                }
            }
            if (outside(qx + 1, qz, ring, heldSet)) {
                for (int k = y0; k <= y1; k += 2) {
                    g.fill(x1, k, x1 + 1, k + 1, RING);
                }
            }
        }

        final double ember = 0.6 + 0.4 * Math.sin(2 * Math.PI * (ticks + pt) / EMBER_TICKS);
        final int emberFill = ((0x40 + (int) (0x50 * ember)) << 24) | EMBER;
        for (int i = 0; i < held; i++) {
            final int x0 = view.chunkPixelX(CrownRegion.x(picks[i]));
            final int y0 = view.chunkPixelZ(CrownRegion.z(picks[i]));
            g.fill(x0, y0, x0 + ppc, y0 + ppc, !s.ticking(i) ? emberFill : riding ? RIDE_FILL : ROSE_FILL);
        }
        final int edge = lerp(riding ? RIDE_EDGE : ROSE_EDGE, 0xFFFFFFFF, beat(pt));
        for (long h : heldSet) {
            final int hx = CrownRegion.x(h);
            final int hz = CrownRegion.z(h);
            final int x0 = view.chunkPixelX(hx);
            final int y0 = view.chunkPixelZ(hz);
            if (!heldSet.contains(CrownRegion.pack(hx, hz - 1))) {
                g.fill(x0, y0, x0 + ppc, y0 + 1, edge);
            }
            if (!heldSet.contains(CrownRegion.pack(hx, hz + 1))) {
                g.fill(x0, y0 + ppc - 1, x0 + ppc, y0 + ppc, edge);
            }
            if (!heldSet.contains(CrownRegion.pack(hx - 1, hz))) {
                g.fill(x0, y0, x0 + 1, y0 + ppc, edge);
            }
            if (!heldSet.contains(CrownRegion.pack(hx + 1, hz))) {
                g.fill(x0 + ppc - 1, y0, x0 + ppc, y0 + ppc, edge);
            }
        }

        final Set<Long> waiting = new HashSet<>();
        for (int i = held; i < picks.length; i++) {
            waiting.add(picks[i]);
        }
        for (long w : waiting) {
            dashedEdges(g, CrownRegion.x(w), CrownRegion.z(w), ppc, waiting, WAIT_EDGE);
        }

        final int ox = view.chunkPixelX(pos.getX() >> 4) + 2;
        final int oy = view.chunkPixelZ(pos.getZ() >> 4) + 2;
        final int os = ppc - 4;
        g.fill(ox, oy, ox + os, oy + 1, OWN_RING);
        g.fill(ox, oy + os - 1, ox + os, oy + os, OWN_RING);
        g.fill(ox, oy, ox + 1, oy + os, OWN_RING);
        g.fill(ox + os - 1, oy, ox + os, oy + os, OWN_RING);

        for (long q : others) {
            final int x0 = view.chunkPixelX(CrownRegion.x(q));
            final int y0 = view.chunkPixelZ(CrownRegion.z(q));
            for (int k = 0; k < ppc; k += 2) {
                g.fill(x0 + k, y0, x0 + k + 1, y0 + 1, OTHER);
                g.fill(x0 + k, y0 + ppc - 1, x0 + k + 1, y0 + ppc, OTHER);
                g.fill(x0, y0 + k, x0 + 1, y0 + k + 1, OTHER);
                g.fill(x0 + ppc - 1, y0 + k, x0 + ppc, y0 + k + 1, OTHER);
            }
        }

        for (long q : paint) {
            final int x0 = view.chunkPixelX(CrownRegion.x(q));
            final int y0 = view.chunkPixelZ(CrownRegion.z(q));
            g.fill(x0, y0, x0 + ppc, y0 + ppc, PAINT_FILL);
            for (int k = 0; k < ppc; k += 3) {
                g.fill(x0 + k, y0, x0 + k + 2, y0 + 1, 0xFFFFFFFF);
                g.fill(x0 + k, y0 + ppc - 1, x0 + k + 2, y0 + ppc, 0xFFFFFFFF);
                g.fill(x0, y0 + k, x0 + 1, y0 + k + 2, 0xFFFFFFFF);
                g.fill(x0 + ppc - 1, y0 + k, x0 + ppc, y0 + k + 2, 0xFFFFFFFF);
            }
        }

        g.fill(r[0], r[1], r[2], r[1] + 1, REGION_EDGE);
        g.fill(r[0], r[3] - 1, r[2], r[3], REGION_EDGE);
        g.fill(r[0], r[1], r[0] + 1, r[3], REGION_EDGE);
        g.fill(r[2] - 1, r[1], r[2], r[3], REGION_EDGE);
        final int[][] crosses = {{r[0] + 6, r[1] + 6}, {r[2] - 7, r[1] + 6}, {r[0] + 6, r[3] - 7}, {r[2] - 7, r[3] - 7}};
        for (int[] x : crosses) {
            g.fill(x[0] - 4, x[1], x[0] + 5, x[1] + 1, 0xFFFFFFFF);
            g.fill(x[0], x[1] - 4, x[0] + 1, x[1] + 5, 0xFFFFFFFF);
        }

        if (hover != null) {
            final int x0 = view.chunkPixelX(CrownRegion.x(hover));
            final int y0 = view.chunkPixelZ(CrownRegion.z(hover));
            g.fill(x0 - 1, y0 - 1, x0 + ppc + 1, y0, 0xFFFFFFFF);
            g.fill(x0 - 1, y0 + ppc, x0 + ppc + 1, y0 + ppc + 1, 0xFFFFFFFF);
            g.fill(x0 - 1, y0, x0, y0 + ppc, 0xFFFFFFFF);
            g.fill(x0 + ppc, y0, x0 + ppc + 1, y0 + ppc, 0xFFFFFFFF);
            g.fill(x0, y0, x0 + ppc, y0 + ppc, HOVER_FILL);
        }

        final int sx = view.pixelX(pos.getX());
        final int sy = view.pixelZ(pos.getZ());
        final String[] diamond = {".kkk.", "kRRRk", "kRWRk", "kRRRk", ".kkk."};
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 5; i++) {
                final char ch = diamond[j].charAt(i);
                if (ch != '.') {
                    g.fill(sx - 2 + i, sy - 2 + j, sx - 1 + i, sy - 1 + j,
                            ch == 'k' ? 0xFF000000 : ch == 'R' ? 0xFFFF5C94 : 0xFFFFECEE);
                }
            }
        }

        final Player me = Minecraft.getInstance().player;
        if (me != null && arrows != null) {
            final int ax = view.pixelX(me.getX()) - 4;
            final int ay = view.pixelZ(me.getZ()) - 4;
            final int step = Math.floorMod(Math.round((me.getYRot() + 180f) / 22.5f), 16);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(ARROWS, ax, ay, 8, 8, step * 8f, 0f, 8, 8, 128, 8);
            RenderSystem.disableBlend();
        }
    }

    private static boolean capRefuses(CrownMapTooltip.Crown c, long[] picks) {
        if (!c.viewerIsOwner()) {
            return false;
        }
        final int rx0 = CrownRegion.regionOf(c.ownChunkX()) * CrownRegion.CHUNKS;
        final int rz0 = CrownRegion.regionOf(c.ownChunkZ()) * CrownRegion.CHUNKS;
        for (int k = 0; k < CrownRegion.CHUNKS * CrownRegion.CHUNKS; k++) {
            final int cx = rx0 + k % CrownRegion.CHUNKS;
            final int cz = rz0 + k / CrownRegion.CHUNKS;
            if (c.indexOf(CrownRegion.pack(cx, cz)) < 0) {
                final CrownPicks.Verdict v = CrownMapTooltip.verdict(c, cx, cz);
                return v == CrownPicks.Verdict.STATION_FULL || v == CrownPicks.Verdict.PLAYER_FULL
                        || v == CrownPicks.Verdict.BOARD_CARRY || v == CrownPicks.Verdict.BOARD_SUPPLY;
            }
        }
        return false;
    }

    private static boolean outside(int x, int z, Set<Long> ring, Set<Long> held) {
        final long q = CrownRegion.pack(x, z);
        return !ring.contains(q) && !held.contains(q);
    }

    private void dashedEdges(GuiGraphics g, int cx, int cz, int ppc, Set<Long> set, int colour) {
        final int x0 = view.chunkPixelX(cx);
        final int y0 = view.chunkPixelZ(cz);
        final boolean n = !set.contains(CrownRegion.pack(cx, cz - 1));
        final boolean s = !set.contains(CrownRegion.pack(cx, cz + 1));
        final boolean w = !set.contains(CrownRegion.pack(cx - 1, cz));
        final boolean e = !set.contains(CrownRegion.pack(cx + 1, cz));
        for (int k = 0; k < ppc; k += 3) {
            if (n) {
                g.fill(x0 + k, y0, x0 + k + 2, y0 + 1, colour);
            }
            if (s) {
                g.fill(x0 + k, y0 + ppc - 1, x0 + k + 2, y0 + ppc, colour);
            }
            if (w) {
                g.fill(x0, y0 + k, x0 + 1, y0 + k + 2, colour);
            }
            if (e) {
                g.fill(x0 + ppc - 1, y0 + k, x0 + ppc, y0 + k + 2, colour);
            }
        }
    }

    private double beat(float pt) {
        final double t = (ticks - pulseAt + pt) / PULSE_TICKS;
        return t < 0 || t > 1 ? 0.0 : 1.0 - Math.abs(2 * t - 1);
    }

    static int lerp(int a, int b, double t) {
        if (t <= 0) {
            return a;
        }
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            final int ca = (a >>> shift) & 0xFF;
            final int cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * Math.min(1.0, t)) & 0xFF) << shift;
        }
        return out;
    }

    private void drawTitle(GuiGraphics g) {
        final Component title = Component.translatable(CrownMapTooltip.KEY + "title", regionX, regionZ);
        g.fill(4, 4, 8 + font.width(title), 16, BACKING);
        g.drawString(font, title, 6, 6, 0xFFFFFFFF, true);
        int y = 18;
        if (picture != null && picture.journeyMapLooked() && !picture.hasJourneyMap()) {
            final Component note = Component.translatable(CrownMapTooltip.KEY + "note_no_map");
            g.fill(4, y, 8 + font.width(note), y + 12, BACKING);
            g.drawString(font, note, 6, y + 2, NOTE, true);
            y += 14;
        }
        if (notice != null && ticks < noticeUntil) {
            g.fill(4, y, 8 + font.width(notice), y + 12, BACKING);
            g.drawString(font, notice, 6, y + 2, noticeColour, true);
        }
    }

    private void drawPlate(GuiGraphics g, CrownMapTooltip.Crown c, int y0) {
        g.fill(0, y0, width, y0 + 1, PlateInk.RIM_OUT);
        g.fill(0, y0 + 1, width, height, PLATE);
        g.fill(0, y0 + 1, width, y0 + 2, PlateInk.PLATE_HI);
        final int[] legendW = new int[4];
        for (int i = 0; i < 4; i++) {
            legendW[i] = font.width(CrownPlate.LEGENDS[i]);
        }
        final boolean narrow = CrownPlate.narrow(width);
        final List<CrownPlate.Field> fields = CrownPlate.layout(c.state(), c.board(), c.law(), width, legendW);
        for (int i = 0; i < fields.size(); i++) {
            final CrownPlate.Field f = fields.get(i);
            final CrownPlate.Window w = CrownPlate.window(width, i, legendW[i], f.hasBar(), narrow);
            g.drawString(font, f.legend(), w.legendX(), y0 + 8, PlateInk.ENGRAVE_LIT, false);
            g.drawString(font, f.legend(), w.legendX(), y0 + 7, PlateInk.ENGRAVE, false);
            final int wx0 = w.x0();
            final int wx1 = w.x1();
            g.fill(wx0, y0 + 4, wx1, y0 + 18, PlateInk.BEZEL);
            g.fill(wx0 + 1, y0 + 5, wx1 - 1, y0 + 17, PlateInk.GLASS);
            g.fill(wx0 - 1, y0 + 3, wx1 + 1, y0 + 4, PlateInk.CUT_DARK);
            g.fill(wx0 - 1, y0 + 3, wx0, y0 + 19, PlateInk.CUT_DARK);
            g.fill(wx0, y0 + 18, wx1 + 1, y0 + 19, PlateInk.CUT_LIT);
            g.fill(wx1, y0 + 4, wx1 + 1, y0 + 19, PlateInk.CUT_LIT);
            final PlateInk.Ink ink = ink(f.ink());
            g.enableScissor(wx0 + 1, y0 + 5, wx1 - 1, y0 + 17);
            int x = w.textX();
            for (CrownPlate.Seg seg : f.segs()) {
                if (seg.figure()) {
                    GlassFont.glow(g, seg.text(), x, y0 + 7, ink, 1);
                } else {
                    GlassFont.draw(g, seg.text(), x, y0 + 7, ink.mid(), 1);
                }
                x += CrownPlate.PITCH * seg.text().length();
            }
            g.disableScissor();
            if (f.hasBar()) {
                loadBar(g, w.barX(), y0 + 8, w.barWidth(), 5, f.bar(), ink);
            }
        }
    }

    private static void loadBar(GuiGraphics g, int x0, int y0, int w, int h, double frac, PlateInk.Ink ink) {
        for (int k = 0; k < w; k++) {
            g.fill(x0 + k, y0, x0 + k + 1, y0 + h, k % 2 == 0 ? ink.dim() : ink.faint());
        }
        final int fw = (int) Math.round(w * Math.max(0.0, Math.min(1.0, frac)));
        if (fw > 0) {
            g.fill(x0, y0, x0 + fw, y0 + h, ink.mid());
            g.fill(x0, y0, x0 + fw, y0 + 1, ink.main());
        }
    }

    private static PlateInk.Ink ink(CrownPlate.Ink i) {
        return switch (i) {
            case GREEN -> PlateInk.GREEN;
            case AMBER -> PlateInk.AMBER;
            case RED -> PlateInk.RED;
            case IDLE -> PlateInk.IDLE;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my >= mapHeight() || button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        pressed = true;
        pressX = mx;
        pressY = my;
        panning = false;
        painting = hasShiftDown();
        paintSamples.clear();
        if (painting) {
            paintRect = hasControlDown();
            final long start = view.chunkAt(mx, my);
            final CrownMapTooltip.Crown c = crown();
            paintDrop = c.indexOf(start) >= 0 && start != CrownRegion.pack(c.ownChunkX(), c.ownChunkZ());
            paintSamples.add(start);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!pressed) {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        if (painting) {
            final long c = view.chunkAt(Math.max(0, Math.min(width - 1, mx)), Math.max(0, Math.min(mapHeight() - 1, my)));
            if (paintSamples.isEmpty() || paintSamples.get(paintSamples.size() - 1) != c) {
                paintSamples.add(c);
            }
            return true;
        }
        if (!panning && Math.abs(mx - pressX) + Math.abs(my - pressY) > 2) {
            panning = true;
        }
        if (panning) {
            view.pan(dx, dy);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!pressed || button != 0) {
            return super.mouseReleased(mx, my, button);
        }
        pressed = false;
        if (painting) {
            painting = false;
            send(paintDrop, paintChunks(crown()));
            paintSamples.clear();
            return true;
        }
        if (panning) {
            panning = false;
            return true;
        }
        final long chunk = view.chunkAt(mx, my);
        final CrownMapTooltip.Crown c = crown();
        final CrownPicks.Verdict v = CrownMapTooltip.verdict(c, CrownRegion.x(chunk), CrownRegion.z(chunk));

        if (v.changes()) {
            send(v == CrownPicks.Verdict.DROP, List.of(chunk));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (my < mapHeight() && scrollY != 0) {
            view.zoomAbout(mx, my, scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private List<Long> paintChunks(CrownMapTooltip.Crown c) {
        if (paintSamples.isEmpty()) {
            return List.of();
        }
        final long first = paintSamples.get(0);
        final long last = paintSamples.get(paintSamples.size() - 1);
        final List<Long> raw = paintRect
                ? CrownMapView.rectangle(CrownRegion.x(first), CrownRegion.z(first), CrownRegion.x(last), CrownRegion.z(last))
                : CrownMapView.path(paintSamples);
        final long own = CrownRegion.pack(c.ownChunkX(), c.ownChunkZ());
        final List<Long> out = new ArrayList<>();
        for (long q : raw) {
            if (out.size() >= CrownPickPacket.MAX) {
                break;
            }
            final boolean picked = c.indexOf(q) >= 0;
            if (paintDrop ? picked && q != own
                    : !picked && CrownRegion.sameRegion(c.ownChunkX(), c.ownChunkZ(), CrownRegion.x(q), CrownRegion.z(q))) {
                out.add(q);
            }
        }
        return out;
    }

    private void send(boolean drop, List<Long> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        final long[] a = new long[chunks.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = chunks.get(i);
        }
        PacketDistributor.sendToServer(new CrownPickPacket(pos, drop, a));
    }

    private static List<Component> components(List<CrownMapTooltip.Line> lines) {
        final List<Component> out = new ArrayList<>(lines.size());
        for (CrownMapTooltip.Line l : lines) {
            out.add(component(l));
        }
        return out;
    }

    private static MutableComponent component(CrownMapTooltip.Line l) {
        final Object[] args = new Object[l.args().size()];
        for (int i = 0; i < args.length; i++) {
            final Object a = l.args().get(i);
            args[i] = a instanceof CrownMapTooltip.Line nested ? Component.translatable(nested.key(),
                    nested.args().toArray()) : a;
        }
        return Component.translatable(l.key(), args).withColor(l.colour() & 0xFFFFFF);
    }

    private static void ensureTextures() {
        final Minecraft mc = Minecraft.getInstance();
        if (hatch == null || mc.getTextureManager().getTexture(HATCH, null) != hatch) {
            final NativeImage img = new NativeImage(HATCH_SIZE, HATCH_SIZE, true);
            for (int y = 0; y < HATCH_SIZE; y++) {
                for (int x = 0; x < HATCH_SIZE; x++) {
                    img.setPixelRGBA(x, y, (x + y) % 5 == 0 ? HATCH_ALPHA << 24 : 0);
                }
            }
            hatch = new DynamicTexture(img);
            mc.getTextureManager().register(HATCH, hatch);
        }
        if (arrows == null || mc.getTextureManager().getTexture(ARROWS, null) != arrows) {
            final Optional<Resource> res = mc.getResourceManager().getResource(PLAYER);
            if (res.isEmpty()) {
                return;
            }
            try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
                final NativeImage strip = new NativeImage(128, 8, true);
                for (int k = 0; k < 16; k++) {
                    final double a = Math.toRadians(k * 22.5);
                    final double cos = Math.cos(a);
                    final double sin = Math.sin(a);
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            final double dx = x + 0.5 - 4;
                            final double dy = y + 0.5 - 4;
                            final int sxp = (int) Math.floor(dx * cos + dy * sin + 4);
                            final int syp = (int) Math.floor(-dx * sin + dy * cos + 4);
                            final int px = sxp >= 0 && sxp < 8 && syp >= 0 && syp < 8
                                    ? src.getPixelRGBA(sxp * src.getWidth() / 8, syp * src.getHeight() / 8) : 0;
                            strip.setPixelRGBA(k * 8 + x, y, px);
                        }
                    }
                }
                arrows = new DynamicTexture(strip);
                mc.getTextureManager().register(ARROWS, arrows);
            } catch (java.io.IOException e) {
                arrows = null;
            }
        }
    }
}
