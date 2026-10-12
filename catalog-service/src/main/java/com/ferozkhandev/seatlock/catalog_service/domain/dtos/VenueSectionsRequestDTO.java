package com.ferozkhandev.seatlock.catalog_service.domain.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record VenueSectionsRequestDTO(
        @NotBlank(message = "Section name must not be blank")
        @Size(min = 2, max = 100, message = "Section name must be between 2 and 100 characters")
        String name,
        @Positive(message = "Row count must be a positive number")
        @NotNull(message = "Row count is required")
        Integer rowCount,
        @Positive(message = "Seats per row must be a positive number")
        @NotNull(message = "Seats per row is required")
        Integer seatsPerRow,
        @Digits(integer = 2, fraction = 2, message = "Price multiplier must have at most 4 digits")
        @Positive(message = "Price multiplier must be a positive number")
        @NotNull(message = "Price multiplier is required")
        BigDecimal priceMultiplier
) {
}
