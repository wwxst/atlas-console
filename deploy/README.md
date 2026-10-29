# Atlas Console 一键部署：首次上线与后续自动更新

> **首次安装**：新服务器可先运行 [一条命令安装器](installer.md)。安装器负责 Docker 与应用首次启动；域名/HTTPS、宝塔反向代理和后续 GitHub Runner 仍需按本文配置。它不会创建云服务器、购买域名或自动开放公网端口。

## 架构与边界

- 后台超级管理员（系统用户 ID=1）在「版本与更新」点击一键更新。
- 后端调用 GitHub Actions API，只允许触发 `wwxst/atlas-console` 中固定的 `deploy-production.yml`，引用 `main`。
- GitHub Actions 在挂有 `atlas-production` 标签的**专用自托管 Runner** 上执行。前端和后端测试都检出本次工作流的同一个 `github.sha`；后端位于 `backend/` 子目录。生产 Runner 只检出这一份已验证的仓库提交，Docker Compose 从根目录前端和 `backend/` 构建镜像，并更新 Nginx、Spring Boot、MySQL 和 Redis。
- 前端查看 Actions 的最近执行状态，**不会直接执行 Shell、传递自定义分支或把 GitHub Token 暴露到浏览器**。
- 默认禁用自动更新。没有服务器、Runner 和凭据时，按钮保持禁用。
- **业务数据持久化**：MySQL、Redis、上传目录使用 Docker 命名卷；重启容器不会清空数据。但**更新不等于备份**，操作前必须另行备份这些卷。

## 真实客户端 IP 与登录限流（生产必读）

生产链路为：**外部访客 → 主机 HTTPS 反向代理 → Docker 网桥网关（172.30.80.1）→ Nginx（固定 172.30.80.10）→ Spring Boot**。

- Compose 专用网段为 `172.30.80.0/24`，网关为 `172.30.80.1`，Nginx 使用固定地址 `172.30.80.10`。后端仅配置 `TRUSTED_PROXY_ADDRESSES=172.30.80.10`；本地开发不配置时完全不信任 `X-Forwarded-For`。如果主机网段与此地址冲突，**必须同时调整 compose、nginx.conf 和可信代理地址**，不能只更改一项。
- 主机 HTTPS 反向代理**必须覆盖**请求里的 `X-Forwarded-For` 为真实客户端单一 IP，不能追加用户自己传入的同名 Header。例如 Nginx 边缘代理在明确控制好接入链时使用 `proxy_set_header X-Forwarded-For $remote_addr;`。如果边缘代理前还有 CDN，必须由边缘代理验证 CDN 来源并采用受信任的真实 IP 恢复规则，禁止盲目信任任意 Header。
- 容器内的 Nginx 只信任来自 Docker 网关 `172.30.80.1` 的 `X-Forwarded-For`，恢复出客户端地址后**重新生成单一** `X-Forwarded-For` 给后端。后端只在 TCP 对端恰好为 `172.30.80.10` 且收到合法的单一 IP 时才使用该值，其余场景使用 `request.getRemoteAddr()`。
- 主机到容器端口保持 `127.0.0.1:8088`，不能直接公开到全网，否则客户端可能直接绕过负责清理转发头的主机 HTTPS 代理。不要把 Spring Boot 8080 端口公开到 Docker 网段以外。
- GitHub Actions 的 `deployment-config` 作业会启动**真实 Nginx 容器**，验证两个不同来源 IP 经代理后仍然不同，并验证一个非可信容器伪造 `X-Forwarded-For` 时会被覆盖。Spring Boot 的单元/MockMvc 测试验证可信代理识别和 Redis IP 限流 key 隔离。首次部署时仍需使用实际域名和真实 TLS 代理做端到端演练。

## 首次上线：必须人工完成一次

推荐先阅读 [一条命令安装指南](installer.md)。如果需要手动安装或排障，再按以下步骤执行。

1. 准备一台 Linux 生产服务器、域名、有效的 HTTPS 证书以及安全的网络策略。建议运行 Docker Engine + Docker Compose v2；将公网流量交给独立的 TLS 反向代理（Caddy/Nginx），只转发到本机 `127.0.0.1:8088`。数据库和 Redis 没有对外发布端口。
2. 在服务器建立专用非 root 部署账号，安装 Docker 和 GitHub Actions Runner。Runner 必须注册到 `wwxst/atlas-console`，并添加 `atlas-production` 标签。按照 GitHub 自托管 Runner 官方步骤将 Runner 作为受控服务运行。**Runner 的 Docker 权限具有接近 root 的风险，生产 Runner 不得执行不受信任的 PR 代码。请设置分支保护、审查工作流变更，并限制仓库写入权限。**
3. 创建 `/opt/atlas/.env`（参考本目录 `env.example`），设置 **独立的** MySQL root 密码和业务密码；用 `openssl rand -base64 48` 生成 JWT_SECRET，用 `openssl rand -base64 32` 生成 STORAGE_ENCRYPTION_KEY。只允许 Runner 的部署账号读取此文件，权限设为 `0600`，**不要提交到 GitHub**。
4. 在 GitHub 仓库的 Actions 设置中允许执行工作流，创建名为 `production` 的 Environment，可选配置 required reviewers。工作流只允许从 `main` 引用执行。仓库 `main` 必须受代码审查和 CI 约束。
5. 首次部署：通过 GitHub Actions 页面手动运行 `Deploy production`，输入任意非空的请求标识（例如 `bootstrap-1`）。工作流会在 Runner 上检出一个仓库，分别构建前端和 `backend/` 后端镜像，启动容器并验证健康状态；之后再确认外部域名和 HTTPS。**没有 Runner 时会一直排队，不能视为部署成功。**
6. **数据库 schema 由 Flyway 版本迁移管理**：全新数据库会在后端首次启动时执行 V1 创建表和默认配置，不再通过 MySQL 容器导入 `web_project.sql`。首次公网开放前必须修改默认系统管理员密码。
7. 如果复用由旧版 `web_project.sql` 初始化的非空数据库，必须先备份并核对 schema，然后按 [后端数据库迁移指南](https://github.com/wwxst/atlas-console/blob/main/backend/docs/database-migrations.md) 临时设置 `ATLAS_FLYWAY_BASELINE_ON_MIGRATE=true` 完成一次 baseline，确认健康后恢复为 `false`。**不要对未核实的数据库启用 baseline。**
8. 首次访问成功后，创建一个只访问 `wwxst/atlas-console` 的 **GitHub fine-grained PAT**，仅授予仓库 Actions 的读写权限，不授予代码写入、Secrets 或 Administration 权限。把它写入服务器 `/opt/atlas/.env` 的 `ATLAS_DEPLOYMENT_GITHUB_TOKEN`，设置 `ATLAS_DEPLOYMENT_ENABLED=true`，再通过受控部署流程重启 backend 容器。这样后台的一键更新按钮才会启用。

## 数据库迁移与应用回滚边界

- 新安装时，MySQL 只创建空的 `web_project` 数据库；后端启动时 Flyway 执行 `V1__initial_schema.sql`。CI 和发布前验证任务都在真实 MySQL 8.4 空数据库上运行相同迁移。
- 已发布的 Flyway SQL 不可修改。后续 schema 变化只能添加新的版本文件，并优先采用向前兼容的 expand/contract 迁移。
- 对已有数据库，默认 `ATLAS_FLYWAY_BASELINE_ON_MIGRATE=false`。只有先完成备份、确认表/列/索引/约束与 V1 相符后，才可临时打开 baseline；Flyway baseline **不会自动比较或修复完整 DDL**。
- Flyway migration 在后端启动时运行。迁移失败会阻止后端健康检查通过，因此发布脚本不会记录新应用版本为成功；但应用镜像回滚**不会回滚已提交的数据库 DDL 或数据**。迁移必须兼容当前版本和可回滚的旧应用版本。
- 真实数据库备份恢复和现有库 baseline 演练仍需在独立测试服务器完成；CI 成功不等于真实服务器验证成功。完整操作步骤见 [后端数据库迁移指南](https://github.com/wwxst/atlas-console/blob/main/backend/docs/database-migrations.md)。

## 后续的「一键更新」

1. 将前端、后端代码经 PR 和 CI 合并到各自 `main`。
2. 使用 ID=1 的超级管理员登录 Atlas Console，打开「版本与更新」。
3. 点击「一键自动更新」并确认。后台固定触发 GitHub Actions。前端和后端验证任务都使用同一个工作流 SHA；生产 Runner 在构建前再次校验两者与仓库检出的提交完全一致。单仓库部署要求前后端 SHA 相同，若缺失或不匹配则拒绝部署。
4. 在弹窗或 GitHub Actions 页查看排队、运行、成功、失败状态。显示成功后自行刷新页面以加载前端资源。**刷新页面本身不部署代码**。


## GitHub Actions 请求跟踪、超时和人工恢复

- 管理员提交部署或回滚时，后端先在 Redis 保存 **请求 ID**（保存 7 天），再向固定的 GitHub 工作流发送 `workflow_dispatch`。GitHub 的 dispatch API 只返回 `204`，**不会直接返回 Run ID**；后端根据工作流 `run-name` 精确匹配请求 ID，找到任务后保存 Run ID，以后直接按 `/actions/runs/{id}` 查询。后续出现其他更晚的执行记录不影响当前请求状态。
- GitHub 在提交后的 **5 分钟内仍未创建可查询的任务**时，后台显示 `timed_out`，不再无限等待；已关联的任务**排队或执行超过 1 小时**会显示 `stalled`。任务记录被删除或无法找到时显示 `run_missing`。这些是**需要人工处理的状态，不代表 GitHub 已取消或部署失败**。
- 超时后，超级管理员在「版本与更新」中点击「核实并恢复更新状态」。后端必须再次查询 GitHub：只要还有排队或运行中的工作流，就**拒绝清除记录**，并要求管理员先在 GitHub Actions 中确认及取消任务。只有确认没有活动任务后，才清除 Redis 的过期请求状态。
- 刷新浏览器或者退出重新登录不会依靠前端本地变量维持等待锁；界面以服务端返回的状态、请求 ID、Run ID 和明确终态为准。GitHub API 不可用时，界面显示查询失败并允许重试，**不会自动视为成功或允许跳过部署检查**。
- GitHub API 执行记录、Redis 跟踪和前端页面的 CI 目前由模拟环境验证。首次生产上线后，还需要按 [独立联调跟踪事项](https://github.com/wwxst/atlas-console/issues/16) 完成真实容器及 Runner 演练。

### 排队、并发锁与无法取消的排查

先区分 **等待 Runner 的 queued job** 和 **等待并发锁的 pending run**。下面命令需要已登录 GitHub CLI；查看 Runner 还需要仓库管理权限，不要为此扩大后台部署 PAT 的权限。

```sh
# 必须存在 online 且空闲、同时具有全部四个标签的专用 Linux x64 Runner。
gh api repos/wwxst/atlas-console/actions/runners \
  --jq '{total_count, runners: [.runners[] | {name, status, busy, labels: [.labels[].name]}]}'

# 用实际 Run ID 替换；检查 deploy 是否 queued、runner_id 是否为 0、steps 是否为空。
gh api repos/wwxst/atlas-console/actions/runs/RUN_ID/jobs \
  --jq '.jobs[] | {name, status, conclusion, runner_id, labels, steps}'
gh api repos/wwxst/atlas-console/actions/runs/RUN_ID \
  --jq '{id, status, conclusion, head_sha, updated_at}'
```

- `runs-on: [self-hosted, linux, x64, atlas-production]` 要求标签**全部匹配**。`total_count: 0` 表示没有注册的 Runner，并非仅仅离线；已有 Runner 则检查服务、联网、`status`、`busy` 和标签。准备服务器后，从仓库 **Settings → Actions → Runners → New self-hosted runner** 注册专用 Runner，并作为服务运行。不要给普通开发机随意添加生产标签来消除排队。
- 工作流的 `atlas-production` 并发组使用 `cancel-in-progress: false`，避免新发布中断正在修改服务的旧发布。旧 run 等待 Runner 时仍占着锁，后续 run 会 `pending`，甚至尚未创建 job。不要改并发组名称、添加 Run ID 或打开自动取消来绕过锁；恢复 Runner 前先处理已有队列，以免积压的部署自动开始。
- 部署 job 使用 `${{ !cancelled() && ... }}`，既允许回滚跳过验证 job，也允许取消传播。不要恢复 `always()`，也不要直接删除状态函数：后者会让回滚因依赖 job 被跳过而无法执行。job 的 `timeout-minutes` 只限制执行时间，不能解决没有 Runner 的排队问题。

**安全取消顺序**：先核对队列中每个请求的意图。若本次只排障、不部署，先取消尚未开始的后续请求，再取消占锁的旧请求，避免释放锁后后续请求自动部署。确认 `deploy` 尚未分配 Runner、没有执行步骤后，尝试普通取消；请求接受后再次读取状态：

```sh
gh api --method POST repos/wwxst/atlas-console/actions/runs/RUN_ID/cancel
gh api repos/wwxst/atlas-console/actions/runs/RUN_ID --jq '{status, conclusion}'

# 仅在普通取消未生效，且已确认可以安全中止时使用。
gh api --method POST repos/wwxst/atlas-console/actions/runs/RUN_ID/force-cancel
gh api repos/wwxst/atlas-console/actions/runs/RUN_ID --jq '{status, conclusion}'
```

`202 Accepted` 只表示接受请求，必须确认最终为 `completed / cancelled`。如果部署已开始，先核实服务器状态和恢复方案，不能将取消视为应用回滚或数据库回滚。若强制取消后仍长期不结束，或接口返回 `409/500`，保存 Run ID、job 状态及响应的 `X-GitHub-Request-Id`，联系 GitHub Support；不要反复触发部署。参考 [GitHub 取消故障排查](https://docs.github.com/en/actions/how-tos/troubleshoot-workflows) 和 [强制取消 API](https://docs.github.com/en/rest/actions/workflow-runs#force-cancel-a-workflow-run)。

2026-10-29 排障记录：旧任务 `40837094201` 的前后端验证成功，`deploy` 等待上述四个标签且 `runner_id=0`、`steps=[]`；仓库 Runner 数量为 0。新任务 `40843633702` 为 `pending`、无 job。先普通取消新任务，再普通取消旧任务；旧任务仍排队后强制取消，两者均确认 `completed / cancelled`，没有执行生产部署。恢复 Runner 后应由负责人重新确认并手动发起新的部署，不能重跑这两个旧请求。

## 部署失败、自动回滚与手动回滚

- 每次部署使用同一个已通过前后端检查的仓库提交 SHA，分别构建 `atlas-console:git-<40位SHA>` 和 `atlas-backend:git-<40位SHA>` 镜像；两者的 SHA 相同，**不会覆盖上一版应用镜像标签**。
- 专用 Runner 在服务器 `/opt/atlas/releases/` 保存 `current.env` 和 `previous.env` 两份版本清单（只包含镜像标签，不包含密钥）。首次成功部署只有 current；第二次成功部署开始才有 previous。
- 更新中 Docker Compose 的健康检查或前端 HTTP 检查失败时，发布脚本自动尝试用 current 清单恢复上一套前后端镜像。**即使自动恢复成功，本次 GitHub Actions 仍标记失败**，便于管理员排查。恢复失败会在日志中明确告警。
- 超级管理员在「版本与更新」点击「回滚上一版本」并确认，后端使用固定的 `operation=rollback` 触发同一生产工作流，Runner **不再构建当前代码**，而是验证 previous 清单和镜像存在，恢复该版本并执行健康检查。成功后互换 current/previous，因此还可以再次切换回来。
- **历史版本管理**：每次成功部署都会在 `/opt/atlas/releases/history/` 归档一份镜像清单（文件名为 `<同一40位SHA>_<同一40位SHA>.env`），记录同一仓库提交构建的前后端镜像，并保留原始归档时间。超级管理员可在「版本与更新 → 历史版本」浏览最近 50 条、区分当前版本并选择任意非当前的历史版本。
- **指定历史版本回滚**：后端校验请求的版本 ID 必须属于该服务器真实保存的历史记录，并通过固定的 `operation=rollback-history` 调度生产工作流；Runner 再次检查文件名、文件内容和本机镜像是否匹配，健康检查通过后才更新当前版本与上一版本记录；失败会尝试恢复先前的服务。
- 如果历史镜像已被清理，选择该版本会**安全失败，不会从来源不明的镜像或任意提交重新构建**。恢复功能不保证永久可用：需要规划磁盘容量、镜像归档/保留策略，并监控服务器空间。历史列表仅包含服务器成功部署过的应用版本，不是 GitHub 全部提交记录。

- `deploy.lock` 使用 `flock` 阻止同一服务器同时运行多次发布，GitHub Actions 的 concurrency 也会串行处理任务。
- **仅回滚前端和后端应用镜像，不自动回滚 MySQL/Redis 数据、上传文件、数据库 schema、环境变量或外部依赖。** 数据库结构变更必须保持向后兼容；有破坏性 schema 变更时禁止直接使用应用回滚，应先准备数据库迁移与恢复方案。
- 操作前独立备份 MySQL、上传文件和部署密钥，并定期演练恢复；回滚功能不能替代备份。镜像被 Docker 手动清理、服务器磁盘损坏或首次部署没有上一版本时无法自动恢复。
- **目前项目尚未准备真实生产服务器或自托管 Runner**。仓库中的 CI 只运行模拟的发布/失败恢复测试与 Compose 配置检查，不能视作真实线上回滚演练。
- 不允许把 `.env`、GitHub PAT、数据库口令、JWT 签名密钥写入 PR、运行日志或前端环境变量。

## 运维检查

检查 Actions 运行记录：`https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml`。

生产服务器运行 `docker compose --env-file /opt/atlas/.env -f <runner-workspace>/atlas-console/deploy/compose.yaml ps` 查看容器状态。Docker Compose 的构建上下文使用同一仓库中的根目录前端与 `backend/` 子目录；请使用正式工作流来部署，不要从任意目录直接执行。
