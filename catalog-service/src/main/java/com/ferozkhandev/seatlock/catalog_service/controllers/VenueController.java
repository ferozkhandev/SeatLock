package com.ferozkhandev.seatlock.catalog_service.controllers;

import com.ferozkhandev.seatlock.catalog_service.domain.dtos.VenueRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.VenueResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.services.VenueService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@AllArgsConstructor
@RequestMapping("/venues")
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<VenueResponseDTO> createVenue(@Valid @RequestBody VenueRequestDTO venueRequestDTO) {
        var response = venueService.createVenue(venueRequestDTO);
        return ResponseEntity.created(URI.create("/venues/" + response.id())).body(response);
    }

}
