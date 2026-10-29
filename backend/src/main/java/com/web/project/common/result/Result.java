package com.web.project.common.result;

/**
 * 后端统一返回结果类
 *
 * @param code 业务状态码，例如：200成功，500失败
 * @param msg  提示信息，失败时通常返回具体错误原因
 * @param data 返回给前端的数据
 * @param <T>  data 的数据类型
 */
public record Result<T>(
        int code,
        String msg,
        T data
) {

    /**
     * 请求成功，没有返回数据
     */
    public static Result<Void> success() {
        return new Result<>(200, null, null);
    }

    /**
     * 请求成功，并返回数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, null, data);
    }

    /**
     * 请求成功，并返回给前端一条操作提示和数据。
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 请求失败，默认使用业务码 500；HTTP 状态由调用边界设置。
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /**
     * 请求失败，使用指定的业务错误码；HTTP 状态由调用边界设置。
     */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
