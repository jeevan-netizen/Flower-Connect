import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NotificationBell } from "@/features/notification/components/NotificationBell";
import { renderWithProviders } from "@/test/render";
import type { NotificationPageResponse, NotificationResponse } from "@/features/notification/types";

const mockFetchUnreadCount = vi.hoisted(() => vi.fn());
const mockFetchNotifications = vi.hoisted(() => vi.fn());
const mockMarkNotificationRead = vi.hoisted(() => vi.fn());
const mockMarkAllNotificationsRead = vi.hoisted(() => vi.fn());

vi.mock("@/features/notification/api", () => ({
  fetchUnreadCount: mockFetchUnreadCount,
  fetchNotifications: mockFetchNotifications,
  markNotificationRead: mockMarkNotificationRead,
  markAllNotificationsRead: mockMarkAllNotificationsRead,
}));

function makeNotification(overrides: Partial<NotificationResponse> = {}): NotificationResponse {
  return {
    id: 1,
    type: "order_placed",
    title: "Order placed",
    message: "Your order #1 is confirmed.",
    targetType: "ORDER",
    targetId: 1,
    readAt: null,
    createdAt: "2026-10-25T09:00:00",
    ...overrides,
  };
}

function makePage(content: NotificationResponse[] = []): NotificationPageResponse {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

function renderBell(enabled = true) {
  return renderWithProviders(<NotificationBell enabled={enabled} />);
}

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchUnreadCount.mockResolvedValue(0);
    mockFetchNotifications.mockResolvedValue(makePage());
    mockMarkNotificationRead.mockResolvedValue(makeNotification());
    mockMarkAllNotificationsRead.mockResolvedValue(0);
  });

  it("renders nothing for a signed-out visitor", () => {
    renderBell(false);

    expect(
      screen.queryByRole("button", { name: /notifications/i }),
    ).not.toBeInTheDocument();
    expect(mockFetchUnreadCount).not.toHaveBeenCalled();
    expect(mockFetchNotifications).not.toHaveBeenCalled();
  });

  it("shows a bell without a badge when nothing is unread", async () => {
    mockFetchUnreadCount.mockResolvedValue(0);
    renderBell();

    const bell = screen.getByRole("button", { name: /notifications/i });
    expect(bell).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("0")).not.toBeInTheDocument();

    await userEvent.click(bell);
    await waitFor(() => {
      expect(screen.getByText("No notifications.")).toBeInTheDocument();
    });
    expect(mockFetchNotifications).toHaveBeenCalledTimes(1);
  });

  it("shows the unread badge and lists unread notifications", async () => {
    mockFetchUnreadCount.mockResolvedValue(1);
    mockFetchNotifications.mockResolvedValue(
      makePage([makeNotification({ id: 7, readAt: null })]),
    );

    renderBell();
    await waitFor(() => {
      expect(screen.getByText("1")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /notifications/i }));
    await waitFor(() => {
      expect(screen.getByText("Order placed")).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: "Mark read" })).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Mark all as read" })).toBeInTheDocument();
  });

  it("marks one notification read and refreshes the inbox", async () => {
    mockFetchUnreadCount.mockResolvedValue(1);
    mockFetchNotifications.mockResolvedValue(makePage([makeNotification({ id: 7 })]));
    mockMarkNotificationRead.mockResolvedValue(
      makeNotification({ id: 7, readAt: "2026-10-25T10:00:00" }),
    );

    renderBell();
    await waitFor(() => {
      expect(screen.getByText("1")).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /notifications/i }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Mark read" })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: "Mark read" }));
    await waitFor(() => {
      expect(mockMarkNotificationRead).toHaveBeenCalledWith(7);
    });
    // The mutation invalidates the queries, so the list is re-read.
    expect(mockFetchNotifications).toHaveBeenCalled();
  });

  it("marks all notifications as read", async () => {
    mockFetchUnreadCount.mockResolvedValue(2);
    mockFetchNotifications.mockResolvedValue(
      makePage([makeNotification({ id: 7 }), makeNotification({ id: 8 })]),
    );

    renderBell();
    await waitFor(() => {
      expect(screen.getByText("2")).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /notifications/i }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Mark all as read" })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: "Mark all as read" }));
    await waitFor(() => {
      expect(mockMarkAllNotificationsRead).toHaveBeenCalledTimes(1);
    });
  });

  it("shows an error state with a retry when the list fails to load", async () => {
    mockFetchUnreadCount.mockResolvedValue(0);
    mockFetchNotifications.mockRejectedValue(new Error("down"));

    renderBell();
    await userEvent.click(screen.getByRole("button", { name: /notifications/i }));
    await waitFor(() => {
      expect(screen.getByText(/notifications could not be loaded/i)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));
    await waitFor(() => {
      expect(mockFetchNotifications).toHaveBeenCalledTimes(2);
    });
  });

  it("shows a loading state while the list is fetched", async () => {
    let resolveFetch!: (value: NotificationPageResponse) => void;
    mockFetchNotifications.mockImplementation(
      () => new Promise((resolve) => (resolveFetch = resolve)),
    );

    renderBell();
    await userEvent.click(screen.getByRole("button", { name: /notifications/i }));
    expect(screen.getByText(/loading notifications/i)).toBeInTheDocument();

    resolveFetch(makePage());
    await waitFor(() => {
      expect(screen.queryByText(/loading notifications/i)).not.toBeInTheDocument();
    });
  });
});
