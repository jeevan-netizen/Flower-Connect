import { useEffect, useId, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearVendorCache } from "@/features/vendor/queries";
import { clearAdminCache } from "@/features/admin/queries";
import { LocationMenu } from "@/features/location/components/LocationMenu";
import { FOCUS_RING_DARK, PRESSABLE } from "@/motion/pressable";
import { useMediaQuery, WIDE_HEADER_QUERY } from "@/app/hooks/useMediaQuery";

/**
 * Application role for a vendor. The plan calls this role "VENDOR"; the seeded
 * role is FLORIST and is not renamed (docs/decisions.md, D-11).
 */
const VENDOR_ROLE = "FLORIST";

/** Seeded role name for administrators (`roles` row 3, V3__seed_roles.sql). */
const ADMIN_ROLE = "ADMIN";

/**
 * Header link treatment on the dark landing surface: 15px, weight 500, blush on
 * hover, and the shared dark focus ring so keyboard focus is visible against the
 * near-black background.
 */
const NAV_LINK =
  "rounded-sm text-[15px] font-medium text-bolder-text transition-colors duration-micro ease-standard hover:text-bolder-blush focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bolder-rose focus-visible:ring-offset-2 focus-visible:ring-offset-bolder-bg";

/**
 * The pill in the right-hand cluster. Signed out it is the way in; signed in it
 * is the way out, so the two states share one shape and one focus ring.
 */
const NAV_PILL =
  `inline-flex items-center rounded-pill border-[1.5px] border-bolder-blush px-6 py-[11px] text-[15px] font-medium text-bolder-blush transition-colors duration-micro ease-standard hover:bg-bolder-rose hover:text-bolder-bg hover:border-bolder-rose ${FOCUS_RING_DARK}`;

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
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const location = useLocation();
  const { isAuthenticated, user, logout } = useAuthStore();
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

  const handleLogout = () => {
    // Vendor and admin data are each cached under their own query key; drop both
    // so the next account to sign in on this tab never sees the previous user's
    // profile, user list or vendor list.
    clearVendorCache(queryClient);
    clearAdminCache(queryClient);
    logout();
    setMenuOpen(false);
    navigate("/", { replace: true });
  };

  /**
   * The links themselves, so the bar and the panel cannot drift apart. Each
   * caller decides the wrapper; a link closes the panel on the way through.
   */
  const renderLinks = (className: string) => (
    <div className={className}>
      <Link className={NAV_LINK} to="/">
        Home
      </Link>
      <Link className={NAV_LINK} to="/browse">
        Browse
      </Link>
      <Link className={NAV_LINK} to="/orders">
        Orders
      </Link>

      {isAuthenticated ? (
        <>
          <Link className={NAV_LINK} to="/cart">
            Cart
          </Link>
          {user?.role === VENDOR_ROLE && (
            <Link className={NAV_LINK} to="/vendor">
              Vendor
            </Link>
          )}
          {user?.role === ADMIN_ROLE && (
            <Link className={NAV_LINK} to="/admin">
              Admin
            </Link>
          )}
          <button type="button" onClick={handleLogout} className={NAV_PILL}>
            Log out
          </button>
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
    <header className="relative bg-bolder-bg text-bolder-text">
      <nav
        aria-label="Main"
        className="mx-auto flex w-full max-w-landing items-center justify-between gap-6 px-5 py-4 sm:px-8 lg:px-14"
      >
        <Link
          to="/"
          className={`flex items-center gap-3 rounded-sm ${FOCUS_RING_DARK}`}
        >
          <LogoMark />
          <span className="font-display text-[24px] font-medium leading-none">
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
        <div className="flex items-center gap-4 lg:gap-6">
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
          className="absolute inset-x-0 top-full z-50 border-t border-glass-border bg-bolder-bg px-5 pb-6 pt-4 shadow-glass sm:px-8"
        >
          {renderLinks("flex flex-col items-start gap-5")}
        </div>
      )}
    </header>
  );
}
