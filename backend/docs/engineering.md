# 后端工程规范

## 开发文档入口

- [AI Java 开发规范](ai-java-development.md)定义代码、测试和文档的编写要求：默认清晰、直接、便于维护，教学式解释按学习需求提供。
- 本文定义项目业务边界、技术环境、构建命令和现有测试能力。
- [后端质量门禁](quality-gates.md)定义每次修改后的检查流程、测试触发条件、通过标准和交付记录；审查轮次与验收标准只在该文档维护。

## 当前业务范围

包含系统用户与普通用户两类账号的完整认证链路：系统用户以用户名和密码登录，并支持系统用户的查询、新增、编辑、启停用、密码重置和删除；普通用户以手机号或邮箱注册、登录，并支持验证码、忘记密码、登录态修改密码、个人资料和联系方式换绑；系统用户可管理手机号、邮箱两个认证渠道的开关，并直接编辑普通用户昵称、手机号和邮箱（包括禁用账号，至少保留一种联系方式）。当前实现不包含普通用户前端。后续开发以该范围为准，新增业务需有明确需求。

系统用户接口使用 `/api/sys-user/**`，普通用户接口使用 `/api/user/**`。接口路径、请求参数和响应结构以[接口文档](api.md)为准，README 只保留接口速览。

数据库表 `sys_user` 对应的 Java 类型统一使用 `SysUser` 前缀，包括 Controller、DTO、Service、Mapper 和 VO；接口路径统一使用 `/api/sys-user/**`。`admin` 只作为 JWT scope 权限值保留，不作为系统用户领域类型名。

两类账号共用 `PasswordEncoder`、`JwtTokenService`、`AuthSessionService`、`AuthSessionStore`、Token DTO、有效期配置、JWT 校验、Refresh 和 Logout 流程。JWT 公共 claims 为 `sub`、`accountType`、`sessionId`、`scope`、`tokenType`；普通用户额外携带 `passwordVersion`，系统用户额外携带 `authVersion`；`sub` 保存账号主键。会话 key 为 `auth:session:{accountType}:{sessionId}`。两类账号的每个受保护请求都必须存在有效 Redis 会话，因此 Logout 后旧 Access Token 立即失效；系统用户还在每个请求上读取数据库状态和认证版本，禁用、密码重置后旧 Access Token 也立即失效。Refresh 同时核对数据库、JWT 和 Redis 中的版本。资源接口只接受 Access Token，Refresh 接口只接受 Refresh Token，两类账号的会话通过 `accountType` 段相互隔离。

普通用户 `user_account.id` 由服务端生成 Snowflake ID，数据库使用 `BIGINT UNSIGNED`，Java 使用 `Long`。普通用户资料、管理列表和编辑响应中的 `id` 以十进制 JSON 字符串返回；前端应保留原字符串用于列表标识及编辑、状态切换路径，不转换为 JavaScript `Number`，避免超出安全整数范围后丢失精度。`userNo` 是独立的对外展示号码，不参与登录和鉴权。

## 最小实现原则

普通用户管理支持 `PATCH /api/sys-user/users/{id}/status` 启停用。单条 SQL 仅更新状态，
沿用数据库匹配行数语义处理重复提交与用户不存在；认证流程继续通过现有账号状态检查拒绝禁用用户。

后端以满足当前业务需求的最小正确实现为准，禁止过度防御编程。此原则同样适用于生产代码、测试、脚本和文档。

- 每个分支、抽象和依赖都必须有当前调用方或可达的失败场景；不为假想需求预留框架、兼容层或扩展点。
- 输入格式在 HTTP、配置等外部边界校验一次。内部代码遵循已建立的契约，不逐层重复判空、校验或补默认值。
- 唯一性等规则交给数据库约束执行，在业务边界转换必要的错误；不以重复查询、应用锁或重试叠加同一保证。
- 只捕获需要转换或确实能恢复的异常；不吞异常，不将失败替换为成功、空列表或默认对象。
- 鉴权、业务状态和事务约束按实际行为保留。删除逻辑前检查调用方及 MyBatis 映射，不以行数更少代替正确性。
- 测试覆盖关键行为与真实失败路径，不为已移除的分支、实现细节或覆盖率数字增加测试，也不为测试新增生产抽象。

## Java 运行环境

本项目统一使用 **JDK 25**。Java 版本以 `pom.xml` 中的 `java.version` 为准，当前值为 `25`。

开发、运行、调试和测试统一使用 IntelliJ IDEA 为项目配置的 JDK 25；命令行和 Agent 也复用这套 JDK：

1. 在 `File > Project Structure > Project` 中将 `Project SDK` 设置为 JDK 25。
2. 在 `Settings > Build, Execution, Deployment > Build Tools > Maven > Runner` 中将 `JRE` 设置为同一个 JDK 25。
3. Maven 导入、IDEA 的测试运行器和 Spring Boot 启动配置都必须使用该 JDK 25。

命令行 `mvnw.cmd` 只在 `JAVA_HOME` 指向同一个 JDK 25 时使用。不得用其他 JDK 版本运行本项目的构建或测试，以免 IDEA、Maven 和编译产物使用不同的 Java 版本。

### 命令行与 Agent 执行规则

- `java` 不在 `PATH` 或 `JAVA_HOME` 未配置，不代表机器未安装 JDK。先查看 `.idea/misc.xml` 中的 `project-jdk-name`，再从 IDEA 的 SDK 配置定位对应安装目录。IDEA 下载的 JDK 通常位于 `%USERPROFILE%\.jdks`。
- 使用该目录下的 `bin\java.exe -version` 确认主版本为 25，再为当前命令进程设置 `JAVA_HOME` 并调用项目的 Maven Wrapper。
- 不因终端环境变量缺失而另装 JDK、降级项目 Java 版本或修改系统级环境变量。
- JDK 安装路径属于本机配置，不写入 `pom.xml`。本机已核实 IDEA 的 `temurin-25` 对应 `C:\Users\ww\.jdks\temurin-25.0.4.1`；升级或换机后重新查找。

PowerShell 示例（路径须与本机 IDEA 配置一致）：

```powershell
$env:JAVA_HOME = 'C:\Users\ww\.jdks\temurin-25.0.4.1'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd test
```

## 构建与验证

在 IntelliJ IDEA 的 Maven 面板中执行：

- `clean`：清理旧的编译产物；
- `test`：运行项目测试；
- `package`：验证可交付的 Spring Boot 构建产物。

开发阶段可用 `test` 验证相关逻辑，用 `package` 检查产物；后端代码、配置、依赖或 SQL 修改的最终交付统一执行 `.\mvnw.cmd verify`，并完成质量门禁要求的适用测试。

命令行统一使用项目 Maven Wrapper，`.\mvnw.cmd verify` 包括测试与打包。测试中的专用密钥通过测试注解注入，不作为应用启动默认值。

现有自动验证的边界：

- `GlobalExceptionHandlerTest`：真实 MVC 参数绑定、400/405/415、业务错误及不泄露内部信息的 500 响应。
- `JwtSecurityTest`：真实 JWT 签名、解码与 Spring Security 过滤链；验证角色隔离、Access/Refresh 不能互相替代、过期、签发者、错误签名及 Access 30 分钟 / Refresh 30 天有效期。业务 Service 使用 mock，不访问数据库。
- `JwtConfigTest` 和 `PasswordEncoderTest`：无效签名密钥拒绝、密码验证成功与失败。
- `AuthSessionStoreTest` 和 `VerificationCodeStoreTest`：在本地真实 Redis 上验证会话 key 隔离、TTL、删除幂等，以及验证码、发送锁、每日次数和失败次数。本机缺少 Redis 时按 JUnit assumption 跳过。
- `AuthSessionServiceTest`：在真实 Redis 上验证会话创建、Refresh、Logout，以及账号类型不匹配、会话删除、账号禁用和密码版本变化时拒绝续期；账号查询使用 mock Mapper。
- `UserAuthServiceImplTest`、`VerificationCodeServiceTest`、`AuthChannelServiceImplTest`、`UserProfileServiceImplTest`、`UserAccountServiceImplTest`：注册、登录、验证码、渠道开关、改密、换绑及管理端编辑普通用户的业务规则与错误路径。
- `ContactNormalizerTest` 和 `PasswordPolicyTest`：联系方式规范化与密码长度、UTF-8 字节边界。
- `SensitiveDataLoggingTest`：敏感对象及响应容器的打印不暴露凭据或完整联系方式，Token 的 JSON 契约不变。
- `VerificationCodeStoreFailureTest`：Redis 无返回结果时抛出异常，不转换为频率限制、零计数或验证码错误。
- `MapperIntegrationTest`：在显式指定的可丢弃 PostgreSQL 库上验证字段映射、写入行数、重复更新、列表不读取密码及渠道更新失败的事务回滚。设置 `ATLAS_POSTGRES_TEST_URL` 后执行，未设置时跳过。
- `AuthConcurrencyIntegrationTest`：在真实 Redis 和 PostgreSQL 上同时竞争同一验证码、发送锁、每日计数、联系方式唯一索引和渠道开关；对成功数量与最终数据作断言。设置 `ATLAS_POSTGRES_TEST_URL` 后执行，未设置时跳过。
- `WebProjectApplicationTests`：Spring 上下文装配；不等同于数据库连通性或业务接口验收。

`mvnw.cmd verify` 中的会话与验证码测试需要本机真实 Redis；缺少 Redis 时这些测试跳过，其余测试仍会执行。

涉及数据库结构或查询的修改，需要单独在可丢弃的 PostgreSQL 测试库验证；不得将 mock 测试通过当作真实数据库验证。

PostgreSQL 集成测试先建立空的、可丢弃的独立数据库，再设置 `ATLAS_POSTGRES_TEST_URL` 为该库的 JDBC URL；Spring Boot 测试上下文会通过 Flyway 执行版本迁移。使用现有 `DB_USERNAME`、`DB_PASSWORD` 配置测试凭据。只连接可丢弃库，测试会修改配置数据。

### 当前工具能力与验收边界

`pom.xml` 使用 JaCoCo 0.8.15 支持 JDK 25；`mvnw.cmd verify` 生成 `target/site/jacoco/index.html` 和 `jacoco.xml`，不设置统一覆盖率百分比门槛，也不排除业务代码。JaCoCo 自身过滤编译器/Lombok 生成的部分代码，统计以报告中的可分析代码为准。Maven 成功只证明该次构建和测试，不能代替完整门禁。

Windows 本机完整测试入口为 `scripts/verify_backend.py`，仅使用 Python 标准库。需要 Python 3、JDK 25、本机 PostgreSQL 17、psql 客户端和 Redis 可执行文件。在 `backend` 目录执行。测试账号分为应用角色（`DB_USERNAME`/`DB_PASSWORD`）和有权创建/删除临时数据库的管理角色（`POSTGRES_ADMIN_USERNAME`/`POSTGRES_ADMIN_PASSWORD`）：

```powershell
python scripts/verify_backend.py `
  --java-home C:/Users/ww/.jdks/temurin-25.0.4.1 `
  --PostgreSQL-client D:/Develop/sofe/Mysql/bin/PostgreSQL.exe `
  --redis-server D:/Develop/sofe/Redis/redis-server.exe
```

路径按本机安装位置调整；PostgreSQL 地址可通过 `--db-host`、`--db-port` 指定，管理角色名可通过 `--db-admin-username` 指定。应用角色必须已存在，管理角色必须有 CREATEDB/删除临时库的权限。脚本自行生成独立数据库和 JWT 密钥、启动私有 Redis 与实际打包应用，不使用现有业务库或 Redis 数据；结束后删除本次创建的库并关闭自己的进程。PostgreSQL 服务是已有本机服务，不会被停止；容量结论因此只用于本机基线。

脚本执行 `clean verify`，拒绝必需集成测试跳过，再通过真实 HTTP 验证注册、两类账号登录/续期/退出、权限、渠道、个人资料、密码版本、换绑、管理端编辑普通用户及错误路径。普通用户注册后的资料 ID 必须是与数据库一致的字符串；管理端编辑与过滤列表使用 `9007199254740993` 验证超出 JavaScript 安全整数范围时仍精确返回字符串 ID。并发检查在 HTTP 层验证验证码登录、发送和注册各 16 个请求的结果分布，以及两个管理员编辑请求争用同一联系方式时仅一个成功。故障检查验证无效渠道配置、Redis 中断及恢复，并扫描本次敏感值是否进入应用日志。验证码从私有 Redis 读取，只用于验证开发发送实现，不代表真实短信/邮件送达。

默认性能基线分别重启应用执行 `control` 和 `protected` 两轮：前者将并行密码校验上限设为 100000，后者设为 8；两轮都将每 IP 和每账号的登录次数上限设为 1000000000，以观察并行密码校验限制。每轮先预热 20 次资料查询，再执行混合请求（约 40% 查询资料、40% 续期、20% 密码登录）和纯密码登录两种模式，各自以 1、8、24、48 个并发工作线程持续 10 秒，共 16 个负载阶段；`--seconds` 可将每阶段时长设为 1～60 秒。每阶段清空私有 Redis 并重新登录后开始计量。

负载阶段将成功业务响应和业务码为 `42901` 的 HTTP 429 记为预期结果；每阶段须实际发出请求，HTTP 5xx、超时和其他非预期响应合计须为零，且结束后的续期及资料查询正常。脚本记录各状态数量、总吞吐量、全部响应及成功响应的 P95/P99、各操作的延迟、登录保护指标、Java CPU 时间和阶段结束时工作集、线程数、Redis 内存、PostgreSQL 活跃会话数。受控拒绝计入总吞吐量，不能将总吞吐量当作成功业务吞吐量。该短时基线不设响应时间 SLA 或最低成功吞吐量，不等同于长时间稳定性、超载极限或生产容量验收。

结果与日志位于 `target/quality-gate/`。脚本在停止应用前从 JaCoCo TCP agent 拉取 HTTP 执行数据，追加到测试覆盖率后生成合并报告。它只执行自动验证，4 次规范检查与 3 次 Review 仍须实际完成并记录；不能把脚本成功等同于完成审查。

结果及报告必须来自最终代码的实际执行。按质量门禁判定为必需、但环境或工具尚未就绪的项目，记录为“未验证”，门禁保持未通过；不适用项目说明原因。

## 运行配置

本地开发允许使用 `src/main/resources/application.yml` 中的默认 JWT 密钥，IDEA 可直接启动。部署或共享环境应通过 `JWT_SECRET` 覆盖该值。需要生成新密钥时，使用至少 32 个安全随机字节并编码为 Base64，例如在 PowerShell 中生成：

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

在 IDEA 的 `Run > Edit Configurations > Environment variables` 中配置 `JWT_SECRET` 即可覆盖本地默认值。使用 Maven 面板启动时，将变量配置到 Maven Runner 的环境变量中。更换签名密钥会使旧 Token 失效；已使用旧默认密钥的环境应重新登录。

数据库配置、Redis 连接和 Token 有效期以 `src/main/resources/application.yml` 为准。本地开发默认连接 `localhost:6379` 的 0 号库，可用 `REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`、`REDIS_PASSWORD` 覆盖。测试不通过打印密码哈希或密钥充当验证；需要实际的成功与失败断言。

Snowflake 节点标识通过 `ID_GENERATOR_DATACENTER_ID`、`ID_GENERATOR_WORKER_ID` 配置，取值均为 0～31，默认均为 0。多实例部署必须为每个实例分配唯一的组合；普通用户主键由该节点生成后写入数据库，不依赖自增主键。

PostgreSQL Connector/J 使用默认的 `PostgreSQL UPDATE 行数语义`，UPDATE 返回匹配行数，重复提交相同值也返回 1。自定义 `DB_URL` 时保持该语义；Service 用它区分“记录存在”与“记录已消失”。JWT issuer 不得为空，Access/Refresh 有效期均至少为 1 秒，无效配置在启动时拒绝。

## 错误处理约定

- MVC 参数错误返回 400；错误方法、内容类型等保留 Spring 对应的 HTTP 状态和响应头，响应体使用现有 `Result` 格式及 `BAD_REQUEST` 错误码。
- 未预期异常及转换为业务异常的 500 错误在全局边界记录一次；日志保留异常原因链的类型和调用栈，不记录可能包含 SQL 参数或凭据的异常消息。响应不包含异常堆栈或数据库细节。
- 关键数据库写入必须命中预期记录；认证渠道配置缺失或不是 true/false 时抛出系统异常，不默认视为关闭。

## 代码边界

- Controller 只负责 HTTP 参数、认证上下文和响应转换。
- Service 负责业务规则和事务边界。
- Mapper 负责 MyBatis 数据访问，不在 Controller 中直接访问数据库。
- 数据库 schema 和初始配置由 Flyway 版本迁移管理；新变更必须添加新的版本脚本，不得修改已发布的迁移。
- `web_project.sql` 仅保留为旧版手动初始化快照和比对参考，不是新安装或 CI 的 schema 来源。旧数据库接管按 [数据库迁移指南](database-migrations.md) 备份、核验并显式 baseline。
- 存储支持本地和阿里云 OSS，包含全局配置、已存在 Bucket 的绑定管理和统一系统用户头像文件服务，见[存储数据库设计](存储数据库设计.md)。Java 实体及 Mapper 使用头像文件 ID，接口返回 `/files/{id}` 地址。
