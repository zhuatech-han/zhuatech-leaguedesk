// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 持久化办赛闭环；实时权限、组织范围、版本和幂等写入保护赛程与晋级。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class LeagueService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final ObjectMapper json;

  public LeagueService(
      Store db, AccessService access, AdminService admin, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.json = json;
  }

  boolean staff() {
    return Set.of("ALL", "DEPARTMENT").contains(access.role().scope);
  }

  boolean visible(Tournament t) {
    var scope = access.role().scope;
    if (scope.equals("ALL")) return true;
    if (!Objects.equals(t.departmentId, access.current().departmentId)) return false;
    return switch (scope) {
      case "DEPARTMENT" -> true;
      case "PARTICIPANT" ->
          t.status.equals("OPEN")
              || !db.query(
                      Entry.class,
                      "from Entry where tournamentId=?1 and accountId=?2",
                      t.id,
                      access.current().id)
                  .isEmpty();
      case "ASSIGNED" ->
          !db.query(
                  Game.class,
                  "from Game where tournamentId=?1 and refereeId=?2",
                  t.id,
                  access.current().id)
              .isEmpty();
      default -> false;
    };
  }

  Tournament event(Long id) {
    access.require("events");
    var t = db.get(Tournament.class, id);
    if (!visible(t)) throw new Problem(403, "OUT_OF_SCOPE");
    return t;
  }

  Tournament manage(Long id) {
    var t = event(id);
    access.require("manage");
    if (!staff()) throw new Problem(403, "OUT_OF_SCOPE");
    return t;
  }

  void org(Long id, String permission) {
    access.require(permission);
    if (!staff() || !access.department(id)) throw new Problem(403, "OUT_OF_SCOPE");
    Rules.check(db.get(Department.class, id).enabled, "DEPARTMENT_DISABLED");
  }

  void version(long current, Object value) {
    Rules.check(current == Rules.id(value), "VERSION_CONFLICT");
  }

  List<Entry> entries(Long id) {
    return db.query(Entry.class, "from Entry where tournamentId=?1 order by seed,id", id);
  }

  List<Game> games(Long id) {
    return db.query(
        Game.class, "from Game where tournamentId=?1 order by stage,roundNo,position,id", id);
  }

  void history(Tournament t, String action, String details) {
    var e = new LeagueEvent();
    e.tournamentId = t.id;
    e.actorId = access.current().id;
    e.action = action;
    e.details = details;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, t.id, t.departmentId);
  }

  Map<String, Object> map(Object... v) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < v.length; i += 2) result.put(v[i].toString(), v[i + 1]);
    return result;
  }

  /** 必要目录按岗位范围过滤；裁判不接触报名联系人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("events");
    var dirs =
        db.all(Department.class).stream()
            .filter(x -> x.enabled && access.department(x.id))
            .toList();
    var orgIds = dirs.stream().map(x -> x.id).toList();
    var courts =
        db.all(Court.class).stream()
            .filter(x -> x.enabled && orgIds.contains(x.departmentId))
            .toList();
    var seasons =
        db.all(Season.class).stream()
            .filter(x -> x.enabled && orgIds.contains(x.departmentId))
            .toList();
    var accounts =
        staff()
            ? db.all(Account.class).stream()
                .filter(a -> a.enabled && orgIds.contains(a.departmentId))
                .map(
                    a ->
                        map(
                            "id",
                            a.id,
                            "name",
                            a.displayName,
                            "departmentId",
                            a.departmentId,
                            "referee",
                            db.get(AccessRole.class, a.roleId).permissions.contains("referee"),
                            "participant",
                            db.get(AccessRole.class, a.roleId).scope.equals("PARTICIPANT")))
                .toList()
            : List.of();
    return map(
        "departments",
        dirs,
        "courts",
        courts,
        "seasons",
        seasons,
        "accounts",
        accounts,
        "categories",
        db.all(DictionaryEntry.class).stream().filter(x -> x.enabled).toList());
  }

  /** 场地和赛季管理保留外键历史，停用不影响已完成记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object master(String type, Long id, Map<String, Object> b) {
    admin.lock();
    Long dept = Rules.id(b.get("departmentId"));
    org(dept, "masters");
    String name = Rules.text(b.get("name"), 120, true);
    boolean enabled = Rules.flag(b.get("enabled"));
    Object out;
    if (type.equals("seasons")) {
      var s = id == null ? new Season() : db.get(Season.class, id);
      if (id != null) {
        org(s.departmentId, "masters");
        version(s.version, b.get("version"));
        Rules.check(s.departmentId.equals(dept), "IDENTITY_LOCKED");
      }
      s.departmentId = dept;
      s.name = name;
      s.enabled = enabled;
      if (id == null) db.save(s);
      else s.version++;
      out = s;
    } else if (type.equals("courts")) {
      var c = id == null ? new Court() : db.get(Court.class, id);
      if (id != null) {
        org(c.departmentId, "masters");
        version(c.version, b.get("version"));
        Rules.check(c.departmentId.equals(dept), "IDENTITY_LOCKED");
        if (!enabled)
          Rules.check(
              db.query(
                      Game.class,
                      "from Game where courtId=?1 and status in ('SCHEDULED','LIVE')",
                      id)
                  .isEmpty(),
              "COURT_IN_USE");
      }
      c.departmentId = dept;
      c.name = name;
      c.enabled = enabled;
      if (id == null) db.save(c);
      else c.version++;
      out = c;
    } else throw new Problem(404, "NOT_FOUND");
    db.flush();
    access.audit("MASTER_" + type, id == null ? "NEW" : id, dept);
    return out;
  }

  /** 授权目录包含停用记录，供管理和历史查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object masters(String type) {
    access.require("masters");
    if (!staff()) throw new Problem(403, "OUT_OF_SCOPE");
    return switch (type) {
      case "seasons" ->
          db.all(Season.class).stream().filter(s -> access.department(s.departmentId)).toList();
      case "courts" ->
          db.all(Court.class).stream().filter(s -> access.department(s.departmentId)).toList();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 删除无引用目录；外键阻止损害历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteMaster(String type, Long id, long version) {
    admin.lock();
    if (type.equals("seasons")) {
      var x = db.get(Season.class, id);
      org(x.departmentId, "masters");
      version(x.version, version);
      db.delete(x);
    } else if (type.equals("courts")) {
      var x = db.get(Court.class, id);
      org(x.departmentId, "masters");
      version(x.version, version);
      db.delete(x);
    } else throw new Problem(404, "NOT_FOUND");
  }

  /** 新建或修改尚未开放的赛事，冻结正式竞赛参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Map<String, Object> b) {
    admin.lock();
    var t = id == null ? new Tournament() : manage(id);
    var season = db.get(Season.class, Rules.id(b.get("seasonId")));
    org(season.departmentId, "manage");
    Rules.check(season.enabled, "SEASON_DISABLED");
    if (id != null) {
      version(t.version, b.get("version"));
      Rules.check(t.status.equals("DRAFT"), "STATE_INVALID");
      Rules.check(t.departmentId.equals(season.departmentId), "IDENTITY_LOCKED");
    } else {
      Rules.check(db.all(Tournament.class).size() < admin.setting("max_events"), "RESOURCE_LIMIT");
      t.departmentId = season.departmentId;
      t.ownerId = access.current().id;
      t.status = "DRAFT";
    }
    t.seasonId = season.id;
    t.name = Rules.text(b.get("name"), 120, true);
    t.description = Rules.paragraph(b.get("description"), 2000, false);
    t.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where code=?1 and enabled=true",
                    t.category)
                .size()
            == 1,
        "CATEGORY_INVALID");
    t.format = Rules.text(b.get("format"), 20, true);
    Rules.check(Set.of("GROUP_KO", "KNOCKOUT").contains(t.format), "INVALID_INPUT");
    t.groups = Rules.integer(b.get("groups"), 1, 8);
    t.advance = Rules.integer(b.get("advance"), 1, 4);
    t.bestOf = Rules.integer(b.get("bestOf"), 3, 7);
    Rules.check(t.bestOf % 2 == 1, "SCORE_INVALID");
    Rules.check(!t.format.equals("GROUP_KO") || t.groups * t.advance >= 2, "DRAW_INVALID");
    t.capacity = Rules.integer(b.get("capacity"), 2, admin.setting("max_entries"));
    Rules.check(
        !t.format.equals("GROUP_KO") || t.capacity >= t.groups * Math.max(2, t.advance),
        "GROUP_SIZE_INVALID");
    t.startsAt =
        Instant.parse(Rules.text(b.get("startsAt"), 40, true))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    t.endsAt =
        Instant.parse(Rules.text(b.get("endsAt"), 40, true))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    Rules.check(
        t.endsAt.isAfter(t.startsAt)
            && Duration.between(t.startsAt, t.endsAt).compareTo(Duration.ofDays(365)) <= 0,
        "TIME_INVALID");
    if (id == null) db.save(t);
    else t.version++;
    db.flush();
    history(t, "SAVE", t.name);
    return t;
  }

  /** 只允许删除未开放、无报名的草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, long v) {
    admin.lock();
    var t = manage(id);
    version(t.version, v);
    Rules.check(t.status.equals("DRAFT") && entries(id).isEmpty(), "STATE_INVALID");
    for (var h : db.query(LeagueEvent.class, "from LeagueEvent where tournamentId=?1", id))
      db.delete(h);
    db.delete(t);
    access.audit("DELETE_EVENT", id, t.departmentId);
  }

  /** 赛事列表的参数绑定搜索、筛选和SQL分页，按角色添加范围谓词。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("events");
    Rules.integer(page, 0, 100000);
    Rules.integer(size, 1, 100);
    String where = " where 1=1";
    var params = new HashMap<String, Object>();
    if (!access.role().scope.equals("ALL")) {
      where += " and t.departmentId=:dept";
      params.put("dept", access.current().departmentId);
      switch (access.role().scope) {
        case "PARTICIPANT" -> {
          where +=
              " and (t.status='OPEN' or exists(select e.id from Entry e where e.tournamentId=t.id and e.accountId=:uid))";
          params.put("uid", access.current().id);
        }
        case "ASSIGNED" -> {
          where +=
              " and exists(select g.id from Game g where g.tournamentId=t.id and g.refereeId=:uid)";
          params.put("uid", access.current().id);
        }
        case "DEPARTMENT" -> {}
        default -> throw new Problem(403, "OUT_OF_SCOPE");
      }
    }
    if (!search.isBlank()) {
      where += " and lower(t.name) like :q";
      params.put("q", "%" + Rules.text(search, 120, false).toLowerCase(Locale.ROOT) + "%");
    }
    if (!status.isBlank()) {
      where += " and t.status=:status";
      params.put("status", status);
    }
    var q =
        db.jpql(
            Tournament.class,
            "from Tournament t"
                + where
                + " order by t."
                + (sort.equals("name")
                    ? "name asc,t.id desc"
                    : sort.equals("time") ? "startsAt asc,t.id desc" : "id desc"));
    var n = db.jpql(Long.class, "select count(t) from Tournament t" + where);
    params.forEach(
        (k, v) -> {
          q.setParameter(k, v);
          n.setParameter(k, v);
        });
    return map(
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList(),
        "total",
        n.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 按岗位返回赛事详情，公开资料与私有报名备注明确分开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var t = event(id);
    return detail(t, false);
  }

  Object detail(Tournament t, boolean publicView) {
    var es = entries(t.id);
    boolean full = !publicView && staff();
    Long uid = publicView ? null : access.current().id;
    var info =
        es.stream()
            .filter(e -> full || e.status.equals("APPROVED") || Objects.equals(e.accountId, uid))
            .map(
                e -> {
                  var m =
                      map(
                          "id",
                          e.id,
                          "name",
                          e.name,
                          "status",
                          e.status,
                          "checkedIn",
                          e.checkedIn,
                          "seed",
                          e.seed,
                          "groupNo",
                          e.groupNo,
                          "version",
                          e.version);
                  if (full || Objects.equals(e.accountId, uid)) {
                    m.put("accountId", e.accountId);
                    m.put("note", e.note);
                  }
                  return m;
                })
            .toList();
    var gs =
        games(t.id).stream()
            .map(
                g -> {
                  var m =
                      map(
                          "id",
                          g.id,
                          "stage",
                          g.stage,
                          "roundNo",
                          g.roundNo,
                          "position",
                          g.position,
                          "groupNo",
                          g.groupNo,
                          "homeId",
                          g.homeId,
                          "awayId",
                          g.awayId,
                          "winnerId",
                          g.winnerId,
                          "status",
                          g.status,
                          "courtId",
                          g.courtId,
                          "startsAt",
                          g.startsAt,
                          "endsAt",
                          g.endsAt,
                          "homeScore",
                          g.homeScore,
                          "awayScore",
                          g.awayScore,
                          "resultType",
                          g.resultType,
                          "sourceHomeId",
                          g.sourceHomeId,
                          "sourceAwayId",
                          g.sourceAwayId,
                          "version",
                          g.version);
                  if (!publicView) m.put("refereeId", g.refereeId);
                  return m;
                })
            .toList();
    return map(
        "tournament",
        t,
        "entries",
        info,
        "games",
        gs,
        "courts",
        db.all(Court.class).stream()
            .filter(x -> x.departmentId.equals(t.departmentId))
            .map(x -> map("id", x.id, "name", x.name))
            .toList(),
        "standings",
        standings(t),
        "history",
        full
            ? db.query(
                LeagueEvent.class, "from LeagueEvent where tournamentId=?1 order by id desc", t.id)
            : List.of());
  }

  /** 本人报名或工作人员登记；绑定实际启用的参赛负责人并禁止超额和重复报名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object register(Long id, Map<String, Object> b) {
    admin.lock();
    var t = event(id);
    Rules.check(t.status.equals("OPEN"), "REGISTRATION_CLOSED");
    Long accountId;
    if (staff() && access.has("manage")) {
      accountId = Rules.id(b.get("accountId"));
    } else {
      access.require("register");
      Rules.check(access.role().scope.equals("PARTICIPANT"), "PARTICIPANT_ROLE_INVALID");
      accountId = access.current().id;
    }
    var a = db.get(Account.class, accountId);
    Rules.check(
        a.enabled
            && a.departmentId.equals(t.departmentId)
            && db.get(AccessRole.class, a.roleId).scope.equals("PARTICIPANT"),
        "PARTICIPANT_INVALID");
    var active =
        entries(id).stream()
            .filter(e -> !Set.of("WITHDRAWN", "REJECTED").contains(e.status))
            .count();
    Rules.check(active < t.capacity, "EVENT_FULL");
    String name = Rules.text(b.get("name"), 120, true);
    String note = Rules.paragraph(b.get("note"), 600, false);
    Rules.check(Boolean.TRUE.equals(b.get("publicConsent")), "PUBLIC_NAME_CONSENT");
    var prior =
        db.query(Entry.class, "from Entry where tournamentId=?1 and accountId=?2", id, accountId);
    var e = prior.isEmpty() ? new Entry() : prior.getFirst();
    Rules.check(
        prior.isEmpty() || Set.of("WITHDRAWN", "REJECTED").contains(e.status), "DUPLICATE_ENTRY");
    e.tournamentId = id;
    e.accountId = accountId;
    e.name = name;
    e.note = note;
    e.status = "PENDING";
    e.seed = 0;
    e.groupNo = 0;
    e.checkedIn = false;
    if (prior.isEmpty()) db.save(e);
    else e.version++;
    t.version++;
    db.flush();
    history(t, "REGISTER", name);
    return detail(t, false);
  }

  /** 单次有效报名撤回，保留原始记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object withdraw(Long id, Long entryId, Map<String, Object> b) {
    admin.lock();
    var t = event(id);
    var e = db.get(Entry.class, entryId);
    Rules.check(e.tournamentId.equals(id), "ENTRY_INVALID");
    if (!Objects.equals(e.accountId, access.current().id)) {
      manage(id);
    } else access.require("register");
    version(e.version, b.get("version"));
    Rules.check(
        t.status.equals("OPEN") && Set.of("PENDING", "APPROVED").contains(e.status),
        "STATE_INVALID");
    e.status = "WITHDRAWN";
    e.checkedIn = false;
    e.version++;
    t.version++;
    history(t, "WITHDRAW", e.name);
    return detail(t, false);
  }

  /** 全部变更绑定UUID和完整载荷；相同重试返回当前详情，不同载荷拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Map<String, Object> b) {
    admin.lock();
    var t = event(id);
    String requestId = Rules.text(b.get("requestId"), 36, true);
    try {
      UUID.fromString(requestId);
    } catch (Exception e) {
      throw new Problem(400, "REQUEST_ID_INVALID");
    }
    String fingerprint;
    try {
      var bytes = json.writeValueAsBytes(new TreeMap<>(b));
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (id + ":" + action + ":" + new String(bytes, StandardCharsets.UTF_8))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    var prior = db.query(CommandStamp.class, "from CommandStamp where requestId=?1", requestId);
    if (!prior.isEmpty()) {
      var s = prior.getFirst();
      Rules.check(
          s.actorId.equals(access.current().id) && s.fingerprint.equals(fingerprint),
          "REQUEST_REUSED");
      return detail(t, false);
    }
    version(t.version, b.get("version"));
    if (Set.of("START", "RESULT").contains(action)) {
      access.require("referee");
    } else manage(id);
    switch (action) {
      case "OPEN" -> {
        Rules.check(t.status.equals("DRAFT"), "STATE_INVALID");
        Rules.check(t.endsAt.isAfter(clock.instant()), "TIME_INVALID");
        t.status = "OPEN";
      }
      case "PUBLISH" -> {
        Rules.check(!t.status.equals("DRAFT"), "STATE_INVALID");
        t.published = Rules.flag(b.get("published"));
      }
      case "REVIEW", "CHECKIN" -> {
        Rules.check(t.status.equals("OPEN"), "STATE_INVALID");
        var e = db.get(Entry.class, Rules.id(b.get("entryId")));
        Rules.check(e.tournamentId.equals(id), "ENTRY_INVALID");
        version(e.version, b.get("entryVersion"));
        if (action.equals("CHECKIN")) {
          Rules.check(e.status.equals("APPROVED"), "STATE_INVALID");
          e.checkedIn = Rules.flag(b.get("checkedIn"));
        } else {
          Rules.check(
              Set.of("PENDING", "APPROVED", "REJECTED").contains(e.status), "STATE_INVALID");
          e.status = Rules.flag(b.get("approved")) ? "APPROVED" : "REJECTED";
          e.checkedIn = false;
          e.seed = Rules.integer(b.get("seed"), 1, 10000);
        }
        e.version++;
      }
      case "DRAW" -> draw(t);
      case "ADVANCE" -> {
        Rules.check(t.status.equals("GROUP"), "STATE_INVALID");
        Rules.check(games(id).stream().allMatch(g -> g.status.equals("DONE")), "GROUP_INCOMPLETE");
        var ranks = standings(t);
        var selected = new ArrayList<Entry>();
        for (int place = 1; place <= t.advance; place++) {
          for (int group = 1; group <= t.groups; group++) {
            final int p = place, g = group;
            var rank =
                ranks.stream()
                    .filter(
                        x ->
                            ((Number) x.get("groupNo")).intValue() == g
                                && ((Number) x.get("rank")).intValue() == p)
                    .findFirst()
                    .orElseThrow(() -> new Problem(409, "DRAW_INVALID"));
            selected.add(db.get(Entry.class, ((Number) rank.get("entryId")).longValue()));
          }
        }
        knockout(t, selected);
        t.status = "KNOCKOUT";
      }
      case "SCHEDULE" -> schedule(t, b);
      case "START" -> {
        var g = game(t, b);
        referee(g);
        Rules.check(g.status.equals("SCHEDULED"), "STATE_INVALID");
        Rules.check(!clock.instant().isBefore(g.startsAt.minusSeconds(1800)), "MATCH_TOO_EARLY");
        validateSlot(t, g, g.courtId, g.refereeId, g.startsAt, g.endsAt);
        g.status = "LIVE";
        g.version++;
      }
      case "RESULT", "CORRECT" -> result(t, b, action.equals("CORRECT"));
      case "FINISH" -> {
        Rules.check(t.status.equals("KNOCKOUT"), "STATE_INVALID");
        Rules.check(
            games(id).stream().allMatch(g -> Set.of("DONE", "BYE").contains(g.status)),
            "MATCHES_INCOMPLETE");
        t.status = "FINISHED";
      }
      case "CANCEL" -> {
        Rules.check(!Set.of("FINISHED", "CANCELLED").contains(t.status), "STATE_INVALID");
        Rules.text(b.get("reason"), 600, true);
        Rules.check(
            games(id).stream().noneMatch(g -> g.status.equals("LIVE")), "MATCH_IN_PROGRESS");
        t.status = "CANCELLED";
        for (var g : games(id))
          if (!Set.of("DONE", "BYE").contains(g.status)) {
            g.status = "CANCELLED";
            g.version++;
          }
      }
      default -> throw new Problem(400, "ACTION_INVALID");
    }
    t.version++;
    var stamp = new CommandStamp();
    stamp.requestId = requestId;
    stamp.actorId = access.current().id;
    stamp.fingerprint = fingerprint;
    stamp.tournamentId = id;
    db.save(stamp);
    history(
        t,
        action,
        Set.of("RESULT", "CORRECT").contains(action) ? resultHistory(t, b) : commandHistory(b));
    db.flush();
    return detail(t, false);
  }

  void draw(Tournament t) {
    Rules.check(t.status.equals("OPEN"), "STATE_INVALID");
    var approved =
        entries(t.id).stream()
            .filter(e -> e.status.equals("APPROVED") && e.checkedIn)
            .sorted(Comparator.comparingInt((Entry e) -> e.seed).thenComparing(e -> e.id))
            .toList();
    Rules.check(approved.size() >= 2 && approved.size() <= t.capacity, "DRAW_INVALID");
    Rules.check(
        approved.stream().map(e -> e.seed).distinct().count() == approved.size(), "SEED_DUPLICATE");
    if (t.format.equals("KNOCKOUT")) {
      knockout(t, approved);
      t.status = "KNOCKOUT";
    } else {
      Rules.check(
          approved.size() / t.groups >= Math.max(2, t.advance)
              && (approved.size() + t.groups - 1) / t.groups <= 16,
          "GROUP_SIZE_INVALID");
      for (int i = 0; i < approved.size(); i++) {
        var e = approved.get(i);
        e.groupNo = DrawEngine.group(i, t.groups);
        e.version++;
      }
      int pos = 0;
      for (int group = 1; group <= t.groups; group++) {
        final int g = group;
        var ids = approved.stream().filter(e -> e.groupNo == g).map(e -> e.id).toList();
        for (var pair : DrawEngine.roundRobin(ids)) {
          var match = new Game();
          match.tournamentId = t.id;
          match.stage = "GROUP";
          match.groupNo = group;
          match.roundNo = pair.round();
          match.position = ++pos;
          match.homeId = pair.home();
          match.awayId = pair.away();
          match.status = "WAITING";
          match.resultType = "";
          db.save(match);
        }
      }
      t.status = "GROUP";
    }
  }

  void knockout(Tournament t, List<Entry> selected) {
    var seed = DrawEngine.seeds(selected.size());
    var previous = new ArrayList<Game>();
    for (int i = 0; i < seed.size(); i += 2) {
      var g = new Game();
      g.tournamentId = t.id;
      g.stage = "KNOCKOUT";
      g.roundNo = 1;
      g.position = i / 2 + 1;
      g.homeId = seed.get(i) <= selected.size() ? selected.get(seed.get(i) - 1).id : null;
      g.awayId = seed.get(i + 1) <= selected.size() ? selected.get(seed.get(i + 1) - 1).id : null;
      g.status = g.homeId == null || g.awayId == null ? "BYE" : "WAITING";
      g.winnerId = g.status.equals("BYE") ? (g.homeId != null ? g.homeId : g.awayId) : null;
      g.resultType = g.status.equals("BYE") ? "BYE" : "";
      db.save(g);
      previous.add(g);
    }
    int round = 2;
    while (previous.size() > 1) {
      var next = new ArrayList<Game>();
      for (int i = 0; i < previous.size(); i += 2) {
        var g = new Game();
        g.tournamentId = t.id;
        g.stage = "KNOCKOUT";
        g.roundNo = round;
        g.position = i / 2 + 1;
        g.sourceHomeId = previous.get(i).id;
        g.sourceAwayId = previous.get(i + 1).id;
        g.homeId = previous.get(i).winnerId;
        g.awayId = previous.get(i + 1).winnerId;
        g.status = "WAITING";
        g.resultType = "";
        db.save(g);
        next.add(g);
      }
      previous = next;
      round++;
    }
  }

  Game game(Tournament t, Map<String, Object> b) {
    Rules.check(Set.of("GROUP", "KNOCKOUT").contains(t.status), "STATE_INVALID");
    var g = db.get(Game.class, Rules.id(b.get("gameId")));
    Rules.check(g.tournamentId.equals(t.id), "MATCH_INVALID");
    version(g.version, b.get("gameVersion"));
    return g;
  }

  void referee(Game g) {
    Rules.check(Objects.equals(g.refereeId, access.current().id), "REFEREE_ONLY");
  }

  void schedule(Tournament t, Map<String, Object> b) {
    var g = game(t, b);
    Rules.check(
        Set.of("WAITING", "SCHEDULED").contains(g.status) && g.homeId != null && g.awayId != null,
        "MATCH_NOT_READY");
    Long court = Rules.id(b.get("courtId")), ref = Rules.id(b.get("refereeId"));
    var start =
        Instant.parse(Rules.text(b.get("startsAt"), 40, true))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var end =
        Instant.parse(Rules.text(b.get("endsAt"), 40, true))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    validateSlot(t, g, court, ref, start, end);
    g.courtId = court;
    g.refereeId = ref;
    g.startsAt = start;
    g.endsAt = end;
    g.status = "SCHEDULED";
    g.version++;
  }

  void validateSlot(
      Tournament t, Game g, Long courtId, Long refereeId, Instant start, Instant end) {
    Rules.check(
        end.isAfter(start)
            && Duration.between(start, end).compareTo(Duration.ofHours(4)) <= 0
            && !start.isBefore(t.startsAt)
            && !end.isAfter(t.endsAt),
        "TIME_INVALID");
    var court = db.get(Court.class, courtId);
    var ref = db.get(Account.class, refereeId);
    Rules.check(court.enabled && court.departmentId.equals(t.departmentId), "COURT_INVALID");
    Rules.check(
        ref.enabled
            && ref.departmentId.equals(t.departmentId)
            && db.get(AccessRole.class, ref.roleId).permissions.contains("referee")
            && !Set.of("PARTICIPANT").contains(db.get(AccessRole.class, ref.roleId).scope),
        "REFEREE_INVALID");
    var participants =
        Set.of(db.get(Entry.class, g.homeId).accountId, db.get(Entry.class, g.awayId).accountId);
    Rules.check(!participants.contains(refereeId), "REFEREE_CONFLICT");
    for (var x :
        db.query(
            Game.class,
            "from Game where startsAt < ?1 and endsAt > ?2 and status in ('SCHEDULED','LIVE','DONE')",
            end,
            start)) {
      if (x.id.equals(g.id)) continue;
      boolean sameCourt = Objects.equals(courtId, x.courtId),
          sameRef = Objects.equals(refereeId, x.refereeId);
      var other = new HashSet<Long>();
      if (x.homeId != null) other.add(db.get(Entry.class, x.homeId).accountId);
      if (x.awayId != null) other.add(db.get(Entry.class, x.awayId).accountId);
      Rules.check(
          !sameCourt
              && !sameRef
              && Collections.disjoint(participants, other)
              && !other.contains(refereeId)
              && !participants.contains(x.refereeId),
          "SCHEDULE_CONFLICT");
    }
  }

  void result(Tournament t, Map<String, Object> b, boolean correction) {
    var g = game(t, b);
    String reason = Rules.text(b.get("reason"), 600, correction);
    if (correction) {
      Rules.check(g.status.equals("DONE"), "STATE_INVALID");
      if (g.stage.equals("GROUP")) Rules.check(t.status.equals("GROUP"), "DOWNSTREAM_LOCKED");
      else {
        var downstream = descendants(g.id, games(t.id));
        Rules.check(
            downstream.stream().noneMatch(x -> Set.of("LIVE", "DONE").contains(x.status)),
            "DOWNSTREAM_LOCKED");
      }
    } else {
      referee(g);
      Rules.check(g.status.equals("LIVE"), "STATE_INVALID");
    }
    String old = g.homeScore + ":" + g.awayScore + ":" + g.winnerId + ":" + g.resultType;
    String type = Rules.text(b.get("resultType"), 20, true);
    int hs = Rules.integer(b.get("homeScore"), 0, t.bestOf / 2 + 1),
        as = Rules.integer(b.get("awayScore"), 0, t.bestOf / 2 + 1);
    Long winner;
    if (type.equals("PLAYED")) {
      int target = t.bestOf / 2 + 1;
      Rules.check((hs == target && as < target) || (as == target && hs < target), "SCORE_INVALID");
      winner = hs > as ? g.homeId : g.awayId;
    } else if (type.equals("WALKOVER")) {
      Rules.check(hs == 0 && as == 0, "SCORE_INVALID");
      winner = Rules.id(b.get("winnerId"));
      Rules.check(winner.equals(g.homeId) || winner.equals(g.awayId), "SCORE_INVALID");
    } else throw new Problem(400, "SCORE_INVALID");
    Rules.check(winner != null, "MATCH_NOT_READY");
    g.homeScore = hs;
    g.awayScore = as;
    g.winnerId = winner;
    g.resultType = type;
    g.status = "DONE";
    g.version++;
    if (correction)
      history(
          t,
          "RESULT_BEFORE",
          g.id + " " + old + " → " + hs + ":" + as + ":" + winner + ":" + type + "; " + reason);
    if (g.stage.equals("KNOCKOUT")) propagate(t, g.id);
  }

  List<Game> descendants(Long id, List<Game> all) {
    var out = new ArrayList<Game>();
    for (var x : all)
      if (Objects.equals(x.sourceHomeId, id) || Objects.equals(x.sourceAwayId, id)) {
        out.add(x);
        out.addAll(descendants(x.id, all));
      }
    return out;
  }

  void propagate(Tournament t, Long id) {
    for (var x : games(t.id))
      if (Objects.equals(x.sourceHomeId, id) || Objects.equals(x.sourceAwayId, id)) {
        Long h = db.get(Game.class, x.sourceHomeId).winnerId,
            a = db.get(Game.class, x.sourceAwayId).winnerId;
        if (!Objects.equals(x.homeId, h) || !Objects.equals(x.awayId, a)) {
          x.homeId = h;
          x.awayId = a;
          x.winnerId = null;
          x.homeScore = 0;
          x.awayScore = 0;
          x.resultType = "";
          x.status = "WAITING";
          x.courtId = null;
          x.refereeId = null;
          x.startsAt = null;
          x.endsAt = null;
          x.version++;
          propagate(t, x.id);
        }
      }
  }

  String commandHistory(Map<String, Object> body) {
    var details = new TreeMap<String, Object>();
    for (var key :
        List.of(
            "entryId",
            "approved",
            "seed",
            "checkedIn",
            "gameId",
            "courtId",
            "refereeId",
            "startsAt",
            "endsAt",
            "published",
            "reason")) if (body.containsKey(key)) details.put(key, body.get(key));
    return details.isEmpty() ? "" : json.writeValueAsString(details);
  }

  String resultHistory(Tournament t, Map<String, Object> b) {
    var g = db.get(Game.class, Rules.id(b.get("gameId")));
    return g.id
        + " "
        + g.homeScore
        + ":"
        + g.awayScore
        + "; winner="
        + g.winnerId
        + "; "
        + Objects.toString(b.get("reason"), "");
  }

  /** 小组排名冻结口径：胜场、净胜局、胜局、种子；弃权只计胜场。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  List<Map<String, Object>> standings(Tournament t) {
    var results = new ArrayList<Map<String, Object>>();
    var matches = games(t.id);
    for (int group = 1; group <= t.groups; group++) {
      final int gn = group;
      var es = entries(t.id).stream().filter(e -> e.groupNo == gn).toList();
      var groupRanks = new ArrayList<Map<String, Object>>();
      for (var e : es) {
        int played = 0, wins = 0, forScore = 0, against = 0;
        for (var g : matches)
          if (g.stage.equals("GROUP")
              && g.status.equals("DONE")
              && (e.id.equals(g.homeId) || e.id.equals(g.awayId))) {
            played++;
            if (e.id.equals(g.winnerId)) wins++;
            forScore += e.id.equals(g.homeId) ? g.homeScore : g.awayScore;
            against += e.id.equals(g.homeId) ? g.awayScore : g.homeScore;
          }
        groupRanks.add(
            map(
                "entryId",
                e.id,
                "name",
                e.name,
                "groupNo",
                gn,
                "played",
                played,
                "wins",
                wins,
                "setsFor",
                forScore,
                "setsAgainst",
                against,
                "difference",
                forScore - against,
                "seed",
                e.seed));
      }
      groupRanks.sort(
          Comparator.<Map<String, Object>>comparingInt(x -> -(int) x.get("wins"))
              .thenComparingInt(x -> -(int) x.get("difference"))
              .thenComparingInt(x -> -(int) x.get("setsFor"))
              .thenComparingInt(x -> (int) x.get("seed")));
      for (int i = 0; i < groupRanks.size(); i++) {
        groupRanks.get(i).put("rank", i + 1);
        results.add(groupRanks.get(i));
      }
    }
    return results;
  }

  /** 本人报名列表不返回其他负责人的私有资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object mine() {
    access.require("register");
    return db
        .query(Entry.class, "from Entry where accountId=?1 order by id desc", access.current().id)
        .stream()
        .map(e -> map("entry", e, "tournament", db.get(Tournament.class, e.tournamentId)))
        .toList();
  }

  /** 裁判仅看到本人指定比赛与双方显示名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object refereeDesk() {
    access.require("referee");
    return db
        .query(
            Game.class,
            "from Game where refereeId=?1 order by case when status='LIVE' then 0 when status='SCHEDULED' then 1 else 2 end,startsAt,id",
            access.current().id)
        .stream()
        .filter(g -> visible(db.get(Tournament.class, g.tournamentId)))
        .map(
            g ->
                map(
                    "game",
                    g,
                    "tournament",
                    db.get(Tournament.class, g.tournamentId),
                    "home",
                    g.homeId == null ? "" : db.get(Entry.class, g.homeId).name,
                    "away",
                    g.awayId == null ? "" : db.get(Entry.class, g.awayId).name,
                    "court",
                    g.courtId == null ? "" : db.get(Court.class, g.courtId).name))
        .toList();
  }

  /** 完成赛事才累加赛季本地积分，不冒充官方运动评级。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object reports() {
    access.require("reports");
    if (!staff()) throw new Problem(403, "OUT_OF_SCOPE");
    var ts = db.all(Tournament.class).stream().filter(this::visible).toList();
    return map(
        "events",
        ts.size(),
        "active",
        ts.stream().filter(t -> Set.of("OPEN", "GROUP", "KNOCKOUT").contains(t.status)).count(),
        "finished",
        ts.stream().filter(t -> t.status.equals("FINISHED")).count(),
        "entries",
        ts.stream().mapToLong(t -> entries(t.id).size()).sum(),
        "standings",
        seasonRanks(ts));
  }

  List<Map<String, Object>> seasonRanks(List<Tournament> ts) {
    var scores = new HashMap<String, Map<String, Object>>();
    for (var t : ts)
      if (t.status.equals("FINISHED")) {
        var gs = games(t.id);
        var finalGame =
            gs.stream()
                .filter(g -> g.stage.equals("KNOCKOUT"))
                .max(Comparator.comparingInt(g -> g.roundNo))
                .orElseThrow();
        var actual =
            gs.stream()
                .flatMap(g -> java.util.stream.Stream.of(g.homeId, g.awayId))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (var e : entries(t.id))
          if (actual.contains(e.id)) {
            int points =
                e.id.equals(finalGame.winnerId)
                    ? 5
                    : (e.id.equals(finalGame.homeId) || e.id.equals(finalGame.awayId)) ? 3 : 1;
            String key = t.seasonId + ":" + e.accountId;
            var row =
                scores.computeIfAbsent(
                    key,
                    k ->
                        map(
                            "seasonId",
                            t.seasonId,
                            "season",
                            db.get(Season.class, t.seasonId).name,
                            "name",
                            e.name,
                            "events",
                            0,
                            "points",
                            0,
                            "wins",
                            0));
            row.put("points", (int) row.get("points") + points);
            row.put("events", (int) row.get("events") + 1);
            if (e.id.equals(finalGame.winnerId)) row.put("wins", (int) row.get("wins") + 1);
          }
      }
    return scores.values().stream()
        .sorted(
            Comparator.<Map<String, Object>>comparingLong(
                    x -> ((Number) x.get("seasonId")).longValue())
                .thenComparingInt(x -> -(int) x.get("points"))
                .thenComparingInt(x -> -(int) x.get("wins"))
                .thenComparing(x -> x.get("name").toString()))
        .toList();
  }

  /** 授权赛事CSV不含账号、备注或广告；文本防公式注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    var t = event(id);
    access.require("export");
    var out =
        new StringBuilder("stage,round,group,home,away,status,score,winner,starts_at,court\n");
    for (var g : games(id)) {
      Object[] values = {
        g.stage,
        g.roundNo,
        g.groupNo,
        g.homeId == null ? "" : db.get(Entry.class, g.homeId).name,
        g.awayId == null ? "" : db.get(Entry.class, g.awayId).name,
        g.status,
        g.homeScore + ":" + g.awayScore,
        g.winnerId == null ? "" : db.get(Entry.class, g.winnerId).name,
        g.startsAt,
        g.courtId == null ? "" : db.get(Court.class, g.courtId).name
      };
      out.append(
              Arrays.stream(values)
                  .map(Rules::csv)
                  .collect(java.util.stream.Collectors.joining(",")))
          .append('\n');
    }
    return out.toString();
  }

  /** 匿名赛事列表仅暴露已发布内容；限量分页防无界加载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object publicList(int page) {
    Rules.integer(page, 0, 100000);
    return db
        .jpql(
            Tournament.class,
            "from Tournament where published=true and status<>'DRAFT' order by startsAt desc,id desc")
        .setFirstResult(page * 20)
        .setMaxResults(20)
        .getResultList()
        .stream()
        .map(
            t ->
                map(
                    "id",
                    t.id,
                    "name",
                    t.name,
                    "format",
                    t.format,
                    "status",
                    t.status,
                    "startsAt",
                    t.startsAt,
                    "endsAt",
                    t.endsAt,
                    "description",
                    t.description))
        .toList();
  }

  /** 单独构造公众详情，不含账号ID、联系人、种子审核备注、裁判身份和审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object publicDetail(Long id) {
    var t = db.get(Tournament.class, id);
    if (!t.published || t.status.equals("DRAFT")) throw new Problem(404, "NOT_FOUND");
    var result = (Map<String, Object>) detail(t, true);
    result.put(
        "tournament",
        map(
            "id",
            t.id,
            "name",
            t.name,
            "format",
            t.format,
            "status",
            t.status,
            "startsAt",
            t.startsAt,
            "endsAt",
            t.endsAt,
            "bestOf",
            t.bestOf,
            "description",
            t.description,
            "groups",
            t.groups,
            "advance",
            t.advance));
    return result;
  }

  /** 组织内操作审计，不包含登录凭据或报名备注。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (!staff()) throw new Problem(403, "OUT_OF_SCOPE");
    return db.all(AuditEvent.class).stream()
        .filter(x -> access.department(x.departmentId))
        .sorted(Comparator.comparing((AuditEvent x) -> x.id).reversed())
        .limit(1000)
        .toList();
  }
}
