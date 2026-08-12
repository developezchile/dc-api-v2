package org.doscolas.controller;

import org.doscolas.db.ConnectionPool;
import org.doscolas.http.RequestContext;
import org.doscolas.http.Response;
import org.doscolas.http.Router;
import org.doscolas.json.Json;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/** Unauthenticated liveness/readiness probe for load balancers and orchestrators. */
public final class HealthController {

    private final ConnectionPool pool;

    public HealthController(ConnectionPool pool) {
        this.pool = pool;
    }

    public void register(Router router) {
        router.get("/health", this::health);
    }

    private Response health(RequestContext ctx) {
        boolean dbUp = checkDb();
        Map<String, Object> body = Json.obj();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("db", dbUp ? "UP" : "DOWN");
        return new Response(dbUp ? 200 : 503, body, Response.JSON, Map.of());
    }

    private boolean checkDb() {
        Connection conn = pool.borrow();
        try (Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            return false;
        } finally {
            pool.release(conn);
        }
    }
}
