package org.doscolas.security;

import org.doscolas.json.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the hand-rolled Fintoc-JWS-Signature header (RS256, detached signature) that
 * {@link JwsSigner} attaches to Transfers requests. There's no Fintoc sandbox to round-trip
 * against here, so correctness is checked the same way Fintoc's own verifier would: rebuild the
 * signing input from the header value and verify it against the matching public key.
 */
class JwsSignerTest {

    private static PublicKey publicKey;
    private static JwsSigner signer;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = keyPair.getPublic();

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        signer = new JwsSigner(pem);
    }

    @Test
    void producesASignatureThatVerifiesAgainstTheMatchingPublicKey() throws Exception {
        String body = Json.write(Map.of("amount", 15000, "currency", "CLP"));
        String header = signer.sign(body);

        assertTrue(verifies(header, body), "signature should verify against the signer's own public key");
    }

    @Test
    void headerCarriesTheDocumentedAlgNonceTsAndCritFields() {
        String header = signer.sign("{}");
        String[] parts = header.split("\\.");
        assertEquals(2, parts.length, "detached JWS: protectedHeader.signature, no embedded payload segment");

        String decodedHeader = new String(Base64.getUrlDecoder().decode(parts[0]));
        assertTrue(decodedHeader.contains("\"alg\":\"RS256\""));
        assertTrue(decodedHeader.contains("\"nonce\":\""));
        assertTrue(decodedHeader.contains("\"ts\":"));
        assertTrue(decodedHeader.contains("\"crit\":[\"ts\",\"nonce\"]"));
    }

    @Test
    void signatureIsBoundToTheExactRequestBody() throws Exception {
        String originalBody = Json.write(Map.of("amount", 15000, "account_id", "acc_1"));
        String tamperedBody = Json.write(Map.of("amount", 999999999, "account_id", "acc_1"));
        String header = signer.sign(originalBody);

        assertTrue(verifies(header, originalBody));
        assertFalse(verifies(header, tamperedBody), "a header signed for one body must not verify against a different body");
    }

    @Test
    void twoSignaturesOverTheSameBodyDifferBecauseOfTheNonce() {
        String body = "{\"amount\":1000}";
        String first = signer.sign(body);
        String second = signer.sign(body);

        assertNotEquals(first, second, "each call mints a fresh nonce/ts, so replaying an old header must not equal a new one");
    }

    /** Mirrors what a verifier on the receiving end would do: reconstruct base64url(header) + "." + base64url(rawBody). */
    private boolean verifies(String jwsHeaderValue, String rawBody) throws Exception {
        String[] parts = jwsHeaderValue.split("\\.");
        String headerB64 = parts[0];
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
        String bodyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBody.getBytes());
        String signingInput = headerB64 + "." + bodyB64;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(signingInput.getBytes());
        return signature.verify(signatureBytes);
    }
}
