package com.mdau.ukena.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> errors;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.errors = List.of();
    }

    public ApiException(HttpStatus status, String message, List<String> errors) {
        super(message);
        this.status = status;
        this.errors = errors == null ? List.of() : errors;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }
}