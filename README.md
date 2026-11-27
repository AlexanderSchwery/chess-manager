# Chess Manager

Spring Boot Anwendung zur Verwaltung von Schachspielern, Turnieren und Partien (Elo-Berechnung, Pairing, Rangliste).

## Tech
- Java, Spring Boot, Maven
- H2 In-Memory Datenbank
- Statisches Frontend (`src/main/resources/static`)

## Starten
```
./mvnw spring-boot:run
```
Windows: `mvnw.cmd spring-boot:run`

- App: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:chessdb`, User `sa`)

## Tests
```
./mvnw test
```
