package org.doscolas.service;

import org.doscolas.exception.FintocApiException;
import org.doscolas.exception.PayoutProviderException;
import org.doscolas.json.Json;
import org.doscolas.security.JwsSigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Hand-rolled client for the two Fintoc products this app needs (no vendor SDK, matching
 * everything else in dc-api-v2):
 * <ul>
 *   <li><b>Checkout Sessions</b> — collecting payment from owners. A hosted, Fintoc-redirect flow
 *       ({@code POST /v1/checkout_sessions} returns a {@code redirect_url}; the browser is sent
 *       there directly, no client-side widget/JS SDK involved). Authenticated with a bare
 *       {@code Authorization: <secret key>} header. Confirmed against the live API on 2026-08-07:
 *       this resource lives under {@code /v1/}, not {@code /v2/} — {@code POST /v2/checkout_sessions}
 *       404s with {@code unrecognized_request} even though {@code /v2/accounts} and
 *       {@code /v2/transfers} both resolve fine, so Fintoc's API versioning is per-resource here,
 *       not a single global version.</li>
 *   <li><b>Transfers</b> — paying sitters out. {@code POST /v2/transfers} additionally requires a
 *       {@code Fintoc-JWS-Signature} header (RS256 over the exact request body) on every request —
 *       see {@link JwsSigner}. If no JWS key is configured, transfer calls fail fast and loud
 *       rather than silently no-op.</li>
 * </ul>
 */
public final class FintocClient {

    private final String apiUrl;
    private final String secretKey;
    private final String accountId;
    private final JwsSigner jwsSigner;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FintocClient(String apiUrl, String secretKey, String accountId, JwsSigner jwsSigner) {
        this.apiUrl = apiUrl;
        this.secretKey = secretKey;
        this.accountId = accountId;
        this.jwsSigner = jwsSigner;
    }

    public record CheckoutSessionResult(String id, String status, String redirectUrl) {
    }

    public record TransferResult(String id, String status) {
    }

    public record TransferCounterparty(String holderId, String holderName, String accountNumber,
                                        String accountType, String institutionId) {
        Map<String, Object> toJson() {
            Map<String, Object> m = Json.obj();
            m.put("holder_id", holderId);
            m.put("holder_name", holderName);
            m.put("account_number", accountNumber);
            m.put("account_type", accountType);
            m.put("institution_id", institutionId);
            return m;
        }
    }

    public CheckoutSessionResult createCheckoutSession(long amount, String currency, String successUrl, String cancelUrl) {
        Map<String, Object> body = Json.obj();
        body.put("amount", (double) amount);
        body.put("currency", currency);
        body.put("success_url", successUrl);
        body.put("cancel_url", cancelUrl);
        return toCheckoutSessionResult(sendCheckout("POST", "/v1/checkout_sessions", Json.write(body)));
    }

    public CheckoutSessionResult getCheckoutSession(String id) {
        return toCheckoutSessionResult(sendCheckout("GET", "/v1/checkout_sessions/" + id, null));
    }

    private CheckoutSessionResult toCheckoutSessionResult(Map<String, Object> json) {
        return new CheckoutSessionResult((String) json.get("id"), (String) json.get("status"), (String) json.get("redirect_url"));
    }

    private Map<String, Object> sendCheckout(String method, String path, String rawBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Authorization", secretKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15));
            builder = "GET".equals(method) ? builder.GET() : builder.POST(HttpRequest.BodyPublishers.ofString(rawBody));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new FintocApiException("Fintoc checkout session request failed (" + response.statusCode() + "): " + response.body());
            }
            return Json.parseObject(response.body());
        } catch (FintocApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FintocApiException("Failed to reach Fintoc", e);
        }
    }

    public TransferResult initiateTransfer(String idempotencyKey, long amount, String currency,
                                            TransferCounterparty counterparty, String comment) {
        Map<String, Object> body = Json.obj();
        body.put("amount", (double) amount);
        body.put("currency", currency);
        body.put("account_id", accountId);
        body.put("comment", comment);
        body.put("counterparty", counterparty.toJson());
        return toTransferResult(sendTransfer("POST", "/v2/transfers", Json.write(body), idempotencyKey));
    }

    public TransferResult getTransfer(String id) {
        return toTransferResult(sendTransfer("GET", "/v2/transfers/" + id, "", null));
    }

    private TransferResult toTransferResult(Map<String, Object> json) {
        return new TransferResult((String) json.get("id"), (String) json.get("status"));
    }

    private Map<String, Object> sendTransfer(String method, String path, String rawBody, String idempotencyKey) {
        if (jwsSigner == null) {
            throw new PayoutProviderException(
                    "FINTOC_JWS_PRIVATE_KEY is not configured — cannot sign Transfers requests", false);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Authorization", secretKey)
                    .header("Fintoc-JWS-Signature", jwsSigner.sign(rawBody == null ? "" : rawBody))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15));
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            builder = "GET".equals(method) ? builder.GET() : builder.POST(HttpRequest.BodyPublishers.ofString(rawBody));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 != 2) {
                boolean retryable = status >= 500 || status == 429;
                throw new PayoutProviderException("Fintoc transfer request failed (" + status + "): " + response.body(), retryable);
            }
            return Json.parseObject(response.body());
        } catch (PayoutProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new PayoutProviderException("Failed to reach Fintoc", true, e);
        }
    }
}
