package org.doscolas.controller;

import org.doscolas.dto.request.AssignPetRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.service.PetSitterService;

public final class PetSitterController {

    private final PetSitterService petSitterService;

    public PetSitterController(PetSitterService petSitterService) {
        this.petSitterService = petSitterService;
    }

    public void register(Router router) {
        router.get("/pet-sitters/pet/{petId}", this::getSittersByPet);
        router.get("/pet-sitters/sitter/{sitterId}", this::getPetsBySitter);
        router.post("/pet-sitters/pet/{petId}/assign", this::assignPetToSitter);
        router.delete("/pet-sitters/pet/{petId}/sitter/{sitterId}", this::unassignPetFromSitter);
    }

    private Response getSittersByPet(RequestContext ctx) {
        ctx.requireUserId();
        return Response.ok(petSitterService.getSittersByPetId(ctx.pathParamLong("petId"))
                .stream().map(r -> r.toMap()).toList());
    }

    private Response getPetsBySitter(RequestContext ctx) {
        ctx.requireUserId();
        return Response.ok(petSitterService.getPetsBySitterId(ctx.pathParamLong("sitterId"))
                .stream().map(r -> r.toMap()).toList());
    }

    private Response assignPetToSitter(RequestContext ctx) {
        ctx.requireUserId();
        AssignPetRequest request = AssignPetRequest.fromJson(ctx.jsonBody());
        return Response.created(petSitterService.assignPetToSitter(ctx.pathParamLong("petId"), request).toMap());
    }

    private Response unassignPetFromSitter(RequestContext ctx) {
        ctx.requireUserId();
        petSitterService.unassignPetFromSitter(ctx.pathParamLong("petId"), ctx.pathParamLong("sitterId"));
        return Response.noContent();
    }
}
