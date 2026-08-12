package org.doscolas.dto.request;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class SmtpSettingsRequest {

    public final String provider;
    public final String host;
    public final Integer port;
    public final String username;
    /** Null/blank means "leave the currently saved password as-is" — see SmtpSettingsService. */
    public final String password;
    public final boolean startTls;
    public final String fromAddress;
    public final String fromName;
    public final boolean enabled;

    private SmtpSettingsRequest(String provider, String host, Integer port, String username, String password,
                                 boolean startTls, String fromAddress, String fromName, boolean enabled) {
        this.provider = provider;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.startTls = startTls;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.enabled = enabled;
    }

    public static SmtpSettingsRequest fromJson(Map<String, Object> json) {
        String provider = str(json, "provider");
        String host = str(json, "host");
        Integer port = intVal(json, "port");
        String username = str(json, "username");
        String password = str(json, "password");
        Boolean startTls = boolVal(json, "startTls");
        String fromAddress = str(json, "fromAddress");
        String fromName = str(json, "fromName");
        Boolean enabled = boolVal(json, "enabled");
        boolean enabledValue = enabled != null && enabled;

        var errors = newErrors();
        if (enabledValue) {
            notBlank(errors, "host", host);
            notNull(errors, "port", port);
            notBlank(errors, "fromAddress", fromAddress);
            email(errors, "fromAddress", fromAddress);
        } else if (fromAddress != null && !fromAddress.isBlank()) {
            email(errors, "fromAddress", fromAddress);
        }
        check(errors);

        return new SmtpSettingsRequest(provider, host, port, username, password,
                startTls == null || startTls, fromAddress, fromName, enabledValue);
    }
}
