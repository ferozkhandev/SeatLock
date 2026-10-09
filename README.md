# SeatLock
This is an application that will keep track of seats reservation for an event. There can be many events that can get 
placed at various venues. At every venue there can be many seats or sections.
This is just a simple application that I made for my own learning. And its purpose is to learn and showcase my skills.
It is to be used as a portfolio.
This application will be backend only and will be used via Swagger UI. 

## Architecture
This application will have 4 services:
1. API Gateway
2. Catalog Service
3. Booking Service
4. Notification Service

## Tech Stack
1. Java 25
2. Spring Boot 4
3. Kafka
4. Redis
5. MinIO
6. Flyway
7. Micrometer
8. Prometheus
9. Docker
10. Kubernetes

## Getting Started
Prerequisites: JDK 25 and Docker.

```bash
cp .env.dev.example .env.dev
docker compose -f infra/docker-compose.yaml up -d --build
curl localhost:8181/actuator/health
```

To run catalog-service from the IDE, make a copy of `.env.dev.example` as `.env.local`, adjust your values in it and load `.env.local` into the run configuration.

