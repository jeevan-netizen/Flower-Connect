package com.flowerconnect.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;

/**
 * Request body for {@code POST /api/v1/admin/refunds/{orderId}/mark}.
 * Requires a non-blank, length-limited reference note.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkRefundRequest implements Serializable {

    @NotBlank(message = "Reference note is required")
    @Size(max = 500, message = "Reference note must be 500 characters or fewer")
    private String referenceNote;
}
