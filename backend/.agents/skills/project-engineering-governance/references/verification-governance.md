# Verification Governance Reference

本文件由主 `SKILL.md` 按需加载。

适用于：测试策略、Coverage、Machine Gate、CI、Integration、DB/Adapter Contract、Expected Output、Build Smoke、Real E2E、False Green、Changed Acceptance。

## 1. 核心目标

> 把当前稳定、重要、容易回归的行为和规则，尽可能转化为简单、稳定、可重复、机器可执行的验证证据。

Machine Gate 不是越多越好。

## 2. Coverage 原则

Coverage 缺口首先是代码审查信号：

```text
这段代码为什么没执行？
        ↓
当前是否真的需要？
   ┌────┴────┐
   No       Yes
   ↓          ↓
 Delete   行为重要吗？
              ↓
          Meaningful Test
```

不要采用 `Coverage 低 → 自动补测试`。

Coverage 只能说明执行到，不能证明测试会抓错。

## 3. 验证工具箱

按项目风险选择，不要求全部存在：

- Static / Type / Lint；
- Architecture / Dependency Gate；
- Unit / Business Rule；
- Contract Test；
- Integration / Composition；
- Database Contract；
- External Adapter Contract；
- Expected Output / Snapshot Replay；
- Build Artifact Smoke；
- Real Consumer Test；
- Real E2E；
- Security；
- Performance / Stress；
- Mutation / Fault Injection（成熟高风险模块按需）。

## 4. Static / Architecture Gates

适合保护类型、import/dependency 边界、forbidden dependency、schema invariant、codegen freshness、package metadata 等。

不能可靠判断的规则继续 Review，不写脆弱正则假 Gate。

## 5. Unit / Business Rule

优先测试：

- 边界值；
- error path；
- 状态转换；
- 幂等；
- 排序/顺序；
- 金额/精度；
- 纯业务规则；
- 协议转换；
- parser/normalizer；
- contract regression。

不优先测试 getter、类型声明、常量、无行为 wrapper 和纯实现细节。

## 6. Contract Test

保护稳定边界：

- API schema；
- Tool protocol；
- Model adapter protocol；
- DB schema；
- file format；
- serialization；
- external request/response mapping；
- public package exports。

关注对外承诺，不关注内部实现。

## 7. Integration / Composition

尽量组合真实内部组件，只替换真正外部或不稳定边界。

例如 Agent Runtime：

- Fake Model；
- 真实 Agent Loop；
- 真实 Session；
- 真实 Tool Registry；
- 真实 Test Tool。

例如业务服务：

- 真实 Application/Spring Context；
- 真实事务代理；
- 真实 Security；
- 测试数据库；
- 测试 Redis；
- Mock 外部 HTTP。

不要把被测核心自己 mock 掉。

## 8. Database Contract

内存数据库可以用于快速测试，但不能自动证明生产数据库行为。

当风险涉及：

- 方言 SQL；
- DATETIME/timezone；
- decimal precision；
- unique/index；
- lock/isolation；
- migration/schema；
- native feature；

考虑真实数据库 Contract Lane。

不要把全套 UI/Controller 测试重复跑一遍真数据库，只验证替代数据库无法可靠证明的部分。

## 9. External Adapter Contract

普通 CI 可使用：

`Fake HTTP Server + 真实 Adapter`

验证 URL、method、auth/signature、query/body、pagination、timestamp、mapping、error mapping、status 等。

不要 `Mock Adapter → 测 Adapter`。

## 10. Expected Output

适合 CLI output、Agent event sequence、normalized protocol、generated config 等。

原则：

- normalize 随机 ID、时间戳、临时路径；
- CI 只 replay；
- CI 不自动刷新正确答案；
- Snapshot 更新必须显式执行并 Review diff。

禁止 `测试失败 → 自动刷新 Snapshot → 变绿`。

## 11. Build Artifact / Consumer Smoke

源码测试绿色不代表交付物正确。

真实发布/消费项目可以：

```text
build
→ 从真实产物 import/install
→ 最小消费
→ smoke
```

用于发现 exports、漏文件、ESM/CJS、declaration、runtime dependency、metadata 等问题。

没有真实发布需求时不要提前建设发布系统。

## 12. Real E2E

真实外部能力不能只靠 Mock 宣布正确。

例如真实数据库、API、FFmpeg、Whisper、浏览器、GPU、native binary、cloud service。

Real E2E 要验证真实结果，不只看 `exitCode == 0`。

## 13. SKIP 与 False Green

```text
Local missing environment
→ SKIP

CI promised environment missing
→ FAIL
```

专用 CI 承诺有 Secret/环境却缺失时不能“全部 skip 但绿色”。

## 14. Changed Acceptance

重要变更应给出与本次风险直接对应的证据。

不仅：

`合法路径 → PASS`

在适用时还要：

`非法路径 → 正确拒绝`

例如权限 allowed + denied、parser valid + invalid、Tool Call valid + malformed。

不要用“全仓库测试绿色”替代本次变更的直接证据。

## 15. Local Fast / CI Exhaustive

按成本分层：

```text
编辑时
→ focused feedback

pre-commit（如已有）
→ 极便宜检查

pre-push（如已有）
→ 中等成本检查

CI
→ 完整确定性 Gate

Real Verification
→ 外部环境 / Secret / 昂贵资源
```

不要让 pre-commit 跑完整 E2E。

## 16. 统一 Gate

优先提供简单统一入口，例如：

- `make check`
- `pnpm check`
- `mvn verify`
- `go test ./...`
- `cargo test`
- `pytest + ruff + pyright`

具体命令自适应项目。

本地和 CI 尽量复用同一核心命令。

## 17. Test Effectiveness

警惕：

- 没 assertion；
- 永远 PASS 的 fake；
- mock 复制生产实现；
- snapshot 只会自动刷新；
- 只验证调用次数不验证行为；
- 对真实错误注入不敏感。

成熟且高风险的稳定模块可局部评估 mutation testing、fault injection、property-based、stress/concurrency。

不默认全项目开启。

## 18. Incident → Guardrail

真实问题发生后问：

1. 是否容易再次发生？
2. 机器能否稳定判断？
3. Guardrail 成本是否合理？

合适时转为 regression test、architecture gate、DB/Adapter contract、expected output、real smoke 或 CI gate。

不是每个 bug 都要永久机制。

## 19. Gate Simplification Review

检查：

- 两个 Gate 是否重复保护同一规则；
- 旧结构测试是否只保护历史文件形状；
- 是否为了 coverage 保留死代码；
- 是否为了测试引入生产抽象；
- 是否存在维护成本大于风险的 CI lane；
- 是否有脆弱 flaky test；
- 是否能用更简单直接的证据替代。

Gate 本身也是代码。
