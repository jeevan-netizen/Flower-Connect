/**
 * Cross-cutting TypeScript contracts.
 *
 * `PageResponse` is declared here rather than in a feature slice because three
 * areas paginate against the same Spring Data envelope: the Phase 2a location
 * listing, both admin listings (plan task 2.10) and the Phase 3c/3d catalog and
 * inventory listings (plan tasks 3.5, 3.6). The slices keep their own named
 * aliases (`ProductPageResponse`, `AdminUserPageResponse`, …) over this one
 * interface, so a change to the envelope is a single edit rather than one per
 * slice — and the admin slice re-exports it, which keeps every existing import
 * path working unchanged.
 */

export interface PageResponse<T> {
  content: T[];
  /** Zero-based page index, as returned by Spring Data. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}