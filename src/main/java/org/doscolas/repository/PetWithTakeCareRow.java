package org.doscolas.repository;

/**
 * Plain projection matching the original JPA native-query interface
 * ({@code PetWithTakeCareDto}): {@code status} is the take-care's status (nullable — a pet may
 * have no take-care rows, or several, one per row, exactly like the original LEFT JOIN), while
 * {@code petStatus} is the pet's own status column. Names kept as-is for parity with the port source.
 */
public record PetWithTakeCareRow(
        Long id,
        String name,
        String type,
        String breed,
        String status,
        String notes,
        Integer age,
        Integer weight,
        Long ownerId,
        String petStatus
) {
}
