# Engineering Governance Reference

本文件由主 `SKILL.md` 按需加载。

适用于：架构审查、开发规范、复杂度控制、删除/简化、依赖方向、兼容策略、项目成熟度判断。

## 1. 核心目标

工程规范不是为了统一风格，而是为了保护当前真实架构边界、限制没有当前消费者的复杂度，并让后续 Agent 能快速判断什么可以改、什么不能随便改。

最高原则：

> 每一份复杂度都必须有当前理由。

## 2. Current Reality 优先

先检查当前目录、模块、imports、dependencies、真实调用链、运行入口、数据流、构建/发布、配置、schema、测试和 CI。

不要让 README、未来 plan、归档设计、历史聊天或“行业一般这样做”覆盖真实代码事实。

## 3. 项目成熟度与兼容策略

先判断项目属于 Prototype、0.x、Internal、Production 或 Stable Public API/SDK。

- Prototype / 0.x：允许直接修正错误边界，不为错误设计保留 LegacyAdapter、CompatibilityBridge 等长期包袱。
- Production：重点保护数据、API、用户状态、配置和外部集成。
- Public SDK：额外考虑 public API compatibility、consumer test、package metadata、deprecation 和 release policy。

兼容性不是默认越多越好。

## 4. Project Invariants

Invariant 应满足：

- 当前真实成立；
- 被破坏会产生真实风险；
- 长期稳定；
- 简短明确；
- 能说明 owner；
- 适合时可映射到 Machine Gate。

示例：

- Core 不依赖 UI；
- Controller 不直接访问数据库；
- 历史账单不能被当前费率重算；
- Provider SDK 不进入 Core；
- 手动操作不能依赖下一次 Scheduler 才生效。

不要把临时命名偏好、目录美观、一次重构后的文件形状升级为 Invariant。

## 5. One Rule, Smallest Applicable Scope

- 全仓库永久规则 → 根级 Agent/开发规则；
- 模块特有规则 → 最近的 subtree/module 规则；
- 一次设计决定 → Decision / ADR；
- 当前任务状态 → Handoff；
- 历史事故 → Postmortem。

不要把所有规则塞进根 `AGENTS.md`。

## 6. 复杂度必须有消费者

新增以下内容前必须指出当前消费者或当前约束：

- Interface；
- Factory；
- Strategy；
- Manager；
- Processor；
- Executor；
- Registry；
- Event Bus；
- Hook；
- State Machine；
- Queue；
- Retry；
- Compensation；
- Cache；
- Lock；
- Wrapper；
- Compatibility Layer；
- Plugin framework。

“以后可能需要”“更灵活”“企业项目一般这样”不能单独作为理由。

已有明确组织价值的结构可以保留。例如项目明确把 `Service + ServiceImpl` 作为业务契约/实现分离，就不应机械删除。

## 7. 抽象是否合理

至少问：

1. 当前谁在用？
2. 删除后具体失去什么？
3. 是否存在两个需要独立演化的角色？
4. 是否存在真实多实现/替换/生命周期边界？
5. 是否只是搬运复杂度？
6. 是否只是为了测试方便？
7. 是否只是为了未来？

## 8. 错误处理

优先：

- 在真实输入/外部边界校验；
- 程序错误保持可观察；
- 第三方失败明确分类；
- 不用默认值伪装成功；
- 不用宽泛 catch 吞掉不可恢复错误。

警惕：

- catch-all + default；
- fallback-to-success；
- parse error → empty result；
- 外部失败 → “暂无数据”。

若确实允许降级，要说明当前业务为什么允许、用户看到什么、日志如何保留真实失败。

## 9. 删除与简化

未覆盖、无静态调用、看起来重复只能触发审查，不能直接证明可删。

删除前按项目实际检查：

- 直接调用；
- 动态注册；
- 反射；
- 注解扫描；
- 配置；
- JSON/序列化；
- Mapper/XML；
- DB schema；
- CLI entry；
- scripts；
- public exports；
- Plugin/Tool 注册；
- 第三方 callback；
- runtime discovery；
- production consumer。

确认当前无用途后优先删除，而不是增加 deprecated、wrapper、兼容层或低价值测试。

## 10. Dependency Governance

新增依赖视为项目边界变化。至少回答：

- 为什么需要？
- 当前谁消费？
- 现有工具为什么不够？
- 版本真相在哪里？
- 是否进入 runtime？
- 是否引入 native/binary？
- 是否影响 license/distribution？
- 删除成本是什么？

按项目需要检查 unused、undeclared、duplicate、cycle、boundary violation、lockfile consistency。

## 11. 事务、状态、并发等高风险机制

- 不把网络调用放进不必要的长事务；
- 不用通用 retry 掩盖语义错误；
- 不用分布式锁解决没有真实竞争的问题；
- 不用 Saga/补偿框架替代简单显式状态；
- 状态机只在真实复杂度存在时引入；
- 框架语义通过真实集成行为验证，而不是只看注解。

## 12. Simplification Review

检查本次涉及范围：

### Production
- 无消费者代码？
- 空壳抽象？
- 只转发 Wrapper？
- 重复状态？
- 未来预留？
- 宽泛 catch/fallback？
- 兼容路径仍有真实消费者？

### Architecture
- 新 package 是否必要？
- 新 Interface 是否有真实边界？
- 新 Registry 是否有真实生命周期？
- 是否为统一风格动了正常代码？

### Dependencies
- 新依赖是否可避免？
- 是否只为小检查引入大型框架？
- 是否声明但无真实 import？

### Governance
- 是否为了“更专业”增加不必要流程？
- 是否把 Review 能解决的问题做成复杂框架？

## 13. 不默认引入

除非当前项目真实需要，不默认引入：

- DDD 全套；
- Hexagonal/Ports & Adapters 大重构；
- CQRS；
- Saga；
- Distributed Transaction；
- Event Bus；
- Workflow Engine；
- Plugin Runtime；
- Service Container；
- 通用 Hook Framework；
- 通用 Retry/Compensation；
- 多层 Manager/Processor/Executor；
- 公共 API 兼容层；
- 大型架构静态分析平台。

目标不是更“企业级”，而是更可维护。
