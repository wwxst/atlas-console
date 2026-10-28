# Atlas Console 一键部署：首次上线与后续自动更新

> **当前阶段**：项目只有 GitHub 仓库，尚无服务器。仓库中的工作流和部署配置仅是可执行模板，**不会创建服务器、购买域名或完成实际部署**。

## 架构与边界

- 后台超级管理员（系统用户 ID=1）在「版本与更新」点击一键更新。
- 后端调用 GitHub Actions API，只允许触发 `wwxst/atlas-console` 中固定的 `deploy-production.yml`，引用 `main`。
- GitHub Actions 在挂有 `atlas-production` 标签的**专用自托管 Runner** 上执行，获取 `atlas-console` 和 `backend` 两个仓库的 `main` 分支，Docker Compose 构建并更新 Nginx、Spring Boot、MySQL 和 Redis。
- 前端查看 Actions 的最近执行状态，**不会直接执行 Shell、传递自定义分支或把 GitHub Token 暴露到浏览器**。
- 默认禁用自动更新。没有服务器、Runner 和凭据时，按钮保持禁用。
- **业务数据持久化**：MySQL、Redis、上传目录使用 Docker 命名卷；重启容器不会清空数据。但**更新不等于备份**，操作前必须另行备份这些卷。

## 首次上线：必须人工完成一次

1. 准备一台 Linux 生产服务器、域名、有效的 HTTPS 证书以及安全的网络策略。建议运行 Docker Engine + Docker Compose v2；将公网流量交给独立的 TLS 反向代理（Caddy/Nginx），只转发到本机 `127.0.0.1:8088`。数据库和 Redis 没有对外发布端口。
2. 在服务器建立专用非 root 部署账号，安装 Docker 和 GitHub Actions Runner。Runner 必须注册到 `wwxst/atlas-console`，并添加 `atlas-production` 标签。按照 GitHub 自托管 Runner 官方步骤将 Runner 作为受控服务运行。**Runner 的 Docker 权限具有接近 root 的风险，生产 Runner 不得执行不受信任的 PR 代码。请设置分支保护、审查工作流变更，并限制仓库写入权限。**
3. 创建 `/opt/atlas/.env`（参考本目录 `env.example`），设置 **独立的** MySQL root 密码和业务密码；用 `openssl rand -base64 48` 生成 JWT_SECRET，用 `openssl rand -base64 32` 生成 STORAGE_ENCRYPTION_KEY。只允许 Runner 的部署账号读取此文件，权限设为 `0600`，**不要提交到 GitHub**。
4. 在 GitHub 仓库的 Actions 设置中允许执行工作流，创建名为 `production` 的 Environment，可选配置 required reviewers。工作流只允许从 `main` 引用执行。仓库 `main` 必须受代码审查和 CI 约束。
5. 首次部署：通过 GitHub Actions 页面手动运行 `Deploy production`，输入任意非空的请求标识（例如 `bootstrap-1`）。工作流会在 Runner 上拉取两个仓库、构建并启动容器，验证可启动后再确认外部域名和 HTTPS。**没有 Runner 时会一直排队，不能视为部署成功。**
6. 初始化 SQL 在**数据库卷第一次建立时**自动执行，之后不会重复清表。系统初始管理员来自项目原有 SQL，**首次公网开放前必须修改其默认密码**；建议另行执行安全审计。
7. 首次访问成功后，创建一个只访问 `wwxst/atlas-console` 的 **GitHub fine-grained PAT**，仅授予仓库 Actions 的读写权限，不授予代码写入、Secrets 或 Administration 权限。把它写入服务器 `/opt/atlas/.env` 的 `ATLAS_DEPLOYMENT_GITHUB_TOKEN`，设置 `ATLAS_DEPLOYMENT_ENABLED=true`，再通过受控部署流程重启 backend。这样后台的一键更新按钮才会启用。

## 后续的「一键更新」

1. 将前端、后端代码经 PR 和 CI 合并到各自 `main`。
2. 使用 ID=1 的超级管理员登录 Atlas Console，打开「版本与更新」。
3. 点击「一键自动更新」并确认。后台固定触发 GitHub Actions。工作流在生产 Runner 上获取最新 `main`，重新构建/启动服务。
4. 在弹窗或 GitHub Actions 页查看排队、运行、成功、失败状态。显示成功后自行刷新页面以加载前端资源。**刷新页面本身不部署代码**。

## 部署失败、自动回滚与手动回滚

- 每次部署从 GitHub 获取前后端已合并的 `main`，分别构建 `atlas-console:git-<40位SHA>` 和 `atlas-backend:git-<40位SHA>` 镜像，**不会覆盖上一版应用镜像标签**。
- 专用 Runner 在服务器 `/opt/atlas/releases/` 保存 `current.env` 和 `previous.env` 两份版本清单（只包含镜像标签，不包含密钥）。首次成功部署只有 current；第二次成功部署开始才有 previous。
- 更新中 Docker Compose 的健康检查或前端 HTTP 检查失败时，发布脚本自动尝试用 current 清单恢复上一套前后端镜像。**即使自动恢复成功，本次 GitHub Actions 仍标记失败**，便于管理员排查。恢复失败会在日志中明确告警。
- 超级管理员在「版本与更新」点击「回滚上一版本」并确认，后端使用固定的 `operation=rollback` 触发同一生产工作流，Runner **不再构建当前代码**，而是验证 previous 清单和镜像存在，恢复该版本并执行健康检查。成功后互换 current/previous，因此还可以再次切换回来。
- `deploy.lock` 使用 `flock` 阻止同一服务器同时运行多次发布，GitHub Actions 的 concurrency 也会串行处理任务。
- **仅回滚前端和后端应用镜像，不自动回滚 MySQL/Redis 数据、上传文件、数据库 schema、环境变量或外部依赖。** 数据库结构变更必须保持向后兼容；有破坏性 schema 变更时禁止直接使用应用回滚，应先准备数据库迁移与恢复方案。
- 操作前独立备份 MySQL、上传文件和部署密钥，并定期演练恢复；回滚功能不能替代备份。镜像被 Docker 手动清理、服务器磁盘损坏或首次部署没有上一版本时无法自动恢复。
- **目前项目尚未准备真实生产服务器或自托管 Runner**。仓库中的 CI 只运行模拟的发布/失败恢复测试与 Compose 配置检查，不能视作真实线上回滚演练。
- 不允许把 `.env`、GitHub PAT、数据库口令、JWT 签名密钥写入 PR、运行日志或前端环境变量。

## 运维检查

检查 Actions 运行记录：`https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml`。

生产服务器运行 `docker compose --env-file /opt/atlas/.env -f <runner-workspace>/atlas-console/deploy/compose.yaml ps` 查看容器状态。Docker Compose 的构建上下文依赖 Runner 工作区内两个仓库并排检出；请使用正式工作流来部署，不要从任意目录直接执行。
