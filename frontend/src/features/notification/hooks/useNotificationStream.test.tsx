import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createTestQueryClient, renderWithProviders } from "@/test/render";
import { useNotificationStream } from "./useNotificationStream";
import { createEventSource } from "../sse";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { notificationKeys } from "../queries";

vi.mock("../sse", () => ({
  createEventSource: vi.fn(() => ({ close: vi.fn() })),
}));

const mockCreateEventSource = vi.mocked(createEventSource);
const invalidateQueries = (client: ReturnType<typeof createTestQueryClient>) =>
  vi.spyOn(client, "invalidateQueries");

function setToken(token: string | null) {
  useAuthStore.setState({ accessToken: token });
}

function Harness({ enabled = true }: { enabled?: boolean }) {
  useNotificationStream({ enabled });
  return null;
}

beforeEach(() => {
  mockCreateEventSource.mockClear();
  setToken("test-token");
});

afterEach(() => {
  setToken(null);
});

describe("useNotificationStream", () => {
  it("connects the SSE stream with the bearer token when enabled", () => {
    renderWithProviders(<Harness enabled />);

    expect(mockCreateEventSource).toHaveBeenCalledTimes(1);
    const [options] = mockCreateEventSource.mock.lastCall!;
    expect(options.token).toBe("test-token");
    expect(typeof options.onMessage).toBe("function");
  });

  it("does not connect when disabled", () => {
    renderWithProviders(<Harness enabled={false} />);

    expect(mockCreateEventSource).not.toHaveBeenCalled();
  });

  it("does not connect when signed out", () => {
    setToken(null);
    renderWithProviders(<Harness enabled />);

    expect(mockCreateEventSource).not.toHaveBeenCalled();
  });

  it("reconnects when the token changes", () => {
    const { rerender } = renderWithProviders(<Harness enabled />);
    expect(mockCreateEventSource).toHaveBeenCalledTimes(1);

    setToken("new-token");
    rerender(<Harness enabled />);

    expect(mockCreateEventSource).toHaveBeenCalledTimes(2);
    expect(mockCreateEventSource.mock.lastCall![0].token).toBe("new-token");
  });

  it("invalidates the notification list and unread count on an event", () => {
    const queryClient = createTestQueryClient();
    renderWithProviders(<Harness enabled />, { queryClient });

    const [options] = mockCreateEventSource.mock.lastCall!;
    expect(options.onMessage).toBeDefined();
    const spy = invalidateQueries(queryClient);

    options.onMessage!({ data: "hello" });

    expect(spy).toHaveBeenCalledWith({ queryKey: notificationKeys.all });
    expect(spy).toHaveBeenCalledWith({ queryKey: notificationKeys.unreadCount });
  });

  it("closes the stream on unmount", () => {
    const { unmount } = renderWithProviders(<Harness enabled />);
    const returned = mockCreateEventSource.mock.results[0].value as { close: () => void };
    const close = returned.close;

    unmount();

    expect(close).toHaveBeenCalledTimes(1);
  });

  it("renders nothing", () => {
    const { container } = renderWithProviders(<Harness enabled />);
    expect(container).toBeEmptyDOMElement();
  });
});
