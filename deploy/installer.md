# Atlas Console 一条命令安装

## 推荐：在全新 Ubuntu/Debian 服务器上运行

```bash
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
```

脚本会检测 Linux 发行版和架构、安装官方 Docker Engine 与 Compose v2（仅自动安装 Ubuntu/Debian）、下载 Atlas 单仓库、生成独立随机密钥、创建权限为 `0600` 的 `/opt/atlas/.env`，并构建启动前后端及依赖。首次构建可能需要数分钟。

安装器不会安装宝塔、修改公网防火墙、申请 TLS 证书或注册 GitHub Actions Runner。请在公网开放服务前完成域名、HTTPS 和默认管理员密码配置。

## 数据库选项

安装时会询问数据库模式：

- **Docker-managed MySQL（推荐）**：自动运行私有 MySQL 8.4 和 Redis 容器；不需要在宝塔另外创建数据库，也不会把数据库端口发布到公网。全新数据库由 Flyway 自动执行 V1 创建表和默认配置。
- **Existing MySQL（宝塔数据库）**：先在宝塔中创建一个空数据库和专用数据库用户，再在安装器输入主机、端口、库名、用户名和密码。安装器不会导入 `web_project.sql`。

宝塔 MySQL 若与 Atlas 位于同一台服务器，容器必须能够连接到宿主机的 MySQL 监听地址。常见默认设置只监听 `127.0.0.1`，Docker 容器无法直接访问；需要在宝塔/MySQL 中配置合适的监听地址，并在系统/云防火墙限制 3306 仅允许必要来源。**绝不能把 3306 对整个公网开放。** 若不熟悉 MySQL 网络授权，选择 Docker-managed MySQL 更安全、简单。

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
```

安装器自动安装 Docker 的路径支持 Ubuntu 和 Debian；其他发行版如果已经安装并启动 Docker Engine 与 Compose v2，也可以运行安装器。安装器不会尝试猜测未知发行版的包管理方式。

## 安装完成后：在宝塔添加网站

Atlas 前端只绑定到宿主机回环地址 `127.0.0.1:8088`。在宝塔网站设置中：

1. 添加你的域名。
2. 配置 Nginx 反向代理，目标为 `http://127.0.0.1:8088`。
3. 申请/配置有效的 HTTPS 证书，并启用 HTTPS。
4. 检查前端页面、登录和后端 API。

不要把 Spring Boot 的 `8080`、MySQL 的 `3306` 或 Redis 的 `6379` 直接映射到公网。Atlas 的主机反向代理必须覆盖 `X-Forwarded-For` 为实际客户端 IP；参考 `deploy/README.md` 的可信代理说明。

## 安装目录和首次启动

- 应用代码：`/opt/atlas/atlas-console`
- 私密环境配置：`/opt/atlas/.env`（权限 `0600`）
- 发布记录：`/opt/atlas/releases/`
- 前端本机监听：`127.0.0.1:8088`（可通过 `--port` 修改）

首次安装使用当前仓库提交构建前后端镜像。Flyway 自动创建全新数据库 schema；`ATLAS_FLYWAY_BASELINE_ON_MIGRATE` 保持 `false`。安装完成后，首次公网开放前必须修改默认系统管理员密码。

如果首次部署因外部 MySQL 网络/授权问题失败，安装器会保留 `.env` 和下载的代码供排查，不会删除数据卷。修复数据库连接后，可以在服务器执行：

```bash
cd /opt/atlas/atlas-console
commit="$(git rev-parse HEAD)"
ATLAS_ENV_FILE=/opt/atlas/.env ATLAS_RELEASE_DIR=/opt/atlas/releases \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash deploy/release.sh deploy
```

这条命令会重试应用部署，不会自动重写 `.env` 或删除已有数据。不要为了重试而删除 Docker volumes。

## 后续 GitHub 自动更新

一条命令安装与后续 GitHub Actions 自动更新是两个阶段。后续自动更新仍需要在生产服务器注册专用 GitHub Actions Runner，标签必须包含 `self-hosted`、`linux`、`x64`、`atlas-production`。Runner 的 Docker 权限接近 root；不要在生产 Runner 上执行未经审查的 PR 代码。安装器不会自动创建或泄露 Runner 注册令牌。

当前安装器提供命令行首次安装。网页安装向导、安装状态页面和宝塔面板内的专用状态展示属于后续功能；安装后可以通过宝塔已有的网站、反向代理和 Docker 管理功能查看/管理相应服务。
