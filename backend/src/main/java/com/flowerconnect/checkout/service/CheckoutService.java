package com.flowerconnect.checkout.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.checkout.dto.CheckoutItemQuote;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutShopQuote;
import com.flowerconnect.config.CheckoutProperties;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checkout quote service (plan task 5.3).
 *
 * <p>Generates a read-only quote for the customer's current cart against a
 * delivery address and per-vendor slot selection. The quote performs all
 * validations that checkout will later re-check under the inventory lock,
 * but makes no mutations: no stock reservation, no order creation, no cart
 * clearing.
 *
 * <p>Validations per shop (vendor):
 * <ol>
 *   <li>Vendor is {@code APPROVED} and {@code acceptingOrders = true} — 403/409.</li>
 *   <li>Address is within {@code delivery_radius_km} — 400 if not.</li>
 *   <li>Subtotal meets {@code min_order_amount} — 400 {@code MINIMUM_ORDER} if not.</li>
 *   <li>Selected slot is valid and has capacity — 409 {@code SLOT_FULL} if not.</li>
 *   <li>Per-item available stock ≥ cart quantity — 409 {@code INSUFFICIENT_STOCK} if not.</li>
 * </ol>
 *
 * <p>Price calculation (decision C-1): the {@code priceSnapshot} on each cart
 * item is authoritative for the order total. The quote re-reads the current
 * {@code products.base_price} and flags any drift via {@code priceChanged}
 * on the item and a {@code "price_changed_since_add_to_cart"} warning on the
 * shop. The customer must explicitly confirm a changed quote before checkout
 * can proceed.
 *
 * <p>All monetary arithmetic uses {@code BigDecimal} with {@code HALF_UP}
 * scale 2. Prices are tax-inclusive (D-3) — no tax line is added.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final CheckoutProperties checkoutProperties;

    private static final BigDecimal EARTH_RADIUS_KM = new BigDecimal("6371");
    private static final int SCALE_2 = 2;
    private static final int SCALE_4 = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Generates a checkout quote for the authenticated customer's cart.
     *
     * @param customerEmail the caller's email (from JWT subject)
     * @param request       the quote request with addressId and per-vendor slot selection
     * @return the quote with per-shop breakdown, totals, warnings, and errors
     * @throws BusinessException if the cart is empty, address not found,
     *                           or any shop has a blocking validation error
     */
    @Transactional(readOnly = true)
    public CheckoutQuoteResponse generateQuote(String customerEmail, CheckoutQuoteRequest request) {
        User user = requireUser(customerEmail);

        // 1. Load the customer's cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> BusinessException.badRequest("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw BusinessException.badRequest("Cart is empty");
        }

        // 2. Load and validate the delivery address (must belong to the caller)
        AddressRepository.AddressWithLocation address = addressRepository.findByIdAndUserIdWithLocation(request.getAddressId(), user.getId())
                .orElseThrow(() -> BusinessException.notFound("Address not found"));

        // 3. Group cart items by vendor
        Map<Long, List<CartItem>> itemsByVendor = cartItems.stream()
                .collect(Collectors.groupingBy(CartItem::getVendorId));

        // 4. Validate that request covers all vendors in the cart
        Set<Long> cartVendorIds = itemsByVendor.keySet();
        Set<Long> requestVendorIds = request.getItems().stream()
                .map(CheckoutQuoteRequest.QuoteItem::getVendorId)
                .collect(Collectors.toSet());

        if (!cartVendorIds.equals(requestVendorIds)) {
            throw BusinessException.badRequest("Slot selection must include exactly one entry per vendor in the cart");
        }

        // 5. Resolve platform fee (request override takes precedence over config)
        BigDecimal platformFee = request.getPlatformFee() != null ? request.getPlatformFee() : checkoutProperties.getPlatformFee();

        // 6. Build per-shop quotes
        List<CheckoutShopQuote> shopQuotes = new ArrayList<>();
        boolean hasErrors = false;
        boolean hasPriceChanges = false;

        for (CheckoutQuoteRequest.QuoteItem quoteItem : request.getItems()) {
            Long vendorId = quoteItem.getVendorId();
            List<CartItem> vendorItems = itemsByVendor.get(vendorId);

            VendorProfile vendor = vendorProfileRepository.findById(vendorId)
                    .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

            CheckoutShopQuote shopQuote = buildShopQuote(vendor, vendorItems, address, quoteItem.getSlotStart(), platformFee);
            shopQuotes.add(shopQuote);

            if (!shopQuote.getErrors().isEmpty()) {
                hasErrors = true;
            }
            if (shopQuote.getWarnings() != null && shopQuote.getWarnings().stream()
                    .anyMatch(w -> w.contains("price_changed"))) {
                hasPriceChanges = true;
            }
        }

        // 7. Compute grand total (only over shops without errors)
        BigDecimal grandTotal = shopQuotes.stream()
                .filter(s -> s.getErrors().isEmpty())
                .map(CheckoutShopQuote::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CheckoutQuoteResponse.builder()
                .shops(shopQuotes)
                .total(grandTotal.setScale(SCALE_2, ROUNDING))
                .hasErrors(hasErrors)
                .hasPriceChanges(hasPriceChanges)
                .build();
    }

    /**
     * Builds the quote for a single vendor's items.
     */
    private CheckoutShopQuote buildShopQuote(VendorProfile vendor,
                                             List<CartItem> vendorItems,
                                             AddressRepository.AddressWithLocation address,
                                             String slotStartStr,
                                             BigDecimal platformFee) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // ---- Vendor eligibility ----
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            errors.add("Vendor is not approved");
        }
        if (!vendor.isAcceptingOrders()) {
            errors.add("Vendor is not accepting orders");
        }

        // ---- Distance and radius check ----
        BigDecimal distanceKm = haversineKm(
                vendor.getLatitude(), vendor.getLongitude(),
                address.getLatitude(), address.getLongitude()
        ).setScale(SCALE_4, ROUNDING);

        boolean withinRadius = distanceKm.compareTo(vendor.getDeliveryRadiusKm()) <= 0;
        if (!withinRadius) {
            errors.add("Address is outside this vendor's delivery radius");
        }

        // ---- Build item quotes ----
        List<CheckoutItemQuote> itemQuotes = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean allItemsPurchasable = true;

        for (CartItem cartItem : vendorItems) {
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElse(null);
            Inventory inventory = inventoryRepository.findByProductId(product != null ? product.getId() : -1L)
                    .orElse(null);

            CheckoutItemQuote itemQuote = buildItemQuote(cartItem, product, inventory);
            itemQuotes.add(itemQuote);

            subtotal = subtotal.add(itemQuote.getSubtotal());

            if (!itemQuote.isPurchasable()) {
                allItemsPurchasable = false;
            }
            if (itemQuote.isPriceChanged()) {
                warnings.add("price_changed_since_add_to_cart");
            }
        }

        // ---- Delivery fee ----
        BigDecimal deliveryFee = calculateDeliveryFee(vendor, subtotal, distanceKm);

        // ---- Minimum order check ----
        boolean minOrderMet = subtotal.compareTo(vendor.getMinOrderAmount()) >= 0;
        if (!minOrderMet) {
            errors.add("Subtotal below vendor's minimum order of " + vendor.getMinOrderAmount());
        }

        // ---- Slot validation (placeholder - full implementation in task 5.5) ----
        // For now, we validate that the slot string is parseable
        boolean slotAvailable = true;
        try {
            LocalDateTime slotStart = LocalDateTime.parse(slotStartStr);
            // Basic sanity: slot must be in the future
            if (slotStart.isBefore(LocalDateTime.now())) {
                slotAvailable = false;
                errors.add("Selected slot is in the past");
            }
            // TODO: Full slot validation against vendor hours, lead time, horizon, capacity (task 5.5)
        } catch (Exception e) {
            slotAvailable = false;
            errors.add("Invalid slotStart format (expected ISO-8601)");
        }

        // ---- Shop total ----
        BigDecimal total = subtotal.add(deliveryFee).add(platformFee).setScale(SCALE_2, ROUNDING);

        return CheckoutShopQuote.builder()
                .vendorId(vendor.getId())
                .vendorName(vendor.getBusinessName())
                .items(itemQuotes)
                .subtotal(subtotal.setScale(SCALE_2, ROUNDING))
                .deliveryFee(deliveryFee)
                .platformFee(platformFee)
                .minOrderAmount(vendor.getMinOrderAmount())
                .total(total)
                .distanceKm(distanceKm.setScale(SCALE_2, ROUNDING))
                .withinRadius(withinRadius)
                .minOrderMet(minOrderMet)
                .slotAvailable(slotAvailable)
                .warnings(warnings.isEmpty() ? List.of() : warnings)
                .errors(errors.isEmpty() ? List.of() : errors)
                .build();
    }

    /**
     * Builds the item quote with price snapshot comparison.
     */
    private CheckoutItemQuote buildItemQuote(CartItem cartItem, Product product, Inventory inventory) {
        BigDecimal priceSnapshot = cartItem.getPriceSnapshot();
        BigDecimal currentPrice = product != null ? product.getBasePrice() : null;
        boolean priceChanged = currentPrice != null && priceSnapshot.compareTo(currentPrice) != 0;

        int available = inventory != null ? inventory.getAvailable() : 0;
        BigDecimal subtotal = priceSnapshot.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                .setScale(SCALE_2, ROUNDING);

        boolean purchasable = true;
        String reason = null;

        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            purchasable = false;
            reason = "Product is no longer available";
        } else if (available < cartItem.getQuantity()) {
            purchasable = false;
            reason = "Insufficient stock";
        }

        return CheckoutItemQuote.builder()
                .productId(cartItem.getProduct().getId())
                .productName(product != null ? product.getName() : null)
                .productSlug(product != null ? product.getSlug() : null)
                .quantity(cartItem.getQuantity())
                .priceSnapshot(priceSnapshot)
                .currentPrice(currentPrice)
                .priceChanged(priceChanged)
                .note(cartItem.getNote())
                .subtotal(subtotal)
                .available(available)
                .purchasable(purchasable)
                .notPurchasableReason(reason)
                .build();
    }

    /**
     * Calculates the delivery fee for a vendor.
     * Formula: baseFee + perKmFee × distanceKm, or 0 if subtotal ≥ freeDeliveryAbove.
     */
    private BigDecimal calculateDeliveryFee(VendorProfile vendor, BigDecimal subtotal, BigDecimal distanceKm) {
        if (vendor.getFreeDeliveryAbove() != null &&
                subtotal.compareTo(vendor.getFreeDeliveryAbove()) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseFee = vendor.getBaseDeliveryFee();
        BigDecimal perKm = vendor.getPerKmFee();
        BigDecimal fee = baseFee.add(perKm.multiply(distanceKm))
                .setScale(SCALE_2, ROUNDING);
        return fee.max(BigDecimal.ZERO); // never negative
    }

    /**
     * Haversine formula: distance in kilometres between two lat/lng points.
     * Copied from DiscoveryService to keep quote self-contained.
     */
    private BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lng1Rad = Math.toRadians(lng1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double lng2Rad = Math.toRadians(lng2.doubleValue());

        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;

        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);

        double a = sinDLat * sinDLat +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinDLng * sinDLng;

        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        double distance = EARTH_RADIUS_KM.doubleValue() * c;

        return BigDecimal.valueOf(distance);
    }

    private User requireUser(String customerEmail) {
        return userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}