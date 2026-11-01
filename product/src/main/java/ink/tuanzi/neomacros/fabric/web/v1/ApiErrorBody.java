package ink.tuanzi.neomacros.fabric.web.v1;

/** The unified error body: {@code {"error":{"code","message"}}}. */
public record ApiErrorBody(ErrorDetail error) {

    /** The error detail object. */
    public record ErrorDetail(String code, String message) {
    }

    /** Builds the body for an {@link ink.tuanzi.neomacros.fabric.web.http.ApiError}. */
    public static ApiErrorBody of(ink.tuanzi.neomacros.fabric.web.http.ApiError error) {
        return new ApiErrorBody(new ErrorDetail(error.code(), error.getMessage()));
    }
}
