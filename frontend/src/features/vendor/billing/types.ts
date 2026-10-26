/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export type SettlementEntryType =
  | "ONLINE_ORDER_CREDIT"
  | "COD_DUES"
  | "DISPUTE_ADJUSTMENT"
  | "SETTLEMENT_PAYMENT";

export type SettlementDirection =
  | "PLATFORM_PAYS_VENDOR"
  | "VENDOR_PAYS_PLATFORM"
  | "ZERO";

export type SettlementStatus = "PENDING" | "SETTLED";

export interface VendorBillingRateResponse {
  vendorId: number;
  rate: number;
}

export interface OutstandingBalanceResponse {
  vendorId: number;
  outstandingBalance: number;
}

export interface SettlementEntryResponse {
  id: number;
  vendorId: number;
  orderId: number | null;
  disputeId: number | null;
  settlementId: number | null;
  type: SettlementEntryType;
  amount: number;
  reference: string | null;
  createdAt: string;
}

export interface SettlementEntryPageResponse {
  content: SettlementEntryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface SettlementResponse {
  id: number;
  vendorId: number;
  vendorBusinessName: string | null;
  periodStart: string;
  periodEnd: string;
  totalCredits: number;
  totalDues: number;
  netAmount: number;
  direction: SettlementDirection;
  status: SettlementStatus;
  adminReferenceNote: string | null;
  settledByUserId: number | null;
  settledByEmail: string | null;
  settledAt: string | null;
  createdAt: string;
}

export interface SettlementPageResponse {
  content: SettlementResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface SettlementDetailResponse {
  settlement: SettlementResponse;
  entries: SettlementEntryResponse[];
}
