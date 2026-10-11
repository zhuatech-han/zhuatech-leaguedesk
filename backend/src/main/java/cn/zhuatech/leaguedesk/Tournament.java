// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 赛事冻结的赛制、时间、发布状态及负责人。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "tournament")
public class Tournament {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "season_id", nullable = false)
  public Long seasonId;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "format", nullable = false, length = 20)
  public String format;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "group_count", nullable = false)
  public int groups;

  @Column(name = "advance_count", nullable = false)
  public int advance;

  @Column(name = "best_of", nullable = false)
  public int bestOf;

  @Column(name = "capacity", nullable = false)
  public int capacity;

  @Column(name = "published", nullable = false)
  public boolean published;

  @Column(name = "starts_at", nullable = false)
  public java.time.Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public java.time.Instant endsAt;

  @Column(name = "description", nullable = false, length = 2000)
  public String description;

  @Column(nullable = false)
  public long version = 1;
}
