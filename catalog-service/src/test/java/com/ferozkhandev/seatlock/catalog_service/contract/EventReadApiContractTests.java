package com.ferozkhandev.seatlock.catalog_service.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventReadApiContractTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID venueId;
    private UUID organizerId;
    private Instant startsAt;
    private Instant endsAt;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, event, venue_section, venue CASCADE");
        venueId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        startsAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        endsAt = startsAt.plus(3, ChronoUnit.HOURS);

        // Insert a venue so event tests don't need POST /venues to work first.
        jdbcTemplate.update("INSERT INTO venue (id, name, city, address) VALUES (?, ?, ?, ?)",
                venueId, "Lahore Arts Hall", "Lahore", "12 Example Road");
    }

    @Test
    void getEvent() throws Exception {
        UUID eventId = saveEvent();

        MvcResult response = mockMvc.perform(get("/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.organizerId").value(organizerId.toString()))
                .andExpect(jsonPath("$.name").value("Winter Music Night"))
                .andExpect(jsonPath("$.description").value("An evening of live music."))
                .andExpect(jsonPath("$.category").value("CONCERT"))
                .andExpect(jsonPath("$.basePrice").isNumber())
                .andExpect(jsonPath("$.basePrice").value(2500.0))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.posterKey").value(nullValue()))
                .andExpect(jsonPath("$.venue.id").value(venueId.toString()))
                .andExpect(jsonPath("$.venue.name").value("Lahore Arts Hall"))
                .andExpect(jsonPath("$.venue.city").value("Lahore"))
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(response.getResponse().getContentAsString());
        assertEquals(startsAt, Instant.parse(responseBody.path("startsAt").asString()));
        assertEquals(endsAt, Instant.parse(responseBody.path("endsAt").asString()));
    }

    @Test
    void getEventWithCurrentStoredValues() throws Exception {
        UUID eventId = saveEvent();
        jdbcTemplate.update("UPDATE event SET name = ?, status = 'PUBLISHED', version = 3, poster_key = ? WHERE id = ?",
                "Updated Music Night", "posters/current.png", eventId);

        MvcResult response = mockMvc.perform(get("/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.name").value("Updated Music Night"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.posterKey").value("posters/current.png"))
                .andExpect(jsonPath("$.venue.id").value(venueId.toString()))
                .andReturn();
    }

    @Test
    void returnNotFoundWhenEventDoesNotExist() throws Exception {
        UUID missingEventId = UUID.randomUUID();
        String path = "/events/" + missingEventId;

        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString(missingEventId.toString())));
    }

    @Test
    void returnBadRequestWhenEventIdIsMalformed() throws Exception {
        mockMvc.perform(get("/events/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events/not-a-uuid"))
                .andExpect(jsonPath("$.code").isNotEmpty());
    }

    @Test
    void createVenueThenCreateEventThenGetEvent() throws Exception {
        String venueRequestBody = """
                {
                    "name": "Lahore Arts Hall",
                    "city": "Lahore",
                    "address": "12 Example Road",
                    "sections": [
                        { "name": "Stalls", "rowCount": 10, "seatsPerRow": 20, "priceMultiplier": 1.00 }
                    ]
                }
                """;
        MvcResult venueResponse = mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueRequestBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode venueResponseBody = objectMapper.readTree(venueResponse.getResponse().getContentAsString());
        UUID createdVenueId = UUID.fromString(venueResponseBody.path("id").asString());

        ObjectNode eventRequestBody = createEventRequest();
        eventRequestBody.put("venueId", createdVenueId.toString());
        MvcResult eventResponse = mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Winter Music Night"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.venue.id").value(createdVenueId.toString()))
                .andReturn();
        JsonNode eventResponseBody = objectMapper.readTree(eventResponse.getResponse().getContentAsString());
        UUID createdEventId = UUID.fromString(eventResponseBody.path("id").asString());
        assertEquals("/events/" + createdEventId, URI.create(eventResponse.getResponse().getHeader("Location")).getPath());

        MvcResult getResponse = mockMvc.perform(get("/events/" + createdEventId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        JsonNode getResponseBody = objectMapper.readTree(getResponse.getResponse().getContentAsString());
        assertEquals(eventResponseBody, getResponseBody);
    }

    private ObjectNode createEventRequest() throws Exception {
        String requestBody = """
                {
                    "venueId": "%s",
                    "name": "Winter Music Night",
                    "description": "An evening of live music.",
                    "category": "CONCERT",
                    "startsAt": "%s",
                    "endsAt": "%s",
                    "basePrice": 2500.00
                }
                """.formatted(venueId, startsAt, endsAt);
        return (ObjectNode) objectMapper.readTree(requestBody);
    }

    private UUID saveEvent() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO event (id, venue_id, organizer_id, name, description, category,
                                   starts_at, ends_at, base_price, status, version)
                VALUES (?, ?, ?, ?, ?, 'CONCERT', CAST(? AS timestamptz), CAST(? AS timestamptz), ?, 'DRAFT', 0)
                """, eventId, venueId, organizerId, "Winter Music Night", "An evening of live music.",
                startsAt.toString(), endsAt.toString(), new BigDecimal("2500.00"));
        return eventId;
    }
}
