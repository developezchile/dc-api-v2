package org.doscolas.dto.response;

import org.doscolas.json.Json;
import org.doscolas.model.SmtpSettings;

import java.util.Map;

/** Never echoes the password back — only whether one is set. */
public final class SmtpSettingsResponse {

    private final SmtpSettings settings;

    public SmtpSettingsResponse(SmtpSettings settings) {
        this.settings = settings;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        if (settings == null) {
            map.put("configured", false);
            map.put("startTls", true);
            map.put("enabled", false);
            return map;
        }
        map.put("configured", true);
        map.put("provider", settings.getProvider());
        map.put("host", settings.getHost());
        map.put("port", settings.getPort());
        map.put("username", settings.getUsername());
        map.put("passwordSet", settings.getPassword() != null && !settings.getPassword().isBlank());
        map.put("startTls", settings.isStartTls());
        map.put("fromAddress", settings.getFromAddress());
        map.put("fromName", settings.getFromName());
        map.put("enabled", settings.isEnabled());
        map.put("updatedAt", settings.getUpdatedAt());
        return map;
    }
}
