package com.createstats.cinecam.film;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class MediaFoundationWriter implements AutoCloseable {
    private static final int QUERY_INTERFACE = 0;
    private static final int RELEASE = 2;

    private static final int GET_ALLOCATED_STRING = 13;
    private static final int SET_UINT32 = 21;
    private static final int SET_UINT64 = 22;
    private static final int SET_GUID = 24;

    private static final int SET_SAMPLE_TIME = 36;
    private static final int SET_SAMPLE_DURATION = 38;
    private static final int ADD_BUFFER = 42;

    private static final int LOCK = 3;
    private static final int UNLOCK = 4;
    private static final int SET_CURRENT_LENGTH = 6;

    private static final int ADD_STREAM = 3;
    private static final int SET_INPUT_MEDIA_TYPE = 4;
    private static final int BEGIN_WRITING = 5;
    private static final int WRITE_SAMPLE = 6;
    private static final int FINALIZE = 11;

    private static final int GET_TRANSFORM_FOR_STREAM = 14;

    private static final int GET_ATTRIBUTES = 8;

    private static final int MF_VERSION = 0x00020070;
    private static final int MFSTARTUP_LITE = 1;
    private static final int COINIT_MULTITHREADED = 0;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    private static final String MF_MT_MAJOR_TYPE = "48eba18e-f8c9-4687-bf11-0a74c9f96a8f";
    private static final String MF_MT_SUBTYPE = "f7e34c9a-42e8-4714-b74b-cb29d72c35e5";
    private static final String MF_MT_FRAME_SIZE = "1652c33d-d6b2-4012-b834-72030849a37d";
    private static final String MF_MT_FRAME_RATE = "c459a2e8-3d2c-4e44-b132-fee5156c7bb0";
    private static final String MF_MT_PIXEL_ASPECT_RATIO = "c6376a1e-8d0a-4027-be45-6d9a0ad39bb6";
    private static final String MF_MT_INTERLACE_MODE = "e2724bb8-e676-4806-b4b2-a8d6efb44ccd";
    private static final String MF_MT_AVG_BITRATE = "20332624-fb0d-4d9e-bd0d-cbf6786c102e";
    private static final String MF_MT_DEFAULT_STRIDE = "644b4e48-1e02-4516-b0eb-c01ca9d49ac6";
    private static final String MF_MT_MPEG2_PROFILE = "ad76a80b-2d5c-4e0b-b375-64e520137036";
    private static final String MF_MT_YUV_MATRIX = "3e23d450-2c75-4d25-a00e-b91670d12327";
    private static final String MF_MT_VIDEO_NOMINAL_RANGE = "c21b8ee5-b956-4071-8daf-325edf5cab11";
    private static final String MF_MT_VIDEO_PRIMARIES = "dbfbe4d7-0740-4ee0-8192-850ab0e21935";
    private static final String MF_MT_TRANSFER_FUNCTION = "5fb0fce9-be5c-4935-a811-ec838f8eed93";
    private static final String MF_MEDIA_TYPE_VIDEO = "73646976-0000-0010-8000-00aa00389b71";
    private static final String MF_VIDEO_FORMAT_H264 = "34363248-0000-0010-8000-00aa00389b71";
    private static final String MF_VIDEO_FORMAT_NV12 = "3231564e-0000-0010-8000-00aa00389b71";
    private static final String MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS = "a634a91c-822b-41b9-a494-4de4643612b0";
    private static final String MF_TRANSCODE_CONTAINERTYPE = "150ff23f-4abc-478b-ac4f-e1916fba1cca";
    private static final String MF_CONTAINER_FMPEG4 = "9ba876f1-419f-4b77-a1e0-35959d9d4004";
    private static final String MF_CONTAINER_MPEG4 = "dc6cd05d-b9d0-40ef-bd35-fa622c1ab28a";
    private static final String IID_IMF_SINK_WRITER_EX = "588d72ab-5bc1-496a-8714-b70617141b25";
    private static final String MFT_CATEGORY_VIDEO_ENCODER = "f79eac7d-e545-4387-bdee-d647d7bde42a";
    private static final String MFT_FRIENDLY_NAME = "314ffbae-5b41-4c95-9c19-4e7d586face3";
    private static final String MFT_ENUM_HARDWARE_URL = "2fb866ac-b078-4942-ab6c-003d05cda674";

    private static final String MF_MEDIA_TYPE_AUDIO = "73647561-0000-0010-8000-00aa00389b71";
    private static final String MF_AUDIO_FORMAT_AAC = "00001610-0000-0010-8000-00aa00389b71";
    private static final String MF_AUDIO_FORMAT_PCM = "00000001-0000-0010-8000-00aa00389b71";
    private static final String MF_MT_AUDIO_NUM_CHANNELS = "37e48bf5-645e-4c5b-89de-ada9e29b696a";
    private static final String MF_MT_AUDIO_SAMPLES_PER_SECOND = "5faeeae7-0290-4c31-9e8a-c534f68d9dba";
    private static final String MF_MT_AUDIO_AVG_BYTES_PER_SECOND = "1aab75c8-cfef-451c-ab95-ac034b8e1731";
    private static final String MF_MT_AUDIO_BLOCK_ALIGNMENT = "322de230-9eeb-43bd-ab7a-ff412251541d";
    private static final String MF_MT_AUDIO_BITS_PER_SAMPLE = "f2deb57f-40fa-4764-aa33-ed4f2d1ff669";

    public static final int AUDIO_CHANNELS = 2;

    private static final int AAC_BYTES_PER_SECOND = 24000;

    private static final int PROGRESSIVE = 2;
    private static final int H264_PROFILE_HIGH = 100;
    private static final int MATRIX_BT709 = 1;
    private static final int RANGE_16_235 = 2;
    private static final int PRIMARIES_BT709 = 2;
    private static final int TRANSFER_709 = 5;

    interface MfPlat extends Library {
        int MFStartup(int version, int flags);

        int MFShutdown();

        int MFCreateAttributes(PointerByReference attributes, int initialSize);

        int MFCreateMediaType(PointerByReference type);

        int MFCreateMemoryBuffer(int maxLength, PointerByReference buffer);

        int MFCreateSample(PointerByReference sample);
    }

    interface MfReadWrite extends Library {
        int MFCreateSinkWriterFromURL(WString url, Pointer byteStream, Pointer attributes, PointerByReference writer);
    }

    interface Ole32 extends Library {
        int CoInitializeEx(Pointer reserved, int coInit);

        void CoUninitialize();

        void CoTaskMemFree(Pointer memory);
    }

    private static volatile Boolean available;

    private final MfPlat mf;
    private final Ole32 ole;
    private final int width;
    private final int height;
    private final int fps;
    private final boolean comJoined;
    private boolean started;
    @Nullable private Pointer writer;
    private int stream;
    @Nullable private String encoder;
    private boolean hardware;
    private boolean fragmented;
    private long written;

    @Nullable private Pointer last;

    private int audioStream = -1;
    private int audioRate;
    private long audioWritten;

    public static boolean available() {
        Boolean a = available;
        if (a == null) {
            boolean ok = false;
            try {
                if (Platform.isWindows() && Platform.is64Bit()) {
                    Native.load("mfplat", MfPlat.class);
                    Native.load("mfreadwrite", MfReadWrite.class);
                    ok = true;
                }
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                ok = false;
            }
            available = a = ok;
        }
        return a;
    }

    public MediaFoundationWriter(Path file, int width, int height, int fps, int bitrate) throws IOException {
        this(file, width, height, fps, bitrate, 0);
    }

    public MediaFoundationWriter(Path file, int width, int height, int fps, int bitrate, int audioRate) throws IOException {
        if (!available()) throw new IOException("Media Foundation is not available on this system");
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) {
            throw new IOException("an NV12 frame needs even sides, not " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.mf = Native.load("mfplat", MfPlat.class);
        this.ole = Native.load("ole32", Ole32.class);
        MfReadWrite rw = Native.load("mfreadwrite", MfReadWrite.class);
        int co = ole.CoInitializeEx(Pointer.NULL, COINIT_MULTITHREADED);
        if (co < 0 && co != RPC_E_CHANGED_MODE) throw new IOException("CoInitializeEx failed: " + hex(co));
        comJoined = co >= 0;
        boolean ok = false;
        try {
            check(mf.MFStartup(MF_VERSION, MFSTARTUP_LITE), "MFStartup");
            started = true;
            writer = openWriter(rw, file, true);
            if (writer == null) writer = openWriter(rw, file, false);
            if (writer == null) throw new IOException("MFCreateSinkWriterFromURL refused " + file);
            Pointer out = mediaType();
            try {
                setGuid(out, MF_MT_MAJOR_TYPE, MF_MEDIA_TYPE_VIDEO);
                setGuid(out, MF_MT_SUBTYPE, MF_VIDEO_FORMAT_H264);
                setUint32(out, MF_MT_AVG_BITRATE, bitrate);
                setUint32(out, MF_MT_INTERLACE_MODE, PROGRESSIVE);
                setUint64(out, MF_MT_FRAME_SIZE, ((long) width << 32) | height);
                setUint64(out, MF_MT_FRAME_RATE, ((long) fps << 32) | 1L);
                setUint64(out, MF_MT_PIXEL_ASPECT_RATIO, (1L << 32) | 1L);
                setUint32(out, MF_MT_MPEG2_PROFILE, H264_PROFILE_HIGH);
                colour(out);
                IntByReference index = new IntByReference();
                check(com(writer, ADD_STREAM, out, index), "AddStream");
                stream = index.getValue();
            } finally {
                release(out);
            }
            Pointer in = mediaType();
            try {
                setGuid(in, MF_MT_MAJOR_TYPE, MF_MEDIA_TYPE_VIDEO);
                setGuid(in, MF_MT_SUBTYPE, MF_VIDEO_FORMAT_NV12);
                setUint32(in, MF_MT_INTERLACE_MODE, PROGRESSIVE);
                setUint64(in, MF_MT_FRAME_SIZE, ((long) width << 32) | height);
                setUint64(in, MF_MT_FRAME_RATE, ((long) fps << 32) | 1L);
                setUint64(in, MF_MT_PIXEL_ASPECT_RATIO, (1L << 32) | 1L);
                setUint32(in, MF_MT_DEFAULT_STRIDE, width);
                colour(in);
                check(com(writer, SET_INPUT_MEDIA_TYPE, stream, in, Pointer.NULL), "SetInputMediaType");
            } finally {
                release(in);
            }
            if (audioRate > 0) addAudio(audioRate);
            check(com(writer, BEGIN_WRITING), "BeginWriting");
            readEncoder();
            ok = true;
        } finally {
            if (!ok) shutdown();
        }
    }

    private void addAudio(int rate) throws IOException {
        Pointer out = mediaType();
        try {
            setGuid(out, MF_MT_MAJOR_TYPE, MF_MEDIA_TYPE_AUDIO);
            setGuid(out, MF_MT_SUBTYPE, MF_AUDIO_FORMAT_AAC);
            setUint32(out, MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
            setUint32(out, MF_MT_AUDIO_SAMPLES_PER_SECOND, rate);
            setUint32(out, MF_MT_AUDIO_NUM_CHANNELS, AUDIO_CHANNELS);
            setUint32(out, MF_MT_AUDIO_AVG_BYTES_PER_SECOND, AAC_BYTES_PER_SECOND);
            IntByReference index = new IntByReference();
            check(com(writer, ADD_STREAM, out, index), "AddStream (sound)");
            audioStream = index.getValue();
        } finally {
            release(out);
        }
        Pointer in = mediaType();
        try {
            setGuid(in, MF_MT_MAJOR_TYPE, MF_MEDIA_TYPE_AUDIO);
            setGuid(in, MF_MT_SUBTYPE, MF_AUDIO_FORMAT_PCM);
            setUint32(in, MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
            setUint32(in, MF_MT_AUDIO_SAMPLES_PER_SECOND, rate);
            setUint32(in, MF_MT_AUDIO_NUM_CHANNELS, AUDIO_CHANNELS);
            setUint32(in, MF_MT_AUDIO_BLOCK_ALIGNMENT, 2 * AUDIO_CHANNELS);
            setUint32(in, MF_MT_AUDIO_AVG_BYTES_PER_SECOND, rate * 2 * AUDIO_CHANNELS);
            check(com(writer, SET_INPUT_MEDIA_TYPE, audioStream, in, Pointer.NULL), "SetInputMediaType (sound)");
        } finally {
            release(in);
        }
        audioRate = rate;
    }

    public boolean hasAudio() {
        return audioStream >= 0;
    }

    public long audioFrames() {
        return audioWritten;
    }

    // audio on the film's clock so a held frame doesn't pull it out of sync
    public void writeAudio(short[] pcm, int frames, long at) throws IOException {
        if (audioStream < 0 || frames <= 0) return;
        if (pcm.length < frames * AUDIO_CHANNELS) throw new IOException("short sound buffer");
        int bytes = frames * 2 * AUDIO_CHANNELS;
        PointerByReference pb = new PointerByReference();
        check(mf.MFCreateMemoryBuffer(bytes, pb), "MFCreateMemoryBuffer (sound)");
        Pointer buffer = pb.getValue();
        try {
            PointerByReference data = new PointerByReference();
            check(com(buffer, LOCK, data, Pointer.NULL, Pointer.NULL), "Lock (sound)");
            try {
                data.getValue().write(0, pcm, 0, frames * AUDIO_CHANNELS);
            } finally {
                com(buffer, UNLOCK);
            }
            check(com(buffer, SET_CURRENT_LENGTH, bytes), "SetCurrentLength (sound)");
            PointerByReference ps = new PointerByReference();
            check(mf.MFCreateSample(ps), "MFCreateSample (sound)");
            Pointer sample = ps.getValue();
            try {
                check(com(sample, ADD_BUFFER, buffer), "AddBuffer (sound)");
                long t0 = at * 10_000_000L / audioRate;
                long t1 = (at + frames) * 10_000_000L / audioRate;
                check(com(sample, SET_SAMPLE_TIME, t0), "SetSampleTime (sound)");
                check(com(sample, SET_SAMPLE_DURATION, t1 - t0), "SetSampleDuration (sound)");
                check(com(writer, WRITE_SAMPLE, audioStream, sample), "WriteSample (sound)");
                audioWritten += frames;
            } finally {
                release(sample);
            }
        } finally {
            release(buffer);
        }
    }

    private static void colour(Pointer type) throws IOException {
        setUint32(type, MF_MT_YUV_MATRIX, MATRIX_BT709);
        setUint32(type, MF_MT_VIDEO_NOMINAL_RANGE, RANGE_16_235);
        setUint32(type, MF_MT_VIDEO_PRIMARIES, PRIMARIES_BT709);
        setUint32(type, MF_MT_TRANSFER_FUNCTION, TRANSFER_709);
    }

    @Nullable
    private Pointer openWriter(MfReadWrite rw, Path file, boolean fragmentedMp4) throws IOException {
        Pointer attributes = attributes(2);
        try {
            setUint32(attributes, MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, 1);
            setGuid(attributes, MF_TRANSCODE_CONTAINERTYPE, fragmentedMp4 ? MF_CONTAINER_FMPEG4 : MF_CONTAINER_MPEG4);
            PointerByReference w = new PointerByReference();
            int hr = rw.MFCreateSinkWriterFromURL(new WString(file.toAbsolutePath().toString()), Pointer.NULL, attributes, w);
            if (hr < 0) return null;
            fragmented = fragmentedMp4;
            return w.getValue();
        } finally {
            release(attributes);
        }
    }

    private void readEncoder() {
        PointerByReference ex = new PointerByReference();
        if (com(writer, QUERY_INTERFACE, guid(IID_IMF_SINK_WRITER_EX), ex) < 0 || ex.getValue() == null) return;
        try {
            for (int i = 0; i < 8; i++) {
                Memory category = new Memory(16);
                PointerByReference transform = new PointerByReference();
                if (com(ex.getValue(), GET_TRANSFORM_FOR_STREAM, stream, i, category, transform) < 0) break;
                try {
                    if (!sameGuid(category, guid(MFT_CATEGORY_VIDEO_ENCODER))) continue;
                    PointerByReference attrs = new PointerByReference();
                    if (com(transform.getValue(), GET_ATTRIBUTES, attrs) < 0 || attrs.getValue() == null) continue;
                    try {
                        encoder = string(attrs.getValue(), MFT_FRIENDLY_NAME);
                        hardware = string(attrs.getValue(), MFT_ENUM_HARDWARE_URL) != null;
                    } finally {
                        release(attrs.getValue());
                    }
                } finally {
                    release(transform.getValue());
                }
            }
        } finally {
            release(ex.getValue());
        }
    }

    @Nullable
    private String string(Pointer attributes, String key) {
        PointerByReference value = new PointerByReference();
        IntByReference length = new IntByReference();
        if (com(attributes, GET_ALLOCATED_STRING, guid(key), value, length) < 0 || value.getValue() == null) return null;
        try {
            return value.getValue().getWideString(0);
        } finally {
            ole.CoTaskMemFree(value.getValue());
        }
    }

    @Nullable
    public String encoder() {
        return encoder;
    }

    public boolean hardware() {
        return hardware;
    }

    public boolean fragmented() {
        return fragmented;
    }

    public long frames() {
        return written;
    }

    public void write(byte[] nv12) throws IOException {
        int size = frameBytes();
        if (nv12.length < size) throw new IOException("an NV12 frame of " + width + "x" + height + " is " + size + " bytes");
        Pointer buffer = newBuffer();
        boolean kept = false;
        try {
            PointerByReference data = new PointerByReference();
            check(com(buffer, LOCK, data, Pointer.NULL, Pointer.NULL), "Lock");
            try {
                data.getValue().write(0, nv12, 0, size);
            } finally {
                com(buffer, UNLOCK);
            }
            submit(buffer);
            kept = true;
        } finally {
            if (!kept) release(buffer);
        }
    }

    public void write(ByteBuffer nv12, Runnable copied) throws IOException {
        Pointer buffer = null;
        boolean kept = false;
        try {
            int size = frameBytes();
            if (nv12.capacity() < size) throw new IOException("an NV12 frame of " + width + "x" + height + " is " + size + " bytes");
            buffer = newBuffer();
            PointerByReference data = new PointerByReference();
            check(com(buffer, LOCK, data, Pointer.NULL, Pointer.NULL), "Lock");
            try {
                ByteBuffer frame = data.getValue().getByteBuffer(0, size);
                frame.put(nv12.duplicate().clear().limit(size));
            } finally {
                com(buffer, UNLOCK);
            }
            copied.run();
            copied = null;
            submit(buffer);
            kept = true;
        } finally {
            if (copied != null) copied.run();
            if (!kept && buffer != null) release(buffer);
        }
    }

    public void repeat() throws IOException {
        if (last != null) submit(last);
    }

    private int frameBytes() throws IOException {
        if (writer == null) throw new IOException("the writer is closed");
        return width * height * 3 / 2;
    }

    private Pointer newBuffer() throws IOException {
        PointerByReference pb = new PointerByReference();
        check(mf.MFCreateMemoryBuffer(frameBytes(), pb), "MFCreateMemoryBuffer");
        return pb.getValue();
    }

    private void submit(Pointer buffer) throws IOException {
        check(com(buffer, SET_CURRENT_LENGTH, frameBytes()), "SetCurrentLength");
        PointerByReference ps = new PointerByReference();
        check(mf.MFCreateSample(ps), "MFCreateSample");
        Pointer sample = ps.getValue();
        try {
            check(com(sample, ADD_BUFFER, buffer), "AddBuffer");
            long at = time(written);
            check(com(sample, SET_SAMPLE_TIME, at), "SetSampleTime");
            check(com(sample, SET_SAMPLE_DURATION, time(written + 1) - at), "SetSampleDuration");
            check(com(writer, WRITE_SAMPLE, stream, sample), "WriteSample");
            written++;
        } finally {
            release(sample);
        }
        if (buffer != last) {
            release(last);
            last = buffer;
        }
    }

    private long time(long frame) {
        return frame * 10_000_000L / fps;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        if (writer != null) {
            int hr = com(writer, FINALIZE);
            if (hr < 0 && written > 0) failure = new IOException("Finalize failed: " + hex(hr));
        }
        shutdown();
        if (failure != null) throw failure;
    }

    private void shutdown() {
        release(last);
        last = null;
        if (writer != null) {
            release(writer);
            writer = null;
        }
        if (started) {
            mf.MFShutdown();
            started = false;
        }
        if (comJoined) ole.CoUninitialize();
    }

    private Pointer attributes(int size) throws IOException {
        PointerByReference p = new PointerByReference();
        check(mf.MFCreateAttributes(p, size), "MFCreateAttributes");
        return p.getValue();
    }

    private Pointer mediaType() throws IOException {
        PointerByReference p = new PointerByReference();
        check(mf.MFCreateMediaType(p), "MFCreateMediaType");
        return p.getValue();
    }

    private static void setUint32(Pointer attributes, String key, int value) throws IOException {
        check(com(attributes, SET_UINT32, guid(key), value), "SetUINT32 " + key);
    }

    private static void setUint64(Pointer attributes, String key, long value) throws IOException {
        check(com(attributes, SET_UINT64, guid(key), value), "SetUINT64 " + key);
    }

    private static void setGuid(Pointer attributes, String key, String value) throws IOException {
        check(com(attributes, SET_GUID, guid(key), guid(value)), "SetGUID " + key);
    }

    private static int com(Pointer self, int index, Object... args) {
        Pointer fn = self.getPointer(0).getPointer((long) index * Native.POINTER_SIZE);
        Object[] all = new Object[args.length + 1];
        all[0] = self;
        System.arraycopy(args, 0, all, 1, args.length);
        return Function.getFunction(fn).invokeInt(all);
    }

    private static void release(@Nullable Pointer p) {
        if (p != null) com(p, RELEASE);
    }

    static Memory guid(String s) {
        String h = s.replace("-", "");
        Memory m = new Memory(16);
        m.setInt(0, (int) Long.parseLong(h.substring(0, 8), 16));
        m.setShort(4, (short) Integer.parseInt(h.substring(8, 12), 16));
        m.setShort(6, (short) Integer.parseInt(h.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) m.setByte(8 + i, (byte) Integer.parseInt(h.substring(16 + 2 * i, 18 + 2 * i), 16));
        return m;
    }

    private static boolean sameGuid(Pointer a, Pointer b) {
        for (int i = 0; i < 16; i++) {
            if (a.getByte(i) != b.getByte(i)) return false;
        }
        return true;
    }

    private static void check(int hr, String what) throws IOException {
        if (hr < 0) throw new IOException(what + " failed: " + hex(hr));
    }

    private static String hex(int hr) {
        return String.format("0x%08X", hr);
    }
}
