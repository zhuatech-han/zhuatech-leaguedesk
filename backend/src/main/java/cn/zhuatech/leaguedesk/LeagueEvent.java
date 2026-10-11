// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 不可修改的赛事操作与成绩更正历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "league_event")
public class LeagueEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "tournament_id", nullable = false)
  public Long tournamentId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "details", nullable = false, length = 2000)
  public String details;

  @Column(name = "created_at", nullable = false)
  public java.time.Instant createdAt;
}
