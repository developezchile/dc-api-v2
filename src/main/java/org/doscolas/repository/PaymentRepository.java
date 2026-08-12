package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.Payment;
import org.doscolas.model.PaymentStatus;

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

public final class PaymentRepository {

    private final ConnectionPool pool;

    public PaymentRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<Payment> findById(long id) {
        return findOne("SELECT * FROM payments WHERE id = ?", id);
    }

    public Optional<Payment> findByExternalReference(String externalReference) {
        return findOne("SELECT * FROM payments WHERE external_reference = ?", externalReference);
    }

    public Optional<Payment> findByFintocCheckoutSessionId(String checkoutSessionId) {
        return findOne("SELECT * FROM payments WHERE fintoc_checkout_session_id = ?", checkoutSessionId);
    }

    /** The completed payment that approved a take-care, if any — used to find who/how much to pay
     *  out once the sitter marks the job done. Filtered to COMPLETED so a stale FAILED attempt for
     *  the same take-care can't be picked up instead. */
    public Optional<Payment> findByTakeCareId(long takeCareId) {
        return findOne("SELECT * FROM payments WHERE take_care_id = ? AND status = 'COMPLETED' ORDER BY id DESC LIMIT 1", takeCareId);
    }

    private Optional<Payment> findOne(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payments", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<Payment> findAll() {
        return list("SELECT * FROM payments ORDER BY id", null);
    }

    public List<Payment> findByUserId(long userId) {
        return list("SELECT * FROM payments WHERE user_id = ? ORDER BY id", userId);
    }

    public List<Payment> findByPetId(long petId) {
        return list("SELECT * FROM payments WHERE pet_id = ? ORDER BY id", petId);
    }

    public List<Payment> findByStatus(PaymentStatus status) {
        return list("SELECT * FROM payments WHERE status = ? ORDER BY id", status.name());
    }

    private List<Payment> list(String sql, Object param) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Payment> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query payments", e);
        } finally {
            pool.release(conn);
        }
    }

    public boolean existsByTakeCareIdAndStatusIn(long takeCareId, List<PaymentStatus> statuses) {
        if (statuses.isEmpty()) return false;
        String placeholders = String.join(",", statuses.stream().map(s -> "?").toList());
        String sql = "SELECT 1 FROM payments WHERE take_care_id = ? AND status IN (" + placeholders + ")";
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, takeCareId);
            int i = 2;
            for (PaymentStatus status : statuses) {
                ps.setString(i++, status.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check payments existence", e);
        } finally {
            pool.release(conn);
        }
    }

    public boolean existsByTakeCareIdAndStatusAndCreatedAtAfter(long takeCareId, PaymentStatus status, LocalDateTime createdAt) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM payments WHERE take_care_id = ? AND status = ? AND created_at > ?")) {
            ps.setLong(1, takeCareId);
            ps.setString(2, status.name());
            ps.setTimestamp(3, Timestamp.valueOf(createdAt));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check payments existence", e);
        } finally {
            pool.release(conn);
        }
    }

    public Payment insert(Payment payment) {
        String sql = """
                INSERT INTO payments
                    (external_reference, authorization_code, card_last_four_digits, pet_id, user_id, take_care_id,
                     amount, platform_fee_amount, total_amount, currency, status, description,
                     fintoc_checkout_session_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, payment.getExternalReference());
            ps.setString(2, payment.getAuthorizationCode());
            ps.setString(3, payment.getCardLastFourDigits());
            ps.setObject(4, payment.getPetId());
            ps.setLong(5, payment.getUserId());
            ps.setObject(6, payment.getTakeCareId());
            ps.setBigDecimal(7, payment.getAmount());
            ps.setBigDecimal(8, payment.getPlatformFeeAmount());
            ps.setBigDecimal(9, payment.getTotalAmount());
            ps.setString(10, payment.getCurrency());
            ps.setString(11, payment.getStatus().name());
            ps.setString(12, payment.getDescription());
            ps.setString(13, payment.getFintocCheckoutSessionId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) payment.setId(keys.getLong(1));
            }
            return payment;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert payment", e);
        } finally {
            pool.release(conn);
        }
    }

    public void updateFintocCheckoutSessionId(long id, String checkoutSessionId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE payments SET fintoc_checkout_session_id = ? WHERE id = ?")) {
            ps.setString(1, checkoutSessionId);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    public Payment update(Payment payment) {
        String sql = """
                UPDATE payments SET
                    authorization_code = ?, card_last_four_digits = ?, status = ?, paid_at = ?
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payment.getAuthorizationCode());
            ps.setString(2, payment.getCardLastFourDigits());
            ps.setString(3, payment.getStatus().name());
            ps.setTimestamp(4, payment.getPaidAt() != null ? Timestamp.valueOf(payment.getPaidAt()) : null);
            ps.setLong(5, payment.getId());
            ps.executeUpdate();
            return payment;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment", e);
        } finally {
            pool.release(conn);
        }
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment payment = new Payment();
        payment.setId(rs.getLong("id"));
        payment.setExternalReference(rs.getString("external_reference"));
        payment.setAuthorizationCode(rs.getString("authorization_code"));
        payment.setCardLastFourDigits(rs.getString("card_last_four_digits"));
        long petId = rs.getLong("pet_id");
        payment.setPetId(rs.wasNull() ? null : petId);
        payment.setUserId(rs.getLong("user_id"));
        long takeCareId = rs.getLong("take_care_id");
        payment.setTakeCareId(rs.wasNull() ? null : takeCareId);
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setPlatformFeeAmount(rs.getBigDecimal("platform_fee_amount"));
        payment.setTotalAmount(rs.getBigDecimal("total_amount"));
        payment.setCurrency(rs.getString("currency"));
        String status = rs.getString("status");
        payment.setStatus(status != null ? PaymentStatus.valueOf(status) : PaymentStatus.PENDING);
        payment.setDescription(rs.getString("description"));
        payment.setFintocCheckoutSessionId(rs.getString("fintoc_checkout_session_id"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        payment.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp paidAt = rs.getTimestamp("paid_at");
        payment.setPaidAt(paidAt != null ? paidAt.toLocalDateTime() : null);
        return payment;
    }
}
