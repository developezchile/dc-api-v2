package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.Payout;
import org.doscolas.model.PayoutStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PayoutRepository {

    private final ConnectionPool pool;

    public PayoutRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<Payout> findById(long id) {
        return findOne("SELECT * FROM payouts WHERE id = ?", id);
    }

    public Optional<Payout> findByFintocTransferId(String transferId) {
        return findOne("SELECT * FROM payouts WHERE fintoc_transfer_id = ?", transferId);
    }

    public boolean existsByPaymentId(long paymentId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM payouts WHERE payment_id = ?")) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check payouts existence", e);
        } finally {
            pool.release(conn);
        }
    }

    private Optional<Payout> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payouts", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<Payout> findAll() {
        return list("SELECT * FROM payouts ORDER BY id", null);
    }

    public List<Payout> findBySitterId(long sitterId) {
        return list("SELECT * FROM payouts WHERE sitter_id = ? ORDER BY id", sitterId);
    }

    public List<Payout> findByStatus(PayoutStatus status) {
        return list("SELECT * FROM payouts WHERE status = ? ORDER BY id", status.name());
    }

    public List<Payout> findBySitterIdAndStatus(long sitterId, PayoutStatus status) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM payouts WHERE sitter_id = ? AND status = ? ORDER BY id")) {
            ps.setLong(1, sitterId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Payout> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payouts", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<Payout> findByStatusAndUpdatedAtBefore(PayoutStatus status, LocalDateTime updatedBefore) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM payouts WHERE status = ? AND updated_at < ? ORDER BY id")) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.valueOf(updatedBefore));
            try (ResultSet rs = ps.executeQuery()) {
                List<Payout> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payouts", e);
        } finally {
            pool.release(conn);
        }
    }

    private List<Payout> list(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Payout> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payouts", e);
        } finally {
            pool.release(conn);
        }
    }

    public Payout insert(Payout payout) {
        String sql = """
                INSERT INTO payouts
                    (payment_id, sitter_id, take_care_id, amount, currency, status,
                     idempotency_key, fintoc_transfer_id, attempts, last_error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, payout.getPaymentId());
            ps.setLong(2, payout.getSitterId());
            ps.setObject(3, payout.getTakeCareId());
            ps.setBigDecimal(4, payout.getAmount());
            ps.setString(5, payout.getCurrency());
            ps.setString(6, payout.getStatus().name());
            ps.setString(7, payout.getIdempotencyKey());
            ps.setString(8, payout.getFintocTransferId());
            ps.setInt(9, payout.getAttempts());
            ps.setString(10, payout.getLastErrorMessage());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) payout.setId(keys.getLong(1));
            }
            return payout;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert payout", e);
        } finally {
            pool.release(conn);
        }
    }

    public Payout update(Payout payout) {
        String sql = """
                UPDATE payouts SET
                    status = ?, idempotency_key = ?, fintoc_transfer_id = ?, attempts = ?,
                    last_error_message = ?, updated_at = NOW(), completed_at = ?
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payout.getStatus().name());
            ps.setString(2, payout.getIdempotencyKey());
            ps.setString(3, payout.getFintocTransferId());
            ps.setInt(4, payout.getAttempts());
            ps.setString(5, payout.getLastErrorMessage());
            ps.setTimestamp(6, payout.getCompletedAt() != null ? Timestamp.valueOf(payout.getCompletedAt()) : null);
            ps.setLong(7, payout.getId());
            ps.executeUpdate();
            return payout;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payout", e);
        } finally {
            pool.release(conn);
        }
    }

    private Payout mapRow(ResultSet rs) throws SQLException {
        Payout payout = new Payout();
        payout.setId(rs.getLong("id"));
        payout.setPaymentId(rs.getLong("payment_id"));
        payout.setSitterId(rs.getLong("sitter_id"));
        long takeCareId = rs.getLong("take_care_id");
        payout.setTakeCareId(rs.wasNull() ? null : takeCareId);
        payout.setAmount(rs.getBigDecimal("amount"));
        payout.setCurrency(rs.getString("currency"));
        String status = rs.getString("status");
        payout.setStatus(status != null ? PayoutStatus.valueOf(status) : PayoutStatus.PENDING);
        payout.setIdempotencyKey(rs.getString("idempotency_key"));
        payout.setFintocTransferId(rs.getString("fintoc_transfer_id"));
        payout.setAttempts(rs.getInt("attempts"));
        payout.setLastErrorMessage(rs.getString("last_error_message"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        payout.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        payout.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        Timestamp completedAt = rs.getTimestamp("completed_at");
        payout.setCompletedAt(completedAt != null ? completedAt.toLocalDateTime() : null);
        return payout;
    }
}
