package org.doscolas.dto.request;

import org.doscolas.model.Role;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class CreateUserRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;
    public final String address;
    public final Role role;

    private CreateUserRequest(String username, String email, String password, String firstName, String lastName,
                               String phone, String address, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
        this.role = role;
    }

    public static CreateUserRequest fromJson(Map<String, Object> json) {
        String username = str(json, "username");
        String email = str(json, "email");
        String password = str(json, "password");
        String firstName = str(json, "firstName");
        String lastName = str(json, "lastName");
        String phone = str(json, "phone");
        String address = str(json, "address");
        String roleRaw = str(json, "role");

        var errors = newErrors();
        notBlank(errors, "username", username);
        minLength(errors, "username", username, 3);
        maxLength(errors, "username", username, 50);
        notBlank(errors, "email", email);
        email(errors, "email", email);
        notBlank(errors, "password", password);
        minLength(errors, "password", password, 6);
        check(errors);

        Role role = roleRaw != null && !roleRaw.isBlank() ? Role.valueOf(roleRaw.toUpperCase()) : Role.SITTER;

        return new CreateUserRequest(username, email, password, firstName, lastName, phone, address, role);
    }
}
