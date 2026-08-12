package org.doscolas.repository;

import org.doscolas.db.ConnectionPool;
import org.doscolas.model.SitterBankAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SitterBankAccountRepository {

    private final ConnectionPool pool;

    public SitterBankAccountRepository(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<SitterBankAccount> findByUserId(long userId) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM sitter_bank_accounts WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query sitter_bank_accounts", e);
        } finally {
            pool.release(conn);
        }
    }

    public List<SitterBankAccount> findAll() {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM sitter_bank_accounts ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<SitterBankAccount> result = new ArrayList<>();
            while (rs.next()) result.add(mapRow(rs));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query sitter_bank_accounts", e);
        } finally {
            pool.release(conn);
        }
    }

    public SitterBankAccount insert(SitterBankAccount account) {
        String sql = """
                INSERT INTO sitter_bank_accounts
                    (user_id, bank_code, bank_name, account_type, account_number, rut, holder_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWrite(ps, account);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) account.setId(keys.getLong(1));
            }
            return account;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert sitter bank account", e);
        } finally {
            pool.release(conn);
        }
    }

    public SitterBankAccount update(SitterBankAccount account) {
        String sql = """
                UPDATE sitter_bank_accounts SET
                    bank_code = ?, bank_name = ?, account_type = ?, account_number = ?,
                    rut = ?, holder_name = ?, updated_at = NOW()
                WHERE id = ?
                """;
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.getBankCode());
            ps.setString(2, account.getBankName());
            ps.setString(3, account.getAccountType());
            ps.setString(4, account.getAccountNumber());
            ps.setString(5, account.getRut());
            ps.setString(6, account.getHolderName());
            ps.setLong(7, account.getId());
            ps.executeUpdate();
            return account;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update sitter bank account", e);
        } finally {
            pool.release(conn);
        }
    }

    public void deleteById(long id) {
        Connection conn = pool.borrow();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sitter_bank_accounts WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete sitter bank account " + id, e);
        } finally {
            pool.release(conn);
        }
    }

    private void bindWrite(PreparedStatement ps, SitterBankAccount account) throws SQLException {
        ps.setLong(1, account.getUserId());
        ps.setString(2, account.getBankCode());
        ps.setString(3, account.getBankName());
        ps.setString(4, account.getAccountType());
        ps.setString(5, account.getAccountNumber());
        ps.setString(6, account.getRut());
        ps.setString(7, account.getHolderName());
    }

    private SitterBankAccount mapRow(ResultSet rs) throws SQLException {
        SitterBankAccount account = new SitterBankAccount();
        account.setId(rs.getLong("id"));
        account.setUserId(rs.getLong("user_id"));
        account.setBankCode(rs.getString("bank_code"));
        account.setBankName(rs.getString("bank_name"));
        account.setAccountType(rs.getString("account_type"));
        account.setAccountNumber(rs.getString("account_number"));
        account.setRut(rs.getString("rut"));
        account.setHolderName(rs.getString("holder_name"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        account.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        account.setUpdatedAt(updatedAt != null ? updatedAt.toLocalDateTime() : null);
        return account;
    }
}
