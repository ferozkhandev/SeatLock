package com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue;

import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue_sections.VenueSectionsResponseDTO;

public record VenueResponseDTO(
        String id,
        String name,
        String city,
        String address,
        VenueSectionsResponseDTO[] sections
) {
}
