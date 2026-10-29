package com.web.project.common.exception;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 *
 * 将业务与框架异常转换为 Result，保留 HTTP 语义并隔离内部诊断信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求体参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = getValidationMessage(exception.getBindingResult());
        Result<Void> result = Result.error(ErrorCode.BAD_REQUEST.getCode(), message);

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus()).body(result);
    }

    /**
     * 处理查询参数绑定与校验异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException exception) {
        String message = getValidationMessage(exception.getBindingResult());
        Result<Void> result = Result.error(ErrorCode.BAD_REQUEST.getCode(), message);

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus()).body(result);
    }

    /**
     * 处理请求体无法读取的异常。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleInvalidRequest(
            Exception exception
    ) {
        Result<Void> result = Result.error(
                ErrorCode.BAD_REQUEST.getCode(),
                ErrorCode.BAD_REQUEST.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus()).body(result);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleInvalidArgumentType(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(Result.error(
                ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getDefaultMessage()));
    }

    /**
     * 处理自定义业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            logSystemException(exception);
        }
        Result<Void> result = Result.error(errorCode.getCode(), exception.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus()).body(result);
    }

    /**
     * 处理没有被单独处理的系统异常。
     *
     * 服务器日志仅记录异常类型和调用栈，避免异常消息泄露敏感数据；
     * 不向前端暴露数据库、代码路径等内部信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        // 保留 MVC 客户端错误的 HTTP 语义，包括 405 的 Allow 响应头。
        if (exception instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(Result.error(ErrorCode.BAD_REQUEST.getCode(),
                            ErrorCode.BAD_REQUEST.getDefaultMessage()));
        }

        logSystemException(exception);

        Result<Void> result = Result.error(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(result);
    }

    private void logSystemException(Exception exception) {
        // 保留原因链的诊断信息，底层异常消息可能包含 SQL 参数或凭据，因此不写入日志。
        log.error("服务器内部异常，类型和调用栈：\n{}", ExceptionDiagnostics.withoutMessages(exception));
    }

    /**
     * 获取参数校验产生的第一条错误信息。
     */
    private String getValidationMessage(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();

        if (fieldError == null || fieldError.getDefaultMessage() == null) {
            return ErrorCode.BAD_REQUEST.getDefaultMessage();
        }

        return fieldError.getDefaultMessage();
    }
}
