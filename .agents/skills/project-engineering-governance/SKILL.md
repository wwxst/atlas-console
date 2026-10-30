---
name: project-engineering-governance
description: 自适应审查并完善任意代码仓库的开发规范、测试策略、工程门禁、文档治理与 Agent 知识连续性。先理解项目真实架构、成熟度和风险，再将稳定且高价值的规则与行为尽可能转化为简单、可靠、可重复、机器可执行的 Guardrail，同时防止生产代码、测试、CI、文档和治理体系本身过度设计。
---

# Project Engineering Governance

## 目标

本 Skill 面向 AI / Agent / Vibe Coding 项目的长期可维护性治理。

它不是固定架构模板，也不是“自动加测试/CI”的脚本。

核心目标：

1. 先确认项目当前真实状态，再谈规范、测试和架构。
2. 提炼当前稳定、重要、容易回归的规则与行为。
3. 将适合机器判断的内容，尽可能转化为简单、稳定、可重复的工程门禁。
4. 对不适合机器判断的问题保留 Review、人工验证或设计决策，不制造假 Gate。
5. 主动发现并限制死代码、无消费者抽象、历史兼容、低价值测试、重复 Gate、文档漂移和治理过度设计。
6. 让规则、事实、决策、事故、Handoff、测试与交付物有清晰归属，使后续 Agent 能连续工作。

最高原则：

> 每一份复杂度都必须有当前理由。
>
> 每一个关键行为都应尽可能有可重复验证证据。
>
> 每一个稳定且能够可靠判断的规则，都应尽可能成为机器门禁。
>
> 每一个已经不需要的复杂度，在确认安全后应删除。

---

# 1. 工作模式

## Audit

当用户要求检查、审查、分析或比较时：

- 先分析，不做大规模修改。
- 输出当前事实、现有优点、冲突、主要风险、高价值 Guardrail、验证缺口、文档/知识连续性缺口、无必要复杂度和最小建议。

## Implement

当用户明确要求实施时：

- 仍先完成 Audit。
- 只实施确认存在的高价值缺口。
- 某项建议若当前项目没有真实缺口，明确“不实施”，不要为了满足清单制造修改。

除非用户任务明确包含架构重构，否则治理任务默认不主动重构核心架构或业务模型。

---

# 2. 最高任务边界

这些规则优先于后续所有建议。

## 2.1 验证层是工具箱，不是完成清单

当前没有真实缺口、没有稳定基础或维护成本明显高于收益的测试/Gate：保持不动。

不得为了让体系“完整”而新增测试、脚本、fixture、CI、Framework 或基础设施。

## 2.2 Machine Gate 不是越多越好

一个规则只有同时满足以下条件才适合长期机器化：

- 属于当前稳定规则或重要行为；
- 存在真实回归风险；
- 机器可以可靠判断；
- 执行简单、稳定、可重复；
- 失败原因清晰；
- 长期维护成本低于它防止的问题。

否则优先采用 Review、人工验证、Decision 或文档说明。

## 2.3 优先复用现有工具

优先使用项目已有构建工具、测试框架、类型检查、Lint、package scripts 和简单脚本。

能够用现有能力解决的问题，不引入新的 Runner、Parser、Task System、DI、Framework 或平台。

## 2.4 不顺手重新设计业务契约

如果错误码、权限模型、支付模型、状态机、数据库迁移策略、重试/补偿策略、Provider 抽象等尚未确认：

> 记录为独立设计问题，不在治理任务里自行发明。

## 2.5 未覆盖/无静态调用不是删除证据

它们只能触发审查。

删除前必须按项目实际检查调用链、动态注册、反射、配置、数据库、序列化、公共 API、插件/Tool 注册、脚本、第三方回调和生产消费者。

确认当前确实无用途后才删除。

## 2.6 治理本身也遵守最小实现

测试、CI、Gate、脚本、文档和治理机制本身都有维护成本，也必须接受 Simplification Review。

---

# 3. 第一阶段：建立 Current Reality

任何规范、测试和 Gate 之前，先确认项目真实状态。

优先检查：

- 根目录、package/module 结构；
- 构建文件、依赖文件、lockfile；
- CI 配置、测试目录和脚本；
- schema / migration / 数据持久化；
- Runtime / Service / CLI / UI 入口；
- 第三方 Adapter；
- 配置文件；
- AGENTS / CLAUDE / CONTRIBUTING / DEVELOPMENT；
- architecture / design / ADR / RFC / plans / archive；
- 真实调用链和实际生产消费者。

必须区分：

```text
Current Code
!= README Claim
!= Future Plan
!= Historical Design
```

机器可读事实优先于 Markdown 重复声明，例如：版本看构建配置，依赖看 lockfile，schema 看唯一 schema source，真实行为看代码 + 测试 + 实际执行。

---

# 4. 识别项目类型、成熟度与风险

不要把一个项目的治理方式机械复制到另一个项目。

先识别：

- CRUD / 业务系统；
- Runtime / SDK / Agent Core；
- 前端应用；
- CLI / Tool；
- 数据 / AI / ML；
- 桌面端 / Electron；
- Monorepo；
- Library / Public SDK；
- Prototype / 0.x / Internal / Production / Stable Public API。

风险随项目变化，例如：

- 业务系统：权限、金额、状态、事务、数据库、第三方、异步任务；
- Runtime/SDK：契约、依赖方向、状态序列、Provider 边界、构建产物、真实消费者；
- 前端：路由、权限、表单、API 契约、状态、build；
- CLI：参数、exit code、stdout/stderr、expected output、binary smoke；
- AI/ML：输入契约、模型/数据版本、可重复性、真实推理/工具链。

兼容成本必须由项目成熟度决定；0.x 和公共稳定 SDK 不能使用同一套兼容策略。

---

# 5. 提炼 Project Invariants

从当前代码、历史事故和稳定项目规则中提炼真正的不变量。

好的 Invariant 应：

- 当前真实成立；
- 被破坏会造成真实风险；
- 长期稳定；
- 尽可能短、硬、明确。

例如：

- Core 不依赖 UI；
- Controller 不直接访问数据库；
- 历史账单不能被当前费率重新计算；
- Provider SDK 不进入 Core；
- 手动操作不能依赖下一轮定时调度才产生效果。

然后判断：

```text
Invariant
  ↓
机器能可靠判断？
  ├─ Yes → Machine Gate / Test
  └─ No  → Review Rule / Decision
```

不要把所有编码风格、命名习惯和文件形状都转成结构测试。

详细工程治理规则见：`references/engineering-governance.md`。

---

# 6. 验证原则

## 6.1 Coverage 缺口首先是代码审查信号

不要采用：

```text
Coverage 低 → 自动补测试
```

应采用：

```text
这段代码为什么没有执行？
        ↓
当前真的需要吗？
   ┌────┴────┐
   No       Yes
   ↓          ↓
 Delete   行为重要吗？
              ↓
          Meaningful Test
```

如果属于死代码、无消费者、重复、历史遗留、已失效兼容、未来预留或没有真实失败场景的防御逻辑，确认安全后优先删除，而不是为它制造测试。

## 6.2 测试保护行为，不保护历史实现

测试应优先保护：

- 业务规则；
- 契约；
- 错误路径；
- 边界值；
- 状态转换；
- 排序/顺序；
- 幂等；
- 数据精度；
- 真实集成语义；
- 对外产物。

不要为了覆盖率测试 getter、类型声明、常量、无业务行为 wrapper 或实现细节。

## 6.3 测试不得反向污染生产架构

不得仅为了 Mock/测试方便在生产代码中增加 Interface、Factory、Manager、DI、测试专用 API 或通用抽象。

只有测试真正暴露职责耦合或架构边界问题时，才考虑重构生产设计。

## 6.4 Changed Acceptance 必须与本次风险对应

重要变更不仅证明“合法路径能通过”，还应在适用时证明关键非法路径会被拒绝。

```text
Positive acceptance
+
Negative rejection
```

不要用“全仓库测试绿色”代替本次修改的直接证据。

完整验证工具箱和 Gate 策略见：`references/verification-governance.md`。

---

# 7. 文档与项目知识

项目长期维护不能依赖单个 Agent 的上下文。

遵循：

> One Fact, One Home.
>
> One Rule, Smallest Applicable Scope.
>
> One Canonical Owner.

不要把所有东西塞进一个 AGENTS 或 PROJECT_MEMORY。

一般应区分：

- Standing Rules：每个 Agent 都必须知道的长期规则；
- Current Truth：当前架构、接口、数据和运行事实；
- Decisions：为什么这样设计、替代方案为什么没选；
- Postmortems：为什么错误逃过现有安全网；
- Handoff：当前任务现场和剩余工作；
- Raw Evidence：聊天、Git、Issue、PR、日志等原始证据；
- Tests / Gates：机器可执行的项目记忆。

原始聊天不直接成为永久规范；应蒸馏为 Rule / Decision / Incident / Handoff。

如果环境能检索历史聊天，使用真实来源；不能检索时，不得凭模糊记忆假装知道之前说过什么，应依赖仓库内已沉淀的知识。

完整知识连续性与文档治理见：`references/knowledge-continuity.md`。

---

# 8. 依赖、交付物与成熟项目治理

源码测试通过不代表真实交付物一定正确。

对于存在实际构建/发布/消费边界的项目，按需评估：

- dependency hygiene；
- package metadata / exports；
- build artifact smoke；
- real consumer test；
- generated truth freshness；
- documentation integrity；
- compatibility policy；
- vendored/native/SDK/model 来源和版本；
- license / third-party notices；
- release verification；
- mutation / fault injection / stress（仅稳定高风险模块按需）。

这些属于成熟度驱动能力，不得默认全部启用。

详见：`references/advanced-governance.md`。

---

# 9. Gate 成本分层

不要把所有检查塞进每次 commit。

建议按成本分层：

```text
编辑时 / local focused
→ 最快反馈

pre-commit（如项目已有）
→ 极便宜、确定性检查

pre-push（如项目已有）
→ 中等成本检查

CI
→ 完整、确定性 Gate

Real Verification
→ 外部系统、Secret、真实二进制、昂贵环境
```

Local 和 CI 核心命令尽量一致，但无需强迫所有昂贵验证进入普通 PR。

本地缺真实环境可以明确 SKIP；专用 CI 若承诺该环境存在但缺失，应 FAIL，避免 False Green。

---

# 10. Incident → Guardrail

不是每个 bug 都要永久机制。

发生真实问题后判断：

1. 是否容易再次发生？
2. 机器能否稳定判断？
3. 新 Guardrail 的维护成本是否低于再次发生的风险？

如果合适，将事故转化为一个或多个：

- Regression Test；
- Architecture Gate；
- Contract Test；
- DB/Adapter Contract；
- Real Verification；
- Standing Rule；
- CI Gate。

严重、跨层或暴露流程缺口的事故才值得 Postmortem。

Postmortem 不是 Bug 日志；它应解释：发生了什么、真正机制、为什么现有测试/Review/CI 没拦住、增加了什么永久 Guardrail。

---

# 11. Simplification Review

治理结束前，单独检查本次涉及范围。

## Production

- 有没有无消费者抽象、字段、状态、兼容路径？
- 有没有只转发一层的 Wrapper/Manager/Helper？
- 有没有默认值、fallback、宽泛 catch 掩盖错误？
- 有没有为了未来预留的 package/API？

## Tests

- 有没有测试实现细节？
- 有没有重复测试、重复 fixture？
- 有没有为了 coverage 保护死代码？
- 有没有为了测试增加生产抽象？

## Gates / CI

- 有没有多个 Gate 重复证明同一规则？
- 有没有本地开发成本过高但价值很低的检查？
- 有没有脆弱正则/脚本假装可靠判断？

## Docs / Knowledge

- 同一个事实是否重复维护？
- Handoff 是否被误当永久文档？
- 旧 Decision 是否已经被替代却仍像当前真相？
- 临时调试过程是否污染 Standing Rules？

只审查本次修改、直接调用链和本次明确暴露的问题；不要借治理任务扩大成全仓库历史大扫除。

---

# 12. 推荐执行流程

```text
Inspect repository
      ↓
Establish Current Reality
      ↓
Classify project + maturity + risks
      ↓
Find conflicts / stale truth
      ↓
Extract stable invariants
      ↓
Map risks to smallest validation mechanism
      ↓
Audit knowledge / decisions / handoff
      ↓
Choose minimal high-value changes
      ↓
Implement (when requested)
      ↓
Run focused evidence
      ↓
Run applicable CI/gates
      ↓
Real Verification when required
      ↓
Simplification Review
      ↓
Update canonical owners only
```

Audit 和 Implement 都遵循这个思路；Implement 只是多了实际修改步骤。

---

# 13. 按需读取 References

不要每次任务都加载全部 reference。

## 工程规范、复杂度、删除、兼容策略

读取：`references/engineering-governance.md`

适用于：架构审查、规范完善、过度设计、依赖方向、删除/简化、项目成熟度与兼容判断。

## 测试、CI、Machine Gate、Real Verification

读取：`references/verification-governance.md`

适用于：测试体系、Coverage、Integration、DB/Adapter Contract、Expected Output、Build Smoke、E2E、CI、False Green、Changed Acceptance。

## 文档、聊天、决策、Postmortem、Agent Handoff

读取：`references/knowledge-continuity.md`

适用于：One Fact One Home、分层规则、Canonical Ownership、聊天蒸馏、Decision Lifecycle、Supersession、Postmortem、跨 Agent 交接。

## 交付物、Generated Truth、供应链、高级质量 Gate

读取：`references/advanced-governance.md`

适用于：SDK/Library、发布物、Public API、生成文档、vendor/native、license、release、mutation/stress/platform matrix。

---

# 14. 最终输出

只输出当前任务适用的章节；不要为了模板完整输出大量“无 / 不适用”。

通常包括：

- Current Reality
- Existing Strengths
- Conflicts / Stale Truth
- Main Risks
- High-value Invariants / Guardrails
- Validation / Machine Gates
- Knowledge Continuity（如适用）
- Unnecessary Complexity
- Implemented / Deleted（Implement 模式）
- Not Implemented + 原因
- Verification Evidence
- Remaining Risks / Manual Review

对于大型治理任务，可增加 Decision/Supersession、Dependency/Deliverable、Real Verification、Architecture Impact 等章节。

不要用：

- “理论上正确”
- “应该可以”
- “预计通过”
- “看起来正常”

代替实际执行证据。

---

# 15. 最终判断标准

一个好的治理结果不是：

- 测试最多；
- Coverage 最高；
- 文档最多；
- CI 最复杂；
- 架构最“企业级”。

而是：

> 当前真实风险被最小、可靠、可重复地约束；项目知识能够传给后续 Agent；长期规则有唯一归属；无必要复杂度没有因为“工程化”继续增长。
