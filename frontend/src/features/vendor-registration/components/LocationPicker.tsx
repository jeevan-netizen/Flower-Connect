import { useId } from "react";
import type { ServiceLocationGroup, ServiceLocationOption } from "@/features/vendor-registration/types";
import { FieldMessage } from "@/motion/FieldMessage";
import { FIELD_TRANSITION } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;
const ERROR_BORDER = "border-red-500";

interface LocationPickerProps {
  /** City-grouped areas exactly as `GET /api/v1/locations` returns them. */
  cities: ServiceLocationGroup[] | undefined;
  /** Flattened option rows; already filtered to areas with a usable id. */
  options: ServiceLocationOption[];
  value: string;
  error?: string;
  /** Shown when there is no error. Defaults to the steady-state explanation. */
  hint?: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}

const DEFAULT_HINT = "Orders are only offered to customers inside the area you pick.";

/**
 * The service-area picker required by plan task 2.5 ("the vendor selects city /
 * area / pincode from `service_locations`") and needed by task 1.7's registration
 * form.
 *
 * Cities become `<optgroup>`s and areas become `<option>`s whose value is the
 * `service_locations.id`. That id is the only thing the form submits: the backend
 * copies the centroid coordinates from it and enforces the foreign key, so the
 * picker never derives coordinates in the browser and never invents an id.
 *
 * The field stays rendered — disabled — while the areas are loading, after a
 * failed fetch, or for an empty region, so the vendor can see that registration
 * needs a service area at all. Hiding it instead would leave the field's own
 * required-value error with no input to attach to, and so silently unreachable.
 */
export function LocationPicker({
  cities,
  options,
  value,
  error,
  hint = DEFAULT_HINT,
  disabled = false,
  onChange,
}: LocationPickerProps) {
  const id = useId();
  // The hint has a steady-state default, so this field always has something to
  // describe: the error replaces the hint in the same element rather than in a
  // second node the description would then have to point at instead.
  const describedBy = id;

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        Service area
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
        className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""}`}
      >
        <option value="">Select your service area...</option>
        {cities?.map((city) => {
          const cityOptions = options.filter((option) => option.city === city.city);
          if (cityOptions.length === 0) {
            return null;
          }
          return (
            <optgroup key={city.city} label={city.city}>
              {cityOptions.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </optgroup>
          );
        })}
      </select>
      <FieldMessage id={id} hint={hint} message={error} reserve />
    </div>
  );
}