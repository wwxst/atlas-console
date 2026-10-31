# Atlas Console

Atlas Console 是一个前后端一体化的管理系统单仓库（Monorepo）。前端 React 应用与 Spring Boot 后端是两个**平级、独立的工程**，部署配置、数据库迁移、自动化测试和 CI/CD 在同一仓库统一管理。

## 工程结构

- `frontend/`：React 19、TypeScript、Vite 前端工程，包含前端源码、静态资源、单元测试和 Playwright 浏览器测试。
- `backend/`：Spring Boot、Spring Security、MyBatis、PostgreSQL、Redis 后端工程；Flyway 版本迁移位于 `backend/src/main/resources/db/migration/`。
- `deploy/`：Docker Compose、发布/回滚脚本、安装器回归测试和部署验收脚本。
- `docs/`：项目级架构、安全、工程和 UI 规范。
- `.github/workflows/`：前端质量、后端质量和生产发布工作流。
- `.agents/`：Codex 项目技能；根目录 `AGENTS.md` 提供跨工程协作规范。

```text
atlas-console/
├── frontend/       # 前端独立工程
├── backend/        # 后端独立工程
├── deploy/         # 统一部署与发布
├── docs/           # 项目级文档
├── .agents/        # Codex skills
├── .github/        # CI/CD
├── install.sh      # 首次安装入口
├── AGENTS.md       # 仓库级开发规范
└── README.md
```

## 本地开发

前端：

```bash
cd frontend
npm ci
npm run dev
```

后端：

```bash
cd backend
./mvnw spring-boot:run
```

## 质量检查

前端（在 `frontend/` 目录执行）：

```bash
npm test
npm run lint
npm run build
```

后端（在 `backend/` 目录执行）：

```bash
./mvnw -B -ntp clean verify
```

CI 还会运行 Playwright 浏览器流程、Docker Compose 配置校验、Nginx 转发 IP 集成测试，以及发布/回滚和安装器回归测试。

## 一键安装与生产部署

全新 Ubuntu/Debian 服务器可以运行：

```bash
curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
```

安装时可选择 Docker 私有 PostgreSQL（推荐），或使用已在宝塔创建的空数据库。安装完成后，在宝塔配置域名反向代理与 HTTPS。安装器不会自动注册高权限 GitHub Runner；后续 GitHub Actions 自动更新需要单独配置 Runner。

首次安装、宝塔代理、数据库模式、安全配置、GitHub Runner、备份及后续更新操作见 [一条命令安装指南](deploy/installer.md) 和 [部署与一键更新指南](deploy/README.md)。

## 版本与数据库

前后端通过同一个 Git 提交进行版本管理。发布工作流分别验证 `frontend/` 与 `backend/`，生产部署只使用同一个已验证的 monorepo commit SHA。

数据库结构由 Flyway 版本迁移管理。已应用到环境的迁移文件不可修改；新增 schema 变更必须新增版本迁移，并通过后端质量工作流验证。