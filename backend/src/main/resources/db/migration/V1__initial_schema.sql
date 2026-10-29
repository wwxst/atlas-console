-- Initial schema for new installations.
-- Released migrations are immutable: future changes must use V2, V3, etc.
-- Existing databases created from the legacy web_project.sql must be backed up,
-- schema-checked, then explicitly baselined at version 1 (see docs/database-migrations.md).

CREATE TABLE sys_user
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '系统用户ID',
    username       VARCHAR(20)  NOT NULL COMMENT '登录账号',
    password       VARCHAR(100) NOT NULL COMMENT '加密后的登录密码',
    nickname       VARCHAR(30)  NOT NULL COMMENT '系统用户昵称',
    avatar_file_id  BIGINT       DEFAULT NULL COMMENT '头像文件ID，关联storage_file.id',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    auth_version   BIGINT       NOT NULL DEFAULT 0 COMMENT '认证版本，密码或状态变化时递增',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_avatar_file_id (avatar_file_id)
) COMMENT = '后台系统用户表'
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

INSERT INTO sys_user (username, password, nickname, status)
VALUES ('admin', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '系统管理员', 1);

CREATE TABLE user_account
(
    id               BIGINT UNSIGNED NOT NULL COMMENT '用户ID，由服务端生成 Snowflake ID',
    user_no          VARCHAR(32)     NOT NULL COMMENT '对外展示号码，不参与登录和鉴权',
    phone            VARCHAR(20)              DEFAULT NULL COMMENT '中国大陆手机号',
    email            VARCHAR(100)             DEFAULT NULL COMMENT '邮箱',
    password         VARCHAR(100)             DEFAULT NULL COMMENT 'BCrypt加密密码；验证码注册时可为空',
    password_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '密码版本，修改或重置密码时递增',
    nickname         VARCHAR(50)              DEFAULT NULL COMMENT '用户昵称',
    avatar           VARCHAR(255)             DEFAULT NULL COMMENT '头像地址',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_user_no (user_no),
    UNIQUE KEY uk_user_account_phone (phone),
    UNIQUE KEY uk_user_account_email (email),
    KEY idx_user_account_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '普通用户账号表';

CREATE TABLE sys_config
(
    config_key   VARCHAR(100) NOT NULL COMMENT '配置项键',
    config_value VARCHAR(255) NOT NULL COMMENT '配置项值',
    description  VARCHAR(255)          DEFAULT NULL COMMENT '配置说明',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '系统配置表';

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('auth.phone.enabled', 'true', '手机号注册、登录和绑定是否可用'),
       ('auth.phone.code.enabled', 'true', '手机验证码是否可用'),
       ('auth.email.enabled', 'true', '邮箱注册、登录和绑定是否可用'),
       ('auth.email.code.enabled', 'true', '邮箱验证码是否可用'),
       ('storage.type', 'local', '新上传文件的存储方式：local本地，aliyun阿里云OSS'),
       ('storage.aliyun.access-key-id', '', '阿里云AccessKeyId，空字符串表示未配置'),
       ('storage.aliyun.access-key-secret', '', '阿里云AccessKeySecret加密密文，空字符串表示未配置'),
       ('storage.aliyun.default-space-id', '', '默认OSS空间ID，关联storage_space.id；空字符串表示未选择');

CREATE TABLE storage_space
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储空间ID',
    bucket_name   VARCHAR(63)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已存在的阿里云OSS Bucket名称',
    region_id     VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '阿里云真实Region ID，例如oss-cn-hangzhou',
    access_domain VARCHAR(255) DEFAULT NULL COMMENT '可选的HTTPS访问域名，不含Object Key；未配置时使用OSS默认域名',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_storage_space_bucket_name (bucket_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '阿里云OSS空间绑定表，不代表创建或删除云端Bucket';

CREATE TABLE storage_file
(
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    provider     VARCHAR(10)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际存储方式：local本地，aliyun阿里云OSS',
    space_id     BIGINT        DEFAULT NULL COMMENT 'OSS空间ID，关联storage_space.id；本地文件为空',
    storage_key  VARCHAR(1024) COLLATE utf8mb4_bin NOT NULL COMMENT '本地相对UPLOAD_DIR的路径或OSS Object Key，不含域名；最多1023个UTF-8字节',
    content_type VARCHAR(127)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件MIME类型，例如image/png',
    size_bytes   BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位字节',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_storage_file_space_id (space_id),
    CONSTRAINT chk_storage_file_destination CHECK (
        (provider = 'local' AND space_id IS NULL)
        OR (provider = 'aliyun' AND space_id IS NOT NULL)
    ),
    CONSTRAINT chk_storage_file_key_length CHECK (OCTET_LENGTH(storage_key) BETWEEN 1 AND 1023)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '上传文件表，记录实际存储位置，不受全局存储切换影响';
