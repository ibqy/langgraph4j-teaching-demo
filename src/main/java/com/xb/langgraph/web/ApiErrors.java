package com.xb.langgraph.web;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(assignableTypes = DemoController.class)
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException failure) {
        return response(failure.status(), failure.getMessage());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> invalidBody() {
        return response(HttpStatus.BAD_REQUEST, "Invalid JSON request body");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed");
    }
    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status.value(), "error", message));
    }
}
