package org.doscolas.testsupport;

import org.doscolas.config.AppConfig;
import org.doscolas.db.ConnectionPool;
import org.doscolas.db.MigrationRunner;

/**
 * Integration tests (suffix {@code IT}) run against a real local Postgres — same convention
 * established for this project's manual/Playwright testing throughout: no mocking the database.
 * Uses the same env vars / defaults as the running app ({@code DB_URL}, {@code DB_USERNAME}, ...),
 * so point {@code DB_URL} elsewhere if you don't want tests touching your dev database.
 */
public final class TestDb {

    private TestDb() {
    }

    public static ConnectionPool pool() {
        AppConfig config = new AppConfig();
        ConnectionPool pool = new ConnectionPool(config.dbUrl, config.dbUsername, config.dbPassword, 5);
        new MigrationRunner(pool).migrate();
        return pool;
    }
}
