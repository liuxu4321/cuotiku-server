package com.yingying.cuotiku.server.web;

public class ApiException extends RuntimeException {

    private final int code;
    private final Object data;

    public ApiException(int code, String message) {
        this(code, message, null);
    }

    public ApiException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    public static ApiException sessionKicked(String message) {
        return new ApiException(4011, message);
    }
}
