package com.ferozkhandev.seatlock.catalog_service.utils;

import com.ferozkhandev.seatlock.catalog_service.domain.Event;
import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventSummary;

import java.util.Objects;

public final class EventConversionUtil {

    private EventConversionUtil() {
        throw new UnsupportedOperationException("Object of util class cannot be created");
    }

    public static Event toEntity(EventRequestDTO dto) {
        Objects.requireNonNull(dto, "Event request must not be null");

        Event event = new Event();
        event.setName(dto.name());
        event.setDescription(dto.description());
        event.setCategory(dto.category());
        event.setStartsAt(dto.startsAt());
        event.setEndsAt(dto.endsAt());
        event.setBasePrice(dto.basePrice());
        return event;
    }

    public static EventResponseDTO toResponseDTO(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        return new EventResponseDTO(
                event.getId().toString(),
                event.getOrganizerId().toString(),
                event.getName(),
                event.getDescription(),
                event.getCategory().name(),
                event.getStartsAt().toString(),
                event.getEndsAt().toString(),
                event.getBasePrice(),
                event.getStatus().name(),
                event.getPosterKey(),
                VenueConversionUtil.toResponseDTO(event.getVenue())
        );
    }

    public static EventSummary toSummary(Event event) {
        Objects.requireNonNull(event, "Event must not be null");

        Venue venue = Objects.requireNonNull(event.getVenue(), "Venue must not be null");

        return new EventSummary(
                event.getId().toString(),
                event.getName(),
                event.getCategory().name(),
                event.getStartsAt().toString(),
                event.getEndsAt().toString(),
                event.getBasePrice(),
                event.getPosterKey(),
                Objects.requireNonNull(venue.getId(), "Venue ID must not be null").toString(),
                venue.getName(),
                venue.getCity()
        );
    }
}
