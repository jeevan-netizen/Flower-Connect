import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";

/** The existing browse route. No new route and no new API is introduced here. */
const BROWSE_PATH = "/browse";

/**
 * The area / pincode field. A real `<label>`, visually hidden rather than
 * omitted: the placeholder disappears as soon as the field is typed into, so on
 * its own it would leave the input unnamed.
 */
const LABEL = "Your area or pincode";

/**
 * The hero's search pill.
 *
 * The application has no customer-facing location search yet: `/browse` is a
 * placeholder route, so there is nothing to query. Rather than invent an API,
 * submitting navigates to that route and carries the typed value as an `area`
 * query parameter, which is what the browse page will read when it is built. An
 * empty field navigates to the bare route.
 *
 * `/browse` sits behind `RequireAuth`, so an anonymous visitor is sent to the
 * login page by the existing guard. That is the route's behaviour, not this
 * component's.
 */
export function HeroSearch() {
  const navigate = useNavigate();
  const [area, setArea] = useState("");

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = area.trim();
    navigate(query ? { pathname: BROWSE_PATH, search: `?area=${encodeURIComponent(query)}` } : BROWSE_PATH);
  };

  return (
    <form
      role="search"
      onSubmit={handleSubmit}
      className="flex w-full max-w-[510px] flex-col gap-2 rounded-pill bg-white/95 p-2 pl-5 shadow-search transition-shadow duration-micro ease-standard focus-within:ring-2 focus-within:ring-bolder-rose sm:flex-row sm:items-center sm:gap-3 sm:pl-6"
    >
      <label htmlFor="home-area-search" className="sr-only">
        {LABEL}
      </label>
      <input
        id="home-area-search"
        name="area"
        type="search"
        value={area}
        onChange={(event) => setArea(event.target.value)}
        placeholder="Enter your area or pincode"
        autoComplete="postal-code"
        className="w-full flex-1 rounded-pill bg-transparent px-1 py-3 text-[15px] text-bolder-plum outline-none placeholder:text-bolder-placeholder"
      />
      <button
        type="submit"
        className={`w-full shrink-0 rounded-pill bg-bolder-rose-action px-6 py-3 text-[15px] font-semibold text-white shadow-button transition-[background-color,transform] duration-micro ease-standard hover:-translate-y-0.5 hover:bg-bolder-rose-deep motion-reduce:hover:translate-y-0 sm:w-auto ${PRESSABLE} ${FOCUS_RING_DARK}`}
      >
        Explore Flowers
      </button>
    </form>
  );
}
