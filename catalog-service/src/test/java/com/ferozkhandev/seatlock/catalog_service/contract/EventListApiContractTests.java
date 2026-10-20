package com.ferozkhandev.seatlock.catalog_service.contract;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T1.2a HTTP contract. Each test inserts only its own small PostgreSQL fixture. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.cache.use_query_cache=false",
        // Loading only main migrations keeps the dev seed out of this test database.
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class EventListApiContractTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID venueId;
    private UUID organizerId;
    private Instant startsAt;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, event, venue_section, venue CASCADE");
        organizerId = UUID.randomUUID();
        startsAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        venueId = saveVenue("Lahore Arts Hall", "Lahore");
    }

    @Test
    void listPublishedUpcomingEventsByDefault() throws Exception {
        UUID publishedId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(3600), "100.00", "DRAFT");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(7200), "100.00", "CANCELLED");
        saveEvent(venueId, "CONCERT", Instant.now().minus(1, ChronoUnit.DAYS), "100.00", "PUBLISHED");
        // This event has started but has not ended. It must still be excluded.
        saveEvent(venueId, "CONCERT", Instant.now().minus(1, ChronoUnit.HOURS), "100.00", "PUBLISHED");

        JsonNode page = readPage(get("/events"));

        assertIds(page, publishedId);
        assertPageMetadata(page, 0, 20, 1, 1);
    }

    @Test
    void returnEmptyPageWhenThereAreNoEvents() throws Exception {
        JsonNode page = readPage(get("/events"));

        assertIds(page);
        assertPageMetadata(page, 0, 20, 0, 0);
    }

    @Test
    void returnSummaryWithVenueNameAndCityWithoutSections() throws Exception {
        UUID eventId = saveEvent(venueId, "CONCERT", startsAt, "2500.00", "PUBLISHED");
        saveSection(venueId);
        jdbcTemplate.update("UPDATE event SET name = ?, poster_key = ? WHERE id = ?",
                "Winter Music Night", "posters/winter.png", eventId);

        var response = mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Winter Music Night"))
                .andExpect(jsonPath("$.content[0].category").value("CONCERT"))
                .andExpect(jsonPath("$.content[0].basePrice").isNumber())
                .andExpect(jsonPath("$.content[0].basePrice").value(2500.0))
                .andExpect(jsonPath("$.content[0].posterKey").value("posters/winter.png"))
                .andExpect(jsonPath("$.content[0].venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.content[0].venueName").value("Lahore Arts Hall"))
                .andExpect(jsonPath("$.content[0].city").value("Lahore"))
                .andExpect(jsonPath("$.content[0].sections").doesNotExist())
                .andExpect(jsonPath("$.content[0].venue.sections").doesNotExist())
                .andReturn();

        JsonNode page = objectMapper.readTree(response.getResponse().getContentAsString());
        assertEquals(startsAt, Instant.parse(page.path("content").get(0).path("startsAt").asString()));
        assertEquals(startsAt.plus(3, ChronoUnit.HOURS),
                Instant.parse(page.path("content").get(0).path("endsAt").asString()));
    }

    @Test
    void allowSummaryWithoutAPoster() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");

        JsonNode summary = readPage(get("/events")).path("content").get(0);

        assertTrue(summary.has("posterKey") && summary.path("posterKey").isNull());
    }

    @Test
    void filterByCity() throws Exception {
        UUID matchingId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        UUID otherVenueId = saveVenue("Karachi Hall", "Karachi");
        saveEvent(otherVenueId, "CONCERT", startsAt.plusSeconds(3600), "100.00", "PUBLISHED");

        JsonNode page = readPage(get("/events").param("city", "Lahore"));

        assertIds(page, matchingId);
        assertEquals(1, page.path("totalElements").asLong());
    }

    @Test
    void filterByCategory() throws Exception {
        UUID matchingId = saveEvent(venueId, "THEATER", startsAt, "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(3600), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("category", "THEATER")), matchingId);
    }

    @Test
    void filterByFromIncludingItsBoundary() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt.minusSeconds(1), "100.00", "PUBLISHED");
        UUID atBoundaryId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        UUID afterBoundaryId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("from", startsAt.toString())), atBoundaryId, afterBoundaryId);
    }

    @Test
    void filterByToIncludingItsBoundary() throws Exception {
        UUID beforeBoundaryId = saveEvent(venueId, "CONCERT", startsAt.minusSeconds(1), "100.00", "PUBLISHED");
        UUID atBoundaryId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("to", startsAt.toString())), beforeBoundaryId, atBoundaryId);
    }

    @Test
    void filterByMinPriceIncludingItsBoundary() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt, "99.99", "PUBLISHED");
        UUID atBoundaryId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        UUID aboveBoundaryId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "100.01", "PUBLISHED");

        assertIds(readPage(get("/events").param("minPrice", "100.00")), atBoundaryId, aboveBoundaryId);
    }

    @Test
    void filterByMaxPriceIncludingItsBoundary() throws Exception {
        UUID belowBoundaryId = saveEvent(venueId, "CONCERT", startsAt, "99.99", "PUBLISHED");
        UUID atBoundaryId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "100.01", "PUBLISHED");

        assertIds(readPage(get("/events").param("maxPrice", "100.00")), belowBoundaryId, atBoundaryId);
    }

    @Test
    void combineCityAndCategoryWithAnd() throws Exception {
        UUID matchingId = saveEvent(venueId, "THEATER", startsAt, "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        UUID otherVenueId = saveVenue("Karachi Hall", "Karachi");
        saveEvent(otherVenueId, "THEATER", startsAt.plusSeconds(2), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("city", "Lahore").param("category", "THEATER")), matchingId);
    }

    @Test
    void combineAllSixFilters() throws Exception {
        Instant matchingTime = startsAt.plus(3, ChronoUnit.DAYS);
        UUID matchingId = saveEvent(venueId, "THEATER", matchingTime, "200.00", "PUBLISHED");
        UUID otherVenueId = saveVenue("Karachi Hall", "Karachi");
        // Each decoy violates one filter, so accidentally omitting any filter admits an extra row.
        saveEvent(otherVenueId, "THEATER", matchingTime, "200.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", matchingTime, "200.00", "PUBLISHED");
        saveEvent(venueId, "THEATER", startsAt.plus(1, ChronoUnit.DAYS), "200.00", "PUBLISHED");
        saveEvent(venueId, "THEATER", startsAt.plus(5, ChronoUnit.DAYS), "200.00", "PUBLISHED");
        saveEvent(venueId, "THEATER", matchingTime, "99.99", "PUBLISHED");
        saveEvent(venueId, "THEATER", matchingTime, "300.01", "PUBLISHED");
        saveEvent(venueId, "THEATER", matchingTime, "200.00", "DRAFT");

        JsonNode page = readPage(get("/events")
                .param("city", "Lahore").param("category", "THEATER")
                .param("from", startsAt.plus(2, ChronoUnit.DAYS).toString())
                .param("to", startsAt.plus(4, ChronoUnit.DAYS).toString())
                .param("minPrice", "100.00").param("maxPrice", "300.00"));

        assertIds(page, matchingId);
        assertPageMetadata(page, 0, 20, 1, 1);
    }

    @Test
    void pastFromCannotExposePastEvents() throws Exception {
        UUID matchingId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        saveEvent(venueId, "CONCERT", past, "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("from", past.minusSeconds(3600).toString())), matchingId);
    }

    @Test
    void pastToReturnsAnEmptyPage() throws Exception {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        saveEvent(venueId, "CONCERT", past.minusSeconds(3600), "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");

        JsonNode page = readPage(get("/events").param("to", past.toString()));

        assertIds(page);
        assertEquals(0, page.path("totalElements").asLong());
    }

    @Test
    void returnEmptyPageWhenFiltersMatchNothing() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");

        JsonNode page = readPage(get("/events").param("city", "Islamabad"));

        assertIds(page);
        assertPageMetadata(page, 0, 20, 0, 0);
    }

    @Test
    void acceptZeroMinPrice() throws Exception {
        UUID matchingId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("minPrice", "0")), matchingId);
    }

    @Test
    void acceptZeroMaxPrice() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("maxPrice", "0")));
    }

    @Test
    void acceptEqualPriceBoundsWithDifferentDecimalScales() throws Exception {
        saveEvent(venueId, "CONCERT", startsAt, "99.99", "PUBLISHED");
        UUID matchingId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "100.01", "PUBLISHED");

        assertIds(readPage(get("/events").param("minPrice", "100.0").param("maxPrice", "100.00")), matchingId);
    }

    @Test
    void returnBadRequestWhenMinPriceIsNegative() throws Exception {
        expectValidationError(get("/events").param("minPrice", "-0.01"));
    }

    @Test
    void returnBadRequestWhenMaxPriceIsNegative() throws Exception {
        expectValidationError(get("/events").param("maxPrice", "-0.01"));
    }

    @Test
    void returnBadRequestWhenMinPriceExceedsMaxPrice() throws Exception {
        expectValidationError(get("/events").param("minPrice", "200").param("maxPrice", "100"));
    }

    @Test
    void returnBadRequestWhenFromIsAfterTo() throws Exception {
        expectValidationError(get("/events")
                .param("from", startsAt.plusSeconds(1).toString()).param("to", startsAt.toString()));
    }

    @Test
    void returnBadRequestWhenFromEqualsTo() throws Exception {
        expectValidationError(get("/events").param("from", startsAt.toString()).param("to", startsAt.toString()));
    }

    @Test
    void returnBadRequestWhenCategoryIsUnknown() throws Exception {
        expectBadRequest(get("/events").param("category", "UNKNOWN"));
    }

    @Test
    void returnBadRequestWhenFromIsMalformed() throws Exception {
        expectBadRequest(get("/events").param("from", "not-a-date"));
    }

    @Test
    void returnBadRequestWhenToIsMalformed() throws Exception {
        expectBadRequest(get("/events").param("to", "not-a-date"));
    }

    @Test
    void returnBadRequestWhenMinPriceIsMalformed() throws Exception {
        expectBadRequest(get("/events").param("minPrice", "not-a-number"));
    }

    @Test
    void returnBadRequestWhenMaxPriceIsMalformed() throws Exception {
        expectBadRequest(get("/events").param("maxPrice", "not-a-number"));
    }

    @Test
    void defaultPageSizeIsTwenty() throws Exception {
        for (int i = 0; i < 21; i++) {
            saveEvent(venueId, "CONCERT", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }

        JsonNode page = readPage(get("/events"));

        assertEquals(20, page.path("content").size());
        assertPageMetadata(page, 0, 20, 21, 2);
    }

    @Test
    void returnRequestedPageWithCorrectMetadata() throws Exception {
        UUID[] ids = new UUID[8];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }

        JsonNode page = readPage(get("/events").param("page", "1").param("size", "5"));

        assertIds(page, ids[5], ids[6], ids[7]);
        assertPageMetadata(page, 1, 5, 8, 2);
    }

    @Test
    void paginateEventsWithEqualPricesWithoutDuplicates() throws Exception {
        List<String> insertedIds = new ArrayList<>();
        // Insert fixed IDs in reverse order so insertion order cannot stand in for id ASC.
        // Both sortable fields tie, so only the appended ID can break these ties.
        for (int i = 45; i > 0; i--) {
            UUID id = new UUID(0, i);
            saveEvent(id, venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
            insertedIds.add(id.toString());
        }
        List<String> expectedIds = insertedIds.stream().sorted().toList();
        List<String> actualIds = new ArrayList<>();

        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            JsonNode page = readPage(get("/events")
                    .param("page", Integer.toString(pageNumber))
                    .param("size", "20")
                    .param("sort", "basePrice,asc"));

            assertPageMetadata(page, pageNumber, 20, 45, 3);
            assertEquals(pageNumber < 2 ? 20 : 5, page.path("content").size());
            page.path("content").valueStream()
                    .map(event -> event.path("id").asString()).forEach(actualIds::add);
        }

        assertEquals(45, actualIds.size(), "The three pages must return 45 events");
        assertEquals(45, new HashSet<>(actualIds).size(), "No event may appear on more than one page");
        assertEquals(new HashSet<>(insertedIds), new HashSet<>(actualIds),
                "Every inserted event must appear across the three pages");
        assertEquals(expectedIds, actualIds,
                "Events with equal prices must follow id ASC across page boundaries");
    }

    @Test
    void countOnlyEventsThatMatchFiltersAndPublicVisibility() throws Exception {
        UUID[] ids = new UUID[3];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = saveEvent(venueId, "THEATER", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }
        saveEvent(venueId, "CONCERT", startsAt.plusSeconds(10), "100.00", "PUBLISHED");
        saveEvent(venueId, "THEATER", startsAt.plusSeconds(20), "100.00", "DRAFT");
        saveEvent(venueId, "THEATER", Instant.now().minus(1, ChronoUnit.DAYS), "100.00", "PUBLISHED");

        JsonNode page = readPage(get("/events").param("category", "THEATER").param("size", "2"));

        assertIds(page, ids[0], ids[1]);
        assertPageMetadata(page, 0, 2, 3, 2);
    }

    @Test
    void returnEmptyContentBeyondTheLastPageWhileKeepingTotals() throws Exception {
        for (int i = 0; i < 3; i++) {
            saveEvent(venueId, "CONCERT", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }

        JsonNode page = readPage(get("/events").param("page", "5").param("size", "2"));

        assertIds(page);
        assertPageMetadata(page, 5, 2, 3, 2);
    }

    @Test
    void capPageSizeJustAboveOneHundred() throws Exception {
        assertPageSizeIsCapped("101");
    }

    @Test
    void capAnExtremelyLargePageSize() throws Exception {
        assertPageSizeIsCapped("1000000");
    }

    @Test
    void defaultSortIsStartsAtAscending() throws Exception {
        UUID lateId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "100.00", "PUBLISHED");
        UUID earlyId = saveEvent(venueId, "CONCERT", startsAt, "300.00", "PUBLISHED");
        UUID middleId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "200.00", "PUBLISHED");

        assertIds(readPage(get("/events")), earlyId, middleId, lateId);
    }

    @Test
    void allowSortingByStartsAtDescending() throws Exception {
        UUID earlyId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        UUID lateId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("sort", "startsAt,desc")), lateId, earlyId);
    }

    @Test
    void allowSortingByBasePriceAscending() throws Exception {
        UUID expensiveId = saveEvent(venueId, "CONCERT", startsAt, "300.00", "PUBLISHED");
        UUID cheapId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        UUID middleId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "200.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("sort", "basePrice,asc")), cheapId, middleId, expensiveId);
    }

    @Test
    void allowSortingByBasePriceDescending() throws Exception {
        UUID cheapId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        UUID expensiveId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "300.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("sort", "basePrice,desc")), expensiveId, cheapId);
    }

    @Test
    void allowAnApprovedSortFieldWithoutAnExplicitDirection() throws Exception {
        UUID expensiveId = saveEvent(venueId, "CONCERT", startsAt, "300.00", "PUBLISHED");
        UUID cheapId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("sort", "basePrice")), cheapId, expensiveId);
    }

    @Test
    void allowMultipleSortOrdersUsingAllowedFields() throws Exception {
        UUID earlyCheapId = saveEvent(venueId, "CONCERT", startsAt, "100.00", "PUBLISHED");
        UUID lateCheapId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(1), "100.00", "PUBLISHED");
        UUID expensiveId = saveEvent(venueId, "CONCERT", startsAt.plusSeconds(2), "200.00", "PUBLISHED");

        assertIds(readPage(get("/events").param("sort", "basePrice,asc", "startsAt,desc")),
                lateCheapId, earlyCheapId, expensiveId);
    }

    @Test
    void returnBadRequestForAnUnknownSortField() throws Exception {
        expectBadRequest(get("/events").param("sort", "nonsense,asc"));
    }

    @Test
    void returnBadRequestForDescriptionSort() throws Exception {
        expectBadRequest(get("/events").param("sort", "description,asc"));
    }

    @Test
    void returnBadRequestForANestedVenueSort() throws Exception {
        expectBadRequest(get("/events").param("sort", "venue.city,asc"));
    }

    @Test
    void returnBadRequestForADisallowedSortFieldWithoutAnExplicitDirection() throws Exception {
        expectBadRequest(get("/events").param("sort", "name"));
    }

    @Test
    void returnBadRequestWhenAnySortOrderIsDisallowed() throws Exception {
        // Checking only the first sort order would miss the disallowed second one.
        expectBadRequest(get("/events").param("sort", "startsAt,asc", "description,desc"));
    }

    @Test
    void fetchOneEventPageWithAtMostTwoStatements() throws Exception {
        assertPageQueryCount(1);
    }

    @Test
    void fetchTwentyEventPageWithAtMostTwoStatements() throws Exception {
        assertPageQueryCount(20);
    }

    @Test
    void fetchOneHundredEventPageWithAtMostTwoStatements() throws Exception {
        assertPageQueryCount(100);
    }

    private UUID saveVenue(String name, String city) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, city, address) VALUES (?, ?, ?, ?)",
                id, name, city, "12 Example Road");
        return id;
    }

    private void saveSection(UUID sectionVenueId) {
        jdbcTemplate.update("""
                INSERT INTO venue_section (id, venue_id, name, row_count, seats_per_row, price_multiplier)
                VALUES (?, ?, 'Stalls', 10, 20, 1.00)
                """, UUID.randomUUID(), sectionVenueId);
    }

    private UUID saveEvent(UUID eventVenueId, String category, Instant start, String price, String eventStatus) {
        return saveEvent(UUID.randomUUID(), eventVenueId, category, start, price, eventStatus);
    }

    private UUID saveEvent(UUID id, UUID eventVenueId, String category, Instant start, String price, String eventStatus) {
        jdbcTemplate.update("""
                INSERT INTO event (id, venue_id, organizer_id, name, description, category,
                                   starts_at, ends_at, base_price, status, version)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?, 0)
                """, id, eventVenueId, organizerId, "Event " + id, "An example event.", category,
                start.toString(), start.plus(3, ChronoUnit.HOURS).toString(), new BigDecimal(price), eventStatus);
        return id;
    }

    private JsonNode readPage(MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    private void assertIds(JsonNode page, UUID... expectedIds) {
        List<String> actualIds = page.path("content").valueStream()
                .map(event -> event.path("id").asString()).toList();
        List<String> expected = java.util.Arrays.stream(expectedIds).map(UUID::toString).toList();
        assertEquals(expected, actualIds, "The page must contain exactly these events in this order");
    }

    private void assertPageMetadata(JsonNode page, int number, int size, long totalElements, int totalPages) {
        assertEquals(number, page.path("page").asInt(), "page");
        assertEquals(size, page.path("size").asInt(), "size");
        assertEquals(totalElements, page.path("totalElements").asLong(), "totalElements");
        assertEquals(totalPages, page.path("totalPages").asInt(), "totalPages");
    }

    private ResultActions expectBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());
    }

    private void expectValidationError(MockHttpServletRequestBuilder request) throws Exception {
        expectBadRequest(request)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.errors[*].field", everyItem(not(emptyOrNullString()))))
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));
    }

    private void assertPageSizeIsCapped(String requestedSize) throws Exception {
        for (int i = 0; i < 103; i++) {
            saveEvent(venueId, "CONCERT", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }

        JsonNode page = readPage(get("/events").param("size", requestedSize));

        assertEquals(100, page.path("content").size());
        assertPageMetadata(page, 0, 100, 103, 2);
    }

    private void assertPageQueryCount(int pageSize) throws Exception {
        // A different venue for EVERY event prevents Hibernate's identity map from hiding N+1.
        // Sections exist too, so accidentally mapping sections will incur additional statements.
        for (int i = 0; i < pageSize + 1; i++) {
            UUID differentVenueId = saveVenue("Venue " + i, "Lahore");
            saveSection(differentVenueId);
            saveEvent(differentVenueId, "CONCERT", startsAt.plusSeconds(i), "100.00", "PUBLISHED");
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertTrue(statistics.isStatisticsEnabled(), "Hibernate statistics must be enabled for this test");
        // Fixture writes happen before this reset; only this HTTP request is measured.
        // There is no test-level transaction or preloaded persistence context.
        statistics.clear();

        JsonNode page = readPage(get("/events").param("size", Integer.toString(pageSize)));
        long statementCount = statistics.getPrepareStatementCount();

        assertEquals(pageSize, page.path("content").size());
        // One extra matching row ensures a full first page and a meaningful total count.
        assertPageMetadata(page, 0, pageSize, pageSize + 1L, 2);
        for (int i = 0; i < pageSize; i++) {
            assertEquals("Venue " + i, page.path("content").get(i).path("venueName").asString());
            assertEquals("Lahore", page.path("content").get(i).path("city").asString());
        }
        assertTrue(statementCount > 0, "The measured request must execute SQL");
        assertTrue(statementCount <= 2,
                "A page of " + pageSize + " events must cost at most 2 statements, but executed " + statementCount);
    }
}
