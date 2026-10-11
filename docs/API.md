# LeagueDesk 接口与状态

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

所有业务API前缀 `/api`。先GET `/auth/csrf` 获取 `{header,token}`，所有POST／PUT／DELETE通过返回头传token，登录后重新获取；浏览器还需携带会话cookie。账号不会自助注册。错误为HTTP状态与 `{code}`，不返回服务端敏感配置。

| 方法与路径 | 用途 |
|---|---|
| POST `/auth/login` | `{username,password}` |
| GET `/auth/me` | 本人菜单、角色、权限和范围 |
| POST `/auth/logout`、`/auth/password` | 退出／旧密码核对后的本人改密 |
| GET `/options` | 范围内启用目录；参赛和裁判不返回账号目录 |
| GET `/events?search=&status=&sort=newest&page=0&size=20` | 范围过滤，检索和分页 |
| POST `/events`、PUT `/events/{id}` | 新建／编辑草稿 |
| DELETE `/events/{id}?version=...` | 删除无报名的草稿 |
| GET `/events/{id}` | 当前岗位允许的详情 |
| POST `/events/{id}/entries` | `{name,note,publicConsent}`；工作人员需accountId，参赛负责人固定本人 |
| POST `/events/{id}/entries/{entryId}/withdraw` | `{version}`，开放报名阶段撤回 |
| POST `/events/{id}/commands/{action}` | 版本与UUID命令，见下表 |
| GET `/mine`、`/referee` | 本人报名／裁判指派 |
| GET `/reports`、`/audit` | 工作人员积分／审计 |
| GET `/events/{id}/report.csv` | 授权下载UTF8比赛CSV |
| GET／POST／PUT／DELETE `/masters/seasons`、`/masters/courts` | 目录；修改／删除指定id与版本，外键保护历史 |
| GET `/admin/options` | 管理目录权限选项 |
| GET／POST／PUT `/admin/{type}` | users、roles、departments、dictionaries、settings、menus；部分系统目录只允许修改，不允许新增 |
| GET `/public/events?page=0` | 匿名已发布赛事，20条一页 |
| GET `/public/events/{id}` | 匿名白名单字段详情；取消发布返回404 |

每个命令需 `{version,requestId}`，requestId为新UUID。相同UUID与相同载荷重试不重复执行；不同载荷409 REQUEST_REUSED。所有事件、报名、比赛版本都必须使用最新查询结果。

| action | 额外字段与规则 |
|---|---|
| OPEN | 草稿开放；冻结参数 |
| PUBLISH | published:boolean，不能发布草稿 |
| REVIEW | entryId、entryVersion、approved、seed(1–10000)；重新审核清除签到 |
| CHECKIN | entryId、entryVersion、checkedIn；仅已通过报名 |
| DRAW | 已审核并签到、种子唯一；至少2人 |
| ADVANCE | 全部小组比赛DONE后生成预设晋级数 |
| SCHEDULE | gameId、gameVersion、courtId、refereeId、startsAt、endsAt；ISO UTC／偏移时间，归一化到微秒 |
| START | gameId、gameVersion；仅本人裁判，开赛前30分钟以内 |
| RESULT | gameId、gameVersion、homeScore、awayScore、resultType；仅本人裁判处理LIVE |
| CORRECT | 同RESULT并需reason；主办方更正DONE，不得影响已开赛下游 |
| FINISH | 全部淘汰结果DONE／BYE后冻结 |
| CANCEL | reason；仍有LIVE不得取消 |

resultType=PLAYED，胜方恰达 `(bestOf+1)/2`、败方低于该数；WALKOVER需winnerId为双方之一，比分0:0。通用请求字段上限以源码 Rules 验证为准。关键错误：401登录无效、403岗位／范围／CSRF拒绝、404不可见、409状态／版本／冲突拒绝、400格式错误、429登录失败限速。
