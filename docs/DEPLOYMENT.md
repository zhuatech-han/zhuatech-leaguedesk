# LeagueDesk 部署与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

Compose 默认只将前端8144映射到127.0.0.1，数据库与后端留在项目私有网络。`python3 scripts/init-env.py` 随机生成三组独立密码，并以0600创建 `.env`，已有文件拒绝覆盖。`.env.example` 仅配置名称与默认非敏感端口，不含真实密钥。

执行 README 的部署命令，健康检查等待 MySQL → 后端 → Nginx；若失败，检查 `docker compose -p leaguedesk logs --tail=100`，勿把包含业务资料的日志公开。构建会执行前后端测试，不能用跳过测试掩盖构建失败。升级前备份，在隔离恢复实例验证后再替换业务实例；应用V1/V2迁移文件不可事后修改。

## HTTPS 与公网

正式运行需域名与可信 TLS 证书，宿主 HTTPS 代理转发至 `127.0.0.1:8144`。保持 `BIND_ADDRESS=127.0.0.1`、`COOKIE_SECURE=true`，以 HTTPS 登录。不要让数据库、调试端口或Actuator管理接口广泛暴露。健康接口只返回状态，除health外Actuator端点未公开。

如使用宿主 Nginx：HTTPS server 的 `location /` 转发至 `http://127.0.0.1:8144` 并保持 Host；证书路径由部署者按实际域名配置，不提供虚假证书。服务端不信任任意转发头，会话依靠明确配置的Secure Cookie。配置代理限流、连接超时、磁盘与日志轮转、备份保留和可用性监控。容器内前后端以非root运行，数据库遵守官方镜像权限；本机验收不等于公网安全认证。

## 外部数据库

使用空的专用 MySQL8.4数据库，分配最小应用权限并允许Flyway建表／索引。配置 `DATABASE_URL=jdbc:mysql://db.example.internal:3306/zhuatech_leaguedesk?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowLoadLocalInfile=false&sslMode=VERIFY_IDENTITY`、实际用户与密码；使用可信CA/Java信任库，不能关闭证书校验来绕过错误。该示例域名不是真实服务。内置 Compose 隔离数据库使用SSL `REQUIRED`；外网连接必须验证身份。仓库未验收外部DB证书部署。

备份恢复脚本仅支持内置数据库，检测外部URL后拒绝操作。外部数据库应由其运维系统备份和演练。

## 备份和恢复

备份脚本验证指定项目后停止该后端，使用事务一致性dump，然后无论成功失败均重新启动后端。备份包含数据库、密码散列、报名备注与命令记录；文件0600，只能放到私有位置。ZIP manifest保存大小与SHA256，不等同加密或真实性签名。应另用加密存储、离线副本和最小访问权限。

恢复只接受受信任的本项目备份，检查文件清单／大小／校验和，拒绝已有项目容器、网络或同名卷。SQL具有执行能力，不导入来自陌生人的备份。恢复 `.env` 是私有文件，新端口避免冲突，默认只能localhost绑定。恢复完整数据库包含已有管理员，因此目标 `ADMIN_PASSWORD` 不会覆盖备份中的密码；需要以原账号密码验证恢复。

验收快照由专用空库 `scripts/quality.py` 生成，之后 `scripts/verify-persistence.py` 比较所有赛事、报名、比赛、更正历史和积分。该工具读取本机私有状态文件，不用于陌生数据，也不上传资料。业务运维自行维护恢复验证记录。

只清理明确命名的测试项目：`docker compose -p leaguedesk-test down -v`。不可对业务库加 `-v`；普通停机用 `stop` 或 `down` 保留卷。不执行全局容器／卷清理。
