# Atlas Console 客户端独立部署与版本更新

Atlas 支持两种独立部署更新方式：

- **Docker Compose**：使用项目官方安装器，容器升级与健康检查按原方案执行。
- **宝塔原生（无需 Docker）**：Nginx 静态目录 + Java JAR systemd 服务，公开 GitHub Release 提供 `atlas-frontend.tar.gz`、`atlas-backend.jar` 和 `SHA256SUMS`，由主机本地更新器完成校验、切换和回滚。首次接入请阅读 [宝塔原生部署与更新说明](baota/README.md)。

两种模式共用同一个后台「版本与更新」入口。安装时只需配置一次服务和权限；不要求客户提供 GitHub PAT 或 Runner。

Atlas Console 按**独立部署产品**交付：客户把系统安装在自己的 Linux 服务器上，之后在后台「版本与更新」中检查 GitHub 正式版本并一键升级。客户不需要 GitHub 账号、Personal Access Token（PAT）、自托管 Runner 或 GitHub Actions 仓库授权。

## 客户首次安装

推荐 Ubuntu/Debian + Docker Engine + Docker Compose v2：

```bash
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
```

安装器会：

- 安装或检查 Docker Engine 和 Compose v2。
- 创建专用的 `atlas-deploy` 主机账号，完成首次 Compose 构建与启动。它不是 GitHub Runner；Docker 组权限接近 root，不应作为日常登录账号。
- 生成数据库、JWT、存储加密和本机更新执行器的随机密钥，写入权限为 `0640` 的 `/opt/atlas/.env`。
- 启动前端、后端、Redis、隔离的本机 updater；选择 Docker PostgreSQL 模式时还会启动 PostgreSQL 17。
- 默认启用本机更新执行器。客户不需要在 GitHub 仓库中创建任何凭据。

安装器不会自动购买域名、修改云防火墙或签发 HTTPS 证书。安装完成后，在宝塔/Nginx 中将域名反向代理到 `http://127.0.0.1:8088` 并启用 HTTPS。不要将 Spring Boot 8080、PostgreSQL 5432 或 Redis 6379 暴露到公网。公网开放前必须修改默认管理员密码。

数据库模式、宝塔 PostgreSQL 网络配置和完整首次安装说明见 [installer.md](installer.md)。

## 更新架构

```text
Atlas 维护者
  └─ 合并代码到 main
       └─ 发布 vX.Y.Z Git tag
            └─ GitHub Release 工作流校验版本并创建公开 Release

客户服务器
  └─ 后台管理员打开「版本与更新」
       └─ 本机 updater 读取公开 GitHub Releases
            └─ 管理员点击一键更新
                 └─ 隔离 worker 下载指定 Release tag
                      └─ 构建前后端/updater 镜像
                           └─ Docker Compose 更新并检查健康状态
                                ├─ 成功：记录版本与历史
                                └─ 失败：恢复上一套应用镜像
```

- 版本来源固定为公开仓库 `wwxst/atlas-console` 的**最新正式 GitHub Release**，不跟随 `main` 的任意提交，也不接受浏览器传入的仓库、URL、分支或 Shell 命令。
- Release tag 使用 `v主版本.次版本.修订版本`，例如 `v0.1.1`。发布工作流检查 tag 与 `frontend/package.json` 的版本一致，并要求该提交已进入 `main`。
- 客户服务器使用匿名 HTTPS 读取 GitHub Release 元数据，再通过公开 Git tag 下载源码；无需 PAT、GitHub 登录或 Runner。
- updater 是独立容器，唯一挂载 Docker socket；业务 backend 不挂载 Docker socket。updater API 只在 Atlas 私有 Docker 网络中监听，并要求安装时生成的 256-bit 随机 Bearer token。
- 只有超级管理员（系统用户 ID=1）能在后台发起更新或回滚。updater 只接受固定的 deploy、rollback、rollback-history 操作。
- updater worker 是独立容器，不会因应用更新重建 updater 服务而中断当前更新任务。每次成功更新会随新 Release 更新 updater 镜像本身。

## 版本发布流程（维护者操作）

1. 在功能分支开发并提交 PR。
2. PR 合并到 `main)，等待前端、后端、数据库、部署配置和浏览器测试全部通过。
3. 将 `frontend/package.json` 的版本号更新为目标版本，例如 `0.1.1)，合并到 `main)。
4. 从已通过 CI 的 main 提交创建并推送版本 tag：

   ```bash
   git checkout main
   git pull --ff-only
   git tag -a v0.1.1 -m "Atlas Console v0.1.1"
   git push origin v0.1.1
   ```

5. GitHub 的 `Publish Atlas Console release` 工作流验证 tag、版本号及 main 祖先关系，通过后自动创建公开 Release 和更新日志。

不要在 CI 未通过的提交上发布 tag。不要复用或移动已发布的版本 tag。客户只会看到正式 Release，不会看到草稿或预发布版本。

## 客户后台操作

1. 使用系统超级管理员登录。
2. 点击侧边栏底部的「版本与更新」。
3. 查看当前版本、最新正式版本和更新日志。
4. 有新版本时点击「一键更新」并确认。
5. 页面显示更新进行中；完成后刷新页面即可加载新前端。

后台状态通过本机 updater 查询。GitHub Release 信息缓存 5 分钟，避免频繁请求 GitHub API。没有新版本时按钮显示「已是最新版本」。GitHub 尚未发布正式 Release 时，会提示暂无正式版本。

## 更新安全与回滚边界

- 每个客户安装有独立随机 token，保存在服务器 `/opt/atlas/.env)，不要发给客户以外的人员或提交到 Git。
- updater API 不对宿主机公网发布端口；不把 Docker socket 挂载给业务 backend 或 frontend。
- 更新 worker 固定克隆官方仓库的 SemVer tag，核对 tag 与前端 package 版本一致，再调用仓库内固定的 `deploy/release.sh)。
- 新版本构建后，Compose 等待服务健康检查并探测前端 HTTP。失败时自动恢复上一套前后端/updater 应用镜像；历史版本也可在后台选择回滚。
- 应用回滚**不会回滚 PostgreSQL schema 或业务数据**。Flyway 迁移必须向前兼容当前版本和可回滚的旧应用版本。生产更新前仍应备份数据库和上传文件。
- 更新日志位于 `/opt/atlas/releases/update-logs/`；版本清单和历史记录位于 `/opt/atlas/releases/`。
- `atlas-deploy` 主机账号只用于首次安装和受控维护，不再承担 Runner 任务。客户不需要为更新创建 GitHub Actions Runner。

## 故障排查

查看服务状态：

```bash
cd /opt/atlas/atlas-console
sudo -u atlas-deploy docker compose --env-file /opt/atlas/.env \
  -f deploy/compose.yaml -f deploy/compose.postgres.yaml ps
```

查看 updater 日志：

```bash
sudo docker logs --tail 200 atlas-updater-1
sudo find /opt/atlas/releases/update-logs -maxdepth 1 -type f -printf '%TY-%Tm-%Td %TH:%TM %p\\n' | sort
```

Compose 项目名如有自定义，请用实际容器名查询。updater 服务只在内部网络监听 8090，不要为排障把该端口发布到公网。

如果更新失败，先查看更新日志和 Docker 服务健康状态；不要删除 Docker volumes、数据库目录或上传文件。自动回滚只恢复应用镜像，不会恢复数据库。修复网络或磁盘问题后，再从后台重新发起更新。

## 数据持久化

- Docker PostgreSQL 模式：PostgreSQL、Redis、上传文件分别使用 Compose 命名卷。
- 外部/宝塔 PostgreSQL 模式：数据库由宿主机/外部 PostgreSQL 管理，Redis 和上传文件仍使用命名卷。
- 更新和应用回滚不会删除这些数据卷。**更新不等于备份**，请建立独立数据库与上传文件备份计划。
- 全新 PostgreSQL schema 由 Flyway 管理。已发布迁移文件不可修改，只能新增后续版本迁移。
