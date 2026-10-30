# Atlas 生产安装验收报告（2026-10-30）

**结论：当前 main 不通过首次安装验收；PR #22 修复候选的三种全新安装、已实现业务检查、IP HTTPS 和整机重启恢复通过。PR 未合并，不能将候选成绩表述为 main 已通过。**

- 最新 main 基线：`b964cb505994cbfcf16720ea5d4681421326193a`。工作期间 main 从 `654bffd77ce4b71a949344607d361db3b1f269ba` 更新为前后端兄弟目录，修复分支已正常合并并适配该变化，再次执行三种干净安装。收尾查询 main 仍为 b964cb5。
- 最终实际构建、安装、API、浏览器和重启所用候选：`b348300b66d3a7d8cd021855dccdbe64f3a0b0b2`。后续报告提交仅更新文档、证据及浏览器验收工具的依赖定位与自测，不改变已部署应用行为。
- 修复 PR：[wwxst/atlas-console#22](https://github.com/wwxst/atlas-console/pull/22)。没有直接写 main、强推、合并 PR 或更改分支保护设置。
- 最终一轮合计 **459 个真实 API 检查点通过，0 失败；重启前后 78 个真实浏览器检查点通过**。计数包含场景汇总，不代表互不重叠的业务功能数量。早期轮次不累加进最终成绩。

## 环境、隔离与数据保留

服务器为 Ubuntu 22.04，约 3.8 GiB 内存、2 GiB swap。初始盘点没有现有站点、Docker 容器或应用数据库。Docker Engine 29.9.2 / Compose 5.6.1 由首次安装路径安装；另安装宝塔 Nginx 1.28.3 和 MySQL 8.4.13。

每种模式使用独立空目录、Compose project、网络、Redis/上传卷，以及 Docker MySQL 卷或宝塔专用空库/用户。它们共享宿主系统、Docker daemon 和镜像缓存，**不是三台独立新系统或三次系统快照恢复**。真实新机自动安装 Docker 依赖的路径执行一次；后续环境验证已装依赖的检测路径。

| 最终模式 | 安装目录 / Compose project | 网段 | 回环前端 | 宝塔 HTTPS |
| --- | --- | --- | --- | --- |
| 交互 + Docker MySQL | `/opt/atlas-acceptance-final-docker` / `atlas-acceptance-final-docker` | `172.30.90.0/24` | `127.0.0.1:19088` | 443 |
| 交互 + 宝塔 MySQL | `/opt/atlas-acceptance-final-external` / `atlas-acceptance-final-external` | `172.30.91.0/24` | `127.0.0.1:19089` | 8443 |
| 非交互 + Docker MySQL | `/opt/atlas-acceptance-final-unattended` / `atlas-acceptance-final-unattended` | `172.30.92.0/24` | `127.0.0.1:19090` | 9443 |

当前只有最终三个环境的 11 个容器运行。此前多轮测试环境、失败现场、数据库和数据卷保留，旧容器停止以控制内存；没有执行数据库删除、volume rm、down -v、全局 prune、数据覆盖或未经核实的 Flyway baseline。临时无卷回归容器只清理本次创建的容器 ID。

口令和原始运行日志在服务器 root 专属目录 `/root/atlas-acceptance-20261030`，不提交 Git。公开证据为筛选后的 JSON，不含口令、令牌或完整敏感响应。最终安装的 SHA、退出码、改密和配置哈希见 [latest-bootstrap.json](evidence/latest-bootstrap.json)。

## 当前 main 的实际失败

最新 b964cb5 的三种原始安装均重新运行：root 克隆 checkout，交给 atlas-deploy 运行发布后，Git 拒绝读取 root 所有的仓库，报 `fatal: detected dubious ownership in repository at '/opt/atlas/atlas-console'`，退出码 **128**。构建和数据库连接尚未开始。

三种基线使用独立目录；在独立 mount namespace 中将各自目录绑定到脚本固定的 `/opt/atlas`，不覆盖初次失败现场。外部数据库使用另建的空测试库和专用用户。见 [最新 main 基线](evidence/latest-main-baseline.json)。

| main 项目 | 交互 Docker | 交互宝塔 MySQL | 非交互 Docker |
| --- | --- | --- | --- |
| 首次安装 | **失败：Git 所有权，128** | **失败：Git 所有权，128** | **失败：Git 所有权，128** |
| 由该原始安装产生的构建、迁移、业务、重启 | 未测试：安装被阻断 | 未测试：安装被阻断 | 未测试：安装被阻断 |

## 最终修复候选矩阵

以下仅针对 b348300、上述最终环境与已实现功能。外部集成及未实现功能另列，不把未测试记为通过。

| 验收项 | 交互 Docker | 交互宝塔 MySQL | 非交互 Docker |
| --- | --- | --- | --- |
| 全新安装脚本退出码 | 通过：0 | 通过：0，真实交互输入特殊字符密码 | 通过：0 |
| 前后端及 Docker MySQL 镜像构建 | 通过 | 通过，外部模式不构建 MySQL | 通过 |
| MySQL 启动、连接与权限 | 通过 | 通过：宝塔私网监听、专用库/来源授权 | 通过 |
| Flyway 自动迁移 | 通过：V1 success=1 | 通过：V1 success=1 | 通过：V1 success=1 |
| Redis / Spring Boot / 前端健康 | 通过 | 通过 | 通过 |
| 登录页、静态 SVG、真实登录 | 通过 | 通过 | 通过 |
| 已实现业务 API | 153 通过 / 0 失败 | 153 通过 / 0 失败 | 153 通过 / 0 失败 |
| 菜单、深层刷新、退出、再次登录 | 通过 | 通过 | 通过 |
| 重复首次安装不破坏数据/配置 | 通过：写入前拒绝，配置哈希不变 | 通过 | 通过 |
| 整机重启自动恢复及持久化 | 通过 | 通过，含宝塔 MySQL 依赖 | 通过 |

API 覆盖系统/普通用户认证、令牌刷新与撤销、权限、分页查询、用户生命周期、状态/密码变更、验证码/联系方式流程、配置 API 和本地头像上传读取；只清理本轮新建的临时系统账号，保留标记测试用户/文件作为持久化哨兵，不清空 Redis 或数据库。

每种 API JSON 有 5 个未测试条目：重启与占位 UI 由独立证据补充，真实 OSS、短信/邮件及生产 Runner 集成仍未测试。原始工具结果保留，不改写工具的未测试计数。

浏览器使用真实 Chromium（Microsoft Edge）无头会话和正常 HTTPS 信任链，没有模拟 API 或忽略证书错误。每种模式重启前后各 13 项：五个菜单及深层刷新、存储标签、版本对话框、个人中心占位路由、退出保护、再次登录返回受保护页面，以及请求同源/HTTPS、console.error、requestfailed 和已认证 API HTTP 错误检查。桌面控制通道超时，未声称人工桌面验收；独立无头浏览器证据是真实运行结果。

- [Docker API](evidence/api-docker-retry.json)、[宝塔 MySQL API](evidence/api-external-retry.json)、[非交互 API](evidence/api-unattended-retry.json)
- [重启前浏览器](evidence/browser-before.json)、[重启后浏览器](evidence/browser-after.json)、[最终 Docker 安装补查](evidence/final-fresh-smoke.json)

## 缺陷、复现、根因与修复

| 复现问题 | 根因 | 修复及自动化验证 |
| --- | --- | --- |
| main 三种首次安装退出 128 | 发布用户读取 root 所有的 checkout 触发 Git 安全检查 | Git 调用仅信任当前精确 checkout；真实所有权回归及最终三种全新安装通过，不配置全局通配信任 |
| 早期候选从 `/root` 启动时 Compose v5 失败 | 降权后继承不可进入的 cwd | release 进入已验证 project_root；真实 Linux runuser/不可读 cwd 回归通过，原 env 安全重试通过 |
| 引号、美元符号等密码转义错误，首尾空白丢失 | dotenv 与 Shell 引号/插值规则不同，read 默认 IFS 裁剪空白 | 专用 dotenv 序列化、Compose 美元转义、IFS= read -r -s；真实 PTY 和容器环境逐字节往返通过 |
| 默认目录/project/端口/网络/卷可能碰撞 | 缺少独立安装身份和完整写入前预检 | 隔离参数、资源/端口/路由预检；真实冲突拒绝且无部分安装，重复执行不改配置 |
| 外部进程环境覆盖私有 env | Compose 环境优先级 | 验证安装身份、显式 project-name、清除同名部署变量；行为回归通过 |
| 两层代理的 HTTPS 与真实 IP 不可靠 | 容器看到内部 HTTP，未验证的转发头可能伪造来源 | 只接受指定网关及合法协议，宝塔覆盖客户端头；真实 Nginx 与最终三个公网站点防伪验证通过 |
| favicon.svg / icons.svg 返回 403 | 私有 checkout 的 0640 被带入镜像，Nginx worker 无权读取 | 仅将镜像公开 html 目录设为可读；真实生产镜像回归旧版 RED=403、修复 GREEN=200，最终全部浏览器无控制台错误 |

回归脚本随 PR 提交。`test-installer.sh` / `test-release.sh` 的部分依赖为模拟；`test-install-config.sh`、`test-nginx-forwarded-ip.sh`、`test-frontend-assets.sh` 使用真实容器；真实安装/API/浏览器/重启单独计账。新增资产回归按本次 create 返回的 ID 清理，重名失败不会删除已有容器，另有安全回归覆盖。

早期浏览器仅检查页面与 HTTP 响应，未直接断言同源/console，因此漏掉了静态 SVG 403。增强工具后发现并修复该缺陷，最终 78 项结果全部重跑，不沿用早期较弱结果。工具还拒绝空目标、重复证据标签、零存储标签，并从新 frontend 项目定位 Playwright。

API 首轮旧预期曾将重复退出和禁用后的旧令牌当作可用；依据现有验证器/安全测试校正验收工具及 API 文档，没有放宽服务端安全行为。旧 backend/scripts/verify_backend.py 含过时断言，本次未运行或宣称其通过；本报告的实时 API 由新增脚本验证。

## 网络配置、失败恢复与宝塔问题

宝塔 MySQL 8.4.13 只监听 `127.0.0.1,172.17.0.1`，关闭未使用的 X Protocol。容器 host.docker.internal 实测解析到 172.17.0.1。最终专用用户仅获 `atlas_acceptance_final_external.*` 权限，来源限制为 `172.30.91.0/255.255.255.0`；真实认证成功，读取 mysql.user 被拒绝。防火墙只给对应私网例外，保留公网 3306 拒绝。

真实外部口令含单/双引号、美元符号、井号、反斜线与首尾空格；完整安装、Flyway 和业务通过。另有制表符等真实 PTY/容器字节回归，口令不出现在本报告。

新环境准备时，操作者曾把 UFW 私网允许规则追加到已有拒绝规则之后，导致 MySQL TCP 超时；再次插入同项只提示已存在，没有移动位置。仅将本次新增的精确例外移到前方，保留公网拒绝后，原失败环境安全重试退出 0、env 哈希不变且认证成功。该现场保留停止，随后用正确规则在独立空库完成最终全新外部安装。安装说明已补规则顺序与重复规则注意事项。见 [失败恢复](evidence/external-network-recovery.json)。这是验收环境配置错误，未冒充应用缺陷。

MySQL systemd 增加 Wants/After Docker，确保绑定的 Docker 网桥先出现。宝塔 MySQL 源码安装曾被官方内存检查拒绝，改用官方快速二进制路径，没有绕过检查。Nginx 安装进程按正常服务脚本停止后交由 systemd 启动并启用自启，没有修改供应商脚本。

宝塔根路径分别代理三个回环前端，保留 /api/ 与 /files/，覆盖 Host、X-Forwarded-Proto、X-Real-IP、X-Forwarded-For。最终公网伪造 IP/协议请求触发的后端计数使用实际来源，伪造 IP 无计数；没有请求跨域、HTTP 混合内容或控制台错误。公网 443/8443/9443 可达，MySQL/Redis/backend 及全部验收回环端口不可达；容器元数据确认私有服务无宿主端口映射。见 [网络边界](evidence/network-boundary.json)、[安全检查](evidence/post-reboot-safety.json)、[真实冲突预检](evidence/real-preflight.json)。

没有域名，按用户要求使用 IP HTTPS。有效 Let's Encrypt IP 短期证书的窗口为 **2026-10-30 08:34 至 2026-11-06 00:34（Asia/Shanghai）**；证书自动续期未演练，不将当前访问通过等同长期续期通过。

用户另报宝塔 Docker 页“返回值非对象”红提示。已确认 SDK 能连接 Docker 29，get_config 返回 dict，Docker/Compose 状态均正常。用户完成本人账号绑定并强制刷新后明确确认页面恢复。未降级/重装 Docker、改写宝塔前端或冒称已定位历史异常响应体；直接触发红提示的历史请求响应未留存。

## 最终整机重启与自动化

真实整机 reboot，未手动 compose up 辅助恢复：

- 最终重启前 boot ID：`4d98539e-59a5-4d89-b76f-e2292c208301`
- 最终重启后 boot ID：`3a52a998-8fb0-4e42-a5bb-a31c4147c479`
- Docker、宝塔 Nginx、宝塔 MySQL 已启用自启，重启后 active；11 个原容器 healthy，策略 unless-stopped。
- 容器 ID/卷身份、每库普通用户与系统用户哨兵、上传头像 SHA256、Redis AOF 哨兵、env SHA256、Flyway V1 历史前后相同。重启后的完整浏览器流程通过。

见 [重启前](evidence/restart-before.json)、[重启后](evidence/restart-after.json)、[最终执行摘要](evidence/verification-summary.json)。早期两次重启记录在 root 历史证据中保留，最终成绩只引用上述一轮。

新目录结构下，本地前端 41/41 单测、lint、构建通过；工作流约束 15/15、浏览器工具 CLI 3/3、API 工具自测 11/11；Linux 安装器/发布器回归、真实 Compose 密码、真实 Nginx 转发和生产镜像资产回归全部通过。模拟与工具自测不累加到真实 API 成绩。

b348300 四项 GitHub checks 成功：[validate / browser-e2e / deployment-config](https://github.com/wwxst/atlas-console/actions/runs/41019232053)、[backend verify](https://github.com/wwxst/atlas-console/actions/runs/41019232079)。报告收尾提交的 checks 以 PR 最新状态为准。

## 未测试或未实现

| 项目 | 状态 |
| --- | --- |
| 真实 OSS 上传、签名资源访问 | 未测试，没有外部云凭据；本地文件流程通过 |
| 真实短信、邮件投递 | 未测试；开发发送器与私有 Redis 验证码只验证 API 流程 |
| 生产自托管 Runner 在线发布/回滚 | 未测试，未配置 Runner；脚本回滚回归不等于真实生产演练 |
| 域名 DNS、域名证书签发 | 未测试，用户只使用 IP |
| IP 证书自动续期、过期恢复 | 未测试 |
| 工作台动态业务、个人中心业务 | 现有静态/占位内容，仅路由和渲染通过，不声称未实现业务通过 |
| 长期负载、容量规划、备份还原、灾难恢复、多机高可用 | 未测试 |
| 修复合并后的 main 干净安装 | 未测试，PR 尚未合并；需要正常评审/CI 合并后固定新 main SHA 再验 |

## 交付

[安装说明](../../deploy/installer.md) 已包含一条命令、数据库模式选择、宝塔私网授权/防火墙、反向代理、首次改密、失败安全重试及重启检查。当前三个公开验收站点的默认管理员密码均已修改；凭据位于服务器 `/root/atlas-acceptance-20261030/admin-{docker,external,unattended}.json`，0600，不提交 Git。

修复与回归测试经 PR #22 交付，由正常评审流程决定合并。此前保持“main 不通过、修复候选已通过上述范围”的结论；所有失败现场、旧环境与数据卷保留供复核。
