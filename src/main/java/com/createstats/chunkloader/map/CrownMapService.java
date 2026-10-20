package com.createstats.chunkloader.map;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.CrownConfig;
import com.createstats.chunkloader.CrownRegistry;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.net.CrownMapDataPacket;
import com.createstats.chunkloader.net.CrownMapRequestPacket;
import com.createstats.chunkloader.net.CrownPickPacket;
import com.createstats.chunkloader.net.CrownPickReplyPacket;
import com.createstats.flight.bridge.SableBridge;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrownMapService {
    static final int PICTURE_TICKS = 10;
    private static final Map<UUID, Long> LAST_PICTURE = new HashMap<>();

    private CrownMapService() {
    }

    private static SparkCrownBE crownFor(ServerPlayer player, BlockPos pos) {
        final ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SparkCrownBE be)) {
            return null;
        }
        return SableBridge.isPlayerInInteractionRangeFor(be, player) ? be : null;
    }

    public static void answer(ServerPlayer player, CrownMapRequestPacket p) {
        final SparkCrownBE be = crownFor(player, p.crown());
        if (be == null) {
            return;
        }
        final ServerLevel level = player.serverLevel();
        final long now = level.getGameTime();
        final Long last = LAST_PICTURE.get(player.getUUID());
        if (last != null && now - last < PICTURE_TICKS && now >= last) {
            return;
        }
        LAST_PICTURE.put(player.getUUID(), now);
        final BlockPos pos = be.getBlockPos();
        final int wx = CrownRegion.regionOf(pos.getX() >> 4) * CrownRegion.CHUNKS - CrownMapView.SURROUND;
        final int wz = CrownRegion.regionOf(pos.getZ() >> 4) * CrownRegion.CHUNKS - CrownMapView.SURROUND;
        final int x0 = Math.max(wx, p.chunkX());
        final int z0 = Math.max(wz, p.chunkZ());
        final int x1 = Math.min(wx + CrownMapView.WINDOW, p.chunkX() + Math.max(0, p.size()));
        final int z1 = Math.min(wz + CrownMapView.WINDOW, p.chunkZ() + Math.max(0, p.size()));
        if (x1 <= x0 || z1 <= z0) {
            return;
        }
        final CrownLaw law = CrownConfig.law();
        final long[] others = CrownRegistry.get(level).heldIn(CrownRegistry.dimensionOf(level), x0, z0, x1, z1,
                pos.asLong());
        final CrownSamples store = CrownSamples.get(level);
        final LongArrayList chunks = new LongArrayList();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean first = true;
        for (int cz = z0; cz < z1; cz++) {
            for (int cx = x0; cx < x1; cx++) {
                final byte[] s = store.of(cx, cz);
                if (s == null) {
                    continue;
                }
                chunks.add(CrownRegion.pack(cx, cz));
                bytes.writeBytes(s);
                if (chunks.size() == CrownMapDataPacket.SLICE) {
                    send(player, new CrownMapDataPacket(pos, law, chunks.toLongArray(), bytes.toByteArray(), 0, 0, 0,
                            0, new byte[0], first ? others : new long[0]));
                    first = false;
                    chunks.clear();
                    bytes.reset();
                }
            }
        }
        if (first || !chunks.isEmpty()) {
            send(player, new CrownMapDataPacket(pos, law, chunks.toLongArray(), bytes.toByteArray(), 0, 0, 0, 0,
                    new byte[0], first ? others : new long[0]));
        }
        CrownSamples.refresh(level, x0, z0, x1, z1);
        for (int rz = CrownRegion.regionOf(z0); rz <= CrownRegion.regionOf(z1 - 1); rz++) {
            for (int rx = CrownRegion.regionOf(x0); rx <= CrownRegion.regionOf(x1 - 1); rx++) {
                final int grx = rx;
                final int grz = rz;
                CrownGuesses.whenReady(level, rx, rz, guesses -> sendGuesses(player, level, pos, law, guesses, grx, grz,
                        x0, z0, x1, z1));
            }
        }
    }

    private static void sendGuesses(ServerPlayer player, ServerLevel level, BlockPos pos, CrownLaw law, byte[] guesses,
                                    int rx, int rz, int x0, int z0, int x1, int z1) {
        if (player.hasDisconnected() || player.serverLevel() != level) {
            return;
        }
        final int gx0 = Math.max(x0, rx * CrownRegion.CHUNKS);
        final int gz0 = Math.max(z0, rz * CrownRegion.CHUNKS);
        final int gx1 = Math.min(x1, (rx + 1) * CrownRegion.CHUNKS);
        final int gz1 = Math.min(z1, (rz + 1) * CrownRegion.CHUNKS);
        final int w = gx1 - gx0;
        final int h = gz1 - gz0;
        if (w <= 0 || h <= 0) {
            return;
        }
        final byte[] cut = new byte[w * h];
        for (int j = 0; j < h; j++) {
            System.arraycopy(guesses, (gz0 + j - rz * CrownRegion.CHUNKS) * CrownRegion.CHUNKS
                    + (gx0 - rx * CrownRegion.CHUNKS), cut, j * w, w);
        }
        send(player, new CrownMapDataPacket(pos, law, new long[0], new byte[0], gx0, gz0, w, h, cut, new long[0]));
    }

    public static void pick(ServerPlayer player, CrownPickPacket p) {
        final SparkCrownBE be = crownFor(player, p.crown());
        if (be == null) {
            return;
        }
        final List<CrownPicks.Verdict> verdicts = new ArrayList<>();
        long at = 0L;
        for (long chunk : p.chunks()) {
            if (be.hasPick(chunk) != p.drop()) {
                continue;
            }
            final CrownPicks.Verdict v = be.togglePick(player, chunk);
            verdicts.add(v);
            at = chunk;
            if (!v.changes()) {
                break;
            }
        }
        if (!verdicts.isEmpty()) {
            be.announce(player, verdicts.get(verdicts.size() - 1));
        }
        send(player, CrownPickReplyPacket.of(be.getBlockPos(), p.drop(), p.chunks().length, verdicts, at));
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        PacketDistributor.sendToPlayer(player, p);
    }
}
