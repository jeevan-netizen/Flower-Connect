import type { ServiceLocationGroup } from "@/features/location/types";

interface LocationOptionsProps {
  cities: ServiceLocationGroup[] | undefined;
}

/**
 * The `<option>` rows of a location picker: the "nothing chosen" placeholder,
 * then one `<optgroup>` per city with one option per area.
 *
 * Cities become optgroups and areas become options whose value is the
 * `service_locations.id` — the plan's "city / area / pincode selector" (task
 * 4.2). Extracted because the hero search and the shell's location menu are two
 * renderings of the same choice; a second copy would be a second place for the
 * option list to drift.
 */
export function LocationOptions({ cities }: LocationOptionsProps) {
  return (
    <>
      <option value="">Choose your delivery area...</option>
      {cities?.map((city) => {
        const cityOptions = city.areas.filter(
          (area) => typeof area.id === "number" && area.id > 0,
        );
        if (cityOptions.length === 0) {
          return null;
        }
        return (
          <optgroup key={city.city} label={city.city}>
            {cityOptions.map((area) => (
              <option key={area.id} value={area.id}>
                {area.area} ({area.pincode})
              </option>
            ))}
          </optgroup>
        );
      })}
    </>
  );
}
