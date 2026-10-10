import { describe, it, expect } from "vitest";
import { makeServiceLocations } from "@/test/factories";
import {
  findSelectedLocation,
  formatSelectedLocation,
  hasNoServiceLocations,
  isSelectedLocation,
  toServiceLocationOptions,
  type ServiceLocationGroup,
} from "@/features/location/types";

describe("toServiceLocationOptions", () => {
  it("flattens the city-grouped response into id/label rows", () => {
    const options = toServiceLocationOptions(makeServiceLocations());

    expect(options).toEqual([
      { id: "3", city: "Bengaluru", label: "Indiranagar (560038)" },
      { id: "4", city: "Bengaluru", label: "Koramangala (560034)" },
    ]);
  });

  it("keeps the id from the response instead of deriving one from the label", () => {
    const [option] = toServiceLocationOptions(makeServiceLocations());

    expect(option?.id).toBe("3");
  });

  it("returns an empty list when the query has not resolved", () => {
    expect(toServiceLocationOptions(undefined)).toEqual([]);
  });

  it("drops an area with no usable id, because it could not be submitted", () => {
    const options = toServiceLocationOptions(
      makeServiceLocations({
        areas: [
          { id: 3, area: "Indiranagar", pincode: "560038", latitude: 12.97, longitude: 77.64 },
          { id: 0, area: "Broken Row", pincode: "000000", latitude: 0, longitude: 0 },
        ],
      }),
    );

    expect(options).toHaveLength(1);
    expect(options[0]?.label).toBe("Indiranagar (560038)");
  });

  it("reports the empty case rather than offering an unusable picker", () => {
    expect(hasNoServiceLocations(toServiceLocationOptions([]))).toBe(true);
    expect(hasNoServiceLocations(toServiceLocationOptions(makeServiceLocations()))).toBe(false);
  });
});

describe("findSelectedLocation", () => {
  it("resolves a row by id with the city that groups it", () => {
    const location = findSelectedLocation(makeServiceLocations(), 4);

    expect(location).toEqual({
      id: 4,
      city: "Bengaluru",
      area: "Koramangala",
      pincode: "560034",
      latitude: 12.9352,
      longitude: 77.6245,
    });
  });

  it("returns null for an id the region does not have, so no location is invented", () => {
    expect(findSelectedLocation(makeServiceLocations(), 999)).toBeNull();
    expect(findSelectedLocation(undefined, 3)).toBeNull();
  });
});

describe("formatSelectedLocation", () => {
  it("renders area and pincode, which is what the shell shows", () => {
    expect(
      formatSelectedLocation({
        id: 3,
        city: "Bengaluru",
        area: "Indiranagar",
        pincode: "560038",
        latitude: 12.97,
        longitude: 77.64,
      }),
    ).toBe("Indiranagar, 560038");
  });
});

describe("isSelectedLocation", () => {
  const valid = {
    id: 3,
    city: "Bengaluru",
    area: "Indiranagar",
    pincode: "560038",
    latitude: 12.97,
    longitude: 77.64,
  };

  it("accepts a complete row", () => {
    expect(isSelectedLocation(valid)).toBe(true);
  });

  it("rejects every shape a persisted value could take that is not a location", () => {
    const invalid: unknown[] = [
      null,
      undefined,
      "Indiranagar",
      3,
      [],
      {},
      { ...valid, id: "3" },
      { ...valid, id: 0 },
      { ...valid, id: 3.5 },
      { ...valid, id: undefined },
      { ...valid, city: undefined },
      { ...valid, pincode: 560038 },
      { ...valid, latitude: "12.97" },
      { ...valid, longitude: null },
    ];

    for (const value of invalid) {
      expect(isSelectedLocation(value)).toBe(false);
    }
  });

  it("rejects a row whose id is missing, the obsolete-schema case", () => {
    const { id, ...obsolete } = valid;
    expect(isSelectedLocation(obsolete)).toBe(false);
  });
});

/** Keeps the fixture's shape honest against the moved type. */
describe("ServiceLocationGroup fixture", () => {
  it("is the hierarchical shape the picker consumes", () => {
    const groups: ServiceLocationGroup[] = makeServiceLocations();

    expect(groups[0]?.city).toBe("Bengaluru");
    expect(groups[0]?.areas[0]?.area).toBe("Indiranagar");
  });
});
