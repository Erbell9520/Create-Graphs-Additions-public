package com.createstats.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.createstats.CreateStats;
import com.createstats.support.SupportPolicy.Basis;
import com.createstats.support.SupportPolicy.Level;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

// no client classes so dedicated servers can load the handlers
public final class SupportClientState {
    private static final long CHALLENGE_WINDOW_MS = 15_000;

    private static final long EXPORT_WINDOW_MS = 60_000;

    private static volatile byte[] serverLoginKey;

    private static volatile long helloSentAt;
    private static volatile long exportRequestedAt;

    private static volatile Level level = Level.NONE;
    private static volatile Basis basis = Basis.NONE;
    private static volatile String serverNote = "";
    private static volatile int grantSecondsLeft = -1;
    private static volatile long statusAt;

    private static volatile String localNote = "";

    private static List<String> exportBuffer;
    private static int exportExpected;
    private static int exportReceived;

    private SupportClientState() {
    }

    public static void captureServerKey(byte[] encoded) {
        serverLoginKey = encoded == null ? null : encoded.clone();
    }

    public static void reset() {
        serverLoginKey = null;
        helloSentAt = 0;
        exportRequestedAt = 0;
        level = Level.NONE;
        basis = Basis.NONE;
        serverNote = "";
        grantSecondsLeft = -1;
        localNote = "";
        exportBuffer = null;
        exportExpected = 0;
    }

    public static boolean canOpen(@Nullable Player player) {
        return SupportAccess.isCreator(player) || level.atLeast(Level.SAFE);
    }

    public static void acceptStatus(int levelOrdinal, String basisName, String note, int secondsLeft) {
        Level[] levels = Level.values();
        level = levelOrdinal >= 0 && levelOrdinal < levels.length ? levels[levelOrdinal] : Level.NONE;
        try {
            basis = Basis.valueOf(basisName);
        } catch (IllegalArgumentException e) {
            basis = Basis.NONE;
        }
        serverNote = note;
        grantSecondsLeft = secondsLeft;
        statusAt = System.currentTimeMillis();
        if (level.atLeast(Level.TIER2)) {
            localNote = "";
        }
    }

    public static Level level() {
        return level;
    }

    public static Basis basis() {
        return basis;
    }

    public static int grantSecondsLeft() {
        int left = grantSecondsLeft;
        return left < 0 ? -1 : (int) Math.max(0, left - (System.currentTimeMillis() - statusAt) / 1000);
    }

    public static String tier2Note() {
        return localNote.isEmpty() ? serverNote : localNote;
    }

    public static void markHelloSent() {
        helloSentAt = System.currentTimeMillis();
    }

    public static byte[] answerChallenge(UUID player, byte[] nonce) {
        if (!SupportAccess.CREATOR_UUID.equals(player)) {
            return refuse("");
        }
        if (System.currentTimeMillis() - helloSentAt > CHALLENGE_WINDOW_MS) {
            return refuse("");
        }
        byte[] loginKey = serverLoginKey;
        if (loginKey == null) {
            CreateStats.LOGGER.warn("[SUPPORT] key challenge arrived but no server login key was captured; "
                + "ClientHandshakeKeyCaptureMixin may not have applied");
            return refuse("no login key captured from this server's handshake");
        }
        Path file = keyFile();
        if (!Files.isRegularFile(file)) {
            return refuse(SupportKeyProof.KEY_FILE_NAME + " not found in " + file.getParent());
        }
        try {
            PrivateKey key = SupportKeyProof.readPrivateKey(file);
            byte[] signature = SupportKeyProof.sign(key, player, nonce, loginKey);
            localNote = "";
            return signature;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            return refuse(SupportKeyProof.KEY_FILE_NAME + " unreadable (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] refuse(String note) {
        localNote = note;
        return null;
    }

    public static Path keyFile() {
        return FMLPaths.GAMEDIR.get().resolve(SupportKeyProof.KEY_FILE_NAME);
    }

    public static void markExportRequested() {
        exportRequestedAt = System.currentTimeMillis();
        exportBuffer = null;
        exportExpected = 0;
    }

    public static synchronized @Nullable List<String> acceptExportChunk(int index, int total, List<String> lines) {
        if (System.currentTimeMillis() - exportRequestedAt > EXPORT_WINDOW_MS
            || total <= 0 || total > SupportExportPacket.MAX_CHUNKS) {
            exportBuffer = null;
            return null;
        }
        if (index == 0) {
            exportBuffer = new ArrayList<>();
            exportExpected = total;
            exportReceived = 0;
        }
        if (exportBuffer == null || total != exportExpected || index != exportReceived) {
            exportBuffer = null;
            return null;
        }
        exportBuffer.addAll(lines);
        exportReceived++;
        if (exportReceived < exportExpected) {
            return null;
        }
        List<String> complete = exportBuffer;
        exportBuffer = null;
        exportRequestedAt = 0;
        return complete;
    }
}
