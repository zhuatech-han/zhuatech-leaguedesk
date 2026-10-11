// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 比赛场地及组织归属。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "court")
public class Court {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;

  @Column(nullable = false)
  public long version = 1;
}
