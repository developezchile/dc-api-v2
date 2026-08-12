package org.doscolas.service;

import org.doscolas.dto.request.CreatePetRequest;
import org.doscolas.dto.request.UpdatePetRequest;
import org.doscolas.dto.response.PetResponse;
import org.doscolas.dto.response.UserResponse;
import org.doscolas.exception.ForbiddenException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.json.Json;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;
import org.doscolas.model.User;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.PetWithTakeCareRow;
import org.doscolas.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public PetService(PetRepository petRepository, UserRepository userRepository) {
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    public List<PetResponse> getAllPets(PetStatus status) {
        List<Pet> pets = status != null ? petRepository.findByStatus(status) : petRepository.findAll();
        return pets.stream().map(this::toResponse).toList();
    }

    public PetResponse getById(long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + id + " not found"));
        return toResponse(pet);
    }

    public List<PetResponse> getByOwnerId(long ownerId, PetStatus status) {
        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("User with id " + ownerId + " not found");
        }
        List<Pet> pets = status != null
                ? petRepository.findByOwnerIdAndStatus(ownerId, status)
                : petRepository.findByOwnerId(ownerId);
        return pets.stream().map(this::toResponse).toList();
    }

    public List<Map<String, Object>> getByOwnerIdWithTakeCare(long ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("User with id " + ownerId + " not found");
        }
        List<PetWithTakeCareRow> rows = petRepository.findPetWithOwnerRolesAndTakeCare(ownerId);
        return rows.stream().map(this::toMap).toList();
    }

    public PetResponse create(CreatePetRequest req, long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + ownerId + " not found"));

        Pet pet = new Pet();
        pet.setName(req.name);
        pet.setType(req.type);
        pet.setBreed(req.breed);
        pet.setAge(req.age);
        pet.setRate(req.rate);
        pet.setOwnerId(owner.getId());
        pet.setStatus(req.petStatus != null ? PetStatus.valueOf(req.petStatus.toUpperCase()) : PetStatus.ACTIVE);
        pet.setNotes(req.notes);
        pet.setWeight(req.weight);

        return toResponse(petRepository.insert(pet));
    }

    public PetResponse update(long id, UpdatePetRequest req, long currentUserId, boolean isAdmin) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + id + " not found"));
        assertOwnerOrAdmin(pet, currentUserId, isAdmin);

        if (req.name != null) pet.setName(req.name);
        if (req.type != null) pet.setType(req.type);
        if (req.breed != null) pet.setBreed(req.breed);
        if (req.age != null) pet.setAge(req.age);
        if (req.rate != null) pet.setRate(req.rate);
        if (req.petStatus != null) pet.setStatus(PetStatus.valueOf(req.petStatus.toUpperCase()));
        if (req.notes != null) pet.setNotes(req.notes);
        if (req.weight != null) pet.setWeight(req.weight);

        return toResponse(petRepository.update(pet));
    }

    public PetResponse updateStatus(long id, PetStatus status, long currentUserId, boolean isAdmin) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + id + " not found"));
        assertOwnerOrAdmin(pet, currentUserId, isAdmin);
        pet.setStatus(status);
        return toResponse(petRepository.update(pet));
    }

    public void delete(long id, long currentUserId, boolean isAdmin) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + id + " not found"));
        assertOwnerOrAdmin(pet, currentUserId, isAdmin);
        petRepository.deleteById(id);
    }

    private void assertOwnerOrAdmin(Pet pet, long currentUserId, boolean isAdmin) {
        if (!isAdmin && (pet.getOwnerId() == null || pet.getOwnerId() != currentUserId)) {
            throw new ForbiddenException("Only the pet's owner can do this");
        }
    }

    public List<PetResponse> getPetsByStatus(PetStatus status) {
        return petRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    private PetResponse toResponse(Pet p) {
        User owner = userRepository.findById(p.getOwnerId()).orElse(null);
        String ownerName = owner != null
                ? ((owner.getFirstName() != null ? owner.getFirstName() : "")
                    + (owner.getLastName() != null ? " " + owner.getLastName() : "")).trim()
                : null;

        UserResponse ownerResponse = owner != null ? new UserResponse(owner.getId(), owner.getUsername(),
                owner.getEmail(), owner.getFirstName(), owner.getLastName(), owner.getPhone(), owner.getAddress(),
                roleNames(owner), owner.isEnabled(), owner.getCreatedAt(), owner.getUpdatedAt()) : null;

        return new PetResponse(p.getId(), p.getName(), p.getType(), p.getBreed(), p.getAge(), p.getWeight(),
                p.getRate(), ownerName, owner != null ? owner.getPhone() : null,
                p.getStatus() != null ? p.getStatus().name() : null, p.getNotes(), ownerResponse);
    }

    private Set<String> roleNames(User u) {
        Set<String> names = new LinkedHashSet<>();
        u.getRoles().forEach(r -> names.add(r.name()));
        return names;
    }

    private Map<String, Object> toMap(PetWithTakeCareRow row) {
        Map<String, Object> map = Json.obj();
        map.put("id", row.id());
        map.put("name", row.name());
        map.put("type", row.type());
        map.put("breed", row.breed());
        map.put("status", row.status());
        map.put("notes", row.notes());
        map.put("age", row.age());
        map.put("weight", row.weight());
        map.put("ownerId", row.ownerId());
        map.put("petStatus", row.petStatus());
        return map;
    }
}
