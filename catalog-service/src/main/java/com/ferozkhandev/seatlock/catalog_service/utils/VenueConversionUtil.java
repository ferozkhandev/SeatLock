package com.ferozkhandev.seatlock.catalog_service.utils;

import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.VenueSection;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue.VenueRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue.VenueResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue_sections.VenueSectionsRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue_sections.VenueSectionsResponseDTO;

import java.util.Objects;

public final class VenueConversionUtil {

    private VenueConversionUtil() {
        throw new UnsupportedOperationException("Object of util class cannot be created");
    }

    public static Venue toEntity(VenueRequestDTO dto) {
        Objects.requireNonNull(dto, "Venue request must not be null");

        Venue venue = new Venue();
        venue.setName(dto.name());
        venue.setCity(dto.city());
        venue.setAddress(dto.address());
        for (VenueSectionsRequestDTO sectionDTO : dto.sections()) {
            venue.addSection(toSectionEntity(sectionDTO));
        }
        return venue;
    }

    public static VenueResponseDTO toResponseDTO(Venue venue) {
        Objects.requireNonNull(venue, "Venue must not be null");

        VenueSectionsResponseDTO[] sections = venue.getSections().stream()
                .map(VenueConversionUtil::toSectionResponseDTO)
                .toArray(VenueSectionsResponseDTO[]::new);

        return new VenueResponseDTO(
                venue.getId().toString(),
                venue.getName(),
                venue.getCity(),
                venue.getAddress(),
                sections
        );
    }

    private static VenueSection toSectionEntity(VenueSectionsRequestDTO dto) {
        VenueSection section = new VenueSection();
        section.setName(dto.name());
        section.setRowCount(dto.rowCount());
        section.setSeatsPerRow(dto.seatsPerRow());
        section.setPriceMultiplier(dto.priceMultiplier());
        return section;
    }

    private static VenueSectionsResponseDTO toSectionResponseDTO(VenueSection section) {
        return new VenueSectionsResponseDTO(
                section.getId().toString(),
                section.getName(),
                section.getRowCount(),
                section.getSeatsPerRow(),
                section.getPriceMultiplier()
        );
    }
}
