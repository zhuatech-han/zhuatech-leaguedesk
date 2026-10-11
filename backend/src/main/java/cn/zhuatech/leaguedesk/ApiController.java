// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 赛事、参赛负责人、裁判、公众和系统目录接口；业务服务逐次检查权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final LeagueService service;
  final AdminService admin;

  public ApiController(LeagueService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 岗位所需目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 分页过滤赛事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events")
  public Object events(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** 创建赛事草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events")
  public Object create(@RequestBody Map<String, Object> b) {
    return service.save(null, b);
  }

  /** 修改尚未开放的赛事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/events/{id}")
  public Object save(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.save(id, b);
  }

  /** 删除无报名草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/events/{id}")
  public Object delete(@PathVariable Long id, @RequestParam long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 岗位范围内详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 本人或主办方登记报名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/entries")
  public Object register(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.register(id, b);
  }

  /** 本人或主办方撤回有效报名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/entries/{entryId}/withdraw")
  public Object withdraw(
      @PathVariable Long id, @PathVariable Long entryId, @RequestBody Map<String, Object> b) {
    return service.withdraw(id, entryId, b);
  }

  /** 幂等赛事状态和比赛命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/events/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return service.command(id, action, b);
  }

  /** 本人报名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/mine")
  public Object mine() {
    return service.mine();
  }

  /** 本人指派裁判场次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/referee")
  public Object referee() {
    return service.refereeDesk();
  }

  /** 赛季积分与指标。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return service.reports();
  }

  /** 授权操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** CSV报告仅含比赛公开字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/events/{id}/report.csv")
  public ResponseEntity<String> report(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tournament-" + id + ".csv")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.export(id));
  }

  /** 场地、赛季目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/masters/{type}")
  public Object masters(@PathVariable String type) {
    return service.masters(type);
  }

  /** 新增本组织目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/masters/{type}")
  public Object newMaster(@PathVariable String type, @RequestBody Map<String, Object> b) {
    return service.master(type, null, b);
  }

  /** 修改本组织目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/masters/{type}/{id}")
  public Object saveMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.master(type, id, b);
  }

  /** 删除未引用目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/masters/{type}/{id}")
  public Object deleteMaster(
      @PathVariable String type, @PathVariable Long id, @RequestParam long version) {
    service.deleteMaster(type, id, version);
    return Map.of("ok", true);
  }

  /** 账号表单所需组织和角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/options")
  public Object adminOptions() {
    return admin.options();
  }

  /** 后台目录读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object admin(@PathVariable String type) {
    return admin.read(type);
  }

  /** 新增后台目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object addAdmin(@PathVariable String type, @RequestBody Map<String, Object> b) {
    return admin.save(type, null, b);
  }

  /** 后台目录版本更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(type, id, b);
  }

  /** 匿名仅可读已发布赛事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/events")
  public Object publicEvents(@RequestParam(defaultValue = "0") int page) {
    return service.publicList(page);
  }

  /** 匿名详情不返回负责人账号或审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/public/events/{id}")
  public Object publicEvent(@PathVariable Long id) {
    return service.publicDetail(id);
  }
}
