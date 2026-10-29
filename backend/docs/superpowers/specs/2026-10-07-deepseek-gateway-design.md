# 多模型付费 API 网关设计（Sub2API 上游）

## 1. 目标与范围

本期在现有 Spring Boot 单体后端中增加多模型付费 API 网关。用户和 Agent 永远只访问我们自己的 backend，backend 再将请求转发到已部署的 Sub2API。

调用链如下：

```text
用户 / Agent
    -> 我们的 Spring Boot backend
    -> UpstreamRouter
    -> Sub2ApiClient
    -> Sub2API
    -> Claude / GPT / Gemini / DeepSeek 等模型
```

本期目标：

- 提供统一的 `POST /v1/chat/completions` 和 `GET /v1/models`。
- 支持多个公开模型、公开模型名与上游模型名映射。
- 支持不同 Sub2API 上游通道，每个模型首期固定绑定一个通道。
- 维护用户 API Key 的创建、查询、撤销和网关鉴权。
- 维护用户钱包、余额、预授权、冻结、实际 usage 结算、消费流水和补偿。
- 按价格档位、基础售价和整数计费倍率计算用户消费。
- 同时保存和统计 `upstreamCost`、`userCharge` 和 `margin`。
- 保留 `/v1/responses`、`/v1/messages` 的内部扩展边界，但本期不实现这两个协议。

本期明确不实现：

- 各模型官方 API 的直接接入。
- Sub2API 账号池、多 Key 轮询、自动故障切换和上游额度调度。
- 复杂 API Key 配额、IP 白名单、细粒度模型权限和轮换策略。
- 消息队列、独立网关微服务和异步账单服务。
- 管理端前端页面。

后续增加 OpenAI、Anthropic、DeepSeek 等官方直连时，只新增对应的 `UpstreamClient` 和通道类型，不改变用户接口、钱包和账务结构。

## 2. 现有系统约束

- 后端使用 Spring Boot 4、Spring MVC、MyBatis、MySQL、Redis 和 JWT。
- 普通用户 Access Token 含 `SCOPE_user`，系统用户接口含 `SCOPE_admin`。
- 数据库结构和初始数据的唯一事实来源是 `backend/web_project.sql`，不新增迁移脚本。
- Controller 只处理 HTTP 输入、认证上下文和响应；Service 负责业务规则与事务；Mapper 负责 MyBatis 数据访问。
- 数据库表保持零外键约束，使用 ID、唯一键和业务事务维护关联。
- 用户钱包和消费统一使用整数积分：`1 CNY = 10 points`。
- 倍率、价格、成本和积分计算不使用 `float` 或 `double`。
- 上游 API Key 使用现有部署密钥加密能力保存，数据库和日志不出现明文。

## 3. 系统职责边界

### 3.1 我们的 backend

backend 负责：

- 用户身份认证和权限判断。
- API Key 创建、查询、撤销和校验。
- 公开模型管理和模型映射。
- 上游通道管理。
- 请求入口、请求大小和输出上限校验。
- 价格档位和价格快照。
- 钱包、余额、预授权、冻结和结算。
- 上游成本、用户消费和差额计算。
- 消费流水、充值、管理员调整和补偿。
- 统一错误、日志脱敏和用户数据隔离。

### 3.2 Sub2API

Sub2API 是已部署的上游统一模型中转项目，负责：

- 将请求转发到 Claude、GPT、Gemini、DeepSeek 等模型。
- 上游模型协议适配。
- Sub2API 自身的账号、分组、额度池、轮询和故障切换。
- 返回统一的模型响应和 Token usage。

backend 不重新实现各模型厂商官方协议，也不把 Sub2API 的账号池和轮询逻辑复制到本项目。

## 4. 核心架构

```text
AiGatewayController
        -> AiGatewayService
        -> UpstreamRouter
        -> UpstreamClient
        -> Sub2ApiClient
```

### `AiGatewayController`

负责 `/v1/*` HTTP 参数、认证上下文和流式响应，不直接访问数据库，不直接修改钱包。

### `AiGatewayService`

负责一次完整的网关请求：

1. 解析 JWT 或用户 API Key 得到普通用户 ID。
2. 校验用户、公开模型和上游通道状态。
3. 解析当前价格档位并锁定价格快照。
4. 计算预授权金额，原子冻结用户余额。
5. 调用 `UpstreamRouter`。
6. 转发非流式 JSON 或 SSE 流式响应。
7. 读取 usage，计算上游成本、用户扣费和差额。
8. 写入请求记录、用量记录和账务流水。
9. 释放冻结金额中的差额，或在失败时全部释放。

该服务只依赖通用模型、价格、通道和账务模型，不依赖 Sub2API 专属实现。

### `UpstreamRouter`

根据上游通道类型和 endpoint 类型选择客户端。本期只支持：

```text
SUB2API -> Sub2ApiClient
```

后续可增加：

```text
OPENAI_DIRECT -> OpenAiDirectClient
ANTHROPIC_DIRECT -> AnthropicDirectClient
DEEPSEEK_DIRECT -> DeepSeekDirectClient
```

### `UpstreamClient`

提供通用的上游调用边界，表达：

- endpoint 类型
- 上游模型名
- 请求载荷
- stream 标志
- 上游响应或流
- input/output/total tokens
- 上游 request ID
- 上游错误

本期只实现 `Sub2ApiClient`。模型、钱包、计费和账务不引用该类。

## 5. API Key 基础能力

本期纳入用户 API Key 基础能力，支持：

- 创建
- 查询列表
- 撤销
- 网关鉴权

本期不实现：

- Key 配额
- IP 白名单
- 细粒度模型权限
- 轮换策略
- 自动过期
- Key 分组和层级管理

### 5.1 创建 API Key

```http
POST /api/user/api-keys
Authorization: Bearer <user-access-token>
Content-Type: application/json
```

请求：

```json
{
  "name": "我的 Agent"
}
```

响应只在创建成功时返回一次完整明文 Key：

```json
{
  "id": 1,
  "name": "我的 Agent",
  "apiKey": "sk-atlas-...",
  "keyPrefix": "sk-atlas-ab12",
  "createdAt": "2026-10-07T15:00:00"
}
```

服务端不保存明文 Key，用户关闭页面后不能再次查询完整 Key。

### 5.2 查询 API Key

```http
GET /api/user/api-keys
Authorization: Bearer <user-access-token>
```

只返回：

- ID
- 名称
- Key 前缀
- 状态
- 创建时间
- 最近使用时间
- 撤销时间

不返回完整 Key、Hash 或内部密钥材料。

### 5.3 撤销 API Key

```http
DELETE /api/user/api-keys/{id}
Authorization: Bearer <user-access-token>
```

撤销操作幂等。撤销后的 Key 立即不能调用 `/v1/*`。

### 5.4 API Key 鉴权

网关支持以下两种身份：

1. 普通用户 JWT Access Token。
2. 用户 API Key。

OpenAI 兼容客户端可以直接使用：

```http
Authorization: Bearer <user-api-key>
```

也可以支持：

```http
X-API-Key: <user-api-key>
```

API Key 格式使用可识别前缀，例如 `sk-atlas-`。认证流程根据前缀识别 Key，查询 Hash 后的密钥记录，确认状态为 `ACTIVE`，再加载对应普通用户身份和 `SCOPE_user` 权限。

API Key 只用于 `/v1/*` 网关接口，不能替代用户 Access Token 调用用户资料、充值或 API Key 管理接口。API Key 认证成功后只更新最近使用时间，不改变余额和权限。

创建、查询和撤销 API Key 必须使用现有普通用户 Access Token。

## 6. 多模型和上游通道

### 6.1 公开模型

模型同时保存用户侧名称和上游名称：

| 用户模型名 | 模型类型 | 上游模型名 |
| --- | --- | --- |
| `claude-pro` | `CLAUDE` | `claude-3-7-sonnet` |
| `gpt-plus` | `GPT` | `gpt-4.1` |
| `gemini-pro` | `GEMINI` | `gemini-2.5-pro` |
| `deepseek-chat` | `DEEPSEEK` | `deepseek-chat` |

用户始终提交 `publicModel`。backend 转换为配置中的 `upstreamModel`。

修改上游模型名不会改变公开接口，也不会影响已经保存的历史账单，因为请求和用量记录保存模型快照。

### 6.2 上游通道

每个 Sub2API API Key 对应一个通道：

| 字段 | 说明 |
| --- | --- |
| `name` | 通道名称，例如 `Claude-Kiro` |
| `channelType` | 上游实现类型，本期固定为 `SUB2API` |
| `protocolType` | API 协议类型：`CHAT_COMPLETIONS`、`RESPONSES` 或 `MESSAGES` |
| `baseUrl` | Sub2API 地址 |
| `encryptedApiKey` | 加密保存的 API Key |
| `enabled` | 是否启用 |

一个公开模型首期固定绑定一个通道。禁用通道后，绑定模型不能调用上游。

一条通道首期固定使用一种 API 协议。`protocolType` 决定请求和响应格式；`channelType` 决定后端使用哪一种上游实现。以后接入其他供应商时，可以复用三种协议类型，只新增对应的上游实现和路由客户端。

不实现多 Key 自动轮询、账号池、自动故障切换和动态负载均衡。

## 7. 对外接口和协议边界

### 7.1 本期实现

```text
POST /v1/chat/completions
GET  /v1/models
```

两类调用均只能访问管理员启用的公开模型。

### 7.2 后续预留

内部 endpoint 类型统一定义为：

```text
CHAT_COMPLETIONS
RESPONSES
MESSAGES
```

本期只实现 `CHAT_COMPLETIONS`，但 `AiGatewayService`、`UpstreamRouter` 和 `UpstreamClient` 不应把所有业务逻辑写死为 Chat Completions。后续新增 `/v1/responses` 或 `/v1/messages` 时，只增加对应的请求/响应适配和客户端能力。

通道配置中的 `protocolType` 是协议能力声明；网关收到请求后，必须校验请求的 `endpointType` 与通道协议一致，再交给对应的协议适配器处理。`ai_gateway_request.endpoint_type` 保存本次实际使用的协议快照。

## 8. 价格档位和计费模型

每个模型支持以下价格档位：

```text
STANDARD
PEAK
OFF_PEAK
```

每个档位分别配置：

- `upstreamInputCost`
- `upstreamOutputCost`
- `baseInputSalePrice`
- `baseOutputSalePrice`
- `billingMultiplierPpm`

用户最终售价：

```text
用户最终售价 = 基础售价 × 计费倍率
```

### 8.1 积分单位

用户充值和模型消费统一使用积分：

```text
1 CNY = 10 points
```

钱包、预授权、消费、退款、`upstreamCost`、`userCharge` 和 `margin` 都保存为整数积分。价格单位为每百万 Token 对应的积分。充值入口接收人民币金额或支付回调金额时，服务端按整数分转换为积分；例如 100 分人民币转换为 10 积分。账务层不把人民币金额和积分放在同一个余额字段中。

### 8.2 倍率单位

倍率使用百万分比整数：

```text
1.0  = 1000000
1.25 = 1250000
1.5  = 1500000
```

不使用 `float` 或 `double`。Java 实现使用 `BigInteger` 或经过溢出检查的整数运算。

设 `SCALE = 1,000,000`：

```text
userInputPricePoints = ceil(baseInputSalePricePoints × billingMultiplierPpm / SCALE)
userOutputPricePoints = ceil(baseOutputSalePricePoints × billingMultiplierPpm / SCALE)
```

### 8.3 上游成本、用户扣费和差额

上游成本不再使用 `officialCost`，统一使用 `upstreamCost`：

```text
upstreamCostPoints
  = ceil(inputTokens × upstreamInputCostPoints / 1,000,000)
  + ceil(outputTokens × upstreamOutputCostPoints / 1,000,000)
```

用户扣费：

```text
userChargePoints
  = ceil(inputTokens × userInputPricePoints / 1,000,000)
  + ceil(outputTokens × userOutputPricePoints / 1,000,000)
```

差额：

```text
marginPoints = userChargePoints - upstreamCostPoints
```

Sub2API 首期一般只返回 Token usage，不一定返回成本金额。因此 `upstreamCostPoints` 使用 `ai_model_price` 中保存的上游输入/输出成本积分价格计算。三项统计字段对外仍命名为 `upstreamCost`、`userCharge` 和 `margin`，但其数值单位统一为积分。

未来官方直连时，官方实际成本仍然写入 `upstreamCost`，不修改钱包、账务或统计结构。

### 8.4 价格档位选择

当前价格档位由 backend 选择，用户不能自行传入任意价格档位。首期使用系统配置：

```text
ai.gateway.current-price-band
```

默认值为 `STANDARD`。模型没有当前档位价格时，使用该模型的 `STANDARD` 价格，并将最终生效的档位写入请求快照。

本期不实现自动按时间切换峰谷价格；后续可增加调度规则，不改变历史账单结构。

## 9. 请求预授权和结算

一次请求的流程：

1. 使用 JWT 或 API Key 获取普通用户 ID。
2. 校验用户状态。
3. 校验 `publicModel` 存在且启用。
4. 校验绑定上游通道存在且启用。
5. 选择当前价格档位。
6. 保存官方模型、上游模型、通道和价格快照。
7. 根据请求字节数和 `max_tokens` 计算保守的预授权积分。
8. 原子冻结用户余额。
9. 调用 Sub2API。
10. 转发 JSON 或 SSE 响应。
11. 读取输入/输出 Token usage。
12. 计算 `upstreamCost`、`userCharge` 和 `margin`。
13. 写入用量记录和消费流水。
14. 从冻结积分扣除实际用户费用，并退回差额积分。

余额不足时不调用上游、不创建消费流水。

上游 4xx、5xx、timeout、连接失败或请求未建立时释放全部冻结金额，状态为 `UPSTREAM_FAILED`。

流式响应没有 usage 时，状态为 `USAGE_MISSING`，按预授权金额结算并记录异常原因，避免少扣费。

上游调用不放在 MySQL 事务内。预授权和请求记录在一个事务中完成；上游完成后的结算是独立事务，以 `request_id` 幂等。

如果进程在上游成功后、结算前崩溃，轻量定时补偿任务扫描长时间处于 `RESERVED` 的请求，按已有 usage 结算或按规则释放。该任务不使用消息队列。

## 10. 数据模型

所有新表写入 `backend/web_project.sql`，不新增迁移脚本，不创建 Foreign Key。

### 10.1 `ai_wallet`

| 字段 | 说明 |
| --- | --- |
| `user_id` | 普通用户 ID，主键 |
| `available_points` | 可用积分 |
| `reserved_points` | 冻结积分 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

### 10.2 `ai_api_key`

| 字段 | 说明 |
| --- | --- |
| `id` | Key ID |
| `user_id` | 普通用户 ID |
| `name` | 用户给 Key 设置的名称 |
| `key_prefix` | 用于展示的前缀 |
| `secret_hash` | 完整 Key 的单向 Hash |
| `status` | `ACTIVE` 或 `REVOKED` |
| `last_used_at` | 最近使用时间 |
| `created_at` | 创建时间 |
| `revoked_at` | 撤销时间 |
| `updated_at` | 更新时间 |

不保存完整明文 Key。`secret_hash` 建立唯一约束，`user_id/status` 建立查询索引。

### 10.3 `ai_upstream_channel`

| 字段 | 说明 |
| --- | --- |
| `id` | 通道 ID |
| `name` | 通道名称，唯一 |
| `channel_type` | 上游实现类型，本期为 `SUB2API` |
| `protocol_type` | API 协议类型：`CHAT_COMPLETIONS`、`RESPONSES` 或 `MESSAGES` |
| `base_url` | Sub2API Base URL |
| `encrypted_api_key` | 加密后的 Sub2API API Key |
| `enabled` | 是否启用 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

### 10.4 `ai_gateway_model`

| 字段 | 说明 |
| --- | --- |
| `id` | 模型 ID |
| `public_model` | 用户侧模型名，唯一 |
| `model_family` | `CLAUDE`、`GPT`、`GEMINI`、`DEEPSEEK` 等 |
| `upstream_model` | Sub2API 实际模型名 |
| `upstream_channel_id` | 固定绑定的通道 ID |
| `enabled` | 是否启用 |
| `max_input_bytes` | 最大请求字节数 |
| `max_output_tokens` | 最大输出 Token |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

### 10.5 `ai_model_price`

| 字段 | 说明 |
| --- | --- |
| `id` | 价格 ID |
| `model_id` | 模型 ID |
| `price_band` | `STANDARD`、`PEAK`、`OFF_PEAK` |
| `upstream_input_cost_points` | 上游输入成本，积分/百万 Token |
| `upstream_output_cost_points` | 上游输出成本，积分/百万 Token |
| `base_input_sale_price_points` | 基础输入售价，积分/百万 Token |
| `base_output_sale_price_points` | 基础输出售价，积分/百万 Token |
| `billing_multiplier_ppm` | 百万分比整数倍率 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

唯一约束为 `(model_id, price_band)`。

### 10.6 `ai_gateway_request`

至少保存：

- `request_id`
- `idempotency_key`
- `user_id`
- `public_model`
- `model_family`
- `upstream_model`
- `upstream_channel_id`
- `endpoint_type`
- `price_band`
- 上游输入/输出成本快照
- 基础输入/输出售价快照
- 倍率快照
- 最终用户输入/输出售价快照
- 预授权积分
- input/output/total token
- `upstream_cost`
- `user_charge`
- `margin`
- `upstream_request_id`
- 请求状态
- 创建时间和完成时间

状态至少包括：

```text
RESERVED
SETTLED
RELEASED
UPSTREAM_FAILED
USAGE_MISSING
```

同一个用户的 `Idempotency-Key` 重试返回原请求状态，不重复调用或扣费；未提供幂等键时，重复提交视为新请求。

### 10.7 `ai_usage_record`

用于独立统计，保存：

- 用户 ID
- `public_model`
- `model_family`
- `upstream_model`
- `upstream_channel_id`
- `endpoint_type`
- `price_band`
- input/output/total token
- 上游输入/输出成本积分价格快照
- 基础输入/输出售价积分快照
- 倍率快照
- 最终用户输入/输出售价快照
- `upstreamCost`
- `userCharge`
- `margin`
- 状态和时间

历史统计只读取快照，不回读当前模型价格。

### 10.8 `ai_billing_transaction`

不可变账务流水，保存：

- 用户 ID
- 请求 ID
- 类型
- 正负积分变化
- 变更后可用积分
- 充值对应的人民币分金额（充值流水可选）
- 操作人
- 幂等操作 ID
- 备注
- 创建时间

类型至少包括：

```text
TOP_UP
CONSUMPTION
REFUND
ADJUSTMENT
```

消费流水通过 `request_id` 关联 `ai_usage_record`，同时返回 `upstreamCost`、`userCharge` 和 `margin`。充值和管理员调整流水的三项金额为空。

## 11. 管理接口范围

系统用户使用 `SCOPE_admin` 管理：

```text
GET/POST/PUT/DELETE /api/sys-user/gateway/channels
GET/POST/PUT/DELETE /api/sys-user/gateway/models
GET/PUT /api/sys-user/gateway/models/{id}/prices/{priceBand}
GET/PUT /api/sys-user/gateway/price-band
GET /api/sys-user/billing/transactions
GET /api/sys-user/billing/usage
GET /api/sys-user/billing/users/{userId}/balance
POST /api/sys-user/billing/users/{userId}/adjustments
```

管理员只能看到 API Key 是否已配置和脱敏标识，不能查询 Sub2API API Key 明文。

## 12. 用户账务接口

普通用户使用 `SCOPE_user`，用户 ID 只从 JWT 读取：

```text
GET /api/user/billing/balance
GET /api/user/billing/transactions
GET /api/user/billing/usage
GET /api/user/billing/usage/summary
```

明细和汇总同时支持：

- Token 数量
- `upstreamCost`
- `userCharge`
- `margin`
- 模型
- 通道
- 价格档位

用户只能读取自己的钱包、流水和用量。

## 13. 安全、错误和日志

- 用户不能获得 Sub2API API Key。
- 用户不能指定任意 upstream URL。
- 用户只能调用管理员启用的模型。
- 禁用模型和禁用通道都不能访问上游。
- API Key 只保存单向 Hash，完整明文只在创建响应中出现一次。
- 撤销后的 API Key 立即失效。
- API Key 不能调用用户资料、账务管理或 Key 管理接口。
- 日志不得记录 API Key、Authorization、完整 prompt、完整 messages 或完整响应。
- 余额不足、模型未启用、通道未启用、请求超限、上游不可用和 usage 缺失使用稳定业务错误码。
- 上游错误不暴露 Sub2API 的内部密钥、详细地址或堆栈。
- 价格、倍率、余额、Key 状态和账务写入都必须检查影响行数。

## 14. 测试与验收

### API Key

- 创建成功只返回一次完整明文 Key。
- 查询列表不返回明文、Hash 或完整 Key。
- 撤销后 Key 立即鉴权失败。
- JWT 和 API Key 都能访问 `/v1/*`。
- API Key 不能访问用户资料、充值和 Key 管理接口。
- 不同用户不能互相查询或撤销 API Key。
- API Key、Authorization 不进入日志。

### 多模型路由

- `publicModel` 正确映射到 `upstreamModel`。
- 请求路由到正确的 Sub2API 通道。
- 不同模型可以绑定不同通道。
- 禁用模型不能调用上游。
- 禁用通道不能调用上游。
- 修改上游模型名不影响用户接口和历史账单。

### 价格和结算

- `STANDARD`、`PEAK`、`OFF_PEAK` 选择正确。
- 没有峰谷价格的模型使用 `STANDARD`。
- 倍率百万分比整数计算正确。
- `1` 元充值转换为 `10` 积分，充值金额和积分不使用浮点计算。
- 输入和输出分别计价，舍入结果正确。
- `upstreamCost`、`userCharge`、`margin` 计算正确。
- 修改价格、倍率、模型映射或通道后，历史快照不变。

### 钱包和账务

- 余额不足时不请求上游。
- 预授权正确冻结余额。
- 实际 usage 结算正确多退少补。
- 上游失败释放全部冻结金额。
- 重复 `requestId` 或 `Idempotency-Key` 不重复扣费。
- 并发预授权时余额不能为负。
- 消费流水、用量记录和最终钱包积分一致。
- 充值和管理员调整具有幂等性。

### 上游和协议

- Sub2API 4xx、5xx、timeout 和连接失败。
- 非流式 usage 结算。
- 流式 usage 结算。
- 流式响应缺失 usage 的约定处理。
- 客户端断开连接后的冻结金额处理。
- `/v1/chat/completions` 不接受任意上游 URL。
- `/v1/models` 只返回启用的公开模型。

### 并发、集成和日志

- MySQL 条件扣款并发测试。
- MySQL 唯一键防止重复结算。
- 真实应用启动后的 HTTP 鉴权和转发冒烟。
- API Key、Authorization、prompt/messages 和完整响应日志扫描。

## 15. 后续接入官方直连

需要修改：

1. 新增 `OpenAiDirectClient`、`AnthropicDirectClient` 或 `DeepSeekDirectClient`。
2. 扩展上游通道类型。
3. 在 `UpstreamRouter` 中增加类型到客户端的映射。
4. 新增或调整模型的上游通道绑定。
5. 为直连模型维护对应的上游成本价格。
6. 为不同协议增加请求和响应转换。

不需要修改：

- 用户 JWT 和 API Key 管理
- `/v1` 用户入口
- `AiGatewayService` 的钱包和账务流程
- 预授权、冻结和余额不足控制
- 消费流水、充值和管理员调整
- `ai_gateway_request`
- `ai_usage_record`
- `ai_billing_transaction`
- `upstreamCost`
- `userCharge`
- `margin`
- 历史价格快照
- 幂等和结算补偿

官方直连只改变上游客户端和通道类型。账务仍然统一记录 `upstreamCost`，不需要重新设计钱包或流水。

## 16. 相比 DeepSeek 专属方案的调整

### 删除

- 单一 DeepSeek 全局 API Key。
- `ai_gateway_config` 单通道配置模式。
- DeepSeek 专属模型路由。
- “官方价格 × 倍率”作为唯一计费规则。
- `officialCost` 命名。
- 只支持 DeepSeek 的 Gateway Service 和 Client。
- 长期 API Key 被排除在本期范围之外的限制。

### 改名

- `DeepSeekGatewayService` -> `AiGatewayService`
- `DeepSeekClient` -> `Sub2ApiClient`
- `officialCost` -> `upstreamCost`
- 官方价格字段 -> 上游成本字段
- DeepSeek 配置 -> 上游通道配置
- DeepSeek 模型 -> 通用模型

### 新增

- `UpstreamRouter`
- `UpstreamClient`
- `ai_api_key`
- `ai_upstream_channel`
- `ai_model_price`
- `publicModel`
- `modelFamily`
- `upstreamModel`
- `endpointType`
- `protocolType`
- `priceBand`
- 基础销售价
- 整数计费倍率
- 上游成本快照
- 用户售价快照
- API Key 创建、查询、撤销和鉴权
- `/v1/responses` 和 `/v1/messages` 内部协议预留

最终边界为：

```text
模型、钱包、计费、账务和 API Key = backend 通用业务能力
Sub2API = 本期唯一上游实现
UpstreamClient = 后续官方直连扩展边界
```

实现时仍需遵循 `backend/AGENTS.md`、`docs/engineering.md` 和 `docs/quality-gates.md`，最终同步 `backend/web_project.sql` 与 `backend/docs/api.md`。
