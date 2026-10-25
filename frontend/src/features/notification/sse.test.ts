import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createEventSource, parseSSE } from "./sse";

function readerFor(text: string) {
  const bytes = new TextEncoder().encode(text);
  let i = 0;
  return {
    getReader: () => ({
      read: () => {
        if (i >= bytes.length) {
          return Promise.resolve({ done: true, value: null });
        }
        const chunk = bytes.slice(i);
        i = bytes.length;
        return Promise.resolve({ done: false, value: chunk });
      },
      releaseLock: () => {},
    }),
  };
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
});

afterEach(() => {
  fetchMock.mockReset();
});

function tick() {
  return new Promise((resolve) => setTimeout(resolve, 100));
}

describe("parseSSE", () => {
  it("parses data, event, id and retry fields", () => {
    const event = parseSSE("event: notification\ndata: {\"id\":1}\nid: 7\nretry: 3000\n\n");

    expect(event.event).toBe("notification");
    expect(event.data).toBe("{\"id\":1}");
    expect(event.id).toBe("7");
    expect(event.retry).toBe(3000);
  });

  it("joins multi-line data and ignores blank lines", () => {
    const event = parseSSE("data: first line\ndata: second line\n\n\n");

    expect(event.data).toBe("first line\nsecond line");
  });

  it("returns an empty event for an empty block", () => {
    expect(parseSSE("\n\n")).toEqual({});
  });
});

describe("createEventSource", () => {
  it("sends the bearer token and parses events", async () => {
    const onMessage = vi.fn();
    fetchMock.mockResolvedValue({ ok: true, body: readerFor("data: hello\n\ndata: world\n\n") });

    const source = createEventSource({
      url: "http://x/stream",
      token: "tok",
      fetch: fetchMock as unknown as typeof globalThis.fetch,
      onMessage,
    });

    await tick();

    expect(fetchMock).toHaveBeenCalledWith("http://x/stream", {
      headers: { Authorization: "Bearer tok" },
      signal: expect.any(AbortSignal),
    });
    expect(onMessage).toHaveBeenCalledWith(expect.objectContaining({ data: "hello" }));
    expect(onMessage).toHaveBeenCalledWith(expect.objectContaining({ data: "world" }));

    source.close();
  });

  it("calls onError when the response is not OK", async () => {
    const onError = vi.fn();
    fetchMock.mockResolvedValue({ ok: false, status: 500 });

    createEventSource({
      url: "http://x/stream",
      token: "tok",
      fetch: fetchMock as unknown as typeof globalThis.fetch,
      onError,
    });

    await tick();

    expect(onError).toHaveBeenCalledTimes(1);
  });

  it("calls onError when fetch rejects", async () => {
    const onError = vi.fn();
    fetchMock.mockRejectedValue(new TypeError("failed to fetch"));

    createEventSource({
      url: "http://x/stream",
      token: "tok",
      fetch: fetchMock as unknown as typeof globalThis.fetch,
      onError,
    });

    await tick();

    expect(onError).toHaveBeenCalledTimes(1);
  });

  it("close stops further reads and no error fires after close", async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();
    const reader = readerFor("data: one\n\ndata: two\n\n");
    fetchMock.mockResolvedValue({ ok: true, body: reader });

    const source = createEventSource({
      url: "http://x/stream",
      token: "tok",
      fetch: fetchMock as unknown as typeof globalThis.fetch,
      onMessage,
      onError,
    });
    source.close();

    await tick();

    expect(onMessage).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
  });
});
