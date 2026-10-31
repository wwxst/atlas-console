# PostgreSQL 数据库版本迁移（Flyway）

## 原则

- Atlas Console 只支持 PostgreSQL 17；MySQL 不再是受支持的数据库后端。
- 全新安装由 Spring Boot 启动时运行 Flyway。数据库容器不执行额外 SQL 初始化脚本。
- `src/main/resources/db/migration/V1__initial_schema.sql` 是 PostgreSQL 新安装的初始 schema 和默认系统配置。发布后迁移文件不可编辑；后续只添加 `V2__...`、`V3__...`。
- 默认启用 `validate-on-migrate`，并禁用 Flyway clean。迁移失败会导致后端启动/健康检查失败。
- Flyway 只负责 schema 和迁移脚本明确写入的数据；不会备份或恢复 PostgreSQL 数据、上传文件、Redis、OSS 对象或服务器配置。

## 全新数据库

1. Docker 模式由 Compose 创建私有 PostgreSQL 17 容器和空的 `web_project` 数据库。
2. 宝塔/外部数据库模式由用户先创建 PostgreSQL 数据库和专用账号，再在安装向导填写连接信息。
3. 后端启动时 Flyway 执行 V1，创建五张业务表并写入初始管理员和系统配置。
4. CI 使用真实 PostgreSQL 17 空数据库执行相同迁移，并运行 Mapper、事务、并发、存储集成测试。

不要手动导入历史 MySQL `web_project.sql`。首次对外开放前必须修改默认系统管理员密码。

## 从历史 MySQL 数据库迁移

Atlas 当前改为 PostgreSQL-only。Flyway 不能把 MySQL 数据库原地转换为 PostgreSQL，也不能通过 baseline 完成跨数据库迁移。

若确实存在历史 MySQL 数据：
1. 先冻结写入并制作、验证 MySQL 备份。
2. 在独立 PostgreSQL 数据库中运行当前 Flyway 迁移，创建目标 schema。
3. 使用经过审查的数据迁移工具/脚本逐表迁移数据；显式处理无符号整数、时间类型、字符排序规则、唯一约束和自增序列。
4. 校验每张表的行数、主键范围、唯一键、空值、时间范围和关键业务关联。
5. 在隔离环境完成应用读写、登录、上传、分页、并发及恢复演练后，再安排切换窗口。
6. 不删除原 MySQL 数据和备份，直到 PostgreSQL 运行稳定并完成恢复演练。

本项目当前没有已确认的生产旧数据库，因此首次部署直接创建全新的 PostgreSQL 数据库，不做 MySQL 数据迁移。

## 后续迁移要求

- 采用向前兼容的 expand/contract。
- 不在常规迁移中无条件删除表/列、清空业务数据或重建 schema。
- 生产迁移前必须备份；评估大表 DDL 的锁、耗时和磁盘空间。
- Flyway 迁移通常不可自动逆转。应用镜像回滚不会回滚数据库结构或数据。失败时保留现场，不删除 `flyway_schema_history` 或 PostgreSQL volume。
- 时间字段使用不带时区的 UTC 时间戳；数据库服务器及应用环境统一使用 UTC。

## 迁移状态查询

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

全新安装应显示 V1；后续每个成功迁移都会新增一条版本记录。
