package com.web.project.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 项目统一错误码。
 * <p>
 * 每一种业务错误都应该有稳定的业务错误码，
 * 不要让前端依赖中文提示判断错误类型。
 */
@Getter
public enum ErrorCode {

    // 通用请求错误。
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "common.bad_request", "请求参数不正确"),

    // 登录账号或密码不正确。
    INVALID_CREDENTIALS(40101, HttpStatus.UNAUTHORIZED, "auth.invalid_credentials", "账号或密码错误"),

    // 登录请求超过并发或 Redis 窗口限制。
    LOGIN_RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "auth.login_rate_limited", "登录请求过于频繁，请稍后再试"),

    // Token 无效或者已过期
    LOGIN_STATUS_INVALID(40102, HttpStatus.UNAUTHORIZED, "auth.login_status_invalid", "登录状态无效或已过期"),

    // 当前用户没有目标接口的访问权限。
    ACCESS_DENIED(40301, HttpStatus.FORBIDDEN, "auth.access_denied", "没有权限访问该接口"),

    // 系统用户账号被禁用。
    SYS_USER_DISABLED(40302, HttpStatus.FORBIDDEN, "sys_user.disabled", "当前账号已被禁用"),

    // 普通用户账号被禁用。
    USER_DISABLED(40303, HttpStatus.FORBIDDEN, "user.disabled", "当前用户账号已被禁用"),

    // 渠道总开关关闭，该渠道的认证能力不可用。
    AUTH_CHANNEL_DISABLED(40010, HttpStatus.BAD_REQUEST, "auth.channel_disabled", "该登录方式暂不可用"),

    // 验证码功能关闭。
    CODE_FUNCTION_DISABLED(40011, HttpStatus.BAD_REQUEST, "auth.code_function_disabled", "验证码功能暂不可用"),

    // 验证码发送过于频繁。
    CODE_SEND_TOO_FREQUENT(40012, HttpStatus.BAD_REQUEST, "auth.code_send_too_frequent", "验证码发送过于频繁，请稍后再试"),

    // 当日验证码发送次数已达上限。
    CODE_SEND_LIMIT_EXCEEDED(40013, HttpStatus.BAD_REQUEST, "auth.code_send_limit_exceeded", "今日验证码发送次数已达上限"),

    // 验证码错误或已过期。
    VERIFICATION_CODE_INVALID(40014, HttpStatus.BAD_REQUEST, "auth.verification_code_invalid", "验证码无效或已过期"),

    // 目标手机号或邮箱已注册。
    TARGET_ALREADY_REGISTERED(40015, HttpStatus.BAD_REQUEST, "auth.target_already_registered", "该手机号或邮箱已注册"),

    // 目标手机号或邮箱尚未注册。
    TARGET_NOT_REGISTERED(40016, HttpStatus.BAD_REQUEST, "auth.target_not_registered", "该手机号或邮箱尚未注册"),

    // 目标手机号或邮箱已被其他账号绑定。
    TARGET_ALREADY_BOUND(40017, HttpStatus.BAD_REQUEST, "auth.target_already_bound", "该手机号或邮箱已被绑定"),

    // 手机号和邮箱两个渠道总开关不能同时关闭。
    CANNOT_DISABLE_ALL_CHANNELS(40018, HttpStatus.BAD_REQUEST, "auth.cannot_disable_all_channels", "手机号和邮箱不能同时关闭"),

    // Refresh Token 无效、过期或对应会话已失效。
    REFRESH_TOKEN_INVALID(40104, HttpStatus.UNAUTHORIZED, "auth.refresh_token_invalid", "刷新令牌无效或已过期"),

    /**
     * 服务器内部未知异常。
     */
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "common.internal_server_error", "服务器内部异常");

    /**
     * 返回给前端的业务错误码。
     */
    private final int code;

    /**
     * 对应的 HTTP 状态码。
     */
    private final HttpStatus httpStatus;

    /**
     * 多语言消息键。
     */
    private final String messageKey;

    /**
     * 默认中文提示。
     */
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String messageKey, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }
}
