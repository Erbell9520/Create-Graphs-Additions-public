package com.createstats.chunkloader.net;

import com.createstats.chunkloader.CrownScreenAccess;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.map.CrownMapView;
import com.createstats.chunkloader.map.CrownSampleShade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// sliced, a full window is ~256KiB of samples
public record CrownMapDataPacket(BlockPos crown, CrownLaw law, long[] sampleChunks, byte[] samples, int guessX,
                                 int guessZ, int guessW, int guessH, byte[] guesses, long[] others)
        implements CustomPacketPayload {
    public static final int SLICE = 1024;

    public static final int MAX_CHUNKS = CrownMapView.WINDOW * CrownMapView.WINDOW;

    public static final CustomPacketPayload.Type<CrownMapDataPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "crown_map_data"));

    public static final StreamCodec<FriendlyByteBuf, CrownMapDataPacket> STREAM_CODEC = StreamCodec.of(
            CrownMapDataPacket::write, CrownMapDataPacket::read);

    public CrownMapDataPacket {
        if (samples.length != sampleChunks.length * CrownSampleShade.PER_CHUNK) {
            throw new IllegalArgumentException("crown_map_data: " + samples.length + " sample bytes for "
                    + sampleChunks.length + " chunks");
        }
        if (guessW < 0 || guessH < 0 || guesses.length != guessW * guessH) {
            throw new IllegalArgumentException("crown_map_data: " + guesses.length + " guesses for " + guessW + " x "
                    + guessH);
        }
    }

    private static void write(FriendlyByteBuf buf, CrownMapDataPacket p) {
        buf.writeBlockPos(p.crown());
        buf.writeVarInt(p.law().baseFePerTick());
        buf.writeVarInt(p.law().growthPercent());
        buf.writeVarInt(p.law().maxPerStation());
        buf.writeVarInt(p.law().maxPerPlayer());
        buf.writeVarInt(p.law().rideSeconds());
        buf.writeVarInt(p.sampleChunks().length);
        for (long c : p.sampleChunks()) {
            buf.writeLong(c);
        }
        buf.writeBytes(p.samples());
        buf.writeInt(p.guessX());
        buf.writeInt(p.guessZ());
        buf.writeVarInt(p.guessW());
        buf.writeVarInt(p.guessH());
        buf.writeBytes(p.guesses());
        buf.writeVarInt(p.others().length);
        for (long c : p.others()) {
            buf.writeLong(c);
        }
    }

    private static CrownMapDataPacket read(FriendlyByteBuf buf) {
        final BlockPos crown = buf.readBlockPos();
        final CrownLaw law = new CrownLaw(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
        final int n = count(buf.readVarInt(), "sample chunks");
        final long[] chunks = new long[n];
        for (int i = 0; i < n; i++) {
            chunks[i] = buf.readLong();
        }
        final byte[] samples = new byte[n * CrownSampleShade.PER_CHUNK];
        buf.readBytes(samples);
        final int gx = buf.readInt();
        final int gz = buf.readInt();
        final int gw = count(buf.readVarInt(), "guess columns");
        final int gh = count(buf.readVarInt(), "guess rows");
        final byte[] guesses = new byte[count(gw * gh, "guesses")];
        buf.readBytes(guesses);
        final int o = count(buf.readVarInt(), "other crowns' chunks");
        final long[] others = new long[o];
        for (int i = 0; i < o; i++) {
            others[i] = buf.readLong();
        }
        return new CrownMapDataPacket(crown, law, chunks, samples, gx, gz, gw, gh, guesses, others);
    }

    private static int count(int n, String what) {
        if (n < 0 || n > MAX_CHUNKS) {
            throw new IllegalArgumentException("crown_map_data names " + n + " " + what + ", at most " + MAX_CHUNKS);
        }
        return n;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrownMapDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CrownScreenAccess.mapData(packet));
    }
}
