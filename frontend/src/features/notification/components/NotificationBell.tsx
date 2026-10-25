import { useEffect, useId, useRef, useState } from "react";
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadCount,
} from "@/features/notification/queries";
import { useNotificationStream } from "@/features/notification/hooks/useNotificationStream";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";

const BUTTON_CLASS = `inline-flex items-center gap-2 rounded-sm text-[15px] font-medium text-bolder-text transition-colors duration-micro ease-standard hover:text-bolder-blush ${PRESSABLE} ${FOCUS_RING_DARK}`;

const PANEL_CLASS =
  "absolute right-0 top-full z-50 mt-2 w-80 border border-glass-border rounded-sm bg-bolder-bg shadow-glass";

const BADGE_CLASS = `inline-flex items-center justify-center min-w-[1.5rem] h-[1.5rem] rounded-full bg-bolder-rose px-[6px] text-[10px] font-bold text-bolder-bg`;

const ROW_CLASS = "flex items-center gap-2 px-3 py-2 text-sm";

const UNREAD_DOT = "h-2 w-2 rounded-full bg-bolder-rose";

/**
 * The notification bell in the application header (plan Phase 9, task 9.2).
 *
 * Renders nothing when the visitor is signed out (`enabled` is false), so no
 * notification request is ever made for a session that has no user — the same
 * signed-out discipline as the cart link (D-41).
 *
 * The badge shows the unread count from `GET /unread-count`. Opening the bell
 * fetches the caller's page with `GET /notifications`, and each row offers a
 * per-notification "mark read" plus a "mark all as read" link, both backed by
 * their mutation and a cache invalidation so the badge and list stay truthful.
 * Loading, empty, error and read states are all explicit.
 */
export function NotificationBell({ enabled }: { enabled: boolean }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);

  const { data: unreadCount = 0 } = useUnreadCount({ enabled });
  const {
    data: page,
    isPending: listPending,
    error: listError,
    refetch: refetchList,
  } = useNotifications({ enabled: enabled && open });

  // Keep the badge and list in sync with live SSE events while signed in.
  useNotificationStream({ enabled });

  const markNotificationRead = useMarkNotificationRead();
  const markAllNotificationsRead = useMarkAllNotificationsRead();

  if (!enabled) {
    return null;
  }

  const notifications = page?.content ?? [];
  const unread = notifications.filter((n) => n.readAt === null);
  const hasUnread = unread.length > 0 || unreadCount > 0;

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  const handleToggle = () => {
    setOpen((previous) => {
      if (!previous) {
        // The list loads on demand when the panel opens.
        void refetchList();
      }
      return !previous;
    });
  };

  const handleMarkRead = (notificationId: number) => {
    markNotificationRead.mutate(notificationId);
  };

  const handleMarkAllRead = () => {
    markAllNotificationsRead.mutate();
  };

  const handleRetry = () => {
    void refetchList();
  };

  const buttonLabel = `Notifications (${Number(unreadCount)} unread)`;

  return (
    <>
      <button
        type="button"
        ref={buttonRef}
        aria-label={buttonLabel}
        aria-expanded={open}
        aria-controls={panelId}
        aria-haspopup="true"
        onClick={handleToggle}
        className={BUTTON_CLASS}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.6}
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          focusable="false"
          className="shrink-0"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        <span className="shrink-0">Notifications</span>
        {hasUnread && <span className={BADGE_CLASS}>{Math.max(0, unreadCount)}</span>}
      </button>

      {open && (
        <div id={panelId} className={PANEL_CLASS}>
          <div className="px-3 py-2">
            {listPending ? (
              <p className="text-sm text-bolder-text/70">Loading notifications…</p>
            ) : listError !== null ? (
              <div className="space-y-2">
                <p className="text-sm text-bolder-text/70">
                  Notifications could not be loaded.
                </p>
                <button
                  type="button"
                  onClick={handleRetry}
                  className={`inline-flex rounded-md text-sm font-medium text-bolder-rose underline underline-offset-2 ${PRESSABLE} ${FOCUS_RING_DARK}`}
                >
                  Try again
                </button>
              </div>
            ) : notifications.length === 0 ? (
              <p className="text-sm text-bolder-text/70">No notifications.</p>
            ) : (
              <>
                <ul role="menu" className="space-y-1">
                  {notifications.map((notification) => (
                    <li key={notification.id} className={ROW_CLASS}>
                      {notification.readAt === null && (
                        <span className={UNREAD_DOT} aria-hidden="true" />
                      )}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-bolder-text truncate">
                          {notification.title}
                        </p>
                        <p className="text-xs text-bolder-text/60 truncate">
                          {notification.message}
                        </p>
                      </div>
                      {notification.readAt === null && (
                        <button
                          type="button"
                          onClick={() => handleMarkRead(notification.id)}
                          disabled={markNotificationRead.isPending}
                          className={`text-xs font-medium text-bolder-rose underline underline-offset-2 transition-colors hover:text-bolder-rose/80 disabled:text-bolder-text/30 ${
                            markNotificationRead.isPending ? "cursor-not-allowed" : ""
                          }`}
                        >
                          Mark read
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
                {hasUnread && (
                  <button
                    type="button"
                    onClick={handleMarkAllRead}
                    disabled={markAllNotificationsRead.isPending || listPending}
                    className="mt-3 block w-full rounded-md border border-bolder-border px-3 py-2 text-sm font-medium text-bolder-blush transition-colors hover:bg-bolder-blush/5 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    Mark all as read
                  </button>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </>
  );
}
