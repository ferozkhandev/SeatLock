package com.ferozkhandev.seatlock.catalog_service.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VenueTests {

    @Test
    void addSectionLinksBothSides() {
        Venue venue = new Venue();
        VenueSection section = new VenueSection();

        venue.addSection(section);

        assertEquals(1, venue.getSections().size());
        assertSame(section, venue.getSections().getFirst());
        assertSame(venue, section.getVenue());
    }

    @Test
    void removeSectionUnlinksBothSides() {
        Venue venue = new Venue();
        VenueSection section = new VenueSection();
        venue.addSection(section);

        venue.removeSection(section);

        assertTrue(venue.getSections().isEmpty());
        assertNull(section.getVenue());
    }
}
