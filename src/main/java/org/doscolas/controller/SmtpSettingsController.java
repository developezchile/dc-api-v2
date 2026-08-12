package org.doscolas.controller;

import org.doscolas.dto.request.SendTestEmailRequest;
import org.doscolas.dto.request.SmtpSettingsRequest;
import org.doscolas.dto.response.SmtpSettingsResponse;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.json.Json;
import org.doscolas.service.SmtpSettingsService;

import java.util.Map;

/** Admin-only: configure the platform's outgoing SMTP provider (e.g. Maileroo) at runtime. */
public final class SmtpSettingsController {

    private final SmtpSettingsService smtpSettingsService;

    public SmtpSettingsController(SmtpSettingsService smtpSettingsService) {
        this.smtpSettingsService = smtpSettingsService;
    }

    public void register(Router router) {
        router.get("/admin/settings/smtp", this::get);
        router.put("/admin/settings/smtp", this::update);
        router.post("/admin/settings/smtp/test", this::sendTest);
    }

    private Response get(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(new SmtpSettingsResponse(smtpSettingsService.get()).toMap());
    }

    private Response update(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        SmtpSettingsRequest request = SmtpSettingsRequest.fromJson(ctx.jsonBody());
        var saved = smtpSettingsService.update(request);
        return Response.ok(new SmtpSettingsResponse(saved).toMap());
    }

    private Response sendTest(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        SendTestEmailRequest request = SendTestEmailRequest.fromJson(ctx.jsonBody());
        smtpSettingsService.sendTest(request.to);
        Map<String, Object> body = Json.obj();
        body.put("message", "Correo de prueba enviado a " + request.to);
        return Response.ok(body);
    }
}
