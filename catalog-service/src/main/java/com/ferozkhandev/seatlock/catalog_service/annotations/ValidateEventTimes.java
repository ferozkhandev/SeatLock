package com.ferozkhandev.seatlock.catalog_service.annotations;

import com.ferozkhandev.seatlock.catalog_service.constraints.EventTimesValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventTimesValidator.class)
public @interface ValidateEventTimes {
    String message() default "Event end time must be after start time";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
