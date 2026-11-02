package ink.tuanzi.neomacros.fabric.web.http;

/**
 * A route failure with its HTTP status and machine-readable code, rendered
 * as the unified {@code {"error":{"code","message"}}} body.
 */
public class ApiError extends RuntimeException {

    /** The standard error codes. */
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String GAME_THREAD_TIMEOUT = "game_thread_timeout";
    public static final String TIMEOUT = "timeout";
    public static final String INTERNAL = "internal";

    private final int status;
    private final String code;

    /** Creates the error with the unified body's code and message. */
    public ApiError(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** The HTTP status code. */
    public int status() {
        return status;
    }

    /** The machine-readable code. */
    public String code() {
        return code;
    }

    /** Convenience builders for the standard statuses. */
    public static ApiError unauthorized(String message) {
        return new ApiError(401, UNAUTHORIZED, message);
    }

    public static ApiError forbidden(String message) {
        return new ApiError(403, FORBIDDEN, message);
    }

    public static ApiError notFound(String message) {
        return new ApiError(404, NOT_FOUND, message);
    }

    public static ApiError conflict(String message) {
        return new ApiError(409, CONFLICT, message);
    }

    public static ApiError invalidRequest(String message) {
        return new ApiError(400, INVALID_REQUEST, message);
    }

    public static ApiError gameThreadTimeout() {
        return new ApiError(504, GAME_THREAD_TIMEOUT,
                "the game main thread did not answer within 5 seconds");
    }
}
