package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UserResponse {

    public final long id;
    public final String username;
    public final String email;
    public final String firstName;
    public final String lastName;
    public final String phone;
    public final String address;
    public final Set<String> roles;
    public final boolean enabled;
    public final LocalDateTime createdAt;
    public final LocalDateTime updatedAt;

    public UserResponse(long id, String username, String email, String firstName, String lastName, String phone,
                         String address, Set<String> roles, boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
        this.roles = roles;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("username", username);
        map.put("email", email);
        map.put("firstName", firstName);
        map.put("lastName", lastName);
        map.put("phone", phone);
        map.put("address", address);
        map.put("roles", List.copyOf(roles));
        map.put("enabled", enabled);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return map;
    }
}
