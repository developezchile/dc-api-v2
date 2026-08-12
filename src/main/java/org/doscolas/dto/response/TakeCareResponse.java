package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public final class TakeCareResponse {

    public final long id;
    public final long petId;
    public final String petName;
    public final Long sitterId;
    public final String sitterName;
    public final LocalDate startDate;
    public final LocalDate endDate;
    public final Double dailyRate;
    public final Double totalAmount;
    public final String status;
    public final String notes;
    public final LocalDateTime createdAt;
    public final UserResponse owner;

    public TakeCareResponse(long id, long petId, String petName, Long sitterId, String sitterName,
                             LocalDate startDate, LocalDate endDate, Double dailyRate, Double totalAmount,
                             String status, String notes, LocalDateTime createdAt, UserResponse owner) {
        this.id = id;
        this.petId = petId;
        this.petName = petName;
        this.sitterId = sitterId;
        this.sitterName = sitterName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dailyRate = dailyRate;
        this.totalAmount = totalAmount;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.owner = owner;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("petId", petId);
        map.put("petName", petName);
        map.put("sitterId", sitterId);
        map.put("sitterName", sitterName);
        map.put("startDate", startDate != null ? startDate.toString() : null);
        map.put("endDate", endDate != null ? endDate.toString() : null);
        map.put("dailyRate", dailyRate);
        map.put("totalAmount", totalAmount);
        map.put("status", status);
        map.put("notes", notes);
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("owner", owner != null ? owner.toMap() : null);
        return map;
    }
}
