package org.doscolas.dto.response;

import org.doscolas.json.Json;

import java.util.Map;

public final class PetResponse {

    public final long id;
    public final String name;
    public final String type;
    public final String breed;
    public final Integer age;
    public final Integer weight;
    public final Double rate;
    public final String ownerName;
    public final String ownerPhone;
    public final String status;
    public final String notes;
    public final UserResponse owner;

    public PetResponse(long id, String name, String type, String breed, Integer age, Integer weight, Double rate,
                        String ownerName, String ownerPhone, String status, String notes, UserResponse owner) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.breed = breed;
        this.age = age;
        this.weight = weight;
        this.rate = rate;
        this.ownerName = ownerName;
        this.ownerPhone = ownerPhone;
        this.status = status;
        this.notes = notes;
        this.owner = owner;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("id", id);
        map.put("name", name);
        map.put("type", type);
        map.put("breed", breed);
        map.put("age", age);
        map.put("weight", weight);
        map.put("rate", rate);
        map.put("ownerName", ownerName);
        map.put("ownerPhone", ownerPhone);
        map.put("status", status);
        map.put("notes", notes);
        map.put("owner", owner != null ? owner.toMap() : null);
        return map;
    }
}
