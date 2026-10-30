# Atlas 生产安装验收报告（2026-10-30）

**结论：当前 main 不通过生产安装验收；PR #22 的修复候选通过本报告覆盖的三种安装模式、HTTPS 浏览器流程和整机重启验证。PR 尚未合并，不能将候选结果表述为 main 已通过。**

- main 基线：`654bffd77ce4b71a949344607d361db3b1f269ba`，收尾时再次查询远程 main，未变化。
- 修复 PR：[wwxst/atlas-console#22](https://github.com/wwxst/atlas-console/pull/22)。仅向修复分支提交，没有直接写 main、强推或更改分支保护设置。
- 三种完整业务验收环境的构建提交：`086fc4b277c5213f0d8ddc890ab54a4daafa897e`。此后提交增加验收工具并校正测试预期/API 文档，没有修改已部署的应用行为。
- 最终全新交互安装复验提交：`3d7f34e4ab1aa7ad5fdc22ca9574b5f30c065207`，安装退出码 0、四项健康检查、Flyway、登录和认证 API 均通过。
- 日期按 UTC 记录在 JSON 中；操作人员时区为 Asia/Shanghai。检查计数为验收工具中的检查点，包含场景汇总，不等于互不重叠的业务功能数量。

## 环境与隔离边界

验收服务器为 Ubuntu 22.04，约 3.8 GiB 内存、2 GiB swap；宝塔已安装。首次盘点没有现有站点、Docker 容器或应用数据库。Docker Engine 29.9.2 / Compose 5.6.1 由首次安装路径安装；另安装宝塔 Nginx 1.28.3 和 MySQL 8.4.13。

每种模式使用独立空应用环境：目录、Compose project、网络、Redis/上传卷、Docker MySQL 卷或宝塔专用库/用户均隔离。它们共享宿主系统、Docker daemon 和镜像缓存，**不是三台独立新系统或三次系统快照恢复**。自动安装 Docker 依赖的真实新机路径执行了一次；后续环境验证已装依赖的自动检测路径。

| 模式 | 安装目录 / project | 独立网段 | 本地前端 | 宝塔 HTTPS 端口 |
| --- | --- | --- | --- | --- |
| 交互 + Docker MySQL | `/opt/atlas-acceptance-docker` / `atlas-acceptance-docker` | `172.30.81.0/24` | `127.0.0.1:8088` | 443 |
| 交互 + 宝塔 MySQL | `/opt/atlas-acceptance-external` / `atlas-acceptance-external` | `172.30.82.0/24` | `127.0.0.1:8089` | 8443 |
| 非交互 + Docker MySQL | `/opt/atlas-acceptance-unattended` / `atlas-acceptance-unattended` | `172.30.83.0/24` | `127.0.0.1:8090` | 9443 |
| 最终交互安装补验 | `/opt/atlas-acceptance-docker-fresh` / `atlas-acceptance-docker-fresh` | `172.30.84.0/24` | `127.0.0.1:8091` | 未公开 |

前三种环境当前保留运行，共 11 个健康容器。第四种补验完成后仅停止其四个容器以释放内存，容器与数据卷均保留；该补验不计入前三种环境的重启成绩。

没有删除现有数据库、Docker 数据卷或失败现场，没有执行 `down -v`、全局 prune、生产数据覆盖或 Flyway 强制 baseline。口令和原始运行日志留在服务器 root 专属目录 `/root/atlas-acceptance-20261030`；提交的 JSON 不包含口令、令牌或完整敏感响应。

## 当前 main 的结果

原始交互 Docker、非交互 Docker、交互外部 MySQL 安装均复现：root 克隆代码后交给 `atlas-deploy` 运行发布，Git 因所有权不匹配拒绝读取，报 `fatal: detected dubious ownership in repository at '/opt/atlas/atlas-console'`，退出码 128。

非交互和外部 MySQL 的原始脚本在独立 mount namespace 中将各自空目录绑定到固定 `/opt/atlas`，避免覆盖首次失败目录。失败发生在构建/启动前，不把后续阶段记为通过。

| main 检查项 | 交互 Docker | 交互宝塔 MySQL | 非交互 Docker |
| --- | --- | --- | --- |
| 原始安装脚本执行 | **失败：Git 所有权** | **失败：Git 所有权** | **失败：Git 所有权** |
| 由原始安装产生的镜像、迁移、业务和重启 | 未测试：安装被阻断 | 未测试：安装被阻断 | 未测试：安装被阻断 |

基线失败摘要见 [verification-summary.json](evidence/verification-summary.json)。保留原始脚本失败日志，未通过手动改数据库掩盖失败。

## 修复候选验收矩阵

“通过”仅适用于本次环境、列出的提交与已实现功能；外部集成限制另列。

| 验收项 | 交互 Docker | 交互宝塔 MySQL | 非交互 Docker |
| --- | --- | --- | --- |
| 安装脚本及镜像构建 | 通过；失败现场安全重试，另做最终全新安装退出 0 | 通过，完整交互安装 | 通过，非交互退出 0 |
| MySQL 启动、连接、专用库 | 通过 | 通过，宿主私网监听与来源授权 | 通过 |
| Flyway 自动迁移 | 通过，V1 success=1 | 通过，V1 success=1 | 通过，V1 success=1 |
| Redis / Spring Boot / 前端健康 | 通过 | 通过 | 通过 |
| HTTPS 登录页、真实管理员登录 | 通过 | 通过 | 通过 |
| 登录后菜单、已实现页面/API | 通过，153 个 API 检查点 | 通过，153 个 API 检查点 | 通过，153 个 API 检查点 |
| 深层路径刷新、退出、重新登录 | 通过 | 通过 | 通过 |
| 重复首次安装不修改现有配置 | 通过：写入前拒绝 | 通过：写入前拒绝 | 通过：写入前拒绝 |
| 整机重启自动恢复 | 通过 | 通过，含宝塔 MySQL 启动依赖 | 通过 |
| 重启后数据库、文件、Redis、配置保留 | 通过 | 通过 | 通过 |

API 合计 **459 个通过，0 失败**；每种模式另有 5 个明确未测试条目。API 工具中的“重启”“占位 UI”由独立重启和浏览器检查补充，原始 JSON 保持原样。其余外部集成仍未测试。

真实 Chromium（Microsoft Edge）无头浏览器通过 Playwright 访问公网 IP HTTPS，未模拟 API、未忽略证书错误。每种模式重启前 13 项、重启后 13 项，共 **78 项通过**，覆盖五个菜单及刷新、存储配置标签、版本对话框、个人中心占位路由、退出保护和再次登录回到原受保护页面。桌面浏览器控制通道连接失败，因此没有宣称人工桌面操作通过；真实浏览器引擎验收由独立无头会话完成。

- [Docker API](evidence/api-docker-retry.json)、[宝塔 MySQL API](evidence/api-external-retry.json)、[非交互 API](evidence/api-unattended-retry.json)
- [重启前浏览器](evidence/browser-before.json)、[重启后浏览器](evidence/browser-after.json)
- [最终全新安装补验](evidence/final-fresh-smoke.json)

## 缺陷、复现和修复

| 问题与复现 | 根因 | 修复及回归证据 |
| --- | --- | --- |
| 原始 main 三种首次安装退出 128 | root 拥有 checkout，发布用户执行 Git 时触发安全目录检查 | Git 调用只显式信任当前 checkout，不写全局通配信任；真实所有权回归和三种实际部署通过 |
| 第一轮修复部署从 `/root` 启动仍失败 | Compose v5 继承了 `atlas-deploy` 无权进入的工作目录 | release 进入已验证的 project_root；Linux root→普通用户、不可读 cwd 回归通过；保留原 `.env` 安全重试成功 |
| 密码含引号/美元符号等不能可靠进入容器，交互输入丢失边缘空白 | dotenv 引号/插值规则与 Shell 不同；默认 read 的 IFS 裁剪空白 | 专用 dotenv 序列化，美元符号按 Compose 规则转义；`IFS= read -r -s`；真实 PTY 字节比较和真实 Compose 容器字节往返通过 |
| 多次验收可能碰撞默认目录、project、端口、网段或已有卷 | 安装身份原先固定，缺少完整写入前检查 | 增加隔离参数及资源/端口/路由预检；真实端口占用和路由重叠拒绝，不留部分安装；重复安装的配置哈希不变 |
| 外部 Shell 环境可能覆盖私有 env 的 Compose 身份 | Compose 优先读取进程环境变量 | 显式 project-name，验证安装身份并清除部署配置同名环境覆盖；行为回归验证，不将模拟测试计为真实部署 |
| HTTPS 协议与真实 IP 在两层代理间传递不可靠 | 容器看到的是内部 HTTP；未经验证的转发头可能伪造来源 | 仅信任指定网关；合法 http/https 协议转发，宝塔覆盖客户端头；真实 Nginx `/api`、`/files` 和公网宝塔伪造头检查通过 |

回归脚本随 PR 提交：`deploy/test-installer.sh`、`test-release.sh`、`test-install-config.sh`、`test-nginx-forwarded-ip.sh` 及其辅助脚本。安装器/发布器行为测试包含模拟依赖；密码往返、Nginx、真实安装、API、浏览器及重启使用真实服务，分别记账。

API 首轮曾有 4 个失败检查（包含场景汇总），根因是验收工具沿用了旧的令牌契约：重复退出、禁用后的旧访问令牌应立即被拒绝。依据现有验证器和安全测试校正测试/API 文档，没有放宽服务端安全行为。旧 `backend/scripts/verify_backend.py` 仍含过时断言，本次没有运行或宣称该旧脚本通过；新增独立验收脚本负责此报告中的实时 API 检查。

浏览器工具最初两轮因异步标签切换等待、重新登录返回原受保护页面的测试预期错误失败；修正等待和预期后，完整重跑前后两轮均通过。真实 IP 第一轮取证超过 Redis 60 秒窗口，后改为请求后立即取证，三种环境都验证实际来源计数存在、伪造来源计数不存在。原始失败证据保留，未将初轮失败计为最终通过。

## 宝塔 MySQL、反向代理与网络

实际 `host.docker.internal:host-gateway` 解析为 `172.17.0.1`。MySQL 只监听 `127.0.0.1,172.17.0.1`，关闭未使用的 X Protocol；防火墙仅允许外部模式的 `172.30.82.0/24` 到宿主私网 3306。专用用户仅有 `atlas_acceptance_external.*` 权限，来源为 `172.30.82.0/255.255.255.0`，不授予 `%` 或全局权限。真实认证和查询成功，读取 `mysql.user` 被拒绝。

外部数据库真实口令含单引号、双引号、`$`、`#`、反斜线和首尾空格，完整安装、Flyway 与业务 API 均成功。额外容器字节往返还覆盖制表符等边缘输入，不在报告泄露口令。

MySQL systemd 增加对 Docker 的 `Wants`/`After` 依赖，保证绑定的 Docker 网桥先出现。宝塔二进制快速安装完成；最初源码安装因可用内存不足被官方脚本拒绝，改用宝塔提供的快速安装路径，没有绕过其内存检查。Nginx 首次安装进程最初未由 systemd 接管，按服务脚本先正常停止再由 systemd 启动并启用自启，未修改宝塔供应商脚本。

宝塔根路径代理到各自回环端口，保留 `/api/`、`/files/`，覆盖 Host、X-Forwarded-Proto、X-Real-IP、X-Forwarded-For。浏览器同源请求和深层刷新通过，无跨域、混合内容或已认证 API HTTP 错误。真实公网请求携带伪造 IP/协议头时，后端限流计数使用实际来源，伪造来源计数不存在。

无域名，使用服务器 IP 证书。浏览器按正常信任链验证成功；证书为 Let's Encrypt IP 短期证书，有效窗口为 2026-10-30 00:34 UTC 至 2026-11-05 16:34 UTC。此结果不证明长期自动续期已通过，必须按短期证书周期管理续期。

公网探测：443/8443/9443 可达；3306/6379/8080/8088/8089/8090 不可达。Docker 检查确认 MySQL/Redis/backend 没有宿主端口映射，前端仅映射回环。见 [网络边界](evidence/network-boundary.json)、[真实冲突预检](evidence/real-preflight.json)、[重启后安全检查](evidence/post-reboot-safety.json)。

## 整机重启证据

使用真实 `systemctl reboot`，不是容器重启。SSH 曾断开并在约 45 秒后恢复；没有手动执行 compose up 帮助恢复。

- 重启前 boot ID：`994040cf-76cb-4176-b69d-78ea5e6acf48`
- 重启后 boot ID：`f51ae7f4-bf22-43a6-82f7-e3c51d6af04f`
- Docker、宝塔 Nginx、宝塔 MySQL 均已启用自启且恢复 active；三个环境的 11 个原容器全部 healthy、策略为 unless-stopped。
- 前后容器 ID、命名卷身份一致；每库普通用户/系统用户哨兵仍在；上传头像 SHA256、Redis AOF 哨兵、`.env` SHA256 和 Flyway 历史均一致；重新登录和全部浏览器流程通过。

见 [重启前记录](evidence/restart-before.json)、[重启后记录](evidence/restart-after.json)。这些哨兵为本次新建测试数据，保留以便复核，没有覆盖既有业务记录。

## 自动化与 CI

- Linux 安装器/发布器回归通过；真实 Compose 密码往返及真实 Nginx 转发回归通过。[执行摘要](evidence/verification-summary.json)
- 前端单测 41/41、lint、构建通过；工作流约束测试 12/12；实时 API 工具自测 11/11。工具自测和模拟行为不计入 459 项真实 API 结果。
- 候选提交 `3d7f34e4ab1aa7ad5fdc22ca9574b5f30c065207` 的四项 GitHub checks 全部成功：[backend verify](https://github.com/wwxst/atlas-console/actions/runs/40993576523)、[validate / browser-e2e / deployment-config](https://github.com/wwxst/atlas-console/actions/runs/40993576437)。最终报告提交的检查以 PR 最新 checks 为准。

## 未测试、未实现及上线边界

| 项目 | 状态及原因 |
| --- | --- |
| 真正 OSS 上传/签名资源访问 | 未测试，没有提供外部云凭据；本地文件上传/读取通过 |
| 真正短信、邮件投递 | 未测试；开发发送器和私有 Redis 验证码只验证 API 流程 |
| GitHub 自托管 Runner 的在线发布/回滚 | 未测试，未配置生产 Runner；脚本回滚回归通过，不等同生产发布演练 |
| 域名 DNS、域名证书签发 | 未测试，用户明确只使用 IP；IP HTTPS 已通过 |
| IP 证书自动续期和过期恢复 | 未测试，当前有效证书访问通过，不等于续期通过 |
| 工作台动态业务、个人中心业务 | 当前实现含静态/占位内容；仅路由和渲染通过，不能标记未实现业务通过 |
| 长时间负载、备份还原、灾难恢复、多机高可用 | 未测试，不属于本次已执行安装检查 |
| 修复合并后的 main 再安装 | 未测试，PR 尚未合并；必须在正常评审/CI 合并后重新固定 main SHA 验收 |

本次为安装与已实现功能的验收，不能据此声称所有外部集成或完整生产运维生命周期已通过。四个 Java 环境并行会使本机内存紧张，因此额外补验已停止；本机没有完成容量规划。

## 交付与复核

部署/安全重试/宝塔私网授权/首次登录说明已更新于 [安装说明](../../deploy/installer.md)。登录种子已验证；三个公开验收站点在开放前均已修改管理员默认密码。各站点当前凭据位于服务器 `/root/atlas-acceptance-20261030/admin-{docker,external,unattended}.json`，权限 0600，不提交 Git。

请通过 PR #22 的正常评审流程合并修复，并在合并后的 main SHA 再次执行干净安装验收。此前不发布“当前 main 已满足一键生产部署”的结论。失败现场、三个运行环境、补验已停止的容器及全部数据卷均保留，便于用户复核。
