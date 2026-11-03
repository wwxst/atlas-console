# Atlas Console — 宝塔原生部署与一键更新

本模式适用于**宝塔 Nginx 托管前端静态文件、Linux systemd 运行 Java JAR** 的客户服务器。
**不需要 Docker、GitHub PAT 或 GitHub Actions Runner**。Docker Compose 部署模式保持原样。

> **部署边界：** 首次接入需要由服务器管理员执行一次安装和网站/Java 服务切换。更新器不会擅自覆盖宝塔网站目录、修改 Nginx 配置、停止原有 Java 进程或迁移数据库。以后只需在系统后台点击「一键更新」。

## 0. 服务器前提

- Linux + systemd（推荐已安装宝塔的 Ubuntu/Debian）。
- JDK **25**、Python **3.11+**、`sudo`、`openssl`、`systemctl`、`visudo`。
- 已运行且可正常连接的 PostgreSQL **17**、Redis。
- 宝塔已能管理网站域名、SSL 证书和 Nginx。
- GitHub 已发布带有以下三项资产的稳定 Release：`atlas-frontend.tar.gz`、`atlas-backend.jar`、`SHA256SUMS`。尚无正式 Release 时，不能执行原生首次部署。

数据库、Redis、存储密钥和上传文件**不会自动创建或迁移**。已经有客户数据时，务必提前备份并演练恢复。

## 1. 准备后端环境配置

先在宝塔/数据库工具中创建数据库及专用最小权限数据库角色，或复用当前 Atlas 使用的 PostgreSQL 数据库。将服务器上当前使用的真实密钥迁移到仅 root 可读的配置文件，**不要重新生成已有生产系统的 JWT/存储加密密钥**。

创建 `/root/atlas-native-backend.env`，例如：

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:5432/web_project?options=-c%20TimeZone%3DUTC
DB_USERNAME=atlas
DB_PASSWORD=请替换为原数据库密码
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DATABASE=0
JWT_SECRET=请替换为原生产JWT的Base64密钥
STORAGE_ENCRYPTION_KEY=请替换为原生产存储加密密钥
```

执行：

```bash
sudo chmod 600 /root/atlas-native-backend.env
```

密码如含空格、美元符号、引号等特殊字符，应使用 systemd `EnvironmentFile` 支持的正确引用方式。此文件**不是 Shell 脚本**，不要通过 `source` 加载。

安装器会复制该文件到 `/etc/atlas-native/backend.env`，并自动加入独立更新器的随机 token、`UPLOAD_DIR` 等配置。**请勿在输入文件中预先设置 `ATLAS_DEPLOYMENT_*`、`UPLOAD_DIR` 或 `AUTH_CODE_SENDER`。**

## 2. 安装一次本机更新执行器

在可信的 Atlas Console GitHub Release 对应代码工作区执行：

```bash
sudo bash deploy/baota/install.sh \
  --backend-env-file /root/atlas-native-backend.env \
  --tag v0.1.1
```

将示例版本号换成实际已发布的稳定版。省略 `--tag` 时使用最新正式 Release。首次安装脚本：

1. 创建两个固定系统用户：`atlas-native-update`、`atlas-native-app`（禁止交互登录）。
2. 把 root 拥有的更新器代码安装在 `/opt/atlas-native/agent/`。
3. 在 `/etc/atlas-native/` 生成权限受限的后端和更新器环境配置，生成每台服务器独有的随机访问 token。
4. 安装 `atlas-native-updater.service`、`atlas-native-backend.service`。仅允许更新器用户通过 sudo 执行 `systemctl restart atlas-native-backend.service`，不开放任意 sudo 命令。
5. 下载正式版本资产并校验 SHA256，解包到私有版本目录，原子切换当前应用链接，启动 Java 服务并检查健康状态（期望未登录的 `/api/sys-user/auth/me` 返回 401）。
6. 成功后启动版本管理服务。失败时不会修改宝塔现有网站配置，也不会删除数据库或上传目录。

可使用 `--skip-bootstrap` 只安装执行器；此时还没有 Atlas 前后端发布文件，**不能先把宝塔网站切到新路径**。

如果初次下载失败、当时还没有 GitHub 正式 Release，或使用了 `--skip-bootstrap`，请先修复网络/数据库问题，然后在首次发布成功前单独执行（不要反复覆盖安装脚本或系统账号）：

```bash
sudo bash deploy/baota/bootstrap.sh --tag v0.1.1
```

省略 `--tag` 会获取最新正式版。该脚本只允许**尚未安装任何有效版本**的原生环境执行；已有运行版本时必须从后台更新或回滚，防止覆盖生产发布记录。

## 3. 宝塔 Nginx 首次配置（只做一次）

在对应宝塔站点 Nginx 配置中，将网站静态根目录改为：

```nginx
root /opt/atlas-native/runtime/current/frontend;
index index.html;
location / {
    try_files $uri $uri/ /index.html;
}
location /api/ {
    proxy_pass http://127.0.0.1:8091;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
location /files/ {
    proxy_pass http://127.0.0.1:8091;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
location /uploads/ {
    proxy_pass http://127.0.0.1:8091;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

以上是**站点配置片段**，不要把它作为整份 Nginx 配置覆盖宝塔生成的 `server { ... }`。如果站点已定义相同的 `location`，应修改现有规则，避免 Nginx 重复 location 错误。保留原有的 SSL、域名、日志及安全设置。执行宝塔 Nginx 配置检查后再 reload。

所有 API 和上传资源由 Java 后端监听本机 `127.0.0.1:8091`，更新器只监听本机 `127.0.0.1:8090`。不要把 8090、8091、5432、6379 开放到公网。

**已有宝塔部署：** 先检查旧 Java 服务是否占用 8091，再停止旧版服务并完成一次受控切换；若上传目录中已有文件，请在备份后将旧上传目录迁移到 `/opt/atlas-native/uploads` 并确保所有者为 `atlas-native-app`。不要让新旧两套服务同时对同一数据库进行不兼容的迁移。若旧系统有特殊环境变量、静态路径或反向代理，必须逐一核对后再切换。

## 4. 后续客户一键更新

超级管理员登录 Atlas Console → **版本与更新**，即可看到：

- 当前版本 / 最新 GitHub 正式版本。
- 更新说明和发布历史。
- 「一键更新至 vX.Y.Z」。
- 上一版本或历史版本回滚（只在本机保留对应文件时可用）。

后台调用本机更新器，更新器验证公开 GitHub Release 地址、资产名称、SHA256 清单和前端压缩包的文件类型/路径，按版本保存 `dist` 与 JAR。然后原子切换 `/opt/atlas-native/runtime/current` 链接、重启专用 systemd JAR 服务并检查健康。失败时尽可能恢复旧链接和旧服务，无需访问 GitHub 即可回滚。

**注意：应用回滚不是数据库回滚**。Flyway schema、数据库内容、Redis 和上传文件不会被旧 JAR 自动恢复。发布迁移时必须保证向前/向后兼容，并在升级前备份数据库及上传目录。

## 5. 目录与权限

| 项目 | 位置 |
|---|---|
| 当前发布链接 | `/opt/atlas-native/runtime/current` |
| 宝塔前端静态目录 | `/opt/atlas-native/runtime/current/frontend` |
| Java JAR | `/opt/atlas-native/runtime/current/backend.jar` |
| 上传文件 | `/opt/atlas-native/uploads` |
| 历史发布文件 | `/opt/atlas-native/releases/apps/` |
| 版本与回滚记录 | `/opt/atlas-native/releases/` |
| 更新执行器代码 | `/opt/atlas-native/agent/`（root 拥有） |
| 环境及授权配置 | `/etc/atlas-native/`（非公开） |
| 更新日志 | `/opt/atlas-native/releases/update-logs/` |

## 6. 排障和服务检查

```bash
sudo systemctl status atlas-native-updater.service atlas-native-backend.service
curl --silent --output /dev/null --write-out '%{http_code}\n' \
  http://127.0.0.1:8091/api/sys-user/auth/me
sudo journalctl -u atlas-native-backend.service -n 100 --no-pager
sudo journalctl -u atlas-native-updater.service -n 100 --no-pager
sudo ls -ltr /opt/atlas-native/releases/update-logs/
```

健康接口在**未登录**时应返回 **401**。如果无法连接 GitHub，检查服务器 DNS、HTTPS 出站、防火墙和 GitHub API 访问。更新器不需要 GitHub PAT，但 GitHub 公开 Release 依然受到公开 API 速率限制。

首次安装脚本为全新原生模式准备，不会尝试从未知的旧版宝塔目录自动迁移业务资产，也不会覆盖已经存在的 systemd 服务单元；老客户应先做一次受控迁移和备份。
