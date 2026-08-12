package org.doscolas.controller;

import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.service.PaymentService;
import org.doscolas.service.PayoutService;
import org.doscolas.service.SitterBankAccountService;
import org.doscolas.service.TakeCareService;

/**
 * Platform-wide listings and destructive operations for the admin dashboard. Users and pets
 * already expose admin operations on their own controllers; this one covers the entities that
 * only had per-user endpoints.
 */
public final class AdminController {

    private final TakeCareService takeCareService;
    private final PaymentService paymentService;
    private final SitterBankAccountService sitterBankAccountService;
    private final PayoutService payoutService;

    public AdminController(TakeCareService takeCareService, PaymentService paymentService,
                            SitterBankAccountService sitterBankAccountService, PayoutService payoutService) {
        this.takeCareService = takeCareService;
        this.paymentService = paymentService;
        this.sitterBankAccountService = sitterBankAccountService;
        this.payoutService = payoutService;
    }

    public void register(Router router) {
        router.get("/admin/take-cares", this::listTakeCares);
        router.delete("/admin/take-cares/{id}", this::deleteTakeCare);
        router.get("/admin/payments", this::listPayments);
        router.get("/admin/bank-accounts", this::listBankAccounts);
        router.delete("/admin/bank-accounts/{id}", this::deleteBankAccount);
        router.get("/admin/payouts", this::listPayouts);
    }

    private Response listTakeCares(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(takeCareService.getAll().stream().map(r -> r.toMap()).toList());
    }

    private Response deleteTakeCare(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        takeCareService.delete(ctx.pathParamLong("id"));
        return Response.noContent();
    }

    private Response listPayments(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(paymentService.getAll().stream().map(r -> r.toMap()).toList());
    }

    private Response listBankAccounts(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(sitterBankAccountService.listAllForAdmin().stream().map(r -> r.toMap()).toList());
    }

    private Response deleteBankAccount(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        sitterBankAccountService.deleteByIdAsAdmin(ctx.pathParamLong("id"));
        return Response.noContent();
    }

    private Response listPayouts(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(payoutService.getAll().stream().map(r -> r.toMap()).toList());
    }
}
