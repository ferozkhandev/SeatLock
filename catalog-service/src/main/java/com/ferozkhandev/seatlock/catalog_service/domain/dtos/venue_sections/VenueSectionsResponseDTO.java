package com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue_sections;

import java.math.BigDecimal;

public record VenueSectionsResponseDTO(
        String id,
        String name,
        Integer rowCount,
        Integer seatsPerRow,
        BigDecimal priceMultiplier
) {
}
