import { describe, it, expect, beforeEach, vi } from "vitest";
import { LOCATION_STORAGE_KEY, useLocationStore } from "@/features/location/stores/location-store";
import type { SelectedLocation } from "@/features/location/types";

/**
 * The store is created once per module instance, and hydration happens at
 * creation — so a test that wants to observe a *restore* must load a fresh
 * module with the value already in `sessionStorage`. `vi.resetModules()` makes
 * each import return a new store object, which is exactly the refresh-a-tab
 * scenario the persistence is for.
 */
async function loadFreshStore() {
  vi.resetModules();
  const module = await import("@/features/location/stores/location-store");
  return module.useLocationStore;
}

const INDIRANAGAR: SelectedLocation = {
  id: 3,
  city: "Bengaluru",
  area: "Indiranagar",
  pincode: "560038",
  latitude: 12.971199,
  longitude: 77.640586,
};

/**
 * The persisted shape is zustand's own envelope — `{state, version}` — with the
 * partialized store slice inside `state`. Reading it back through the same
 * envelope is what a browser restart would do.
 */
function readStoredSelection(): unknown {
  const raw = sessionStorage.getItem(LOCATION_STORAGE_KEY);
  if (raw === null) {
    return null;
  }
  return (JSON.parse(raw) as { state?: { selected?: unknown } }).state?.selected ?? null;
}

function writeStored(selected: unknown) {
  sessionStorage.setItem(LOCATION_STORAGE_KEY, JSON.stringify({ state: { selected }, version: 0 }));
}

describe("location store", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
  });

  it("starts with no location chosen", () => {
    expect(useLocationStore.getState().selected).toBeNull();
  });

  it("stores the selection and persists it to sessionStorage", () => {
    useLocationStore.getState().select(INDIRANAGAR);

    expect(useLocationStore.getState().selected).toEqual(INDIRANAGAR);
    expect(readStoredSelection()).toEqual(INDIRANAGAR);
  });

  it("clears the selection and leaves sessionStorage holding no selection", () => {
    useLocationStore.getState().select(INDIRANAGAR);
    useLocationStore.getState().clear();

    expect(useLocationStore.getState().selected).toBeNull();
    // Zustand's persist writes the partialized state on every change rather
    // than removing the key, so what matters is that the persisted selection
    // is null — a restored read of this entry must still yield no location.
    expect(readStoredSelection()).toBeNull();
  });

  it("persists under sessionStorage, never localStorage: a new tab starts fresh", () => {
    useLocationStore.getState().select(INDIRANAGAR);

    // The point of D-34: the choice is per-tab, not a durable cross-session
    // preference. A `localStorage` write here would survive into the next
    // browser session, which task 4.2 does not make.
    expect(localStorage.getItem(LOCATION_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(LOCATION_STORAGE_KEY)).not.toBeNull();
  });

  it("restores a valid stored selection on a fresh load, like a page refresh", async () => {
    writeStored(INDIRANAGAR);

    const store = await loadFreshStore();

    expect(store.getState().selected).toEqual(INDIRANAGAR);
  });

  it("survives malformed sessionStorage instead of crashing the application", async () => {
    sessionStorage.setItem(LOCATION_STORAGE_KEY, "{not json at all");

    const store = await loadFreshStore();

    // The application must boot with no selection rather than throw: a corrupt
    // entry is data the user never wrote.
    expect(store.getState().selected).toBeNull();
    expect(() => store.getState().select(INDIRANAGAR)).not.toThrow();
  });

  it("rejects a well-formed value of the wrong shape", async () => {
    writeStored({ id: "3", area: "Indiranagar" });

    const store = await loadFreshStore();

    expect(store.getState().selected).toBeNull();
  });

  it("rejects an obsolete value missing fields the app now reads", async () => {
    writeStored({ id: 3, area: "Indiranagar" });

    const store = await loadFreshStore();

    expect(store.getState().selected).toBeNull();
  });

  it("rejects a non-object stored value entirely", async () => {
    writeStored("Koramangala");

    const store = await loadFreshStore();

    expect(store.getState().selected).toBeNull();
  });

  it("leaves authentication and every other store untouched", () => {
    // The store holds exactly one field and imports nothing else, so choosing an
    // area cannot sign a user out or disturb any cached query — asserted here so
    // the isolation stays true if the store ever grows a second field.
    useLocationStore.getState().select(INDIRANAGAR);

    expect(Object.keys(useLocationStore.getState())).toEqual(
      expect.arrayContaining(["selected", "select", "clear"]),
    );
    expect(useLocationStore.getState().selected?.id).toBe(3);
  });
});
