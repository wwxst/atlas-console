# Atlas Console

基于 React、TypeScript 和 Vite 的自研中后台前端基础项目。

UI 组件按实际业务需求逐步实现。Arco Design 与 TDesign 仅作为产品设计和交互参考，不复制源码，也不作为运行时组件依赖。

## 技术架构

- React 19 + TypeScript + Vite
- 原生 React 自研 UI，按实际业务需求逐步增加组件
- React Router 负责路由和页面切换
- TanStack Query 负责服务端数据、缓存、刷新和 mutation
- Zustand 负责主题、侧栏等轻量客户端状态
- Axios 统一封装 API 请求和认证头
- Less + CSS Modules + CSS 变量，支持局部隔离和主题切换

## 目录

```text
src/
├─ app/                 # 应用级 Provider（后续扩展）
├─ features/            # 按业务域组织接口和业务逻辑
├─ layouts/             # 中后台布局
├─ pages/               # 路由页面
├─ router/              # 路由定义
├─ services/            # Axios 和基础服务
├─ stores/              # Zustand 客户端状态
├─ ui/                  # 自研 UI 组件和主题变量，不依赖第三方组件库
└─ styles/              # 全局样式和主题（后续扩展）
```

## 开发

```bash
npm install
npm run dev
```

生产构建与质量检查：

```bash
npm run build
npm test
npm run lint
```

CI 另外执行 Playwright 浏览器流程测试（模拟 HTTP API）。本地启动方式与安全说明见 [会话与浏览器安全](docs/security.md)；生产静态站点的 CSP/同源代理示例见 `deploy/nginx.conf`。


## 首次部署与一键更新

后台「版本与更新」支持超级管理员触发固定的 GitHub Actions 生产部署流程。不过**在准备服务器与 Runner 前此功能默认禁用**，不会自动创建云服务器。首次上线、安全配置、Docker Compose、GitHub Runner、备份及后续更新操作见 [部署与一键更新指南](deploy/README.md)。

## 单仓库结构

前端、后端、部署配置与 CI/CD 工作流统一维护在本仓库：

- `src/`：React + TypeScript 前端。
- `backend/`：Spring Boot 后端、Flyway 数据库迁移与后端测试，详见 [后端说明](backend/README.md)。
- `deploy/`：Docker Compose、Nginx、部署、升级与回滚脚本。
- `.github/workflows/`：前后端质量检查与统一发布工作流。

前后端通过同一仓库提交进行版本管理；发布前分别验证前端和后端，生产部署只检出一个已验证的提交 SHA。

## 项目文档

- [前端架构](docs/architecture.md)
- [UI 系统](docs/ui-system.md)
- [工程规范](docs/engineering.md)
- [Codex 前端技能](.agents/skills/atlas-frontend/SKILL.md)
