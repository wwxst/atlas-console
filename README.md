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

生产构建：

```bash
npm run build
```

## 项目文档

- [前端架构](docs/architecture.md)
- [UI 系统](docs/ui-system.md)
- [工程规范](docs/engineering.md)
- [Codex 前端技能](.agents/skills/atlas-frontend/SKILL.md)
