package com.createstats.gui.crown;

import com.createstats.chunkloader.map.CrownGuess;
import com.createstats.chunkloader.map.CrownMapView;
import com.createstats.chunkloader.map.CrownSampleShade;
import com.createstats.chunkloader.map.JourneyMapPaths;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.net.CrownMapDataPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

// paper, then seed guess, then server sample. best on top
@OnlyIn(Dist.CLIENT)
final class CrownMapPicture implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/crown_map");

    static final int BLOCKS = CrownMapView.WINDOW * 16;
    private static final int CHUNKS = CrownMapView.WINDOW;

    private static final int REGION0 = CrownMapView.SURROUND * 16;
    private static final int REGION1 = REGION0 + CrownRegion.CHUNKS * 16;

    static final int CHUNKS_A_FRAME = 1024;

    private static int serial;

    private final int chunkX0;
    private final int chunkZ0;
    private final byte[] samples = new byte[CHUNKS * CHUNKS * CrownSampleShade.PER_CHUNK];
    private final boolean[] sampled = new boolean[CHUNKS * CHUNKS];
    private final byte[] guesses = new byte[CHUNKS * CHUNKS];
    private int[] journeyMap;
    private boolean journeyMapLooked;
    private final BitSet dirty = new BitSet(CHUNKS * CHUNKS);
    private final NativeImage full = new NativeImage(BLOCKS, BLOCKS, true);
    private final NativeImage half = new NativeImage(BLOCKS / 2, BLOCKS / 2, true);
    private final DynamicTexture fullTexture;
    private final DynamicTexture halfTexture;
    final ResourceLocation fullId;
    final ResourceLocation halfId;
    private volatile int[] arrived;
    private volatile boolean arrivedLooked;
    private boolean closed;

    CrownMapPicture(int chunkX0, int chunkZ0) {
        this.chunkX0 = chunkX0;
        this.chunkZ0 = chunkZ0;
        final int n = ++serial;
        fullId = ResourceLocation.fromNamespaceAndPath("create_stats", "dynamic/crown_map_ground_" + n);
        halfId = ResourceLocation.fromNamespaceAndPath("create_stats", "dynamic/crown_map_ground_half_" + n);
        fullTexture = new DynamicTexture(full);
        halfTexture = new DynamicTexture(half);
        final Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().register(fullId, fullTexture);
        mc.getTextureManager().register(halfId, halfTexture);
        dirty.set(0, CHUNKS * CHUNKS);
        startJourneyMap(mc);
    }

    boolean hasJourneyMap() {
        return journeyMap != null;
    }

    boolean journeyMapLooked() {
        return journeyMapLooked;
    }

    void accept(CrownMapDataPacket p) {
        final long[] chunks = p.sampleChunks();
        for (int k = 0; k < chunks.length; k++) {
            final int i = CrownRegion.x(chunks[k]) - chunkX0;
            final int j = CrownRegion.z(chunks[k]) - chunkZ0;
            if (i < 0 || j < 0 || i >= CHUNKS || j >= CHUNKS) {
                continue;
            }
            final int c = j * CHUNKS + i;
            System.arraycopy(p.samples(), k * CrownSampleShade.PER_CHUNK, samples, c * CrownSampleShade.PER_CHUNK,
                    CrownSampleShade.PER_CHUNK);
            sampled[c] = true;
            dirty.set(c);
        }
        for (int j = 0; j < p.guessH(); j++) {
            for (int i = 0; i < p.guessW(); i++) {
                final int ci = p.guessX() + i - chunkX0;
                final int cj = p.guessZ() + j - chunkZ0;
                if (ci < 0 || cj < 0 || ci >= CHUNKS || cj >= CHUNKS) {
                    continue;
                }
                guesses[cj * CHUNKS + ci] = p.guesses()[j * p.guessW() + i];
                dirty.set(cj * CHUNKS + ci);
            }
        }
    }

    void refresh() {
        if (closed) {
            return;
        }
        if (!journeyMapLooked && arrivedLooked) {
            journeyMapLooked = true;
            journeyMap = arrived;
            if (journeyMap != null) {
                dirty.set(0, CHUNKS * CHUNKS);
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        int done = 0;
        for (int c = dirty.nextSetBit(0); c >= 0 && done < CHUNKS_A_FRAME; c = dirty.nextSetBit(c + 1)) {
            compose(c % CHUNKS, c / CHUNKS);
            dirty.clear(c);
            done++;
        }
        fullTexture.upload();
        halfTexture.upload();
    }

    private void compose(int ci, int cj) {
        final int c = cj * CHUNKS + ci;
        final int guess = guesses[c] == 0 ? CrownGuess.PAPER : CrownGuess.argb(guesses[c]);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int bx = ci * 16 + lx;
                final int bz = cj * 16 + lz;
                int argb = guess;
                if (sampled[c]) {
                    final int s = samples[c * CrownSampleShade.PER_CHUNK + CrownSampleShade.index(lx, lz)] & 0xFF;
                    if (s != 0) {
                        argb = abgrToArgb(MapColor.getColorFromPackedId(s));
                    }
                }
                if (journeyMap != null) {
                    final int j = journeyMap[bz * BLOCKS + bx];
                    if ((j >>> 24) != 0) {
                        argb = over(j, argb);
                    }
                }
                if (bx < REGION0 || bx >= REGION1 || bz < REGION0 || bz >= REGION1) {
                    argb = surround(argb);
                }
                full.setPixelRGBA(bx, bz, argbToAbgr(argb));
            }
        }
        for (int hz = 0; hz < 8; hz++) {
            for (int hx = 0; hx < 8; hx++) {
                final int x = ci * 16 + hx * 2;
                final int z = cj * 16 + hz * 2;
                half.setPixelRGBA(x / 2, z / 2, average(full.getPixelRGBA(x, z), full.getPixelRGBA(x + 1, z),
                        full.getPixelRGBA(x, z + 1), full.getPixelRGBA(x + 1, z + 1)));
            }
        }
    }

    static int surround(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final int l = (int) (0.3 * r + 0.59 * g + 0.11 * b);
        final int v = (int) (l * 0.55);
        return 0xFF000000 | v << 16 | v << 8 | Math.min(255, (int) (v * 1.04));
    }

    private static int over(int top, int under) {
        final int a = top >>> 24;
        if (a >= 255) {
            return top;
        }
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final int t = (top >> shift) & 0xFF;
            final int u = (under >> shift) & 0xFF;
            out |= ((t * a + u * (255 - a)) / 255) << shift;
        }
        return out;
    }

    private static int average(int a, int b, int c, int d) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            final int sum = ((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF) + ((c >>> shift) & 0xFF)
                    + ((d >>> shift) & 0xFF);
            out |= ((sum + 2) / 4) << shift;
        }
        return out;
    }

    static int abgrToArgb(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
    }

    static int argbToAbgr(int argb) {
        return abgrToArgb(argb);
    }

    private void startJourneyMap(Minecraft mc) {
        final Path game = mc.gameDirectory.toPath();
        final boolean single = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
        final String save = single ? saveFolder(mc) : "";
        final ServerData entry = mc.getCurrentServer();
        final String server = JourneyMapPaths.serverFolder(entry == null ? null : entry.name,
                entry == null ? null : entry.ip);
        final String dimension = mc.level == null ? "minecraft:overworld" : mc.level.dimension().location().toString();
        final int bx0 = chunkX0 * 16;
        final int bz0 = chunkZ0 * 16;
        CompletableFuture.supplyAsync(() -> read(game, single, save, server, dimension, bx0, bz0),
                        Util.backgroundExecutor())
                .whenComplete((pixels, error) -> {
                    if (error != null) {
                        LOG.warn("Spark Crown map: could not read JourneyMap's tiles", error);
                    }
                    arrived = error == null ? pixels : null;
                    arrivedLooked = true;
                });
    }

    private static String saveFolder(Minecraft mc) {
        try {
            final Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return root.getFileName() == null ? "" : root.getFileName().toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static int[] read(Path game, boolean single, String save, String server, String dimension, int bx0,
                              int bz0) {
        final Path mpDir = game.resolve("journeymap").resolve("data").resolve("mp");
        final List<JourneyMapPaths.Folder> mpFolders = new ArrayList<>();
        if (!single && Files.isDirectory(mpDir)) {
            try (Stream<Path> s = Files.list(mpDir)) {
                s.filter(Files::isDirectory).forEach(p -> {
                    try {
                        mpFolders.add(new JourneyMapPaths.Folder(p.getFileName().toString(),
                                Files.getLastModifiedTime(p).toMillis()));
                    } catch (java.io.IOException ignored) {
                    }
                });
            } catch (java.io.IOException e) {
                return null;
            }
        }
        Path world = null;
        String dimFolder = null;
        for (Path w : JourneyMapPaths.worldDirs(game, single, save, server, Files::isDirectory, mpFolders)) {
            final String d = JourneyMapPaths.dimensionFolder(dimension, name -> Files.isDirectory(w.resolve(name)));
            if (Files.isDirectory(w.resolve(d).resolve("day"))) {
                world = w;
                dimFolder = d;
                break;
            }
        }
        if (world == null) {
            return null;
        }
        int[] out = null;
        for (int rz = Math.floorDiv(bz0, 512); rz <= Math.floorDiv(bz0 + BLOCKS - 1, 512); rz++) {
            for (int rx = Math.floorDiv(bx0, 512); rx <= Math.floorDiv(bx0 + BLOCKS - 1, 512); rx++) {
                final Path tile = JourneyMapPaths.dayTile(world, dimFolder, rx, rz);
                if (!Files.isRegularFile(tile)) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(tile); NativeImage img = NativeImage.read(in)) {
                    if (out == null) {
                        out = new int[BLOCKS * BLOCKS];
                    }
                    final int ox = rx * 512 - bx0;
                    final int oz = rz * 512 - bz0;
                    final int w = Math.min(512, img.getWidth());
                    final int h = Math.min(512, img.getHeight());
                    for (int y = 0; y < h; y++) {
                        final int wz = oz + y;
                        if (wz < 0 || wz >= BLOCKS) {
                            continue;
                        }
                        for (int x = 0; x < w; x++) {
                            final int wx = ox + x;
                            if (wx >= 0 && wx < BLOCKS) {
                                out[wz * BLOCKS + wx] = abgrToArgb(img.getPixelRGBA(x, y));
                            }
                        }
                    }
                } catch (java.io.IOException | RuntimeException e) {
                    LOG.debug("Spark Crown map: JourneyMap tile {} unreadable", tile, e);
                }
            }
        }
        return out;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        final Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().release(fullId);
        mc.getTextureManager().release(halfId);
    }
}
