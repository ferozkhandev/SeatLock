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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventCreationApiContractTests {

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
    void createEvent() throws Exception {
        ObjectNode requestBody = createEventRequest();

        MvcResult response = mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("Location"))
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
        UUID savedEventId = UUID.fromString(responseBody.path("id").asString());
        String location = response.getResponse().getHeader("Location");
        assertEquals("/events/" + savedEventId, URI.create(location).getPath());
        assertNull(URI.create(location).getQuery());

        Map<String, Object> savedEvent = jdbcTemplate.queryForMap("SELECT * FROM event WHERE id = ?", savedEventId);
        assertEquals(venueId, savedEvent.get("venue_id"));
        assertEquals(organizerId, savedEvent.get("organizer_id"));
        assertEquals("Winter Music Night", savedEvent.get("name"));
        assertEquals("An evening of live music.", savedEvent.get("description"));
        assertEquals("CONCERT", savedEvent.get("category"));
        assertEquals(startsAt, jdbcTemplate.queryForObject("SELECT starts_at FROM event WHERE id = ?", java.sql.Timestamp.class, savedEventId).toInstant());
        assertEquals(endsAt, jdbcTemplate.queryForObject("SELECT ends_at FROM event WHERE id = ?", java.sql.Timestamp.class, savedEventId).toInstant());
        assertEquals(0, new BigDecimal("2500.00").compareTo((BigDecimal) savedEvent.get("base_price")));
        assertEquals("DRAFT", savedEvent.get("status"));
        assertEquals(0, savedEvent.get("version"));
        assertNull(savedEvent.get("poster_key"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
    }

    @Test
    void takeOrganizerIdFromTheRequestHeader() throws Exception {
        UUID differentOrganizerId = UUID.randomUUID();
        ObjectNode requestBody = createEventRequest();

        MvcResult response = mockMvc.perform(post("/events")
                        .header("X-User-Id", differentOrganizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(differentOrganizerId.toString()))
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(response.getResponse().getContentAsString());
        UUID savedEventId = UUID.fromString(responseBody.path("id").asString());
        UUID savedOrganizerId = jdbcTemplate.queryForObject("SELECT organizer_id FROM event WHERE id = ?", UUID.class, savedEventId);
        assertEquals(differentOrganizerId, savedOrganizerId);
    }

    @Test
    void createEventWithConcertCategory() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "CONCERT");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("CONCERT"));
    }

    @Test
    void createEventWithFestivalCategory() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "FESTIVAL");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("FESTIVAL"));
    }

    @Test
    void createEventWithSportsCategory() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "SPORTS");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SPORTS"));
    }

    @Test
    void createEventWithTheaterCategory() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "THEATER");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("THEATER"));
    }

    @Test
    void createEventWithOtherCategory() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "OTHER");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("OTHER"));
    }

    @Test
    void createEventWhenDescriptionIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("description");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void createEventWhenDescriptionIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("description");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void createEventWithMaximumNameLengthAndPrice() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("name", "e".repeat(255));
        requestBody.put("basePrice", new BigDecimal("99999999.99"));

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("e".repeat(255)))
                .andExpect(jsonPath("$.basePrice").isNumber())
                .andExpect(jsonPath("$.basePrice").value(99999999.99));
    }

    @Test
    void returnBadRequestWhenVenueIdIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("venueId");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("venueId")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventNameIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("name");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventNameIsBlank() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("name", " ");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventNameIsTooLong() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("name", "e".repeat(256));

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenCategoryIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("category");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("category")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenStartsAtIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("startsAt");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("startsAt")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEndsAtIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("endsAt");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("endsAt")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceIsNull() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.putNull("basePrice");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceIsZero() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("basePrice", 0);

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceIsNegative() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("basePrice", -1);

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceIsTooLarge() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("basePrice", new BigDecimal("100000000"));

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceHasTooManyDecimalPlaces() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("basePrice", new BigDecimal("2500.001"));

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventVenueIdIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("venueId");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("venueId")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventNameIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("name");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventCategoryIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("category");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("category")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventStartsAtIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("startsAt");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("startsAt")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventEndsAtIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("endsAt");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("endsAt")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventBasePriceIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.remove("basePrice");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventStartsInThePast() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("startsAt")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEventEndsAtTheStartTime() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("endsAt", startsAt.toString());

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void  returnBadRequestWhenEventEndsBeforeTheStartTime() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("endsAt", startsAt.minusSeconds(1).toString());

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueIdIsMalformed() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("venueId", "not-a-uuid");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenCategoryIsUnknown() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("category", "UNKNOWN");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenStartsAtIsMalformed() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("startsAt", "not-a-date");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenEndsAtIsMalformed() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("endsAt", "not-a-date");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenBasePriceIsMalformed() throws Exception {
        ObjectNode requestBody = createEventRequest();
        requestBody.put("basePrice", "not-a-number");

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnNotFoundWhenVenueDoesNotExist() throws Exception {
        UUID missingVenueId = UUID.randomUUID();
        ObjectNode requestBody = createEventRequest();
        requestBody.put("venueId", missingVenueId.toString());

        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString(missingVenueId.toString())));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
    }

    @Test
    void returnBadRequestWhenOrganizerHeaderIsMissing() throws Exception {
        ObjectNode requestBody = createEventRequest();

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenOrganizerHeaderIsMalformed() throws Exception {
        ObjectNode requestBody = createEventRequest();

        mockMvc.perform(post("/events")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void returnBadRequestWhenOrganizerHeaderIsBlank() throws Exception {
        ObjectNode requestBody = createEventRequest();

        mockMvc.perform(post("/events")
                        .header("X-User-Id", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
    }

    @Test
    void doNotAcceptServerControlledValuesFromTheBody() throws Exception {
        UUID suppliedEventId = UUID.randomUUID();
        ObjectNode requestBody = createEventRequest();
        requestBody.put("id", suppliedEventId.toString());
        requestBody.put("organizerId", UUID.randomUUID().toString());
        requestBody.put("status", "PUBLISHED");
        requestBody.put("version", 99);
        requestBody.put("posterKey", "untrusted-poster");

        MvcResult response = mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andReturn();

        int responseStatus = response.getResponse().getStatus();
        JsonNode responseBody = objectMapper.readTree(response.getResponse().getContentAsString());
        // Rejecting extra fields or ignoring them are both valid choices.
        if (responseStatus == 400) {
            assertTrue(response.getResponse().getContentType().startsWith("application/problem+json"));
            assertEquals(400, responseBody.path("status").asInt());
            assertFalse(responseBody.path("type").asString().isBlank());
            assertFalse(responseBody.path("title").asString().isBlank());
            assertFalse(responseBody.path("detail").asString().isBlank());
            assertFalse(responseBody.path("code").asString().isBlank());
            assertEquals("/events", responseBody.path("instance").asString());
            assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
        } else {
            assertEquals(201, responseStatus);
            UUID savedEventId = UUID.fromString(responseBody.path("id").asString());
            assertNotEquals(suppliedEventId, savedEventId);
            assertEquals(organizerId.toString(), responseBody.path("organizerId").asString());
            assertEquals("DRAFT", responseBody.path("status").asString());
            assertEquals(0, responseBody.path("version").asInt());
            assertTrue(responseBody.has("posterKey") && responseBody.path("posterKey").isNull());
            assertEquals("/events/" + savedEventId, URI.create(response.getResponse().getHeader("Location")).getPath());
            Map<String, Object> savedEvent = jdbcTemplate.queryForMap("SELECT * FROM event WHERE id = ?", savedEventId);
            assertEquals(organizerId, savedEvent.get("organizer_id"));
            assertEquals("DRAFT", savedEvent.get("status"));
            assertEquals(0, savedEvent.get("version"));
            assertNull(savedEvent.get("poster_key"));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
        }
    }

    @Test
    void returnBadRequestWhenEventJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/events")
                        .header("X-User-Id", organizerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/events"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class));
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
}
