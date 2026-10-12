package com.ferozkhandev.seatlock.catalog_service.controllers;

import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventRequestDTO;
import com.ferozkhandev.seatlock.catalog_service.domain.dtos.EventResponseDTO;
import com.ferozkhandev.seatlock.catalog_service.services.EventService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<EventResponseDTO> getEventById(@Valid @PathVariable("id") UUID id) {
        EventResponseDTO eventResponseDTO = eventService.getEventById(id);
        return ResponseEntity.ok(eventResponseDTO);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventResponseDTO> createEvent(@Valid @RequestBody EventRequestDTO eventDTO,
                                                        @RequestHeader(value = "X-User-Id", required = true) UUID organizerId) {
        EventResponseDTO eventResponseDTO1 = eventService.createEvent(eventDTO, organizerId);
        return ResponseEntity.created(URI.create("/events/" + eventResponseDTO1.id())).body(eventResponseDTO1);
    }
}
