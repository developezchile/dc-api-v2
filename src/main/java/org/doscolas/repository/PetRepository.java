package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PetRepository {

    private final ConnectionPool pool;

    public PetRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public boolean existsById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pets WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check pet existence", e);
        } finally {
            pool.release(conn);
        }
    }

    public Optional<Pet> findById(long id) {
        return findOne("SELECT * FROM pets WHERE id = ?", id);
    }

    private Optional<Pet> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pets", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<Pet> findAll() {
        return list("SELECT * FROM pets ORDER BY id", null);
    }

    public List<Pet> findByStatus(PetStatus status) {
        return list("SELECT * FROM pets WHERE status = ? ORDER BY id", status.name());
    }

    public List<Pet> findByOwnerId(long ownerId) {
        return list("SELECT * FROM pets WHERE owner_id = ? ORDER BY id", ownerId);
    }

    public List<Pet> findByOwnerIdAndStatus(long ownerId, PetStatus status) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM pets WHERE owner_id = ? AND status = ? ORDER BY id")) {
            ps.setLong(1, ownerId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Pet> pets = new ArrayList<>();
                while (rs.next()) pets.add(mapRow(rs));
                return pets;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pets", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<Pet> list(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Pet> pets = new ArrayList<>();
                while (rs.next()) pets.add(mapRow(rs));
                return pets;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pets", e);
        } finally {
            pool.release(conn);
        }
    }

    /**
     * Ported from the original native query joining pets -> users -> user_roles -> take_cares.
     * One row per (pet, take_care) pair — a pet with several take-care records yields several
     * rows, a pet with none yields one row with a null take-care status, same as the original
     * LEFT JOIN.
     */
    /**
     * One row per pet — {@code DISTINCT ON (p.id)} picks each pet's single most recent take-care
     * (highest {@code id}, real rows sorted before the {@code NULL} a pet with no take-care history
     * gets from the {@code LEFT JOIN}) rather than fanning out into one row per take-care a pet has
     * ever had. Previously unfiltered, so a pet with more than one take-care record (e.g. an old
     * completed sitting plus a new application) rendered as duplicate "pets" in the owner's list.
     */
    public List<PetWithTakeCareRow> findPetWithOwnerRolesAndTakeCare(long ownerId) {
        String sql = """
                SELECT DISTINCT ON (p.id)
                    p.id as id,
                    p.name as name, p.type as type, p.breed as breed,
                    p.notes as notes,
                    p.age as age, p.weight as weight, p.owner_id as owner_id, p.status as pet_status,
                    tc.status as tc_status
                FROM pets p
                LEFT JOIN take_cares tc ON p.id = tc.pet_id
                WHERE p.owner_id = ?
                ORDER BY p.id, tc.id DESC NULLS LAST
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PetWithTakeCareRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PetWithTakeCareRow(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getString("breed"),
                            rs.getString("tc_status"),
                            rs.getString("notes"),
                            (Integer) rs.getObject("age"),
                            (Integer) rs.getObject("weight"),
                            rs.getLong("owner_id"),
                            rs.getString("pet_status")));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pets with take-care", e);
        } finally {
            pool.release(conn);
        }
    }

    public Pet insert(Pet pet) {
        String sql = """
                INSERT INTO pets (name, type, breed, age, weight, rate, owner_id, status, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pet.getName());
            ps.setString(2, pet.getType());
            ps.setString(3, pet.getBreed());
            ps.setObject(4, pet.getAge());
            ps.setObject(5, pet.getWeight());
            ps.setObject(6, pet.getRate());
            ps.setLong(7, pet.getOwnerId());
            ps.setString(8, pet.getStatus().name());
            ps.setString(9, pet.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) pet.setId(keys.getLong(1));
            }
            return pet;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert pet", e);
        } finally {
            pool.release(conn);
        }
    }

    public Pet update(Pet pet) {
        String sql = """
                UPDATE pets SET
                    name = ?, type = ?, breed = ?, age = ?, weight = ?, rate = ?, status = ?, notes = ?, updated_at = NOW()
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pet.getName());
            ps.setString(2, pet.getType());
            ps.setString(3, pet.getBreed());
            ps.setObject(4, pet.getAge());
            ps.setObject(5, pet.getWeight());
            ps.setObject(6, pet.getRate());
            ps.setString(7, pet.getStatus().name());
            ps.setString(8, pet.getNotes());
            ps.setLong(9, pet.getId());
            ps.executeUpdate();
            return pet;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update pet", e);
        } finally {
            pool.release(conn);
        }
    }

    public void deleteById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pets WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete pet " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    private Pet mapRow(ResultSet rs) throws SQLException {
        Pet pet = new Pet();
        pet.setId(rs.getLong("id"));
        pet.setName(rs.getString("name"));
        pet.setType(rs.getString("type"));
        pet.setBreed(rs.getString("breed"));
        pet.setAge((Integer) rs.getObject("age"));
        pet.setWeight((Integer) rs.getObject("weight"));
        Object rate = rs.getObject("rate");
        pet.setRate(rate != null ? ((Number) rate).doubleValue() : null);
        pet.setOwnerId(rs.getLong("owner_id"));
        String status = rs.getString("status");
        pet.setStatus(status != null ? PetStatus.valueOf(status) : PetStatus.ACTIVE);
        pet.setNotes(rs.getString("notes"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        pet.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        pet.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return pet;
    }
}
