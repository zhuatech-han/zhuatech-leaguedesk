// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 报名显示名、负责人、审核、签到和冻结种子。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "entry")
public class Entry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "tournament_id", nullable = false)
  public Long tournamentId;

  @Column(name = "account_id", nullable = false)
  public Long accountId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "checked_in", nullable = false)
  public boolean checkedIn;

  @Column(name = "seed", nullable = false)
  public int seed;

  @Column(name = "group_no", nullable = false)
  public int groupNo;

  @Column(name = "note", nullable = false, length = 600)
  public String note;

  @Column(nullable = false)
  public long version = 1;
}
