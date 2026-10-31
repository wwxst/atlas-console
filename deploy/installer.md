# Atlas Console 一条命令安装

## 推荐：全新 Ubuntu/Debian 服务器

```bash
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
```

安装器会检测环境、安装 Docker Engine + Compose v2（自动安装仅支持 Ubuntu/Debian）、下载 Atlas 单仓库、生成随机密钥、写入权限受限的 `/opt/atlas/.env`，并构建启动前后端及依赖。首次构建可能需要数分钟。

安装器不会安装宝塔、修改公网防火墙、申请 TLS 证书或注册 GitHub Actions Runner。公网开放前请完成域名、HTTPS 和默认管理员密码配置。

## 数据库选项

Atlas Console **只支持 PostgreSQL 17**，不再提供 MySQL 驱动、MySQL Compose 服务或 MySQL 部署模式。

安装时会询问：

- **Docker-managed PostgreSQL（推荐）**：自动运行私有 PostgreSQL 17 和 Redis 容器；不需要在宝塔另建数据库，数据库端口不发布到宿主机。Flyway 首次启动时自动创建表和默认配置。
- **Existing PostgreSQL（宝塔/外部数据库）**：先在宝塔或 PostgreSQL 管理端创建一个新的空数据库和专用登录角色，再在安装器输入主机、端口、库名、用户名和密码。不要使用 PostgreSQL 超级用户账号。

### 宝塔 PostgreSQL 的安全连接准备

宝塔 PostgreSQL 与 Atlas 位于同一台服务器时，Docker 容器访问宿主机数据库不能填写容器内的 `localhost`。安装器默认建议使用 `host.docker.internal`，并通过 Docker 的 `host-gateway` 映射解析到宿主机网桥地址。

以下示例假设 Atlas 使用 `--network-prefix 172.30.82`，外部 PostgreSQL 监听端口为 5432。请将示例 IP、数据库名、角色名替换为实际值。

1. **确认 Docker 到宿主机的网关地址。**

   ```bash
   docker run --rm --add-host host.docker.internal:host-gateway alpine:3.22 \
     sh -c 'grep host.docker.internal /etc/hosts'
   ```

   记录实际解析的宿主机私网 IP，例如 `172.17.0.1`。不要假设它一定是这个地址。

2. **配置 PostgreSQL 只监听必要地址。**

   在宝塔 PostgreSQL 的 `postgresql.conf` 中，将 `listen_addresses` 设置为本机地址和上一步确认的宿主机私网地址，例如：

   ```ini
   listen_addresses = 'localhost,172.17.0.1'
   password_encryption = 'scram-sha-256'
   ```

   不要为了让 Docker 连接而直接设置为 `*` 或 `0.0.0.0`。修改 `listen_addresses` 通常需要重启 PostgreSQL 服务。

3. **在 pg_hba.conf 中仅允许 Atlas Docker 子网。**

   添加精确规则（示例）：

   ```conf
   host    atlas_external    atlas_external    172.30.82.0/24    scram-sha-256
   ```

   PostgreSQL 会按规则顺序匹配；不要添加允许任意来源的 `0.0.0.0/0` 规则。修改后按宝塔服务管理方式 reload PostgreSQL，使 pg_hba.conf 生效。

4. **创建专用角色和空数据库。**

   使用宝塔 PostgreSQL 管理终端或 psql，以管理员身份执行。密码使用独立随机值：

   ```sql
   CREATE ROLE atlas_external LOGIN PASSWORD '替换为专用随机密码';
   CREATE DATABASE atlas_external OWNER atlas_external;
   ```

   数据库所有者需要在该数据库内创建表、索引、函数和触发器，足以运行 Flyway。不要把超级用户凭据交给 Atlas，也不要对现有业务库执行这些示例命令。

5. **限制主机防火墙和云安全组。**

   只允许 Atlas 的 Docker 子网访问 PostgreSQL 的宿主机私网地址 5432。例如 UFW 规则（请替换实际网段和网关 IP）：

   ```bash
   sudo ufw allow from 172.30.82.0/24 to 172.17.0.1 port 5432 proto tcp
   ```

   同时确认云安全组没有对公网开放 5432。若主机已有拒绝规则，先检查规则顺序，确保只对指定私网来源开放。最终用 `ss -ltnp '( sport = :5432 )'` 检查监听地址，不应出现公网可访问的全地址监听。

6. **运行安装器并填写连接信息。**

   ```bash
   curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
   ```

   选择 Existing PostgreSQL，输入 `host.docker.internal`、5432、`atlas_external`、`atlas_external` 和专用密码。密码提示框支持空格、引号、美元符号、井号和反斜线，不需要手动转义。

7. 如果连接失败，安装器会保留配置和代码供排查，不会删除数据卷。先检查 PostgreSQL 监听、`pg_hba.conf`、主机防火墙和容器网络，再使用 psql 从 Atlas 网络测试。安装器失败后，先通过 `docker network ls` 找到本次 Compose 网络（默认项目名为 `atlas`）：

   ```bash
   docker run --rm -it --network atlas_default \
     --add-host host.docker.internal:host-gateway postgres:17-alpine \
     psql -W -h host.docker.internal -p 5432 \
       -U atlas_external -d atlas_external \
       -v ON_ERROR_STOP=1 -c 'SELECT current_user, current_database();'
   ```

   `-W` 会交互式提示输入密码，不要把数据库密码写进命令行或日志。测试网络名、数据库名和用户需按实际安装参数调整。

如果不熟悉 PostgreSQL 网络授权，选择 Docker-managed PostgreSQL 更安全简单。

## 安装器参数

```bash
# Docker 管理 PostgreSQL，安装 main
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash

# 非交互安装（仅支持 Docker PostgreSQL）
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh \
  | sudo bash -s -- --non-interactive --db-mode docker --port 8088

# 下载并检查脚本
curl -fsSLO https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh
less install.sh
sudo bash install.sh --help

# 同宿主机的隔离测试安装：目录、project、网段、端口必须独立
sudo bash install.sh --db-mode docker --install-dir /opt/atlas-test \
  --project-name atlas-test --network-prefix 172.30.81 --port 18081
```

默认安装目录 `/opt/atlas`、Compose project `atlas`、网段 `172.30.80.0/24)、HTTP 端口 8088。安装器会在写入文件或安装依赖前检查目录、project 容器/卷/网络、HTTP 监听和网络冲突；发现冲突立即退出，不删除任何已有资源。

## 安装完成后：宝塔添加网站

Atlas 前端只绑定到宿主机回环地址 `127.0.0.1:8088`。在宝塔网站设置中：

1. 添加域名。
2. 配置 Nginx 反向代理到 `http://127.0.0.1:8088`。
3. 配置有效 HTTPS 证书并启用 HTTPS。
4. 确认登录、页面和后端 API 正常。

不要把 Spring Boot 的 8080、PostgreSQL 的 5432 或 Redis 的 6379 直接映射到公网。

主机反向代理必须覆盖转发头，不能追加访客自己传入的值：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
```

请将这些设置合并进宝塔生成的站点根路径 `/` 代理配置，不要覆盖整份 Nginx 配置。保留原始 `/api/`、`/files/` 路径，不把应用挂在额外 URL 子目录。

## 首次浏览器登录

通过 HTTPS 打开 `https://你的域名/login`。全新数据库初始账号为 `admin`，初始密码为 `123456`。首次登录后立即修改默认密码，退出并重新登录验证新密码生效。再检查用户列表、系统用户、存储空间、设置、上传、搜索、深层路由刷新和重新登录。

## 安装目录与首次启动

- 应用代码：`/opt/atlas/atlas-console`
- 私密环境配置：`/opt/atlas/.env`（root:atlas-deploy，权限 `0640`）
- 发布记录：`/opt/atlas/releases/`
- 前端本机监听：`127.0.0.1:8088`

全新 PostgreSQL 数据库由 Flyway 执行 V1 创建 schema；首次公网开放前必须修改管理员密码。

**历史 MySQL 数据库不能直接用于 PostgreSQL，也不能通过 Flyway baseline 转换。** 若未来确有历史数据需要迁移，必须先备份 MySQL，在独立 PostgreSQL 中运行 Flyway，再通过单独的数据迁移工具转换并逐表校验。当前首次部署没有旧数据库，直接使用全新的 PostgreSQL。

首次部署因外部 PostgreSQL 网络/授权失败时，安装器保留 `.env` 和下载的代码。修复连接后，以专用部署账号重试：

```bash
atlas_dir=/opt/atlas
commit="$(sudo -u atlas-deploy git -c safe.directory="$atlas_dir/atlas-console" \
  -C "$atlas_dir/atlas-console" rev-parse HEAD)"
sudo -u atlas-deploy env ATLAS_ENV_FILE="$atlas_dir/.env" ATLAS_RELEASE_DIR="$atlas_dir/releases" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$atlas_dir/atlas-console/deploy/release.sh" deploy
```

不要直接以 root 执行 release.sh，也不要为重试删除目录或 Docker volumes。不要 `source .env`：它是 Compose dotenv 格式，不是 Shell 脚本。

## 重启恢复验收

```bash
atlas_dir=/opt/atlas
sudo -u atlas-deploy docker compose --env-file "$atlas_dir/.env" \
  -f "$atlas_dir/atlas-console/deploy/compose.yaml" \
  -f "$atlas_dir/atlas-console/deploy/compose.postgres.yaml" ps
curl --fail --silent --output /dev/null http://127.0.0.1:8088/
```

外部 PostgreSQL 由宿主机/宝塔服务管理，不随 Atlas Compose 自动启动；整机重启后必须确认外部数据库也已正常启动。

## 后续 GitHub 自动更新

一条命令首次安装与 GitHub Actions 自动更新是两个阶段。自动更新仍需要在生产服务器注册专用 GitHub Actions Runner，标签包含 `self-hosted`、`linux`、`x64`、`atlas-production`。安装器创建的 `atlas-deploy` 是专用非 root 账号，并加入 Docker 组；Docker 组权限接近 root，因此不要用于日常登录，也不要在生产 Runner 上执行未经审查的 PR 代码。

当前安装器提供命令行首次安装。网页安装向导、安装状态页面和宝塔面板内的专用状态展示属于后续功能。
