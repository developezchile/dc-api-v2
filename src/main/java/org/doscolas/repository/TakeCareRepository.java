package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.TakeCare;
import org.doscolas.model.TakeCareStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TakeCareRepository {

    private final ConnectionPool pool;

    public TakeCareRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<TakeCare> findById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM take_cares WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    public boolean existsById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM take_cares WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check take_cares existence", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<TakeCare> findAll() {
        return list("SELECT * FROM take_cares ORDER BY id");
    }

    public List<TakeCare> findByPetId(long petId) {
        return listByLong("SELECT * FROM take_cares WHERE pet_id = ? ORDER BY id", petId);
    }

    public List<TakeCare> findBySitterId(long sitterId) {
        return listByLong("SELECT * FROM take_cares WHERE sitter_id = ? ORDER BY id", sitterId);
    }

    public List<TakeCare> findByStatus(TakeCareStatus status) {
        return listByString("SELECT * FROM take_cares WHERE status = ? ORDER BY id", status.name());
    }

    public List<TakeCare> findByPetIdAndStatus(long petId, TakeCareStatus status) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM take_cares WHERE pet_id = ? AND status = ? ORDER BY id")) {
            ps.setLong(1, petId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<TakeCare> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<TakeCare> findByStatusAndEndDateBefore(TakeCareStatus status, LocalDate date) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM take_cares WHERE status = ? AND end_date < ? ORDER BY id")) {
            ps.setString(1, status.name());
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                List<TakeCare> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<TakeCare> list(String sql) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<TakeCare> result = new ArrayList<>();
            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<TakeCare> listByLong(String sql, long param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<TakeCare> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<TakeCare> listByString(String sql, String param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<TakeCare> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query take_cares", e);
        } finally {
            pool.release(conn);
        }
    }

    public TakeCare insert(TakeCare takeCare) {
        String sql = """
                INSERT INTO take_cares (pet_id, sitter_id, start_date, end_date, daily_rate, total_amount, status, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, takeCare.getPetId());
            ps.setObject(2, takeCare.getSitterId());
            ps.setObject(3, takeCare.getStartDate());
            ps.setObject(4, takeCare.getEndDate());
            ps.setObject(5, takeCare.getDailyRate());
            ps.setObject(6, takeCare.getTotalAmount());
            ps.setString(7, takeCare.getStatus().name());
            ps.setString(8, takeCare.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) takeCare.setId(keys.getLong(1));
            }
            return takeCare;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert take_care", e);
        } finally {
            pool.release(conn);
        }
    }

    public TakeCare update(TakeCare takeCare) {
        String sql = """
                UPDATE take_cares SET
                    sitter_id = ?, start_date = ?, end_date = ?, daily_rate = ?, total_amount = ?,
                    status = ?, notes = ?, updated_at = NOW()
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, takeCare.getSitterId());
            ps.setObject(2, takeCare.getStartDate());
            ps.setObject(3, takeCare.getEndDate());
            ps.setObject(4, takeCare.getDailyRate());
            ps.setObject(5, takeCare.getTotalAmount());
            ps.setString(6, takeCare.getStatus().name());
            ps.setString(7, takeCare.getNotes());
            ps.setLong(8, takeCare.getId());
            ps.executeUpdate();
            return takeCare;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update take_care", e);
        } finally {
            pool.release(conn);
        }
    }

    /**
     * Atomically assigns a sitter only if the row is still {@code LOOKING_FOR_SITTER}, moving it to
     * {@code WAITING_APPROVAL} — the sitting itself doesn't start until the owner approves by paying,
     * see {@link #approveIfWaitingApproval}. The status check and the write happen in the same
     * statement, so under concurrent calls only one caller's {@code WHERE} clause can still match
     * once the row updates — the loser gets 0 affected rows instead of silently overwriting the
     * winner's assignment.
     */
    public boolean assignSitterIfLookingForSitter(long id, long sitterId) {
        String sql = """
                UPDATE take_cares SET sitter_id = ?, status = ?, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sitterId);
            ps.setString(2, TakeCareStatus.WAITING_APPROVAL.name());
            ps.setLong(3, id);
            ps.setString(4, TakeCareStatus.LOOKING_FOR_SITTER.name());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign sitter to take_care " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    /**
     * Atomically moves a row from {@code WAITING_APPROVAL} to {@code ON_SITTER} once the owner's
     * payment completes — same conditional-{@code UPDATE} race-safety pattern as
     * {@link #assignSitterIfLookingForSitter}, so a duplicate/late webhook delivery can't re-trigger
     * the transition. Returns whether a row actually matched and was updated.
     */
    public boolean approveIfWaitingApproval(long id) {
        String sql = """
                UPDATE take_cares SET status = ?, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TakeCareStatus.ON_SITTER.name());
            ps.setLong(2, id);
            ps.setString(3, TakeCareStatus.WAITING_APPROVAL.name());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to approve take_care " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    public void deleteById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM take_cares WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete take_care " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    private TakeCare mapRow(ResultSet rs) throws SQLException {
        TakeCare takeCare = new TakeCare();
        takeCare.setId(rs.getLong("id"));
        takeCare.setPetId(rs.getLong("pet_id"));
        long sitterId = rs.getLong("sitter_id");
        takeCare.setSitterId(rs.wasNull() ? null : sitterId);
        takeCare.setStartDate(rs.getObject("start_date", LocalDate.class));
        takeCare.setEndDate(rs.getObject("end_date", LocalDate.class));
        Object dailyRate = rs.getObject("daily_rate");
        takeCare.setDailyRate(dailyRate != null ? ((Number) dailyRate).doubleValue() : null);
        Object totalAmount = rs.getObject("total_amount");
        takeCare.setTotalAmount(totalAmount != null ? ((Number) totalAmount).doubleValue() : null);
        String status = rs.getString("status");
        takeCare.setStatus(status != null ? TakeCareStatus.valueOf(status) : TakeCareStatus.ON_SITTER);
        takeCare.setNotes(rs.getString("notes"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        takeCare.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        takeCare.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return takeCare;
    }
}
