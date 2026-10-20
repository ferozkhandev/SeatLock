package com.ferozkhandev.seatlock.catalog_service.services;

import com.ferozkhandev.seatlock.catalog_service.domain.Event;
import com.ferozkhandev.seatlock.catalog_service.domain.EventStatus;
import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.PageResponse;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventFilterRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.event.EventSummary;
import com.ferozkhandev.seatlock.catalog_service.exceptions.event.EventNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.exceptions.event.EventWrongSortKeyException;
import com.ferozkhandev.seatlock.catalog_service.exceptions.venue.VenueNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.repositories.EventRepository;
import com.ferozkhandev.seatlock.catalog_service.repositories.VenueRepository;
import com.ferozkhandev.seatlock.catalog_service.repositories.specifications.EventSpecifications;
import com.ferozkhandev.seatlock.catalog_service.utils.EventConversionUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private static final Set<String> ALLOWD_SORT_BY = Set.of("startsAt", "basePrice");

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

    @Transactional(readOnly = true)
    public PageResponse<EventSummary> getEvents(EventFilterRequestDTO filterRequest, Pageable pageable) {
        validateSort(pageable);

        /* Appending id here to keep data consistent as in pagination every page is a new query and if let say data is
           being queried over price and there are multiple rows with same price then those rows can be inconsistent
           among pages. To make them consistent we can use id to sort data on.*/
        var sort = pageable.getSort().and(Sort.by("id"));
        var stablePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        var instant = Instant.now();
        List<Specification<Event>> specifications = new ArrayList<>();
        specifications.add(EventSpecifications.isPublished());
        specifications.add(EventSpecifications.startsAtOrAfter(instant));

        if (filterRequest.city() != null) {
            specifications.add(EventSpecifications.inCity(filterRequest.city()));
        }

        if (filterRequest.category() != null) {
            specifications.add(EventSpecifications.hasCategory(filterRequest.category()));
        }

        if (filterRequest.from() != null) {
            specifications.add(EventSpecifications.startsAtOrAfter(filterRequest.from()));
        }

        if (filterRequest.to() != null) {
            specifications.add(EventSpecifications.startsAtOrBefore(filterRequest.to()));
        }

        if (filterRequest.minPrice() != null) {
            specifications.add(EventSpecifications.basePriceAtLeast(filterRequest.minPrice()));
        }

        if (filterRequest.maxPrice() != null) {
            specifications.add(EventSpecifications.basePriceAtMost(filterRequest.maxPrice()));
        }

        var eventSpecification = Specification.allOf(specifications);
        var pagedEvents = eventRepository.findAll(eventSpecification, stablePageable);
        var eventSummaries = pagedEvents.map(EventConversionUtil::toSummary);
        return new PageResponse<>(eventSummaries.getContent(), pagedEvents.getNumber(),
                pagedEvents.getSize(), pagedEvents.getTotalElements(), pagedEvents.getTotalPages());
    }

    private void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!ALLOWD_SORT_BY.contains(order.getProperty())) {
                throw new EventWrongSortKeyException(order.getProperty());
            }
        });
    }
}
