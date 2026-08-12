package org.doscolas.dto.request;

import java.util.Map;

import static org.doscolas.validation.Validate.*;

public final class CreatePetRequest {

    public final String name;
    public final String type;
    public final String breed;
    public final Integer age;
    public final Integer weight;
    public final Double rate;
    public final String petStatus;
    public final String notes;

    private CreatePetRequest(String name, String type, String breed, Integer age, Integer weight,
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

    public static CreatePetRequest fromJson(Map<String, Object> json) {
        String name = str(json, "name");
        String type = str(json, "type");
        String breed = str(json, "breed");
        Integer age = intVal(json, "age");
        Integer weight = intVal(json, "weight");
        Double rate = doubleVal(json, "rate");
        String petStatus = str(json, "petStatus");
        String notes = str(json, "notes");

        var errors = newErrors();
        notBlank(errors, "name", name);
        check(errors);

        return new CreatePetRequest(name, type, breed, age, weight, rate, petStatus, notes);
    }
}
