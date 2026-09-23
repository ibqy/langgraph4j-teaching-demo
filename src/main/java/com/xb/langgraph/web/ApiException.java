package com.xb.langgraph.web;

import org.springframework.http.HttpStatus;

final class ApiException extends RuntimeException {
    private final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    HttpStatus status() { return status; }
}
