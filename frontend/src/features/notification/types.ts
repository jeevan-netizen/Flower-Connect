/**
 * Notification frontend types — plan Phase 9, task 9.2.
 *
 * Mirrors the backend `NotificationResponse` and the shared `PageResponse`
 * envelope exactly. Money-free: notifications carry only text, an optional
 * deep-link target and read/create timestamps.
 */

import type { PageResponse } from "@/shared/types";

export interface NotificationResponse {
  id: number;
  type: string;
  title: string;
  message: string;
  targetType: string | null;
  targetId: number | null;
  readAt: string | null;
  createdAt: string;
}

/** Alias over the shared envelope for a page of the caller's notifications. */
export type NotificationPageResponse = PageResponse<NotificationResponse>;
