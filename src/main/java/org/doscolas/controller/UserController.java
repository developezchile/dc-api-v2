package org.doscolas.controller;

import org.doscolas.dto.request.CreateUserRequest;
import org.doscolas.dto.request.UpdateUserRequest;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.service.UserService;

public final class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public void register(Router router) {
        router.get("/users", this::listUsers);
        router.get("/users/{id}", this::getUser);
        router.post("/users", this::createUser);
        router.put("/users/{id}", this::updateUser);
        router.delete("/users/{id}", this::deleteUser);
    }

    private Response listUsers(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        return Response.ok(userService.getAllUsers().stream().map(u -> u.toMap()).toList());
    }

    private Response getUser(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        ctx.requireRoleOrSelf(id, "ADMIN");
        return Response.ok(userService.getById(id).toMap());
    }

    private Response createUser(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        CreateUserRequest request = CreateUserRequest.fromJson(ctx.jsonBody());
        return Response.created(userService.create(request).toMap());
    }

    private Response updateUser(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        ctx.requireRoleOrSelf(id, "ADMIN");
        UpdateUserRequest request = UpdateUserRequest.fromJson(ctx.jsonBody());
        return Response.ok(userService.update(id, request).toMap());
    }

    private Response deleteUser(RequestContext ctx) {
        ctx.requireRole("ADMIN");
        userService.delete(ctx.pathParamLong("id"));
        return Response.noContent();
    }
}
