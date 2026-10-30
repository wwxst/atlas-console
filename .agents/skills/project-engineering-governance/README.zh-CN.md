# 项目工程治理 Skill

[English](README.md) | [简体中文](README.zh-CN.md)

让 Vibe Coding 项目逐步变成可维护、证据驱动、适合 AI Coding Agent 长期持续开发的软件项目。

这是一个面向 Codex、Claude Code 等 Coding Agent 的通用工程治理 Skill，覆盖：

- 工程规范
- 测试与验证策略
- Machine Gate / CI
- 文档治理
- Simplification Review
- Decision / Postmortem / Handoff
- Agent 知识连续性

## 核心原则

- **Current Reality First**：以当前真实代码、调用链和运行行为为准。
- **Evidence-Driven**：重要行为要有可重复验证证据。
- **Coverage 是审查信号，不是目标**：未覆盖代码先判断是否真的需要。
- **验证层级是分类模型，不是完成清单**：不为了体系完整强行补测试。
- **One Fact, One Home**：同一个事实只有一个权威来源。
- **Simplification 是治理的一部分**：治理体系自身也要避免过度设计。

## 工作方式

```text
检查当前仓库
↓
确认真实情况
↓
识别项目风险和稳定规则
↓
发现验证缺口和不必要复杂度
↓
决定哪些规则值得机器化
↓
最小修改
↓
运行真实验证
↓
Simplification Review
↓
沉淀长期项目知识

project-engineering-governance-skill/
├─ SKILL.md
├─ references/
│  ├─ engineering-governance.md
│  ├─ verification-governance.md
│  ├─ knowledge-continuity.md
│  └─ advanced-governance.md
├─ README.md
├─ README.zh-CN.md
└─ LICENSE
