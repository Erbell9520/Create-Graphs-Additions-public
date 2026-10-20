package com.createstats.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SupportKeyProofTest {
    private static final UUID PLAYER = UUID.fromString("c8881ffb-c50b-4c86-a87e-aeddb3ac65b3");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static byte[] bytes(int length, int seed) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = (byte) (seed * 31 + i * 7);
        }
        return out;
    }

    private static KeyPair pair() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void validProofVerifies() throws Exception {
        KeyPair keys = pair();
        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 1);
        byte[] server = bytes(294, 2);
        byte[] signature = SupportKeyProof.sign(keys.getPrivate(), PLAYER, nonce, server);
        assertEquals(64, signature.length);
        assertTrue(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, signature));
    }

    @Test
    void relayedProofForAnotherServerFails() throws Exception {
        KeyPair keys = pair();
        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 1);
        byte[] serverX = bytes(294, 2);
        byte[] serverY = bytes(294, 3);
        byte[] signature = SupportKeyProof.sign(keys.getPrivate(), PLAYER, nonce, serverX);
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, serverY, signature));
    }

    @Test
    void proofDoesNotTransferBetweenChallengesAccountsOrKeys() throws Exception {
        KeyPair keys = pair();
        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 1);
        byte[] server = bytes(294, 2);
        byte[] signature = SupportKeyProof.sign(keys.getPrivate(), PLAYER, nonce, server);

        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, bytes(SupportKeyProof.NONCE_BYTES, 9), server, signature));
        assertFalse(SupportKeyProof.verify(keys.getPublic(), OTHER, nonce, server, signature));
        assertFalse(SupportKeyProof.verify(pair().getPublic(), PLAYER, nonce, server, signature));

        byte[] tampered = signature.clone();
        tampered[10] ^= 1;
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, tampered));
    }

    @Test
    void malformedInputIsInvalidNotAnException() throws Exception {
        KeyPair keys = pair();
        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 1);
        byte[] server = bytes(294, 2);
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, new byte[0]));
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, new byte[65]));
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, null));
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, new byte[5], server, new byte[64]));
        assertFalse(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, new byte[0], new byte[64]));
        assertFalse(SupportKeyProof.verify(null, PLAYER, nonce, server, new byte[64]));
    }

    @Test
    void embeddedCreatorKeyIsAValidEd25519PublicKey() {
        PublicKey key = SupportKeyProof.creatorPublicKey();
        assertNotNull(key);
        assertEquals("EdDSA", key.getAlgorithm());
        assertEquals(44, key.getEncoded().length);
    }

    @Test
    void privateKeyFileParsesWithLineBreaksAndPemWrapper() throws Exception {
        KeyPair keys = pair();
        String base64 = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String broken = "  " + base64.substring(0, 20) + "\r\n" + base64.substring(20) + "\n\n";
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";

        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 4);
        byte[] server = bytes(294, 5);
        for (String text : new String[]{base64, broken, pem}) {
            byte[] signature = SupportKeyProof.sign(SupportKeyProof.parsePrivateKey(text), PLAYER, nonce, server);
            assertTrue(SupportKeyProof.verify(keys.getPublic(), PLAYER, nonce, server, signature));
        }
        assertArrayEquals(keys.getPrivate().getEncoded(), SupportKeyProof.parsePrivateKey(pem).getEncoded());
    }

    @Test
    void garbageKeyFileIsRefused() {
        assertThrows(GeneralSecurityException.class, () -> SupportKeyProof.parsePrivateKey("not a key!"));
        assertThrows(GeneralSecurityException.class, () -> SupportKeyProof.parsePrivateKey("AAAA"));
    }

    @Test
    void messageBindsEveryField() {
        byte[] nonce = bytes(SupportKeyProof.NONCE_BYTES, 1);
        byte[] server = bytes(294, 2);
        byte[] base = SupportKeyProof.message(PLAYER, nonce, server);
        assertFalse(java.util.Arrays.equals(base, SupportKeyProof.message(OTHER, nonce, server)));
        assertFalse(java.util.Arrays.equals(base, SupportKeyProof.message(PLAYER, bytes(SupportKeyProof.NONCE_BYTES, 3), server)));
        assertFalse(java.util.Arrays.equals(base, SupportKeyProof.message(PLAYER, nonce, bytes(294, 4))));
    }
}
