package com.web.project.auth.util;

import com.web.project.auth.model.TargetType;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 联系方式规范化工具。
 *
 * 所有查询、写入和 Redis key 都使用规范化后的值：
 * 手机号去除首尾空格并校验 11 位中国大陆手机号；
 * 邮箱去除首尾空格并转为小写。
 */
public final class ContactNormalizer {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ContactNormalizer() {
    }

    /**
     * 规范化并校验联系方式。
     *
     * @param targetType 联系方式类型
     * @param raw        原始输入
     * @return 规范化后的联系方式
     */
    public static String normalize(TargetType targetType, String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String value = raw.trim();

        if (targetType == TargetType.PHONE) {
            if (!PHONE_PATTERN.matcher(value).matches()) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "手机号格式不正确"
                );
            }
            return value;
        }

        value = value.toLowerCase(Locale.ROOT);

        if (value.length() > 100 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "邮箱格式不正确"
            );
        }

        return value;
    }
}
