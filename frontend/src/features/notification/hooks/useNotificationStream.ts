import { useCallback, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createEventSource } from "../sse";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { notificationKeys } from "../queries";

const NOTIFICATION_STREAM_PATH = "/notifications/stream";

function streamUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
  return base.replace(/\/$/, "") + NOTIFICATION_STREAM_PATH;
}

/**
 * Subscribes the authenticated caller to their own notification SSE stream.
 *
 * The stream refreshes the notification list and unread count promptly when a
 * new event arrives. Polling (see {@code useNewOrderAlert} /
 * {@code useOrderStatusToast}) is retained as a fallback and is unchanged by
 * this hook. Because both mechanisms only invalidate shared TanStack Query
 * keys, a single update cannot produce duplicate user alerts.
 *
 * @param enabled whether the stream should be open (false when signed out)
 */
export function useNotificationStream({ enabled = true }: { enabled?: boolean } = {}) {
  const queryClient = useQueryClient();
  const token = useAuthStore((state) => state.accessToken);
  const sourceRef = useRef<ReturnType<typeof createEventSource> | null>(null);

  const close = useCallback(() => {
    sourceRef.current?.close();
    sourceRef.current = null;
  }, []);

  const handleMessage = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount });
  }, [queryClient]);

  useEffect(() => {
    if (!enabled || !token) {
      close();
      return;
    }

    sourceRef.current = createEventSource({
      url: streamUrl(),
      token,
      onMessage: handleMessage,
    });

    return () => {
      close();
    };
  }, [enabled, token, close, handleMessage]);

  return { close };
}
