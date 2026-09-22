package com.flowerconnect.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowerconnect.exception.ErrorCode;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    @JsonProperty("code")
    private ErrorCode errorCode;
    private String message;
    private String path;
    private Map<String, String> validation;
}
