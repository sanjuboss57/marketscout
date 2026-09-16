package com.marketscout.service;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite Database Integration & Complete CRUD Implementation.
 * Demonstrates relational table schema, foreign keys, and CRUD operations.
 */
@Slf4j
@Service
public class DatabaseService {

    private final DataSource dataSource;

    public DatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable SQLite Foreign Key constraints
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Users Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    full_name TEXT,
                    created_at TEXT NOT NULL
                );
            """);

            // 2. Watchlists Table with Foreign Key
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS watchlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    commodity_code TEXT NOT NULL,
                    notes TEXT,
                    added_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """);

            // 3. Alert Records Table with Foreign Key
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS alert_records (
                    id TEXT PRIMARY KEY,
                    user_id INTEGER,
                    commodity TEXT NOT NULL,
                    condition TEXT NOT NULL,
                    target_price REAL NOT NULL,
                    triggered INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """);

            // 4. External Market Snapshots Table (Stores parsed JSON data)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS external_market_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    base_currency TEXT NOT NULL,
                    rate_eur REAL,
                    rate_gbp REAL,
                    rate_jpy REAL,
                    rate_cad REAL,
                    fetched_at TEXT NOT NULL
                );
            """);

            // Insert default user if not exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO users (id, username, password_hash, full_name, created_at) VALUES (1, 'sanjuboss57', 'admin123', 'Sanju Developer', ?)"
            )) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }

            log.info("SQLite Database initialized successfully with relational schema.");

        } catch (SQLException e) {
            log.error("Failed to initialize SQLite database: {}", e.getMessage(), e);
        }
    }

    // ───────────────────────────────────────────────────────────
    //  CRUD: USERS
    // ───────────────────────────────────────────────────────────

    public boolean validateUser(String username, String password) {
        String sql = "SELECT id FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Auth validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ───────────────────────────────────────────────────────────
    //  CRUD: ALERTS (CREATE, READ, UPDATE, DELETE)
    // ───────────────────────────────────────────────────────────

    // CREATE
    public void createAlert(AlertRule rule, Integer userId) {
        String sql = "INSERT OR REPLACE INTO alert_records (id, user_id, commodity, condition, target_price, triggered, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rule.getId());
            if (userId != null) ps.setInt(2, userId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, rule.getCommodity().name());
            ps.setString(4, rule.getCondition().name());
            ps.setDouble(5, rule.getTargetPrice());
            ps.setInt(6, rule.isTriggered() ? 1 : 0);
            ps.setString(7, rule.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert alert into SQLite: {}", e.getMessage());
        }
    }

    // READ (All)
    public List<AlertRule> getAllAlerts() {
        List<AlertRule> list = new ArrayList<>();
        String sql = "SELECT id, commodity, condition, target_price, triggered, created_at FROM alert_records ORDER BY created_at DESC";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                AlertRule r = new AlertRule();
                r.setId(rs.getString("id"));
                r.setCommodity(Commodity.valueOf(rs.getString("commodity")));
                r.setCondition(AlertCondition.valueOf(rs.getString("condition")));
                r.setTargetPrice(rs.getDouble("target_price"));
                r.setTriggered(rs.getInt("triggered") == 1);
                r.setCreatedAt(Instant.parse(rs.getString("created_at")));
                list.add(r);
            }
        } catch (Exception e) {
            log.error("Failed to read alerts from SQLite: {}", e.getMessage());
        }
        return list;
    }

    // UPDATE
    public boolean updateAlert(String id, double targetPrice, AlertCondition condition) {
        String sql = "UPDATE alert_records SET target_price = ?, condition = ?, triggered = 0 WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, targetPrice);
            ps.setString(2, condition.name());
            ps.setString(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to update alert in SQLite: {}", e.getMessage());
            return false;
        }
    }

    // DELETE
    public boolean deleteAlert(String id) {
        String sql = "DELETE FROM alert_records WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to delete alert from SQLite: {}", e.getMessage());
            return false;
        }
    }

    // ───────────────────────────────────────────────────────────
    //  CRUD: WATCHLISTS (Relational table with Foreign Key)
    // ───────────────────────────────────────────────────────────

    // CREATE Watchlist Item
    public int addToWatchlist(int userId, String commodityCode, String notes) {
        String sql = "INSERT INTO watchlists (user_id, commodity_code, notes, added_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, commodityCode);
            ps.setString(3, notes);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Failed to add to watchlist: {}", e.getMessage());
        }
        return -1;
    }

    // READ Watchlist
    public List<Map<String, Object>> getWatchlist(int userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT w.id, w.commodity_code, w.notes, w.added_at, u.username
            FROM watchlists w
            JOIN users u ON w.user_id = u.id
            WHERE w.user_id = ?
            ORDER BY w.id DESC
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Map.of(
                            "id", rs.getInt("id"),
                            "commodityCode", rs.getString("commodity_code"),
                            "notes", rs.getString("notes") != null ? rs.getString("notes") : "",
                            "addedAt", rs.getString("added_at"),
                            "username", rs.getString("username")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to read watchlist: {}", e.getMessage());
        }
        return list;
    }

    // UPDATE Watchlist Item Note
    public boolean updateWatchlistNote(int watchlistId, String updatedNotes) {
        String sql = "UPDATE watchlists SET notes = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, updatedNotes);
            ps.setInt(2, watchlistId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to update watchlist note: {}", e.getMessage());
            return false;
        }
    }

    // DELETE Watchlist Item
    public boolean deleteFromWatchlist(int watchlistId) {
        String sql = "DELETE FROM watchlists WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, watchlistId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to delete from watchlist: {}", e.getMessage());
            return false;
        }
    }

    // ───────────────────────────────────────────────────────────
    //  SAVE EXTERNAL JSON SNAPSHOT
    // ───────────────────────────────────────────────────────────

    public void saveExternalSnapshot(String base, double eur, double gbp, double jpy, double cad) {
        String sql = "INSERT INTO external_market_snapshots (base_currency, rate_eur, rate_gbp, rate_jpy, rate_cad, fetched_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, base);
            ps.setDouble(2, eur);
            ps.setDouble(3, gbp);
            ps.setDouble(4, jpy);
            ps.setDouble(5, cad);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save external snapshot: {}", e.getMessage());
        }
    }

    public Map<String, Object> getLatestExternalSnapshot() {
        String sql = "SELECT * FROM external_market_snapshots ORDER BY id DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return Map.of(
                        "base", rs.getString("base_currency"),
                        "EUR", rs.getDouble("rate_eur"),
                        "GBP", rs.getDouble("rate_gbp"),
                        "JPY", rs.getDouble("rate_jpy"),
                        "CAD", rs.getDouble("rate_cad"),
                        "fetchedAt", rs.getString("fetched_at")
                );
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve snapshot: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
