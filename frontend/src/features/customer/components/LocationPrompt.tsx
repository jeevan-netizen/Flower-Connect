import { useId } from "react";
import { useLocationSelection } from "@/features/location/hooks/useLocationSelection";
import { LocationPicker } from "@/features/location/components/LocationPicker";
import { Card } from "@/features/vendor/components/StatCard";
import { FadeIn } from "@/motion/FadeIn";

interface LocationPromptProps {
  title?: string;
  description?: string;
}

const DEFAULT_TITLE = "Choose your delivery area";
const DEFAULT_DESCRIPTION =
  "We use your area to show only florists who deliver to you. Pick one to continue.";

/**
 * The gate in front of every location-aware catalogue surface.
 *
 * The discovery, search and product-detail endpoints all require a
 * `locationId`: without one the server cannot compute a distance and cannot
 * say whether a florist delivers to the customer. Rather than showing a list
 * that might not be deliverable — and then failing the request — the page
 * shows this prompt and **no location-dependent request is made at all**; the
 * matching query stays disabled until an area is chosen.
 *
 * The picker is the same control the shell's location menu uses, over the same
 * session-scoped state, so there is one place a delivery area is chosen and
 * one copy of the choice.
 */
export function LocationPrompt({
  title = DEFAULT_TITLE,
  description = DEFAULT_DESCRIPTION,
}: LocationPromptProps) {
  const { cities, selected, selectById, isPending, error } = useLocationSelection();
  const headingId = useId();

  return (
    <FadeIn>
      <Card>
        {/*
          The region is named by its heading, not by a second "Delivery location"
          string: the picker below already owns that name, and two elements
          answering to the same accessible name makes the control ambiguous to
          a screen reader.
        */}
        <div role="region" aria-labelledby={headingId}>
          <h2 id={headingId} className="text-lg font-semibold text-slate-900">
            {title}
          </h2>
          <p className="mt-1 text-sm text-slate-600">{description}</p>

          <div className="mt-4 max-w-sm">
            <LocationPicker
              cities={cities}
              value={selected ? String(selected.id) : ""}
              disabled={isPending || error !== null}
              hint={
                error !== null
                  ? "We could not load the delivery areas. Please try again."
                  : isPending
                    ? "Loading delivery areas..."
                    : undefined
              }
              onChange={selectById}
            />
          </div>
        </div>
      </Card>
    </FadeIn>
  );
}
