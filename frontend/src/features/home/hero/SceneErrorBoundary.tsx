import { Component, type ErrorInfo, type ReactNode } from "react";

interface SceneErrorBoundaryProps {
  /** Rendered instead of the children once anything below has thrown. */
  fallback: ReactNode;
  children: ReactNode;
  /** Diagnostics only; never affects what is rendered. */
  onError?: (error: Error, info: ErrorInfo) => void;
  /**
   * Changing any of these clears the error and retries the children.
   *
   * Without a reset the boundary is a one-way door: a failure that was merely
   * transient — a chunk fetch that dropped, a driver hiccup — would keep the
   * fallback up for the rest of the component's life. The caller passes
   * something that changes when a retry is actually worth making, such as the
   * router's location key.
   */
  resetKeys?: readonly unknown[];
}

interface SceneErrorBoundaryState {
  hasError: boolean;
}

/**
 * Keeps a failure inside the scene from taking the page down.
 *
 * A `Canvas` can throw while the WebGL context is being created (driver
 * failure, lost context during resize, a context-creation rejection surfacing as
 * a render error). React unmounts the whole tree when a render throws, so
 * without a boundary the `/` route would blank out; with it the boundary swaps
 * in `HeroFallback` and the page keeps its heading, copy and calls to action.
 */
export class SceneErrorBoundary extends Component<
  SceneErrorBoundaryProps,
  SceneErrorBoundaryState
> {
  state: SceneErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): SceneErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    this.props.onError?.(error, info);
  }

  componentDidUpdate(prevProps: SceneErrorBoundaryProps): void {
    const previous = prevProps.resetKeys;
    const next = this.props.resetKeys;
    if (!this.state.hasError || !next || !previous || next.length !== previous.length) {
      return;
    }
    const changed = next.some((key, index) => !Object.is(key, previous[index]));
    if (changed) {
      this.setState({ hasError: false });
    }
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}