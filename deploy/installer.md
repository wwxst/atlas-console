# Atlas Console 一条命令安装

## 推荐：在全新 Ubuntu/Debian 服务器上运行

```bash
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
```

以 root 或有 sudo 权限的用户操作；先安装 `curl` 和 `iproute2`（用于只读端口检查）。脚本会检测 Linux 发行版和架构、安装官方 Docker Engine 与 Compose v2（仅自动安装 Ubuntu/Debian）、下载 Atlas 单仓库、生成独立随机密钥、创建权限为 `0640`、仅 root 和专用部署账号组可读的 `/opt/atlas/.env`，并构建启动前后端及依赖。首次构建可能需要数分钟。

安装器不会安装宝塔、修改公网防火墙、申请 TLS 证书或注册 GitHub Actions Runner。请在公网开放服务前完成域名、HTTPS 和默认管理员密码配置。

## 数据库选项

安装时会询问数据库模式：

- **Docker-managed MySQL（推荐）**：自动运行私有 MySQL 8.4 和 Redis 容器；不需要在宝塔另外创建数据库，也不会把数据库端口发布到公网。全新数据库由 Flyway 自动执行 V1 创建表和默认配置。
- **Existing MySQL（宝塔数据库）**：先在宝塔中创建一个空数据库和专用数据库用户，再在安装器输入主机、端口、库名、用户名和密码。安装器不会导入 `web_project.sql`。

宝塔 MySQL 若与 Atlas 位于同一台服务器，容器必须能够连接到宿主机的 MySQL 监听地址。常见默认设置只监听 `127.0.0.1`，Docker 容器无法直接访问；需要在宝塔/MySQL 中配置合适的监听地址，并在系统/云防火墙限制 3306 仅允许必要来源。**绝不能把 3306 对整个公网开放。** 若不熟悉 MySQL 网络授权，选择 Docker-managed MySQL 更安全、简单。

### 宝塔 MySQL 的内网连接准备

以下示例用于**新建的独立空数据库**，假设为本次安装选择 `--network-prefix 172.30.82`。不要修改或导入现有业务数据库，也不要向应用提供 MySQL root 凭据。

1. 先准备并启动 Docker（已由宝塔安装的 Docker 也可以）。在宿主机只读查看实际 host-gateway 映射：

   ```bash
   docker run --rm --add-host host.docker.internal:host-gateway alpine:3.22 \
     sh -c 'grep host.docker.internal /etc/hosts'
   ```

   记录输出中的私网 IP。它通常是 Docker 默认网桥的地址，**不一定是 Atlas 的 `172.30.82.1`**；不要凭猜测填写。`host-gateway` 说明见 [Docker 官方文档](https://docs.docker.com/reference/cli/docker/container/run/#add-entries-to-container-hosts-file---add-host)。

2. 在宝塔 MySQL 配置中保留本机监听并添加这个实际私网地址。例如实际解析为 `172.17.0.1`、使用 MySQL 8.4 时，可以在现有 `[mysqld]` 配置中调整为：

   ```ini
   bind-address=127.0.0.1,172.17.0.1
   ```

   多地址支持及限制见 [MySQL 8.4 bind_address 文档](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html#sysvar_bind_address)。旧版本若不支持多地址，不要直接改成 `0.0.0.0`；选择 Docker MySQL，或由运维提供合适的私网监听。修改已有 MySQL 监听会影响其他应用，先保存配置并安排维护窗口。若监听地址属于 Docker 网桥，还应让实际 MySQL systemd 服务在 `docker.service` 后启动（针对实际服务名设置 `After=docker.service`、`Requires=docker.service`），避免重启时地址尚不存在；修改后必须演练重启。

3. 系统防火墙只允许 Atlas 的 `172.30.82.0/24` 到这个私网地址的 3306；云安全组不开放 3306/33060。修改防火墙时合并现有规则，不清空规则。重载后执行 `ss -ltnp '( sport = :3306 or sport = :33060 )'`，确认没有公网或全地址监听；不使用的 MySQL X Protocol 可以在核实无人使用后关闭。

4. 在宝塔创建专用空库和用户。若需要通过 MySQL 管理终端设置精确来源权限，以下为示例，**仅适用于新建库/新建用户**，将口令替换为本次专用口令：

   ```sql
   CREATE DATABASE atlas_external CHARACTER SET utf8mb4;
   CREATE USER 'atlas_external'@'172.30.82.0/255.255.255.0' IDENTIFIED BY '<专用随机口令>';
   GRANT ALL PRIVILEGES ON atlas_external.* TO 'atlas_external'@'172.30.82.0/255.255.255.0';
   SHOW GRANTS FOR 'atlas_external'@'172.30.82.0/255.255.255.0';
   ```

   Flyway 需要在这个专用库内建表/改表权限，不需要全局权限。不要授予 `'%'` 来源或 `*.*` 权限。SQL 中的口令引号遵循 MySQL SQL 规则；安装器密码提示框则直接输入原始口令，支持单/双引号、`$`、`#`、空格和反斜线，不需要手动转义。

5. 运行交互安装，选择 Existing MySQL，输入 `host.docker.internal`、3306、专用库名和用户。若连接失败，保留安装目录，先排除监听、容器到宿主机防火墙和用户来源授权问题。此时安装创建的独立网络已存在，可以从该网络做真实 SQL 探测（以 `atlas-external` 为本次 project）：

   ```bash
   docker run --rm -it --network atlas-external_default \
     --add-host host.docker.internal:host-gateway mysql:8.4 \
     mysql -h host.docker.internal -P 3306 -u atlas_external -p atlas_external \
     -e 'SELECT CURRENT_USER(), DATABASE();'
   ```

   `-p` 会安全提示输入密码，不把口令写进命令行。`Access denied` 重点核对 `SHOW GRANTS` 和来源网段；超时/拒绝连接重点核对监听、防火墙及实际 `/etc/hosts` 地址。SQL 探测成功后按下文原配置安全重试。

## 安装器参数

```bash
# 使用 Docker 管理 MySQL，安装稳定 main 分支
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash

# 非交互安装（仅适用于 Docker-managed MySQL）
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh \
  | sudo bash -s -- --non-interactive --db-mode docker --port 8088

# 也可以先下载脚本再检查
curl -fsSLO https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh
less install.sh
sudo bash install.sh --help
sudo bash install.sh --db-mode docker

# 同一宿主机上的独立测试安装：四项必须分别选择未占用的值
sudo bash install.sh --db-mode docker --install-dir /opt/atlas-test-docker \
  --project-name atlas-test-docker --network-prefix 172.30.81 --port 18081
```

安装器自动安装 Docker 的路径支持 Ubuntu 和 Debian；其他发行版如果已经安装并启动 Docker Engine 与 Compose v2，也可以运行安装器。安装器不会尝试猜测未知发行版的包管理方式。

默认安装目录 `/opt/atlas`、Compose project `atlas`、网段 `172.30.80.0/24`、HTTP 端口 8088。安装器在写入文件或安装依赖之前检查目录、project 的容器/卷/网络、HTTP 监听及网络冲突；发现冲突立即退出，不删除任何已有资源。同宿主的不同测试模式必须使用不同目录、project、网段和端口，以及独立空数据库。project 同时隔离 MySQL、Redis、上传卷；网络前缀自动派生可信代理地址及 Nginx 网关。不同目录或端口本身不能替代完整隔离。

## 安装完成后：在宝塔添加网站

Atlas 前端只绑定到宿主机回环地址 `127.0.0.1:8088`。在宝塔网站设置中：

1. 添加你的域名。
2. 配置 Nginx 反向代理，目标为 `http://127.0.0.1:8088`。
3. 申请/配置有效的 HTTPS 证书，并启用 HTTPS。
4. 检查前端页面、登录和后端 API。

不要把 Spring Boot 的 `8080`、MySQL 的 `3306` 或 Redis 的 `6379` 直接映射到公网。Atlas 的主机反向代理必须覆盖 `X-Forwarded-For` 为实际客户端 IP；参考 `deploy/README.md` 的可信代理说明。

在宝塔反向代理的高级配置中，确保转发头是覆盖而不是追加客户端传入的值：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
```

请把这些设置合并进宝塔生成的**站点根路径 `/`**代理配置，不要覆盖整份 Nginx 配置文件。保持原始 `/api/`、`/files/` 路径，不把应用挂在额外 URL 子目录。浏览器和 API 使用同一个 HTTPS 域名，无需另开跨域地址。容器 Nginx 只接受来自本次 Docker 网关的真实 IP 和合法 `http`/`https` 协议声明。

### 第一次浏览器登录

通过 HTTPS 打开 `https://你的域名/login`。全新数据库的初始账号为 `admin`，初始密码为 `123456`（仅初始种子，不适用于已有账号）。登录后打开「系统用户」，进入 `admin` 详情，点击「重置密码」，设置自己的 8–24 位新密码。退出后使用新密码再次登录，确认旧密码失效。首次改密前可先通过 SSH 隧道访问回环端口，或将宝塔站点访问限制为自己的 IP。

随后逐个打开菜单；在用户列表、系统用户、存储空间、设置等页面检查加载、搜索和相应 API 响应；直接刷新 `/system-users` 等深层页面应保留页面，退出后重新登录应正常。使用浏览器开发者工具确认没有混合 HTTP 内容、跨域错误和 API 5xx。

## 安装目录和首次启动

- 应用代码：`/opt/atlas/atlas-console`
- 私密环境配置：`/opt/atlas/.env`（root:atlas-deploy，权限 `0640`）
- 发布记录：`/opt/atlas/releases/`
- 前端本机监听：`127.0.0.1:8088`（可通过 `--port` 修改）

首次安装使用当前仓库提交构建前后端镜像。Flyway 自动创建全新数据库 schema；`ATLAS_FLYWAY_BASELINE_ON_MIGRATE` 保持 `false`。安装完成后，首次公网开放前必须修改默认系统管理员密码。

如果首次部署因外部 MySQL 网络/授权问题失败，安装器会保留 `.env` 和下载的代码供排查，不会删除数据卷。修复数据库连接后，可以在服务器执行：

```bash
atlas_dir=/opt/atlas # 独立安装请改为实际 --install-dir
commit="$(sudo -u atlas-deploy git -c safe.directory="$atlas_dir/atlas-console" \
  -C "$atlas_dir/atlas-console" rev-parse HEAD)"
sudo -u atlas-deploy env ATLAS_ENV_FILE="$atlas_dir/.env" ATLAS_RELEASE_DIR="$atlas_dir/releases" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$atlas_dir/atlas-console/deploy/release.sh" deploy
```

这条命令保持 `atlas-deploy` 身份重试，不自动重写 `.env`、轮换密钥或删除已有数据。不要直接以 root 执行 `release.sh`，否则新发布记录的所有权会影响后续部署。不要为了重试而删除目录或 Docker volumes，也不要 `source .env`：它是 Compose dotenv 格式，不是 Shell 脚本。重复运行首次安装命令会安全拒绝；失败后的排障重试使用上述 release 命令。

## 重启恢复验收

安装器会启用 Docker systemd 服务；前端、后端、Redis 和 Docker MySQL 使用 `restart: unless-stopped`。先确认容器正常且未被手动停止，然后在可维护窗口重启服务器。恢复后运行：

```bash
atlas_dir=/opt/atlas
sudo -u atlas-deploy docker compose --env-file "$atlas_dir/.env" \
  -f "$atlas_dir/atlas-console/deploy/compose.yaml" ps
curl --fail --silent --output /dev/null http://127.0.0.1:8088/
```

检查 Docker、宝塔 Nginx、外部 MySQL（若使用）的启动状态；浏览器重新登录并确认测试记录仍在。Docker MySQL 模式查看数据库详情时同时加 `-f "$atlas_dir/atlas-console/deploy/compose.mysql.yaml"`。容器自动重启策略不保证外部 MySQL或宝塔服务正常，实际验收必须覆盖整机重启。

## 后续 GitHub 自动更新

一条命令安装与后续 GitHub Actions 自动更新是两个阶段。后续自动更新仍需要在生产服务器注册专用 GitHub Actions Runner，标签必须包含 `self-hosted`、`linux`、`x64`、`atlas-production`。安装器创建的 `atlas-deploy` 是专用非 root 账号，并加入 Docker 组以执行部署；Docker 组权限接近 root，所以不要给该账号日常登录使用，也不要在生产 Runner 上执行未经审查的 PR 代码。安装器不会自动创建或泄露 Runner 注册令牌。

当前安装器提供命令行首次安装。网页安装向导、安装状态页面和宝塔面板内的专用状态展示属于后续功能；安装后可以通过宝塔已有的网站、反向代理和 Docker 管理功能查看/管理相应服务。
