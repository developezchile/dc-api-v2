package org.doscolas.dto.request;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class UpdateUserRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;
    public final String address;

    private UpdateUserRequest(String username, String email, String password, String firstName, String lastName,
                               String phone, String address) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
    }

    public static UpdateUserRequest fromJson(Map<String, Object> json) {
        String username = str(json, "username");
        String email = str(json, "email");
        String password = str(json, "password");
        String firstName = str(json, "firstName");
        String lastName = str(json, "lastName");
        String phone = str(json, "phone");
        String address = str(json, "address");

        var errors = newErrors();
        if (username != null) minLength(errors, "username", username, 3);
        if (username != null) maxLength(errors, "username", username, 50);
        if (email != null) email(errors, "email", email);
        if (password != null) minLength(errors, "password", password, 6);
        check(errors);

        return new UpdateUserRequest(username, email, password, firstName, lastName, phone, address);
    }
}
