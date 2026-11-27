import { useEffect, useId, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { useCart } from "@/features/cart/queries";
import { LocationMenu } from "@/features/location/components/LocationMenu";
import { NotificationBell } from "@/features/notification/components/NotificationBell";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";
import { useMediaQuery, WIDE_HEADER_QUERY } from "@/app/hooks/useMediaQuery";

/**
 * Application role for a vendor. The plan calls this role "VENDOR"; the seeded
 * role is FLORIST and is not renamed (docs/decisions.md, D-11).
 */
const VENDOR_ROLE = "FLORIST";

/** Seeded role name for administrators (`roles` row 3, V3__seed_roles.sql). */
const ADMIN_ROLE = "ADMIN";

/** Seeded role name for farmers (V35 migration). */
const FARMER_ROLE = "FARMER";

/** Seeded role name for customers (V3__seed_roles.sql). */
const CUSTOMER_ROLE = "CUSTOMER";

/**
 * Header link treatment on the ivory surface: 15px, weight 500, rose on hover,
 * and the shared focus ring so keyboard focus is always visible.
 */
const NAV_LINK =
  "rounded-sm text-[15px] font-medium text-bolder-text transition-colors duration-micro ease-standard hover:text-bolder-rose-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bolder-rose focus-visible:ring-offset-2 focus-visible:ring-offset-bolder-bg";

/**
 * The pill in the right-hand cluster. Signed out it is the way in; signed in it
 * is the way out, so the two states share one shape and one focus ring. The
 * primary action is rose; green is reserved for the botanical accents.
 */
const NAV_PILL =
  `inline-flex items-center rounded-pill border-[1.5px] border-transparent bg-bolder-rose-action px-6 py-[11px] text-[15px] font-medium text-white transition-colors duration-micro ease-standard hover:bg-bolder-rose-deep ${FOCUS_RING_DARK}`;

/**
 * Three overlapping circles standing in for a rose head, sized by the design.
 * `fill-bolder-*` keeps the mark on the token palette, so it re-tints with the
 * theme rather than carrying its own hex.
 */
function LogoMark() {
  return (
    <svg
      width="30"
      height="30"
      viewBox="0 0 30 30"
      aria-hidden="true"
      focusable="false"
      className="shrink-0"
    >
      <circle cx="11" cy="12" r="7" className="fill-bolder-rose-strong" />
      <circle cx="19" cy="12" r="7" className="fill-bolder-rose" />
      <circle cx="15" cy="19" r="7" className="fill-bolder-blush" />
    </svg>
  );
}

/**
 * The hamburger / close mark for the small-screen disclosure. Decorative: the
 * button carries the accessible name, so the icon is hidden from assistive
 * technology rather than labelled twice.
 */
function MenuIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      aria-hidden="true"
      focusable="false"
      className="shrink-0"
    >
      {open ? (
        <>
          <path d="M6 6l12 12" />
          <path d="M18 6 6 18" />
        </>
      ) : (
        <>
          <path d="M4 7h16" />
          <path d="M4 12h16" />
          <path d="M4 17h16" />
        </>
      )}
    </svg>
  );
}

/**
 * The application shell's header.
 *
 * Navigation is auth-aware and unchanged in substance from the header it
 * replaces: the customer links are always available, a signed-in account gets
 * Cart / Orders / its own area and a way out, and a signed-out visitor gets the
 * way in plus both account-creation entry points. What changed is the treatment
 * — the dark landing palette, the wordmark, and the pill — because the landing
 * page it sits above is dark.
 *
 * The links do not fit on one row below 1280px, so they are one of two renderings
 * of the *same* list: the bar from 1280px up, and a disclosure panel below it.
 * The choice is made from the real viewport (`useMediaQuery`) rather than by
 * rendering both and hiding one with CSS, because a `display:none` copy is out of
 * the tab order but still a second copy in the DOM that every query, test and
 * assistive technology has to reason around.
 */
export function SiteHeader() {
  const location = useLocation();
  const { isAuthenticated, user } = useAuthStore();
  // The cart link below is rendered only for a customer, so the cart is
  // fetched only for a customer. An anonymous visitor, a florist and an admin
  // would each get a refusal the app has no use for — a 401 for the first two
  // and a 403 for the third — and a 401 additionally starts a refresh and a
  // logout that can neither succeed. The query and the link are now gated by
  // the same condition, so they cannot disagree.
  const { data: cart } = useCart({
    enabled: isAuthenticated && user?.role === CUSTOMER_ROLE,
  });
  const [menuOpen, setMenuOpen] = useState(false);
  const panelId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const isWide = useMediaQuery(WIDE_HEADER_QUERY);

  // Following a link from the panel must leave it closed, or the next page opens
  // with the menu still covering it. A navigation does not change any state this
  // component owns, so the pathname is what tells us one happened.
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  // Escape closes the panel and returns focus to the button that opened it, so a
  // keyboard user is never stranded behind a panel they cannot see.
  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
        toggleRef.current?.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [menuOpen]);

  /**
   * The links themselves, so the bar and the panel cannot drift apart. Each
   * caller decides the wrapper; a link closes the panel on the way through.
   */
  const renderLinks = (className: string) => (
    <div className={className}>
      <Link className={NAV_LINK} to="/">
        Home
      </Link>
      {/* Customer-facing catalogue links. Browsing is public, so an
          unauthenticated visitor still sees Browse; a signed-in customer sees
          Browse and Orders. A vendor or admin must not see customer browsing
          or customer order history in the shared header. */}
      {!isAuthenticated ? (
        <Link className={NAV_LINK} to="/browse">
          Browse
        </Link>
      ) : user?.role === CUSTOMER_ROLE ? (
        <>
          <Link className={NAV_LINK} to="/browse">
            Browse
          </Link>
          <Link className={NAV_LINK} to="/orders">
            Orders
          </Link>
        </>
      ) : null}
      <Link className={NAV_LINK} to="/profile">
        Profile
      </Link>

      {isAuthenticated ? (
        <>
          {user?.role === CUSTOMER_ROLE && (
            <>
              <Link
                className={NAV_LINK}
                to="/cart"
              >
                Cart
                {cart?.totalItems && (
                  <span className="ml-1 inline-flex h-[1.5rem] min-w-[1.5rem] items-center justify-center rounded-full bg-bolder-rose-action px-[6px] text-[10px] font-bold text-white">
                    {cart.totalItems}
                  </span>
                )}
              </Link>
            </>
          )}
          {user?.role === VENDOR_ROLE && (
            <Link className={NAV_LINK} to="/vendor">
              Vendor
            </Link>
          )}
          {user?.role === FARMER_ROLE && (
            <Link className={NAV_LINK} to="/farmer">
              Farmer
            </Link>
          )}
          {user?.role === ADMIN_ROLE && (
            <Link className={NAV_LINK} to="/admin">
              Admin
            </Link>
          )}
          <span className="relative">
            <NotificationBell enabled={isAuthenticated} />
          </span>
        </>
      ) : (
        <>
          {/* Phase 1's vendor onboarding entry point: it was a forward link to a
              Phase 2 that did not exist yet, and now resolves to the registration
              page. Kept in the signed-out branch because registering creates a new
              account — a signed-in visitor must not end up with two. */}
          <Link className={NAV_LINK} to="/register">
            Register
          </Link>
          <Link className={NAV_LINK} to="/vendor/register">
            For florists
          </Link>
          {/*
            `aria-current` marks the active page for assistive technology. The
            header sits above every route, so without it a visitor has no way
            to hear which one they are on.
          */}
          <Link
            className={NAV_PILL}
            to="/login"
            aria-current={location.pathname === "/login" ? "page" : undefined}
          >
            Login
          </Link>
        </>
      )}
    </div>
  );

  return (
    <header className="relative border-b border-bolder-line bg-bolder-bg text-bolder-text">
        <nav
          aria-label="Main"
          className="mx-auto flex w-full max-w-landing items-center justify-between gap-4 px-5 py-4 sm:gap-6 sm:px-8 lg:px-14"
        >
        <Link
          to="/"
          className={`flex items-center gap-3 rounded-sm ${FOCUS_RING_DARK}`}
        >
          <LogoMark />
          <span className="font-display text-[20px] sm:text-[24px] font-medium leading-none">
            FlowerConnect
          </span>
        </Link>

        {/*
          The location control sits in the top bar on every viewport, not inside
          the disclosure panel: the chosen area is the context the whole
          marketplace runs in, so it must be one click away from every page
          rather than buried behind the mobile menu. Its own dropdown panel is
          coordinated with the navigation panel — opening one closes the other,
          because both are anchored full-width to this header.
        */}
        <div className="flex items-center gap-2 sm:gap-4 lg:gap-6">
          <LocationMenu onOpen={() => setMenuOpen(false)} />
          {isWide ? (
            renderLinks("flex items-center gap-6 lg:gap-9")
          ) : (
            <button
              type="button"
              ref={toggleRef}
              aria-expanded={menuOpen}
              aria-controls={panelId}
              onClick={() => setMenuOpen((open) => !open)}
              className={`rounded-sm p-2 text-bolder-text transition-colors duration-micro ease-standard hover:text-bolder-blush ${PRESSABLE} ${FOCUS_RING_DARK}`}
            >
              <MenuIcon open={menuOpen} />
              <span className="sr-only">{menuOpen ? "Close menu" : "Open menu"}</span>
            </button>
          )}
        </div>
      </nav>

      {!isWide && menuOpen && (
        <div
          id={panelId}
          className="absolute inset-x-0 top-full z-50 border-t border-bolder-line bg-bolder-bg px-5 pb-6 pt-4 shadow-glass sm:px-8"
        >
          {renderLinks("flex flex-col items-start gap-5")}
        </div>
      )}
    </header>
  );
}
