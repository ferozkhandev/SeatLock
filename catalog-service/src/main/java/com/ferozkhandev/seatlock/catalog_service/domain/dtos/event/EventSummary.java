package com.ferozkhandev.seatlock.catalog_service.domain.dtos.event;

import java.math.BigDecimal;

public record EventSummary(
        String id,
        String name,
        String category,
        String startsAt,
        String endsAt,
        BigDecimal basePrice,
        String posterKey,
        String venueId,
        String venueName,
        String city
) {
}
