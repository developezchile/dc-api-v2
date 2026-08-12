package org.doscolas.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Hand-rolled RS256 JWS signer for Fintoc's Transfers API, which rejects money-moving requests
 * without a {@code Fintoc-JWS-Signature} header (see docs.fintoc.com/docs/setting-up-jws-keys).
 * Mirrors {@link JwtService}'s "just base64url + a signature, no library" approach.
 *
 * <p>Signing input is {@code base64url(protectedHeader) + "." + base64url(rawRequestBody)}; the
 * header value sent on the wire is {@code base64url(protectedHeader) + "." + base64url(signature)}
 * (a detached signature — the payload travels in the actual HTTP body, not inside the JWS).
 */
public final class JwsSigner {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;

    public JwsSigner(String pkcs8PemPrivateKey) {
        this.privateKey = parsePrivateKey(pkcs8PemPrivateKey);
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse FINTOC_JWS_PRIVATE_KEY — must be a PKCS8 PEM RSA private key "
                            + "(openssl pkcs8 -topk8 -inform PEM -in key.pem -out key8.pem -nocrypt)", e);
        }
    }

    /** Builds the {@code Fintoc-JWS-Signature} header value for a request whose body is {@code rawBody}. */
    public String sign(String rawBody) {
        String header = "{\"alg\":\"RS256\",\"nonce\":\"" + UUID.randomUUID()
                + "\",\"ts\":" + (System.currentTimeMillis() / 1000) + ",\"crit\":[\"ts\",\"nonce\"]}";
        String headerB64 = B64.encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String bodyB64 = B64.encodeToString(rawBody.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + bodyB64;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return headerB64 + "." + B64.encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Fintoc JWS signature", e);
        }
    }
}
