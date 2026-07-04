package com.aimv.interfaces.error;

import com.aimv.shared.api.ApiResponse;
import com.aimv.shared.error.ApiError;
import com.aimv.shared.error.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity
            .status(exception.status())
            .body(ApiResponse.fail(new ApiError(exception.code(), exception.getMessage())));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.fail(new ApiError("VALIDATION_ERROR", "请求参数不符合接口合同")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        // 必须打日志：此前直接吞掉异常只回通用文案，线上排障两眼一抹黑。
        log.error("未预期异常，返回 INTERNAL_ERROR", exception);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(new ApiError("INTERNAL_ERROR", "系统处理失败，请稍后重试")));
    }
}
