package org.doscolas.controller;

import org.doscolas.dto.request.CreatePaymentRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.model.PaymentStatus;
import org.doscolas.service.PaymentService;

public final class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void register(Router router) {
        router.get("/payments/user/{userId}", this::getPaymentsByUser);
        router.get("/payments/pet/{petId}", this::getPaymentsByPet);
        router.patch("/payments/{id}/status", this::updateStatus);
        router.get("/payments/{id}", this::getPayment);
        router.post("/payments", this::createPayment);
        router.post("/payments/{id}/fintoc-sync", this::syncFintoc);
    }

    private Response createPayment(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        CreatePaymentRequest request = CreatePaymentRequest.fromJson(ctx.jsonBody());
        return Response.created(paymentService.createPayment(request, currentUserId).toMap());
    }

    private Response getPayment(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        return Response.ok(paymentService.getById(ctx.pathParamLong("id"), currentUserId, isAdmin).toMap());
    }

    private Response getPaymentsByUser(RequestContext ctx) {
        long userId = ctx.pathParamLong("userId");
        ctx.requireRoleOrSelf(userId, "ADMIN");
        return Response.ok(paymentService.getByUserId(userId).stream().map(r -> r.toMap()).toList());
    }

    private Response getPaymentsByPet(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        return Response.ok(paymentService.getByPetId(ctx.pathParamLong("petId"), currentUserId, isAdmin).stream().map(r -> r.toMap()).toList());
    }

    private Response updateStatus(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        PaymentStatus status = PaymentStatus.valueOf(ctx.queryParam("status").toUpperCase());
        return Response.ok(paymentService.updateStatus(ctx.pathParamLong("id"), status).toMap());
    }

    private Response syncFintoc(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        return Response.ok(paymentService.syncFintocPayment(ctx.pathParamLong("id"), currentUserId).toMap());
    }
}
