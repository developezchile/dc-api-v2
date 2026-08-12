package org.doscolas.controller;

import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.service.PayoutService;

public final class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    public void register(Router router) {
        router.get("/payouts/me", this::getMine);
        router.get("/payouts", this::listAll);
        router.post("/payouts/{id}/retry", this::retry);
    }

    private Response getMine(RequestContext ctx) {
        long userId = ctx.requireUserId();
        return Response.ok(payoutService.getBySitterId(userId).stream().map(r -> r.toMap()).toList());
    }

    private Response listAll(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(payoutService.getAll().stream().map(r -> r.toMap()).toList());
    }

    private Response retry(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(payoutService.retry(ctx.pathParamLong("id")).toMap());
    }
}
