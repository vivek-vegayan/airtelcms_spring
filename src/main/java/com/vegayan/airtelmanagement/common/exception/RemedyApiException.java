package com.vegayan.airtelmanagement.common.exception;

import lombok.Getter;

@Getter
public class RemedyApiException extends RuntimeException  {
    private final Integer errorCode;

    public RemedyApiException(String message, Integer errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
