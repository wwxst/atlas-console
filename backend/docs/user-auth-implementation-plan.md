# 账号认证体系重构实施计划

> 状态：已实施。
>
> 本文记录[账号认证体系重构方案](user-auth-refactor-plan.md)的实施步骤，不改变已经确认的业务设计。

## 1. 实施边界

本次实施包含：

- 后端数据库结构、Redis 配置、公共 Token 与 Session、系统用户双 Token、普通用户认证和个人资料；
- 管理端前端的系统用户双 Token、自动 Refresh 和后端 Logout；
- 必要的自动测试、MySQL 与 Redis 联调；
- 实现完成并验收后更新 `docs/api.md`、README 和工程说明。

本次不包含普通用户前端，也不增加方案已排除的 Refresh rotation、会话索引、`rebindTicket`、验证码发送补偿和跨存储锁。

实施期间遵守以下规则：

1. 以 `user-auth-refactor-plan.md` 为业务和技术边界，不在编码时临时扩展需求。
2. 输入只在 HTTP 边界校验；唯一性由数据库索引保证，Service 负责转换业务错误。
3. 项目只面向全新数据库，不保留旧普通用户 `username` 登录兼容逻辑，不编写迁移脚本。各阶段在同一功能分支持续完成，不把中间状态单独部署。
4. 每个阶段完成针对性测试；全部阶段完成后再做全量构建和真实 MySQL、Redis 验收。
5. `docs/api.md` 只记录已实现接口，因此放在最后更新。

## 2. 实施前基线

开始实施前应再次确认基线，避免计划与代码状态脱节：

- 后端两类账号目前都是 `username + password`，登录只返回单个 Access Token；
- `JwtTokenService` 当前使用 `userType`，还没有 `accountType`、`sessionId`、`tokenType` 和 Refresh Token；
- 项目目前没有 Redis 依赖、Session 存储、验证码、注册、换绑和渠道配置；
- `user_account` 当前仍有 `username`，没有 `user_no`、`phone`、`email`、`password_version` 和 `avatar`；
- 管理端前端只保存 `atlas-token`，401 时直接清除 Token，退出时只清理本地状态；
- `docs/api.md` 描述的是当前已实现接口，在最终验收前保持不变。

## 3. 实施顺序

依赖关系如下：

```text
数据库与 Redis 基础
        |
        v
公共 Token 与 Session
        |
        +----> 系统用户双 Token + 管理端前端
        |
        v
渠道配置与验证码
        |
        v
普通用户认证
        |
        v
个人资料、改密与换绑
        |
        v
全量联调与文档更新
```

### 阶段一：数据库与 Redis 基础

**目标**

准备渠道配置表、数据访问层和 Redis 连接，使后续 Session、验证码和渠道配置有明确存储基础。

**修改范围**

- `pom.xml`：增加 Spring Data Redis 依赖；测试依赖只按实际测试需要添加。
- `src/main/resources/application.yml`：增加 Redis 连接配置；Access Token 改为 30 分钟，增加 Refresh Token 30 天配置。
- `src/main/java/com/web/project/config/properties/JwtProperties.java`：增加 Refresh Token 有效期。
- `web_project.sql`：增加 `sys_config` 及 4 项渠道初始配置。
- 新增 `SysConfig` 实体、Mapper 和 XML；此阶段只提供后续业务需要的查询与锁定更新语句。

**实施要点**

- 渠道配置更新所需的 `SELECT ... FOR UPDATE` 在 Mapper 中按固定顺序读取两个总开关。

**验证**

- Mapper XML 可加载，后端测试可以启动 Spring 上下文。
- 在可丢弃 MySQL 库执行全量建库脚本，检查 `sys_config` 结构及 4 项配置。
- 使用本地真实 Redis 验证应用可以连接并执行基本读写。

**完成条件**

新库得到正确的渠道配置；MySQL 与 Redis 均可供后续阶段使用。

### 阶段二：公共 Token 与 Session

**目标**

建立普通用户和系统用户共用的双 Token 与 Session 基础层，并确保资源接口只接受 Access Token。

**修改范围**

- `auth/service/JwtTokenService.java`：统一签发 Access/Refresh Token，使用方案规定的 claims。
- `config/JwtConfig.java`：保留资源服务器 Access Token 解码器，增加独立的 Refresh Token 校验入口。
- `config/SecurityConfig.java`：公开两类登录、Refresh 以及普通用户注册、验证码、重置密码接口；保护 Logout 和 `/me`。
- 新增 `AuthSessionService` 和 `AuthSessionStore`，统一创建、读取和删除 `auth:session:{accountType}:{sessionId}`。
- 新增共用 `TokenPairVO`、`RefreshTokenDTO` 和 Refresh 成功响应 DTO。
- `common/error/ErrorCode.java`：加入方案确定的认证错误码。

**实施要点**

- 公共 claims 固定为 `sub`、`accountType`、`sessionId`、`scope`、`tokenType`；普通用户额外带 `passwordVersion`。
- Access Token 默认 30 分钟，Refresh Token 默认 30 天；同一 Session 内 Refresh Token 保持不变。
- 资源服务器必须拒绝 `tokenType != access`；Refresh 入口必须拒绝 `tokenType != refresh`。
- Redis Session 缺失或 Redis 读取失败时不得签发新 Access Token。

**验证**

- 扩展 `JwtConfigTest` 和 `JwtSecurityTest`，覆盖签名、issuer、过期、账号类型、scope 和 Token 类型。
- 增加 Session 存取测试，覆盖两类账号 key 隔离、TTL、删除幂等和 Session 缺失。

**完成条件**

公共服务能够为两类账号创建 Session 和双 Token，Access/Refresh Token 不能互相替代。

### 阶段三：系统用户双 Token 与管理端同步

**目标**

先用系统用户完成一条端到端双 Token 链路，并同步现有管理端前端。

**后端修改范围**

- `SysUserAuthController`、`SysUserAuthService`、`SysUserAuthServiceImpl`：登录返回双 Token，增加 Refresh 和 Logout。
- `SysUserLoginVO`：替换为公共 Token 响应；系统用户资料继续由现有 `/api/sys-user/auth/me` 返回。
- `SysUserMapper`：复用现有按 ID 查询和状态字段，不修改系统用户登录凭据。

**前端修改范围**

- `src/features/auth/api.ts`：调整登录类型，增加 Logout 请求。
- `src/services/http.ts`：统一存取 Access/Refresh Token；使用独立客户端发起 Refresh，普通请求遇到 401 时调用一次 Refresh，然后重试原请求一次。
- `src/pages/LoginPage.tsx`：保存双 Token，随后调用 `/sys-user/auth/me` 获取资料。
- `src/layouts/TopBar.tsx`：调用后端 Logout，结束后清理两个 Token 和查询缓存。
- `src/router/RequireAdmin.tsx`：根据 Token 和 `/me` 结果处理登录状态。
- `frontend/tests/`：补充双 Token、401 Refresh、重试上限和 Refresh 失败清理状态的测试。

**实施要点**

- Refresh 请求自身不进入 401 重试流程，原业务请求最多重试一次。
- Logout 无论后端成功、Session 已不存在或网络失败，前端最终都清理本地登录状态。

**验证**

- 后端覆盖登录响应、Refresh、禁用账号拒绝 Refresh、Session 删除后拒绝 Refresh 和 Logout 幂等。
- 前端运行 `npm test`、`npm run lint` 和 `npm run build`。
- 手工验证登录、Access Token 过期后续期、刷新失败回登录页和退出。

**完成条件**

管理端使用双 Token 完成登录、自动续期和退出，现有 `/me`、系统用户列表及 `SCOPE_admin` 权限正常。

### 阶段四：渠道配置与验证码

**目标**

完成普通用户认证依赖的渠道开关、联系方式规范化、验证码存储和发送流程。

**修改范围**

- 新增渠道配置 Service 和管理端 Controller，对应 `/api/sys-user/auth-channels`。
- 新增联系方式类型、验证码场景及规范化工具；只承载方案列出的规则。
- 新增 `VerificationCodeStore`，集中实现验证码、发送锁、每日次数和失败次数 Redis key。
- 新增 `CodeSender` 接口及开发环境实现。
- 在 `UserAuthController` 增加 `/api/user/auth/send-code`。

**实施要点**

- 渠道配置直接查 MySQL，不增加缓存。
- 两个总开关的更新在一个事务中按固定顺序锁定并校验，禁止同时关闭。
- 发送锁使用 `SET NX EX 60`，每日次数使用 `INCR`，验证码和失败次数 TTL 为 5 分钟。
- 正确验证码的比较和删除由 `VerificationCodeStore` 原子完成；不引入 `sendId`、额度预留或失败补偿。
- 开发实现不得在生产日志打印验证码；真实短信和邮件供应商接入不在本次范围内。

**验证**

- 覆盖渠道关闭、验证码功能关闭、场景资格、60 秒发送间隔、每日 10 次上限、错误 5 次失效和正确验证码只成功一次。
- 在真实 Redis 检查 key、TTL 和 Asia/Shanghai 自然日过期时间。
- 覆盖并发修改渠道时不能把两个总开关都关闭。

**完成条件**

管理端可以查询和修改渠道；验证码发送与消费符合方案，Redis key 与 TTL 正确。

### 阶段五：普通用户注册与登录

**目标**

把普通用户从 `username + password` 切换为手机号或邮箱账号，完成注册、密码登录、验证码登录、Refresh 和 Logout。

**修改范围**

- 重写 `UserLoginDTO`、`UserLoginVO`、`UserAuthService` 和 `UserAuthServiceImpl`。
- 在 `UserAuthController` 增加注册、Refresh、重置密码和 Logout，移除旧 `/api/user/auth/me`。
- 新增注册、验证码登录、重置密码所需 DTO；只为实际接口创建类型。
- `web_project.sql`：把 `user_account` 定义为目标结构。
- `UserAccount`、Mapper 接口、Mapper XML、列表 VO 和查询逻辑：同步目标字段。
- `UserAccountMapper` 和 XML 增加按规范化手机号、邮箱和 ID 查询，以及账号创建和密码更新。
- `ErrorCode` 补齐普通用户认证业务错误。

**实施要点**

- `targetType` 决定查询手机号或邮箱，客户端不能提交账号类型、scope 或 Session key。
- `phone`、`email` 和 `user_no` 使用数据库唯一索引；项目只创建新库，不编写历史数据转换和兼容分支。
- 注册时生成随机 6 位 `user_no`；唯一索引冲突最多重试 5 次。
- 密码满足 8～72 个字符且 UTF-8 不超过 72 字节；无密码账号只能用验证码登录。
- 登录和注册成功统一调用公共 `AuthSessionService` 返回双 Token。
- 重置密码在一个 MySQL 事务中更新 BCrypt 密文并递增 `password_version`。

**验证**

- 覆盖手机号和邮箱注册、重复联系方式、`user_no` 冲突重试及上限。
- 覆盖密码与验证码登录、二选一校验、账号禁用、无密码账号、渠道开关。
- 覆盖普通用户 Refresh、Logout、Session 缺失和密码版本变化后旧 Refresh 失效。

**完成条件**

普通用户认证接口全部使用手机号或邮箱；代码、SQL 和响应中不再保留普通用户 `username` 登录路径。

### 阶段六：个人资料、修改密码与换绑

**目标**

完成普通用户登录后的资料维护、密码修改和联系方式换绑。

**修改范围**

- 新增 `UserProfileController`，承载 `/api/user/me`、资料、密码、手机号和邮箱接口。
- 新增或调整普通用户 Profile Service、DTO 和 `UserInfoVO`。
- 保持现有 `UserAccountController` 的 `/api/sys-user/users` 管理职责不变。

**实施要点**

- Controller 只从 JWT `sub` 读取当前账号 ID，不接受客户端传入用户 ID。
- 普通资料接口只修改 `nickname` 和 `avatar`；联系方式只能通过换绑接口修改。
- 登录态修改密码与忘记密码共用密码更新 Service，并在事务中递增 `password_version`。
- 换绑最终请求同时提交旧、新验证码；依次消费后在事务中更新联系方式。
- 账号必须始终至少保留手机号或邮箱之一，唯一冲突转换为方案规定的业务错误。

**验证**

- 覆盖查询和修改资料、越权隔离、密码修改后旧 Refresh 失效。
- 覆盖旧联系方式不属于当前用户、新联系方式已绑定、任一验证码失败和换绑成功。
- 覆盖换绑后账号仍至少有一种联系方式。

**完成条件**

`/api/user/me` 系列接口完整可用，旧 `/api/user/auth/me` 不再存在，管理端用户列表仍正常。

### 阶段七：全量联调与文档更新

**目标**

验证完整认证链路，在实现与方案一致后更新对外文档。

**执行内容**

1. 使用 JDK 25 执行后端 `mvnw.cmd clean verify`。
2. 执行前端 `npm test`、`npm run lint` 和 `npm run build`。
3. 在可丢弃 MySQL 和真实 Redis 上跑通系统用户、普通用户、验证码、Refresh、Logout、改密和换绑核心流程。
4. 检查日志和错误响应不包含密码、完整验证码、完整联系方式、JWT 或数据库内部错误。
5. 核对 `user-auth-refactor-plan.md` 的接口、字段、claims、TTL 和错误码均已实现。
6. 更新 `docs/api.md` 为实际接口；同步 README 接口速览和 `docs/engineering.md` 中已经变化的工程事实。

**完成条件**

- 后端、前端自动检查全部通过；
- MySQL 与 Redis 实际验收通过，不能用 mock 测试替代；
- 文档只描述最终实际行为；
- Git diff 中没有方案外功能、兼容代码、迁移脚本、调试日志或无关文件。

## 4. 最终验收清单

最终验收只检查以下结果：

- 系统用户仍以用户名和密码登录，普通用户只以手机号或邮箱注册、登录；
- 两类账号共用 Access/Refresh Token、Session、Refresh、Logout 和 JWT 校验；
- Access Token 与 Refresh Token 不能互相替代，两类账号的 Session 不能互相使用；
- 管理端在 Access Token 失效后刷新并只重试一次，退出时删除后端 Session 并清理本地 Token；
- 验证码的渠道开关、发送限制、失败次数和单次消费有效；
- 普通用户注册、两种登录、重置/修改密码、个人资料和换绑可用；
- 改密后旧 Refresh Token 无法续期，Logout 后当前 Session 无法续期；
- `docs/api.md`、README、数据库脚本和生产行为一致。

以上结果全部满足后，本次认证重构才算实施完成。
