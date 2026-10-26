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

import { useState } from "react";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { PlatformCommissionCard } from "../billing/components/PlatformCommissionCard";
import { VendorRateOverrideCard } from "../billing/components/VendorRateOverrideCard";
import { OverdueVendorsCard } from "../billing/components/OverdueVendorsCard";
import { AdminSettlementTable } from "../billing/components/AdminSettlementTable";
import { AdminLedgerTable } from "../billing/components/AdminLedgerTable";
import { RunSettlementModal } from "../billing/components/RunSettlementModal";
import { FinalizeSettlementModal } from "../billing/components/FinalizeSettlementModal";
import { SettlementDetailModal } from "@/features/vendor/billing/components/SettlementDetailModal";
import { useAdminLedger, useAdminOverdueVendors, useAdminSettlements } from "../billing/queries";
import type { SettlementEntryType, SettlementResponse, SettlementStatus } from "../billing/types";

export function AdminBillingPage() {
  const [settlementPage, setSettlementPage] = useState(0);
  const [settlementStatus, setSettlementStatus] = useState<SettlementStatus | "">("");
  const [settlementVendorId, setSettlementVendorId] = useState("");

  const [ledgerPage, setLedgerPage] = useState(0);
  const [ledgerType, setLedgerType] = useState<SettlementEntryType | "">("");
  const [ledgerVendorId, setLedgerVendorId] = useState("");

  const [isRunModalOpen, setIsRunModalOpen] = useState(false);
  const [settlementToFinalize, setSettlementToFinalize] = useState<SettlementResponse | null>(null);
  const [selectedSettlementId, setSelectedSettlementId] = useState<number | null>(null);

  const parsedSettlementVendorId = settlementVendorId.trim()
    ? parseInt(settlementVendorId.trim(), 10)
    : undefined;
  const parsedLedgerVendorId = ledgerVendorId.trim()
    ? parseInt(ledgerVendorId.trim(), 10)
    : undefined;

  const {
    data: settlementsData,
    isLoading: settlementsLoading,
    error: settlementsError,
    refetch: refetchSettlements,
  } = useAdminSettlements({
    page: settlementPage,
    size: 10,
    status: settlementStatus ? settlementStatus : undefined,
    vendorId: parsedSettlementVendorId,
  });

  const {
    data: ledgerData,
    isLoading: ledgerLoading,
    error: ledgerError,
    refetch: refetchLedger,
  } = useAdminLedger({
    page: ledgerPage,
    size: 10,
    type: ledgerType ? ledgerType : undefined,
    vendorId: parsedLedgerVendorId,
  });

  const {
    data: overdueData,
    isLoading: overdueLoading,
  } = useAdminOverdueVendors();

  if (settlementsError || ledgerError) {
    return (
      <AdminErrorState
        error={settlementsError || ledgerError}
        onRetry={() => {
          void refetchSettlements();
          void refetchLedger();
        }}
      />
    );
  }

  return (
    <div className="space-y-8" data-testid="admin-billing-page">
      <PageHeading
        title="Billing & Settlements"
        description="Configure platform and vendor commission rates, trigger weekly netting cycles, finalize payouts, and review ledger audit trails."
      />

      {/* Top 2 Cards: Platform Default Rate & Vendor Custom Override */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <PlatformCommissionCard />
        <VendorRateOverrideCard />
      </div>

      {/* Overdue Accounts Notice (v1 Advisory Policy) */}
      <OverdueVendorsCard
        overdueVendors={overdueData}
        isLoading={overdueLoading}
        onFilterVendor={(vId) => {
          setLedgerVendorId(String(vId));
          setLedgerPage(0);
        }}
      />

      {/* Settlements Table Section */}
      <div>
        <AdminSettlementTable
          settlements={settlementsData?.content}
          isLoading={settlementsLoading}
          selectedStatus={settlementStatus}
          onStatusChange={(status) => {
            setSettlementStatus(status);
            setSettlementPage(0);
          }}
          vendorIdFilter={settlementVendorId}
          onVendorIdFilterChange={(val) => {
            setSettlementVendorId(val);
            setSettlementPage(0);
          }}
          page={settlementsData?.page ?? settlementPage}
          totalPages={settlementsData?.totalPages ?? 1}
          totalElements={settlementsData?.totalElements ?? 0}
          onPageChange={setSettlementPage}
          onOpenRunModal={() => setIsRunModalOpen(true)}
          onOpenFinalizeModal={(settlement) => setSettlementToFinalize(settlement)}
          onSelectSettlement={(id) => setSelectedSettlementId(id)}
        />
      </div>

      {/* Ledger Transactions Section */}
      <div>
        <AdminLedgerTable
          entries={ledgerData?.content}
          isLoading={ledgerLoading}
          selectedType={ledgerType}
          onTypeChange={(type) => {
            setLedgerType(type);
            setLedgerPage(0);
          }}
          vendorIdFilter={ledgerVendorId}
          onVendorIdFilterChange={(val) => {
            setLedgerVendorId(val);
            setLedgerPage(0);
          }}
          page={ledgerData?.page ?? ledgerPage}
          totalPages={ledgerData?.totalPages ?? 1}
          totalElements={ledgerData?.totalElements ?? 0}
          onPageChange={setLedgerPage}
        />
      </div>

      {/* Modals */}
      <RunSettlementModal
        isOpen={isRunModalOpen}
        onClose={() => setIsRunModalOpen(false)}
      />

      <FinalizeSettlementModal
        settlement={settlementToFinalize}
        onClose={() => setSettlementToFinalize(null)}
      />

      {selectedSettlementId !== null && (
        <SettlementDetailModal
          settlementId={selectedSettlementId}
          onClose={() => setSelectedSettlementId(null)}
        />
      )}
    </div>
  );
}
