import { useEffect, useId, useRef, useState } from "react";
import { LocationPicker } from "@/features/location/components/LocationPicker";
import {
  isLocationListEmpty,
  useLocationSelection,
} from "@/features/location/hooks/useLocationSelection";
import { formatSelectedLocation } from "@/features/location/types";
import { MapPinIcon } from "@/features/home/components/icons";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";

const BUTTON_CLASS = `inline-flex items-center gap-2 rounded-sm text-[15px] font-medium text-bolder-text transition-colors duration-micro ease-standard hover:text-bolder-blush ${PRESSABLE} ${FOCUS_RING_DARK}`;

const PANEL_CLASS =
  "absolute inset-x-0 top-full z-50 border-t border-slate-200 bg-white px-5 py-4 shadow-glass sm:px-8";

interface LocationMenuProps {
  /**
   * Called when this panel opens, so the shell's mobile navigation panel can
   * close: two full-width panels anchored to the same header would overlap.
   */
  onOpen?: () => void;
}

/**
 * The application shell's delivery-location control (plan task 4.2).
 *
 * One button in the header showing the current selection — or "Set your
 * delivery location" when there is none — and one panel with the picker. The
 * selection itself lives in the session store (`sessionStorage`, D-34), so it
 * is the same on every page of the tab and survives a refresh.
 *
 * The panel follows the header's existing disclosure pattern (the same one the
 * navigation uses): a button with `aria-expanded`/`aria-controls`, Escape
 * closes it and returns focus to the button, and a navigation or a selection
 * closes it too. Choosing an area closes the panel and returns focus to the
 * button, so a keyboard user is never stranded behind a panel they cannot see.
 *
 * This control is the one place the selection is *validated*: the stored value
 * is displayed only while the live service-area list confirms it exists. A
 * selection whose area no longer exists is cleared rather than shown, because
 * the discovery and search queries of tasks 4.3/4.4 would reject it.
 */
export function LocationMenu({ onOpen }: LocationMenuProps) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const selection = useLocationSelection();
  const { selected, cities, isPending, error, refetch, selectById } = selection;

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  const handleToggle = () => {
    setOpen((previous) => {
      const next = !previous;
      if (next) {
        onOpen?.();
      }
      return next;
    });
  };

  const handleChange = (value: string) => {
    if (value === "") {
      return;
    }
    selectById(value);
    setOpen(false);
    buttonRef.current?.focus();
  };

  const empty = isLocationListEmpty(selection);
  const unavailable = isPending || error !== null || empty;
  const hint = isPending
    ? "Loading delivery areas..."
    : error !== null
      ? "Delivery areas could not be loaded."
      : empty
        ? "No delivery areas are configured yet."
        : undefined;

  const buttonLabel = selected
    ? `Delivery location: ${formatSelectedLocation(selected)}`
    : "Set your delivery location";

  return (
    <>
      <button
        type="button"
        ref={buttonRef}
        aria-label={buttonLabel}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={handleToggle}
        className={BUTTON_CLASS}
      >
        <MapPinIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span>{selected ? formatSelectedLocation(selected) : "Set location"}</span>
      </button>

      {open && (
        <div id={panelId} className={PANEL_CLASS}>
          <div className="mx-auto w-full max-w-md">
            <LocationPicker
              cities={cities}
              value={selected ? String(selected.id) : ""}
              hint={hint}
              disabled={unavailable}
              onChange={handleChange}
            />
            {Boolean(error) && (
              <button
                type="button"
                onClick={refetch}
                className={`mt-2 inline-flex rounded-md text-sm font-medium text-brand-700 underline underline-offset-2 ${PRESSABLE} ${FOCUS_RING_DARK}`}
              >
                Try again
              </button>
            )}
          </div>
        </div>
      )}
    </>
  );
}
