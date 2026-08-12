package org.doscolas.service;

import org.doscolas.dto.request.AssignPetRequest;
import org.doscolas.dto.response.TakeCareResponse;
import org.doscolas.dto.response.UserResponse;
import org.doscolas.exception.ForbiddenException;
import org.doscolas.exception.BusinessRuleException;
import org.doscolas.exception.DuplicateResourceException;
import org.doscolas.exception.ResourceNotFoundException;
import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.model.Payment;
import org.doscolas.model.Pet;
import org.doscolas.model.PetStatus;
import org.doscolas.model.TakeCare;
import org.doscolas.model.TakeCareStatus;
import org.doscolas.model.User;
import org.doscolas.repository.PaymentRepository;
import org.doscolas.repository.PetRepository;
import org.doscolas.repository.TakeCareRepository;
import org.doscolas.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class TakeCareService {

    private static final Logger log = LogManager.getLogger(TakeCareService.class);

    private final TakeCareRepository takeCareRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutService payoutService;

    public TakeCareService(TakeCareRepository takeCareRepository, PetRepository petRepository, UserRepository userRepository,
                            PaymentRepository paymentRepository, PayoutService payoutService) {
        this.takeCareRepository = takeCareRepository;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.payoutService = payoutService;
    }

    public TakeCareResponse assignPetToSitter(long petId, AssignPetRequest request) {
        TakeCare takeCare = takeCareRepository.findByPetIdAndStatus(petId, TakeCareStatus.LOOKING_FOR_SITTER)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No open TakeCare request found for pet " + petId));

        User sitter = userRepository.findById(request.sitterId)
                .orElseThrow(() -> new ResourceNotFoundException("Sitter with id " + request.sitterId + " not found"));

        if (!takeCareRepository.assignSitterIfLookingForSitter(takeCare.getId(), sitter.getId())) {
            throw new DuplicateResourceException("This pet has already been assigned to a sitter");
        }

        TakeCare updated = takeCareRepository.findById(takeCare.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TakeCare with id " + takeCare.getId() + " not found"));
        return toResponse(updated);
    }

    public TakeCareResponse getById(long id, long currentUserId, boolean isAdmin) {
        TakeCare takeCare = takeCareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TakeCare with id " + id + " not found"));
        if (!isAdmin && !isPetOwner(takeCare.getPetId(), currentUserId)
                && (takeCare.getSitterId() == null || takeCare.getSitterId() != currentUserId)) {
            throw new ForbiddenException("Only the pet's owner or the assigned sitter can view this");
        }
        return toResponse(takeCare);
    }

    public List<TakeCareResponse> getBySitterId(long sitterId) {
        return takeCareRepository.findBySitterId(sitterId).stream().map(this::toResponse).toList();
    }

    public List<TakeCareResponse> getByPetId(long petId, long currentUserId, boolean isAdmin) {
        if (!isAdmin && !isPetOwner(petId, currentUserId)) {
            throw new ForbiddenException("Only the pet's owner can view its take-care history");
        }
        return takeCareRepository.findByPetId(petId).stream().map(this::toResponse).toList();
    }

    private boolean isPetOwner(long petId, long currentUserId) {
        return petRepository.findById(petId)
                .map(pet -> pet.getOwnerId() != null && pet.getOwnerId() == currentUserId)
                .orElse(false);
    }

    public List<TakeCareResponse> getAvailable() {
        return takeCareRepository.findByStatus(TakeCareStatus.LOOKING_FOR_SITTER).stream().map(this::toResponse).toList();
    }

    public List<TakeCareResponse> getAll() {
        return takeCareRepository.findAll().stream().map(this::toResponse).toList();
    }

    public void delete(long id) {
        if (!takeCareRepository.existsById(id)) {
            throw new ResourceNotFoundException("TakeCare with id " + id + " not found");
        }
        takeCareRepository.deleteById(id);
    }

    public TakeCareResponse updateStatus(long id, TakeCareStatus status) {
        TakeCare takeCare = takeCareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TakeCare with id " + id + " not found"));
        takeCare.setStatus(status);
        return toResponse(takeCareRepository.update(takeCare));
    }

    public TakeCareResponse completeBySitter(long id, long currentUserId) {
        TakeCare takeCare = takeCareRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TakeCare with id " + id + " not found"));

        if (takeCare.getSitterId() == null || takeCare.getSitterId() != currentUserId) {
            throw new ForbiddenException("Only the assigned sitter can complete this assignment");
        }
        if (takeCare.getStatus() != TakeCareStatus.ON_SITTER) {
            throw new BusinessRuleException("Only active assignments can be completed");
        }

        return toResponse(completeAndTriggerPayout(takeCare));
    }

    /**
     * Marks a take-care {@code COMPLETED}, frees up the pet, and — since payment now happens up
     * front to approve the sitting, not after it's done — this is where the sitter's payout
     * actually gets created, via whichever {@link Payment} approved this take-care.
     */
    private TakeCare completeAndTriggerPayout(TakeCare takeCare) {
        takeCare.setStatus(TakeCareStatus.COMPLETED);
        TakeCare updated = takeCareRepository.update(takeCare);

        petRepository.findById(takeCare.getPetId()).ifPresent(pet -> {
            pet.setStatus(PetStatus.INACTIVE);
            petRepository.update(pet);
        });

        paymentRepository.findByTakeCareId(takeCare.getId()).ifPresentOrElse(
                payoutService::createPendingPayout,
                () -> log.warn("TakeCare {} completed with no COMPLETED payment on file — no payout created", takeCare.getId()));

        return updated;
    }

    public int completeExpiredTakeCares() {
        List<TakeCare> expired = takeCareRepository.findByStatusAndEndDateBefore(TakeCareStatus.ON_SITTER, LocalDate.now());
        if (expired.isEmpty()) return 0;
        for (TakeCare tc : expired) {
            completeAndTriggerPayout(tc);
        }
        return expired.size();
    }

    public TakeCareResponse create(AssignPetRequest request, long currentUserId, boolean isAdmin) {
        Pet pet = petRepository.findById(request.petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet with id " + request.petId + " not found"));
        if (!isAdmin && (pet.getOwnerId() == null || pet.getOwnerId() != currentUserId)) {
            throw new ForbiddenException("Only the pet's owner can list it as looking for a sitter");
        }

        LocalDate startDate = request.startDate != null ? request.startDate : LocalDate.now();
        Double dailyRate = request.dailyRate != null ? request.dailyRate : pet.getRate();
        Double totalAmount = null;
        if (dailyRate != null && request.endDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, request.endDate) + 1;
            totalAmount = dailyRate * days;
        }

        TakeCareStatus status = request.status != null
                ? TakeCareStatus.valueOf(request.status.toUpperCase())
                : TakeCareStatus.LOOKING_FOR_SITTER;

        TakeCare takeCare = new TakeCare();
        takeCare.setPetId(pet.getId());
        takeCare.setStartDate(startDate);
        takeCare.setEndDate(request.endDate);
        takeCare.setDailyRate(dailyRate);
        takeCare.setTotalAmount(totalAmount);
        takeCare.setNotes(request.notes);
        takeCare.setStatus(status);

        return toResponse(takeCareRepository.insert(takeCare));
    }

    private TakeCareResponse toResponse(TakeCare tc) {
        Pet pet = petRepository.findById(tc.getPetId()).orElse(null);
        User owner = pet != null ? userRepository.findById(pet.getOwnerId()).orElse(null) : null;
        User sitter = tc.getSitterId() != null ? userRepository.findById(tc.getSitterId()).orElse(null) : null;

        UserResponse ownerResponse = owner != null ? new UserResponse(owner.getId(), owner.getUsername(),
                owner.getEmail(), owner.getFirstName(), owner.getLastName(), owner.getPhone(), owner.getAddress(),
                owner.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                owner.isEnabled(), owner.getCreatedAt(), owner.getUpdatedAt()) : null;

        String sitterName = sitter != null
                ? ((sitter.getFirstName() != null ? sitter.getFirstName() : "")
                    + (sitter.getLastName() != null ? " " + sitter.getLastName() : "")).trim()
                : null;

        String status = tc.getStatus() != null ? tc.getStatus().name() : null;
        if (TakeCareStatus.ON_SITTER.name().equals(status)) {
            status = PetStatus.ACTIVE.name();
        }

        return new TakeCareResponse(tc.getId(), tc.getPetId(), pet != null ? pet.getName() : null,
                tc.getSitterId(), sitterName, tc.getStartDate(), tc.getEndDate(), tc.getDailyRate(),
                tc.getTotalAmount(), status, tc.getNotes(), tc.getCreatedAt(), ownerResponse);
    }
}
