package com.ferozkhandev.seatlock.catalog_service.exceptions.event;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID eventId) {
        super("Event " + eventId + " not found");
    }
}
