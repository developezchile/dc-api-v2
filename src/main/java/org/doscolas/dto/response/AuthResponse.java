package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuthResponse {

    public final long id;
    public final String token;
    public final String username;
    public final String email;
    public final Set<String> roles;

    public AuthResponse(long id, String token, String username, String email, Set<String> roles) {
        this.id = id;
        this.token = token;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("token", token);
        map.put("username", username);
        map.put("email", email);
        map.put("roles", List.copyOf(roles));
        return map;
    }
}
