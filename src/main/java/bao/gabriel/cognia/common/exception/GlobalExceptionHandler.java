package bao.gabriel.cognia.common.exception;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Translates exceptions into the uniform API envelope. Security filters use
 * their own entry point / denied handler because they run outside MVC.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ResultCode code = ex.getResultCode();
        if (code.getHttpStatus().is5xxServerError()) {
            log.error("Business error: {}", ex.getMessage(), ex);
        } else {
            log.debug("Business error [{}]: {}", code.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = ResultCode.BAD_REQUEST.getMessage();
        }
        return badRequest(message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage() == null ? "" : error.getDefaultMessage())
                .filter(m -> !m.isBlank())
                .distinct()
                .collect(Collectors.joining("；"));
        return badRequest(message.isBlank() ? ResultCode.BAD_REQUEST.getMessage() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        return badRequest(message.isBlank() ? ResultCode.BAD_REQUEST.getMessage() : message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return badRequest("请求体或参数格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.debug("Upload rejected because it exceeds the servlet multipart limit: {}", ex.getMessage());
        return badRequest("上传文件大小超出限制，单文件最大 512 MB");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(ResultCode.NOT_FOUND, "接口不存在: /" + ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResponse.error(ResultCode.METHOD_NOT_ALLOWED, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(ResultCode.CONFLICT.getHttpStatus())
                .body(ApiResponse.error(ResultCode.CONFLICT, "数据已存在或违反唯一性约束"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ResultCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ResultCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(ResultCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(ResultCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ResultCode.BAD_REQUEST, message));
    }

    /** The client closed an SSE/async response before the error could be written. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncConnectionClosed(AsyncRequestNotUsableException ex) {
        log.debug("Async response is no longer usable: {}", ex.getMessage());
    }
}
