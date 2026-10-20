package com.ferozkhandev.seatlock.catalog_service.domain.dtos.event;

import com.ferozkhandev.seatlock.catalog_service.domain.EventCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

public record EventFilterRequestDTO(
        String city,
        EventCategory category,
        Instant from,
        Instant to,
        @DecimalMin("0")
        BigDecimal minPrice,
        @DecimalMin("0")
        BigDecimal maxPrice
) {

    @AssertTrue(message = "Invalid time range: 'from' must be before 'to'")
    public boolean isTimeRangeValid() {
        if (from != null && to != null) {
            return from.isBefore(to);
        }
        return true;
    }

    @AssertTrue(message = "Invalid price range: 'minPrice' must be less than or equal to 'maxPrice'")
    public boolean isPriceRangeValid() {
        if (minPrice != null && maxPrice != null) {
            return minPrice.compareTo(maxPrice) <= 0;
        }
        return true;
    }
}
