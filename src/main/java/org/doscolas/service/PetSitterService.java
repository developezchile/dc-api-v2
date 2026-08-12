package org.doscolas.service;

import org.doscolas.dto.request.AssignPetRequest;
import org.doscolas.dto.response.PetSitterResponse;
import org.doscolas.exception.DuplicateResourceException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.model.Pet;
import org.doscolas.model.PetSitter;
import org.doscolas.model.Role;
import org.doscolas.model.User;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.PetSitterRepository;
import org.doscolas.repository.UserRepository;

import java.util.List;

public final class PetSitterService {

    private final PetSitterRepository petSitterRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public PetSitterService(PetSitterRepository petSitterRepository, PetRepository petRepository, UserRepository userRepository) {
        this.petSitterRepository = petSitterRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    public List<PetSitterResponse> getSittersByPetId(long petId) {
        if (!petRepository.existsById(petId)) {
            throw new ResourceNotFoundException("Pet with id " + petId + " not found");
        }
        return petSitterRepository.findByPetId(petId).stream().map(this::toResponse).toList();
    }

    public List<PetSitterResponse> getPetsBySitterId(long sitterId) {
        if (!userRepository.existsById(sitterId)) {
            throw new ResourceNotFoundException("User with id " + sitterId + " not found");
        }
        return petSitterRepository.findBySitterId(sitterId).stream().map(this::toResponse).toList();
    }

    public PetSitterResponse assignPetToSitter(long petId, AssignPetRequest req) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + petId + " not found"));

        User sitter = userRepository.findById(req.sitterId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + req.sitterId + " not found"));

        if (!sitter.getRoles().contains(Role.SITTER)) {
            throw new IllegalArgumentException("User with id " + req.sitterId + " is not a sitter");
        }

        if (petSitterRepository.existsByPetIdAndSitterId(petId, req.sitterId)) {
            throw new DuplicateResourceException("Pet is already assigned to this sitter");
        }

        PetSitter petSitter = new PetSitter();
        petSitter.setPetId(pet.getId());
        petSitter.setSitterId(sitter.getId());
        petSitter.setStartDate(req.startDate);
        petSitter.setEndDate(req.endDate);
        petSitter.setDailyRate(req.dailyRate);
        petSitter.setNotes(req.notes);

        return toResponse(petSitterRepository.insert(petSitter));
    }

    public void unassignPetFromSitter(long petId, long sitterId) {
        PetSitter petSitter = petSitterRepository.findByPetIdAndSitterId(petId, sitterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found for pet " + petId + " and sitter " + sitterId));
        petSitterRepository.delete(petSitter.getId());
    }

    private PetSitterResponse toResponse(PetSitter ps) {
        Pet pet = petRepository.findById(ps.getPetId()).orElse(null);
        User owner = pet != null ? userRepository.findById(pet.getOwnerId()).orElse(null) : null;
        String ownerName = owner != null
                ? ((owner.getFirstName() != null ? owner.getFirstName() : "")
                    + (owner.getLastName() != null ? " " + owner.getLastName() : "")).trim()
                : null;

        return new PetSitterResponse(ps.getId(), pet != null ? pet.getName() : null,
                pet != null ? pet.getType() : null, pet != null ? pet.getBreed() : null, ownerName,
                owner != null ? owner.getPhone() : null, ps.getStartDate(), ps.getEndDate(), ps.getStatus(),
                ps.getDailyRate(), ps.getNotes());
    }
}
