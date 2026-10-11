// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 比赛双方与晋级来源、排赛、胜局和结果版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "game")
public class Game {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "tournament_id", nullable = false)
  public Long tournamentId;

  @Column(name = "stage", nullable = false, length = 20)
  public String stage;

  @Column(name = "round_no", nullable = false)
  public int roundNo;

  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "group_no", nullable = false)
  public int groupNo;

  @Column(name = "home_id")
  public Long homeId;

  @Column(name = "away_id")
  public Long awayId;

  @Column(name = "source_home_id")
  public Long sourceHomeId;

  @Column(name = "source_away_id")
  public Long sourceAwayId;

  @Column(name = "winner_id")
  public Long winnerId;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "court_id")
  public Long courtId;

  @Column(name = "referee_id")
  public Long refereeId;

  @Column(name = "starts_at")
  public java.time.Instant startsAt;

  @Column(name = "ends_at")
  public java.time.Instant endsAt;

  @Column(name = "home_score", nullable = false)
  public int homeScore;

  @Column(name = "away_score", nullable = false)
  public int awayScore;

  @Column(name = "result_type", nullable = false, length = 20)
  public String resultType;

  @Column(nullable = false)
  public long version = 1;
}
