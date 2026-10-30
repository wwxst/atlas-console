# Advanced Governance Reference

本文件由主 `SKILL.md` 按需加载。

适用于：成熟 SDK/Library、发布物、Generated Truth、依赖/供应链、Public API、Release、Mutation/Stress、Platform Matrix 等高级治理。

这些能力都按项目成熟度启用，不默认实施。

## 1. 何时启用

仅当项目存在真实需求，例如：

- 对外发布 package/SDK；
- 稳定 Public API；
- 多平台支持；
- binary/native dependency；
- vendored source；
- 自动生成文档/目录；
- 正式 release；
- 复杂供应链；
- 核心高风险模块需要测试有效性验证；
- 大规模 package/monorepo hygiene。

## 2. Source Correctness ≠ Deliverable Correctness

成熟项目应按需验证：

```text
Source
→ Build
→ Package
→ Install/Import
→ Real Consumer Smoke
```

可发现 build 漏文件、exports、metadata、runtime dependency、declaration/type、ESM/CJS/ABI、resources、native binary path 等问题。

## 3. Consumer Test

Public Library/SDK 特别适合：

- 只使用 public entry；
- 不 import internal path；
- 从真实 package/build artifact 使用；
- 验证最小真实用例。

Consumer Test 的价值通常高于继续堆 internal unit tests。

## 4. Generated Truth

候选：

- CLI commands；
- config reference；
- tool registry catalog；
- API schema docs；
- module dependency graph；
- persistence catalog；
- package export list。

采用：

```text
canonical source
→ generator
→ generated artifact
→ freshness check
```

不要让 generated artifact 和 canonical source 都能手工编辑。

## 5. Documentation Integrity

成熟文档体系可按需检查：

- dead link；
- code sample compile/typecheck；
- generated reference freshness；
- heading/anchor consistency；
- doc ownership；
- archive/current status；
- version drift。

如果文档不是核心产品/契约，不需要复杂 docs pipeline。

## 6. Dependency Hygiene

成熟 monorepo/library 可评估：

- unused dependencies；
- undeclared dependencies；
- workspace constraints；
- duplicate versions；
- package cycles；
- public/private dependency leakage；
- peer dependency correctness；
- package metadata validation。

工具选择跟随生态，不为 hygiene 增加不必要平台。

## 7. Supply-chain Governance

当项目使用 vendored source、native binary、external SDK、model files、downloaded scripts 等，应按风险记录：

- upstream source；
- version / commit / checksum；
- local modifications；
- license；
- upgrade procedure；
- owner。

避免后续 Agent 把本地修改误认为纯上游代码。

## 8. License / Third-party Notices

对外分发项目按需检查：

- runtime dependency license；
- vendored code license；
- native binary redistribution；
- model/data usage license；
- notice requirements。

内部小项目不默认建设完整 license automation。

## 9. Compatibility Matrix

只在真实支持多个环境时启用，例如：

- Node/Python/Java versions；
- OS；
- CPU arch；
- database versions；
- browser versions。

矩阵中的每个维度都应有真实用户/部署环境。

## 10. Public API Compatibility

稳定 SDK/API 可考虑：

- API surface snapshot；
- type compatibility；
- consumer compile；
- deprecation policy；
- semantic version gate；
- migration notes。

0.x/internal 项目不要提前背这套成本。

## 11. Release Verification

正式发布前可按需建立：

```text
build
→ package
→ install in clean temp env
→ smoke
→ version/metadata check
→ release artifact integrity
```

没有正式 release 就不要建设 release pipeline。

## 12. CI Workflow Integrity

只有 CI 已复杂到多个 workflow、secret、matrix、cross-job dependency 时，才考虑：

- workflow self-test；
- config schema validation；
- required secret preflight；
- workflow dependency assertions。

小项目不要测试 CI 自己。

## 13. Test Effectiveness

当核心稳定模块覆盖率很高、测试很多但仍漏回归，可局部考虑：

- mutation testing；
- targeted fault injection；
- property-based testing；
- chaos/failure simulation；
- stress/concurrency。

不默认全仓库开启。

## 14. Performance / Stress

只有性能本身是当前产品契约时才建立门禁，例如 latency SLO、throughput、memory budget、startup time、large-data behavior、concurrency limit。

不要把一次 benchmark 当稳定 CI Gate，除非环境足够可重复。

## 15. Security Gates

根据真实威胁模型按需：

- dependency vulnerability；
- secret scan；
- SAST；
- permission regression；
- authz negative test；
- insecure config；
- supply-chain checks。

不要为了“安全扫描齐全”盲目堆工具。

## 16. Observability / Diagnostics

生产系统可按需审查：

- error observability；
- structured logs；
- metrics；
- tracing；
- correlation IDs；
- audit events；
- health/readiness。

不要让 observability 逻辑反过来污染核心业务模型。

## 17. 启用原则

每个高级 Gate 都先回答：

1. 当前有真实风险吗？
2. 当前有真实消费者吗？
3. 机器能稳定判断吗？
4. 维护成本合理吗？
5. 是否有更简单的方法？

如果不能明确回答，不启用。

成熟不是 Gate 越多。

成熟是：

> 项目知道哪些风险值得长期支付治理成本。
