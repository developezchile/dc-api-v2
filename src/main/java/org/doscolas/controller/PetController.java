package org.doscolas.controller;

import org.doscolas.dto.request.CreatePetRequest;
import org.doscolas.dto.request.UpdatePetRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.model.PetStatus;
import org.doscolas.service.PetService;

public final class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    public void register(Router router) {
        router.get("/pets/status/{status}", this::getPetsByStatus);
        router.get("/pets/owner/{ownerId}", this::getPetsByOwner);
        router.get("/pets", this::listPets);
        router.get("/pets/{id}", this::getPet);
        router.post("/pets", this::createPet);
        router.put("/pets/{id}", this::updatePet);
        router.delete("/pets/{id}", this::deletePet);
        router.patch("/pets/{id}/status", this::updatePetStatus);
    }

    // Platform-wide, unfiltered pet listings — only the admin dashboard has a legitimate need
    // for "every pet, regardless of owner".
    private Response listPets(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        PetStatus status = statusParam(ctx.queryParam("status"));
        return Response.ok(petService.getAllPets(status).stream().map(p -> p.toMap()).toList());
    }

    private Response getPet(RequestContext ctx) {
        ctx.requireUserId();
        return Response.ok(petService.getById(ctx.pathParamLong("id")).toMap());
    }

    private Response getPetsByOwner(RequestContext ctx) {
        long ownerId = ctx.pathParamLong("ownerId");
        ctx.requireRoleOrSelf(ownerId, "ADMIN");
        return Response.ok(petService.getByOwnerIdWithTakeCare(ownerId));
    }

    private Response createPet(RequestContext ctx) {
        long ownerId = ctx.requireUserId();
        CreatePetRequest request = CreatePetRequest.fromJson(ctx.jsonBody());
        return Response.created(petService.create(request, ownerId).toMap());
    }

    private Response updatePet(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        UpdatePetRequest request = UpdatePetRequest.fromJson(ctx.jsonBody());
        return Response.ok(petService.update(ctx.pathParamLong("id"), request, currentUserId, isAdmin).toMap());
    }

    private Response deletePet(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        petService.delete(ctx.pathParamLong("id"), currentUserId, isAdmin);
        return Response.noContent();
    }

    private Response getPetsByStatus(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        PetStatus status = PetStatus.valueOf(ctx.pathParam("status").toUpperCase());
        return Response.ok(petService.getPetsByStatus(status).stream().map(p -> p.toMap()).toList());
    }

    private Response updatePetStatus(RequestContext ctx) {
        long currentUserId = ctx.requireUserId();
        boolean isAdmin = ctx.hasRole("ADMIN");
        PetStatus status = PetStatus.valueOf(ctx.queryParam("status").toUpperCase());
        return Response.ok(petService.updateStatus(ctx.pathParamLong("id"), status, currentUserId, isAdmin).toMap());
    }

    private PetStatus statusParam(String raw) {
        return raw != null && !raw.isBlank() ? PetStatus.valueOf(raw.toUpperCase()) : null;
    }
}
