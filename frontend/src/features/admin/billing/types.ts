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

import type {
  OutstandingBalanceResponse,
  SettlementDetailResponse,
  SettlementDirection,
  SettlementEntryPageResponse,
  SettlementEntryResponse,
  SettlementEntryType,
  SettlementPageResponse,
  SettlementResponse,
  SettlementStatus,
} from "@/features/vendor/billing/types";

export type {
  OutstandingBalanceResponse,
  SettlementDetailResponse,
  SettlementDirection,
  SettlementEntryPageResponse,
  SettlementEntryResponse,
  SettlementEntryType,
  SettlementPageResponse,
  SettlementResponse,
  SettlementStatus,
};

export interface PlatformCommissionRateResponse {
  configKey: string;
  rate: number;
}

export interface VendorCommissionRateResponse {
  vendorId: number;
  rate: number;
}

export interface UpdateCommissionRateRequest {
  rate: number;
}

export interface RunSettlementRequest {
  vendorId?: number;
  periodStart: string;
  periodEnd: string;
}

export interface FinalizeSettlementRequest {
  adminReferenceNote: string;
}

export interface AdminSettlementFilters {
  page?: number;
  size?: number;
  vendorId?: number;
  status?: SettlementStatus;
}

export interface AdminLedgerFilters {
  page?: number;
  size?: number;
  vendorId?: number;
  from?: string;
  to?: string;
  type?: SettlementEntryType;
}

export interface OverdueVendorResponse {
  vendorId: number;
  vendorBusinessName: string;
  outstandingBalance: number;
  outstandingDues: number;
  daysOverdue: number;
  oldestDuesDate: string;
}
