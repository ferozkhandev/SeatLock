package com.ferozkhandev.seatlock.catalog_service.utils;

import com.ferozkhandev.seatlock.catalog_service.domain.Event;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventResponseDTO;

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
}
