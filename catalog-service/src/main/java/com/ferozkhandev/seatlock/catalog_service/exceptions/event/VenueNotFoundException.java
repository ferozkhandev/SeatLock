package com.ferozkhandev.seatlock.catalog_service.exceptions.event;

import java.util.UUID;

public class VenueNotFoundException extends RuntimeException {
    public VenueNotFoundException(UUID venueId) {
        super("Venue " + venueId + " not found");
    }
}
