package com.ferozkhandev.seatlock.catalog_service.domain.dtos.event;

import com.ferozkhandev.seatlock.catalog_service.annotations.ValidateEventTimes;
import com.ferozkhandev.seatlock.catalog_service.domain.EventCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ValidateEventTimes
public record EventRequestDTO(

        @NotNull(message = "venueId is required")
        UUID venueId,
        @NotBlank(message = "name is required")
        @Size(min = 3, max = 255, message = "name must be between 3 and 255 characters")
        String name,
        String description,
        @NotNull(message = "category is required")
        EventCategory category,
        @NotNull(message = "startsAt is required")
        @FutureOrPresent
        Instant startsAt,
        @NotNull(message = "endsAt is required")
        Instant endsAt,
        @NotNull(message = "basePrice is required")
        @Digits(integer = 8, fraction = 2)
        @Positive
        BigDecimal basePrice
) {
}
