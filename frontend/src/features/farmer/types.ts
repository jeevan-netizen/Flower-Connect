/**
 * Farmer module frontend types — Stage 3 of the farmer module.
 *
 * Mirrors the backend contracts exactly:
 *   `FarmerLinkResponse` / `FarmerVendorResponse`
 *   `FarmerCollectionRequest` / `FarmerCollectionResponse`
 *   `PassbookResponse` / `PassbookEntryResponse`
 *   `FarmerDeposit` (vendor-entered deposit row)
 *
 * Four deliberate constraints, matching the vendor slice:
 *
 * 1. Every status and mode is a closed union taken from the backend enum, so a
 *    request can never carry a value the column would reject.
 * 2. Money stays a JSON number on the wire and is only formatted for display — no
 *    arithmetic is done in the browser; the backend is authoritative.
 * 3. `PassbookEntryResponse` is a flat entry: a `DEPOSIT` row carries the deposit
 *    fields and nulls the collection ones, and a `COLLECTION` row does the
 *    inverse. This keeps one combined, chronologically ordered list.
 * 4. Amounts are `number` (the wire format), never `BigDecimal` — the browser has
 *    no decimal type and no business need to recompute.
 */

import type { PageResponse } from "@/shared/types";

export const FARMER_ROLE = "FARMER";

export const LINK_STATUSES = ["PENDING", "ACTIVE", "REJECTED", "SUSPENDED"] as const;
export type LinkStatus = (typeof LINK_STATUSES)[number];

export const LINK_STATUS_LABELS: Record<LinkStatus, string> = {
  PENDING: "Awaiting approval",
  ACTIVE: "Active",
  REJECTED: "Rejected",
  SUSPENDED: "Suspended",
};

export function formatLinkStatus(status: LinkStatus): string {
  return LINK_STATUS_LABELS[status];
}

export const LINK_STATUS_TONES: Record<LinkStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  ACTIVE: "bg-brand-100 text-brand-900",
  REJECTED: "bg-slate-200 text-slate-800",
  SUSPENDED: "bg-red-100 text-red-800",
};

/** Mirrors `FarmerLinkResponse`. */
export interface FarmerLink {
  id: number;
  vendorId: number;
  farmerId: number;
  farmerName: string;
  vendorName: string;
  status: LinkStatus;
  outstandingBalance: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors `FarmerVendorResponse`. */
export interface FarmerVendor {
  id: number;
  businessName: string;
  vendorStatus: string;
  linkStatus: LinkStatus | null;
}

/** Mirrors `FarmerProfileResponse` (GET /api/v1/farmer/profile). */
export interface FarmerProfile {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  address: string | null;
  role: string;
}

/** Mirrors `FarmerCollectionRequest`. */
export interface FarmerCollectionRequest {
  amount: number;
  mode: CollectionMode;
  note: string | null;
}

export type CollectionMode = "CASH" | "UPI";

export const COLLECTION_MODES = ["CASH", "UPI"] as const;
export const COLLECTION_MODE_LABELS: Record<CollectionMode, string> = {
  CASH: "Cash",
  UPI: "UPI",
};

/** Mirrors `FarmerCollectionResponse`. */
export interface FarmerCollection {
  id: number;
  linkId: number;
  amount: number;
  mode: CollectionMode;
  note: string | null;
  status: CollectionStatus;
  requestedAt: string;
  decidedAt: string | null;
}

export type CollectionStatus = "PENDING" | "CONFIRMED" | "REJECTED";

export const COLLECTION_STATUS_LABELS: Record<CollectionStatus, string> = {
  PENDING: "Pending",
  CONFIRMED: "Confirmed",
  REJECTED: "Rejected",
};

export const COLLECTION_STATUS_TONES: Record<CollectionStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-brand-100 text-brand-900",
  REJECTED: "bg-slate-200 text-slate-800",
};

/** Mirrors `PassbookResponse`. */
export interface Passbook {
  linkId: number;
  farmerId: number;
  farmerName: string;
  vendorId: number;
  vendorName: string;
  linkStatus: LinkStatus;
  outstandingBalance: number;
  entries: PassbookEntry[];
}

/** Mirrors `PassbookEntryResponse`. */
export interface PassbookEntry {
  id: number;
  type: PassbookEntryType;
  date: string;
  runningBalance: number;
  /** Set for a `DEPOSIT` entry; null for a `COLLECTION` entry. */
  amount: number | null;
  mode: CollectionMode | null;
  status: CollectionStatus | null;
  note: string | null;
  /** Set for a `DEPOSIT` entry; null for a `COLLECTION` entry. */
  flowerType: string | null;
  grossKg: number | null;
  deductionKg: number | null;
  netKg: number | null;
  pricePerKg: number | null;
}

export type PassbookEntryType = "DEPOSIT" | "COLLECTION";

export const PASSBOOK_ENTRY_LABELS: Record<PassbookEntryType, string> = {
  DEPOSIT: "Flower deposit",
  COLLECTION: "Money collection",
};

/** Mirrors the deposit row the vendor writes (`FarmerDeposit`). */
export interface FarmerDeposit {
  id: number;
  linkId: number;
  flowerType: string;
  grossKg: number;
  deductionKg: number;
  netKg: number;
  pricePerKg: number;
  amount: number;
  depositedAt: string;
  createdAt: string;
}

/** `GET /api/v1/farmer/links` and `GET /api/v1/vendor/farmer-links` share the page envelope. */
export type FarmerLinkPageResponse = PageResponse<FarmerLink>;
