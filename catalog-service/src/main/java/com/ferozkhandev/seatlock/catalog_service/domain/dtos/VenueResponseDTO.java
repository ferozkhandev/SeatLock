package com.ferozkhandev.seatlock.catalog_service.domain.dtos;

public record VenueResponseDTO(
        String id,
        String name,
        String city,
        String address,
        VenueSectionsResponseDTO[] sections
) {
}
