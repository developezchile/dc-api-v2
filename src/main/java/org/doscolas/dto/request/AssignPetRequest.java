package org.doscolas.dto.request;

import java.time.LocalDate;
import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class AssignPetRequest {

    public Long sitterId;
    public final Long petId;
    public final LocalDate startDate;
    public final LocalDate endDate;
    public final Double dailyRate;
    public final String notes;
    public final String status;

    private AssignPetRequest(Long sitterId, Long petId, LocalDate startDate, LocalDate endDate,
                              Double dailyRate, String notes, String status) {
        this.sitterId = sitterId;
        this.petId = petId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dailyRate = dailyRate;
        this.notes = notes;
        this.status = status;
    }

    public static AssignPetRequest fromJson(Map<String, Object> json) {
        Long sitterId = longVal(json, "sitterId");
        Long petId = longVal(json, "petId");
        LocalDate startDate = dateVal(json, "startDate");
        LocalDate endDate = dateVal(json, "endDate");
        Double dailyRate = doubleVal(json, "dailyRate");
        String notes = str(json, "notes");
        String status = str(json, "status");

        return new AssignPetRequest(sitterId, petId, startDate, endDate, dailyRate, notes, status);
    }

    /** Set by the controller for endpoints where the sitter is the authenticated caller, not a JSON field. */
    public void setSitterId(Long sitterId) {
        this.sitterId = sitterId;
    }
}
