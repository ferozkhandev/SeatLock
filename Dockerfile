# Shared image template for every SeatLock service.
# Build: docker build --build-arg SERVICE=catalog-service -t seatlock/catalog-service .

FROM eclipse-temurin:25-jdk AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN ./gradlew :${SERVICE}:bootJar --no-daemon
RUN java -Djarmode=tools -jar ${SERVICE}/build/libs/*SNAPSHOT.jar extract --layers --destination extracted


FROM eclipse-temurin:25-jre
LABEL authors="feroz"
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/*.jar ./app.jar
USER app
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
