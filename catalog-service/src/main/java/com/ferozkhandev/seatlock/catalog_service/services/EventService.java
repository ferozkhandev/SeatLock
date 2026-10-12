package com.ferozkhandev.seatlock.catalog_service.services;

import com.ferozkhandev.seatlock.catalog_service.domain.Event;
import com.ferozkhandev.seatlock.catalog_service.domain.EventStatus;
import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.exceptions.event.EventNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.exceptions.event.VenueNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.repositories.EventRepository;
import com.ferozkhandev.seatlock.catalog_service.repositories.VenueRepository;
import com.ferozkhandev.seatlock.catalog_service.utils.EventConversionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;

    public EventService(EventRepository eventRepository, VenueRepository venueRepository) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public EventResponseDTO createEvent(EventRequestDTO eventDTO, UUID organizerId) {
        UUID venueId = eventDTO.venueId();
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        Event event = EventConversionUtil.toEntity(eventDTO);
        event.setVenue(venue);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);
        Event savedEvent = eventRepository.save(event);
        return EventConversionUtil.toResponseDTO(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventResponseDTO getEventById(UUID id) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        return EventConversionUtil.toResponseDTO(event);
    }
}
