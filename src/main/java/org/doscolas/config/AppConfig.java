package org.doscolas.config;

import java.net.URI;

/**
 * Central place where every environment-derived setting is read once at startup, via
 * {@link Env} (env var &gt; {@code config.yml} &gt; the hardcoded default passed below).
 * Those defaults mirror the current {@code dc-api} module's {@code application.yml} (postgres
 * profile) so both services can point at the same local Postgres instance during development.
 */
public final class AppConfig {

    public final int port;
    public final String contextPath;

    public final String dbUrl;
    public final String dbUsername;
    public final String dbPassword;
    public final int dbPoolSize;

    public final String jwtSecret;
    public final long jwtExpirationMs;

    public final String frontendUrl;

    public final String smtpHost;
    public final int smtpPort;
    public final String smtpUsername;
    public final String smtpPassword;
    public final boolean smtpStartTls;
    public final String smtpFromAddress;
    public final String smtpFromName;

    public final int rateLimitMaxRequests;
    public final long rateLimitWindowMs;

    public final String fintocApiUrl;
    public final String fintocSecretKey;
    public final String fintocAccountId;
    public final String fintocWebhookSecret;
    /** PEM-encoded PKCS8 RSA private key used to JWS-sign Transfers requests (Fintoc's payout rail).
     *  Blank (the default) means payouts can't actually be sent — see FintocClient. Generate with
     *  {@code openssl genrsa -out k.pem 2048 && openssl pkcs8 -topk8 -inform PEM -in k.pem -out k8.pem -nocrypt},
     *  upload the matching public key to dashboard.fintoc.com -> API Keys -> JWS Public Keys. */
    public final String fintocJwsPrivateKey;
    /** Base URL for Fintoc's success_url/cancel_url, deliberately separate from FRONTEND_URL:
     *  Fintoc rejects non-HTTPS callback URLs, so local dev's FRONTEND_URL (plain http://localhost)
     *  can't be reused here without breaking Checkout Session creation — see PaymentService. */
    public final String fintocCallbackUrl;
    public final double platformFeePercentage;
    public final int payoutMaxAttempts;
    public final long payoutProcessIntervalMs;
    public final long payoutPollIntervalMs;

    public AppConfig() {
        this.port = Env.getInt("PORT", 8080);
        this.contextPath = Env.get("CONTEXT_PATH", "/api");

        // Accepts either the JDBC form (jdbc:postgresql://host:port/db, paired with DB_USERNAME/
        // DB_PASSWORD — the localhost default below) or a bare postgres(ql):// URL with embedded
        // credentials, which is what Render's fromDatabase connectionString hands you. Same
        // DB_URL env var works unchanged in both places.
        String[] db = parseDbUrl(Env.get("DB_URL", "jdbc:postgresql://localhost:5432/doscolas"));
        this.dbUrl = db[0];
        this.dbUsername = db[1] != null ? db[1] : Env.get("DB_USERNAME", "postgres");
        this.dbPassword = db[2] != null ? db[2] : Env.get("DB_PASSWORD", "37dominga");
        this.dbPoolSize = Env.getInt("DB_POOL_SIZE", 10);

        this.jwtSecret = Env.get("JWT_SECRET", "Y3VhdHJvUGF0YXNTZWNyZXRLZXlGb3JKV1RBdXRoMjU2Qml0cw==");
        this.jwtExpirationMs = Env.getLong("JWT_EXPIRATION_MS", 86_400_000L);

        this.frontendUrl = Env.get("FRONTEND_URL", "http://localhost:3000");

        // Blank SMTP_HOST (the default) makes Main wire up LoggingEmailSender instead of real SMTP.
        this.smtpHost = Env.get("SMTP_HOST", "");
        this.smtpPort = Env.getInt("SMTP_PORT", 587);
        this.smtpUsername = Env.get("SMTP_USERNAME", "");
        this.smtpPassword = Env.get("SMTP_PASSWORD", "");
        this.smtpStartTls = Boolean.parseBoolean(Env.get("SMTP_STARTTLS", "true"));
        this.smtpFromAddress = Env.get("SMTP_FROM_ADDRESS", "no-reply@doscolas.cl");
        this.smtpFromName = Env.get("SMTP_FROM_NAME", "Dos Colas");

        this.rateLimitMaxRequests = Env.getInt("RATE_LIMIT_MAX_REQUESTS", 10);
        this.rateLimitWindowMs = Env.getLong("RATE_LIMIT_WINDOW_MS", 60_000L);

        this.fintocApiUrl = Env.get("FINTOC_API_URL", "https://api.fintoc.com");
        this.fintocSecretKey = Env.get("FINTOC_SECRET_KEY", "sk_test_placeholder");
        this.fintocAccountId = Env.get("FINTOC_ACCOUNT_ID", "acc_placeholder");
        this.fintocWebhookSecret = Env.get("FINTOC_WEBHOOK_SECRET", "whsec_placeholder");
        this.fintocJwsPrivateKey = Env.get("FINTOC_JWS_PRIVATE_KEY", "");
        this.fintocCallbackUrl = Env.get("FINTOC_CALLBACK_URL", "https://www.doscolas.cl");
        this.platformFeePercentage = Double.parseDouble(Env.get("PLATFORM_FEE_PERCENTAGE", "0.10"));
        this.payoutMaxAttempts = Env.getInt("PAYOUT_MAX_ATTEMPTS", 3);
        this.payoutProcessIntervalMs = Env.getLong("PAYOUT_PROCESS_INTERVAL_MS", 60_000L);
        this.payoutPollIntervalMs = Env.getLong("PAYOUT_POLL_INTERVAL_MS", 300_000L);
    }

    /**
     * Returns {@code [jdbcUrl, username, password]}; username/password are {@code null} when
     * {@code rawUrl} is already a JDBC URL (they come from DB_USERNAME/DB_PASSWORD instead).
     */
    private static String[] parseDbUrl(String rawUrl) {
        if (rawUrl.startsWith("jdbc:")) {
            return new String[] { rawUrl, null, null };
        }
        URI uri = URI.create(rawUrl);
        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            username = sep >= 0 ? userInfo.substring(0, sep) : userInfo;
            password = sep >= 0 ? userInfo.substring(sep + 1) : null;
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath()
                + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
        return new String[] { jdbcUrl, username, password };
    }
}
