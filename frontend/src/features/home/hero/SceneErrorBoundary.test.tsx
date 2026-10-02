import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { SceneErrorBoundary } from "./SceneErrorBoundary";

/**
 * The boundary exists so a failure in the 3D scene — or in the chunk that
 * contains it — degrades the hero instead of the page. The part worth testing is
 * the recovery: a boundary that can never clear turns one transient failure into
 * a permanent one.
 */

function Boom({ shouldThrow = true }: { shouldThrow?: boolean }): ReactNode {
  if (shouldThrow) {
    throw new Error("chunk load failed");
  }
  return <div data-testid="children" />;
}

/** Renders a throwing child without React's expected-error console noise. */
function renderBoom(onError?: (error: Error) => void) {
  const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
  const result = render(
    <SceneErrorBoundary fallback={<p>fallback shown</p>} onError={onError}>
      <Boom />
    </SceneErrorBoundary>,
  );
  return { ...result, consoleError };
}

describe("SceneErrorBoundary", () => {
  it("renders the fallback and reports the error through onError", () => {
    const onError = vi.fn();
    const { consoleError } = renderBoom(onError);

    expect(screen.getByText("fallback shown")).toBeInTheDocument();
    expect(onError).toHaveBeenCalledTimes(1);
    consoleError.mockRestore();
  });

  it("retries the children once a reset key changes", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    const { rerender } = render(
      <SceneErrorBoundary fallback={<p>fallback shown</p>} resetKeys={["first"]}>
        <Boom />
      </SceneErrorBoundary>,
    );
    expect(screen.getByText("fallback shown")).toBeInTheDocument();

    // Same key: still degraded, no second attempt.
    rerender(
      <SceneErrorBoundary fallback={<p>fallback shown</p>} resetKeys={["first"]}>
        <Boom shouldThrow={false} />
      </SceneErrorBoundary>,
    );
    expect(screen.getByText("fallback shown")).toBeInTheDocument();

    // Changed key: the boundary clears and the children get another attempt.
    rerender(
      <SceneErrorBoundary fallback={<p>fallback shown</p>} resetKeys={["second"]}>
        <Boom shouldThrow={false} />
      </SceneErrorBoundary>,
    );

    expect(screen.getByTestId("children")).toBeInTheDocument();
    expect(screen.queryByText("fallback shown")).not.toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("stays degraded without reset keys", () => {
    const { consoleError } = renderBoom();

    expect(screen.getByText("fallback shown")).toBeInTheDocument();
    consoleError.mockRestore();
  });
});