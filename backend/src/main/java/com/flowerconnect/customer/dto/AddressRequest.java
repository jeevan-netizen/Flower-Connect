package com.flowerconnect.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for address create and update (plan task 4.1).
 *
 * <p>The server-derived fields are <em>not</em> accepted from the client:
 * {@code latitude} and {@code longitude} are copied from the selected
 * service location's centroid on every write (D-4), and {@code id},
 * default-flag bookkeeping and the timestamps belong to the server.
 * The coordinate fields are absent from this DTO rather than
 * optional-with-a-null sentinel, which would let a client send arbitrary
 * coordinates; unknown JSON properties are ignored by the mapper, so a
 * client that sends them anyway has them silently dropped.
 *
 * <p>{@code defaultAddress} is optional and means different things on the
 * two write paths:
 * <ul>
 *   <li>on create, the first address becomes the default automatically —
 *       an explicit {@code false} is not honoured for a first address,
 *       because a customer must always be able to receive at a default
 *       address once they have one;</li>
 *   <li>on update, an omitted value keeps the stored flag (the
 *       {@code CategoryRequest.active} precedent), so editing the street
 *       name cannot accidentally change which address is the default; an
 *       explicit value is applied as a full-replacement field (D-15).</li>
 * </ul>
 *
 * <p>The property is named {@code defaultAddress} — the entity's and the
 * response's name — so the request and response share one vocabulary on
 * the wire. A primitive {@code boolean isDefault} would serialize as
 * {@code default} (Jackson strips the "is" prefix from Lombok's getter);
 * see D-33.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 128, message = "Label must be at most 128 characters")
    private String label;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must be at most 255 characters")
    private String line1;

    @Size(max = 255, message = "Address line 2 must be at most 255 characters")
    private String line2;

    @NotNull(message = "Service location is required")
    private Long serviceLocationId;

    private Boolean defaultAddress;
}
