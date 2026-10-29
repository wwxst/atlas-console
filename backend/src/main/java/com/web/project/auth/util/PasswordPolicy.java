package com.web.project.auth.util;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;

/**
 * 密码策略。
 *
 * 设置密码要求 8～72 个字符且 UTF-8 编码不超过 72 字节；登录只限制字节上限。
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /**
     * 校验密码是否满足长度约束。
     *
     * @param password 明文密码
     */
    public static void validate(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "密码长度需为8到72个字符，且UTF-8编码不超过72字节"
            );
        }
        validateForLogin(password);
    }

    /**
     * 登录只限制 BCrypt 的字节上限，不套用创建密码时的最小长度要求。
     */
    public static void validateForLogin(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码UTF-8编码不能超过72字节");
        }
    }
}
