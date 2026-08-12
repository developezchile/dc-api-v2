package org.doscolas.controller;

import org.doscolas.dto.request.AuthRequest;
import org.doscolas.dto.request.ForgotPasswordRequest;
import org.doscolas.dto.request.RegisterRequest;
import org.doscolas.dto.request.ResendVerificationRequest;
import org.doscolas.dto.request.ResetPasswordRequest;
import org.doscolas.dto.request.VerifyEmailRequest;
import org.doscolas.exception.TooManyRequestsException;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.json.Json;
import org.doscolas.security.RateLimiter;
import org.doscolas.service.AuthService;

import java.util.Map;

public final class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    public void register(Router router) {
        router.post("/auth/register", this::registerUser);
        router.post("/auth/login", this::login);
        router.post("/auth/verify-email", this::verifyEmail);
        router.post("/auth/resend-verification", this::resendVerification);
        router.post("/auth/forgot-password", this::forgotPassword);
        router.post("/auth/reset-password", this::resetPassword);
    }

    private Response registerUser(RequestContext ctx) {
        limit(ctx, "register");
        RegisterRequest request = RegisterRequest.fromJson(ctx.jsonBody());
        var response = authService.register(request);
        return Response.created(response.toMap());
    }

    private Response login(RequestContext ctx) {
        limit(ctx, "login");
        AuthRequest request = AuthRequest.fromJson(ctx.jsonBody());
        var response = authService.authenticate(request);
        return Response.ok(response.toMap());
    }

    private Response verifyEmail(RequestContext ctx) {
        limit(ctx, "verify-email");
        VerifyEmailRequest request = VerifyEmailRequest.fromJson(ctx.jsonBody());
        authService.verifyEmail(request.token);
        return Response.ok(message("Correo verificado correctamente. Ya puedes iniciar sesión."));
    }

    private Response resendVerification(RequestContext ctx) {
        limit(ctx, "resend-verification");
        ResendVerificationRequest request = ResendVerificationRequest.fromJson(ctx.jsonBody());
        authService.resendVerification(request.email);
        return Response.ok(message("Si el correo existe y no ha sido verificado, enviamos un nuevo enlace."));
    }

    private Response forgotPassword(RequestContext ctx) {
        limit(ctx, "forgot-password");
        ForgotPasswordRequest request = ForgotPasswordRequest.fromJson(ctx.jsonBody());
        authService.forgotPassword(request.email);
        return Response.ok(message("Si el correo existe, enviamos un enlace para restablecer la contraseña."));
    }

    private Response resetPassword(RequestContext ctx) {
        limit(ctx, "reset-password");
        ResetPasswordRequest request = ResetPasswordRequest.fromJson(ctx.jsonBody());
        authService.resetPassword(request.token, request.newPassword);
        return Response.ok(message("Contraseña actualizada correctamente. Ya puedes iniciar sesión."));
    }

    private void limit(RequestContext ctx, String route) {
        if (!rateLimiter.tryAcquire(route + ":" + ctx.clientIp())) {
            throw new TooManyRequestsException("Demasiados intentos. Intenta nuevamente en unos minutos.");
        }
    }

    private Map<String, Object> message(String text) {
        Map<String, Object> body = Json.obj();
        body.put("message", text);
        return body;
    }
}
