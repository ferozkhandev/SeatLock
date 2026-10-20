package com.ferozkhandev.seatlock.catalog_service.domain;

import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventFilterRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Fast validation checks. These do not need Docker or a Spring application. */
class EventFilterRequestDTOTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static final Instant FROM = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant TO = FROM.plusSeconds(3600);

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptAllFiltersBeingAbsent() {
        assertValid(new EventFilterRequestDTO(null, null, null, null, null, null));
    }

    @Test
    void acceptCityAndCategoryWithoutRanges() {
        assertValid(new EventFilterRequestDTO("Lahore", EventCategory.CONCERT, null, null, null, null));
    }

    @Test
    void acceptFromWithoutTo() {
        assertValid(new EventFilterRequestDTO(null, null, FROM, null, null, null));
    }

    @Test
    void acceptToWithoutFrom() {
        assertValid(new EventFilterRequestDTO(null, null, null, TO, null, null));
    }

    @Test
    void acceptFromBeforeTo() {
        assertValid(new EventFilterRequestDTO(null, null, FROM, TO, null, null));
    }

    @Test
    void rejectFromAfterTo() {
        assertInvalid(new EventFilterRequestDTO(null, null, TO, FROM, null, null));
    }

    @Test
    void rejectFromEqualToTo() {
        assertInvalid(new EventFilterRequestDTO(null, null, FROM, FROM, null, null));
    }

    @Test
    void acceptPastDateBounds() {
        // The service enforces upcoming events. A search bound itself need not be in the future.
        Instant past = Instant.parse("2000-01-01T00:00:00Z");
        assertValid(new EventFilterRequestDTO(null, null, past, past.plusSeconds(3600), null, null));
    }

    @Test
    void acceptMinPriceWithoutMaxPrice() {
        assertValid(new EventFilterRequestDTO(null, null, null, null, new BigDecimal("100.00"), null));
    }

    @Test
    void acceptMaxPriceWithoutMinPrice() {
        assertValid(new EventFilterRequestDTO(null, null, null, null, null, new BigDecimal("100.00")));
    }

    @Test
    void acceptZeroPriceBounds() {
        assertValid(new EventFilterRequestDTO(null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectNegativeMinPrice() {
        var violations = validator.validate(
                new EventFilterRequestDTO(null, null, null, null, new BigDecimal("-0.01"), null));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("minPrice")),
                "A negative minPrice must have a validation error on minPrice");
    }

    @Test
    void rejectNegativeMaxPrice() {
        var violations = validator.validate(
                new EventFilterRequestDTO(null, null, null, null, null, new BigDecimal("-0.01")));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxPrice")),
                "A negative maxPrice must have a validation error on maxPrice");
    }

    @Test
    void acceptMinPriceBelowMaxPrice() {
        assertValid(new EventFilterRequestDTO(null, null, null, null,
                new BigDecimal("100.00"), new BigDecimal("200.00")));
    }

    @Test
    void rejectMinPriceAboveMaxPrice() {
        assertInvalid(new EventFilterRequestDTO(null, null, null, null,
                new BigDecimal("200.00"), new BigDecimal("100.00")));
    }

    @Test
    void acceptEqualPricesWithDifferentDecimalScales() {
        assertValid(new EventFilterRequestDTO(null, null, null, null,
                new BigDecimal("100.0"), new BigDecimal("100.00")));
    }

    private void assertValid(EventFilterRequestDTO filters) {
        var violations = validator.validate(filters);
        assertTrue(violations.isEmpty(), () -> "Unexpected validation errors: " + violations);
    }

    private void assertInvalid(EventFilterRequestDTO filters) {
        Set<ConstraintViolation<EventFilterRequestDTO>> violations = validator.validate(filters);
        assertFalse(violations.isEmpty(), "An invalid range must produce a validation error");
        for (var violation : violations) {
            assertFalse(violation.getMessage().isBlank(), "Each validation error needs a useful message");
        }
    }
}
