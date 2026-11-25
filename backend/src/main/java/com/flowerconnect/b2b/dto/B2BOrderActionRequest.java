package com.flowerconnect.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * Request for an order action (accept, reject, cancel, ship, deliver).
 *
 * <p>Accept and reject take an optional reason; ship requires a shipment
 * reference and accepts an optional shipped date note; cancel takes an
 * optional reason. Deliver takes no body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BOrderActionRequest {

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    @Size(max = 255, message = "Shipment reference must not exceed 255 characters")
    private String shipmentReference;
}
