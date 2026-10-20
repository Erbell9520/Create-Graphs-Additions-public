package com.createstats.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

// uuid alone is one key to every server running this. second factor
public final class SupportKeyProof {
    public static final String KEY_FILE_NAME = "STATS_TIER2_KEY";

    static final String CREATOR_PUBLIC_KEY = "MCowBQYDK2VwAyEAlk/MHRpCKuKgp9nXOiXU/TQXZoQcTtEmVsGo9UT0CaA=";

    public static final int NONCE_BYTES = 32;

    public static final int MAX_SIGNATURE_BYTES = 64;

    private static final byte[] CONTEXT = "create_stats/support-proof/v1".getBytes(StandardCharsets.US_ASCII);

    private static final String ALGORITHM = "Ed25519";

    private SupportKeyProof() {
    }

    public static byte[] message(UUID player, byte[] nonce, byte[] serverLoginKey) {
        byte[] serverDigest = sha256(serverLoginKey);
        ByteBuffer buffer = ByteBuffer.allocate(CONTEXT.length + 16 + nonce.length + serverDigest.length);
        buffer.put(CONTEXT);
        buffer.putLong(player.getMostSignificantBits());
        buffer.putLong(player.getLeastSignificantBits());
        buffer.put(nonce);
        buffer.put(serverDigest);
        return buffer.array();
    }

    public static byte[] sign(PrivateKey key, UUID player, byte[] nonce, byte[] serverLoginKey)
        throws GeneralSecurityException {
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(message(player, nonce, serverLoginKey));
        return signer.sign();
    }

    public static boolean verify(PublicKey key, UUID player, byte[] nonce, byte[] serverLoginKey, byte[] signature) {
        if (key == null || player == null || nonce == null || nonce.length != NONCE_BYTES
            || serverLoginKey == null || serverLoginKey.length == 0
            || signature == null || signature.length == 0 || signature.length > MAX_SIGNATURE_BYTES) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(message(player, nonce, serverLoginKey));
            return verifier.verify(signature);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    public static PublicKey creatorPublicKey() {
        return publicKey(CREATOR_PUBLIC_KEY);
    }

    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("support public key is not a valid Ed25519 key", e);
        }
    }

    public static PrivateKey readPrivateKey(Path file) throws IOException, GeneralSecurityException {
        return parsePrivateKey(Files.readString(file, StandardCharsets.US_ASCII));
    }

    public static PrivateKey parsePrivateKey(String text) throws GeneralSecurityException {
        String body = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("key file is not Base64", e);
        }
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
