package com.ferozkhandev.seatlock.catalog_service.services;

import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue.VenueRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.venue.VenueResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.repositories.VenueRepository;
import com.ferozkhandev.seatlock.catalog_service.utils.VenueConversionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public VenueResponseDTO createVenue(VenueRequestDTO venueRequestDTO) {
        Venue venue = VenueConversionUtil.toEntity(venueRequestDTO);
        Venue savedVenue = venueRepository.save(venue);
        return VenueConversionUtil.toResponseDTO(savedVenue);
    }
}
