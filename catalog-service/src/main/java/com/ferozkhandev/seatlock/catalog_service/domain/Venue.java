package com.ferozkhandev.seatlock.catalog_service.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "venue")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "address", nullable = false)
    private String address;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private List<VenueSection> sections = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void addSection(VenueSection section) {
        Objects.requireNonNull(section, "Section must not be null");
        sections.add(section);
        section.setVenue(this);
    }

    public void removeSection(VenueSection section) {
        if (sections.remove(section)) {
            section.setVenue(null);
        }
    }
}
