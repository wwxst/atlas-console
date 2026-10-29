# 账号认证体系重构方案

> 状态：已实现。
>
> 本文只描述当前要做的业务和必要约束。`docs/api.md` 继续只记录生产代码中已经存在的接口；本方案完成并验收后再同步更新。

## 1. 目标与范围

普通用户账号由 `username + password` 调整为手机号或邮箱账号，支持：

- 手机号或邮箱注册；
- 密码登录或验证码登录；
- 发送和校验验证码；
- Access Token + Refresh Token；
- 忘记密码和登录态修改密码；
- 查询、修改个人资料；
- 换绑手机号或邮箱；
- 管理端启停手机号、邮箱及其验证码能力。

系统用户继续使用用户名和密码登录，但与普通用户共用双 Token、Session、Refresh、Logout 和 JWT 校验能力。系统用户不增加注册、验证码、换绑等普通用户功能。

现有管理端前端必须同步接入系统用户双 Token：保存 Access/Refresh Token、在 Access Token 失效时调用 Refresh 并重试一次原请求、退出时调用后端 Logout。普通用户前端尚不存在，不在本轮实现。

本轮不实现：

- 登录设备列表、踢出指定设备、退出全部设备；
- Refresh Token rotation、复用检测和令牌黑名单；
- 国际手机号；
- 验证码发送历史和统计后台；
- 人工申诉与账号恢复；
- 旧接口和旧字段兼容。

## 2. 账号模型

### 2.1 user_account

`user_account` 调整为：

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | 主键 | 内部用户 ID |
| `user_no` | VARCHAR(32) | NOT NULL, UNIQUE | 对外展示号码，不参与登录和鉴权 |
| `phone` | VARCHAR(20) | NULL, UNIQUE | 中国大陆手机号 |
| `email` | VARCHAR(100) | NULL, UNIQUE | 邮箱 |
| `password` | VARCHAR(100) | NULL | BCrypt 密文；验证码注册时可为空 |
| `password_version` | BIGINT UNSIGNED | NOT NULL DEFAULT 0 | 修改或重置密码时递增 |
| `nickname` | VARCHAR(50) | NULL | 昵称 |
| `avatar` | VARCHAR(255) | NULL | 头像地址 |
| `status` | TINYINT | NOT NULL DEFAULT 1 | `0` 禁用，`1` 正常 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

账号至少绑定手机号或邮箱之一。唯一性由数据库唯一索引保证，Service 只把唯一键冲突转换为业务错误。

项目只面向全新数据库，因此数据库、实体、Mapper 和接口直接使用目标结构，不保留 `username` 兼容逻辑或迁移脚本。

### 2.2 user_no

`user_no` 只用于展示，不得作为登录凭据或权限依据。

当前随机生成 6 位数字。数据库唯一索引冲突时重新生成，最多尝试 5 次；超过上限返回服务器错误。

本轮不实现日期号段、特殊号码过滤和 `USER_NO_SECRET`。

### 2.3 联系方式

所有查询、写入和 Redis key 都使用规范化后的值：

- 手机号：去除首尾空格，只接受 11 位中国大陆手机号；
- 邮箱：去除首尾空格并转为小写；
- 日志和错误响应不得输出完整联系方式。

本轮不引入国际号码解析依赖。以后需要支持国际手机号时再扩展。

### 2.4 sys_user

系统用户继续使用现有 `sys_user` 表、用户名、BCrypt 密码、状态和 `SCOPE_admin` 权限。本轮不改变系统用户登录凭据，也不新增注册和验证码能力。

系统用户当前没有修改密码接口，因此不为 `sys_user` 增加 `password_version`。Refresh 时只检查账号存在且状态正常。

## 3. 渠道配置

在 `sys_config` 保存 4 个布尔配置：

| key | 说明 |
|-----|------|
| `auth.phone.enabled` | 手机号注册、登录和绑定是否可用 |
| `auth.phone.code.enabled` | 手机验证码是否可用 |
| `auth.email.enabled` | 邮箱注册、登录和绑定是否可用 |
| `auth.email.code.enabled` | 邮箱验证码是否可用 |

规则：

- 渠道总开关关闭时，该渠道所有普通用户认证能力不可用；
- 验证码开关关闭时，只禁止验证码能力，密码登录仍可使用；
- 手机和邮箱总开关不能同时关闭；
- 修改配置时在一个数据库事务中按固定顺序使用 `SELECT ... FOR UPDATE` 锁定手机号、邮箱两个总开关，再校验和更新；
- 本轮直接读取 MySQL，不增加配置缓存。

管理端接口：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/sys-user/auth-channels` | `SCOPE_admin` | 查询渠道配置 |
| PUT | `/api/sys-user/auth-channels/{channel}` | `SCOPE_admin` | 修改 `phone` 或 `email` 配置 |

## 4. 验证码

### 4.1 Redis key

| key | TTL | 用途 |
|-----|-----|------|
| `auth:code:{scene}:{type}:{target}` | 5 分钟 | 当前验证码 |
| `auth:code:lock:{scene}:{type}:{target}` | 60 秒 | 发送间隔 |
| `auth:code:daily:{type}:{target}:{yyyyMMdd}` | 当日结束 | 每日发送次数 |
| `auth:code:fail:{scene}:{type}:{target}` | 5 分钟 | 校验失败次数 |

`scene` 只允许：

- `REGISTER`
- `LOGIN`
- `RESET_PASSWORD`
- `REBIND_OLD`
- `REBIND_NEW`

### 4.2 发送流程

1. 校验请求字段并规范化联系方式。
2. 校验渠道总开关、验证码开关和场景资格。
3. 使用 `SET ... NX EX 60` 获取发送锁；获取失败时返回 `CODE_SEND_TOO_FREQUENT`。
4. 使用 `INCR` 增加每日计数；首次增加时设置到当日结束的 TTL，返回值超过 10 时删除本次发送锁并返回 `CODE_SEND_LIMIT_EXCEEDED`。
5. 生成 6 位数字验证码并写入验证码 key，TTL 为 5 分钟。
6. 调用 `CodeSender` 发送。发送失败时返回服务器错误，Redis key 等待 TTL 自动过期，不执行补偿删除。

每日计数按 Asia/Shanghai 自然日计算，TTL 设置到当天结束。当前 `CodeSender` 可以是开发环境打桩实现，但不得在生产日志中打印验证码。

这里不实现发送额度预留、`sendId`、补偿脚本或 Redis Cluster 适配。少量发送失败仍占用一次每日额度，是本轮接受的取舍。

### 4.3 校验流程

1. 读取验证码；不存在时返回 `VERIFICATION_CODE_INVALID`。
2. 验证码错误时增加失败次数；达到 5 次后删除验证码。
3. 验证码正确时删除验证码和失败次数，校验成功。

验证码存取集中在 `VerificationCodeStore`，业务 Service 不直接拼接 Redis key。正确验证码的比较和删除必须作为一次原子存储操作，确保同一验证码只有一个请求成功。

## 5. 共用 Token 与会话

普通用户和系统用户共用 `PasswordEncoder`、`JwtTokenService`、`AuthSessionService`、`AuthSessionStore`、Token DTO、有效期配置、JWT 校验、Refresh 流程和 Logout 流程。两类登录 Service 只负责校验各自凭据，成功后调用公共会话服务签发 Token。

### 5.1 Token

两类账号登录成功后，以及普通用户注册成功后，统一返回：

```json
{
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>"
}
```

| Token | 默认有效期 | 用途 |
|-------|------------|------|
| Access Token | 30 分钟 | 调用对应权限的业务接口 |
| Refresh Token | 30 天 | 获取新的 Access Token |

公共 claims：

| claim | 值 |
|-------|----|
| `sub` | 账号主键的字符串形式 |
| `accountType` | `SYS_USER` 或 `USER` |
| `sessionId` | 当前登录会话 |
| `scope` | `admin` 或 `user` |
| `tokenType` | `access` 或 `refresh` |

普通用户 Token 额外包含 `passwordVersion`。系统用户当前没有密码版本字段，不携带该 claim。

`sub` 继续作为两个 `/me` 接口读取账号 ID 的唯一来源，不再增加重复的 `accountId` claim。资源服务器只接受 `tokenType=access`，Refresh 接口只接受 `tokenType=refresh`。账号类型决定查询哪张表，scope 决定接口权限，两者不得由客户端请求参数指定。

### 5.2 Session

每次成功登录或注册生成新的 `sessionId`，并写入：

```text
auth:session:{accountType}:{sessionId} = { accountId, passwordVersion? }
TTL = Refresh Token 有效期
```

`passwordVersion` 只在普通用户 Session 中保存。两类账号都可以有多个 Session；每个 Session 独立过期，不维护账号会话索引，也不预留设备管理结构。

Refresh Token 在一个 Session 生命周期内保持不变，不做 rotation。这样不需要 `jti`、CAS、复用检测或前端 single-flight。

接受的取舍：Refresh Token 泄露后，在 Session 被删除、密码版本变化或 Token 到期前可能被重复使用。因此客户端必须安全保存 Refresh Token，服务端不得记录明文 Token。

### 5.3 Refresh

普通用户使用 `POST /api/user/auth/refresh`，系统用户使用 `POST /api/sys-user/auth/refresh`。请求结构相同：

```json
{ "refreshToken": "<refreshToken>" }
```

处理流程：

1. 校验 JWT 签名、issuer、过期时间和 `tokenType=refresh`。
2. 校验 `accountType` 与当前接口路径一致：系统用户接口只接受 `SYS_USER`，普通用户接口只接受 `USER`。
3. 将 `sub` 解析为 `accountId`，再根据 `accountType + sessionId` 读取 Session 并校验账号 ID。
4. 根据 `accountType` 查询 `sys_user` 或 `user_account`，确认账号存在且状态正常。
5. 普通用户继续校验数据库 `password_version` 与 Token、Session 一致；系统用户跳过版本校验。
6. 返回新的 Access Token；Refresh Token 保持不变。

成功响应：

```json
{
  "accessToken": "<newAccessToken>"
}
```

Session 不存在、普通用户密码版本不一致或 Token 无效时统一返回 `REFRESH_TOKEN_INVALID`。Redis 故障时返回服务器错误，不跳过会话校验。

这里不持有 MySQL 锁等待 Redis，也不追求密码修改与并发 Refresh 之间的跨存储强一致。已经签发的 Access Token 最长仍可使用 30 分钟，这是无状态 Access Token 的既定边界。

### 5.4 Logout

普通用户使用 `POST /api/user/auth/logout`，系统用户使用 `POST /api/sys-user/auth/logout`。两个接口都携带对应的 Access Token，不需要请求体。

服务端从 Access Token 取得 `accountType + sessionId`，删除对应 Session key。接口保持幂等。退出后 Access Token 在剩余有效期内仍可使用，但 Refresh Token 不能继续续期。

### 5.5 密码变化

重置或修改密码时，在同一个 MySQL 事务内更新 BCrypt 密文并递增 `password_version`。

不枚举和清理 Redis 中的全部 Session。旧 Refresh Token 因版本不一致无法续期，Session key 等待 TTL 自动删除。旧 Access Token 最长仍可使用 30 分钟。

系统用户当前没有修改密码接口，不增加密码版本逻辑；如果以后增加修改密码功能，再同时补充系统用户密码版本或主动删除相关 Session。

## 6. 接口设计

### 6.1 接口清单

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/sys-user/auth/login` | 匿名 | 系统用户密码登录，返回双 Token |
| POST | `/api/sys-user/auth/refresh` | 匿名 | 系统用户 Refresh Token 换取 Access Token |
| POST | `/api/sys-user/auth/logout` | `SCOPE_admin` | 删除系统用户当前 Session |
| POST | `/api/user/auth/send-code` | 按场景 | 发送验证码 |
| POST | `/api/user/auth/register` | 匿名 | 注册并返回双 Token |
| POST | `/api/user/auth/login` | 匿名 | 密码或验证码登录 |
| POST | `/api/user/auth/refresh` | 匿名 | Refresh Token 换取 Access Token |
| POST | `/api/user/auth/reset-password` | 匿名 | 忘记密码 |
| POST | `/api/user/auth/logout` | `SCOPE_user` | 删除当前 Session |
| GET | `/api/user/me` | `SCOPE_user` | 查询个人资料 |
| PUT | `/api/user/me` | `SCOPE_user` | 修改昵称和头像 |
| PUT | `/api/user/me/password` | `SCOPE_user` | 修改密码 |
| PUT | `/api/user/me/phone` | `SCOPE_user` | 换绑手机号 |
| PUT | `/api/user/me/email` | `SCOPE_user` | 换绑邮箱 |

系统用户登录请求仍为用户名和密码，但响应改为只返回 `accessToken + refreshToken`。管理端前端登录成功后保存两个 Token，再调用现有 `GET /api/sys-user/auth/me` 获取并缓存 `id`、`username`、`nickname`。系统用户管理接口保持不变。

两类登录和 Refresh 接口允许匿名访问。普通用户注册、发送验证码和重置密码同样允许匿名访问；`REBIND_OLD`、`REBIND_NEW` 虽共用发送验证码接口，但必须有有效普通用户 Access Token。

### 6.2 发送验证码

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "scene": "LOGIN"
}
```

场景资格：

- `REGISTER`：目标尚未注册；
- `LOGIN`、`RESET_PASSWORD`：目标已经注册；
- `REBIND_OLD`：目标属于当前用户；
- `REBIND_NEW`：目标未被其他用户绑定。

### 6.3 注册

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "password": "optionalPassword"
}
```

密码可选；有值时长度为 8～72 个字符。注册成功后创建账号和 Session，返回双 Token。

### 6.4 登录

密码登录：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "password": "password"
}
```

验证码登录：

```json
{
  "target": "user@example.com",
  "targetType": "EMAIL",
  "code": "123456"
}
```

`password` 与 `code` 必须且只能提供一个。账号禁用时拒绝登录；没有密码的账号只能使用验证码登录。

### 6.5 重置和修改密码

忘记密码：

```json
{
  "target": "13800138000",
  "targetType": "PHONE",
  "code": "123456",
  "newPassword": "newPassword"
}
```

登录态修改密码使用相同字段，但目标必须属于当前用户。两个接口共用密码更新 Service。

### 6.6 换绑联系方式

换绑不使用 `rebindTicket`。客户端分别获取旧联系方式和新联系方式的验证码，最终一次提交。

换绑手机号：

```json
{
  "oldTarget": "old@example.com",
  "oldTargetType": "EMAIL",
  "oldCode": "123456",
  "newPhone": "13900139000",
  "newCode": "654321"
}
```

换绑邮箱结构相同，将 `newPhone` 改为 `newEmail`。

服务端确认旧联系方式属于当前用户，依次校验并消费两个验证码，然后在数据库事务中更新联系方式。新联系方式的唯一性仍由数据库索引保证。

如果第二个验证码校验失败，第一个已经消费的验证码不恢复，用户需要重新发送。这是简化流程接受的交互成本。

### 6.7 个人资料

`GET /api/user/me` 返回 `id`、`userNo`、`phone`、`email`、`nickname`、`avatar`、`status`、`createdAt`，不返回密码。

`PUT /api/user/me` 只修改 `nickname` 和 `avatar`。手机号和邮箱必须走换绑接口。

## 7. 输入约束

| 字段 | 约束 |
|------|------|
| `targetType` | `PHONE` 或 `EMAIL` |
| `code`、`oldCode`、`newCode` | 6 位数字 |
| `password`、`newPassword` | 8～72 个字符，且 UTF-8 编码不超过 72 字节 |
| `nickname` | 最多 50 个字符 |
| `avatar` | 最多 255 个字符，可为空 |

输入格式在 DTO 边界校验一次，Service 使用已经校验和规范化的数据，不重复增加防御性判断。

## 8. 错误码

沿用现有通用错误码，并增加：

| HTTP | 业务码 | 名称 |
|------|--------|------|
| 400 | `40010` | `AUTH_CHANNEL_DISABLED` |
| 400 | `40011` | `CODE_FUNCTION_DISABLED` |
| 400 | `40012` | `CODE_SEND_TOO_FREQUENT` |
| 400 | `40013` | `CODE_SEND_LIMIT_EXCEEDED` |
| 400 | `40014` | `VERIFICATION_CODE_INVALID` |
| 400 | `40015` | `TARGET_ALREADY_REGISTERED` |
| 400 | `40016` | `TARGET_NOT_REGISTERED` |
| 400 | `40017` | `TARGET_ALREADY_BOUND` |
| 400 | `40018` | `CANNOT_DISABLE_ALL_CHANNELS` |
| 401 | `40104` | `REFRESH_TOKEN_INVALID` |

验证码错误与过期统一返回 `VERIFICATION_CODE_INVALID`。登录失败继续使用现有 `INVALID_CREDENTIALS`，避免暴露账号是否存在。

## 9. 代码改动范围

主要新增：

- Redis 依赖和连接配置；
- `SysConfig`、Mapper 和渠道配置 Service；
- 公共 `TokenPairVO`、`RefreshTokenDTO` 和两类 Logout 接口；
- `AuthSessionService`、`AuthSessionStore`；
- 注册、验证码、密码和换绑 DTO；
- `VerificationCodeStore`、`CodeSender`；
- 普通用户认证与个人资料 Service；
- Access/Refresh Token 签发与独立校验。

主要修改：

- `UserAccount`、Mapper、VO 和查询条件；
- `SysUserAuthController`、`SysUserAuthService` 和登录响应；
- `UserAuthController`；
- `JwtTokenService`、`JwtConfig`、`SecurityConfig`；
- `ErrorCode`；
- `web_project.sql`。

保持现有 `UserAccountController` 的 `/api/sys-user/users` 管理端职责不变；新增 `UserProfileController` 承载 `/api/user/me` 及其资料、密码和换绑接口。

管理端前端同步修改：

- `src/features/auth/api.ts`：登录响应改为双 Token，增加 Logout 请求；
- `src/services/http.ts`：分别保存 Access/Refresh Token，请求携带 Access Token；使用独立客户端发起 Refresh，普通请求遇到 401 时刷新并重试一次，Refresh 失败时清理登录状态；
- `src/pages/LoginPage.tsx`：保存双 Token，登录后调用 `/sys-user/auth/me` 获取当前系统用户；
- `src/layouts/TopBar.tsx`：退出时调用后端 Logout，并在结束后清理本地 Token 和查询缓存；
- `src/router/RequireAdmin.tsx`：以 Access/Refresh Token 状态和 `/me` 结果判断登录状态。

删除：

- 普通用户 `username` 字段和相关登录逻辑；
- 旧的 `/api/user/auth/me` 路径；
- 已废弃的验证码或渠道专用表设计。

## 10. 实施与验收

实施顺序：

1. 修改全新数据库结构、实体和 Mapper。
2. 增加 Redis、验证码和渠道配置。
3. 完成共用 Token、Session、Refresh 和 Logout，并接入系统用户登录。
4. 完成普通用户注册、登录、重置密码、修改密码、个人资料和换绑。
5. 同步管理端前端的双 Token、自动 Refresh 和 Logout。
6. 更新安全配置和错误码。
7. 更新 `docs/api.md`、`README.md` 和必要的工程说明。

测试至少覆盖：

- 手机号和邮箱注册；
- 密码与验证码两种登录；
- 验证码过期、错误次数、发送间隔和每日上限；
- Access/Refresh Token 不能互相替代；
- 系统用户登录返回双 Token，Refresh 和 Logout 可用；
- 管理端前端可保存双 Token，Access Token 失效后刷新并只重试一次原请求；
- 管理端退出会删除服务端 Session 并清理本地两个 Token；
- 两类账号的 Session key 相互隔离；
- 任一账号的 Session 删除后 Refresh 失败；
- 系统用户禁用后不能 Refresh；
- 密码版本变化后旧 Refresh 失败；
- 换绑必须同时通过旧、新联系方式验证码；
- 联系方式唯一键冲突；
- 两个渠道总开关不能同时关闭；
- 系统用户 `/me`、列表和 `SCOPE_admin` 权限保持正常。

最终运行 `mvnw.cmd clean verify`，并在可丢弃的 MySQL 测试库和真实 Redis 上验证核心流程。Mock 测试通过不能替代数据库和 Redis 验收。

## 11. 明确取舍

- Refresh Token 不轮换，降低实现和客户端复杂度；泄露窗口由 Session 删除、密码版本和 30 天有效期控制。
- 不维护账号 Session 索引，暂不支持会话列表和退出全部设备。
- 改密后不主动清理全部 Redis Session，由 `password_version` 阻止旧 Refresh 续期。
- 验证码发送失败仍可能占用当日额度，不建设跨发送方与 Redis 的补偿机制。
- 换绑不使用临时 Ticket；部分失败时用户可能需要重新获取验证码。
- 无状态 Access Token 无法即时吊销，退出或改密后最长仍可使用 30 分钟。

以上取舍满足当前功能范围。只有出现明确产品需求或真实故障后，才增加 rotation、会话索引、发送补偿或更强的并发控制。
