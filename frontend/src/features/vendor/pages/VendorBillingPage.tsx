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
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import {
  useVendorBillingRate,
  useVendorLedger,
  useVendorOutstandingBalance,
  useVendorSettlements,
} from "../billing/queries";
import { OutstandingBalanceCard } from "../billing/components/OutstandingBalanceCard";
import { CommissionRateCard } from "../billing/components/CommissionRateCard";
import { StatementDownloadCard } from "../billing/components/StatementDownloadCard";
import { SettlementHistoryTable } from "../billing/components/SettlementHistoryTable";
import { LedgerTransactionTable } from "../billing/components/LedgerTransactionTable";
import { SettlementDetailModal } from "../billing/components/SettlementDetailModal";
import type { SettlementEntryType } from "../billing/types";

export function VendorBillingPage() {
  const [ledgerPage, setLedgerPage] = useState(0);
  const [selectedType, setSelectedType] = useState<SettlementEntryType | "">("");
  const [selectedSettlementId, setSelectedSettlementId] = useState<number | null>(null);

  const {
    data: rateData,
    isLoading: rateLoading,
    error: rateError,
    refetch: refetchRate,
  } = useVendorBillingRate();

  const {
    data: balanceData,
    isLoading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useVendorOutstandingBalance();

  const {
    data: settlementsData,
    isLoading: settlementsLoading,
    error: settlementsError,
    refetch: refetchSettlements,
  } = useVendorSettlements({ page: 0, size: 10 });

  const {
    data: ledgerData,
    isLoading: ledgerLoading,
    error: ledgerError,
    refetch: refetchLedger,
  } = useVendorLedger({
    page: ledgerPage,
    size: 10,
    type: selectedType ? selectedType : undefined,
  });

  const hasFatalError = Boolean(
    rateError || balanceError || settlementsError || ledgerError,
  );

  if (hasFatalError) {
    return (
      <VendorErrorState
        error={rateError || balanceError || settlementsError || ledgerError}
        onRetry={() => {
          void refetchRate();
          void refetchBalance();
          void refetchSettlements();
          void refetchLedger();
        }}
      />
    );
  }

  const handleTypeChange = (type: SettlementEntryType | "") => {
    setSelectedType(type);
    setLedgerPage(0);
  };

  return (
    <div className="space-y-8" data-testid="vendor-billing-page">
      <PageHeading
        title="Billing & Payouts"
        description="Monitor marketplace commissions, unattached balance, weekly netting settlements, and official ledger records."
      />

      {/* Top summary row: Unattached balance & Commission rate */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <OutstandingBalanceCard
          balance={balanceData?.outstandingBalance}
          isLoading={balanceLoading}
        />
        <CommissionRateCard
          rate={rateData?.rate}
          isLoading={rateLoading}
        />
      </div>

      {/* Statement Download Form */}
      <div>
        <StatementDownloadCard />
      </div>

      {/* Settlement History Table */}
      <div>
        <SettlementHistoryTable
          settlements={settlementsData?.content}
          isLoading={settlementsLoading}
          onSelectSettlement={(id) => setSelectedSettlementId(id)}
        />
      </div>

      {/* Ledger Transaction Log */}
      <div>
        <LedgerTransactionTable
          entries={ledgerData?.content}
          isLoading={ledgerLoading}
          page={ledgerData?.page ?? ledgerPage}
          totalPages={ledgerData?.totalPages ?? 1}
          totalElements={ledgerData?.totalElements ?? 0}
          selectedType={selectedType}
          onTypeChange={handleTypeChange}
          onPageChange={setLedgerPage}
        />
      </div>

      {/* Detail Modal for Selected Settlement */}
      {selectedSettlementId !== null && (
        <SettlementDetailModal
          settlementId={selectedSettlementId}
          onClose={() => setSelectedSettlementId(null)}
        />
      )}
    </div>
  );
}
