// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import jakarta.persistence.*;

/** 绑定操作者和载荷的全局幂等命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_stamp")
public class CommandStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "request_id", nullable = false, length = 36)
  public String requestId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;

  @Column(name = "tournament_id", nullable = false)
  public Long tournamentId;
}
