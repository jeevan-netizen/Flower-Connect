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
import { useFinalizeAdminSettlement } from "../queries";
import type { SettlementResponse } from "../types";

interface FinalizeSettlementModalProps {
  settlement: SettlementResponse | null;
  onClose: () => void;
}

export function FinalizeSettlementModal({ settlement, onClose }: FinalizeSettlementModalProps) {
  const [adminReferenceNote, setAdminReferenceNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const finalizeMutation = useFinalizeAdminSettlement();

  if (!settlement) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminReferenceNote.trim()) {
      setError("Admin reference note is required to finalize settlement.");
      return;
    }

    try {
      setError(null);
      await finalizeMutation.mutateAsync({
        id: settlement.id,
        adminReferenceNote: adminReferenceNote.trim(),
      });
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to finalize settlement.";
      setError(msg);
    }
  };

  return (
    <div
      data-testid="finalize-settlement-modal"
      className="fixed inset-0 z-50 flex items-center justify-center bg-bolder-overlay backdrop-blur-sm p-4 animate-in fade-in"
    >
      <div className="relative w-full max-w-md rounded-2xl bg-bolder-surface p-6 shadow-2xl border border-bolder-line">
        <div className="flex items-center justify-between border-b border-bolder-line pb-3">
          <h3 className="text-base font-bold text-bolder-text">
            Finalize Settlement #{settlement.id}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-bolder-muted hover:bg-bolder-bg hover:text-bolder-text"
          >
            ✕
          </button>
        </div>

        <div className="mt-4 rounded-xl bg-bolder-bg p-4 space-y-2 text-xs">
          <div className="flex justify-between">
            <span className="text-bolder-muted">Vendor:</span>
            <span className="font-semibold text-bolder-text">
              {settlement.vendorBusinessName || `Vendor #${settlement.vendorId}`}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-bolder-muted">Period:</span>
            <span className="font-medium text-bolder-text">
              {settlement.periodStart} → {settlement.periodEnd}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-bolder-muted">Net Payable:</span>
            <span className="font-extrabold text-bolder-text">
              ₹{Math.abs(settlement.netAmount).toFixed(2)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-bolder-muted">Direction:</span>
            <span className="font-medium text-bolder-text">{settlement.direction}</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label htmlFor="adminReferenceNoteInput" className="block text-xs font-medium text-bolder-text">
              Admin Reference Note <span className="text-bolder-danger">*</span>
            </label>
            <textarea
              id="adminReferenceNoteInput"
              rows={3}
              placeholder="e.g., Bank NEFT UTR #45920392, or manual voucher #FC-2026-004"
              value={adminReferenceNote}
              onChange={(e) => setAdminReferenceNote(e.target.value)}
              className="mt-1 w-full rounded-lg border border-bolder-line-control px-2.5 text-xs text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
              required
            />
            <span className="text-[11px] text-bolder-muted">
              Mandatory reference note recorded into immutable audit logs.
            </span>
          </div>

          {error && <p className="text-xs font-medium text-bolder-danger">{error}</p>}

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-bolder-line">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-4 py-2 text-xs font-medium text-bolder-text hover:bg-bolder-bg"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={finalizeMutation.isPending}
              className="rounded-lg bg-bolder-success-surface px-4 py-2 text-xs font-semibold text-bolder-success shadow-sm hover:bg-bolder-success-surface disabled:opacity-50"
            >
              {finalizeMutation.isPending ? "Finalizing..." : "Confirm Finalization"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
