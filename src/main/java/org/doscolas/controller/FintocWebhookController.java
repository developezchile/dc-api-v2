package org.doscolas.controller;

import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.json.Json;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.security.FintocWebhookVerifier;
import org.doscolas.service.PaymentService;
import org.doscolas.service.PayoutService;

import java.util.Map;

/**
 * Public endpoint (no bearer token — Fintoc can't send one) for Fintoc's webhook POSTs, guarded
 * instead by {@link FintocWebhookVerifier}'s HMAC check on {@code Fintoc-Signature}. Handles
 * {@code checkout_session.*} (payment collection) and {@code transfer.*} (payout) events; the
 * event envelope shape ({@code {type, data: {object}}} vs. a flattened object) isn't confirmed
 * against live traffic yet, so {@link #extractObject} tries both.
 */
public final class FintocWebhookController {

    private static final Logger log = LogManager.getLogger(FintocWebhookController.class);

    private final FintocWebhookVerifier verifier;
    private final PaymentService paymentService;
    private final PayoutService payoutService;

    public FintocWebhookController(FintocWebhookVerifier verifier, PaymentService paymentService, PayoutService payoutService) {
        this.verifier = verifier;
        this.paymentService = paymentService;
        this.payoutService = payoutService;
    }

    public void register(Router router) {
        router.post("/webhooks/fintoc", this::handle);
    }

    private Response handle(RequestContext ctx) {
        String rawBody = ctx.body();
        String signature = ctx.header("Fintoc-Signature");
        if (!verifier.isValid(signature, rawBody)) {
            log.warn("Rejected Fintoc webhook with invalid or missing signature");
            return Response.raw(401, "text/plain; charset=utf-8", "Invalid signature");
        }

        Map<String, Object> event = Json.parseObject(rawBody);
        String type = String.valueOf(event.get("type"));
        Map<String, Object> object = extractObject(event);
        if (object == null) {
            return Response.ok(Map.of("received", true));
        }

        String id = (String) object.get("id");
        String status = (String) object.get("status");
        if (id == null) {
            return Response.ok(Map.of("received", true));
        }

        if (type.startsWith("checkout_session")) {
            paymentService.handleFintocCheckoutSessionFinished(id, status);
        } else if (type.startsWith("transfer")) {
            if ("succeeded".equalsIgnoreCase(status)) {
                payoutService.markCompleted(id);
            } else if ("failed".equalsIgnoreCase(status)) {
                payoutService.markFailed(id, "Fintoc reported the transfer as failed");
            }
        }
        return Response.ok(Map.of("received", true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractObject(Map<String, Object> event) {
        Object data = event.get("data");
        if (data instanceof Map<?, ?> dataMap && dataMap.get("object") instanceof Map<?, ?> objectMap) {
            return (Map<String, Object>) objectMap;
        }
        return event.containsKey("id") ? event : null;
    }
}
