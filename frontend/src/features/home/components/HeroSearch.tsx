import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import {
  isLocationListEmpty,
  useLocationSelection,
} from "@/features/location/hooks/useLocationSelection";
import { LocationOptions } from "@/features/location/components/LocationOptions";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";

/** The existing browse route. No new route and no new API is introduced here. */
const BROWSE_PATH = "/browse";

/**
 * The hero's search pill, now backed by the seeded service areas (plan task
 * 4.2).
 *
 * This replaces the free-text area field. The visitor now chooses a delivery
 * area from `GET /api/v1/locations` instead of typing one, and the choice is
 * held for the session by the location store (`sessionStorage`, D-34) — so it
 * is available to every page of the tab and to the discovery and search work of
 * tasks 4.3 and 4.4. No browser Geolocation API and no GPS is involved (D-4):
 * the coordinates are the service area's centroid, resolved by the backend.
 *
 * Submitting still navigates to the existing `/browse` route. The selection is
 * not carried in the URL: the session store is the single source, and a
 * duplicate `?locationId=` parameter would be a second copy of the same state
 * that can disagree with it. `/browse` sits behind `RequireAuth`, so an
 * anonymous visitor is sent to the login page by the existing guard — that is
 * the route's behaviour, not this component's.
 *
 * The three data states (loading, failed, empty) render as one muted line under
 * the pill and leave the rest of the hero — heading, copy, calls to action —
 * untouched, so a location-list problem never blocks browsing.
 *
 * The pill shows the selection because the store changed, never because a save
 * was announced: there is no "location saved" message anywhere, so the UI
 * cannot claim a success that did not happen.
 */
export function HeroSearch() {
  const navigate = useNavigate();
  const selection = useLocationSelection();
  const { selected, cities, isPending, error, refetch, selectById } = selection;

  const empty = isLocationListEmpty(selection);
  const unavailable = isPending || error !== null || empty;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    navigate(BROWSE_PATH);
  };

  const statusLine = isPending
    ? "Loading delivery areas..."
    : error !== null
      ? "Could not load delivery areas."
      : empty
        ? "No delivery areas are available yet."
        : null;

  return (
    <div className="w-full max-w-[510px]">
      <form
        role="search"
        onSubmit={handleSubmit}
        className="flex w-full flex-col gap-2 rounded-pill bg-white/95 p-2 pl-5 shadow-search transition-shadow duration-micro ease-standard focus-within:ring-2 focus-within:ring-bolder-rose sm:flex-row sm:items-center sm:gap-3 sm:pl-6"
      >
        <label htmlFor="home-location" className="sr-only">
          Delivery location
        </label>
        <select
          id="home-location"
          value={selected ? String(selected.id) : ""}
          disabled={unavailable}
          onChange={(event) => selectById(event.target.value)}
          className="w-full flex-1 rounded-pill bg-transparent px-1 py-3 text-[15px] text-bolder-plum outline-none disabled:cursor-not-allowed disabled:opacity-60"
        >
          <LocationOptions cities={cities} />
        </select>
        <button
          type="submit"
          className={`w-full shrink-0 rounded-pill bg-bolder-rose-action px-6 py-3 text-[15px] font-semibold text-white shadow-button transition-[background-color,transform] duration-micro ease-standard hover:-translate-y-0.5 hover:bg-bolder-rose-deep motion-reduce:hover:translate-y-0 sm:w-auto ${PRESSABLE} ${FOCUS_RING_DARK}`}
        >
          Explore Flowers
        </button>
      </form>

      {statusLine && (
        <p className="mt-2 text-[14px] text-bolder-muted" aria-live="polite">
          {statusLine}
        </p>
      )}
      {error !== null && (
        <button
          type="button"
          onClick={refetch}
          className={`mt-1 inline-flex rounded-sm text-[14px] font-medium text-bolder-blush underline underline-offset-2 ${PRESSABLE} ${FOCUS_RING_DARK}`}
        >
          Try again
        </button>
      )}
    </div>
  );
}
