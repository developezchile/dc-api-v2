package org.doscolas.dto.request;

import org.doscolas.model.Role;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.doscolas.validation.Validate.*;

public final class RegisterRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;
    public final String address;
    public final Set<Role> roles;

    private RegisterRequest(String username, String email, String password, String firstName, String lastName,
                             String phone, String address, Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
        this.roles = roles;
    }

    @SuppressWarnings("unchecked")
    public static RegisterRequest fromJson(Map<String, Object> json) {
        String username = str(json, "username");
        String email = str(json, "email");
        String password = str(json, "password");
        String firstName = str(json, "firstName");
        String lastName = str(json, "lastName");
        String phone = str(json, "phone");
        String address = str(json, "address");

        var errors = newErrors();
        notBlank(errors, "username", username);
        minLength(errors, "username", username, 3);
        maxLength(errors, "username", username, 50);
        notBlank(errors, "email", email);
        email(errors, "email", email);
        notBlank(errors, "password", password);
        minLength(errors, "password", password, 6);
        check(errors);

        Set<Role> roles = new LinkedHashSet<>();
        Object rawRoles = json.get("roles");
        if (rawRoles instanceof List<?> list) {
            for (Object r : list) {
                roles.add(Role.valueOf(String.valueOf(r).toUpperCase()));
            }
        }
        if (roles.isEmpty()) {
            roles.add(Role.SITTER);
        }

        return new RegisterRequest(username, email, password, firstName, lastName, phone, address, roles);
    }
}
