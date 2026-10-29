package com.web.project.common.exception;

import com.web.project.common.error.ErrorCode;
import lombok.Getter;

/**
 * 自定义业务异常。
 * 用于处理可预期的业务错误，
 * 例如用户不存在、密码错误、账号已禁用等。
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 当前异常对应的错误码定义。
     */
    private final ErrorCode errorCode;

    /**
     * 使用统一错误码创建业务异常。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用统一错误码，并自定义本次提示。
     *
     * 自定义提示必须是面向客户端的业务消息，不包含内部或敏感信息。
     */
    public BusinessException(
            ErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 转换底层异常，并保留原因链；客户端只接收错误码对应的业务提示。
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义业务提示转换底层异常，并保留原因链。
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
