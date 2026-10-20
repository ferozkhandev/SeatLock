package com.ferozkhandev.seatlock.catalog_service.constraints;

import com.ferozkhandev.seatlock.catalog_service.annotations.ValidateEventTimes;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventTimesValidator implements ConstraintValidator<ValidateEventTimes, EventRequestDTO> {

    @Override
    public boolean isValid(EventRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.startsAt() == null || dto.endsAt() == null) {
            return true; // Let @NotNull handle null checks
        }
        return dto.startsAt().isBefore(dto.endsAt());
    }
}
