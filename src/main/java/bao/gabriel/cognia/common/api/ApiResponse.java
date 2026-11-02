package bao.gabriel.cognia.common.api;

/**
 * Uniform response envelope used by every JSON endpoint.
 *
 * <pre>
 * { "code": 0, "message": "success", "data": { ... }, "timestamp": 1730000000000 }
 * </pre>
 */
public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data,
                System.currentTimeMillis());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(ResultCode resultCode, String message) {
        return new ApiResponse<>(resultCode.getCode(),
                message == null || message.isBlank() ? resultCode.getMessage() : message,
                null, System.currentTimeMillis());
    }

    public static ApiResponse<Void> error(ResultCode resultCode) {
        return error(resultCode, null);
    }
}
