package com.ferozkhandev.seatlock.catalog_service.contract;

import com.ferozkhandev.seatlock.catalog_service.domain.Venue;
import com.ferozkhandev.seatlock.catalog_service.domain.VenueSection;
import com.ferozkhandev.seatlock.catalog_service.repositories.VenueRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
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
class VenueApiContractTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, event, venue_section, venue CASCADE");
    }

    @Test
    void createVenueWithSections() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode balcony = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        requestBody.withArray("sections").add(balcony);

        MvcResult response = mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Lahore Arts Hall"))
                .andExpect(jsonPath("$.city").value("Lahore"))
                .andExpect(jsonPath("$.address").value("12 Example Road"))
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(response.getResponse().getContentAsString());
        UUID savedVenueId = UUID.fromString(responseBody.path("id").asString());
        String location = response.getResponse().getHeader("Location");
        assertEquals("/venues/" + savedVenueId, URI.create(location).getPath());
        assertNull(URI.create(location).getQuery());
        UUID firstSectionId = UUID.fromString(responseBody.path("sections").get(0).path("id").asString());
        UUID secondSectionId = UUID.fromString(responseBody.path("sections").get(1).path("id").asString());
        assertNotEquals(firstSectionId, secondSectionId);

        // Sections can be returned in either order.
        boolean foundStalls = false;
        boolean foundBalcony = false;
        for (JsonNode section : responseBody.path("sections")) {
            assertTrue(section.path("rowCount").isIntegralNumber());
            assertTrue(section.path("seatsPerRow").isIntegralNumber());
            assertTrue(section.path("priceMultiplier").isNumber());
            if (section.path("name").asString().equals("Stalls")) {
                assertEquals(10, section.path("rowCount").asInt());
                assertEquals(20, section.path("seatsPerRow").asInt());
                assertEquals(0, new BigDecimal("1.00").compareTo(section.path("priceMultiplier").decimalValue()));
                foundStalls = true;
            } else if (section.path("name").asString().equals("Balcony")) {
                assertEquals(5, section.path("rowCount").asInt());
                assertEquals(12, section.path("seatsPerRow").asInt());
                assertEquals(0, new BigDecimal("1.50").compareTo(section.path("priceMultiplier").decimalValue()));
                foundBalcony = true;
            }
            UUID sectionId = UUID.fromString(section.path("id").asString());
            Map<String, Object> savedSection = jdbcTemplate.queryForMap("SELECT * FROM venue_section WHERE id = ?", sectionId);
            assertEquals(savedVenueId, savedSection.get("venue_id"));
            assertEquals(section.path("name").asString(), savedSection.get("name"));
            assertEquals(section.path("rowCount").asInt(), savedSection.get("row_count"));
            assertEquals(section.path("seatsPerRow").asInt(), savedSection.get("seats_per_row"));
            assertEquals(0, section.path("priceMultiplier").decimalValue().compareTo((BigDecimal) savedSection.get("price_multiplier")));
        }
        assertTrue(foundStalls);
        assertTrue(foundBalcony);

        Map<String, Object> savedVenue = jdbcTemplate.queryForMap("SELECT * FROM venue WHERE id = ?", savedVenueId);
        assertEquals("Lahore Arts Hall", savedVenue.get("name"));
        assertEquals("Lahore", savedVenue.get("city"));
        assertEquals("12 Example Road", savedVenue.get("address"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void createVenueWithMinimumSeatCountsAndMaximumFieldLengths() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("name", "v".repeat(255));
        requestBody.put("city", "c".repeat(100));
        ObjectNode section = (ObjectNode) requestBody.path("sections").get(0);
        section.put("name", "s".repeat(100));
        section.put("rowCount", 1);
        section.put("seatsPerRow", 1);
        section.put("priceMultiplier", new BigDecimal("99.99"));

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("v".repeat(255)))
                .andExpect(jsonPath("$.city").value("c".repeat(100)))
                .andExpect(jsonPath("$.sections[0].name").value("s".repeat(100)))
                .andExpect(jsonPath("$.sections[0].rowCount").value(1))
                .andExpect(jsonPath("$.sections[0].seatsPerRow").value(1))
                .andExpect(jsonPath("$.sections[0].priceMultiplier").isNumber())
                .andExpect(jsonPath("$.sections[0].priceMultiplier").value(99.99));
    }

    @Test
    void returnBadRequestWhenVenueNameIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.putNull("name");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueNameIsBlank() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("name", " ");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueNameIsTooLong() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("name", "v".repeat(256));

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenCityIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.putNull("city");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("city")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenCityIsBlank() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("city", " ");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("city")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenCityIsTooLong() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("city", "c".repeat(101));

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("city")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenAddressIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.putNull("address");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenAddressIsBlank() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.put("address", " ");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionsAreNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.putNull("sections");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionsAreEmpty() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.putArray("sections");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueNameIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.remove("name");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueCityIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.remove("city");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("city")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueAddressIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.remove("address");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueSectionsIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.remove("sections");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionNameIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.putNull("name");
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionNameIsBlank() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("name", " ");
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionNameIsTooLong() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("name", "s".repeat(101));
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionRowCountIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.putNull("rowCount");
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].rowCount")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionRowCountIsZero() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("rowCount", 0);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].rowCount")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionRowCountIsNegative() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("rowCount", -1);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].rowCount")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionSeatsPerRowIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.putNull("seatsPerRow");
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].seatsPerRow")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionSeatsPerRowIsZero() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("seatsPerRow", 0);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].seatsPerRow")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionSeatsPerRowIsNegative() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("seatsPerRow", -1);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].seatsPerRow")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.putNull("priceMultiplier");
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierIsZero() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("priceMultiplier", 0);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierIsNegative() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("priceMultiplier", -1);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierIsTooLarge() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("priceMultiplier", 100);
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierHasTooManyDecimalPlaces() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode secondSection = (ObjectNode) objectMapper.readTree("""
                { "name": "Balcony", "rowCount": 5, "seatsPerRow": 12, "priceMultiplier": 1.50 }
                """);
        secondSection.put("priceMultiplier", new BigDecimal("1.001"));
        requestBody.withArray("sections").add(secondSection);

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[1].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionNameIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode section = (ObjectNode) requestBody.path("sections").get(0);
        section.remove("name");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].name")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionRowCountIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode section = (ObjectNode) requestBody.path("sections").get(0);
        section.remove("rowCount");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].rowCount")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionSeatsPerRowIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode section = (ObjectNode) requestBody.path("sections").get(0);
        section.remove("seatsPerRow");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].seatsPerRow")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenSectionPriceMultiplierIsMissing() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        ObjectNode section = (ObjectNode) requestBody.path("sections").get(0);
        section.remove("priceMultiplier");

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].priceMultiplier")))
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].message", everyItem(not(emptyOrNullString()))));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenASectionIsNull() throws Exception {
        ObjectNode requestBody = createVenueRequest();
        requestBody.withArray("sections").addNull();

        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void returnBadRequestWhenVenueJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/venues"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM venue_section", Integer.class));
    }

    @Test
    void savingDetachedVenueCascadesSectionUpdatesAndAdditions() {
        UUID venueId = persistVenueWithSections();
        Venue detachedVenue = transactionTemplate.execute(status -> {
            Venue venue = venueRepository.findById(venueId).orElseThrow();
            venue.getSections().size();
            return venue;
        });
        VenueSection existingSection = detachedVenue.getSections().getFirst();
        existingSection.setName("Updated Stalls");
        existingSection.setPriceMultiplier(new BigDecimal("1.25"));
        detachedVenue.addSection(newSection("Gallery"));

        transactionTemplate.executeWithoutResult(status -> venueRepository.saveAndFlush(detachedVenue));

        assertEquals("Updated Stalls", jdbcTemplate.queryForObject(
                "SELECT name FROM venue_section WHERE id = ?", String.class, existingSection.getId()));
        assertEquals(0, new BigDecimal("1.25").compareTo(jdbcTemplate.queryForObject(
                "SELECT price_multiplier FROM venue_section WHERE id = ?", BigDecimal.class, existingSection.getId())));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue_section WHERE venue_id = ?", Integer.class, venueId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue_section WHERE venue_id = ? AND name = 'Gallery'", Integer.class, venueId));
    }

    @Test
    void removingSectionFromVenueDeletesTheOrphan() {
        UUID venueId = persistVenueWithSections();
        UUID removedSectionId = transactionTemplate.execute(status -> {
            Venue venue = venueRepository.findById(venueId).orElseThrow();
            VenueSection section = venue.getSections().getFirst();
            UUID sectionId = section.getId();
            venue.removeSection(section);
            return sectionId;
        });

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue_section WHERE id = ?", Integer.class, removedSectionId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue_section WHERE venue_id = ?", Integer.class, venueId));
        assertTrue(venueRepository.existsById(venueId));
    }

    @Test
    void deletingVenueCascadesToItsSections() {
        UUID venueId = persistVenueWithSections();

        venueRepository.deleteById(venueId);

        assertFalse(venueRepository.existsById(venueId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM venue_section WHERE venue_id = ?", Integer.class, venueId));
    }

    private UUID persistVenueWithSections() {
        Venue venue = new Venue();
        venue.setName("Lahore Arts Hall");
        venue.setCity("Lahore");
        venue.setAddress("12 Example Road");
        venue.addSection(newSection("Stalls"));
        venue.addSection(newSection("Balcony"));
        return venueRepository.saveAndFlush(venue).getId();
    }

    private VenueSection newSection(String name) {
        VenueSection section = new VenueSection();
        section.setName(name);
        section.setRowCount(10);
        section.setSeatsPerRow(20);
        section.setPriceMultiplier(new BigDecimal("1.00"));
        return section;
    }

    private ObjectNode createVenueRequest() throws Exception {
        String requestBody = """
                {
                    "name": "Lahore Arts Hall",
                    "city": "Lahore",
                    "address": "12 Example Road",
                    "sections": [
                        {
                            "name": "Stalls",
                            "rowCount": 10,
                            "seatsPerRow": 20,
                            "priceMultiplier": 1.00
                        }
                    ]
                }
                """;
        return (ObjectNode) objectMapper.readTree(requestBody);
    }
}
