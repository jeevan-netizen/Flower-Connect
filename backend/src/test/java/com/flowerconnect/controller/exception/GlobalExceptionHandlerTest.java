package com.flowerconnect.controller.exception;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.security.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Coverage for {@link GlobalExceptionHandler}: unexpected exceptions return a
 * generic structured error without exposing stack traces, SQL, file paths, or
 * internal exception details to the client.
 */
class GlobalExceptionHandlerTest {

    @Test
    void unexpectedExceptionReturnsGenericErrorWithoutInternalDetails() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GlobalExceptionHandler handler = new GlobalExceptionHandler(objectMapper,
                java.time.Clock.systemUTC());
        WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        // Must not throw and must return a generic 500
        var response = assertDoesNotThrow(() -> handler.handleGeneral(
                new IllegalStateException("internal detail: java.sql.SQLException: connection refused at host db.internal"),
                request));

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Internal Server Error", body.getError());
        assertEquals("An unexpected error occurred", body.getMessage());
        assertEquals(null, body.getErrorCode(), "errorCode must be null for unexpected errors");

        // No internal details leak into the serialized body
        String serialized = objectMapper.writeValueAsString(body).toLowerCase();
        assertFalse(serialized.contains("sql"), "SQL must not appear: " + serialized);
        assertFalse(serialized.contains("illegalstateexception"), "Exception class must not appear: " + serialized);
        assertFalse(serialized.contains("connection refused"), "Exception message must not appear: " + serialized);
        assertFalse(serialized.contains("db.internal"), "Host name must not appear: " + serialized);
    }

    @Test
    void businessExceptionStillReturnsItsOwnCodeAndMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GlobalExceptionHandler handler = new GlobalExceptionHandler(objectMapper,
                java.time.Clock.systemUTC());
        WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        var response = handler.handleBusinessException(
                new BusinessException(ErrorCode.CONFLICT, "expected business conflict"));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(ErrorCode.CONFLICT, body.getErrorCode());
        assertEquals("expected business conflict", body.getMessage());
    }
}
