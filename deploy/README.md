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

## 部署失败与回滚

- 当前工作流执行镜像构建及 Compose 启动检查，**尚未提供可验证的自动业务回滚或数据库迁移回退**。如失败请先查看 GitHub Actions 日志，并评估是否停止继续部署。
- 在更新前执行 **MySQL 备份与 uploads 卷备份**，并定期测试恢复。对数据库 schema 的破坏性变更应单独设计迁移/回滚。
- 不允许把 `.env`、GitHub PAT、数据库口令、JWT 签名密钥写入 PR、运行日志或前端环境变量。
- 未部署 Runner 的安装、DNS、TLS、卷备份与真实服务器健康检查之前，本功能只是开发就绪的部署流程，**不是已经上线的自动更新服务**。

## 运维检查

检查 Actions 运行记录：`https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml`。

生产服务器运行 `docker compose --env-file /opt/atlas/.env -f <runner-workspace>/atlas-console/deploy/compose.yaml ps` 查看容器状态。Docker Compose 的构建上下文依赖 Runner 工作区内两个仓库并排检出；请使用正式工作流来部署，不要从任意目录直接执行。
