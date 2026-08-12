package org.doscolas.controller;

import org.doscolas.dto.request.SaveBankAccountRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.service.SitterBankAccountService;

public final class SitterBankAccountController {

    private final SitterBankAccountService service;

    public SitterBankAccountController(SitterBankAccountService service) {
        this.service = service;
    }

    public void register(Router router) {
        router.get("/sitters/me/bank-account", this::getMine);
        router.put("/sitters/me/bank-account", this::saveMine);
    }

    private Response getMine(RequestContext ctx) {
        long userId = ctx.requireRole("SITTER");
        return Response.ok(service.getByUserId(userId).toMap());
    }

    private Response saveMine(RequestContext ctx) {
        long userId = ctx.requireRole("SITTER");
        SaveBankAccountRequest request = SaveBankAccountRequest.fromJson(ctx.jsonBody());
        return Response.ok(service.save(userId, request).toMap());
    }
}
