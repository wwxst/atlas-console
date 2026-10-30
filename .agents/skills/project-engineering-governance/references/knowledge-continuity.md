# Knowledge Continuity Reference

本文件由主 `SKILL.md` 按需加载。

适用于：文档治理、One Fact One Home、分层规则、Canonical Ownership、聊天蒸馏、Decision Lifecycle、Supersession、Postmortem、Agent Handoff、跨 Agent 连续性。

## 1. 核心目标

> 项目不应该依赖某一个 Agent 的上下文才能继续维护。

核心原则：

> One Fact, One Home.
>
> One Rule, Smallest Applicable Scope.
>
> One Canonical Owner.

## 2. Project Knowledge Pyramid

```text
             Standing Rules
                 AGENTS
                   ▲
                   │
            Current Truth
         Architecture / Docs
                   ▲
                   │
         Decisions / Agent Notes
           为什么这样设计
                   ▲
                   │
              Postmortems
           为什么以前出错
                   ▲
                   │
               Handoffs
            当前做到哪里
                   ▲
                   │
 Raw Conversation / Git / PR / Issue / Logs
```

同时：

```text
历史教训
   ↓
Tests / Gates
   ↓
Executable Memory
```

## 3. AGENTS 不是项目历史

根级 Agent 指令只放长期 Standing Orders，例如永久架构边界、安全规则、必跑 Gate、高风险禁止项。

不要放：

- 三个月前为什么这样改；
- 某次调试命令；
- 聊天摘要；
- 普通 bug 记录；
- 长篇 reasoning；
- 当前任务进度。

```text
AGENTS
!= Chat Summary
!= Dev Log
!= Postmortem
!= Handoff
```

## 4. Hierarchical Rules

规则放在最小适用作用域：

- repo-wide → root Agent rules；
- package/module-specific → nearest subtree rules；
- docs-specific → docs rules；
- test-specific → test/dev guide；
- 当前任务 → Handoff。

不要让根规则变成几千行百科全书。

## 5. Current Truth

Current Docs 回答：

> 现在系统实际上是什么？

包括当前架构、模块、API、数据模型、运行方式、依赖方向。

不应混入未来计划、历史实现、被否决方案、调试过程。

## 6. Decision / Agent Note

Decision 回答：

> 为什么主动选择这样设计？

建议包含：

- Problem；
- Context；
- Decision；
- Alternatives considered；
- 为什么没选其他方案；
- Consequences；
- Validation / Testing；
- Status；
- Supersedes / Superseded by（如适用）。

尤其记录容易被后续 Agent 再次提出的已否决方案。

目标：

> 不重新诉讼已经解决的问题。

## 7. Decision Lifecycle

自适应已有 ADR/RFC 体系。

没有现成体系时可采用最小生命周期：

- Proposed；
- Implemented / Accepted；
- Rejected；
- Archived。

Archived 可以作为历史证据，但不再作为当前权威。

## 8. Supersession Check

新增重要 Decision 前先搜索旧决策。

判断：

- 新增；
- 部分替代；
- 完全替代；
- 只是实现细节变化。

完全替代时旧 Decision 应标记 archived/superseded，并双向链接。

不要让互相冲突的两个 Decision 同时看起来都是 Current。

## 9. Postmortem

Postmortem 回答：

> 为什么这个问题逃过了已有测试、Review 和 CI？

应包含：

- What broke；
- Impact；
- Root mechanism；
- Why existing safety nets missed it；
- Process/system gap；
- Permanent guardrail；
- Links to regression tests / gates / decisions。

只对严重、重复、跨层或暴露制度缺口的问题使用 Postmortem。

普通小 bug 不需要永久事故文档。

## 10. Incident → Knowledge

```text
Incident
   ↓
原因/逃逸机制
→ Postmortem（必要时）

长期规则
→ Standing Rule

机器能稳定判断
→ Test / Gate

设计改变
→ Decision

当前没做完
→ Handoff
```

## 11. Chat → Knowledge Distillation

Raw Conversation 不应直接复制进永久规范。

```text
Raw Conversation
        ↓
    Distillation
        ↓
┌────────┬──────────┬───────────┬──────────┐
Rules   Decisions  Incidents   Handoff
```

值得沉淀：

1. 用户明确确认的长期规则；
2. 重要设计决定；
3. 被明确否决且容易重新提出的方案；
4. 真实事故和教训；
5. 当前任务进度。

探索过程、临时命令、重复讨论、已失效推理不应变成永久知识。

## 12. 跨会话原始聊天

如果环境支持真实检索历史聊天：

- 使用真实来源；
- 不只依赖摘要；
- 必要时恢复被蒸馏掉的细节。

如果环境不支持：

- 不得凭模糊记忆假装知道；
- 依赖仓库 Decision / Handoff / Postmortem / Git / Issue / PR；
- 必要时说明证据边界。

关键原则：

> 原始聊天不能成为长期维护所必需的唯一知识源。

## 13. Agent Handoff

Handoff 是短期 Working Continuity，不是永久文档。

适合记录：

- Goal；
- Current status；
- Confirmed facts；
- User decisions；
- Rejected approaches；
- Files changed；
- Verification performed；
- Known failures；
- Remaining work；
- Next recommended step。

## 14. Handoff Lifecycle

```text
Conversation / Work
        ↓
Handoff
        ↓
Next Agent continues
        ↓
Task completed
        ↓
Distill durable knowledge
```

任务完成后：

- 长期规则 → Standing Rules；
- 当前事实 → Current Docs；
- 设计理由 → Decision；
- 严重事故 → Postmortem；
- 自动防护 → Tests/Gates；
- 临时过程 → 删除；
- Handoff → 删除或归档。

不要让 Handoff 永久堆积成新的 PROJECT_MEMORY。

## 15. Tests / Gates = Executable Memory

测试和 Gate 是机器能执行的项目记忆。

```text
曾经权限绕过
→ Regression Test

曾经非法依赖
→ Architecture Gate

曾经 Mock 绿但真实 API 坏
→ Real Verification

曾经数据库兼容问题
→ DB Contract
```

Markdown 告诉后续 Agent“为什么”；Test/Gate 直接阻止同类错误再次进入。

## 16. One Fact, One Home

一个事实只维护一个 canonical owner。

其他位置只简短引用或链接，不复制完整内容。

例如：

AGENTS：永久规则；
Postmortem：历史事故和逃逸机制；
Test：机器证明规则不会再次被破坏。

## 17. Canonical Ownership

例如：

- runtime version → build config；
- dependencies → manifest/lockfile；
- schema → canonical schema/migration source；
- API → schema/code；
- current architecture → architecture owner doc；
- standing rule → Agent rule owner；
- decision → ADR/Note；
- historical failure → Postmortem；
- working state → Handoff；
- generated catalog → generator source。

Markdown 不应成为第二份配置文件。

## 18. Generated Truth

如果某类文档事实能可靠从源码生成、经常漂移、人工维护成本高，可以考虑：

```text
Source
→ Generate
→ Commit/Publish
→ Freshness Gate
```

适合 CLI help、config catalog、tool catalog、module graph、API reference、package exports、schema catalog。

不要为了自动生成文档再造大型平台。

## 19. Documentation Integrity

按项目价值评估：

- Markdown links；
- generated docs freshness；
- executable examples；
- code snippets typecheck；
- obsolete references；
- archive/current status。

不是所有项目都需要文档 CI。

## 20. Knowledge Simplification Review

检查：

- AGENTS 是否塞了历史故事；
- Decision 是否互相冲突；
- Archived 是否仍被当 current；
- Handoff 是否过期堆积；
- Postmortem 是否只是普通 bug 日志；
- 同一事实是否重复维护；
- Raw chat 是否被直接复制成永久文档；
- tests/gates 是否已替代重复文字规则；
- 是否存在没人知道 owner 的事实。

项目记忆越多，不等于项目越聪明。

关键是：

> 知识有层级、有 owner、有生命周期。
