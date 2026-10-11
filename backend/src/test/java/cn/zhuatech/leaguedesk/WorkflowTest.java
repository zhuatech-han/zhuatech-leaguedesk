// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真实HTTP、SpringSecurity、Flyway与JPA验证完整办赛和越权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class WorkflowTest {
  static final String PW = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:league;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("leaguedesk.admin-password", () -> PW);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, referee, participant;
  long event, court, refereeId;
  String suffix;
  List<Long> accountIds;
  JsonNode tournament;

  JsonNode call(
      MockHttpSession session, String method, String url, Map<String, Object> b, int expected)
      throws Exception {
    MockHttpServletRequestBuilder r =
        switch (method) {
          case "POST" -> post(url);
          case "PUT" -> put(url);
          case "DELETE" -> delete(url);
          default -> get(url);
        };
    if (session != null) r.session(session);
    if (!method.equals("GET"))
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    var res = mvc.perform(r).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), url + " " + res.getContentAsString());
    if (res.getContentType() != null && res.getContentType().startsWith("text/csv"))
      return json.valueToTree(Map.of("csv", res.getContentAsString()));
    return json.readTree(res.getContentAsString());
  }

  MockHttpSession login(String user) throws Exception {
    var s = new MockHttpSession();
    call(s, "POST", "/api/auth/login", Map.of("username", user, "password", PW), 200);
    return s;
  }

  JsonNode detail() throws Exception {
    return call(admin, "GET", "/api/events/" + event, Map.of(), 200);
  }

  JsonNode act(MockHttpSession actor, String action, Map<String, Object> extra, int expected)
      throws Exception {
    var b = new LinkedHashMap<>(extra);
    b.put("version", detail().get("tournament").get("version").asLong());
    b.put("requestId", UUID.randomUUID().toString());
    return call(actor, "POST", "/api/events/" + event + "/commands/" + action, b, expected);
  }

  long account(String user, int role) throws Exception {
    return call(
            admin,
            "POST",
            "/api/admin/users",
            Map.of(
                "username",
                user,
                "displayName",
                "TEST " + user,
                "roleId",
                role,
                "departmentId",
                1,
                "enabled",
                true,
                "password",
                PW),
            200)
        .get("id")
        .asLong();
  }

  @BeforeEach
  void setup() throws Exception {
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    refereeId = account("r" + suffix, 3);
    referee = login("r" + suffix);
    accountIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) accountIds.add(account("p" + i + suffix, 4));
    participant = login("p0" + suffix);
    var season =
        call(
            admin,
            "POST",
            "/api/masters/seasons",
            Map.of("departmentId", 1, "name", "TEST season " + suffix, "enabled", true),
            200);
    court =
        call(
                admin,
                "POST",
                "/api/masters/courts",
                Map.of("departmentId", 1, "name", "TEST court " + suffix, "enabled", true),
                200)
            .get("id")
            .asLong();
    var b =
        new LinkedHashMap<String, Object>(
            Map.of(
                "seasonId",
                season.get("id").asLong(),
                "name",
                "TEST tournament " + suffix,
                "category",
                "RACKET",
                "format",
                "GROUP_KO",
                "groups",
                1,
                "advance",
                2,
                "bestOf",
                3,
                "capacity",
                5,
                "startsAt",
                Instant.now().minusSeconds(100000).toString(),
                "endsAt",
                Instant.now().plusSeconds(100000).toString()));
    b.put("description", "TEST only");
    tournament = call(admin, "POST", "/api/events", b, 200);
    event = tournament.get("id").asLong();
  }

  void prepare(int n) throws Exception {
    act(admin, "OPEN", Map.of(), 200);
    for (int i = 0; i < n; i++) {
      var d =
          call(
              admin,
              "POST",
              "/api/events/" + event + "/entries",
              Map.of(
                  "accountId",
                  accountIds.get(i),
                  "name",
                  "TEST team " + i,
                  "note",
                  "PRIVATE TEST NOTE",
                  "publicConsent",
                  true),
              200);
      var e = entryFor(accountIds.get(i));
      act(
          admin,
          "REVIEW",
          Map.of(
              "entryId",
              e.get("id").asLong(),
              "entryVersion",
              e.get("version").asLong(),
              "approved",
              true,
              "seed",
              i + 1),
          200);
      var row = entryFor(accountIds.get(i));
      act(
          admin,
          "CHECKIN",
          Map.of(
              "entryId",
              row.get("id").asLong(),
              "entryVersion",
              row.get("version").asLong(),
              "checkedIn",
              true),
          200);
    }
  }

  void enter(int n) throws Exception {
    prepare(n);
    act(admin, "DRAW", Map.of(), 200);
  }

  JsonNode entryFor(long accountId) throws Exception {
    for (var e : detail().get("entries")) if (e.get("accountId").asLong() == accountId) return e;
    throw new AssertionError("Missing entry");
  }

  Map<String, Object> gameBody(JsonNode g) {
    return new LinkedHashMap<>(
        Map.of("gameId", g.get("id").asLong(), "gameVersion", g.get("version").asLong()));
  }

  void play(JsonNode g, int slot) throws Exception {
    var b = gameBody(g);
    var time = Instant.now().minusSeconds(90000 - slot * 600);
    b.putAll(
        Map.of(
            "courtId",
            court,
            "refereeId",
            refereeId,
            "startsAt",
            time.toString(),
            "endsAt",
            time.plusSeconds(300).toString()));
    act(admin, "SCHEDULE", b, 200);
    g = find(g.get("id").asLong());
    act(referee, "START", gameBody(g), 200);
    g = find(g.get("id").asLong());
    b = gameBody(g);
    b.putAll(Map.of("homeScore", 2, "awayScore", 0, "resultType", "PLAYED"));
    act(referee, "RESULT", b, 200);
  }

  JsonNode find(long id) throws Exception {
    for (var g : detail().get("games")) if (g.get("id").asLong() == id) return g;
    throw new AssertionError();
  }

  @Test
  void fullOddGroupToFinalAndSeasonPoints() throws Exception {
    enter(5);
    assertEquals(10, detail().get("games").size());
    int i = 0;
    for (var g : detail().get("games")) play(g, i++);
    act(admin, "ADVANCE", Map.of(), 200);
    for (var g : detail().get("games"))
      if (g.get("stage").asText().equals("KNOCKOUT")) play(g, i++);
    act(admin, "FINISH", Map.of(), 200);
    assertEquals("FINISHED", detail().get("tournament").get("status").asText());
    var finalGame =
        java.util.stream.StreamSupport.stream(detail().get("games").spliterator(), false)
            .filter(g -> g.get("stage").asText().equals("KNOCKOUT"))
            .findFirst()
            .orElseThrow();
    var frozen = gameBody(finalGame);
    frozen.putAll(
        Map.of("homeScore", 0, "awayScore", 2, "resultType", "PLAYED", "reason", "TEST frozen"));
    act(admin, "CORRECT", frozen, 409);
    var reports = call(admin, "GET", "/api/reports", Map.of(), 200);
    assertTrue(reports.get("finished").asInt() > 0);
    assertTrue(
        call(admin, "GET", "/api/events/" + event + "/report.csv", Map.of(), 200)
            .get("csv")
            .asText()
            .contains("GROUP"));
  }

  @Test
  void publicViewsExcludePrivateDataAndUnpublishRevokes() throws Exception {
    act(admin, "OPEN", Map.of(), 200);
    call(
        participant,
        "POST",
        "/api/events/" + event + "/entries",
        Map.of("name", "TEST public label", "note", "SECRET TEST NOTE", "publicConsent", true),
        200);
    act(admin, "PUBLISH", Map.of("published", true), 200);
    var p = call(null, "GET", "/api/public/events/" + event, Map.of(), 200);
    assertFalse(p.toString().contains("SECRET TEST NOTE"));
    assertFalse(p.toString().contains("accountId"));
    assertFalse(p.toString().contains("ownerId"));
    assertEquals(0, p.get("entries").size());
    act(admin, "PUBLISH", Map.of("published", false), 200);
    call(null, "GET", "/api/public/events/" + event, Map.of(), 404);
  }

  @Test
  void participantCannotManageOtherEntryOrAdmin() throws Exception {
    act(admin, "OPEN", Map.of(), 200);
    call(
        participant,
        "POST",
        "/api/events/" + event + "/entries",
        Map.of(
            "accountId", accountIds.get(1), "name", "TEST mine", "note", "", "publicConsent", true),
        200);
    assertEquals(accountIds.getFirst(), detail().get("entries").get(0).get("accountId").asLong());
    act(participant, "DRAW", Map.of(), 403);
    call(participant, "GET", "/api/admin/users", Map.of(), 403);
    call(null, "GET", "/api/events/" + event, Map.of(), 401);
  }

  @Test
  void duplicateAndConsentRejected() throws Exception {
    act(admin, "OPEN", Map.of(), 200);
    var b = Map.<String, Object>of("name", "TEST mine", "note", "", "publicConsent", false);
    call(participant, "POST", "/api/events/" + event + "/entries", b, 409);
    b = Map.of("name", "TEST mine", "note", "", "publicConsent", true);
    call(participant, "POST", "/api/events/" + event + "/entries", b, 200);
    call(participant, "POST", "/api/events/" + event + "/entries", b, 409);
  }

  @Test
  void commandReplayDoesNotDuplicateDraw() throws Exception {
    act(admin, "OPEN", Map.of(), 200);
    var b =
        Map.<String, Object>of(
            "version",
            detail().get("tournament").get("version").asLong(),
            "requestId",
            UUID.randomUUID().toString(),
            "published",
            true);
    call(admin, "POST", "/api/events/" + event + "/commands/PUBLISH", b, 200);
    call(admin, "POST", "/api/events/" + event + "/commands/PUBLISH", b, 200);
    var changed = new LinkedHashMap<>(b);
    changed.put("published", false);
    call(admin, "POST", "/api/events/" + event + "/commands/PUBLISH", changed, 409);
  }

  @Test
  void prematureAdvanceAndForeignRefereeRejected() throws Exception {
    enter(3);
    act(admin, "ADVANCE", Map.of(), 409);
    var g = detail().get("games").get(0);
    act(admin, "RESULT", gameBody(g), 409);
  }

  @Test
  void lastAdministratorProtected() throws Exception {
    var a = call(admin, "GET", "/api/admin/users", Map.of(), 200).get(0);
    var b = new LinkedHashMap<String, Object>();
    a.properties().forEach(e -> b.put(e.getKey(), json.convertValue(e.getValue(), Object.class)));
    b.put("enabled", false);
    b.put("password", "");
    call(admin, "PUT", "/api/admin/users/" + a.get("id").asLong(), b, 409);
  }

  Map<String, Object> body(JsonNode node) {
    return json.convertValue(node, Map.class);
  }

  void direct() throws Exception {
    var b = new LinkedHashMap<>(body(tournament));
    b.put("format", "KNOCKOUT");
    tournament = call(admin, "PUT", "/api/events/" + event, b, 200);
  }

  JsonNode schedule(JsonNode game, Instant start, long courtId, long official, int expected)
      throws Exception {
    var b = gameBody(game);
    b.putAll(
        Map.of(
            "courtId",
            courtId,
            "refereeId",
            official,
            "startsAt",
            start.toString(),
            "endsAt",
            start.plusSeconds(300).toString()));
    return act(admin, "SCHEDULE", b, expected);
  }

  @Test
  void oddKnockoutByesCompleteWithoutPhantomPlayers() throws Exception {
    direct();
    enter(5);
    assertEquals(7, detail().get("games").size());
    assertEquals(
        3,
        java.util.stream.StreamSupport.stream(detail().get("games").spliterator(), false)
            .filter(g -> g.get("status").asText().equals("BYE"))
            .count());
    int slot = 0;
    for (int round = 1; round <= 3; round++)
      for (var g : detail().get("games"))
        if (g.get("roundNo").asInt() == round && !g.get("status").asText().equals("BYE"))
          play(g, slot++);
    act(admin, "FINISH", Map.of(), 200);
    assertEquals("FINISHED", detail().get("tournament").get("status").asText());
  }

  @Test
  void duplicateSeedAndUncheckedEntriesCannotDraw() throws Exception {
    prepare(2);
    var e = entryFor(accountIds.get(1));
    act(
        admin,
        "REVIEW",
        Map.of(
            "entryId",
            e.get("id").asLong(),
            "entryVersion",
            e.get("version").asLong(),
            "approved",
            true,
            "seed",
            1),
        200);
    act(admin, "DRAW", Map.of(), 409);
    e = entryFor(accountIds.get(1));
    act(
        admin,
        "CHECKIN",
        Map.of(
            "entryId",
            e.get("id").asLong(),
            "entryVersion",
            e.get("version").asLong(),
            "checkedIn",
            true),
        200);
    assertEquals("SEED_DUPLICATE", act(admin, "DRAW", Map.of(), 409).get("code").asText());
  }

  @Test
  void schedulingChecksCourtRefereeAndParticipantIndependently() throws Exception {
    enter(3);
    var gs = detail().get("games");
    var first = gs.get(0);
    var next = gs.get(1);
    Instant time = Instant.now().minusSeconds(80000);
    schedule(first, time, court, refereeId, 200);
    assertEquals(
        "SCHEDULE_CONFLICT", schedule(next, time, court, refereeId, 409).get("code").asText());
    long other =
        call(
                admin,
                "POST",
                "/api/masters/courts",
                Map.of("departmentId", 1, "name", "TEST second " + suffix, "enabled", true),
                200)
            .get("id")
            .asLong();
    assertEquals(
        "SCHEDULE_CONFLICT", schedule(next, time, other, refereeId, 409).get("code").asText());
    long official = account("other" + suffix, 3);
    assertEquals(
        "SCHEDULE_CONFLICT", schedule(next, time, other, official, 409).get("code").asText());
    schedule(next, time.plusSeconds(300), other, official, 200);
  }

  @Test
  void invalidScoresPreserveLiveMatchAndCancelIsBlocked() throws Exception {
    enter(2);
    var g = detail().get("games").get(0);
    schedule(g, Instant.now().minusSeconds(50000), court, refereeId, 200);
    g = find(g.get("id").asLong());
    act(referee, "START", gameBody(g), 200);
    g = find(g.get("id").asLong());
    var b = gameBody(g);
    b.putAll(Map.of("homeScore", 2, "awayScore", 2, "resultType", "PLAYED"));
    assertEquals("SCORE_INVALID", act(referee, "RESULT", b, 409).get("code").asText());
    assertEquals("LIVE", find(g.get("id").asLong()).get("status").asText());
    act(admin, "CANCEL", Map.of("reason", "TEST cancellation"), 409);
  }

  @Test
  void correctionResetsUnstartedDownstreamAndLocksStartedDownstream() throws Exception {
    direct();
    enter(4);
    var first = detail().get("games").get(0);
    play(first, 0);
    play(detail().get("games").get(1), 1);
    var finalGame = detail().get("games").get(2);
    schedule(finalGame, Instant.now().minusSeconds(50000), court, refereeId, 200);
    var completed = find(first.get("id").asLong());
    var b = gameBody(completed);
    b.putAll(
        Map.of(
            "homeScore", 0, "awayScore", 2, "resultType", "PLAYED", "reason", "TEST wrong winner"));
    act(admin, "CORRECT", b, 200);
    var changed = find(finalGame.get("id").asLong());
    assertEquals("WAITING", changed.get("status").asText());
    assertTrue(changed.get("courtId").isNull());
    assertEquals(completed.get("awayId").asLong(), changed.get("homeId").asLong());
    schedule(changed, Instant.now().minusSeconds(40000), court, refereeId, 200);
    changed = find(changed.get("id").asLong());
    act(referee, "START", gameBody(changed), 200);
    b = gameBody(find(first.get("id").asLong()));
    b.putAll(
        Map.of(
            "homeScore",
            2,
            "awayScore",
            0,
            "resultType",
            "PLAYED",
            "reason",
            "TEST correction after final start"));
    assertEquals("DOWNSTREAM_LOCKED", act(admin, "CORRECT", b, 409).get("code").asText());
  }

  @Test
  void groupResultsCannotChangeAfterQualifiersGenerated() throws Exception {
    enter(2);
    var g = detail().get("games").get(0);
    play(g, 0);
    act(admin, "ADVANCE", Map.of(), 200);
    var b = gameBody(find(g.get("id").asLong()));
    b.putAll(
        Map.of(
            "homeScore", 0, "awayScore", 2, "resultType", "PLAYED", "reason", "TEST locked group"));
    assertEquals("DOWNSTREAM_LOCKED", act(admin, "CORRECT", b, 409).get("code").asText());
  }

  @Test
  void staleVersionAndPrematureFinishDoNotAdvance() throws Exception {
    var b = Map.<String, Object>of("version", 99999, "requestId", UUID.randomUUID().toString());
    act(admin, "OPEN", Map.of(), 200);
    assertEquals(
        "VERSION_CONFLICT",
        call(admin, "POST", "/api/events/" + event + "/commands/PUBLISH", b, 409)
            .get("code")
            .asText());
    act(admin, "FINISH", Map.of(), 409);
  }

  @Test
  void csrfRequiredAndAccountDisableRevokesExistingSession() throws Exception {
    var res =
        mvc.perform(
                post("/api/events").session(admin).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse();
    assertEquals(403, res.getStatus());
    var a = call(admin, "GET", "/api/admin/users", Map.of(), 200);
    for (var row : a)
      if (row.get("id").asLong() == accountIds.getFirst()) {
        var b = body(row);
        b.put("enabled", false);
        b.put("password", "");
        call(admin, "PUT", "/api/admin/users/" + accountIds.getFirst(), b, 200);
      }
    call(participant, "GET", "/api/auth/me", Map.of(), 401);
  }

  @Test
  void withdrawalAllowsReentryWithoutDeletingHistory() throws Exception {
    prepare(2);
    var e = entryFor(accountIds.getFirst());
    call(
        participant,
        "POST",
        "/api/events/" + event + "/entries/" + e.get("id").asLong() + "/withdraw",
        Map.of("version", e.get("version").asLong()),
        200);
    call(
        participant,
        "POST",
        "/api/events/" + event + "/entries",
        Map.of("name", "TEST reentry", "publicConsent", true),
        200);
    var again = entryFor(accountIds.getFirst());
    assertEquals(e.get("id").asLong(), again.get("id").asLong());
    assertEquals("PENDING", again.get("status").asText());
    assertFalse(again.get("checkedIn").asBoolean());
  }

  @Test
  void crossOrganizationStaffCannotReadOrListOtherEvents() throws Exception {
    long org =
        call(
                admin,
                "POST",
                "/api/admin/departments",
                Map.of("name", "TEST other org " + suffix, "zone", "UTC", "enabled", true),
                200)
            .get("id")
            .asLong();
    long seasonId =
        call(
                admin,
                "POST",
                "/api/masters/seasons",
                Map.of("departmentId", org, "name", "TEST foreign " + suffix, "enabled", true),
                200)
            .get("id")
            .asLong();
    var b = new LinkedHashMap<>(body(tournament));
    b.put("seasonId", seasonId);
    b.remove("id");
    long foreign = call(admin, "POST", "/api/events", b, 200).get("id").asLong();
    account("staff" + suffix, 2);
    var staffSession = login("staff" + suffix);
    call(staffSession, "GET", "/api/events/" + foreign, Map.of(), 403);
    var list = call(staffSession, "GET", "/api/events?search=" + suffix, Map.of(), 200);
    assertEquals(1, list.get("total").asInt());
    call(participant, "GET", "/api/events/" + foreign, Map.of(), 403);
  }

  @Test
  void participantRoleCannotGainAdministrativePermissions() throws Exception {
    call(
        admin,
        "POST",
        "/api/admin/roles",
        Map.of(
            "name",
            "TEST unsafe " + suffix,
            "scope",
            "PARTICIPANT",
            "permissions",
            List.of("events", "register", "users")),
        409);
  }

  @Test
  void impossibleGroupConfigurationRejectedBeforeOpening() throws Exception {
    var b = new LinkedHashMap<>(body(tournament));
    b.put("groups", 4);
    b.put("advance", 2);
    b.put("capacity", 5);
    call(admin, "PUT", "/api/events/" + event, b, 409);
  }
}
