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

package com.flowerconnect.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for commission-rate changes.
 *
 * <p>Percentage 0-100 with at most 2 dp, e.g. {@code 10.00} = 10%.
 */
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminCommissionRateRequest {

    @NotNull(message = "Commission rate is required")
    @Digits(integer = 3, fraction = 2, message = "Commission rate must have at most 2 decimal places")
    @DecimalMin(value = "0.00", message = "Commission rate must be >= 0.00")
    @DecimalMax(value = "100.00", message = "Commission rate must be <= 100.00")
    private BigDecimal rate;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
