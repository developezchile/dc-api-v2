package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.Role;
import org.doscolas.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Plain JDBC access to {@code users} + its {@code user_roles} child table — no ORM, no reflection. */
public final class UserRepository {

    private final ConnectionPool pool;

    public UserRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public boolean existsByUsername(String username) {
        return exists("SELECT 1 FROM users WHERE username = ?", username);
    }

    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM users WHERE email = ?", email);
    }

    public boolean existsById(long id) {
        return exists("SELECT 1 FROM users WHERE id = ?", id);
    }

    private boolean exists(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence in users", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<User> findById(long id) {
        return findOne("SELECT * FROM users WHERE id = ?", id);
    }

    public Optional<User> findByUsername(String username) {
        return findOne("SELECT * FROM users WHERE username = ?", username);
    }

    public Optional<User> findByEmail(String email) {
        return findOne("SELECT * FROM users WHERE email = ?", email);
    }

    private Optional<User> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                User user = mapRow(rs);
                user.setRoles(loadRoles(conn, user.getId()));
                return Optional.of(user);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query users", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<User> findAll() {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                User user = mapRow(rs);
                user.setRoles(loadRoles(conn, user.getId()));
                users.add(user);
            }
            return users;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query users", e);
        } finally {
            pool.release(conn);
        }
    }

    public User insert(User user) {
        String sql = """
                INSERT INTO users
                    (username, email, password, first_name, last_name, phone, address, enabled, email_verified, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getLastName());
            ps.setString(6, user.getPhone());
            ps.setString(7, user.getAddress());
            ps.setBoolean(8, user.isEnabled());
            ps.setBoolean(9, user.isEmailVerified());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            saveRoles(conn, user.getId(), user.getRoles());
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert user", e);
        } finally {
            pool.release(conn);
        }
    }

    public User update(User user) {
        String sql = """
                UPDATE users SET
                    username = ?, email = ?, password = ?, first_name = ?, last_name = ?,
                    phone = ?, address = ?, enabled = ?, email_verified = ?, updated_at = NOW()
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getLastName());
            ps.setString(6, user.getPhone());
            ps.setString(7, user.getAddress());
            ps.setBoolean(8, user.isEnabled());
            ps.setBoolean(9, user.isEmailVerified());
            ps.setLong(10, user.getId());
            ps.executeUpdate();
            saveRoles(conn, user.getId(), user.getRoles());
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update user", e);
        } finally {
            pool.release(conn);
        }
    }

    public void deleteById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement deleteRoles = conn.prepareStatement("DELETE FROM user_roles WHERE user_id = ?");
             PreparedStatement deleteUser = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            deleteRoles.setLong(1, id);
            deleteRoles.executeUpdate();
            deleteUser.setLong(1, id);
            deleteUser.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    private Set<Role> loadRoles(Connection conn, long userId) throws SQLException {
        Set<Role> roles = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT role FROM user_roles WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(Role.valueOf(rs.getString("role")));
                }
            }
        }
        return roles;
    }

    private void saveRoles(Connection conn, long userId, Set<Role> roles) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
            delete.setLong(1, userId);
            delete.executeUpdate();
        }
        if (roles == null || roles.isEmpty()) return;
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO user_roles (user_id, role) VALUES (?, ?)")) {
            for (Role role : roles) {
                insert.setLong(1, userId);
                insert.setString(2, role.name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhone(rs.getString("phone"));
        user.setAddress(rs.getString("address"));
        user.setEnabled(rs.getBoolean("enabled"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        user.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        user.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return user;
    }
}
