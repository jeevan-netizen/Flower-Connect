import api from "@/shared/lib/api";
import type { NotificationPageResponse, NotificationResponse } from "./types";

/** The notification namespace under `/api/v1`. */
export const NOTIFICATION_PATH = "/notifications";

/** `GET /api/v1/notifications` — the caller's own notifications, newest first. */
export async function fetchNotifications(page = 0, size = 20): Promise<NotificationPageResponse> {
  const response = await api.get<NotificationPageResponse>(
    `${NOTIFICATION_PATH}?page=${page}&size=${size}`,
  );
  return response.data;
}

/** `GET /api/v1/notifications/unread-count` — the caller's unread count. */
export async function fetchUnreadCount(): Promise<number> {
  const response = await api.get<number>(`${NOTIFICATION_PATH}/unread-count`);
  return response.data;
}

/** `PATCH /api/v1/notifications/{id}/read` — mark one notification read. */
export async function markNotificationRead(
  notificationId: number,
): Promise<NotificationResponse> {
  const response = await api.patch<NotificationResponse>(
    `${NOTIFICATION_PATH}/${notificationId}/read`,
  );
  return response.data;
}

/** `POST /api/v1/notifications/read-all` — mark all notifications read. */
export async function markAllNotificationsRead(): Promise<number> {
  const response = await api.post<number>(`${NOTIFICATION_PATH}/read-all`);
  return response.data;
}
