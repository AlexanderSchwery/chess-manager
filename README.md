# ♞ Chess Manager

[![CI](https://github.com/AlexanderSchwery/chess-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/AlexanderSchwery/chess-manager/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

Eine Webanwendung zur Verwaltung von Schachspielern, Turnieren und Elo-Wertungen – gebaut mit
Spring Boot und einem schlanken Vanilla-JS-Frontend.

Ich spiele selbst Schach und habe das Projekt privat gestartet, um zwei Dinge zu verbinden:
die Organisation von Club- und Freundesturnieren und das Lernen von Backend-Entwicklung mit Java
und Spring Boot. Es ist ein persönliches Lern- und Hobbyprojekt.

---

## Inhalt

- [Funktionen](#funktionen)
- [Schnellstart](#schnellstart)
- [Turniermodi und Regeln](#turniermodi-und-regeln)
- [Elo-Berechnung](#elo-berechnung)
- [REST-API](#rest-api)
- [Architektur](#architektur)
- [Tests](#tests)
- [Bekannte Einschränkungen](#bekannte-einschränkungen)
- [Ideen für später](#ideen-für-später)
- [Lizenz](#lizenz)

---

## Funktionen

**Spieler**
- Spieler mit Name, Start-Elo und optionaler SSB-Nummer (Schweizerischer Schachbund) erfassen
- Suche nach Name oder SSB-Nummer
- Elo manuell korrigieren
- Spielerhistorie: alle Turniere mit Platzierung, Punkten und jeder einzelnen Partie inkl. Elo-Veränderung

**Turniere**
- Drei Modi: **Schweizer System**, **doppeltes Rundenturnier** und **freies Spiel**
- Automatische Paarungen (Schweizer System Runde für Runde, Rundenturnier komplett im Voraus)
- Spielfrei (Bye) bei ungerader Teilnehmerzahl
- Bonus- und Strafpunkte mit Begründung
- Disqualifikation (offene Partien werden kampflos gewertet) und Wiederaufnahme
- Turniere archivieren (schreibgeschützt), wiederherstellen und löschen

**Spielbetrieb und Rangliste**
- Ergebnisse eintragen (1–0, ½–½, 0–1) oder Partien absagen
- Elo wird sofort nach jedem Ergebnis aktualisiert
- Rangliste mit Podest, Punkten, Buchholz-Wertung und aktueller Elo

---

## Schnellstart

**Voraussetzung:** Java 21 (JDK). Maven muss nicht installiert sein, der Maven Wrapper ist dabei.

```bash
git clone https://github.com/AlexanderSchwery/chess-manager.git
cd chess-manager
./mvnw spring-boot:run
```

Unter Windows: `mvnw.cmd spring-boot:run`

Danach erreichbar:

| Was         | URL                                   |
|-------------|---------------------------------------|
| Anwendung   | http://localhost:8080                 |
| Swagger UI  | http://localhost:8080/swagger-ui.html |
| H2-Konsole  | http://localhost:8080/h2-console      |
| Health      | http://localhost:8080/actuator/health |

H2-Konsole: JDBC-URL `jdbc:h2:mem:chessdb`, Benutzer `sa`, Passwort leer.

> **Hinweis:** Die Datenbank läuft im Arbeitsspeicher. Nach einem Neustart sind alle Daten weg.
> Für dauerhafte Daten siehe [Bekannte Einschränkungen](#bekannte-einschränkungen).

### Typischer Ablauf

1. **Spieler** anlegen
2. **Turnier** erstellen und Spieler einschreiben
3. Im **Spielbetrieb** Paarungen generieren und Ergebnisse eintragen
4. In der **Rangliste** den Stand verfolgen

---

## Turniermodi und Regeln

### Schweizer System (`SWISS`)

- Rundenzahl standardmässig `⌈log₂(n)⌉` (z. B. 8 Spieler → 3 Runden), kann beim Erstellen festgelegt werden.
- Die nächste Runde kann erst gepaart werden, wenn alle Partien der aktuellen Runde fertig sind.
- Paarung: Spieler werden nach Punkten, dann Elo sortiert und von oben nach unten gepaart.
  Bereits gespielte Paarungen werden per Backtracking vermieden.
- Farbverteilung: Wer bisher weniger oft Weiss hatte, bekommt Weiss.
- **Spielfrei:** Bei ungerader Anzahl bekommt ein Spieler ohne bisheriges Freilos aus der tiefsten
  Punktgruppe (dort der Elo-schwächste) ein Freilos. Es zählt als Sieg (1 Punkt), ohne Elo-Änderung.
  Orientiert an den FIDE-Grundregeln für Schweizer Systeme.

### Doppeltes Rundenturnier (`DOUBLE_ROUND_ROBIN`)

- Jeder spielt zweimal gegen jeden, einmal mit Weiss und einmal mit Schwarz (Kreismethode / Berger-System).
- Alle Runden werden auf einmal generiert: bei n Spielern `2·(n−1)` Runden (bei ungeradem n `2·n` Runden mit je einem Freilos).
- Solange noch keine Partie gespielt wurde, können Teilnehmer geändert werden; der Spielplan wird dann verworfen.

### Freies Spiel (`FREE_PLAY`)

- Keine automatischen Paarungen und kein Ende – Partien werden manuell angesetzt.
  Praktisch für Clubabende oder Blitzrunden unter Freunden.

### Rangliste

Sortierung:

1. Disqualifizierte Spieler immer am Ende
2. Punkte (inkl. Bonus-/Strafpunkte)
3. **Buchholz** – Summe der Punkte aller gespielten Gegner (Freilose zählen nicht)
4. Aktuelle Elo
5. Name

### Disqualifikation

Ein disqualifizierter Spieler wird nicht mehr gepaart. Seine offenen Partien werden für den Gegner
kampflos gewonnen gewertet, **ohne Elo-Änderung**. Bereits gespielte Partien bleiben bestehen.

---

## Elo-Berechnung

Die Anwendung nutzt das klassische Elo-System:

```
Erwartungswert  E = 1 / (1 + 10^((R_Gegner − R_eigen) / 400))
Neue Elo        R' = R + K · (S − E)
```

- `S` = erzieltes Ergebnis (1, 0.5 oder 0)
- `K = 20` (fest, siehe `EloService.K_FACTOR`)
- Die Wertungsdifferenz wird wie bei der FIDE auf **400 Punkte begrenzt**
- Die Änderung wird auf ganze Punkte gerundet und bei jeder Partie gespeichert,
  damit die Historie nachvollziehbar bleibt

Beispiel: Zwei Spieler mit 1500 – der Sieger bekommt +10, der Verlierer −10. Remis ändert nichts.

---

## REST-API

Alle Endpunkte liegen unter `/api`. Die vollständige, interaktive Doku gibt es in der Swagger UI.

| Methode  | Pfad                                                | Beschreibung                                  |
|----------|-----------------------------------------------------|-----------------------------------------------|
| `GET`    | `/players?search=`                                  | Spieler auflisten / suchen                    |
| `POST`   | `/players`                                          | Spieler anlegen                               |
| `GET`    | `/players/{id}`                                     | Spieler abrufen                               |
| `GET`    | `/players/{id}/history`                             | Turnier- und Partiehistorie                   |
| `PATCH`  | `/players/{id}/elo`                                 | Elo manuell setzen                            |
| `GET`    | `/tournaments`                                      | Turniere auflisten                            |
| `POST`   | `/tournaments`                                      | Turnier anlegen                               |
| `GET`    | `/tournaments/{id}`                                 | Turnier abrufen                               |
| `DELETE` | `/tournaments/{id}`                                 | Turnier löschen                               |
| `PATCH`  | `/tournaments/{id}/archive` · `/unarchive`          | Archivieren / wiederherstellen                |
| `POST`   | `/tournaments/{id}/players/{playerId}`              | Spieler einschreiben                          |
| `DELETE` | `/tournaments/{id}/players/{playerId}`              | Spieler austragen (nur ohne gespielte Partie) |
| `POST`   | `/tournaments/{id}/players/{playerId}/points`       | Bonus-/Strafpunkte                            |
| `POST`   | `/tournaments/{id}/players/{playerId}/disqualify`   | Disqualifizieren                              |
| `DELETE` | `/tournaments/{id}/players/{playerId}/disqualify`   | Wiederaufnehmen                               |
| `POST`   | `/tournaments/{id}/generate-round-robin`            | Rundenturnier-Spielplan erzeugen              |
| `POST`   | `/tournaments/{id}/generate-next-round`             | Nächste Schweizer Runde paaren                |
| `GET`    | `/tournaments/{id}/standings`                       | Rangliste                                     |
| `GET`    | `/tournaments/{id}/matches?round=&status=`          | Partien (optional gefiltert)                  |
| `POST`   | `/tournaments/{id}/matches`                         | Partie manuell ansetzen                       |
| `POST`   | `/matches/{id}/result`                              | Ergebnis eintragen                            |
| `PATCH`  | `/matches/{id}/cancel`                              | Partie absagen                                |

### Beispiel mit curl

```bash
# Spieler anlegen
curl -X POST localhost:8080/api/players \
  -H 'Content-Type: application/json' \
  -d '{"name":"Anna","currentElo":1650,"ssbNumber":"12345"}'

# Schweizer Turnier anlegen
curl -X POST localhost:8080/api/tournaments \
  -H 'Content-Type: application/json' \
  -d '{"name":"Clubmeisterschaft","type":"SWISS","totalRounds":5}'

# Ergebnis eintragen (WHITE_WINS, BLACK_WINS oder DRAW)
curl -X POST localhost:8080/api/matches/1/result \
  -H 'Content-Type: application/json' \
  -d '{"result":"DRAW"}'
```

### Fehlerantworten

Fehler kommen immer im selben Format zurück:

```json
{ "status": 409, "error": "Conflict", "message": "Finish all matches of the current round first", "fieldErrors": null }
```

| Status | Bedeutung                                                          |
|--------|--------------------------------------------------------------------|
| `400`  | Ungültige Eingabe (Validierung, falsches JSON, unbekannter Wert)   |
| `404`  | Spieler, Turnier oder Partie nicht gefunden                        |
| `409`  | Verstoss gegen eine Turnierregel (z. B. Runde noch nicht fertig)   |

---

## Architektur

Klassischer Schichtenaufbau:

```
Browser (index.html + app.js)
        │  fetch / JSON
        ▼
Controller   ── REST-Endpunkte, Validierung der Eingaben (DTOs mit Bean Validation)
        ▼
Service      ── Geschäftslogik: Paarungen, Elo, Rangliste, Turnierregeln
        ▼
Repository   ── Spring Data JPA
        ▼
H2 (In-Memory)
```

### Technologien

| Bereich   | Technologie                                                       |
|-----------|-------------------------------------------------------------------|
| Backend   | Java 21, Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)  |
| Datenbank | H2 In-Memory, Hibernate                                           |
| API-Doku  | springdoc-openapi (Swagger UI)                                    |
| Frontend  | HTML, Vanilla JavaScript, Tailwind CSS (CDN)                      |
| Build     | Maven (Wrapper), Lombok                                           |
| Tests     | JUnit 5, Spring Boot Test, MockMvc                                |
| CI        | GitHub Actions                                                    |

### Projektstruktur

```
src/main/java/ch/ims/chess/chess_manager/
├── controller/   REST-Controller (Player, Tournament, Match)
├── service/      Geschäftslogik (EloService, PairingService, TournamentService, …)
├── model/        JPA-Entities und Enums (Player, Tournament, Match, …)
├── dto/          Request-/Response-Objekte (Java Records)
├── repository/   Spring-Data-Repositories
├── exception/    Eigene Exceptions und globaler Fehler-Handler
└── config/       CORS-Konfiguration
src/main/resources/
├── static/       Frontend (index.html, app.js, styles.css)
└── application.properties
```

### Datenmodell

- **Player** – Name, SSB-Nummer, aktuelle Elo
- **Tournament** – Modus, Rundenzahl, Status (`CREATED` → `IN_PROGRESS` → `COMPLETED`), Teilnehmer, Disqualifizierte, archiviert
- **Match** – Runde, Weiss, Schwarz (leer bei Freilos), Ergebnis, Status, Elo vorher und Elo-Änderung beider Spieler
- **PointAdjustment** – Bonus-/Strafpunkte eines Spielers in einem Turnier mit Begründung

---

## Tests

```bash
./mvnw test
```

- `EloAndPairingTests` – Unit-Tests für Elo-Berechnung, Rundenturnier-Generierung und Schweizer Paarung
- `TournamentApiTests` – Integrationstests über die REST-API (Validierung, Elo-Update, Freilos, Disqualifikation, Archiv)
- `ChessManagerApplicationTests` – prüft, ob der Spring-Kontext startet

Bei jedem Push laufen die Tests automatisch über GitHub Actions (`.github/workflows/ci.yml`).

---

## Bekannte Einschränkungen

- **Keine dauerhafte Speicherung:** H2 läuft im Speicher. Für persistente Daten kann man in
  `application.properties` z. B. `spring.datasource.url=jdbc:h2:file:./data/chessdb` setzen.
- **Keine Anmeldung:** Jeder mit Zugriff kann alles ändern. Die H2-Konsole ist aktiv.
  Die Anwendung ist für den lokalen Gebrauch gedacht, nicht für den Betrieb im Internet.
- **Vereinfachte Schweizer Paarung:** Die Paarung orientiert sich an den FIDE-Grundregeln, ist aber
  kein vollständiges Dutch-System (keine Floater-Regeln, keine strikten Farbregeln).
- **Fester K-Faktor:** Die FIDE verwendet je nach Spieler K = 40/20/10; hier gilt immer K = 20.
- Das Frontend lädt Tailwind über das CDN und braucht deshalb eine Internetverbindung.

---

## Ideen für später

- [ ] Persistente Datenbank (H2-Datei oder PostgreSQL) mit Flyway-Migrationen
- [ ] Weitere Wertungen: Sonneborn-Berger, Buchholz-Cut-1, direkter Vergleich
- [ ] Variabler K-Faktor nach FIDE-Regeln
- [ ] Export der Rangliste als PDF/CSV, Import von Spielern (z. B. SSB-Elo-Liste)
- [ ] Login mit Rollen (Turnierleiter / Zuschauer)
- [ ] Docker-Image für einfaches Deployment

---

## Lizenz

[MIT](LICENSE) – frei nutzbar, auch zum Lernen und Weiterentwickeln.
