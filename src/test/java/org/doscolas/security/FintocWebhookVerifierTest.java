package org.doscolas.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code Fintoc-Signature} HMAC gate that {@link org.doscolas.controller.FintocWebhookController}
 * relies on to trust webhook POSTs — the only thing standing between an attacker and forging a
 * "payment finished" or "transfer succeeded" event.
 */
class FintocWebhookVerifierTest {

    private static final String SECRET = "whsec_test_secret";

    @Test
    void acceptsAHeaderComputedWithTheCorrectSecret() throws Exception {
        FintocWebhookVerifier verifier = new FintocWebhookVerifier(SECRET);
        String body = "{\"type\":\"checkout_session.finished\",\"data\":{\"object\":{\"id\":\"cs_1\",\"status\":\"finished\"}}}";
        String header = signedHeader(SECRET, "1700000000", body);

        assertTrue(verifier.isValid(header, body));
    }

    @Test
    void rejectsASignatureComputedWithTheWrongSecret() throws Exception {
        FintocWebhookVerifier verifier = new FintocWebhookVerifier(SECRET);
        String body = "{\"id\":\"cs_1\"}";
        String header = signedHeader("a_completely_different_secret", "1700000000", body);

        assertFalse(verifier.isValid(header, body));
    }

    @Test
    void rejectsWhenTheBodyWasTamperedAfterSigning() throws Exception {
        FintocWebhookVerifier verifier = new FintocWebhookVerifier(SECRET);
        String signedBody = "{\"id\":\"cs_1\",\"status\":\"finished\"}";
        String header = signedHeader(SECRET, "1700000000", signedBody);
        String tamperedBody = "{\"id\":\"cs_1\",\"status\":\"expired\"}";

        assertFalse(verifier.isValid(header, tamperedBody));
    }

    @Test
    void rejectsAMissingOrMalformedSignatureHeader() {
        FintocWebhookVerifier verifier = new FintocWebhookVerifier(SECRET);
        String body = "{\"id\":\"cs_1\"}";

        assertFalse(verifier.isValid(null, body));
        assertFalse(verifier.isValid("", body));
        assertFalse(verifier.isValid("garbage-not-key-value-pairs", body));
        assertFalse(verifier.isValid("t=1700000000", body)); // missing v1
        assertFalse(verifier.isValid("v1=deadbeef", body)); // missing t
    }

    @Test
    void rejectsANullBody() throws Exception {
        FintocWebhookVerifier verifier = new FintocWebhookVerifier(SECRET);
        String header = signedHeader(SECRET, "1700000000", "{}");

        assertFalse(verifier.isValid(header, null));
    }

    /** Builds a {@code t=<ts>,v1=<hex hmac>} header the way Fintoc's own signer would. */
    private static String signedHeader(String secret, String timestamp, String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes());
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
