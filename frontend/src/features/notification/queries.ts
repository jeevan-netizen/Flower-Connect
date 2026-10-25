import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "./api";
import type { NotificationPageResponse } from "./types";

/**
 * Query keys for the caller's notifications (plan Phase 9, task 9.2).
 *
 * Notifications are resolved from the JWT subject — there is one inbox per
 * signed-in account, so one key family serves every notification screen.
 *
 * Mutations invalidate the whole key family rather than patching rows: a
 * mark-read can change the unread count and the list ordering, so the data
 * has to be re-read to stay truthful.
 */
export const notificationKeys = {
  all: ["notification"] as const,
  list: (page: number, size: number) =>
    ["notification", "list", page, size] as const,
  unreadCount: ["notification", "unread-count"] as const,
};

/**
 * `GET /api/v1/notifications` — the caller's own page.
 *
 * The endpoint requires authentication, so callers pass `enabled` to avoid a
 * guaranteed-to-fail 401 on anonymous visits (the same cart `enabled`
 * discipline, D-41). `useNotifications` itself defaults to `enabled: true`;
 * the shell gates it on `isAuthenticated`.
 */
export function useNotifications({
  page = 0,
  size = 20,
  enabled = true,
}: { page?: number; size?: number; enabled?: boolean } = {}) {
  return useQuery<NotificationPageResponse>({
    queryKey: notificationKeys.list(page, size),
    queryFn: () => fetchNotifications(page, size),
    enabled,
  });
}

/** `GET /api/v1/notifications/unread-count` — the caller's unread count. */
export function useUnreadCount({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery<number>({
    queryKey: notificationKeys.unreadCount,
    queryFn: fetchUnreadCount,
    enabled,
  });
}

/** `PATCH /api/v1/notifications/{id}/read`. On success the inbox is re-read. */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: number) => markNotificationRead(notificationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

/** `POST /api/v1/notifications/read-all`. On success the inbox is re-read. */
export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

/** Drops every cached notification query. Called on logout. */
export function clearNotificationCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: notificationKeys.all });
}
