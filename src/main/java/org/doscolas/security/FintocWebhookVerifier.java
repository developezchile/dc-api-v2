package org.doscolas.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the {@code Fintoc-Signature} header Fintoc sends on every webhook POST:
 * {@code t=<unix-seconds>,v1=<hex hmac>}, where the hmac is HMAC-SHA256 over
 * {@code "<timestamp>.<rawBody>"} keyed with the webhook signing secret.
 */
public final class FintocWebhookVerifier {

    private final byte[] secretBytes;

    public FintocWebhookVerifier(String webhookSecret) {
        this.secretBytes = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String signatureHeader, String rawBody) {
        if (signatureHeader == null || rawBody == null) return false;
        String timestamp = null;
        String signatureHex = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) timestamp = kv[1];
            if ("v1".equals(kv[0])) signatureHex = kv[1];
        }
        if (timestamp == null || signatureHex == null) return false;

        try {
            byte[] expected = hmacSha256(timestamp + "." + rawBody);
            byte[] actual = HexFormat.of().parseHex(signatureHex);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
