# 账号后端

基于 Spring Boot、Spring Security、MyBatis、MySQL 和 Redis 的账号认证服务。

## 当前功能

- 系统用户使用用户名和密码登录；普通用户使用手机号或邮箱注册、登录。
- 两类账号共用 Access/Refresh Token、Session、Refresh、Logout 和 JWT 校验。
- 普通用户支持验证码、忘记密码、登录态修改密码、个人资料和联系方式换绑。
- 系统用户可查询和修改手机号、邮箱两个认证渠道的开关。
- 系统用户分页查询系统用户和普通用户列表，可按关键词、状态筛选，并可直接编辑普通用户昵称、手机号和邮箱。
- BCrypt 密码验证、JWT 认证及 `SCOPE_admin` / `SCOPE_user` 权限隔离。
- 本地/阿里云 OSS 存储配置、现有 Bucket 绑定管理及统一系统用户头像上传、访问和清理。

普通用户前端尚未实现；管理端前端已接入系统用户双 Token。

## 接口

完整请求、响应、参数和错误码说明见[接口文档](docs/api.md)。

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | `/api/sys-user/auth/login` | 公开 | 系统用户登录（双 Token） |
| POST | `/api/sys-user/auth/refresh` | 公开 | 系统用户刷新 Access Token |
| POST | `/api/sys-user/auth/logout` | `SCOPE_admin` | 系统用户退出登录 |
| GET | `/api/sys-user/auth/me` | `SCOPE_admin` | 当前系统用户 |
| GET | `/api/sys-user/sys-users` | `SCOPE_admin` | 系统用户列表 |
| GET | `/api/sys-user/users` | `SCOPE_admin` | 普通用户列表 |
| PUT | `/api/sys-user/users/{id}` | `SCOPE_admin` | 编辑普通用户昵称、手机号和邮箱 |
| PATCH | `/api/sys-user/users/{id}/status` | `SCOPE_admin` | 启用或停用普通用户 |
| GET | `/api/sys-user/auth-channels` | `SCOPE_admin` | 查询渠道配置 |
| PUT | `/api/sys-user/auth-channels/{channel}` | `SCOPE_admin` | 修改渠道配置 |
| POST | `/api/user/auth/send-code` | 公开 | 发送验证码 |
| POST | `/api/user/auth/register` | 公开 | 普通用户注册（双 Token） |
| POST | `/api/user/auth/login` | 公开 | 普通用户密码或验证码登录 |
| POST | `/api/user/auth/refresh` | 公开 | 普通用户刷新 Access Token |
| POST | `/api/user/auth/reset-password` | 公开 | 忘记密码 |
| POST | `/api/user/auth/logout` | `SCOPE_user` | 普通用户退出登录 |
| GET | `/api/user/me` | `SCOPE_user` | 查询个人资料 |
| PUT | `/api/user/me` | `SCOPE_user` | 修改昵称和头像 |
| PUT | `/api/user/me/password` | `SCOPE_user` | 修改密码 |
| PUT | `/api/user/me/phone` | `SCOPE_user` | 换绑手机号 |
| PUT | `/api/user/me/email` | `SCOPE_user` | 换绑邮箱 |

响应统一为 `Result<T>`，分页数据为 `PageResult<T>`。Access Token 默认 30 分钟，Refresh Token 默认 30 天。

## 目录

```text
src/main/java/com/web/project/
├── admin/       系统用户列表、实体与 Mapper
├── user/        普通用户账号、列表、个人资料与 Mapper
├── auth/        两类账号的登录、Token、会话、验证码与 JWT 签发
├── channel/     渠道配置（sys_config）实体、Mapper 与管理端接口
├── common/      错误与响应结构
├── config/      认证、安全、Redis 与配置
└── WebProjectApplication.java
src/main/resources/mapper/
├── admin/
├── channel/
└── user/
```

## 启动

1. 使用 IntelliJ IDEA 为项目配置的 JDK，具体步骤见[后端工程规范](docs/engineering.md)。
2. 准备 MySQL 和 Redis，并创建空的 `web_project` 数据库。应用启动时 Flyway 会执行 `src/main/resources/db/migration/V1__initial_schema.sql`，创建 5 张业务表并写入初始系统账号和配置；新安装不再手动导入 `web_project.sql`。首次对外开放前必须修改默认系统管理员密码。
3. 数据库连接可通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指定；Redis 连接可通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`、`REDIS_PASSWORD` 指定，默认连接 `localhost:6379` 的 0 号库。
4. 本地开发可以直接启动，`src/main/resources/application.yml` 已提供本地 JWT 默认密钥；需要替换密钥时，在 IDEA 启动配置或 Maven Runner 中设置 `JWT_SECRET`。
5. 运行 `WebProjectApplication`，或在 Maven 面板执行 `spring-boot:run`。默认地址为 `http://localhost:8080`。

密钥生成方法及构建验证命令见[后端工程规范](docs/engineering.md)。

本地上传目录用 `UPLOAD_DIR` 指定，默认 `uploads`。接入 OSS 前设置 `STORAGE_ENCRYPTION_KEY` 为 Base64 编码的32字节随机密钥，并长期保留该密钥；更换后已保存的 OSS Secret 不能解密，需要重新保存凭据。通过存储设置页面保存一套凭据、绑定或同步已有 Bucket、选定仅允许公共读（PublicRead）的默认空间，再保存全局阿里云存储方式。详细行为见[存储数据库设计](docs/存储数据库设计.md)。

## 数据库

数据库结构和初始数据由 Flyway 版本迁移管理，详见[数据库迁移指南](docs/database-migrations.md)。`web_project.sql` 仅保留为旧版手动初始化快照；复用旧数据库前必须先备份、核对 schema，并按指南显式 baseline。
