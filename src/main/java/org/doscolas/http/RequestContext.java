package org.doscolas.http;

import org.doscolas.exception.ForbiddenException;
import org.doscolas.exception.UnauthorizedException;
import org.doscolas.json.Json;
import org.doscolas.security.JwtService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Per-request facade over {@link HttpExchange}: path/query params, body parsing, auth. */
public final class RequestContext {

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final JwtService jwtService;
    private String cachedBody;

    RequestContext(HttpExchange exchange, Map<String, String> pathParams, JwtService jwtService) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.jwtService = jwtService;
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public Long pathParamLong(String name) {
        String value = pathParam(name);
        return value == null ? null : Long.valueOf(value);
    }

    public String queryParam(String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** Best-effort caller IP for rate limiting — the socket peer address, not X-Forwarded-For
     *  (this process isn't expected to sit behind a proxy that sets it trustworthily). */
    public String clientIp() {
        var remote = exchange.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    public String body() {
        if (cachedBody == null) {
            try (InputStream in = exchange.getRequestBody()) {
                cachedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read request body", e);
            }
        }
        return cachedBody;
    }

    public Map<String, Object> jsonBody() {
        String raw = body();
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        return Json.parseObject(raw);
    }

    /** Validates the Bearer token from the Authorization header and returns the authenticated user id. */
    public long requireUserId() {
        return jwtService.userIdFromToken(bearerToken());
    }

    /** Validates the Bearer token and returns the caller's {@code roles} claim. */
    public Set<String> roles() {
        return jwtService.rolesFromToken(bearerToken());
    }

    /**
     * Validates the Bearer token and checks for a single role, without throwing. For endpoints
     * that must fetch the target resource before they know its owner — so
     * {@link #requireRoleOrSelf} 's direct-target-id shortcut doesn't apply — the usual pattern is
     * {@code requireUserId()} + {@code hasRole("ADMIN")}, then compare the caller id against the
     * resource's owner id once it's loaded, allowing the request through if either holds.
     */
    public boolean hasRole(String role) {
        return roles().contains(role);
    }

    /**
     * Validates the Bearer token and ensures the caller has at least one of {@code allowedRoles}
     * (mirrors the old {@code @PreAuthorize("hasAnyRole(...)")}). Returns the authenticated user id.
     */
    public long requireRole(String... allowedRoles) {
        String token = bearerToken();
        Set<String> userRoles = jwtService.rolesFromToken(token);
        for (String allowed : allowedRoles) {
            if (userRoles.contains(allowed)) {
                return jwtService.userIdFromToken(token);
            }
        }
        throw new ForbiddenException("No tienes permisos para realizar esta acción");
    }

    /** Requires the caller to either hold one of {@code allowedRoles} or be the user identified by {@code targetUserId}. */
    public long requireRoleOrSelf(long targetUserId, String... allowedRoles) {
        String token = bearerToken();
        long callerId = jwtService.userIdFromToken(token);
        if (callerId == targetUserId) {
            return callerId;
        }
        Set<String> userRoles = jwtService.rolesFromToken(token);
        for (String allowed : allowedRoles) {
            if (userRoles.contains(allowed)) {
                return callerId;
            }
        }
        throw new ForbiddenException("No tienes permisos para realizar esta acción");
    }

    private String bearerToken() {
        String auth = header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new UnauthorizedException("Falta el token de autenticación");
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (!jwtService.validate(token)) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
        return token;
    }
}
