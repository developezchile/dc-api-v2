package org.doscolas.service;

import org.doscolas.dto.request.CreateUserRequest;
import org.doscolas.dto.request.UpdateUserRequest;
import org.doscolas.dto.response.UserResponse;
import org.doscolas.exception.DuplicateResourceException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.model.Role;
import org.doscolas.model.User;
import org.doscolas.repository.UserRepository;
import org.doscolas.security.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse getById(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
        return toResponse(user);
    }

    public UserResponse create(CreateUserRequest req) {
        checkUsernameAvailable(req.username);
        checkEmailAvailable(req.email);

        User user = new User();
        user.setUsername(req.username);
        user.setEmail(req.email);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setFirstName(req.firstName);
        user.setLastName(req.lastName);
        user.setPhone(req.phone);
        user.setAddress(req.address);
        user.setRoles(Set.of(req.role != null ? req.role : Role.SITTER));
        user.setEnabled(true);

        return toResponse(userRepository.insert(user));
    }

    public UserResponse update(long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));

        if (req.username != null && !req.username.equals(user.getUsername())) {
            checkUsernameAvailable(req.username);
            user.setUsername(req.username);
        }
        if (req.email != null && !req.email.equals(user.getEmail())) {
            checkEmailAvailable(req.email);
            user.setEmail(req.email);
        }
        if (req.password != null) {
            user.setPassword(passwordEncoder.encode(req.password));
        }
        if (req.firstName != null) user.setFirstName(req.firstName);
        if (req.lastName != null) user.setLastName(req.lastName);
        if (req.phone != null) user.setPhone(req.phone);
        if (req.address != null) user.setAddress(req.address);

        return toResponse(userRepository.update(user));
    }

    public void delete(long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User with id " + id + " not found");
        }
        userRepository.deleteById(id);
    }

    private void checkUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
    }

    private void checkEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }
    }

    private UserResponse toResponse(User u) {
        Set<String> roleNames = new LinkedHashSet<>();
        u.getRoles().forEach(r -> roleNames.add(r.name()));
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getAddress(), roleNames, u.isEnabled(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
