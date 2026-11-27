import { useCallback, useEffect, useId, useRef, useState } from "react";
import type { ServiceLocationOption } from "@/features/location/types";
import { FieldMessage } from "@/motion/FieldMessage";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-bolder-line-control shadow-sm ${FIELD_TRANSITION} focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm`;
const ERROR_BORDER = "border-bolder-danger-border";
const MENU_CLASS = `absolute z-50 mt-1 w-full rounded-md border border-bolder-line-control bg-bolder-surface shadow-lg ${FIELD_TRANSITION}`;
const ITEM_CLASS = `px-3 py-2 text-sm cursor-pointer ${PRESSABLE}`;
const ITEM_ACTIVE = "bg-bolder-blush-light text-bolder-rose-deep";

interface ServiceAreaPickerProps {
  /** Flattened existing service areas, each row's `id` is the `service_locations.id`. */
  options: ServiceLocationOption[];
  /** The selected service location id as a string, or "" for nothing chosen. */
  value: string;
  error?: string;
  hint?: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}

const DEFAULT_HINT =
  "Type to filter existing service areas, then pick one. A brand-new area is proposed "
  + "from your shop address instead and needs administrator approval before customers can "
  + "choose it.";

/**
 * A typeable service-area field backed by the seeded service locations.
 *
 * The vendor can type to narrow the existing areas and pick one; the submitted value
 * is the `service_locations.id`, so the backend's foreign key is always satisfied and
 * no coordinate or id is invented in the browser (D-4). The field is keyboard
 * navigable: arrow keys move the highlight, Enter selects, Escape closes.
 *
 * A brand-new area the vendor types but does not pick is not submitted here; it is
 * captured by the shop's city/area/pincode fields and persisted by the backend as a
 * PENDING service-area proposal for administrator review.
 */
export function ServiceAreaPicker({
  options,
  value,
  error,
  hint = DEFAULT_HINT,
  disabled = false,
  onChange,
}: ServiceAreaPickerProps) {
  const id = useId();
  const menuId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);

  const selectedOption = options.find((option) => option.id === value);

  const filtered = useCallback(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter(
      (option) =>
        option.label.toLowerCase().includes(q) ||
        option.city.toLowerCase().includes(q),
    );
  }, [query, options]);

  const select = useCallback(
    (option: ServiceLocationOption) => {
      setQuery(option.label);
      setOpen(false);
      setHighlighted(-1);
      onChange(option.id);
    },
    [onChange],
  );

  const close = useCallback(() => {
    setOpen(false);
    setHighlighted(-1);
  }, []);

  const rows = filtered();

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setOpen(true);
        setHighlighted((previous) =>
          previous >= rows.length - 1 ? 0 : previous + 1,
        );
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setOpen(true);
        setHighlighted((previous) =>
          previous <= 0 ? rows.length - 1 : previous - 1,
        );
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        if (open && highlighted >= 0 && highlighted < rows.length) {
          select(rows[highlighted]);
        }
        return;
      }
      if (event.key === "Escape") {
        close();
      }
    },
    [open, highlighted, rows, select, close],
  );

  const handleInputChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setQuery(event.target.value);
      setOpen(true);
      setHighlighted(-1);
    },
    [],
  );

  // Close the menu when clicking outside the input + menu container.
  useEffect(() => {
    const handleClick = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        close();
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [close]);

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-bolder-text">
        Service area
      </label>
      <div ref={containerRef} className="relative mt-1" role="none">
        <input
          ref={inputRef}
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={open ? menuId : undefined}
          aria-invalid={error ? true : undefined}
          value={selectedOption ? selectedOption.label : query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          autoComplete="off"
          spellCheck="false"
          className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""} ${PRESSABLE} ${FOCUS_RING}`}
        />
        {open && !disabled && rows.length > 0 && (
          <div id={menuId} role="listbox" aria-label="Service areas" className={MENU_CLASS}>
            {rows.map((option, index) => {
              const active = index === highlighted;
              return (
                <div
                  key={option.id}
                  role="option"
                  aria-selected={active}
                  id={`${id}-option-${index}`}
                  onClick={() => select(option)}
                  onMouseEnter={() => setHighlighted(index)}
                  className={`${ITEM_CLASS} ${active ? ITEM_ACTIVE : ""}`}
                >
                  {option.label}
                </div>
              );
            })}
          </div>
        )}
      </div>
      <FieldMessage id={id} hint={hint} message={error} reserve />
    </div>
  );
}
