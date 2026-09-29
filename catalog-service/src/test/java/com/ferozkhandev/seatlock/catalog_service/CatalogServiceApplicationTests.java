package com.ferozkhandev.seatlock.catalog_service;

import org.junit.jupiter.api.Test;
 import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class CatalogServiceApplicationTests {

	@Container
	@ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

	@Test
	void contextLoads() {
	}

}
