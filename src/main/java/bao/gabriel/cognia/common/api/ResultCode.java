package bao.gabriel.cognia.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business result codes. The numeric value is stable and can be consumed by
 * clients; the HTTP status describes the transport-level outcome.
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success", HttpStatus.OK),

    BAD_REQUEST(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未认证或登录已失效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(40102, "无效的身份凭证", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(40103, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(40104, "账户已被禁用，请联系管理员", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(40105, "账户已被锁定，请稍后重试", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "没有权限执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40900, "资源冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
