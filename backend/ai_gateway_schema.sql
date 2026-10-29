/**
 * 多模型付费 API 网关数据库结构草案（Sub2API 上游）
 *
 * 本文件用于独立审阅，暂不并入 web_project.sql。
 * 遵循当前项目约束：不创建 Foreign Key，表之间通过 ID、唯一键和业务事务关联。
 */

USE web_project;

/* 用户钱包：余额和消费单位为积分，1 CNY = 10 points。 */
CREATE TABLE ai_wallet
(
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '普通用户ID，不创建外键',
    available_points BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '可用积分',
    reserved_points  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '请求冻结积分',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '普通用户 AI 网关钱包';

/* 用户 API Key：只保存单向 Hash，完整明文只在创建成功时返回一次。 */
CREATE TABLE ai_api_key
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'API Key ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '普通用户ID，不创建外键',
    name           VARCHAR(100)    NOT NULL COMMENT '用户设置的 Key 名称',
    key_prefix     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '展示用 Key 前缀',
    secret_hash    VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整 Key 的单向 Hash',
    status         VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 或 REVOKED',
    last_used_at   DATETIME        DEFAULT NULL COMMENT '最近使用时间',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    revoked_at     DATETIME        DEFAULT NULL COMMENT '撤销时间',
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_api_key_secret_hash (secret_hash),
    KEY idx_ai_api_key_user_status (user_id, status),
    CONSTRAINT chk_ai_api_key_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '普通用户 API Key';

/* 上游通道：每个 Sub2API API Key 对应一个通道。 */
CREATE TABLE ai_upstream_channel
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上游通道ID',
    name               VARCHAR(100)    NOT NULL COMMENT '通道名称',
    channel_type       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游实现类型，本期为 SUB2API',
    protocol_type      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'API 协议类型：CHAT_COMPLETIONS、RESPONSES 或 MESSAGES',
    base_url           VARCHAR(512)    NOT NULL COMMENT '上游 Base URL',
    encrypted_api_key  VARCHAR(1024)   NOT NULL COMMENT '加密后的上游 API Key',
    enabled            TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_upstream_channel_name (name),
    KEY idx_ai_upstream_channel_channel_type_enabled (channel_type, enabled),
    CONSTRAINT chk_ai_upstream_channel_channel_type CHECK (channel_type IN ('SUB2API')),
    CONSTRAINT chk_ai_upstream_channel_protocol CHECK (protocol_type IN ('CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES')),
    CONSTRAINT chk_ai_upstream_channel_enabled CHECK (enabled IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关上游通道';

/* 用户可见模型与上游模型的映射。 */
CREATE TABLE ai_gateway_model
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模型ID',
    public_model         VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户侧稳定模型名',
    model_family         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型族，例如 GPT、CLAUDE、GEMINI、DEEPSEEK',
    upstream_model       VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游实际模型名',
    upstream_channel_id  BIGINT UNSIGNED NOT NULL COMMENT '固定绑定的上游通道ID，不创建外键',
    enabled              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    max_input_bytes      INT UNSIGNED    NOT NULL COMMENT '最大请求体字节数',
    max_output_tokens    INT UNSIGNED    NOT NULL COMMENT '最大输出 Token 数',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_gateway_model_public_model (public_model),
    KEY idx_ai_gateway_model_channel_enabled (upstream_channel_id, enabled),
    CONSTRAINT chk_ai_gateway_model_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_ai_gateway_model_limits CHECK (max_input_bytes > 0 AND max_output_tokens > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关公开模型';

/* 模型分价格档位配置：金额和倍率均使用整数保存。 */
CREATE TABLE ai_model_price
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模型价格ID',
    model_id                 BIGINT UNSIGNED NOT NULL COMMENT '模型ID，不创建外键',
    price_band               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'STANDARD、PEAK 或 OFF_PEAK',
    upstream_input_cost_points    BIGINT UNSIGNED NOT NULL COMMENT '上游输入成本，积分/百万Token',
    upstream_output_cost_points   BIGINT UNSIGNED NOT NULL COMMENT '上游输出成本，积分/百万Token',
    base_input_sale_price_points  BIGINT UNSIGNED NOT NULL COMMENT '基础输入售价，积分/百万Token',
    base_output_sale_price_points BIGINT UNSIGNED NOT NULL COMMENT '基础输出售价，积分/百万Token',
    billing_multiplier_ppm   BIGINT UNSIGNED NOT NULL DEFAULT 1000000 COMMENT '计费倍率，百万分比；1000000表示1.0',
    created_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_price_model_band (model_id, price_band),
    KEY idx_ai_model_price_band (price_band),
    CONSTRAINT chk_ai_model_price_band CHECK (price_band IN ('STANDARD', 'PEAK', 'OFF_PEAK')),
    CONSTRAINT chk_ai_model_price_multiplier CHECK (billing_multiplier_ppm > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关模型价格与计费倍率';

/* 一次网关请求：保存路由、价格、倍率和结算快照。 */
CREATE TABLE ai_gateway_request
(
    id                              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部请求记录ID',
    request_id                      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '网关请求ID',
    idempotency_key                 VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '用户幂等键，可为空',
    user_id                         BIGINT UNSIGNED NOT NULL COMMENT '普通用户ID，不创建外键',
    public_model                    VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户侧模型名快照',
    model_family                    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型族快照',
    upstream_model                  VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游模型名快照',
    upstream_channel_id             BIGINT UNSIGNED NOT NULL COMMENT '上游通道ID快照，不创建外键',
    endpoint_type                   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CHAT_COMPLETIONS、RESPONSES 或 MESSAGES',
    price_band                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '生效价格档位快照',
    upstream_input_cost_points_snapshot    BIGINT UNSIGNED NOT NULL COMMENT '上游输入成本快照，积分/百万Token',
    upstream_output_cost_points_snapshot   BIGINT UNSIGNED NOT NULL COMMENT '上游输出成本快照，积分/百万Token',
    base_input_sale_price_points_snapshot  BIGINT UNSIGNED NOT NULL COMMENT '基础输入售价快照，积分/百万Token',
    base_output_sale_price_points_snapshot BIGINT UNSIGNED NOT NULL COMMENT '基础输出售价快照，积分/百万Token',
    billing_multiplier_ppm_snapshot BIGINT UNSIGNED NOT NULL COMMENT '计费倍率快照，百万分比',
    user_input_price_points_snapshot  BIGINT UNSIGNED NOT NULL COMMENT '最终用户输入售价快照，积分/百万Token',
    user_output_price_points_snapshot BIGINT UNSIGNED NOT NULL COMMENT '最终用户输出售价快照，积分/百万Token',
    reserved_points                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '预授权积分',
    input_tokens                    BIGINT UNSIGNED DEFAULT NULL COMMENT '实际输入Token数',
    output_tokens                   BIGINT UNSIGNED DEFAULT NULL COMMENT '实际输出Token数',
    total_tokens                    BIGINT UNSIGNED DEFAULT NULL COMMENT '实际总Token数',
    upstream_cost_points             BIGINT UNSIGNED DEFAULT NULL COMMENT '上游成本，单位积分',
    user_charge_points               BIGINT UNSIGNED DEFAULT NULL COMMENT '用户扣费，单位积分',
    margin_points                    BIGINT          DEFAULT NULL COMMENT '差额，用户扣费减上游成本，单位积分',
    upstream_request_id             VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '上游请求ID',
    status                          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求状态',
    failure_code                    VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '失败业务码或补偿原因',
    created_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_at                    DATETIME        DEFAULT NULL COMMENT '完成时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_gateway_request_request_id (request_id),
    UNIQUE KEY uk_ai_gateway_request_user_idempotency (user_id, idempotency_key),
    KEY idx_ai_gateway_request_user_created (user_id, created_at),
    KEY idx_ai_gateway_request_model_created (public_model, created_at),
    KEY idx_ai_gateway_request_status_created (status, created_at),
    CONSTRAINT chk_ai_gateway_request_endpoint CHECK (endpoint_type IN ('CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES')),
    CONSTRAINT chk_ai_gateway_request_price_band CHECK (price_band IN ('STANDARD', 'PEAK', 'OFF_PEAK')),
    CONSTRAINT chk_ai_gateway_request_status CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED', 'UPSTREAM_FAILED', 'USAGE_MISSING'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关请求及结算快照';

/* 独立用量统计：只读取此表中的快照，不回读当前价格配置。 */
CREATE TABLE ai_usage_record
(
    id                              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用量记录ID',
    request_id                      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '网关请求ID快照',
    user_id                         BIGINT UNSIGNED NOT NULL COMMENT '普通用户ID，不创建外键',
    public_model                    VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户侧模型名快照',
    model_family                    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型族快照',
    upstream_model                  VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游模型名快照',
    upstream_channel_id             BIGINT UNSIGNED NOT NULL COMMENT '上游通道ID快照，不创建外键',
    endpoint_type                   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '协议类型快照',
    price_band                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '价格档位快照',
    upstream_input_cost_points_snapshot    BIGINT UNSIGNED NOT NULL COMMENT '上游输入成本快照，积分/百万Token',
    upstream_output_cost_points_snapshot   BIGINT UNSIGNED NOT NULL COMMENT '上游输出成本快照，积分/百万Token',
    base_input_sale_price_points_snapshot  BIGINT UNSIGNED NOT NULL COMMENT '基础输入售价快照，积分/百万Token',
    base_output_sale_price_points_snapshot BIGINT UNSIGNED NOT NULL COMMENT '基础输出售价快照，积分/百万Token',
    billing_multiplier_ppm_snapshot BIGINT UNSIGNED NOT NULL COMMENT '计费倍率快照，百万分比',
    user_input_price_points_snapshot  BIGINT UNSIGNED NOT NULL COMMENT '最终用户输入售价快照，积分/百万Token',
    user_output_price_points_snapshot BIGINT UNSIGNED NOT NULL COMMENT '最终用户输出售价快照，积分/百万Token',
    input_tokens                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输入Token数',
    output_tokens                   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输出Token数',
    total_tokens                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总Token数',
    upstream_cost_points             BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上游成本，单位积分',
    user_charge_points               BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户扣费，单位积分',
    margin_points                    BIGINT          NOT NULL DEFAULT 0 COMMENT '差额，用户扣费减上游成本，单位积分',
    status                          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用量状态',
    created_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_usage_record_request_id (request_id),
    KEY idx_ai_usage_record_user_created (user_id, created_at),
    KEY idx_ai_usage_record_model_created (public_model, created_at),
    KEY idx_ai_usage_record_channel_created (upstream_channel_id, created_at),
    CONSTRAINT chk_ai_usage_record_endpoint CHECK (endpoint_type IN ('CHAT_COMPLETIONS', 'RESPONSES', 'MESSAGES')),
    CONSTRAINT chk_ai_usage_record_price_band CHECK (price_band IN ('STANDARD', 'PEAK', 'OFF_PEAK')),
    CONSTRAINT chk_ai_usage_record_status CHECK (status IN ('SETTLED', 'USAGE_MISSING'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关 Token 用量和成本统计';

/* 不可变账务流水：积分变化有符号，支持充值、扣费、退款和管理员调整。 */
CREATE TABLE ai_billing_transaction
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '账务流水ID',
    user_id               BIGINT UNSIGNED NOT NULL COMMENT '普通用户ID，不创建外键',
    request_id            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '关联网关请求ID',
    transaction_type      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TOP_UP、CONSUMPTION、REFUND 或 ADJUSTMENT',
    amount_change_points  BIGINT NOT NULL COMMENT '积分变化；扣费为负数',
    balance_after_points  BIGINT UNSIGNED NOT NULL COMMENT '流水完成后的可用积分',
    recharge_amount_fen  BIGINT UNSIGNED DEFAULT NULL COMMENT '充值对应的人民币金额，单位分；非充值流水为空',
    operation_id          VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '充值或调整操作幂等ID',
    operator_user_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '管理员操作人ID，不创建外键',
    description           VARCHAR(255) DEFAULT NULL COMMENT '业务备注，不保存完整请求内容',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_billing_transaction_request_type (request_id, transaction_type),
    UNIQUE KEY uk_ai_billing_transaction_operation (operation_id),
    KEY idx_ai_billing_transaction_user_created (user_id, created_at),
    KEY idx_ai_billing_transaction_type_created (transaction_type, created_at),
    CONSTRAINT chk_ai_billing_transaction_type CHECK (transaction_type IN ('TOP_UP', 'CONSUMPTION', 'REFUND', 'ADJUSTMENT')),
    CONSTRAINT chk_ai_billing_transaction_amount CHECK (amount_change_points <> 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'AI 网关不可变账务流水';

/* 当前价格档位不是新表，先以系统配置保存；确认后可合并到 web_project.sql 的初始配置。 */
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('ai.gateway.current-price-band', 'STANDARD', 'AI 网关当前生效价格档位：STANDARD、PEAK 或 OFF_PEAK');
