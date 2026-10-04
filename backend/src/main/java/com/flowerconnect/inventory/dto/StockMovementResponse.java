package com.flowerconnect.inventory.dto;

import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for one row of the stock movement log (plan task 3.6).
 *
 * <p>The actor is flattened to {@code actorUserId} and
 * {@code actorEmail} so a history page can render "who did this"
 * without a second request per row. Both are null for a
 * system-initiated movement — the expiry scheduler's {@code WASTE}
 * write-off has no human actor, which is why {@code actor_user_id}
 * is nullable — so a client must treat a null actor as "automatic",
 * not as missing data.
 *
 * <p>{@code quantityDelta} is the signed change as stored, never the
 * resulting level: reconstructing the level would require reading the
 * log from the beginning, whereas the delta is the fact itself.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    private Long id;
    private Long productId;
    private MovementType movementType;
    private Integer quantityDelta;
    private String reason;
    private Long referenceId;
    private String referenceType;
    private Long actorUserId;
    private String actorEmail;
    private LocalDateTime createdAt;
}