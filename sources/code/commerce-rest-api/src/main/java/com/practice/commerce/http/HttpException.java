package com.practice.commerce.http;

public class HttpException extends Exception {
    private final int code;

    public HttpException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getStatusCode() {
        return code;
    }
}
