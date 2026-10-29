# MySQL 数据库版本迁移（Flyway）

## 原则

- 新安装由 Spring Boot 启动时运行 Flyway；MySQL 容器不再通过 `/docker-entrypoint-initdb.d/web_project.sql` 创建业务表。
- `src/main/resources/db/migration/V1__initial_schema.sql` 是新安装的初始 schema 和默认系统配置。**已合并/发布的迁移文件不可编辑**；后续只添加 `V2__...`, `V3__...` 等新文件。
- 默认启用 `validate-on-migrate`，并禁用 Flyway clean。迁移失败会导致后端启动/健康检查失败，发布流程不会记录新应用版本为成功。
- Flyway 只负责 schema 和明确写入迁移脚本的数据；它不会备份或恢复 MySQL 数据、上传文件、Redis、OSS 对象或服务器配置。

## 全新数据库（当前首次部署推荐）

1. Compose 创建空的 `web_project` 数据库。
2. 后端启动时 Flyway 执行 V1，创建 `sys_user`、`user_account`、`sys_config`、`storage_space`、`storage_file` 并写入默认配置。
3. CI 使用真正的 MySQL 8.4 空数据库执行相同迁移，并检查 Flyway 版本、表和初始记录。

无需手动导入 `web_project.sql`。新部署完成后，首次对外开放前必须修改默认系统管理员密码。

## 从旧版 `web_project.sql` 升级已有数据库

已有 Docker volume 不会重新执行 MySQL 初始化脚本。Flyway 默认拒绝在没有迁移历史的非空数据库上自动接管，这是有意的安全保护。

**不要直接删除数据库 volume，不要重新导入 `web_project.sql`，也不要在没有备份时打开 baseline。**

1. 在停机/变更窗口前制作数据库备份，并验证备份文件非空、可读；最好在独立测试环境先完成一次恢复演练。例如在服务器上：
   ```bash
   mkdir -p /opt/atlas/backups
   docker compose --env-file /opt/atlas/.env -f <runner-workspace>/atlas-console/deploy/compose.yaml \
     exec -T mysql sh -c 'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers web_project' \
     > "/opt/atlas/backups/web_project-$(date +%F-%H%M%S).sql"
   ```
   将 `<runner-workspace>` 替换为实际工作区路径；不要把备份放在 Docker volume 内或提交到 GitHub。
2. 确认数据库确实是当前旧版 schema。至少核对五张业务表的列、索引、约束，以及 `sys_user` 和 `sys_config` 的现有数据；不要对不明来源或部分初始化的库进行 baseline。
3. 只有在备份完成且 schema 与当前 `web_project.sql`/V1 相符后，才在服务器私有 `/opt/atlas/.env` 中临时设置：
   ```dotenv
   ATLAS_FLYWAY_BASELINE_ON_MIGRATE=true
   ```
4. 通过正常的受保护发布流程部署。Flyway 会把**现有非空 schema**登记为版本 1，不会再次执行 V1 的建表和默认数据插入；后续 V2+ 才会运行。
5. 确认后端健康、登录、用户/管理员列表、配置读取与文件记录正常后，将该变量改回 `false`（或删除它），再重建/重启后端。迁移历史已存在后，后续发布应走正常的 Flyway validate/migrate 流程。
6. 如果 schema 无法确认与 V1 相符，**不要启用 baseline**。先在隔离环境分析差异并准备专门的迁移脚本。

baseline 是一次性接管操作，不是 schema 自动修复工具；它不会比对现有数据库的完整 DDL 与 V1 文件，也不会补齐缺失的表/列。

## 编写后续迁移的要求

- 采用向前兼容的 expand/contract：先新增 nullable 列/表或兼容索引，再发布同时兼容旧、新 schema 的应用；确认回滚窗口结束后才考虑移除旧结构。
- 不要在常规迁移里无条件删除表/列、重命名列、清空业务数据或重建 schema。
- 生产迁移前必须备份；对大表 DDL 评估锁表、耗时和磁盘空间。
- Flyway 迁移通常不可自动逆转。应用镜像回滚**不会**把数据库结构或数据回滚。迁移失败时不要删除 `flyway_schema_history` 或数据库 volume；保留现场，按备份恢复方案或向前修复。
- 任何破坏性变更都需要独立审批、明确的数据恢复步骤，并等所有仍可能回滚的旧版应用退出后再执行。

## 迁移状态查询

在数据库中执行：

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

新安装应显示 V1；后续每个成功迁移都会新增一条版本记录。
