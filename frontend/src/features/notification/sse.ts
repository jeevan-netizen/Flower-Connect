/**
 * Fetch-based SSE client (plan Phase 9, task 9.5).
 *
 * Browser `EventSource` cannot send an `Authorization` header, so this client
 * uses `fetch` with a `ReadableStream` body and forwards the existing bearer
 * token. Tokens are never placed in URLs or logged.
 *
 * <p>Delivery is best-effort: the caller is responsible for reconnecting. The
 * returned `close()` aborts the in-flight request and releases the reader.
 *
 * <p>The `fetch` function is injectable so the stream can be unit-tested
 * without a real network or a real `ReadableStream`.
 */

export interface SSEvent {
  event?: string;
  data?: string;
  id?: string;
  retry?: number;
}

export interface SSEOptions {
  url: string;
  token: string;
  onMessage?: (event: SSEvent) => void;
  onError?: (error: Event) => void;
  fetch?: typeof globalThis.fetch;
}

export interface SSESource {
  close: () => void;
}

const LINE_SEPARATOR = "\n\n";

/** Parses one SSE block (fields terminated by `\n`, blocks by `\n\n`). */
export function parseSSE(chunk: string): SSEvent {
  const event: SSEvent = {};
  let data = "";
  for (const line of chunk.split("\n")) {
    if (line.startsWith("data:")) {
      data += line.slice(5).replace(/^ /, "") + "\n";
    } else if (line.startsWith("event:")) {
      event.event = line.slice(6).replace(/^ /, "");
    } else if (line.startsWith("id:")) {
      event.id = line.slice(3).replace(/^ /, "");
    } else if (line.startsWith("retry:")) {
      event.retry = Number(line.slice(6).replace(/^ /, ""));
    }
  }
  if (data) {
    event.data = data.replace(/\n$/, "");
  }
  return event;
}

/**
 * Opens a fetch-based SSE stream to `url`, sending `Authorization: Bearer
 * token`. Calls `onMessage` for every complete event and `onError` on failure.
 * Returns `{ close }` to abort the stream.
 */
export function createEventSource({
  url,
  token,
  onMessage,
  onError,
  fetch: fetchFn = globalThis.fetch,
}: SSEOptions): SSESource {
  let cancelled = false;
  const controller = new AbortController();

  const fail = (error: Event) => {
    if (cancelled) return;
    onError?.(error);
  };

  (async () => {
    try {
      const response = await fetchFn(url, {
        headers: { Authorization: `Bearer ${token}` },
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        fail(new Event("error"));
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!cancelled) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let index;
        while ((index = buffer.indexOf(LINE_SEPARATOR)) !== -1) {
          const block = buffer.slice(0, index);
          buffer = buffer.slice(index + LINE_SEPARATOR.length);
          const parsed = parseSSE(block);
          if (parsed.data !== undefined) {
            onMessage?.(parsed);
          }
        }
      }
    } catch {
      if (cancelled) return;
      fail(new Event("error"));
    }
  })();

  return {
    close: () => {
      cancelled = true;
      controller.abort();
    },
  };
}
