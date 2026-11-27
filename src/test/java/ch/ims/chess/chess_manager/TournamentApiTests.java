package ch.ims.chess.chess_manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests through the REST API against the in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TournamentApiTests {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired
    private MockMvc mvc;

    private long createPlayer(String name, int elo) throws Exception {
        return firstId(mvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currentElo\":" + elo + "}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createTournament(String type) throws Exception {
        return firstId(mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private static long firstId(MvcResult r) throws Exception {
        Matcher m = ID.matcher(r.getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("no id in response");
        return Long.parseLong(m.group(1));
    }

    private List<Long> scheduledMatchIds(long tournamentId) throws Exception {
        String body = mvc.perform(get("/api/tournaments/" + tournamentId + "/matches").param("status", "SCHEDULED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // every match object starts with its own id, followed by tournamentId
        Matcher m = Pattern.compile("\"id\":(\\d+),\"tournamentId\"").matcher(body);
        List<Long> ids = new ArrayList<>();
        while (m.find()) ids.add(Long.parseLong(m.group(1)));
        return ids;
    }

    private void record(long matchId, String result) throws Exception {
        mvc.perform(post("/api/matches/" + matchId + "/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"" + result + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPlayerIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"currentElo\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.currentElo").exists());
    }

    @Test
    void unknownTournamentGives404() throws Exception {
        mvc.perform(get("/api/tournaments/999")).andExpect(status().isNotFound());
    }

    @Test
    void resultUpdatesEloAndStandings() throws Exception {
        long a = createPlayer("Anna", 1500);
        long b = createPlayer("Ben", 1500);
        long t = createTournament("SWISS");
        mvc.perform(post("/api/tournaments/" + t + "/players/" + a)).andExpect(status().isOk());
        mvc.perform(post("/api/tournaments/" + t + "/players/" + b)).andExpect(status().isOk());
        mvc.perform(post("/api/tournaments/" + t + "/generate-next-round")).andExpect(status().isOk());

        List<Long> open = scheduledMatchIds(t);
        record(open.get(0), "WHITE_WINS");

        // a second result on the same match is refused
        mvc.perform(post("/api/matches/" + open.get(0) + "/result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"DRAW\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/tournaments/" + t + "/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(1.0))
                .andExpect(jsonPath("$[0].elo").value(1510))
                .andExpect(jsonPath("$[1].points").value(0.0))
                .andExpect(jsonPath("$[1].elo").value(1490));

        // 2 players -> 1 Swiss round -> tournament is finished
        mvc.perform(get("/api/tournaments/" + t)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void swissWithOddCountGivesByeAndUsesBuchholzAsTieBreak() throws Exception {
        long t = createTournament("SWISS");
        for (int i = 0; i < 5; i++) {
            long p = createPlayer("P" + i, 1800 - i * 100);
            mvc.perform(post("/api/tournaments/" + t + "/players/" + p)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/tournaments/" + t + "/generate-next-round"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRounds").value(3)); // ceil(log2(5))

        List<Long> open = scheduledMatchIds(t);
        org.junit.jupiter.api.Assertions.assertEquals(2, open.size(), "5 players: 2 games + 1 bye");

        // next round cannot be paired while games are open
        mvc.perform(post("/api/tournaments/" + t + "/generate-next-round")).andExpect(status().isConflict());

        for (long id : open) record(id, "WHITE_WINS");
        mvc.perform(get("/api/tournaments/" + t + "/standings"))
                .andExpect(jsonPath("$[0].points").value(1.0))
                .andExpect(jsonPath("$[0].buchholz").exists())
                .andExpect(jsonPath("$[?(@.byes == 1)]").isNotEmpty());
    }

    @Test
    void doubleRoundRobinCreatesAllGames() throws Exception {
        long t = createTournament("DOUBLE_ROUND_ROBIN");
        for (int i = 0; i < 4; i++) {
            long p = createPlayer("P" + i, 1500);
            mvc.perform(post("/api/tournaments/" + t + "/players/" + p)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/tournaments/" + t + "/generate-round-robin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRounds").value(6));
        org.junit.jupiter.api.Assertions.assertEquals(12, scheduledMatchIds(t).size());
    }

    @Test
    void disqualificationForfeitsOpenGamesWithoutEloChange() throws Exception {
        long a = createPlayer("Anna", 1500);
        long b = createPlayer("Ben", 1500);
        long t = createTournament("DOUBLE_ROUND_ROBIN");
        mvc.perform(post("/api/tournaments/" + t + "/players/" + a));
        mvc.perform(post("/api/tournaments/" + t + "/players/" + b));
        mvc.perform(post("/api/tournaments/" + t + "/generate-round-robin")).andExpect(status().isOk());

        mvc.perform(post("/api/tournaments/" + t + "/players/" + b + "/disqualify")).andExpect(status().isOk());

        mvc.perform(get("/api/tournaments/" + t + "/standings"))
                .andExpect(jsonPath("$[0].name").value("Anna"))
                .andExpect(jsonPath("$[0].points").value(2.0))
                .andExpect(jsonPath("$[0].elo").value(1500))
                .andExpect(jsonPath("$[1].disqualified").value(true));
        mvc.perform(get("/api/tournaments/" + t)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void archivedTournamentIsReadOnly() throws Exception {
        long t = createTournament("FREE_PLAY");
        long p = createPlayer("Anna", 1500);
        mvc.perform(patch("/api/tournaments/" + t + "/archive")).andExpect(status().isOk());
        mvc.perform(post("/api/tournaments/" + t + "/players/" + p)).andExpect(status().isConflict());
    }
}
