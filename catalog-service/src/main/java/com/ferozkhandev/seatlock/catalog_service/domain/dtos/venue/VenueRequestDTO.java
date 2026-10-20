package com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue;

import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue_sections.VenueSectionsRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VenueRequestDTO(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 255, message = "name must be between 2 and 255 characters")
        String name,
        @NotBlank(message = "city is required")
        @Size(min = 2, max = 100, message = "city must be between 2 and 100 characters")
        String city,
        @NotBlank(message = "address is required")
        String address,
        @Valid
        @NotEmpty(message = "sections cannot be empty")
        List<@NotNull @Valid VenueSectionsRequestDTO> sections
) {
}
