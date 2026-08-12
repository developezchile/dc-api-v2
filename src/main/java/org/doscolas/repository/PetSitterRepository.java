package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.PetSitter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PetSitterRepository {

    private final ConnectionPool pool;

    public PetSitterRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public List<PetSitter> findByPetId(long petId) {
        return list("SELECT * FROM pet_sitters WHERE pet_id = ? ORDER BY id", petId);
    }

    public List<PetSitter> findBySitterId(long sitterId) {
        return list("SELECT * FROM pet_sitters WHERE sitter_id = ? ORDER BY id", sitterId);
    }

    public Optional<PetSitter> findByPetIdAndSitterId(long petId, long sitterId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM pet_sitters WHERE pet_id = ? AND sitter_id = ?")) {
            ps.setLong(1, petId);
            ps.setLong(2, sitterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pet_sitters", e);
        } finally {
            pool.release(conn);
        }
    }

    public boolean existsByPetIdAndSitterId(long petId, long sitterId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pet_sitters WHERE pet_id = ? AND sitter_id = ?")) {
            ps.setLong(1, petId);
            ps.setLong(2, sitterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check pet_sitters existence", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<PetSitter> list(String sql, long param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<PetSitter> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query pet_sitters", e);
        } finally {
            pool.release(conn);
        }
    }

    public PetSitter insert(PetSitter petSitter) {
        String sql = """
                INSERT INTO pet_sitters (pet_id, sitter_id, start_date, end_date, status, daily_rate, notes, assigned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, petSitter.getPetId());
            ps.setLong(2, petSitter.getSitterId());
            ps.setObject(3, petSitter.getStartDate());
            ps.setObject(4, petSitter.getEndDate());
            ps.setString(5, petSitter.getStatus());
            ps.setObject(6, petSitter.getDailyRate());
            ps.setString(7, petSitter.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) petSitter.setId(keys.getLong(1));
            }
            return petSitter;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert pet_sitter", e);
        } finally {
            pool.release(conn);
        }
    }

    public void delete(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pet_sitters WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete pet_sitter " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    private PetSitter mapRow(ResultSet rs) throws SQLException {
        PetSitter petSitter = new PetSitter();
        petSitter.setId(rs.getLong("id"));
        petSitter.setPetId(rs.getLong("pet_id"));
        petSitter.setSitterId(rs.getLong("sitter_id"));
        petSitter.setStartDate(rs.getObject("start_date", java.time.LocalDate.class));
        petSitter.setEndDate(rs.getObject("end_date", java.time.LocalDate.class));
        petSitter.setStatus(rs.getString("status"));
        Object dailyRate = rs.getObject("daily_rate");
        petSitter.setDailyRate(dailyRate != null ? ((Number) dailyRate).doubleValue() : null);
        petSitter.setNotes(rs.getString("notes"));
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        petSitter.setAssignedAt(assignedAt != null ? assignedAt.toLocalDateTime() : null);
        return petSitter;
    }
}
