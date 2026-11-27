package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BStatement.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Request to record (full) payment for a B2B statement. Only the statement's
 * seller may record payment; the statement must be {@code OPEN}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordB2BStatementPaymentRequest {

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    @Size(max = 500, message = "Payment note must not exceed 500 characters")
    private String paymentNote;

    private LocalDateTime paidAt;
}
