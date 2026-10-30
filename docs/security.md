# 管理端会话与浏览器安全

## 当前实施

- 浏览器的 Access Token 与 Refresh Token 仅保存在当前标签页的 `sessionStorage` 中。刷新页面保留会话，关闭标签页后不再长期留存；其他标签页独立登录。升级后不迁移旧 `localStorage` Token，旧登录需要重新认证。
- 后端在每次受保护请求验证 JWT 签名、类型、时间与对应 Redis Session。管理端同时校验账号状态和 `authVersion`。退出登录删除 Session 后，旧 Access/Refresh Token 均不可继续访问；已经通过校验的在途请求不被追溯撤回。
- Redis 故障时无法验证会话，不得放行请求。代价是所有已认证请求都依赖 Redis 可用性和查询延迟，部署时必须监控 Redis、连接池及接口 P95/P99。
- `sessionStorage` **不是**针对 XSS 的完整防护：同源恶意脚本仍可能读取 Token。部署层还需要严格 CSP、依赖审计、禁止不可信 HTML 渲染，并避免将 Token 写入日志、URL 或错误上报。
- `frontend/nginx.conf` 提供静态站点与 API 同源代理以及 CSP、安全响应头的部署样例。只有生产服务器实际使用对应配置，响应头才会生效；将 CSP 应用于你的网关时，应先检查实际加载的脚本、字体和图像资源。正式 HTTPS 站点还应在 TLS 入口设置 HSTS。

## 验证方式

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
# 浏览器交互测试（模拟 API，非真实前后端联调）
npm install --no-save --package-lock=false @playwright/test@1.56.1
npx playwright install chromium
npx playwright test
```

端到端测试覆盖管理员登录、普通用户精确 ID 编辑/启停用、存储设置界面。真实后端会话撤销由后端 Redis + JWT 集成测试保障；在发布前仍需执行真实浏览器/后端连通与部署网关 CSP 验收。

## 后续架构选择

如果安全基线要求防止 XSS 直接读取 Refresh Token，需要将 Refresh Token 改为服务端设置的 HttpOnly、Secure、SameSite Cookie（或采用 BFF）并做好 CSRF 防护。这涉及前后端认证协议和部署域名，不应简单替换存储 API 而破坏已有移动端/普通用户调用。
