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

package com.flowerconnect.billing.domain;

/**
 * Types of immutable entries recorded in the settlement ledger.
 *
 * <ul>
 *   <li>{@code ONLINE_ORDER_CREDIT}: Platform owes vendor {@code +(S - C + D)} for delivered online order.</li>
 *   <li>{@code COD_DUES}: Vendor collected cash at doorstep; vendor owes platform {@code -(C + P)}.</li>
 *   <li>{@code DISPUTE_ADJUSTMENT}: Dispute refund reversed from vendor balance.</li>
 *   <li>{@code SETTLEMENT_PAYMENT}: Settlement discharge payment entry.</li>
 * </ul>
 */
public enum SettlementEntryType {
    ONLINE_ORDER_CREDIT,
    COD_DUES,
    DISPUTE_ADJUSTMENT,
    SETTLEMENT_PAYMENT
}
