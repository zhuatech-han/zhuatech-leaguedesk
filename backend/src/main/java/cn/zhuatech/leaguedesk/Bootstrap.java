// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 初始化岗位、权限、赛事分类和强密码管理员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final Set<String> CODES =
      Set.of(
          "events",
          "manage",
          "register",
          "referee",
          "masters",
          "reports",
          "export",
          "users",
          "roles",
          "settings",
          "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${leaguedesk.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 空库初始化，重启不会覆盖密码或插入比赛。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "赛事组织";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var names =
        Map.ofEntries(
            Map.entry("events", "赛事查阅"),
            Map.entry("manage", "办赛管理"),
            Map.entry("register", "本人报名"),
            Map.entry("referee", "指定比赛裁判"),
            Map.entry("masters", "场地与赛季"),
            Map.entry("reports", "赛季统计"),
            Map.entry("export", "导出比赛"),
            Map.entry("users", "账号管理"),
            Map.entry("roles", "角色权限"),
            Map.entry("settings", "组织设置"),
            Map.entry("audit", "操作审计"));
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", CODES);
    role("主办人员", "DEPARTMENT", Set.of("events", "manage", "masters", "reports", "export", "audit"));
    role("裁判", "ASSIGNED", Set.of("events", "referee"));
    role("报名负责人", "PARTICIPANT", Set.of("events", "register"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    db.save(a);
    String[][] menus = {
      {"events", "赛事", "Tournaments", "events"},
      {"mine", "我的报名", "My entries", "register"},
      {"referee", "裁判工作台", "Referee desk", "referee"},
      {"masters", "赛季与场地", "Seasons & courts", "masters"},
      {"reports", "赛季积分", "Season standings", "reports"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "users"},
      {"roles", "角色权限", "Roles", "roles"},
      {"settings", "组织与设置", "Settings", "settings"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var k : Map.of("max_events", "1000", "max_entries", "64").entrySet()) {
      var s = new SystemSetting();
      s.code = k.getKey();
      s.value = k.getValue();
      db.save(s);
    }
    var c = new DictionaryEntry();
    c.type = "CATEGORY";
    c.code = "RACKET";
    c.name = "球类胜局赛";
    c.nameEn = "Racket games";
    c.enabled = true;
    db.save(c);
  }

  private void role(String n, String scope, Set<String> codes) {
    var r = new AccessRole();
    r.name = n;
    r.scope = scope;
    r.permissions.addAll(codes);
    db.save(r);
  }
}
