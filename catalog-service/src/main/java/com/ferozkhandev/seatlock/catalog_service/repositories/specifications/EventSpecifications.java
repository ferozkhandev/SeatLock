package com.ferozkhandev.seatlock.catalog_service.repositories.specifications;

import com.ferozkhandev.seatlock.catalog_service.domain.Event;
import com.ferozkhandev.seatlock.catalog_service.domain.EventCategory;
import com.ferozkhandev.seatlock.catalog_service.domain.EventStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;

public final class EventSpecifications {

    private EventSpecifications() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    public static Specification<Event> isPublished() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), EventStatus.PUBLISHED);
    }

    public static Specification<Event> startsAtOrAfter(Instant instant) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("startsAt"), instant);
    }

    public static Specification<Event> hasCategory(EventCategory category) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("category"), category);
    }

    public static Specification<Event> inCity(String city) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("venue").get("city"), city);
    }

    public static Specification<Event> startsAtOrBefore(Instant instant) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("startsAt"), instant);
    }

    public static Specification<Event> basePriceAtLeast(BigDecimal price) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("basePrice"), price);
    }

    public static Specification<Event> basePriceAtMost(BigDecimal price) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("basePrice"), price);
    }
}
