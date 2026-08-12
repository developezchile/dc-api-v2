package org.doscolas.controller;

import org.doscolas.dto.request.AssignPetRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.model.TakeCareStatus;
import org.doscolas.service.TakeCareService;

public final class TakeCareController {

    private final TakeCareService takeCareService;

    public TakeCareController(TakeCareService takeCareService) {
        this.takeCareService = takeCareService;
    }

    public void register(Router router) {
        // Literal routes must be registered before "/take-care/{id}" — the router tries routes
        // in registration order and "{id}" would otherwise swallow "available".
        router.get("/take-care/available", this::getAvailable);
        router.post("/take-care/process-due-dates", this::processDueDates);
        router.post("/take-care/pet/{petId}/assign", this::assignPetToSitter);
        router.get("/take-care/sitter/{sitterId}", this::getBySitter);
        router.get("/take-care/pet/{petId}", this::getByPet);
        router.post("/take-care/{id}/complete", this::complete);
        router.patch("/take-care/{id}/status", this::updateStatus);
        router.get("/take-care/{id}", this::getById);
        router.post("/take-care", this::create);
    }

    private Response assignPetToSitter(RequestContext ctx) {
        long sitterId = ctx.requireUserId();
        AssignPetRequest request = AssignPetRequest.fromJson(ctx.jsonBody());
        request.setSitterId(sitterId);
        return Response.created(takeCareService.assignPetToSitter(ctx.pathParamLong("petId"), request).toMap());
    }

    private Response getById(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        return Response.ok(takeCareService.getById(ctx.pathParamLong("id"), currentUserId, isAdmin).toMap());
    }

    private Response getBySitter(RequestContext ctx) {
        long sitterId = ctx.pathParamLong("sitterId");
        ctx.requireRoleOrSelf(sitterId, "ADMIN");
        return Response.ok(takeCareService.getBySitterId(sitterId).stream().map(r -> r.toMap()).toList());
    }

    private Response getByPet(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        return Response.ok(takeCareService.getByPetId(ctx.pathParamLong("petId"), currentUserId, isAdmin).stream().map(r -> r.toMap()).toList());
    }

    private Response complete(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        return Response.ok(takeCareService.completeBySitter(ctx.pathParamLong("id"), currentUserId).toMap());
    }

    private Response updateStatus(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        TakeCareStatus status = TakeCareStatus.valueOf(ctx.queryParam("status").toUpperCase());
        return Response.ok(takeCareService.updateStatus(ctx.pathParamLong("id"), status).toMap());
    }

    private Response create(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        AssignPetRequest request = AssignPetRequest.fromJson(ctx.jsonBody());
        return Response.created(takeCareService.create(request, currentUserId, isAdmin).toMap());
    }

    private Response getAvailable(RequestContext ctx) {
        ctx.requireUserId();
        return Response.ok(takeCareService.getAvailable().stream().map(r -> r.toMap()).toList());
    }

    private Response processDueDates(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(takeCareService.completeExpiredTakeCares());
    }
}
