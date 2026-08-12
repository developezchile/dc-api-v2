package org.doscolas.dto.request;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class UpdatePetRequest {

    public final String name;
    public final String type;
    public final String breed;
    public final Integer age;
    public final Integer weight;
    public final Double rate;
    public final String petStatus;
    public final String notes;

    private UpdatePetRequest(String name, String type, String breed, Integer age, Integer weight,
                              Double rate, String petStatus, String notes) {
        this.name = name;
        this.type = type;
        this.breed = breed;
        this.age = age;
        this.weight = weight;
        this.rate = rate;
        this.petStatus = petStatus;
        this.notes = notes;
    }

    public static UpdatePetRequest fromJson(Map<String, Object> json) {
        return new UpdatePetRequest(
                str(json, "name"),
                str(json, "type"),
                str(json, "breed"),
                intVal(json, "age"),
                intVal(json, "weight"),
                doubleVal(json, "rate"),
                str(json, "petStatus"),
                str(json, "notes"));
    }
}
