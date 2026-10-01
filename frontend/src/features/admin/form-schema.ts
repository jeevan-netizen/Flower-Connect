import { z } from "zod";
import { ADMIN_REASON_MAX_LENGTH } from "@/features/admin/types";

/**
 * Form schemas for the admin area.
 *
 * Both admin reason fields — `VendorAdminReasonRequest.reason` and
 * `UserStatusUpdateRequest.reason` — carry the identical Bean Validation
 * constraints, so they share one schema:
 *
 *   @NotBlank(message = "Reason is required")
 *   @Size(max = 500,   message = "Reason must not exceed 500 characters")
 *
 * The messages are copied verbatim from the DTOs so the client and the server
 * never disagree about what was wrong with the same input.
 *
 * This is a fast convenience only: the backend re-validates and stays
 * authoritative, and a 400 from the server is surfaced through the shared
 * `toApiError` normalisation rather than being trusted to be impossible.
 */
export const adminReasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "Reason is required")
    .max(
      ADMIN_REASON_MAX_LENGTH,
      `Reason must not exceed ${ADMIN_REASON_MAX_LENGTH} characters`,
    ),
});

export type AdminReasonValues = z.infer<typeof adminReasonSchema>;