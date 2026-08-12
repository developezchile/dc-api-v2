package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.time.LocalDate;
import java.util.Map;

public final class PetSitterResponse {

    public final long id;
    public final String petName;
    public final String petType;
    public final String petBreed;
    public final String ownerName;
    public final String ownerPhone;
    public final LocalDate startDate;
    public final LocalDate endDate;
    public final String status;
    public final Double dailyRate;
    public final String notes;

    public PetSitterResponse(long id, String petName, String petType, String petBreed, String ownerName,
                              String ownerPhone, LocalDate startDate, LocalDate endDate, String status,
                              Double dailyRate, String notes) {
        this.id = id;
        this.petName = petName;
        this.petType = petType;
        this.petBreed = petBreed;
        this.ownerName = ownerName;
        this.ownerPhone = ownerPhone;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.dailyRate = dailyRate;
        this.notes = notes;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("petName", petName);
        map.put("petType", petType);
        map.put("petBreed", petBreed);
        map.put("ownerName", ownerName);
        map.put("ownerPhone", ownerPhone);
        map.put("startDate", startDate != null ? startDate.toString() : null);
        map.put("endDate", endDate != null ? endDate.toString() : null);
        map.put("status", status);
        map.put("dailyRate", dailyRate);
        map.put("notes", notes);
        return map;
    }
}
