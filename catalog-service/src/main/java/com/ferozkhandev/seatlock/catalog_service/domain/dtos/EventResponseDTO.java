package com.ferozkhandev.seatlock.catalog_service.domain.dtos;

import java.math.BigDecimal;

public record EventResponseDTO(
        String id,
        String organizerId,
        String name,
        String description,
        String category,
        String startsAt,
        String endsAt,
        BigDecimal basePrice,
        String status,
        String posterKey,
        VenueResponseDTO venue) {
}
