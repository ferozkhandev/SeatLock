package com.ferozkhandev.seatlock.catalog_service.exceptions.event;

public class EventWrongSortKeyException extends RuntimeException {
    public EventWrongSortKeyException(String key) {
        super("Sorting by " + key + " is not allowed");
    }
}
