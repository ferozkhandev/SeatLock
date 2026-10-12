package com.ferozkhandev.seatlock.catalog_service.repositories;

import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
