import { useId } from "react";
import type { ServiceLocationGroup } from "@/features/location/types";
import { LocationOptions } from "@/features/location/components/LocationOptions";
import { FieldMessage } from "@/motion/FieldMessage";
import { FIELD_TRANSITION } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 bg-white shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;
const ERROR_BORDER = "border-red-500";

interface LocationPickerProps {
  /** City-grouped areas exactly as `GET /api/v1/locations` returns them. */
  cities: ServiceLocationGroup[] | undefined;
  /** The chosen area id as a string, or "" for nothing chosen. */
  value: string;
  error?: string;
  /** Shown when there is no error. Defaults to the steady-state explanation. */
  hint?: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}

const DEFAULT_HINT = "We use this area to find florists who deliver to you.";

/**
 * The delivery-area selector (plan task 4.2), backed by `GET /api/v1/locations`.
 *
 * The selected value is the `service_locations.id`, never coordinates and never
 * free text: the backend resolves the centroid from the id (D-4), so the picker
 * never derives a position in the browser and there is no Geolocation API or
 * GPS anywhere in this flow. The choice is remembered for the session in
 * `sessionStorage` by the location store (D-34).
 *
 * The field stays rendered — disabled — while the areas are loading, after a
 * failed fetch, or for an empty region, so the visitor can see that a delivery
 * area is expected at all. Hiding it instead would leave the label with no
 * control to attach to. The caller decides *why* it is unavailable through
 * `hint` and `disabled`; this component renders one control and one description
 * line, so the two surfaces that use it cannot disagree about the states.
 */
export function LocationPicker({
  cities,
  value,
  error,
  hint = DEFAULT_HINT,
  disabled = false,
  onChange,
}: LocationPickerProps) {
  // Two ids, not one: the description element and the control must not share
  // an id, or `aria-describedby` resolves to the control itself and announces
  // nothing. (The vendor-registration picker shares one id for both; this
  // component gets it right from the start.)
  const controlId = useId();
  const messageId = useId();

  return (
    <div>
      <label htmlFor={controlId} className="block text-sm font-medium text-slate-700">
        Delivery location
      </label>
      <select
        id={controlId}
        value={value}
        disabled={disabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={messageId}
        onChange={(event) => onChange(event.target.value)}
        className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""}`}
      >
        <LocationOptions cities={cities} />
      </select>
      <FieldMessage id={messageId} hint={hint} message={error} reserve />
    </div>
  );
}
