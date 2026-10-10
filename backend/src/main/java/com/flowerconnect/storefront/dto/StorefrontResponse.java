package com.flowerconnect.storefront.dto;

import com.flowerconnect.geo.dto.PageResponse;
import lombok.*;

/**
 * The public vendor storefront (plan task 4.5).
 *
 * <p>Two parts, because they answer two different questions. {@code vendor}
 * is one profile — who this shop is, its delivery settings and its weekly
 * hours — and is not paginated. {@code products} is the shop's ACTIVE
 * catalogue and uses the standard {@link PageResponse} envelope the rest of
 * the public API already returns, so a client pages the product list with
 * the same {@code page}/{@code size} parameters and reads the same
 * {@code totalElements}/{@code totalPages} metadata it reads from discovery
 * and search. Inventing a second pagination shape for one half of one
 * response was avoided deliberately.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontResponse {

    private StorefrontVendorResponse vendor;
    private PageResponse<StorefrontProductResponse> products;
}
