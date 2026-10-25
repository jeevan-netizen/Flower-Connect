/**
 * Dispute types (plan Phase 8, tasks 8.4-8.5).
 */

export const DISPUTE_STATUSES = [
  "OPEN",
  "UNDER_REVIEW",
  "RESOLVED_REFUND",
  "RESOLVED_PARTIAL",
  "RESOLVED_REJECTED",
] as const;
export type DisputeStatus = (typeof DISPUTE_STATUSES)[number];

export const DISPUTE_REASON_CODES = [
  "ITEM_NOT_AS_DESCRIBED",
  "ITEM_DAMAGED",
  "ITEM_MISSING",
  "WRONG_ITEM_DELIVERED",
  "QUALITY_COMPLAINT",
  "LATE_DELIVERY",
  "OTHER",
] as const;
export type DisputeReasonCode = (typeof DISPUTE_REASON_CODES)[number];

export const RESOLUTION_STATUSES = [
  "RESOLVED_REFUND",
  "RESOLVED_PARTIAL",
  "RESOLVED_REJECTED",
] as const;
export type ResolutionStatus = (typeof RESOLUTION_STATUSES)[number];

export interface DisputeDetailResponse {
  id: number;
  orderId: number;
  orderNumber: string;
  status: DisputeStatus;
  reasonCode: DisputeReasonCode;
  description: string;
  customerName: string;
  customerEmail: string;
  vendorName: string;
  orderTotal: number;
  refundAmount: number | null;
  vendorResponse: string | null;
  resolutionNote: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface DisputeListItem {
  id: number;
  orderId: number;
  orderNumber: string;
  status: DisputeStatus;
  reasonCode: DisputeReasonCode;
  customerName: string;
  vendorName: string;
  orderTotal: number;
  refundAmount: number | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface DisputePageResponse {
  content: DisputeListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface RaiseDisputeRequest {
  reasonCode: DisputeReasonCode;
  description: string;
}

export interface VendorDisputeResponseRequest {
  response: string;
}

export interface ResolveDisputeRequest {
  status: ResolutionStatus;
  resolutionNote: string;
  refundAmount: number | null;
}

export const REASON_CODE_LABELS: Record<DisputeReasonCode, string> = {
  ITEM_NOT_AS_DESCRIBED: "Item not as described",
  ITEM_DAMAGED: "Item damaged",
  ITEM_MISSING: "Item missing",
  WRONG_ITEM_DELIVERED: "Wrong item delivered",
  QUALITY_COMPLAINT: "Quality complaint",
  LATE_DELIVERY: "Late delivery",
  OTHER: "Other",
};

export const DISPUTE_STATUS_LABELS: Record<DisputeStatus, string> = {
  OPEN: "Open",
  UNDER_REVIEW: "Under review",
  RESOLVED_REFUND: "Resolved — full refund",
  RESOLVED_PARTIAL: "Resolved — partial refund",
  RESOLVED_REJECTED: "Resolved — rejected",
};

export const DISPUTES_PATH = "/disputes";
export const VENDOR_DISPUTES_PATH = "/vendor/disputes";
export const ADMIN_DISPUTES_PATH = "/admin/disputes";