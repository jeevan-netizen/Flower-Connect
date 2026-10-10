/**
 * Formatting helpers used by more than one feature slice.
 *
 * Only genuinely shared primitives live here. Anything a single slice owns
 * (status label maps, money formatting, page-number arithmetic for one listing)
 * stays in that slice — a shared module that accumulates slice-specific maps is
 * how two areas end up disagreeing about what a status is called.
 */

/**
 * One-based page number for display. The wire format is zero-based: every
 * paginated controller in this project defaults `page` to `0`.
 *
 * Shared because the displayed number and the wire number come from the same
 * source in both listings; computing it twice is how a "Page 1 of 3" caption
 * ends up attached to the wrong set of rows.
 */
export function displayPageNumber(page: number): number {
  return page + 1;
}